package com.speakbetter.dto;

public record TranscriptionResponse(
        String text,
        String language,
        String textHindi,
        String textEnglish
) {}
