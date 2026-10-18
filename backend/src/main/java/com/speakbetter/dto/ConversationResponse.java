package com.speakbetter.dto;

import java.util.List;

public record ConversationResponse(
        String rawTranscript,
        List<ConversationTurn> turns,
        ClinicalSummary summaryEnglish,
        ClinicalSummary summaryHindi
) {}
