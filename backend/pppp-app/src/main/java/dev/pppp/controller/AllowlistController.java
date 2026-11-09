package dev.pppp.controller;

import dev.pppp.model.Role;
import dev.pppp.model.User;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.AllowlistService;
import dev.pppp.service.RateLimiter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
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

/** /me plus the admin allowlist and user endpoints. User id = email, so both share AllowlistService. */
@RestController
@RequestMapping("/api/v1")
public class AllowlistController {
    public record Entry(@NotBlank @Email String email, @NotNull Role role) {}
    public record RoleChange(@NotNull Role role) {}
    /** picture: Google profile photo URL, null for non-OIDC logins or accounts without one. */
    public record Me(String id, String name, String picture, List<String> roles, int rateLimitRemaining) {}

    private final AllowlistService allowlist;
    private final RateLimiter rateLimiter;

    public AllowlistController(AllowlistService allowlist, RateLimiter rateLimiter) {
        this.allowlist = allowlist;
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/me")
    public Me me(Authentication auth) {
        String id = CurrentUser.id(auth);
        OidcUser oidc = auth.getPrincipal() instanceof OidcUser o ? o : null;
        String name = oidc != null && oidc.getFullName() != null ? oidc.getFullName() : id;
        String picture = oidc != null ? oidc.getPicture() : null;
        allowlist.rememberPicture(id, picture);
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .toList();
        return new Me(id, name, picture, roles, rateLimiter.remaining(id));
    }

    /** Player id -> Google profile photo URL, for the Up next list. */
    @GetMapping("/players/pictures")
    public java.util.Map<String, String> pictures() {
        return allowlist.pictures();
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
        allowlist.changeRole(email, c.role());
        return new Entry(AllowlistService.normalize(email), c.role());
    }

    @DeleteMapping("/admin/allowlist/{email}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String email) {
        allowlist.remove(email);
    }

    @GetMapping("/admin/users")
    public List<User> users() {
        return allowlist.list();
    }

    @PostMapping("/admin/users/{email}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void ban(@PathVariable String email) {
        allowlist.ban(email);
    }

    @DeleteMapping("/admin/users/{email}/ban")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unban(@PathVariable String email) {
        allowlist.unban(email);
    }
}
