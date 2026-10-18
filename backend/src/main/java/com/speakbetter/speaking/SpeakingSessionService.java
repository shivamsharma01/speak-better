package com.speakbetter.speaking;

import com.speakbetter.speaking.domain.CorrectionEvent;
import com.speakbetter.speaking.domain.LearnerPattern;
import com.speakbetter.speaking.domain.SpeakingSession;
import com.speakbetter.speaking.domain.SpeakingTurn;
import com.speakbetter.speaking.domain.TurnRole;
import com.speakbetter.speaking.domain.UtteranceSource;
import com.speakbetter.speaking.dto.CreateSessionRequest;
import com.speakbetter.speaking.dto.TurnResponse;
import com.speakbetter.speaking.provider.CoachingEngine;
import com.speakbetter.speaking.provider.ConversationEngine;
import com.speakbetter.speaking.provider.ProviderRegistry;
import com.speakbetter.speaking.store.SpeakingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SpeakingSessionService {

    private final SpeakingSessionRepository repository;
    private final ProviderRegistry providers;
    private final CoachingAsyncWorker coachingAsyncWorker;
    private final SessionLocks sessionLocks;

    public SpeakingSessionService(
            SpeakingSessionRepository repository,
            ProviderRegistry providers,
            CoachingAsyncWorker coachingAsyncWorker,
            SessionLocks sessionLocks
    ) {
        this.repository = repository;
        this.providers = providers;
        this.coachingAsyncWorker = coachingAsyncWorker;
        this.sessionLocks = sessionLocks;
    }

    public Map<String, Object> providers() {
        return providers.describe();
    }

    public SpeakingSession create(CreateSessionRequest request) {
        ConversationEngine conversation = providers.conversation();
        CoachingEngine coaching = providers.coaching();

        SpeakingSession session = new SpeakingSession();
        session.setId(UUID.randomUUID().toString());
        session.setMode(request.resolvedMode());
        session.setTopic(request.topic().trim());
        session.setLevel(request.resolvedLevel());
        session.setStatus("active");
        session.setCreatedAt(Instant.now());
        session.setConversationProvider(conversation.id());
        session.setCoachingProvider(coaching.id());

        String opening = conversation.openingMessage(session);
        SpeakingTurn assistantOpen = new SpeakingTurn(
                UUID.randomUUID().toString(),
                TurnRole.ASSISTANT,
                opening,
                UtteranceSource.TYPED,
                Instant.now()
        );
        session.getTurns().add(assistantOpen);
        repository.save(session);
        return session;
    }

    public SpeakingSession get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Speaking session not found: " + id));
    }

    public List<SpeakingSession> list(int limit) {
        return repository.listRecent(limit);
    }

    public TurnResponse submitTurn(String sessionId, String text, UtteranceSource source) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("Turn text is required");
        }
        SpeakingTurn studentTurn;
        SpeakingTurn assistantTurn;
        synchronized (sessionLocks.forSession(sessionId)) {
            SpeakingSession session = get(sessionId);
            if (!"active".equals(session.getStatus())) {
                throw new IllegalStateException("Session is not active");
            }

            studentTurn = new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.STUDENT,
                    text.trim(),
                    source != null ? source : UtteranceSource.TYPED,
                    Instant.now()
            );
            session.getTurns().add(studentTurn);

            String reply = providers.conversation().reply(session, studentTurn.getText());
            assistantTurn = new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.ASSISTANT,
                    reply,
                    UtteranceSource.TYPED,
                    Instant.now()
            );
            session.getTurns().add(assistantTurn);

            CorrectionEvent pending = new CorrectionEvent();
            pending.setId(UUID.randomUUID().toString());
            pending.setTurnId(studentTurn.getId());
            pending.setOriginalText(studentTurn.getText());
            pending.setStatus("pending");
            pending.setCreatedAt(Instant.now());
            session.getCorrections().add(pending);

            repository.save(session);
        }

        coachingAsyncWorker.analyse(
                sessionId,
                studentTurn.getId(),
                studentTurn.getText(),
                studentTurn.getSource()
        );
        return new TurnResponse(sessionId, studentTurn, assistantTurn, true);
    }

    public SpeakingSession end(String sessionId) {
        synchronized (sessionLocks.forSession(sessionId)) {
            SpeakingSession session = get(sessionId);
            session.setStatus("ended");
            session.setEndedAt(Instant.now());
            session.setReviewSummary(buildReview(session));
            repository.save(session);
            return session;
        }
    }

    private static String buildReview(SpeakingSession session) {
        long studentTurns = session.getTurns().stream()
                .filter(t -> t.getRole() == TurnRole.STUDENT)
                .count();
        List<CorrectionEvent> ready = session.getCorrections().stream()
                .filter(c -> "ready".equals(c.getStatus()))
                .toList();
        long upgrades = ready.stream()
                .filter(c -> c.getIssueType() != null && (
                        c.getIssueType().contains("word") || "vague_wording".equals(c.getIssueType())
                ))
                .count();
        List<LearnerPattern> top = new ArrayList<>(session.getPatterns());
        top.sort(Comparator.comparingInt(LearnerPattern::getCount).reversed());

        StringBuilder sb = new StringBuilder();
        sb.append("You spoke ").append(studentTurns).append(" turns on \"")
                .append(session.getTopic()).append("\". ");
        if (ready.isEmpty()) {
            sb.append("No coaching notes this time — keep practising retrieving richer wording under pressure.");
        } else {
            sb.append(ready.size()).append(" coaching note(s)");
            if (upgrades > 0) {
                sb.append(" (").append(upgrades).append(" word upgrade(s))");
            }
            sb.append(". ");
            if (!session.getVocabulary().isEmpty()) {
                sb.append("Phrases to reuse: ");
                session.getVocabulary().stream().limit(5).forEach(v ->
                        sb.append("“").append(v.getPhrase()).append("”; ")
                );
            }
            if (!top.isEmpty()) {
                LearnerPattern p = top.get(0);
                sb.append("Top pattern: ").append(p.getIssueType())
                        .append(" (x").append(p.getCount()).append(").");
            }
        }
        return sb.toString().trim();
    }
}
