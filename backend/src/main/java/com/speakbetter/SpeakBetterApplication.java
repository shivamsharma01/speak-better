package com.speakbetter;

import com.speakbetter.config.GroqProperties;
import com.speakbetter.config.SpeakingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({GroqProperties.class, SpeakingProperties.class})
public class SpeakBetterApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeakBetterApplication.class, args);
    }
}
