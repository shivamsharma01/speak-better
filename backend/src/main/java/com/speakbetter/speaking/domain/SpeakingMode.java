package com.speakbetter.speaking.domain;

public enum SpeakingMode {
    JUST_TALK,
    COACH_ME,
    /** Student speaks Hindi; coach returns the same idea in natural English. */
    HINDI_TO_ENGLISH,
    /** Show Hinglish sentence; student says it in English; evaluate. */
    HINGLISH_SENTENCE,
    /** Show Hinglish chat between two people; student says each turn in English; evaluate. */
    HINGLISH_CHAT
}
