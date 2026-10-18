package com.speakbetter.speaking.provider;

import com.speakbetter.config.SpeakingProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProviderRegistry {

    private final SpeakingProperties props;
    private final Map<String, ConversationEngine> conversationEngines;
    private final Map<String, CoachingEngine> coachingEngines;

    public ProviderRegistry(
            SpeakingProperties props,
            List<ConversationEngine> conversationEngines,
            List<CoachingEngine> coachingEngines
    ) {
        this.props = props;
        this.conversationEngines = conversationEngines.stream()
                .collect(Collectors.toMap(ConversationEngine::id, Function.identity()));
        this.coachingEngines = coachingEngines.stream()
                .collect(Collectors.toMap(CoachingEngine::id, Function.identity()));
    }

    public ConversationEngine conversation() {
        ConversationEngine engine = conversationEngines.get(props.conversationProvider());
        if (engine == null) {
            throw new IllegalStateException(
                    "Unknown speaking.conversation-provider='" + props.conversationProvider()
                            + "'. Available: " + conversationEngines.keySet()
            );
        }
        return engine;
    }

    public CoachingEngine coaching() {
        CoachingEngine engine = coachingEngines.get(props.coachingProvider());
        if (engine == null) {
            throw new IllegalStateException(
                    "Unknown speaking.coaching-provider='" + props.coachingProvider()
                            + "'. Available: " + coachingEngines.keySet()
            );
        }
        return engine;
    }

    public Map<String, Object> describe() {
        return Map.of(
                "conversationProvider", props.conversationProvider(),
                "coachingProvider", props.coachingProvider(),
                "availableConversation", conversationEngines.keySet(),
                "availableCoaching", coachingEngines.keySet(),
                "sttNote", "browser (Web Speech API) — swap on frontend adapter",
                "ttsNote", "browser (speechSynthesis) — swap on frontend adapter"
        );
    }
}
