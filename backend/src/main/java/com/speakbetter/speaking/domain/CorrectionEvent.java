package com.speakbetter.speaking.domain;

import java.time.Instant;

public class CorrectionEvent {
    private String id;
    private String turnId;
    private String originalText;
    private String issueType;
    private boolean error;
    private String correction;
    private String naturalAlternative;
    private String explanation;
    private String priority;
    private double confidence;
    private String status; // pending | ready | skipped | failed
    /** Short reusable chunk for word upgrades (e.g. "get my point across"). */
    private String usefulPhrase;
    /** When/why to use the upgrade — one short line. */
    private String whenToUse;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTurnId() {
        return turnId;
    }

    public void setTurnId(String turnId) {
        this.turnId = turnId;
    }

    public String getOriginalText() {
        return originalText;
    }

    public void setOriginalText(String originalText) {
        this.originalText = originalText;
    }

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public boolean isError() {
        return error;
    }

    public void setError(boolean error) {
        this.error = error;
    }

    public String getCorrection() {
        return correction;
    }

    public void setCorrection(String correction) {
        this.correction = correction;
    }

    public String getNaturalAlternative() {
        return naturalAlternative;
    }

    public void setNaturalAlternative(String naturalAlternative) {
        this.naturalAlternative = naturalAlternative;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUsefulPhrase() {
        return usefulPhrase;
    }

    public void setUsefulPhrase(String usefulPhrase) {
        this.usefulPhrase = usefulPhrase;
    }

    public String getWhenToUse() {
        return whenToUse;
    }

    public void setWhenToUse(String whenToUse) {
        this.whenToUse = whenToUse;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
