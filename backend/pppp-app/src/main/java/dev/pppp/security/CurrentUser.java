package dev.pppp.security;

import java.util.Locale;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

/** A user's id everywhere in pppp is their lower-cased email (allowlist key, stream "user" field). */
public final class CurrentUser {
    private CurrentUser() {}

    public static String id(Authentication auth) {
        String raw = auth.getPrincipal() instanceof OidcUser oidc ? oidc.getEmail() : auth.getName();
        return raw.toLowerCase(Locale.ROOT);
    }

    public static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
