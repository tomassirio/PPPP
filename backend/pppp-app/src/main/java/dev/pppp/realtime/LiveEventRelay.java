package dev.pppp.realtime;

import dev.pppp.model.ChatMessage;
import dev.pppp.model.LiveEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Forwards LiveEvents published by core services to every SSE client; ADMIN chat lines to admins only. */
@Component
public class LiveEventRelay {
    private final EventPublisher sse;

    public LiveEventRelay(EventPublisher sse) {
        this.sse = sse;
    }

    @EventListener
    public void relay(LiveEvent event) {
        if (event.payload() instanceof ChatMessage m && "ADMIN".equals(m.kind())) sse.publishToAdmins(event.type(), m);
        else sse.publish(event.type(), event.payload());
    }
}
