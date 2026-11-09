package dev.pppp.controller;

import dev.pppp.model.Viewer;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.security.CurrentUser;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventsController {
    private final EventPublisher events;

    public EventsController(EventPublisher events) {
        this.events = events;
    }

    @GetMapping(path = "/api/v1/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Authentication auth) {
        return events.subscribe(CurrentUser.viewer(auth));
    }

    /** Who has /play open right now. Kept current afterwards by PRESENCE events. */
    @GetMapping("/api/v1/presence")
    public List<Viewer> presence() {
        return events.viewers();
    }
}
