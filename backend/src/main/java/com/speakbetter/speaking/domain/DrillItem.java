package com.speakbetter.speaking.domain;

/** One Hinglish prompt the student should express in English. */
public class DrillItem {
    private String id;
    private int index;
    /** Speaker label for chat drills (e.g. Rahul / Priya); null for sentence drills. */
    private String speaker;
    private String hinglish;
    /** Model English reference — used for evaluation, may be shown after attempt. */
    private String modelEnglish;
    private String status; // pending | done | skipped

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getSpeaker() {
        return speaker;
    }

    public void setSpeaker(String speaker) {
        this.speaker = speaker;
    }

    public String getHinglish() {
        return hinglish;
    }

    public void setHinglish(String hinglish) {
        this.hinglish = hinglish;
    }

    public String getModelEnglish() {
        return modelEnglish;
    }

    public void setModelEnglish(String modelEnglish) {
        this.modelEnglish = modelEnglish;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
