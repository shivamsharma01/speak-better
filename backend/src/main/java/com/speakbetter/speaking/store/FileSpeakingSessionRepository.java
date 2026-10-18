package com.speakbetter.speaking.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.speakbetter.config.SpeakingProperties;
import com.speakbetter.speaking.domain.SpeakingSession;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Personal-use persistence: in-memory cache + JSON files under speaking.data-dir.
 * Swap later for JPA without changing the service layer.
 */
@Repository
public class FileSpeakingSessionRepository implements SpeakingSessionRepository {

    private final Path dir;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, SpeakingSession> cache = new ConcurrentHashMap<>();

    public FileSpeakingSessionRepository(SpeakingProperties props, ObjectMapper objectMapper) {
        this.dir = Path.of(props.dataDir()).toAbsolutePath().normalize();
        this.mapper = objectMapper.copy()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(this.dir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create speaking data dir: " + this.dir, e);
        }
    }

    @Override
    public synchronized void save(SpeakingSession session) {
        cache.put(session.getId(), session);
        Path file = dir.resolve(session.getId() + ".json");
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), session);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to persist session " + session.getId(), e);
        }
    }

    @Override
    public Optional<SpeakingSession> findById(String id) {
        SpeakingSession cached = cache.get(id);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path file = dir.resolve(id + ".json");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            SpeakingSession session = mapper.readValue(file.toFile(), SpeakingSession.class);
            cache.put(id, session);
            return Optional.of(session);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load session " + id, e);
        }
    }

    @Override
    public List<SpeakingSession> listRecent(int limit) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed())
                    .limit(Math.max(1, limit))
                    .map(p -> {
                        String id = p.getFileName().toString().replace(".json", "");
                        return findById(id).orElse(null);
                    })
                    .filter(s -> s != null)
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to list speaking sessions", e);
        }
    }
}
