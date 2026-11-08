package dev.pppp.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/** Lets in only verified, allowlisted, unbanned Google accounts; grants ROLE_PLAYER (+ ROLE_ADMIN). */
@Service
public class AllowlistOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {
    private final OidcUserService delegate = new OidcUserService();
    private final AllowlistService allowlist;

    public AllowlistOidcUserService(AllowlistService allowlist) {
        this.allowlist = allowlist;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser google = delegate.loadUser(request);
        String email = google.getEmail();
        if (email == null || !Boolean.TRUE.equals(google.getEmailVerified())) throw denied("unverified email");
        AllowlistService.Role role = allowlist.role(email);
        if (role == null) throw denied("not on the allowlist");
        if (allowlist.isBanned(email)) throw denied("banned");

        List<GrantedAuthority> roles = new ArrayList<>(List.of(new SimpleGrantedAuthority("ROLE_PLAYER")));
        if (role == AllowlistService.Role.ADMIN) roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return new Principal(roles, google.getIdToken(), google.getUserInfo());
    }

    private static OAuth2AuthenticationException denied(String why) {
        return new OAuth2AuthenticationException(new OAuth2Error("access_denied", why, null));
    }

    /** Principal name = lower-cased email, so Spring Session's principal index finds sessions by user id. */
    static final class Principal extends DefaultOidcUser {
        private static final long serialVersionUID = 1L;

        Principal(List<GrantedAuthority> roles, OidcIdToken idToken, OidcUserInfo userInfo) {
            super(roles, idToken, userInfo, "email");
        }

        @Override
        public String getName() {
            return AllowlistService.normalize(super.getName());
        }
    }
}
