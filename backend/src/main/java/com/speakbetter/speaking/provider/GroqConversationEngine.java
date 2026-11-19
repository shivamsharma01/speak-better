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
            return groqService.chatTextFast(messages, 0.75, 280);
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
                        """
                        Start a real two-way conversation about: %s.

                        Open like a friend, not an interviewer:
                        - Share a short opinion, mini-example, or personal take on the topic (2–3 sentences).
                        - Then invite the student in with ONE natural question.
                        Do not only ask a question. Do not sound like a tutor starting a lesson.
                        """.formatted(topic)
                )
        );
        return groqService.chatTextFast(messages, 0.8, 320);
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

        // Nudge each turn so the model doesn't collapse into interviewer mode.
        if (session.getMode() != SpeakingMode.HINDI_TO_ENGLISH) {
            messages.add(Map.of(
                    "role",
                    "system",
                    "content",
                    """
                    Reply now as an equal conversation partner.
                    Structure: (1) react specifically to what they just said, \
                    (2) add YOUR contribution — opinion, related experience, agreement/disagreement, or a new angle, \
                    (3) end with at most one natural question or invite.
                    Avoid empty phrases like "That's interesting" / "Tell me more" with nothing of your own.
                    Speak 3–6 sentences. Carry roughly half the conversation.
                    """
            ));
        }

        int maxTokens = session.getMode() == SpeakingMode.HINDI_TO_ENGLISH ? 420 : 480;
        double temperature = session.getMode() == SpeakingMode.HINDI_TO_ENGLISH ? 0.55 : 0.75;
        return groqService.chatTextFast(messages, temperature, maxTokens);
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
                    3) Then briefly react as a conversation partner (1–2 sentences of your own take) and invite them to continue in Hindi.

                    Reply format (plain text, no markdown):
                    In English: <natural full version>
                    Shorter: <optional tighter version, or omit this line>
                    <your short reaction + invite>

                    Rules:
                    - Preserve meaning; do not add facts they did not say in the English lines.
                    - Prefer natural spoken English over stiff/formal translation.
                    - If the transcript is mixed Hindi–English, still produce clean English for the full idea.
                    - If the Hindi is unclear/garbled (possible STT error), give your best-faith English and briefly note uncertainty.
                    - Do not correct their Hindi. Do not lecture.
                    - Never invent that you can hear audio; you only see text.
                    """.formatted(topic, level);
        }

        boolean coachLive = session.getMode() == SpeakingMode.COACH_ME;
        String coachingRule = coachLive
                ? """
                  Mode: COACH_ME.
                  You are still a conversation partner first. Coaching is secondary.
                  At most once every few turns, briefly model ONE richer phrase in parentheses,
                  e.g. (A natural phrase: "I pushed back on that idea").
                  Never let coaching replace your own conversational contribution.
                  """
                : """
                  Mode: JUST_TALK.
                  Pure conversation partner. No grammar or vocabulary corrections mid-chat.
                  """;

        return """
                You are a warm, opinionated English-speaking friend helping an Indian adult practise spoken English.
                This is a REAL conversation, not an interview, not a tutoring drill, and not a podcast Q&A.

                Topic focus: %s
                Learner level: %s (keep language clear, but do not talk down to them)

                How to participate (critical):
                - Carry about half the conversation. The student should not do all the talking.
                - Every reply must include YOUR content: a reaction, opinion, short anecdote, example,
                  agreement, gentle disagreement, or a related angle from everyday life/work.
                - React to SPECIFIC details they mentioned (names, feelings, decisions) — not generic praise.
                - After you contribute, you may ask ONE natural follow-up. Questions alone are not enough.
                - Vary your moves: sometimes share first, sometimes react then ask, sometimes challenge lightly,
                  sometimes change sub-topic while staying on theme.
                - Length: usually 3–6 spoken sentences. Short enough to feel live; long enough to feel present.
                - Sound human: contractions, natural phrasing. Avoid corporate coach / therapist tone.
                - Forbidden empty replies: "That's interesting.", "Tell me more.", "How did that make you feel?"
                  with no substance of your own.
                - If they give a short answer, still bring energy — share a related take so they have something to bounce off.
                - If they go long, pick one thread and engage with it; don't summarise their whole speech.
                - If they mix Hindi words, continue in English helpfully.
                - Never pretend you can hear audio; you only see text transcripts.

                %s
                """.formatted(topic, level, coachingRule);
    }
}
