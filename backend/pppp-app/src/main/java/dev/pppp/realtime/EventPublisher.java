package dev.pppp.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Fan-out for GET /api/v1/events. Event types: INPUT_EXECUTED, MODE_CHANGED, MODE_VOTES, VOTE_TALLY,
 * CONSOLE_CHANGED, CHAT_MESSAGE, CHAT_DELETED, GAME_EVENT, GAME_STATE.
 */
@Component
public class EventPublisher {
    /** emitter -> whether its viewer is an admin. */
    private final Map<SseEmitter, Boolean> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(boolean admin) {
        var emitter = new SseEmitter(0L);
        emitters.put(emitter, admin);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    public void publish(String type, Object data) {
        send(type, data, false);
    }

    /** Only to admins' streams (ADMIN chat lines). */
    public void publishToAdmins(String type, Object data) {
        send(type, data, true);
    }

    private void send(String type, Object data, boolean adminsOnly) {
        for (var entry : emitters.entrySet()) {
            if (adminsOnly && !entry.getValue()) continue;
            var emitter = entry.getKey();
            try {
                emitter.send(SseEmitter.event().name(type).data(data));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
