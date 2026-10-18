package com.speakbetter.controller;

import com.speakbetter.dto.ConversationResponse;
import com.speakbetter.service.ConversationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/conversation")
@CrossOrigin(origins = "http://localhost:4200")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversationResponse process(
            @RequestPart("audio") MultipartFile audio,
            @RequestParam(value = "language", required = false) String language
    ) {
        return conversationService.process(audio, language);
    }
}
