package com.speakbetter.controller;

import com.speakbetter.dto.RephraseRequest;
import com.speakbetter.dto.RephraseResponse;
import com.speakbetter.dto.TranscriptionResponse;
import com.speakbetter.service.GroqService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:4200")
public class SpeechController {

    private final GroqService groqService;

    public SpeechController(GroqService groqService) {
        this.groqService = groqService;
    }

    @PostMapping(value = "/transcribe", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false) String language
    ) {
        return groqService.transcribe(audio, language);
    }

    @PostMapping(value = "/rephrase", consumes = MediaType.APPLICATION_JSON_VALUE)
    public RephraseResponse rephrase(@Valid @RequestBody RephraseRequest request) {
        return groqService.rephrase(
                request.text(),
                request.language(),
                request.tone(),
                request.question(),
                request.responseLanguage()
        );
    }
}
