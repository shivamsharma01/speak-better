package com.speakbetter.speaking.dto;

import com.speakbetter.speaking.domain.SpeakingTurn;

public record TurnResponse(
        String sessionId,
        SpeakingTurn studentTurn,
        SpeakingTurn assistantTurn,
        boolean coachingPending
) {}
