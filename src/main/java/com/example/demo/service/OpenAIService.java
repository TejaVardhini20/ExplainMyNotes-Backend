package com.example.demo.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.stereotype.Service;

@Service
public class OpenAIService {

    private final HttpClient httpClient;

    public OpenAIService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String analyzeNotes(String notes) throws Exception {

        if (notes == null || notes.trim().isEmpty()) {
            throw new IllegalArgumentException("Notes cannot be empty.");
        }

        String sourceNotes = notes.trim();

if (sourceNotes.length() > 20000) {
    throw new IllegalArgumentException(
            "This PDF contains too much text for one analysis. "
                    + "Please split it into smaller sections and upload them separately."
    );
}

        String prompt = """
                You are ExplainMyNotes, an accurate study assistant.

                Return ONLY valid JSON with these keys:
                simpleExplanation, summary, keyPoints, flashcards, quiz.

                Required structure:
                {
                  "simpleExplanation": "Simple explanation",
                  "summary": "Meaningful summary",
                  "keyPoints": [
                    "Point 1",
                    "Point 2",
                    "Point 3",
                    "Point 4",
                    "Point 5"
                  ],
                  "flashcards": [
                    {
                      "question": "Question",
                      "answer": "Answer"
                    }
                  ],
                  "quiz": [
                    {
                      "question": "Question",
                      "options": [
                        "Option 1",
                        "Option 2",
                        "Option 3",
                        "Option 4"
                      ],
                      "answer": "Exact correct option"
                    }
                  ]
                }

                
ACCURACY RULES:
- Treat STUDENT NOTES as the only source of factual information.
- Use no outside knowledge, assumptions, or invented details.
- Before writing any fact, check that it is supported by the supplied notes.
- Preserve names, definitions, dates, numbers, technical terms, and course codes exactly as supported by the notes.
- Do not create relationships between names, subjects, dates, or codes unless the notes explicitly support them.
- If the notes do not contain enough evidence for a question, do not generate that question.
- Every key point must communicate a fact supported by the notes.
- Every flashcard answer must directly answer its question using information supported by the notes.
- Every quiz question must have exactly one clearly correct answer supported by the notes.
- Every quiz answer must exactly match one of its four options.
- All four quiz options must be different.
- Incorrect options may be plausible alternatives, but must not also be correct according to the notes.
- Do not use an answer merely because it sounds reasonable.
- If the notes are incomplete or unclear, state the limitation instead of guessing.


                CONTENT RULES:
                - Use simple English suitable for a college student.
                - Preserve technical terms and course names.
                - Write words with proper spacing.
                - Write "What is", never "Whatis".
                - Write "second semester", never "secondsemester".
                - Write "analysis in", never "analysisin".
                - Provide up to five key points.
                - Provide up to five flashcards.
                - Provide up to five multiple-choice questions.
                - Avoid making quiz questions identical to flashcards.
                - Make the explanation and summary useful and meaningful.
                - Return JSON only, without Markdown or extra text.

                STUDENT NOTES:
                %s
                """.formatted(sourceNotes);

        String requestBody = """
                {
                  "model": "llama3.2:3b",
                  "prompt": "%s",
                  "stream": false,
                  "format": "json",
                  "options": {
                    "temperature": 0.1,
                    "num_predict": 2500
                  }
                }
                """.formatted(escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:11434/api/generate"))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException(
                    "Ollama request failed with status "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );
        }

        return response.body();
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