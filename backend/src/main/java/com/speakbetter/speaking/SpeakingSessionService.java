package com.speakbetter.speaking;

import com.speakbetter.speaking.domain.CorrectionEvent;
import com.speakbetter.speaking.domain.DrillEvaluation;
import com.speakbetter.speaking.domain.DrillItem;
import com.speakbetter.speaking.domain.LearnerPattern;
import com.speakbetter.speaking.domain.SpeakingMode;
import com.speakbetter.speaking.domain.SpeakingSession;
import com.speakbetter.speaking.domain.SpeakingTurn;
import com.speakbetter.speaking.domain.TurnRole;
import com.speakbetter.speaking.domain.UtteranceSource;
import com.speakbetter.speaking.domain.VocabularyItem;
import com.speakbetter.speaking.dto.CreateSessionRequest;
import com.speakbetter.speaking.dto.DrillAttemptResponse;
import com.speakbetter.speaking.dto.TurnResponse;
import com.speakbetter.speaking.provider.CoachingEngine;
import com.speakbetter.speaking.provider.ConversationEngine;
import com.speakbetter.speaking.provider.DrillEngine;
import com.speakbetter.speaking.provider.ProviderRegistry;
import com.speakbetter.speaking.store.SpeakingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class SpeakingSessionService {

    private final SpeakingSessionRepository repository;
    private final ProviderRegistry providers;
    private final CoachingAsyncWorker coachingAsyncWorker;
    private final SessionLocks sessionLocks;
    private final DrillEngine drillEngine;

    public SpeakingSessionService(
            SpeakingSessionRepository repository,
            ProviderRegistry providers,
            CoachingAsyncWorker coachingAsyncWorker,
            SessionLocks sessionLocks,
            DrillEngine drillEngine
    ) {
        this.repository = repository;
        this.providers = providers;
        this.coachingAsyncWorker = coachingAsyncWorker;
        this.sessionLocks = sessionLocks;
        this.drillEngine = drillEngine;
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

        if (session.isDrillMode()) {
            DrillEngine.GeneratedDrill drill = drillEngine.generate(session);
            session.setChatTitle(drill.title());
            session.setDrillItems(drill.items());
            session.setCurrentDrillIndex(0);
            SpeakingTurn assistantOpen = new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.ASSISTANT,
                    buildDrillIntro(session, drill),
                    UtteranceSource.TYPED,
                    Instant.now()
            );
            session.getTurns().add(assistantOpen);
        } else {
            String opening = conversation.openingMessage(session);
            SpeakingTurn assistantOpen = new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.ASSISTANT,
                    opening,
                    UtteranceSource.TYPED,
                    Instant.now()
            );
            session.getTurns().add(assistantOpen);
        }

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
        SpeakingSession probe = get(sessionId);
        if (probe.isDrillMode()) {
            throw new IllegalStateException(
                    "This is a Hinglish drill session. Use /api/speaking/sessions/{id}/drill/attempt instead."
            );
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

    /**
     * Evaluate one drill turn (current Hinglish item → student English).
     */
    public DrillAttemptResponse submitDrillAttempt(String sessionId, String text, UtteranceSource source) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("English attempt text is required");
        }

        SpeakingTurn studentTurn;
        DrillEvaluation evaluation;
        DrillItem nextItem;
        boolean complete;
        int nextIndex;
        List<DrillEvaluation> allEvals;

        synchronized (sessionLocks.forSession(sessionId)) {
            SpeakingSession session = get(sessionId);
            if (!session.isDrillMode()) {
                throw new IllegalStateException("Session is not a Hinglish drill");
            }
            if (!"active".equals(session.getStatus())) {
                throw new IllegalStateException("Session is not active");
            }
            if (session.getCurrentDrillIndex() >= session.getDrillItems().size()) {
                throw new IllegalStateException("Drill already complete");
            }

            DrillItem current = session.getDrillItems().get(session.getCurrentDrillIndex());
            studentTurn = new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.STUDENT,
                    text.trim(),
                    source != null ? source : UtteranceSource.TYPED,
                    Instant.now()
            );
            session.getTurns().add(studentTurn);

            evaluation = drillEngine.evaluateOne(current, text.trim(), session.getLevel());
            session.getDrillEvaluations().add(evaluation);
            current.setStatus("done");
            mergeDrillVocabulary(session, evaluation);

            SpeakingTurn feedback = new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.ASSISTANT,
                    formatDrillFeedback(current, evaluation),
                    UtteranceSource.TYPED,
                    Instant.now()
            );
            session.getTurns().add(feedback);

            nextIndex = session.getCurrentDrillIndex() + 1;
            session.setCurrentDrillIndex(nextIndex);
            complete = nextIndex >= session.getDrillItems().size();
            nextItem = complete ? null : session.getDrillItems().get(nextIndex);

            if (complete) {
                session.setStatus("ended");
                session.setEndedAt(Instant.now());
                session.setReviewSummary(buildReview(session));
            }

            repository.save(session);
            allEvals = List.copyOf(session.getDrillEvaluations());
        }

        return new DrillAttemptResponse(
                sessionId,
                studentTurn,
                evaluation,
                nextItem,
                nextIndex,
                complete,
                allEvals
        );
    }

    /**
     * Chat drill only: student speaks / pastes the full English retelling; evaluate every turn.
     */
    public SpeakingSession submitFullChatRetelling(String sessionId, String text, UtteranceSource source) {
        if (!StringUtils.hasText(text)) {
            throw new IllegalArgumentException("English retelling is required");
        }

        synchronized (sessionLocks.forSession(sessionId)) {
            SpeakingSession session = get(sessionId);
            if (session.getMode() != SpeakingMode.HINGLISH_CHAT) {
                throw new IllegalStateException("Full retelling is only for HINGLISH_CHAT drills");
            }
            if (!"active".equals(session.getStatus())) {
                throw new IllegalStateException("Session is not active");
            }

            SpeakingTurn studentTurn = new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.STUDENT,
                    text.trim(),
                    source != null ? source : UtteranceSource.TYPED,
                    Instant.now()
            );
            session.getTurns().add(studentTurn);

            List<DrillEvaluation> evals = drillEngine.evaluateFullRetelling(
                    session.getDrillItems(),
                    text.trim(),
                    session.getLevel()
            );
            session.getDrillEvaluations().clear();
            session.getDrillEvaluations().addAll(evals);
            for (DrillItem item : session.getDrillItems()) {
                item.setStatus("done");
            }
            for (DrillEvaluation e : evals) {
                mergeDrillVocabulary(session, e);
            }
            session.setCurrentDrillIndex(session.getDrillItems().size());

            StringBuilder feedback = new StringBuilder("Turn-by-turn review of your full English retelling:\n");
            for (DrillEvaluation e : evals) {
                feedback.append(e.getDrillIndex() + 1)
                        .append(") score ")
                        .append(e.getScore())
                        .append("/5 — ")
                        .append(e.getVerdict())
                        .append("\n");
            }
            session.getTurns().add(new SpeakingTurn(
                    UUID.randomUUID().toString(),
                    TurnRole.ASSISTANT,
                    feedback.toString().trim(),
                    UtteranceSource.TYPED,
                    Instant.now()
            ));

            session.setStatus("ended");
            session.setEndedAt(Instant.now());
            session.setReviewSummary(buildReview(session));
            repository.save(session);
            return session;
        }
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

    private static String buildDrillIntro(SpeakingSession session, DrillEngine.GeneratedDrill drill) {
        StringBuilder sb = new StringBuilder();
        if (session.getMode() == SpeakingMode.HINGLISH_SENTENCE) {
            sb.append("Hinglish → English sentence drill");
        } else {
            sb.append("Hinglish chat → English drill");
        }
        if (StringUtils.hasText(drill.title())) {
            sb.append(": ").append(drill.title());
        }
        sb.append(".\n");
        sb.append(drill.instructions()).append("\n");
        sb.append("There are ").append(drill.items().size()).append(" items. ");
        if (session.getMode() == SpeakingMode.HINGLISH_CHAT) {
            sb.append("Say each turn in English one by one, or retell the whole chat at once.");
        } else {
            sb.append("Say each Hinglish sentence in natural English.");
        }
        return sb.toString();
    }

    private static String formatDrillFeedback(DrillItem item, DrillEvaluation evaluation) {
        StringBuilder sb = new StringBuilder();
        sb.append("Score: ").append(evaluation.getScore()).append("/5. ");
        if (StringUtils.hasText(evaluation.getVerdict())) {
            sb.append(evaluation.getVerdict()).append(" ");
        }
        if (StringUtils.hasText(evaluation.getBetterEnglish())
                && !evaluation.getBetterEnglish().equalsIgnoreCase(evaluation.getStudentEnglish())) {
            sb.append("Natural English: ").append(evaluation.getBetterEnglish()).append(" ");
        }
        if (StringUtils.hasText(evaluation.getUsefulPhrase())) {
            sb.append("Remember: \"").append(evaluation.getUsefulPhrase()).append("\".");
        }
        return sb.toString().trim();
    }

    private static void mergeDrillVocabulary(SpeakingSession session, DrillEvaluation evaluation) {
        if (!StringUtils.hasText(evaluation.getUsefulPhrase())) {
            return;
        }
        String phrase = evaluation.getUsefulPhrase().trim();
        // Skip trivial leftovers that slipped past the model.
        String[] words = phrase.split("\\s+");
        if (words.length < 2
                || phrase.matches("(?i)(a bit|a little|very|really|so|quite)\\s+\\w+")) {
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
            item.setInsteadOf(evaluation.getHinglish());
            item.setWhenToUse(evaluation.getTip());
            item.setSeenCount(1);
            item.setCreatedAt(now);
            item.setLastSeenAt(now);
            session.getVocabulary().add(item);
        } else {
            existing.setSeenCount(existing.getSeenCount() + 1);
            existing.setLastSeenAt(now);
        }
    }

    private static String buildReview(SpeakingSession session) {
        if (session.isDrillMode()) {
            List<DrillEvaluation> evals = session.getDrillEvaluations();
            if (evals.isEmpty()) {
                return "Drill ended with no attempts.";
            }
            double avg = evals.stream().mapToInt(DrillEvaluation::getScore).average().orElse(0);
            StringBuilder sb = new StringBuilder();
            sb.append("Hinglish drill \"").append(
                    StringUtils.hasText(session.getChatTitle()) ? session.getChatTitle() : session.getTopic()
            ).append("\": average ").append(String.format(Locale.ROOT, "%.1f", avg)).append("/5 across ")
                    .append(evals.size()).append(" turn(s). ");
            if (!session.getVocabulary().isEmpty()) {
                sb.append("Phrases to reuse: ");
                session.getVocabulary().stream().limit(5).forEach(v ->
                        sb.append("“").append(v.getPhrase()).append("”; ")
                );
            }
            return sb.toString().trim();
        }

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
