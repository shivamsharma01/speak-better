package com.speakbetter.dto;

import java.util.List;

public record ClinicalSummary(
        String chiefComplaint,
        List<String> keyPoints,
        String assessment,
        String plan,
        String followUp,
        List<String> medicationsMentioned
) {}
