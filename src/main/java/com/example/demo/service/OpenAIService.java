package com.example.demo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class OpenAIService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OpenAIService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String analyzeNotes(String notes) throws Exception {
        if (notes == null || notes.trim().isEmpty()) {
            throw new IllegalArgumentException("Notes cannot be empty.");
        }

        String sourceNotes = notes.trim();
        if (sourceNotes.length() > 4000) {
            sourceNotes = sourceNotes.substring(0, 4000);
        }

        String prompt = """
                You are ExplainMyNotes, a study assistant.
                Return only valid JSON with these keys:
                simpleExplanation, summary, keyPoints, flashcards, quiz.
                Use this structure:
                {
                  "simpleExplanation": "Simple explanation",
                  "summary": "Short summary",
                  "keyPoints": ["Point 1", "Point 2", "Point 3"],
                  "flashcards": [
                    {"question": "Question", "answer": "Answer"}
                  ],
                  "quiz": [
                    {
                      "question": "Question",
                      "options": ["Option 1", "Option 2", "Option 3", "Option 4"],
                      "answer": "Exact correct option"
                    }
                  ]
                }
                Rules:
                - Use only facts from the student notes.
                - Use simple English and concise explanations.
                - Provide up to three key points, flashcards, and quiz questions.
                - Every quiz must have four distinct options.
                - Each answer must exactly match one option.
                - Return JSON only, without Markdown.
                STUDENT NOTES:
                %s
                """.formatted(sourceNotes);

        String requestBody = """
                {
                  "model": "glm-5.2",
                  "messages": [
                    {
                      "role": "user",
                      "content": "%s"
                    }
                  ],
                  "temperature": 0.1,
                  "max_tokens": 1200,
                  "stream": false,
                  "response_format": {"type": "json_object"}
                }
                """.formatted(escapeJson(prompt));

        String apiKey = System.getenv("INFERENCE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("INFERENCE_API_KEY is not configured.");
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.inference.net/v1/chat/completions"))
                .timeout(Duration.ofMinutes(3))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Hosted AI request failed with status "
                    + response.statusCode() + ": " + response.body());
        }

        JsonNode result = objectMapper.readTree(response.body());
        JsonNode content = result.path("choices").path(0)
                .path("message").path("content");

        if (!content.isTextual() || content.asText().isBlank()) {
            throw new RuntimeException("Hosted AI returned an empty response.");
        }

        String analysis = content.asText();
        objectMapper.readTree(analysis);
        return analysis;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
