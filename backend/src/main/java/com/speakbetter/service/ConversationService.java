package com.speakbetter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speakbetter.dto.ClinicalSummary;
import com.speakbetter.dto.ConversationResponse;
import com.speakbetter.dto.ConversationTurn;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConversationService {

    private final GroqService groqService;
    private final ObjectMapper objectMapper;

    public ConversationService(GroqService groqService) {
        this.groqService = groqService;
        this.objectMapper = groqService.objectMapper();
    }

    public ConversationResponse process(MultipartFile audio, String languageHint) {
        String raw = groqService.transcribeRaw(audio, languageHint);
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("No speech detected in the recording");
        }

        // Primary model (120B): one heavy structuring pass
        String draftJson = structureAndTranslate(raw);
        // Fast model (20B): refinement + summary — separate TPM bucket on Groq
        String refinedJson = refineTranslations(draftJson, raw);
        List<ConversationTurn> turns = parseTurns(refinedJson);

        BilingualSummaries summaries = summarizeBilingual(turns);

        return new ConversationResponse(
                raw.trim(),
                turns,
                summaries.summaryEnglish(),
                summaries.summaryHindi()
        );
    }

    private String structureAndTranslate(String rawTranscript) {
        String system = """
                You are a medical documentation specialist. A doctor–patient conversation was captured by speech-to-text (may be Hindi, English, or mixed).

                Tasks:
                1) Split the FULL transcript into ordered turns. Label each speaker as exactly "doctor" or "patient" using conversational context (questions, examination, advice = doctor; symptoms, history, concerns = patient).
                2) For EACH turn, write textHindi (Devanagari) and textEnglish (accurate clinical English).
                3) Include every clinically relevant detail from the raw transcript. Do NOT summarize or omit content.

                Respond ONLY as JSON (no markdown). Escape double quotes inside strings with backslash.
                {"turns":[{"speaker":"doctor","textHindi":"...","textEnglish":"..."},{"speaker":"patient","textHindi":"...","textEnglish":"..."}]}
                """;

        return groqService.chatJson(
                system,
                "Raw STT transcript:\n\"\"\"\n" + rawTranscript + "\n\"\"\"",
                0.15,
                4096
        );
    }

    private String refineTranslations(String draftJson, String rawTranscript) {
        String system = """
                You are a bilingual medical editor reviewing a doctor–patient transcript draft.

                Fix transcription errors, grammar, terminology, and translation mistakes in textHindi and textEnglish.

                CRITICAL RULES:
                - Do NOT change medical meaning, add new facts, remove information, or paraphrase for style.
                - Keep the same number of turns in the same order with the same speaker labels ("doctor" or "patient").
                - Only correct errors so Hindi and English faithfully match the original conversation meaning.
                - textHindi must remain Devanagari; textEnglish must remain natural clinical English.

                Respond ONLY as JSON with the same shape. Escape double quotes inside strings with backslash.
                {"turns":[{"speaker":"doctor","textHindi":"...","textEnglish":"..."}]}
                """;

        String user = """
                Original raw speech-to-text (ground truth for meaning):
                \"\"\"
                %s
                \"\"\"

                Draft to correct (fix translations without changing meaning):
                %s
                """.formatted(rawTranscript, draftJson);

        return groqService.chatJsonFast(system, user, 0.05, 4096);
    }

    private List<ConversationTurn> parseTurns(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode arr = root.path("turns");
            if (!arr.isArray() || arr.isEmpty()) {
                throw new IllegalStateException("Model did not return conversation turns");
            }
            List<ConversationTurn> turns = new ArrayList<>();
            for (JsonNode n : arr) {
                String speaker = normalizeSpeaker(n.path("speaker").asText(""));
                String hi = n.path("textHindi").asText("").trim();
                String en = n.path("textEnglish").asText("").trim();
                if (!hi.isEmpty() || !en.isEmpty()) {
                    turns.add(new ConversationTurn(speaker, hi, en));
                }
            }
            if (turns.isEmpty()) {
                throw new IllegalStateException("No usable conversation turns");
            }
            return turns;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse conversation JSON: " + json, e);
        }
    }

    private static String normalizeSpeaker(String speaker) {
        String s = speaker.trim().toLowerCase();
        if (s.contains("doctor") || s.contains("physician") || s.contains("dr")) {
            return "doctor";
        }
        return "patient";
    }

    private record BilingualSummaries(ClinicalSummary summaryEnglish, ClinicalSummary summaryHindi) {}

    private BilingualSummaries summarizeBilingual(List<ConversationTurn> turns) {
        String system = """
                You summarize doctor–patient conversations for clinical handoff in BOTH English and Hindi.
                Extract only what was actually discussed — do not invent diagnoses or plans.

                Respond ONLY as JSON. Escape double quotes inside strings with backslash.
                {"summaryEnglish":{"chiefComplaint":"","keyPoints":[],"assessment":"","plan":"","followUp":"","medicationsMentioned":[]},"summaryHindi":{"chiefComplaint":"","keyPoints":[],"assessment":"","plan":"","followUp":"","medicationsMentioned":[]}}
                All summaryHindi strings must be in Devanagari Hindi.
                """;

        StringBuilder dialogue = new StringBuilder();
        for (ConversationTurn t : turns) {
            dialogue.append(t.speaker())
                    .append(": ")
                    .append(t.textEnglish())
                    .append("\n");
        }

        String content = groqService.chatJsonFast(
                system,
                "Conversation (English reference):\n\"\"\"\n" + dialogue + "\n\"\"\"",
                0.2,
                2048
        );

        try {
            JsonNode root = objectMapper.readTree(content);
            return new BilingualSummaries(
                    parseSummaryNode(root.path("summaryEnglish")),
                    parseSummaryNode(root.path("summaryHindi"))
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse bilingual summary JSON: " + content, e);
        }
    }

    private ClinicalSummary parseSummaryNode(JsonNode n) {
        return new ClinicalSummary(
                n.path("chiefComplaint").asText("").trim(),
                readStringList(n.path("keyPoints")),
                n.path("assessment").asText("").trim(),
                n.path("plan").asText("").trim(),
                n.path("followUp").asText("").trim(),
                readStringList(n.path("medicationsMentioned"))
        );
    }

    private static List<String> readStringList(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) {
            for (JsonNode item : arr) {
                String s = item.asText("").trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }
}
