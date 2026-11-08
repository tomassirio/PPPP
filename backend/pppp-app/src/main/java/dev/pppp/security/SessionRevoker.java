package dev.pppp.security;

import dev.pppp.model.UserRevokedEvent;
import dev.pppp.model.UserSessionsEndedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

/** Deletes a user's Spring Sessions (found by principal name = user id) when they are revoked or their role changes. */
@Component
public class SessionRevoker {
    private final FindByIndexNameSessionRepository<? extends Session> sessions;

    public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
        this.sessions = sessions;
    }

    @EventListener
    public void onRevoked(UserRevokedEvent e) {
        endSessions(e.userId());
    }

    @EventListener
    public void onSessionsEnded(UserSessionsEndedEvent e) {
        endSessions(e.userId());
    }

    private void endSessions(String userId) {
        sessions.findByPrincipalName(userId).keySet().forEach(sessions::deleteById);
    }
}
