package com.speakbetter.speaking.provider;

import com.speakbetter.speaking.domain.CorrectionEvent;
import com.speakbetter.speaking.domain.SpeakingSession;
import com.speakbetter.speaking.domain.UtteranceSource;

import java.util.List;

/**
 * Analyses a finalised student utterance asynchronously.
 * Must never be on the critical path of the spoken reply.
 */
public interface CoachingEngine {

    String id();

    List<CorrectionEvent> analyseUtterance(
            SpeakingSession session,
            String turnId,
            String studentText,
            UtteranceSource source
    );
}
