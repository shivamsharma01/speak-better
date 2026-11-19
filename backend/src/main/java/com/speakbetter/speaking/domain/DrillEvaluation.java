package com.speakbetter.speaking.domain;

import java.time.Instant;

public class DrillEvaluation {
    private String id;
    private String drillItemId;
    private int drillIndex;
    private String studentEnglish;
    private String modelEnglish;
    private String hinglish;
    /** 1–5 overall how well meaning + natural English landed. */
    private int score;
    private boolean meaningPreserved;
    private String verdict;
    private String betterEnglish;
    private String usefulPhrase;
    private String tip;
    private Instant createdAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDrillItemId() {
        return drillItemId;
    }

    public void setDrillItemId(String drillItemId) {
        this.drillItemId = drillItemId;
    }

    public int getDrillIndex() {
        return drillIndex;
    }

    public void setDrillIndex(int drillIndex) {
        this.drillIndex = drillIndex;
    }

    public String getStudentEnglish() {
        return studentEnglish;
    }

    public void setStudentEnglish(String studentEnglish) {
        this.studentEnglish = studentEnglish;
    }

    public String getModelEnglish() {
        return modelEnglish;
    }

    public void setModelEnglish(String modelEnglish) {
        this.modelEnglish = modelEnglish;
    }

    public String getHinglish() {
        return hinglish;
    }

    public void setHinglish(String hinglish) {
        this.hinglish = hinglish;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isMeaningPreserved() {
        return meaningPreserved;
    }

    public void setMeaningPreserved(boolean meaningPreserved) {
        this.meaningPreserved = meaningPreserved;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getBetterEnglish() {
        return betterEnglish;
    }

    public void setBetterEnglish(String betterEnglish) {
        this.betterEnglish = betterEnglish;
    }

    public String getUsefulPhrase() {
        return usefulPhrase;
    }

    public void setUsefulPhrase(String usefulPhrase) {
        this.usefulPhrase = usefulPhrase;
    }

    public String getTip() {
        return tip;
    }

    public void setTip(String tip) {
        this.tip = tip;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
