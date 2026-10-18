package com.speakbetter.speaking;

import com.speakbetter.speaking.domain.CorrectionEvent;
import com.speakbetter.speaking.domain.LearnerPattern;
import com.speakbetter.speaking.domain.SpeakingSession;
import com.speakbetter.speaking.domain.SpeakingTurn;
import com.speakbetter.speaking.domain.UtteranceSource;
import com.speakbetter.speaking.domain.VocabularyItem;
import com.speakbetter.speaking.provider.ProviderRegistry;
import com.speakbetter.speaking.store.SpeakingSessionRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Separate bean so {@code @Async} is applied via Spring proxy (not self-invocation).
 * Keeps coaching off the spoken-reply critical path.
 */
@Component
public class CoachingAsyncWorker {

    private static final Set<String> WORD_UPGRADE_TYPES = Set.of(
            "word_upgrade", "word_choice", "vague_wording"
    );

    private final SpeakingSessionRepository repository;
    private final ProviderRegistry providers;
    private final SessionLocks sessionLocks;

    public CoachingAsyncWorker(
            SpeakingSessionRepository repository,
            ProviderRegistry providers,
            SessionLocks sessionLocks
    ) {
        this.repository = repository;
        this.providers = providers;
        this.sessionLocks = sessionLocks;
    }

    @Async
    public void analyse(String sessionId, String turnId, String studentText, UtteranceSource source) {
        List<CorrectionEvent> results;
        try {
            SpeakingSession snapshot = repository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            UtteranceSource resolved = source != null ? source : resolveSource(snapshot, turnId);
            results = providers.coaching().analyseUtterance(snapshot, turnId, studentText, resolved);
        } catch (Exception e) {
            CorrectionEvent failed = new CorrectionEvent();
            failed.setId(UUID.randomUUID().toString());
            failed.setTurnId(turnId);
            failed.setOriginalText(studentText);
            failed.setStatus("failed");
            failed.setExplanation(e.getMessage());
            failed.setCreatedAt(Instant.now());
            results = List.of(failed);
        }

        synchronized (sessionLocks.forSession(sessionId)) {
            SpeakingSession session = repository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            session.getCorrections().removeIf(c ->
                    turnId.equals(c.getTurnId()) && "pending".equals(c.getStatus()));
            if (results.isEmpty()) {
                CorrectionEvent skipped = new CorrectionEvent();
                skipped.setId(UUID.randomUUID().toString());
                skipped.setTurnId(turnId);
                skipped.setOriginalText(studentText);
                skipped.setStatus("skipped");
                skipped.setExplanation("No high-value wording tip for this turn.");
                skipped.setCreatedAt(Instant.now());
                session.getCorrections().add(skipped);
            } else {
                for (CorrectionEvent event : results) {
                    session.getCorrections().add(event);
                    if (!"ready".equals(event.getStatus()) || !StringUtils.hasText(event.getIssueType())) {
                        continue;
                    }
                    mergePattern(session, event);
                    if (WORD_UPGRADE_TYPES.contains(event.getIssueType().toLowerCase(Locale.ROOT))) {
                        mergeVocabulary(session, event);
                    }
                }
            }
            repository.save(session);
        }
    }

    private static UtteranceSource resolveSource(SpeakingSession session, String turnId) {
        return session.getTurns().stream()
                .filter(t -> turnId.equals(t.getId()))
                .map(SpeakingTurn::getSource)
                .findFirst()
                .orElse(UtteranceSource.TYPED);
    }

    private static void mergePattern(SpeakingSession session, CorrectionEvent event) {
        String type = event.getIssueType();
        LearnerPattern match = session.getPatterns().stream()
                .filter(p -> type.equals(p.getIssueType()))
                .findFirst()
                .orElse(null);
        if (match == null) {
            match = new LearnerPattern();
            match.setIssueType(type);
            match.setCount(0);
            session.getPatterns().add(match);
        }
        match.setCount(match.getCount() + 1);
        match.setExampleOriginal(event.getOriginalText());
        match.setExampleCorrection(
                StringUtils.hasText(event.getUsefulPhrase()) ? event.getUsefulPhrase() : event.getCorrection()
        );
        match.setLastSeenAt(Instant.now());
    }

    private static void mergeVocabulary(SpeakingSession session, CorrectionEvent event) {
        String phrase = StringUtils.hasText(event.getUsefulPhrase())
                ? event.getUsefulPhrase().trim()
                : event.getCorrection().trim();
        if (!StringUtils.hasText(phrase)) {
            return;
        }
        String key = phrase.toLowerCase(Locale.ROOT);
        VocabularyItem existing = session.getVocabulary().stream()
                .filter(v -> v.getPhrase() != null && v.getPhrase().toLowerCase(Locale.ROOT).equals(key))
                .findFirst()
                .orElse(null);
        Instant now = Instant.now();
        if (existing == null) {
            VocabularyItem item = new VocabularyItem();
            item.setId(UUID.randomUUID().toString());
            item.setPhrase(phrase);
            item.setInsteadOf(event.getOriginalText());
            item.setWhenToUse(event.getWhenToUse());
            item.setTurnId(event.getTurnId());
            item.setSeenCount(1);
            item.setCreatedAt(now);
            item.setLastSeenAt(now);
            session.getVocabulary().add(item);
        } else {
            existing.setSeenCount(existing.getSeenCount() + 1);
            existing.setLastSeenAt(now);
            if (StringUtils.hasText(event.getWhenToUse())) {
                existing.setWhenToUse(event.getWhenToUse());
            }
        }
    }
}
