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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class GroqService {

    private static final int MAX_RATE_LIMIT_RETRIES = 3;
    private static final int MAX_JSON_RETRIES = 3;
    private static final Pattern RETRY_AFTER_SECONDS = Pattern.compile("try again in ([\\d.]+)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final WebClient groqWebClient;
    private final GroqProperties props;
    private final ObjectMapper objectMapper;

    public GroqService(WebClient groqWebClient, GroqProperties props, ObjectMapper objectMapper) {
        this.groqWebClient = groqWebClient;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public TranscriptionResponse transcribe(MultipartFile audio, String languageHint) {
        String text = transcribeRaw(audio, languageHint);
        String langOut = StringUtils.hasText(languageHint) && !"auto".equalsIgnoreCase(languageHint.trim())
                ? languageHint.trim()
                : "auto";

        BilingualTranscript bilingual = buildBilingualTranscript(text);
        return new TranscriptionResponse(
                text,
                langOut,
                bilingual.textHindi(),
                bilingual.textEnglish()
        );
    }

    /** Whisper speech-to-text only (no bilingual pass). */
    public String transcribeRaw(MultipartFile audio, String languageHint) {
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
        return root.get("text").asText();
    }

    /**
     * Chat JSON using the primary (best) model.
     */
    public String chatJson(String system, String user, double temperature, int maxTokens) {
        return chatJsonWithModel(resolvePrimaryModel(), system, user, temperature, maxTokens);
    }

    /**
     * Chat JSON using the fast model — separate Groq TPM pool, avoids exhausting 120B quota.
     */
    public String chatJsonFast(String system, String user, double temperature, int maxTokens) {
        return chatJsonWithModel(resolveFastModel(), system, user, temperature, maxTokens);
    }

    /**
     * Plain-text chat (no JSON mode) — used for low-latency live conversation turns.
     * {@code messages} is an OpenAI-style list of role/content maps.
     */
    public String chatTextFast(List<Map<String, String>> messages, double temperature, int maxTokens) {
        return chatTextWithModel(resolveFastModel(), messages, temperature, maxTokens);
    }

    private String chatTextWithModel(
            String model,
            List<Map<String, String>> messages,
            double temperature,
            int maxTokens
    ) {
        validateApiKey();
        Map<String, Object> body = buildChatBody(model, messages, temperature, maxTokens, false);
        try {
            JsonNode root = executeChatCompletion(body);
            return extractMessageContent(root).trim();
        } catch (WebClientResponseException ex) {
            throw mapGroqError(ex);
        }
    }

    private String chatJsonWithModel(
            String model,
            String system,
            String user,
            double temperature,
            int maxTokens
    ) {
        validateApiKey();
        Exception lastError = null;

        for (int attempt = 0; attempt < MAX_JSON_RETRIES; attempt++) {
            boolean strictJson = attempt == 0;
            int tokens = attempt == 0 ? maxTokens : Math.min(maxTokens + 1536, 8192);
            String systemPrompt = strictJson
                    ? system
                    : system + "\n\nReturn ONE valid JSON object only. No markdown fences, no commentary.";

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", user)
            );
            Map<String, Object> body = buildChatBody(model, messages, temperature, tokens, strictJson);
            try {
                JsonNode root = executeChatCompletion(body);
                String content = extractMessageContent(root);
                return normalizeJsonContent(content);
            } catch (WebClientResponseException ex) {
                if (isJsonValidateFailed(ex)) {
                    lastError = ex;
                    continue;
                }
                throw mapGroqError(ex);
            } catch (Exception ex) {
                lastError = ex;
                if (attempt < MAX_JSON_RETRIES - 1) {
                    continue;
                }
                throw ex instanceof IllegalStateException ise
                        ? ise
                        : new IllegalStateException("Failed to parse model JSON: " + ex.getMessage(), ex);
            }
        }

        throw new IllegalStateException(
                "Groq could not produce valid JSON after " + MAX_JSON_RETRIES + " attempts. "
                        + "Try a shorter recording or wait a moment and retry.",
                lastError
        );
    }

    private Map<String, Object> buildChatBody(
            String model,
            List<Map<String, String>> messages,
            double temperature,
            int maxTokens,
            boolean strictJson
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        if (strictJson) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", messages);
        return body;
    }

    private JsonNode executeChatCompletion(Map<String, Object> body) {
        int rateLimitAttempt = 0;
        while (true) {
            try {
                return groqWebClient.post()
                        .uri("/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();
            } catch (WebClientResponseException ex) {
                if (ex.getStatusCode().value() == 429 && rateLimitAttempt < MAX_RATE_LIMIT_RETRIES) {
                    sleepForRateLimit(ex);
                    rateLimitAttempt++;
                    continue;
                }
                throw ex;
            }
        }
    }

    private static String extractMessageContent(JsonNode root) {
        if (root == null) {
            throw new IllegalStateException("Empty response from Groq chat");
        }
        String content = root.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Unexpected chat completion shape from Groq");
        }
        return content;
    }

    private String normalizeJsonContent(String raw) {
        String candidate = raw.trim();
        Matcher fence = JSON_FENCE.matcher(candidate);
        if (fence.find()) {
            candidate = fence.group(1).trim();
        }
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            candidate = candidate.substring(start, end + 1);
        }
        try {
            objectMapper.readTree(candidate);
            return candidate;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Model returned unparsable JSON", e);
        }
    }

    private static boolean isJsonValidateFailed(WebClientResponseException ex) {
        if (ex.getStatusCode().value() != 400) {
            return false;
        }
        String body = ex.getResponseBodyAsString();
        return body != null && body.contains("json_validate_failed");
    }

    private void sleepForRateLimit(WebClientResponseException ex) {
        long waitMs = 55_000L;
        List<String> retryAfter = ex.getHeaders().get("retry-after");
        if (retryAfter != null && !retryAfter.isEmpty()) {
            try {
                waitMs = (long) (Double.parseDouble(retryAfter.get(0)) * 1000);
            } catch (NumberFormatException ignored) {
                // use default
            }
        } else {
            Matcher m = RETRY_AFTER_SECONDS.matcher(ex.getResponseBodyAsString());
            if (m.find()) {
                try {
                    waitMs = (long) (Double.parseDouble(m.group(1)) * 1000) + 500;
                } catch (NumberFormatException ignored) {
                    // use default
                }
            }
        }
        waitMs = Math.min(Math.max(waitMs, 5_000L), 120_000L);
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Groq rate limit", ie);
        }
    }

    private String resolvePrimaryModel() {
        return props.chatModel();
    }

    private String resolveFastModel() {
        if (StringUtils.hasText(props.chatModelFast())) {
            return props.chatModelFast();
        }
        return props.chatModel();
    }

    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    private record BilingualTranscript(String textHindi, String textEnglish) {}

    private BilingualTranscript buildBilingualTranscript(String whisperText) {
        if (!StringUtils.hasText(whisperText)) {
            return new BilingualTranscript("", "");
        }

        String system = """
                You normalize speech transcripts for language learners.
                The user spoke; the raw transcript may be Hindi, English, or mixed (Roman or Devanagari).
                Produce the FULL utterance in both languages — do not summarize or omit parts.

                Respond ONLY as JSON (no markdown):
                {"textHindi":"complete transcript in Devanagari Hindi","textEnglish":"complete accurate English translation"}

                Rules:
                - textHindi must use Devanagari script.
                - textEnglish must be natural English.
                - If the input is already Hindi, textHindi should match the meaning closely (fix obvious STT errors lightly).
                - If the input is already English, textEnglish should match closely; textHindi is the Hindi translation.
                - If mixed, merge into coherent full sentences in each language.
                """;

        try {
            String content = chatJsonFast(system, "Raw transcript:\n\"\"\"\n" + whisperText + "\n\"\"\"", 0.2, 1536);
            JsonNode parsed = objectMapper.readTree(content);
            String hi = parsed.path("textHindi").asText("").trim();
            String en = parsed.path("textEnglish").asText("").trim();
            if (hi.isEmpty() && en.isEmpty()) {
                return fallbackBilingual(whisperText);
            }
            if (hi.isEmpty()) {
                hi = whisperText.trim();
            }
            if (en.isEmpty()) {
                en = whisperText.trim();
            }
            return new BilingualTranscript(hi, en);
        } catch (Exception e) {
            return fallbackBilingual(whisperText);
        }
    }

    private static BilingualTranscript fallbackBilingual(String whisperText) {
        String t = whisperText.trim();
        return new BilingualTranscript(t, t);
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

        String content = chatJsonFast(system, userBlock, 0.55, 1536);

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

    private IllegalStateException mapGroqError(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (ex.getStatusCode().value() == 401 || (body != null && body.contains("invalid_api_key"))) {
            return new IllegalStateException(
                    "GROQ_API_KEY is missing or invalid. Export a real key from https://console.groq.com/keys "
                            + "in the same terminal before starting the backend (mvn spring-boot:run), then restart. "
                            + "Details: " + body,
                    ex
            );
        }
        if (ex.getStatusCode().value() == 429) {
            return new IllegalStateException(
                    "Groq rate limit reached (tokens per minute). The app will auto-retry when possible; "
                            + "if this persists, wait about a minute between long recordings or upgrade your Groq tier. "
                            + "Details: " + body,
                    ex
            );
        }
        if (isJsonValidateFailed(ex)) {
            return new IllegalStateException(
                    "Groq rejected the model output as invalid JSON. The server will retry automatically; "
                            + "if you still see this, try a shorter clip. Details: " + body,
                    ex
            );
        }
        return new IllegalStateException(
                "Groq API error " + ex.getStatusCode().value() + ": " + body,
                ex
        );
    }
}
