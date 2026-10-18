package com.speakbetter.speaking.domain;

import java.time.Instant;

/** A useful phrase/word upgrade for the learner to reuse later. */
public class VocabularyItem {
    private String id;
    private String phrase;
    private String insteadOf;
    private String whenToUse;
    private String turnId;
    private int seenCount;
    private Instant createdAt;
    private Instant lastSeenAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPhrase() {
        return phrase;
    }

    public void setPhrase(String phrase) {
        this.phrase = phrase;
    }

    public String getInsteadOf() {
        return insteadOf;
    }

    public void setInsteadOf(String insteadOf) {
        this.insteadOf = insteadOf;
    }

    public String getWhenToUse() {
        return whenToUse;
    }

    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
    }

    public String getTurnId() {
        return turnId;
    }

    public void setTurnId(String turnId) {
        this.turnId = turnId;
    }

    public int getSeenCount() {
        return seenCount;
    }

    public void setSeenCount(int seenCount) {
        this.seenCount = seenCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
