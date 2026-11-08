package dev.pppp.auth;

import dev.pppp.PpppProperties;
import dev.pppp.chat.ChatService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Service;

/** Runtime allowlist (Redis hash allowlist: email -> role), bans (Redis set bans) and session revocation. */
@Service
public class AllowlistService {
    public enum Role { PLAYER, ADMIN }

    public record User(String id, Role role, boolean banned) {}

    private static final String ALLOWLIST = "allowlist";
    private static final String BANS = "bans";

    private final StringRedisTemplate redis;
    private final FindByIndexNameSessionRepository<? extends Session> sessions;
    private final ApplicationEventPublisher events;
    private final ChatService chat;
    private final PpppProperties props;

    public AllowlistService(StringRedisTemplate redis, FindByIndexNameSessionRepository<? extends Session> sessions,
                            ApplicationEventPublisher events, ChatService chat, PpppProperties props) {
        this.redis = redis;
        this.sessions = sessions;
        this.events = events;
        this.chat = chat;
        this.props = props;
    }

    public static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @EventListener(ApplicationReadyEvent.class)
    void seedAdmins() {
        if (redis.opsForHash().size(ALLOWLIST) > 0) return;
        Map<String, String> seed = new HashMap<>();
        // An empty PPPP_ADMINS binds to [""], so skip blanks.
        props.admins().stream().filter(e -> !e.isBlank()).forEach(e -> seed.put(normalize(e), Role.ADMIN.name()));
        if (!seed.isEmpty()) redis.opsForHash().putAll(ALLOWLIST, seed);
    }

    /** The user's role, or null if not allowlisted. */
    public Role role(String email) {
        Object raw = redis.opsForHash().get(ALLOWLIST, normalize(email));
        return raw == null ? null : Role.valueOf((String) raw);
    }

    public boolean isBanned(String email) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(BANS, normalize(email)));
    }

    public List<User> list() {
        Set<String> banned = redis.opsForSet().members(BANS);
        return redis.<String, String>opsForHash().entries(ALLOWLIST).entrySet().stream()
                .map(e -> new User(e.getKey(), Role.valueOf(e.getValue()), banned.contains(e.getKey())))
                .sorted(Comparator.comparing(User::id))
                .toList();
    }

    /** Adds or changes a role. A role change ends the user's sessions so it applies on next login. */
    public void put(String email, Role role) {
        String id = normalize(email);
        Role old = role(id);
        redis.opsForHash().put(ALLOWLIST, id, role.name());
        if (old != null && old != role) endSessions(id);
    }

    /** Returns false if the email was not on the allowlist. */
    public boolean remove(String email) {
        String id = normalize(email);
        if (redis.opsForHash().delete(ALLOWLIST, id) == 0) return false;
        revoke(id);
        return true;
    }

    public void ban(String email) {
        String id = normalize(email);
        redis.opsForSet().add(BANS, id);
        revoke(id);
        chat.postSystem(id + " was banned");
    }

    private void revoke(String id) {
        endSessions(id);
        events.publishEvent(new UserRevokedEvent(id));
    }

    private void endSessions(String id) {
        sessions.findByPrincipalName(id).keySet().forEach(sessions::deleteById);
    }
}
