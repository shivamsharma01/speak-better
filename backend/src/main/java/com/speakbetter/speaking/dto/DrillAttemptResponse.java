package com.speakbetter.speaking.dto;

import com.speakbetter.speaking.domain.DrillEvaluation;
import com.speakbetter.speaking.domain.DrillItem;
import com.speakbetter.speaking.domain.SpeakingTurn;

import java.util.List;

public record DrillAttemptResponse(
        String sessionId,
        SpeakingTurn studentTurn,
        DrillEvaluation evaluation,
        DrillItem nextItem,
        int currentDrillIndex,
        boolean drillComplete,
        List<DrillEvaluation> evaluations
) {}
