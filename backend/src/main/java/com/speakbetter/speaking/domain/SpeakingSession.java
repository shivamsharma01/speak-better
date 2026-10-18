package com.speakbetter.speaking.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class SpeakingSession {
    private String id;
    private SpeakingMode mode;
    private String topic;
    private String level;
    private String status; // active | ended
    private Instant createdAt;
    private Instant endedAt;
    private String conversationProvider;
    private String coachingProvider;
    private List<SpeakingTurn> turns = new ArrayList<>();
    private List<CorrectionEvent> corrections = new ArrayList<>();
    private List<LearnerPattern> patterns = new ArrayList<>();
    private List<VocabularyItem> vocabulary = new ArrayList<>();
    private String reviewSummary;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public SpeakingMode getMode() {
        return mode;
    }

    public void setMode(SpeakingMode mode) {
        this.mode = mode;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public String getConversationProvider() {
        return conversationProvider;
    }

    public void setConversationProvider(String conversationProvider) {
        this.conversationProvider = conversationProvider;
    }

    public String getCoachingProvider() {
        return coachingProvider;
    }

    public void setCoachingProvider(String coachingProvider) {
        this.coachingProvider = coachingProvider;
    }

    public List<SpeakingTurn> getTurns() {
        return turns;
    }

    public void setTurns(List<SpeakingTurn> turns) {
        this.turns = turns != null ? turns : new ArrayList<>();
    }

    public List<CorrectionEvent> getCorrections() {
        return corrections;
    }

    public void setCorrections(List<CorrectionEvent> corrections) {
        this.corrections = corrections != null ? corrections : new ArrayList<>();
    }

    public List<LearnerPattern> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<LearnerPattern> patterns) {
        this.patterns = patterns != null ? patterns : new ArrayList<>();
    }

    public List<VocabularyItem> getVocabulary() {
        return vocabulary;
    }

    public void setVocabulary(List<VocabularyItem> vocabulary) {
        this.vocabulary = vocabulary != null ? vocabulary : new ArrayList<>();
    }

    public String getReviewSummary() {
        return reviewSummary;
    }

    public void setReviewSummary(String reviewSummary) {
        this.reviewSummary = reviewSummary;
    }
}
