package com.speakbetter.speaking.dto;

import com.speakbetter.speaking.domain.SpeakingMode;
import jakarta.validation.constraints.NotBlank;

public record CreateSessionRequest(
        @NotBlank String topic,
        SpeakingMode mode,
        String level
) {
    public SpeakingMode resolvedMode() {
        return mode != null ? mode : SpeakingMode.JUST_TALK;
    }

    public String resolvedLevel() {
        return level != null && !level.isBlank() ? level.trim() : "intermediate";
    }
}
