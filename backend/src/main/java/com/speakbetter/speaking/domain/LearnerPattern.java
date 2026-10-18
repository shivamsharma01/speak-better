package com.speakbetter.speaking.domain;

import java.time.Instant;

public class LearnerPattern {
    private String issueType;
    private String exampleOriginal;
    private String exampleCorrection;
    private int count;
    private Instant lastSeenAt;

    public String getIssueType() {
        return issueType;
    }

    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getExampleOriginal() {
        return exampleOriginal;
    }

    public void setExampleOriginal(String exampleOriginal) {
        this.exampleOriginal = exampleOriginal;
    }

    public String getExampleCorrection() {
        return exampleCorrection;
    }

    public void setExampleCorrection(String exampleCorrection) {
        this.exampleCorrection = exampleCorrection;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public Instant getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Instant lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }
}
