package com.speakbetter.speaking.domain;

import java.time.Instant;

public class SpeakingTurn {
    private String id;
    private TurnRole role;
    private String text;
    private UtteranceSource source;
    private Instant createdAt;
    private boolean finalised = true;

    public SpeakingTurn() {}

    public SpeakingTurn(String id, TurnRole role, String text, UtteranceSource source, Instant createdAt) {
        this.id = id;
        this.role = role;
        this.text = text;
        this.source = source;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TurnRole getRole() {
        return role;
    }

    public void setRole(TurnRole role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public UtteranceSource getSource() {
        return source;
    }

    public void setSource(UtteranceSource source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isFinalised() {
        return finalised;
    }

    public void setFinalised(boolean finalised) {
        this.finalised = finalised;
    }
}
