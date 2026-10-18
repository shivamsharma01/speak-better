package com.speakbetter.speaking.provider;

import com.speakbetter.speaking.domain.SpeakingSession;

/**
 * Produces the AI partner's next spoken reply.
 * Swap implementations via {@code speaking.conversation-provider}.
 */
public interface ConversationEngine {

    String id();

    String openingMessage(SpeakingSession session);

    String reply(SpeakingSession session, String latestStudentText);
}
