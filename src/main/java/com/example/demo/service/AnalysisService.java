
package com.example.demo.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
            String ollamaResponse =
                    openAIService.analyzeNotes(note.getContent());

            JsonNode outerJson = objectMapper.readTree(ollamaResponse);
            String aiJson = outerJson.path("response").asText();
            JsonNode aiData = objectMapper.readTree(aiJson);

            Map<String, Object> result = new LinkedHashMap<>();

            result.put("noteId", note.getId());
            result.put("fileName", note.getFileName());
            result.put("simpleExplanation",
                    cleanText(aiData.path("simpleExplanation").asText("")));
            result.put("summary",
                    cleanText(aiData.path("summary").asText("")));

            List<String> keyPoints = new ArrayList<>();
            JsonNode keyPointsNode = aiData.path("keyPoints");

            if (keyPointsNode.isArray()) {
                for (JsonNode item : keyPointsNode) {
                    String point = cleanText(item.asText());
                    if (!point.isEmpty()) {
                        keyPoints.add(point);
                    }
                }
            }

            result.put("keyPoints", keyPoints);

            List<Map<String, Object>> quiz = new ArrayList<>();
            JsonNode quizNode = aiData.path("quiz");

            if (quizNode.isArray()) {
                for (JsonNode questionNode : quizNode) {

                    String question = cleanText(
                            questionNode.path("question").asText(""));

                    List<String> options = new ArrayList<>();
                    Set<String> usedOptions = new HashSet<>();
                    JsonNode optionsNode = questionNode.path("options");

                    if (optionsNode.isArray()) {
                        for (JsonNode option : optionsNode) {
                            String cleanedOption =
                                    cleanText(option.asText());

                            if (!cleanedOption.isEmpty()
                                    && usedOptions.add(
                                            cleanedOption.toLowerCase(Locale.ROOT))) {
                                options.add(cleanedOption);
                            }
                        }
                    }

                    String answer = cleanText(
                            questionNode.path("answer").asText(""));

                    String matchedAnswer = "";

                    for (String option : options) {
                        if (option.equalsIgnoreCase(answer)) {
                            matchedAnswer = option;
                            break;
                        }
                    }

                    if (!question.isEmpty()
                            && options.size() == 4
                            && !matchedAnswer.isEmpty()) {

                        Map<String, Object> cleanedQuestion =
                                new LinkedHashMap<>();

                        cleanedQuestion.put("question", question);
                        cleanedQuestion.put("options", options);
                        cleanedQuestion.put("answer", matchedAnswer);

                        quiz.add(cleanedQuestion);
                    }
                }
            }

            List<Map<String, String>> flashcards = new ArrayList<>();

            for (Map<String, Object> quizQuestion : quiz) {
                Map<String, String> card = new LinkedHashMap<>();

                card.put("question",
                        String.valueOf(quizQuestion.get("question")));
                card.put("answer",
                        String.valueOf(quizQuestion.get("answer")));

                flashcards.add(card);
            }

            result.put("flashcards", flashcards);
            result.put("quiz", quiz);

            return result;

        } catch (Exception e) {
            throw new RuntimeException(
                    "AI analysis failed: " + e.getMessage(), e);
        }
    }

    private String cleanText(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replaceAll("([a-z])([A-Z])", "$1 $2")
                .replaceAll("(?i)\\bsecondsemester\\b", "second semester")
                .replaceAll("(?i)\\bwhatis\\b", "What is")
                .replaceAll("(?i)\\banalysisin\\b", "analysis in")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
