package com.speakbetter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient groqWebClient(GroqProperties props) {
        return WebClient.builder()
                .baseUrl(props.baseUrl())
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .build();
    }
}
