package com.speakbetter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
public record GroqProperties(
        String apiKey,
        String baseUrl,
        String sttModel,
        /** Best quality — use sparingly (lower TPM on free tier). */
        String chatModel,
        /** Lighter model for refine / summary / practice flows (separate TPM bucket). */
        String chatModelFast
) {}
