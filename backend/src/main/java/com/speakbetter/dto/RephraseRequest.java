package com.speakbetter.dto;

import jakarta.validation.constraints.NotBlank;

public record RephraseRequest(
        @NotBlank String text,
        String language,
        String tone,
        String question,
        /** Output language for coaching JSON: {@code "en"} (default) or {@code "same"} as transcript. */
        String responseLanguage
) {}
