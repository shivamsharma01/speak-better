package com.speakbetter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Provider IDs are swappable without changing session/API contracts.
 * Free POC defaults: conversation+coaching = groq, STT/TTS = browser (frontend).
 */
@ConfigurationProperties(prefix = "speaking")
public record SpeakingProperties(
        String conversationProvider,
        String coachingProvider,
        String dataDir,
        int maxContextTurns
) {
    public SpeakingProperties {
        if (conversationProvider == null || conversationProvider.isBlank()) {
            conversationProvider = "groq";
        }
        if (coachingProvider == null || coachingProvider.isBlank()) {
            coachingProvider = "groq";
        }
        if (dataDir == null || dataDir.isBlank()) {
            dataDir = "./data/speaking";
        }
        if (maxContextTurns <= 0) {
            maxContextTurns = 16;
        }
    }
}
