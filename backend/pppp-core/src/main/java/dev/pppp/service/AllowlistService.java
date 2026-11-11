package dev.pppp.service;

import dev.pppp.config.PpppProperties;
import dev.pppp.exception.NotFoundException;
import dev.pppp.model.AccessRequest;
import dev.pppp.model.LiveEvent;
import dev.pppp.model.Role;
import dev.pppp.model.User;
import dev.pppp.model.UserRevokedEvent;
import dev.pppp.model.UserSessionsEndedEvent;
import dev.pppp.repository.AccessRequestRepository;
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
    /** ponytail: hard cap on pending requests, so strangers can't fill Redis; raise it if a real crowd shows up. */
    static final int MAX_REQUESTS = 200;

    private final AllowlistRepository repo;
    private final AccessRequestRepository requests;
    private final ApplicationEventPublisher events;
    private final ChatService chat;
    private final PpppProperties props;

    public AllowlistService(AllowlistRepository repo, AccessRequestRepository requests, ApplicationEventPublisher events,
                            ChatService chat, PpppProperties props) {
        this.repo = repo;
        this.requests = requests;
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

    /** Remembers a player's profile photo so others can see it (Up next). Only https URLs; never throws. */
    public void rememberPicture(String id, String url) {
        if (url == null || !url.startsWith("https://")) return;
        try {
            repo.savePicture(id, url);
        } catch (RuntimeException e) {
            // a missing avatar is not worth failing /me over
        }
    }

    /** Player id -> profile photo URL, for players who have opened the page since photos were remembered. */
    public java.util.Map<String, String> pictures() {
        return repo.pictures();
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

    /**
     * Adds or changes a role. A role change ends the user's sessions so it applies on next login. Adding someone
     * also settles any access request of theirs.
     */
    public void put(String email, Role role) {
        String id = normalize(email);
        Role old = repo.role(id);
        repo.put(id, role);
        requests.unreject(id);
        if (requests.remove(id)) requestsChanged();
        if (old != null && old != role) events.publishEvent(new UserSessionsEndedEvent(id));
    }

    /**
     * A verified Google account that is not on the allowlist tried to sign in: keep it for the admins to decide.
     * Returns false when it is not kept (rejected before, or too many pending), so the sign-in page can say so.
     */
    public boolean requestAccess(String email, String name, String picture) {
        String id = normalize(email);
        if (requests.isRejected(id)) return false;
        boolean known = requests.exists(id);
        if (!known && requests.count() >= MAX_REQUESTS) return false;
        requests.save(new AccessRequest(id, name, picture != null && picture.startsWith("https://") ? picture : null,
                System.currentTimeMillis()));
        if (!known) requestsChanged();
        return true;
    }

    /** Pending requests, oldest first. */
    public List<AccessRequest> accessRequests() {
        return requests.findAll().stream().sorted(Comparator.comparingLong(AccessRequest::requestedAt)).toList();
    }

    /** Lets a requester in with that role. */
    public void accept(String email, Role role) {
        String id = normalize(email);
        if (!requests.exists(id)) throw new NotFoundException("No access request from " + id);
        put(id, role);
        chat.postAdmin(id + " was let in");
    }

    /** Turns a request down; later sign-in attempts from that account are not listed again (adding them still works). */
    public void reject(String email) {
        String id = normalize(email);
        if (!requests.remove(id)) throw new NotFoundException("No access request from " + id);
        requests.reject(id);
        requestsChanged();
    }

    /** Only the count goes out (to every browser); admins fetch the list itself. */
    private void requestsChanged() {
        events.publishEvent(new LiveEvent("ACCESS_REQUESTS", java.util.Map.of("pending", requests.count())));
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
