package com.speakbetter.speaking.store;

import com.speakbetter.speaking.domain.SpeakingSession;

import java.util.List;
import java.util.Optional;

public interface SpeakingSessionRepository {

    void save(SpeakingSession session);

    Optional<SpeakingSession> findById(String id);

    List<SpeakingSession> listRecent(int limit);
}
