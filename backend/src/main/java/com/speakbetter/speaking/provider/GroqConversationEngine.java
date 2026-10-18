package com.speakbetter.speaking.provider;

import com.speakbetter.config.SpeakingProperties;
import com.speakbetter.service.GroqService;
import com.speakbetter.speaking.domain.SpeakingMode;
import com.speakbetter.speaking.domain.SpeakingSession;
import com.speakbetter.speaking.domain.SpeakingTurn;
import com.speakbetter.speaking.domain.TurnRole;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class GroqConversationEngine implements ConversationEngine {

    private final GroqService groqService;
    private final SpeakingProperties speakingProperties;

    public GroqConversationEngine(GroqService groqService, SpeakingProperties speakingProperties) {
        this.groqService = groqService;
        this.speakingProperties = speakingProperties;
    }

    @Override
    public String id() {
        return "groq";
    }

    @Override
    public String openingMessage(SpeakingSession session) {
        if (session.getMode() == SpeakingMode.HINDI_TO_ENGLISH) {
            String topic = StringUtils.hasText(session.getTopic())
                    ? session.getTopic().trim()
                    : "anything on your mind";
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt(session)),
                    Map.of(
                            "role",
                            "user",
                            "content",
                            "Give a short opening (2–3 sentences) for Hindi→English practice about: "
                                    + topic
                                    + ". Invite the student to say a complete thought in Hindi."
                    )
            );
            return groqService.chatTextFast(messages, 0.7, 260);
        }

        String topic = StringUtils.hasText(session.getTopic())
                ? session.getTopic().trim()
                : "everyday life";
        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt(session)),
                Map.of(
                        "role",
                        "user",
                        "content",
                        "Start the conversation now about: " + topic
                                + ". Give a short warm opening (1–2 sentences) and one easy open question."
                )
        );
        return groqService.chatTextFast(messages, 0.7, 220);
    }

    @Override
    public String reply(SpeakingSession session, String latestStudentText) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(session)));

        int max = speakingProperties.maxContextTurns();
        List<SpeakingTurn> turns = session.getTurns();
        int from = Math.max(0, turns.size() - max);
        for (int i = from; i < turns.size(); i++) {
            SpeakingTurn t = turns.get(i);
            if (t.getRole() == TurnRole.SYSTEM) {
                continue;
            }
            Map<String, String> msg = new HashMap<>();
            msg.put("role", t.getRole() == TurnRole.STUDENT ? "user" : "assistant");
            msg.put("content", t.getText());
            messages.add(msg);
        }
        if (turns.isEmpty() || !latestStudentText.equals(turns.get(turns.size() - 1).getText())) {
            messages.add(Map.of("role", "user", "content", latestStudentText));
        }

        int maxTokens = session.getMode() == SpeakingMode.HINDI_TO_ENGLISH ? 420 : 320;
        return groqService.chatTextFast(messages, 0.55, maxTokens);
    }

    private static String systemPrompt(SpeakingSession session) {
        String topic = StringUtils.hasText(session.getTopic()) ? session.getTopic().trim() : "everyday English";
        String level = StringUtils.hasText(session.getLevel()) ? session.getLevel().trim() : "intermediate";

        if (session.getMode() == SpeakingMode.HINDI_TO_ENGLISH) {
            return """
                    You help an Indian adult express ideas in English.
                    Mode: HINDI_TO_ENGLISH.
                    Topic focus: %s
                    Learner level: %s

                    The student will say a COMPLETE thought in Hindi (Devanagari or Roman Hindi / Hinglish).
                    Your job on EVERY student turn:
                    1) Give the same idea in natural spoken English (primary answer — this will be read aloud).
                    2) Optionally give one shorter alternative English line if useful.
                    3) End with one short invite to continue (they may answer again in Hindi).

                    Reply format (keep it tight, plain text, no markdown):
                    In English: <natural full version>
                    Shorter: <optional tighter version, or omit this line>
                    <one short follow-up invite>

                    Rules:
                    - Preserve meaning; do not add facts they did not say.
                    - Prefer natural spoken English over stiff/formal translation.
                    - If the transcript is mixed Hindi–English, still produce clean English for the full idea.
                    - If the Hindi is unclear/garbled (possible STT error), give your best-faith English and briefly note uncertainty in one short clause.
                    - Do not correct their Hindi. Do not lecture. Do not ask them to repeat in English unless they want to.
                    - Never invent that you can hear audio; you only see text.
                    """.formatted(topic, level);
        }

        boolean coachLive = session.getMode() == SpeakingMode.COACH_ME;
        String coachingRule = coachLive
                ? """
                  Mode: COACH_ME.
                  Keep the conversation natural. At most once every few turns, briefly model ONE richer
                  phrase the student could have used, in parentheses, e.g.
                  (A natural phrase here: "I pushed back on that idea").
                  Focus on word choice / useful chunks, not grammar lectures.
                  Never give more than one parenthetical tip per reply. Detailed notes are handled separately.
                  """
                : """
                  Mode: JUST_TALK.
                  Do not correct grammar or vocabulary while speaking. Be a friendly conversation partner only.
                  """;

        return """
                You are an AI speaking partner helping an Indian adult learner practise spoken English.
                Topic focus: %s
                Learner level: %s

                Rules:
                - Speak in clear, natural English. Keep replies short (1–3 sentences) so turn-taking feels live.
                - Ask one relevant follow-up question most of the time.
                - Follow the student's lead; do not force a rigid script.
                - If the student mixes Hindi words, respond in English and continue helpfully.
                - Never invent that you can hear audio; you only see text transcripts.

                %s
                """.formatted(topic, level, coachingRule);
    }
}
