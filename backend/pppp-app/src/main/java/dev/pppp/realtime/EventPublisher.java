package dev.pppp.realtime;

import dev.pppp.model.Viewer;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fan-out for GET /api/v1/events. Event types: INPUT_EXECUTED, MODE_CHANGED, MODE_VOTES, VOTE_TALLY,
 * CONSOLE_CHANGED, CHAT_MESSAGE, CHAT_DELETED, GAME_EVENT, PRESENCE.
 *
 * <p>Presence = who has a stream open, kept in memory (single instance). PRESENCE carries the list of
 * viewers, one entry per user however many tabs they have open.
 */
@Component
public class EventPublisher {
    private final Map<SseEmitter, Viewer> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Viewer viewer) {
        var emitter = new SseEmitter(0L);
        emitters.put(emitter, viewer);
        emitter.onCompletion(() -> drop(emitter));
        emitter.onTimeout(() -> drop(emitter));
        emitter.onError(e -> drop(emitter));
        publish("PRESENCE", viewers());
        return emitter;
    }

    /** Distinct viewers, sorted by name. */
    public List<Viewer> viewers() {
        return emitters.values().stream()
                .distinct()
                .sorted(Comparator.comparing(Viewer::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public void publish(String type, Object data) {
        for (var emitter : emitters.keySet()) {
            try {
                emitter.send(SseEmitter.event().name(type).data(data));
            } catch (IOException | IllegalStateException e) {
                drop(emitter);
            }
        }
    }

    /** A closed tab is only noticed on the next write; ping so presence drops it within ~15 s. */
    @Scheduled(fixedDelay = 15_000)
    void heartbeat() {
        for (var emitter : emitters.keySet()) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException | IllegalStateException e) {
                drop(emitter);
            }
        }
    }

    private void drop(SseEmitter emitter) {
        if (emitters.remove(emitter) != null) publish("PRESENCE", viewers());
    }
}
