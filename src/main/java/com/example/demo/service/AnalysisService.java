
package com.example.demo.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.demo.entity.Note;
import com.example.demo.repository.NoteRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class AnalysisService {

    private final NoteRepository noteRepository;
    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    public AnalysisService(
            NoteRepository noteRepository,
            OpenAIService openAIService,
            ObjectMapper objectMapper) {
        this.noteRepository = noteRepository;
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> analyze(Long id) {

        Note note = noteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Note not found"));

        try {
            String aiResponse = openAIService.analyzeNotes(note.getContent());

            JsonNode aiData = objectMapper.readTree(aiResponse);

            if (aiData.has("choices")) {
                aiData = objectMapper.readTree(
                        aiData.path("choices")
                                .path(0)
                                .path("message")
                                .path("content")
                                .asText());
            } else if (aiData.has("response")) {
                aiData = objectMapper.readTree(
                        aiData.path("response").asText());
            }

            Map<String, Object> result = new LinkedHashMap<>();

            result.put("noteId", note.getId());
            result.put("fileName", note.getFileName());

            String explanation = aiData.path("simpleExplanation").asText("");
            String summary = aiData.path("summary").asText("");

            if (explanation.isBlank() && summary.isBlank()) {
                throw new RuntimeException(
                        "AI returned empty study material. Check the AI response and note content.");
            }

            result.put("simpleExplanation", explanation);
            result.put("summary", summary);

            List<String> keyPoints = new ArrayList<>();
            JsonNode pointsNode = aiData.path("keyPoints");

            if (pointsNode.isArray()) {
                for (JsonNode item : pointsNode) {
                    if (!item.asText().isBlank()) {
                        keyPoints.add(item.asText().trim());
                    }
                }
            }

            result.put("keyPoints", keyPoints);

            List<Map<String, String>> flashcards = new ArrayList<>();
            JsonNode cardsNode = aiData.path("flashcards");

            if (cardsNode.isArray()) {
                for (JsonNode item : cardsNode) {
                    String question = item.path("question").asText("");
                    String answer = item.path("answer").asText("");

                    if (!question.isBlank() && !answer.isBlank()) {
                        Map<String, String> card = new LinkedHashMap<>();
                        card.put("question", question);
                        card.put("answer", answer);
                        flashcards.add(card);
                    }
                }
            }

            List<Map<String, Object>> quiz = new ArrayList<>();
            JsonNode quizNode = aiData.path("quiz");

            if (quizNode.isArray()) {
                for (JsonNode item : quizNode) {
                    String question = item.path("question").asText("");
                    String answer = item.path("answer").asText("");
                    List<String> options = new ArrayList<>();

                    JsonNode optionsNode = item.path("options");
                    if (optionsNode.isArray()) {
                        for (JsonNode option : optionsNode) {
                            options.add(option.asText());
                        }
                    }

                    if (!question.isBlank() && !answer.isBlank()
                            && options.size() >= 2) {
                        Map<String, Object> q = new LinkedHashMap<>();
                        q.put("question", question);
                        q.put("options", options);
                        q.put("answer", answer);
                        quiz.add(q);
                    }
                }
            }

            result.put("flashcards", flashcards);
            result.put("quiz", quiz);

            return result;

        } catch (Exception e) {
            throw new RuntimeException(
                    "AI analysis failed: " + e.getMessage(), e);
        }
    }
}
