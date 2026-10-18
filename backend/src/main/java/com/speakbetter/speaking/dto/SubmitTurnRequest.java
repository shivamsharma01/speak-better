package com.speakbetter.speaking.dto;

import com.speakbetter.speaking.domain.UtteranceSource;
import jakarta.validation.constraints.NotBlank;

public record SubmitTurnRequest(
        @NotBlank String text,
        UtteranceSource source
) {
    public UtteranceSource resolvedSource() {
        return source != null ? source : UtteranceSource.TYPED;
    }
}
