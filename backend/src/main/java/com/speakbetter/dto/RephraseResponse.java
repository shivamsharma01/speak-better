package com.speakbetter.dto;

import java.util.List;

public record RephraseResponse(
        List<Suggestion> suggestions,
        String modelAnswer,
        ImprovementFeedback improvements
) {}
