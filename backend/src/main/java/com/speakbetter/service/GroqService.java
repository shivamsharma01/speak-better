package com.speakbetter.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.speakbetter.config.GroqProperties;
import com.speakbetter.dto.ImprovementFeedback;
import com.speakbetter.dto.RephraseResponse;
import com.speakbetter.dto.Suggestion;
import com.speakbetter.dto.TranscriptionResponse;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private final WebClient groqWebClient;
    private final GroqProperties props;
    private final ObjectMapper objectMapper;

    public GroqService(WebClient groqWebClient, GroqProperties props, ObjectMapper objectMapper) {
        this.groqWebClient = groqWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public TranscriptionResponse transcribe(MultipartFile audio, String languageHint) {
        validateApiKey();
        String filename = StringUtils.hasText(audio.getOriginalFilename())
                ? audio.getOriginalFilename()
                : "recording.webm";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        try {
            builder.part("file", new ByteArrayResource(audio.getBytes()) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }, MediaType.APPLICATION_OCTET_STREAM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read audio upload", e);
        }
        builder.part("model", props.sttModel());
        if (StringUtils.hasText(languageHint) && !"auto".equalsIgnoreCase(languageHint.trim())) {
            builder.part("language", languageHint.trim());
        }

        JsonNode root = groqWebClient.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientResponseException.class, this::mapGroqError)
                .block();

        if (root == null || !root.has("text")) {
            throw new IllegalStateException("Unexpected transcription response from Groq");
        }
        String text = root.get("text").asText();
        String langOut = StringUtils.hasText(languageHint) && !"auto".equalsIgnoreCase(languageHint.trim())
                ? languageHint.trim()
                : "auto";
        return new TranscriptionResponse(text, langOut);
    }

    public RephraseResponse rephrase(
            String text,
            String language,
            String tone,
            String question,
            String responseLanguage
    ) {
        validateApiKey();
        String langLabel = StringUtils.hasText(language) && !"auto".equalsIgnoreCase(language)
                ? language
                : "the same language as the original (English or Hindi)";

        String toneLine = StringUtils.hasText(tone)
                ? "Overall tone to aim for: " + tone + "."
                : "Vary register slightly (one polished, one concise, one natural).";

        boolean hasQuestion = StringUtils.hasText(question);
        boolean englishOutput = !"same".equalsIgnoreCase(
                StringUtils.hasText(responseLanguage) ? responseLanguage.trim() : "en"
        );

        String system = englishOutput
                ? buildSystemEnglish(hasQuestion, toneLine, langLabel)
                : buildSystemSameLanguage(hasQuestion, toneLine, langLabel);

        String userBlock = hasQuestion
                ? "QUESTION:\n\"\"\"\n" + question.trim() + "\n\"\"\"\n\nUSER'S SPOKEN ANSWER (transcript):\n\"\"\"\n" + text + "\n\"\"\""
                : "Original transcript:\n\"\"\"\n" + text + "\n\"\"\"";

        Map<String, Object> body = new HashMap<>();
        body.put("model", props.chatModel());
        body.put("temperature", 0.55);
        body.put("max_tokens", 2048);
        body.put("response_format", Map.of("type", "json_object"));
        body.put(
                "messages",
                List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", userBlock)
                )
        );

        JsonNode root = groqWebClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .onErrorMap(WebClientResponseException.class, this::mapGroqError)
                .block();

        if (root == null) {
            throw new IllegalStateException("Empty response from Groq chat");
        }
        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Unexpected chat completion shape from Groq");
        }

        try {
            JsonNode parsed = objectMapper.readTree(content);
            JsonNode arr = parsed.path("suggestions");
            if (!arr.isArray() || arr.isEmpty()) {
                throw new IllegalStateException("Model did not return suggestions array");
            }
            List<Suggestion> suggestions = new ArrayList<>();
            for (JsonNode n : arr) {
                String t = n.path("text").asText("").trim();
                String w = n.path("why").asText("").trim();
                if (!t.isEmpty()) {
                    suggestions.add(new Suggestion(t, w));
                }
            }
            if (suggestions.isEmpty()) {
                throw new IllegalStateException("No usable suggestions in model output");
            }

            String modelAnswer = parsed.path("modelAnswer").asText("").trim();
            if (!hasQuestion) {
                modelAnswer = "";
            }

            JsonNode imp = parsed.path("improvements");
            ImprovementFeedback improvements = new ImprovementFeedback(
                    imp.path("opening").asText("").trim(),
                    imp.path("wording").asText("").trim(),
                    imp.path("fillers").asText("").trim(),
                    imp.path("other").asText("").trim()
            );

            return new RephraseResponse(suggestions, modelAnswer, improvements);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse rephrase JSON: " + content, e);
        }
    }

    /**
     * Original behavior: all coaching strings match the transcript's primary language.
     */
    private static String buildSystemSameLanguage(boolean hasQuestion, String toneLine, String langLabel) {
        if (hasQuestion) {
            return """
                    You are an expert interview and public-speaking coach. The user was answering a QUESTION (may be English or Hindi).
                    Their spoken answer transcript is below. It may be English, Hindi, or mixed.

                    Tasks (all in the SAME primary language as the user's transcript, unless the question is clearly only in one language—in that case answer the question in that language and keep other fields aligned):
                    1) Suggest exactly 3 clearer, stronger ways to express the user's answer (same meaning, same language as the transcript).
                    2) Write "modelAnswer": a complete, high-quality answer to the QUESTION as if you (the assistant) were answering it directly—like ChatGPT. Be substantive and structured if helpful.
                    3) Write "improvements" with concrete coaching on the USER's transcript vs the question: how to open/hook ("opening"), sharper wording ("wording"), fillers to cut and what to say instead ("fillers"), and any other tips ("other").

                    %s

                    Respond ONLY as JSON with this exact shape (no markdown, no code fences):
                    {"suggestions":[{"text":"...","why":"short reason"},{"text":"...","why":"..."},{"text":"...","why":"..."}],"modelAnswer":"...","improvements":{"opening":"...","wording":"...","fillers":"...","other":"..."}}
                    Language context for matching the transcript: %s
                    """.formatted(toneLine, langLabel);
        }
        return """
                You are a writing and speaking coach. The user spoke the following. It may be in English or Hindi.
                Suggest exactly 3 clearer or more impactful ways to say the same thing in the SAME language as the original.
                Preserve meaning. %s

                Also fill "improvements" with coaching on delivery: stronger opening/hook ("opening"), wording upgrades ("wording"), fillers to reduce and replacements ("fillers"), other tips ("other").
                Set "modelAnswer" to an empty string "".

                Respond ONLY as JSON with this exact shape (no markdown, no code fences):
                {"suggestions":[{"text":"...","why":"short reason"},{"text":"...","why":"..."},{"text":"...","why":"..."}],"modelAnswer":"","improvements":{"opening":"...","wording":"...","fillers":"...","other":"..."}}
                Language context: %s
                """.formatted(toneLine, langLabel);
    }

    /**
     * English-learning mode: every JSON string value is in English.
     */
    private static String buildSystemEnglish(boolean hasQuestion, String toneLine, String langLabel) {
        String outputRule = """
                OUTPUT LANGUAGE (critical): Every string in the JSON response must be in ENGLISH — including suggestions[].text, suggestions[].why, modelAnswer, and all improvements fields.
                The transcript may be Hindi, English, or mixed. Interpret the user's intent; when the spoken content is not English, give natural English phrasings that preserve meaning.
                You may briefly quote non-English words from the transcript inside English sentences if needed for clarity.
                """;

        if (hasQuestion) {
            return """
                    You are an expert interview and public-speaking coach helping someone practice ENGLISH.
                    The user was answering a QUESTION (may be English or Hindi). Their spoken answer transcript is below.

                    %s

                    Tasks:
                    1) Suggest exactly 3 clearer, stronger ENGLISH ways to express their answer (same meaning).
                    2) Write "modelAnswer": a complete, high-quality ENGLISH answer to the QUESTION as if you were answering in a strong interview or exam style.
                    3) Write "improvements" in ENGLISH with concrete coaching on the USER's transcript vs the question: opening/hook ("opening"), sharper wording ("wording"), fillers to cut and what to say instead ("fillers"), and other tips ("other").

                    %s

                    Respond ONLY as JSON with this exact shape (no markdown, no code fences):
                    {"suggestions":[{"text":"...","why":"short reason"},{"text":"...","why":"..."},{"text":"...","why":"..."}],"modelAnswer":"...","improvements":{"opening":"...","wording":"...","fillers":"...","other":"..."}}
                    Spoken-input hint (for context only; output stays English): %s
                    """.formatted(outputRule, toneLine, langLabel);
        }
        return """
                You are a writing and speaking coach helping someone practice ENGLISH. The user spoke below; the transcript may be English, Hindi, or mixed.

                %s

                Suggest exactly 3 clearer or more impactful ways to say the same thing in ENGLISH. Preserve meaning.
                %s

                Fill "improvements" in ENGLISH: stronger opening/hook ("opening"), wording upgrades ("wording"), fillers to reduce and replacements ("fillers"), other tips ("other").
                Set "modelAnswer" to an empty string "".

                Respond ONLY as JSON with this exact shape (no markdown, no code fences):
                {"suggestions":[{"text":"...","why":"short reason"},{"text":"...","why":"..."},{"text":"...","why":"..."}],"modelAnswer":"","improvements":{"opening":"...","wording":"...","fillers":"...","other":"..."}}
                Spoken-input hint (for context only; output stays English): %s
                """.formatted(outputRule, toneLine, langLabel);
    }

    private void validateApiKey() {
        if (!StringUtils.hasText(props.apiKey())) {
            throw new IllegalStateException("GROQ_API_KEY is not set");
        }
    }

    private Throwable mapGroqError(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        return new IllegalStateException(
                "Groq API error " + ex.getStatusCode().value() + ": " + body,
                ex
        );
    }
}
