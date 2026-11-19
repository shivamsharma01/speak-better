package com.speakbetter.speaking.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.speakbetter.service.GroqService;
import com.speakbetter.speaking.domain.DrillEvaluation;
import com.speakbetter.speaking.domain.DrillItem;
import com.speakbetter.speaking.domain.SpeakingMode;
import com.speakbetter.speaking.domain.SpeakingSession;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class GroqDrillEngine implements DrillEngine {

    private final GroqService groqService;

    public GroqDrillEngine(GroqService groqService) {
        this.groqService = groqService;
    }

    @Override
    public String id() {
        return "groq";
    }

    @Override
    public GeneratedDrill generate(SpeakingSession session) {
        if (session.getMode() == SpeakingMode.HINGLISH_CHAT) {
            return generateChat(session);
        }
        return generateSentences(session);
    }

    private GeneratedDrill generateSentences(SpeakingSession session) {
        String topic = StringUtils.hasText(session.getTopic()) ? session.getTopic().trim() : "daily life";
        String level = StringUtils.hasText(session.getLevel()) ? session.getLevel().trim() : "intermediate";
        String system = """
                You create Hinglish → English speaking drills for Indian adults.
                Generate exactly 5 natural Hinglish sentences about the topic.
                Each sentence should sound like real spoken Indian Hinglish (Roman script OK),
                not pure Hindi and not pure English.

                Respond ONLY as JSON:
                {
                  "title": "short drill title",
                  "instructions": "one short line of what the student should do",
                  "items": [
                    {
                      "hinglish": "...",
                      "modelEnglish": "natural spoken English with the same meaning"
                    }
                  ]
                }

                Rules:
                - Level: %s
                - Topic: %s
                - Vary length: 1 short, a few medium, 1 longer thought.
                - modelEnglish must be natural spoken English (not stiff translation).
                - No markdown.
                """.formatted(level, topic);

        String content = groqService.chatJsonFast(
                system,
                "Create the 5-sentence Hinglish drill now.",
                0.7,
                1400
        );
        return parseGenerated(content, false);
    }

    private GeneratedDrill generateChat(SpeakingSession session) {
        String topic = StringUtils.hasText(session.getTopic()) ? session.getTopic().trim() : "weekend plans";
        String level = StringUtils.hasText(session.getLevel()) ? session.getLevel().trim() : "intermediate";
        String system = """
                You create a short Hinglish WhatsApp-style chat between two Indian adults.
                Generate exactly 6 turns (alternating speakers), Roman Hinglish, natural and conversational.

                Respond ONLY as JSON:
                {
                  "title": "short chat title",
                  "instructions": "Read each Hinglish turn and say the same idea in natural English.",
                  "items": [
                    {
                      "speaker": "Asha",
                      "hinglish": "...",
                      "modelEnglish": "natural spoken English for that turn"
                    }
                  ]
                }

                Rules:
                - Level: %s
                - Topic/situation: %s
                - Use two clear speaker names.
                - Keep each turn to one spoken idea (not a paragraph).
                - modelEnglish should sound like that person speaking English naturally.
                - No markdown.
                """.formatted(level, topic);

        String content = groqService.chatJsonFast(
                system,
                "Create the Hinglish chat drill now.",
                0.75,
                1800
        );
        return parseGenerated(content, true);
    }

    private GeneratedDrill parseGenerated(String content, boolean chat) {
        try {
            JsonNode root = groqService.objectMapper().readTree(content);
            String title = root.path("title").asText(chat ? "Hinglish chat" : "Hinglish sentences");
            String instructions = root.path("instructions").asText(
                    "Say each Hinglish line in natural English."
            );
            JsonNode arr = root.path("items");
            List<DrillItem> items = new ArrayList<>();
            if (arr.isArray()) {
                int i = 0;
                for (JsonNode n : arr) {
                    String hinglish = n.path("hinglish").asText("").trim();
                    String model = n.path("modelEnglish").asText("").trim();
                    if (!StringUtils.hasText(hinglish) || !StringUtils.hasText(model)) {
                        continue;
                    }
                    DrillItem item = new DrillItem();
                    item.setId(UUID.randomUUID().toString());
                    item.setIndex(i);
                    item.setHinglish(hinglish);
                    item.setModelEnglish(model);
                    item.setSpeaker(chat ? n.path("speaker").asText("").trim() : null);
                    item.setStatus("pending");
                    items.add(item);
                    i++;
                }
            }
            if (items.isEmpty()) {
                throw new IllegalStateException("Drill generator returned no usable items");
            }
            return new GeneratedDrill(title, items, instructions);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate Hinglish drill: " + e.getMessage(), e);
        }
    }

    @Override
    public DrillEvaluation evaluateOne(DrillItem item, String studentEnglish, String level) {
        String system = """
                You evaluate how well a student expressed a Hinglish idea in spoken English.
                Score ENGLISH skill, not the microphone.

                STT / names (critical):
                - Speech recognition often mangles Indian names and places
                  (e.g. "Shahi Dilli" → "Shahid Delhi", "Connaught" → "connect").
                - If the student's words are a close phonetic match to names in the Hinglish /
                  model English, treat the meaning as preserved. Do NOT scold them about the name.
                - Never say "check the name" unless they clearly said a totally different place/person.

                usefulPhrase rules (critical):
                - Only suggest a phrase if it is a reusable speaking chunk the student missed
                  (e.g. "Let's try…", "I feel like…", "It's a bit of a stretch").
                - Do NOT suggest trivial leftover words like "a bit fancy", "new restaurant",
                  single adjectives, or fragments already used well by the student.
                - Prefer "" (empty string) over a weak phrase. Empty is better than noise.

                verdict rules:
                - Be specific and fair. Quote what was good.
                - If meaning is basically right, say so. Don't invent problems.

                Respond ONLY as JSON:
                {
                  "score": 1,
                  "meaningPreserved": true,
                  "verdict": "one short specific sentence",
                  "betterEnglish": "polished natural English (fix only STT name spellings lightly)",
                  "usefulPhrase": "high-value reusable chunk OR empty string",
                  "tip": "one short tip, or empty if nothing useful to add"
                }

                Scoring:
                5 = meaning clear + natural English (ignore STT name spelling)
                4 = meaning clear, small wording upgrade
                3 = mostly clear, awkward or incomplete
                2 = meaning partly lost
                1 = mostly wrong / empty / unrelated
                Level hint: %s
                """.formatted(StringUtils.hasText(level) ? level : "intermediate");

        String user = """
                Hinglish prompt:
                \"%s\"

                Model English reference:
                \"%s\"

                Student English (may be from speech recognition):
                \"%s\"
                """.formatted(item.getHinglish(), item.getModelEnglish(), studentEnglish.trim());

        DrillEvaluation evaluation = parseEvaluation(
                item,
                studentEnglish,
                groqService.chatJsonFast(system, user, 0.15, 700)
        );
        sanitizeFeedback(evaluation, studentEnglish);
        return evaluation;
    }

    @Override
    public List<DrillEvaluation> evaluateFullRetelling(
            List<DrillItem> items,
            String fullEnglishRetelling,
            String level
    ) {
        StringBuilder prompts = new StringBuilder();
        for (DrillItem item : items) {
            prompts.append(item.getIndex() + 1).append(". ");
            if (StringUtils.hasText(item.getSpeaker())) {
                prompts.append(item.getSpeaker()).append(": ");
            }
            prompts.append(item.getHinglish())
                    .append("\n   Model EN: ")
                    .append(item.getModelEnglish())
                    .append("\n");
        }

        String system = """
                The student heard/read a Hinglish chat and retold the WHOLE conversation in English.
                Split their retelling into turns matching the original chat order and evaluate each turn.

                Respond ONLY as JSON:
                {
                  "evaluations": [
                    {
                      "index": 0,
                      "studentEnglish": "portion of retelling for this turn",
                      "score": 1,
                      "meaningPreserved": true,
                      "verdict": "short",
                      "betterEnglish": "...",
                      "usefulPhrase": "...",
                      "tip": "..."
                    }
                  ]
                }

                Rules:
                - Return one evaluation per original turn index (0..n-1).
                - If a turn is missing from the retelling, score low and say what was missing.
                - Forgive STT near-misses on Indian names/places; do not nag about names.
                - usefulPhrase only if high-value; otherwise "".
                - Level: %s
                """.formatted(StringUtils.hasText(level) ? level : "intermediate");

        String user = """
                Original Hinglish chat turns:
                %s

                Student's full English retelling:
                \"\"\"
                %s
                \"\"\"
                """.formatted(prompts, fullEnglishRetelling.trim());

        try {
            String content = groqService.chatJsonFast(system, user, 0.2, 1600);
            JsonNode root = groqService.objectMapper().readTree(content);
            JsonNode arr = root.path("evaluations");
            List<DrillEvaluation> out = new ArrayList<>();
            if (!arr.isArray()) {
                return out;
            }
            for (JsonNode n : arr) {
                int idx = n.path("index").asInt(-1);
                if (idx < 0 || idx >= items.size()) {
                    continue;
                }
                DrillItem item = items.get(idx);
                String studentBit = n.path("studentEnglish").asText("").trim();
                if (!StringUtils.hasText(studentBit)) {
                    studentBit = "(not covered in retelling)";
                }
                DrillEvaluation e = fromJsonNode(item, studentBit, n);
                sanitizeFeedback(e, studentBit);
                out.add(e);
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to evaluate full retelling: " + e.getMessage(), e);
        }
    }

    /**
     * Drop low-value "Remember" phrases and vague name nags caused by STT near-misses.
     */
    static void sanitizeFeedback(DrillEvaluation evaluation, String studentEnglish) {
        String phrase = evaluation.getUsefulPhrase() != null ? evaluation.getUsefulPhrase().trim() : "";
        if (!StringUtils.hasText(phrase)) {
            evaluation.setUsefulPhrase("");
        } else {
            String[] words = phrase.split("\\s+");
            String student = studentEnglish != null ? studentEnglish.toLowerCase(java.util.Locale.ROOT) : "";
            boolean alreadySaid = student.contains(phrase.toLowerCase(java.util.Locale.ROOT));
            boolean tooTrivial = words.length < 2
                    || words.length > 8
                    || phrase.matches("(?i)(a bit|a little|very|really|so|quite)\\s+\\w+")
                    || phrase.matches("(?i)^(new|old|good|bad|fancy|nice)\\s+\\w+$");
            evaluation.setUsefulPhrase(alreadySaid || tooTrivial ? "" : phrase);
        }

        String tip = evaluation.getTip() != null ? evaluation.getTip() : "";
        String verdict = evaluation.getVerdict() != null ? evaluation.getVerdict() : "";
        if (isVagueNameNag(tip) || isVagueNameNag(verdict)) {
            if (evaluation.isMeaningPreserved() && evaluation.getScore() < 4) {
                evaluation.setScore(4);
            }
            if (isVagueNameNag(verdict)) {
                evaluation.setVerdict("Meaning came across clearly — good spoken English.");
            }
            if (isVagueNameNag(tip)) {
                evaluation.setTip("");
            }
        }
    }

    private static boolean isVagueNameNag(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("check the name")
                || lower.contains("wrong name")
                || lower.contains("correct the name")
                || (lower.contains("name") && (lower.contains("check") || lower.contains("missed") || lower.contains("incorrect")));
    }

    private DrillEvaluation parseEvaluation(DrillItem item, String studentEnglish, String content) {
        try {
            JsonNode n = groqService.objectMapper().readTree(content);
            return fromJsonNode(item, studentEnglish, n);
        } catch (Exception e) {
            DrillEvaluation fallback = new DrillEvaluation();
            fallback.setId(UUID.randomUUID().toString());
            fallback.setDrillItemId(item.getId());
            fallback.setDrillIndex(item.getIndex());
            fallback.setStudentEnglish(studentEnglish);
            fallback.setModelEnglish(item.getModelEnglish());
            fallback.setHinglish(item.getHinglish());
            fallback.setScore(3);
            fallback.setMeaningPreserved(true);
            fallback.setVerdict("Could not fully score this attempt; compare with the model English.");
            fallback.setBetterEnglish(item.getModelEnglish());
            fallback.setUsefulPhrase("");
            fallback.setTip(e.getMessage());
            fallback.setCreatedAt(Instant.now());
            return fallback;
        }
    }

    private static DrillEvaluation fromJsonNode(DrillItem item, String studentEnglish, JsonNode n) {
        DrillEvaluation e = new DrillEvaluation();
        e.setId(UUID.randomUUID().toString());
        e.setDrillItemId(item.getId());
        e.setDrillIndex(item.getIndex());
        e.setStudentEnglish(studentEnglish);
        e.setModelEnglish(item.getModelEnglish());
        e.setHinglish(item.getHinglish());
        int score = n.path("score").asInt(3);
        e.setScore(Math.max(1, Math.min(5, score)));
        e.setMeaningPreserved(n.path("meaningPreserved").asBoolean(true));
        e.setVerdict(n.path("verdict").asText("").trim());
        String better = n.path("betterEnglish").asText("").trim();
        e.setBetterEnglish(StringUtils.hasText(better) ? better : item.getModelEnglish());
        e.setUsefulPhrase(n.path("usefulPhrase").asText("").trim());
        e.setTip(n.path("tip").asText("").trim());
        e.setCreatedAt(Instant.now());
        return e;
    }
}
