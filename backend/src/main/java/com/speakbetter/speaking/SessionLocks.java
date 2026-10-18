package com.speakbetter.speaking;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionLocks {

    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public Object forSession(String sessionId) {
        return locks.computeIfAbsent(sessionId, k -> new Object());
    }
}
