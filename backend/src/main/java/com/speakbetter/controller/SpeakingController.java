package com.speakbetter.controller;

import com.speakbetter.speaking.SpeakingSessionService;
import com.speakbetter.speaking.domain.SpeakingSession;
import com.speakbetter.speaking.dto.CreateSessionRequest;
import com.speakbetter.speaking.dto.SubmitTurnRequest;
import com.speakbetter.speaking.dto.TurnResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/speaking")
public class SpeakingController {

    private final SpeakingSessionService speakingSessionService;

    public SpeakingController(SpeakingSessionService speakingSessionService) {
        this.speakingSessionService = speakingSessionService;
    }

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return speakingSessionService.providers();
    }

    @PostMapping("/sessions")
    public SpeakingSession create(@Valid @RequestBody CreateSessionRequest request) {
        return speakingSessionService.create(request);
    }

    @GetMapping("/sessions")
    public List<SpeakingSession> list(@RequestParam(defaultValue = "20") int limit) {
        return speakingSessionService.list(limit);
    }

    @GetMapping("/sessions/{id}")
    public SpeakingSession get(@PathVariable String id) {
        return speakingSessionService.get(id);
    }

    @PostMapping("/sessions/{id}/turns")
    public TurnResponse turn(@PathVariable String id, @Valid @RequestBody SubmitTurnRequest request) {
        return speakingSessionService.submitTurn(id, request.text(), request.resolvedSource());
    }

    @PostMapping("/sessions/{id}/end")
    public SpeakingSession end(@PathVariable String id) {
        return speakingSessionService.end(id);
    }
}
