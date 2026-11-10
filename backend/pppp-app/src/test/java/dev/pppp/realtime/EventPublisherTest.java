package dev.pppp.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pppp.model.Viewer;
import org.junit.jupiter.api.Test;
import org.springframework.session.MapSession;
import org.springframework.session.events.SessionDeletedEvent;

class EventPublisherTest {
    @Test
    void signingOutClosesThatSessionsStreamsInEveryTab() {
        var events = new EventPublisher();
        var zoe = new Viewer("zoe@x.y", "Zoe", null);
        events.subscribe(zoe, false, "zoe-session"); // tab 1
        events.subscribe(zoe, false, "zoe-session"); // tab 2, same login
        events.subscribe(new Viewer("abe@x.y", "Abe", null), false, "abe-session");

        events.onSessionEnded(new SessionDeletedEvent(this, new MapSession("zoe-session")));

        assertThat(events.viewers()).extracting(Viewer::id).containsExactly("abe@x.y");
    }
}
