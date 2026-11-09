package dev.pppp.service;

import dev.pppp.config.PpppProperties;
import dev.pppp.exception.NotFoundException;
import dev.pppp.model.Role;
import dev.pppp.model.User;
import dev.pppp.model.UserRevokedEvent;
import dev.pppp.model.UserSessionsEndedEvent;
import dev.pppp.repository.AllowlistRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Runtime allowlist and bans. Revocation is announced as events: UserRevokedEvent (remove/ban: sessions end,
 * sockets close) and UserSessionsEndedEvent (role change: sessions end). Listeners in pppp-app do the ending.
 */
@Service
public class AllowlistService {
    private final AllowlistRepository repo;
    private final ApplicationEventPublisher events;
    private final ChatService chat;
    private final PpppProperties props;

    public AllowlistService(AllowlistRepository repo, ApplicationEventPublisher events, ChatService chat,
                            PpppProperties props) {
        this.repo = repo;
        this.events = events;
        this.chat = chat;
        this.props = props;
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedAdmins() {
        if (!repo.isEmpty()) return;
        Map<String, Role> seed = new HashMap<>();
        // An empty PPPP_ADMINS binds to [""], so skip blanks.
        props.admins().stream().filter(e -> !e.isBlank()).forEach(e -> seed.put(normalize(e), Role.ADMIN));
        if (!seed.isEmpty()) repo.putAll(seed);
    }

    /** The user's role, or null if not allowlisted. */
    public Role role(String email) {
        return repo.role(normalize(email));
    }

    public boolean isBanned(String email) {
        return repo.isBanned(normalize(email));
    }

    public List<User> list() {
        Set<String> banned = repo.banned();
        return repo.roles().entrySet().stream()
                // ponytail: one Redis read per user for the mute; fine for a friends-sized allowlist.
                .map(e -> new User(e.getKey(), e.getValue(), banned.contains(e.getKey()), chat.mutedUntil(e.getKey())))
                .sorted(Comparator.comparing(User::id))
                .toList();
    }

    /** Adds or changes a role. A role change ends the user's sessions so it applies on next login. */
    public void put(String email, Role role) {
        String id = normalize(email);
        Role old = repo.role(id);
        repo.put(id, role);
        if (old != null && old != role) events.publishEvent(new UserSessionsEndedEvent(id));
    }

    /** Like put, but only for someone already allowlisted. */
    public void changeRole(String email, Role role) {
        requireAllowlisted(email);
        put(email, role);
    }

    /** Admins can't be removed directly: demote them to player first. */
    public void remove(String email) {
        String id = normalize(email);
        if (repo.role(id) == Role.ADMIN) throw new IllegalStateException("Demote " + id + " to player before removing them");
        if (!repo.remove(id)) throw notAllowlisted(id);
        events.publishEvent(new UserRevokedEvent(id));
    }

    public void ban(String email) {
        requireAllowlisted(email);
        String id = normalize(email);
        repo.ban(id);
        events.publishEvent(new UserRevokedEvent(id));
        chat.postAdmin(id + " was banned");
    }

    public void unban(String email) {
        String id = normalize(email);
        if (!repo.unban(id)) throw new NotFoundException(id + " is not banned");
        chat.postAdmin(id + " was unbanned");
    }


    private void requireAllowlisted(String email) {
        if (role(email) == null) throw notAllowlisted(normalize(email));
    }

    private static NotFoundException notAllowlisted(String id) {
        return new NotFoundException(id + " is not on the allowlist");
    }
}
