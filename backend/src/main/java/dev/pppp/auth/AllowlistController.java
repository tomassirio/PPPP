package dev.pppp.auth;

import dev.pppp.settings.SettingsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** /me plus the admin allowlist and user endpoints. User id = email, so both share AllowlistService. */
@RestController
@RequestMapping("/api/v1")
public class AllowlistController {
    public record Entry(@NotBlank @Email String email, @NotNull AllowlistService.Role role) {}
    public record RoleChange(@NotNull AllowlistService.Role role) {}
    public record Me(String id, String name, List<String> roles, int rateLimitRemaining) {}

    private final AllowlistService allowlist;
    private final SettingsService settings;
    private final StringRedisTemplate redis;

    public AllowlistController(AllowlistService allowlist, SettingsService settings, StringRedisTemplate redis) {
        this.allowlist = allowlist;
        this.settings = settings;
        this.redis = redis;
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        String id = CurrentUser.id(auth);
        String name = auth.getPrincipal() instanceof OidcUser o && o.getFullName() != null ? o.getFullName() : id;
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        String used = redis.opsForValue().get("ratelimit:" + id);
        int remaining = settings.rateLimit().inputsPerSecond() - (used == null ? 0 : Integer.parseInt(used));
        return new Me(id, name, roles, Math.max(0, remaining));
    }

    @GetMapping("/admin/allowlist")
    public List<Entry> allowlist() {
        return allowlist.list().stream().map(u -> new Entry(u.id(), u.role())).toList();
    }

    @PostMapping("/admin/allowlist")
    @ResponseStatus(HttpStatus.CREATED)
    public Entry add(@Valid @RequestBody Entry e) {
        allowlist.put(e.email(), e.role());
        return new Entry(AllowlistService.normalize(e.email()), e.role());
    }

    @PutMapping({"/admin/allowlist/{email}", "/admin/users/{email}/role"})
    public Entry changeRole(@PathVariable String email, @Valid @RequestBody RoleChange c) {
        requireAllowlisted(email);
        allowlist.put(email, c.role());
        return new Entry(AllowlistService.normalize(email), c.role());
    }

    @DeleteMapping("/admin/allowlist/{email}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String email) {
        if (!allowlist.remove(email)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    @GetMapping("/admin/users")
    public List<AllowlistService.User> users() {
        return allowlist.list();
    }

    @PostMapping("/admin/users/{email}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(@PathVariable String email) {
        requireAllowlisted(email);
        allowlist.ban(email);
    }

    private void requireAllowlisted(String email) {
        if (allowlist.role(email) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
}
