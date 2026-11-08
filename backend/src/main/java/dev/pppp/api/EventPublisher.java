package dev.pppp.api;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fan-out for GET /api/v1/events. Event types: INPUT_EXECUTED, MODE_CHANGED, MODE_VOTES, VOTE_TALLY,
 * CONSOLE_CHANGED, CHAT_MESSAGE, CHAT_DELETED, GAME_EVENT.
 */
@Component
public class EventPublisher {
    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter subscribe() {
        var emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void publish(String type, Object data) {
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(type).data(data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
