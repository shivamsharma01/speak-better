package com.speakbetter.speaking.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.speakbetter.service.GroqService;
import com.speakbetter.speaking.domain.CorrectionEvent;
import com.speakbetter.speaking.domain.SpeakingMode;
import com.speakbetter.speaking.domain.SpeakingSession;
import com.speakbetter.speaking.domain.SpeakingTurn;
import com.speakbetter.speaking.domain.TurnRole;
import com.speakbetter.speaking.domain.UtteranceSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class GroqCoachingEngine implements CoachingEngine {

    private static final double MIN_CONFIDENCE_TYPED = 0.6;
    private static final double MIN_CONFIDENCE_SPEECH = 0.78;
    private static final double MIN_CONFIDENCE_WORD_UPGRADE_SPEECH = 0.62;
    private static final Set<String> WORD_UPGRADE_TYPES = Set.of(
            "word_upgrade", "word_choice", "vague_wording"
    );
    private static final Set<String> GRAMMAR_TYPES = Set.of(
            "verb_pattern", "preposition", "tense", "collocation", "countability", "clarity"
    );

    private final GroqService groqService;

    public GroqCoachingEngine(GroqService groqService) {
        this.groqService = groqService;
    }

    @Override
    public String id() {
        return "groq";
    }

    @Override
    public List<CorrectionEvent> analyseUtterance(
            SpeakingSession session,
            String turnId,
            String studentText,
            UtteranceSource source
    ) {
        boolean hindiMode = session.getMode() == SpeakingMode.HINDI_TO_ENGLISH;
        String trimmed = studentText.trim();
        int tokens = trimmed.split("\\s+").length;
        if (!StringUtils.hasText(trimmed) || (!hindiMode && tokens < 4) || (hindiMode && trimmed.length() < 6)) {
            return List.of();
        }

        boolean fromSpeech = source == UtteranceSource.SPEECH;
        String context = recentContext(session);
        String alreadySuggested = session.getVocabulary().stream()
                .map(v -> v.getPhrase())
                .filter(StringUtils::hasText)
                .distinct()
                .limit(12)
                .collect(Collectors.joining("; "));

        String system = hindiMode
                ? hindiToEnglishCoachPrompt(alreadySuggested)
                : englishWordActivationPrompt(alreadySuggested, fromSpeech);

        String user = """
                Conversation topic: %s
                Recent context:
                %s

                Student utterance:
                \"\"\"
                %s
                \"\"\"
                """.formatted(
                StringUtils.hasText(session.getTopic()) ? session.getTopic() : "general",
                context,
                trimmed
        );

        try {
            String content = groqService.chatJsonFast(system, user, 0.25, 1100);
            JsonNode root = groqService.objectMapper().readTree(content);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<CorrectionEvent> out = new ArrayList<>();
            boolean haveUpgrade = false;
            boolean haveGrammar = false;
            for (JsonNode n : items) {
                if (n.path("likelySttError").asBoolean(false)) {
                    continue;
                }
                String issueType = n.path("issueType").asText("word_upgrade").toLowerCase(Locale.ROOT);
                boolean isUpgrade = WORD_UPGRADE_TYPES.contains(issueType);
                boolean isGrammar = GRAMMAR_TYPES.contains(issueType);
                if (!isUpgrade && !isGrammar) {
                    continue;
                }
                if (isUpgrade && haveUpgrade) {
                    continue;
                }
                if (isGrammar && haveGrammar) {
                    continue;
                }

                double confidence = n.path("confidence").asDouble(0);
                double minConfidence = hindiMode
                        ? 0.55
                        : (isUpgrade
                        ? (fromSpeech ? MIN_CONFIDENCE_WORD_UPGRADE_SPEECH : 0.55)
                        : (fromSpeech ? MIN_CONFIDENCE_SPEECH : MIN_CONFIDENCE_TYPED));
                if (confidence < minConfidence) {
                    continue;
                }

                String correction = n.path("correction").asText("").trim();
                String original = n.path("originalText").asText("").trim();
                if (original.isEmpty()) {
                    original = studentText.trim();
                }
                if (correction.isEmpty() || correction.equalsIgnoreCase(original)) {
                    continue;
                }
                // STT filter only for English grammar near-misses, not Hindi→English or richer rewrites
                if (!hindiMode && !isUpgrade && looksLikeSttArtifact(original, correction)) {
                    continue;
                }
                if (!hindiMode && fromSpeech && original.split("\\s+").length > 18 && !"clarity".equals(issueType)) {
                    continue;
                }

                String usefulPhrase = n.path("usefulPhrase").asText("").trim();
                if (usefulPhrase.isEmpty() && isUpgrade) {
                    usefulPhrase = pickUsefulPhrase(correction);
                }

                CorrectionEvent e = new CorrectionEvent();
                e.setId(UUID.randomUUID().toString());
                e.setTurnId(turnId);
                e.setOriginalText(original);
                e.setIssueType(issueType);
                e.setError(isGrammar && n.path("isError").asBoolean(true));
                e.setCorrection(correction);
                e.setNaturalAlternative(n.path("naturalAlternative").asText("").trim());
                e.setUsefulPhrase(usefulPhrase);
                e.setWhenToUse(n.path("whenToUse").asText("").trim());
                e.setExplanation(n.path("explanation").asText("").trim());
                e.setPriority(n.path("priority").asText(isUpgrade ? "high" : "medium"));
                e.setConfidence(confidence);
                e.setStatus("ready");
                e.setCreatedAt(Instant.now());
                out.add(e);
                if (isUpgrade) {
                    haveUpgrade = true;
                }
                if (isGrammar) {
                    haveGrammar = true;
                }
                if (out.size() >= 2) {
                    break;
                }
            }
            // Prefer showing word upgrades first
            out.sort((a, b) -> Boolean.compare(
                    !WORD_UPGRADE_TYPES.contains(a.getIssueType()),
                    !WORD_UPGRADE_TYPES.contains(b.getIssueType())
            ));
            return out;
        } catch (Exception ex) {
            CorrectionEvent failed = new CorrectionEvent();
            failed.setId(UUID.randomUUID().toString());
            failed.setTurnId(turnId);
            failed.setOriginalText(studentText.trim());
            failed.setIssueType("other");
            failed.setError(false);
            failed.setCorrection("");
            failed.setNaturalAlternative("");
            failed.setExplanation("Coaching unavailable for this turn: " + ex.getMessage());
            failed.setPriority("low");
            failed.setConfidence(0);
            failed.setStatus("failed");
            failed.setCreatedAt(Instant.now());
            return List.of(failed);
        }
    }

    private static String hindiToEnglishCoachPrompt(String alreadySuggested) {
        return """
                The student expressed a full idea in Hindi. The live coach already gave English.
                Your job: pick ONE useful English chunk from that idea for the phrase bank.

                Respond ONLY as JSON:
                {
                  "items": [
                    {
                      "originalText": "short Hindi (or Roman Hindi) bit, or short English gloss of that bit",
                      "issueType": "word_upgrade",
                      "isError": false,
                      "correction": "natural English for that idea (one sentence max)",
                      "naturalAlternative": "optional second English phrasing",
                      "usefulPhrase": "2–7 word English chunk to reuse when speaking",
                      "whenToUse": "when to use this in English conversations",
                      "explanation": "one short tip",
                      "priority": "high",
                      "confidence": 0.8,
                      "likelySttError": false
                    }
                  ]
                }

                Rules:
                - Return 0–1 items. Prefer a high-value reusable phrase over a full re-translation.
                - Do not coach Hindi grammar. Do not treat Hindi as an English error.
                - If the Hindi looks too garbled to trust, return {"items":[]}.
                - Avoid phrases already suggested: %s
                """.formatted(alreadySuggested.isBlank() ? "(none yet)" : alreadySuggested);
    }

    private static String englishWordActivationPrompt(String alreadySuggested, boolean fromSpeech) {
        return """
                You coach an advanced Indian English speaker who already knows many words but,
                under speaking pressure, falls back to simple generic wording (very, really, good,
                bad, nice, thing, stuff, problem, get, make, a lot, basically, somehow, etc.).
                Your MAIN job is WORD ACTIVATION: help them use more precise words and natural
                chunks they likely already recognise but did not retrieve in the moment.

                Respond ONLY as JSON:
                {
                  "items": [
                    {
                      "originalText": "short phrase the student actually said",
                      "issueType": "word_upgrade|word_choice|vague_wording|verb_pattern|preposition|tense|collocation|countability|clarity",
                      "isError": false,
                      "correction": "same idea with sharper wording (keep their voice; do not over-formalise)",
                      "naturalAlternative": "one other natural option, or empty string",
                      "usefulPhrase": "the key reusable chunk to remember (2–7 words)",
                      "whenToUse": "one short line: when to reach for this phrase",
                      "explanation": "why this wording lands better — 1 sentence",
                      "priority": "high|medium|low",
                      "confidence": 0.0,
                      "likelySttError": false
                    }
                  ]
                }

                Priority order (important):
                1) word_upgrade / word_choice / vague_wording — DEFAULT focus
                2) Only add a grammar item if there is a clear real error (tense, verb pattern, etc.)

                Word-upgrade rules:
                - Prefer upgrading vague/generic wording that weakens the point.
                - usefulPhrase must be something they can reuse in a later conversation.
                - Keep meaning identical. Do NOT force rare/academic vocabulary.
                - Prefer natural spoken English chunks over fancy synonyms.
                - Set isError=false for word upgrades (they are not mistakes).
                - Quote a SHORT originalText (the vague bit), not the whole paragraph.

                Other rules:
                - Return 1 item normally; at most 2 (one word upgrade + one grammar if needed).
                - If wording is already precise enough, return {"items":[]}.
                - Ignore speech-to-text mishears. Set likelySttError=true.
                - Avoid suggesting phrases already listed as previously suggested: %s
                """.formatted(alreadySuggested.isBlank() ? "(none yet)" : alreadySuggested)
                + (fromSpeech
                ? "\nThis utterance came from LIVE SPEECH. Skip anything that could be a mishear."
                : "\nThis utterance was TYPED, so wording is intentional.");
    }

    private static String pickUsefulPhrase(String correction) {
        String[] words = correction.trim().split("\\s+");
        if (words.length <= 7) {
            return correction.trim();
        }
        return String.join(" ", java.util.Arrays.copyOfRange(words, 0, 7));
    }

    /**
     * Heuristic filter for common Web-Speech mishears the model still tries to "correct".
     */
    static boolean looksLikeSttArtifact(String original, String correction) {
        String o = normalize(original);
        String c = normalize(correction);
        if (o.isEmpty() || c.isEmpty()) {
            return true;
        }
        String[] ow = o.split("\\s+");
        String[] cw = c.split("\\s+");

        if (ow.length >= 2 && cw.length == 1 && String.join("", ow).equals(cw[0])) {
            return true;
        }
        if (ow.length == cw.length && ow.length <= 4) {
            int diffs = 0;
            int dist = 0;
            for (int i = 0; i < ow.length; i++) {
                if (!ow[i].equals(cw[i])) {
                    diffs++;
                    dist += editDistance(ow[i], cw[i]);
                }
            }
            if (diffs == 1 && dist <= 2) {
                return true;
            }
        }
        if (ow.length == 1 && cw.length == 1) {
            String a = ow[0];
            String b = cw[0];
            if (a.length() <= 12 && b.length() <= 12 && editDistance(a, b) <= 3) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z\\s']", " ").replaceAll("\\s+", " ").trim();
    }

    private static int editDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private static String recentContext(SpeakingSession session) {
        List<SpeakingTurn> turns = session.getTurns();
        int from = Math.max(0, turns.size() - 8);
        return turns.subList(from, turns.size()).stream()
                .filter(t -> t.getRole() != TurnRole.SYSTEM)
                .map(t -> (t.getRole() == TurnRole.STUDENT ? "Student: " : "AI: ") + t.getText())
                .collect(Collectors.joining("\n"));
    }
}
