package dev.pppp.realtime;

import dev.pppp.model.LiveEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Forwards LiveEvents published by core services to every SSE client. */
@Component
public class LiveEventRelay {
    private final EventPublisher sse;

    public LiveEventRelay(EventPublisher sse) {
        this.sse = sse;
    }

    @EventListener
    public void relay(LiveEvent event) {
        sse.publish(event.type(), event.payload());
    }
}
