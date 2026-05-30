package com.speakbetter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "groq")
public record GroqProperties(
        String apiKey,
        String baseUrl,
        String sttModel,
        String chatModel
) {}
