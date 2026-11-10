package dev.pppp.security;

import dev.pppp.model.Role;
import dev.pppp.service.AllowlistService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.config.PpppProperties;
import dev.pppp.TestRedis;
import dev.pppp.repository.AllowlistRepository;
import dev.pppp.service.ChatService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@DirtiesContext
@AutoConfigureMockMvc
@Import(TestRedis.class)
class AuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired StringRedisTemplate redis;
    @Autowired AllowlistService allowlist;
    @Autowired ChatService chat;
    @Autowired AllowlistRepository repo;
    @Autowired AllowlistOidcUserService oidcUsers;
    @Autowired RedisIndexedSessionRepository sessions;
    @Autowired ApplicationEventPublisher events;

    @BeforeEach
    void reset() {
        redis.delete(List.of("allowlist", "bans", "ratelimit:me@x.com", "users:pictures"));
    }

    @Test
    void googleLoginOnlyForAllowlistedUnbannedUsers() {
        allowlist.put("Admin@X.com", Role.ADMIN);
        allowlist.put("player@x.com", Role.PLAYER);
        allowlist.put("banned@x.com", Role.PLAYER);
        redis.opsForSet().add("bans", "banned@x.com");

        OidcUser admin = oidcUsers.loadUser(googleLogin("Admin@X.com"));
        assertThat(admin.getName()).isEqualTo("admin@x.com");
        assertThat(admin.getAuthorities()).extracting(a -> a.getAuthority()).containsExactlyInAnyOrder("ROLE_PLAYER", "ROLE_ADMIN");
        assertThat(oidcUsers.loadUser(googleLogin("player@x.com")).getAuthorities())
                .extracting(a -> a.getAuthority()).containsExactly("ROLE_PLAYER");
        assertThatThrownBy(() -> oidcUsers.loadUser(googleLogin("stranger@x.com")))
                .isInstanceOf(OAuth2AuthenticationException.class);
        assertThatThrownBy(() -> oidcUsers.loadUser(googleLogin("banned@x.com")))
                .isInstanceOf(OAuth2AuthenticationException.class);
    }

    @Test
    void adminEndpointsAreAdminOnlyAndValidated() throws Exception {
        String body = "{\"email\":\"friend@x.com\",\"role\":\"PLAYER\"}";
        mvc.perform(post("/api/v1/admin/allowlist").with(csrf()).with(user("p@x.com").roles("PLAYER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/allowlist").with(csrf()).with(user("a@x.com").roles("PLAYER", "ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        assertThat(allowlist.role("friend@x.com")).isEqualTo(Role.PLAYER);

        mvc.perform(post("/api/v1/admin/allowlist").with(csrf()).with(user("a@x.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"nope\",\"role\":\"PLAYER\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/users/friend@x.com/role").with(csrf()).with(user("a@x.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"GOD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/admin/users/friend@x.com/role").with(csrf()).with(user("a@x.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").with(user("a@x.com").roles("ADMIN")))
                .andExpect(jsonPath("$[0].id").value("friend@x.com"))
                .andExpect(jsonPath("$[0].role").value("ADMIN"))
                .andExpect(jsonPath("$[0].banned").value(false));
    }

    @Test
    void removalBanAndRoleChangeEndSessions() throws Exception {
        allowlist.put("gone@x.com", Role.PLAYER);
        allowlist.put("bad@x.com", Role.PLAYER);
        allowlist.put("promo@x.com", Role.PLAYER);
        session("gone@x.com");
        session("bad@x.com");
        session("promo@x.com");

        mvc.perform(put("/api/v1/admin/users/promo@x.com/role").with(csrf()).with(user("a@x.com").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk());
        assertThat(sessions.findByPrincipalName("promo@x.com")).isEmpty();
        mvc.perform(delete("/api/v1/admin/allowlist/nobody@x.com").with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/admin/allowlist/Gone@x.com").with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/admin/users/bad@x.com/ban").with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isNoContent());

        assertThat(allowlist.role("gone@x.com")).isNull();
        assertThat(allowlist.isBanned("bad@x.com")).isTrue();
        assertThat(sessions.findByPrincipalName("gone@x.com")).isEmpty();
        assertThat(sessions.findByPrincipalName("bad@x.com")).isEmpty();
    }

    @Test
    void unbanAndAdminsMustBeDemotedBeforeRemoval() throws Exception {
        allowlist.put("bad@x.com", Role.PLAYER);
        allowlist.put("boss@x.com", Role.ADMIN);
        allowlist.ban("bad@x.com");

        mvc.perform(delete("/api/v1/admin/users/Bad@x.com/ban").with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isNoContent());
        assertThat(allowlist.isBanned("bad@x.com")).isFalse();
        mvc.perform(delete("/api/v1/admin/users/bad@x.com/ban").with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/admin/allowlist/boss@x.com").with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isConflict());
        assertThat(allowlist.role("boss@x.com")).isEqualTo(Role.ADMIN);
        allowlist.changeRole("boss@x.com", Role.PLAYER);
        mvc.perform(delete("/api/v1/admin/allowlist/boss@x.com").with(csrf()).with(user("a@x.com").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void meReportsRolesAndRateLimitRemaining() throws Exception {
        redis.opsForValue().set("ratelimit:me@x.com", "1");
        mvc.perform(get("/api/v1/me").with(user("Me@x.com").roles("PLAYER")))
                .andExpect(jsonPath("$.id").value("me@x.com"))
                .andExpect(jsonPath("$.roles[0]").value("PLAYER"))
                .andExpect(jsonPath("$.rateLimitRemaining").value(1)); // default limit 2
    }

    @Test
    void meIncludesGooglePicture() throws Exception {
        mvc.perform(get("/api/v1/me").with(oidcLogin()
                        .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_PLAYER"))
                        .idToken(t -> t.claim("email", "pic@x.com").claim("picture", "https://lh3.example/p.jpg"))))
                .andExpect(jsonPath("$.picture").value("https://lh3.example/p.jpg"));
        mvc.perform(get("/api/v1/me").with(user("plain@x.com").roles("PLAYER")))
                .andExpect(jsonPath("$.picture").doesNotExist());
        // Remembered for the other players (Up next).
        mvc.perform(get("/api/v1/players/pictures").with(user("other@x.com").roles("PLAYER")))
                .andExpect(jsonPath("$['pic@x.com']").value("https://lh3.example/p.jpg"))
                .andExpect(jsonPath("$['plain@x.com']").doesNotExist());
    }

    @Test
    void seedsConfiguredAdminsOnlyWhenAllowlistIsEmpty() {
        // An empty PPPP_ADMINS binds to [""].
        var props = new PpppProperties(List.of("", "Boss@X.com"), null);
        new AllowlistService(repo, events, chat, props).seedAdmins();
        assertThat(redis.opsForHash().entries("allowlist")).isEqualTo(Map.of("boss@x.com", "ADMIN"));

        new AllowlistService(repo, events, chat,
                new PpppProperties(List.of("other@x.com"), null)).seedAdmins();
        assertThat(redis.opsForHash().size("allowlist")).isEqualTo(1);
    }

    private void session(String email) {
        var s = sessions.createSession();
        s.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, email);
        sessions.save(s);
        assertThat(sessions.findByPrincipalName(email)).hasSize(1);
    }

    private static OidcUserRequest googleLogin(String email) {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId("c").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("http://localhost/cb").authorizationUri("http://g/auth").tokenUri("http://g/token")
                .scope("openid", "email").build();
        Instant now = Instant.now();
        var token = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "t", now, now.plusSeconds(60));
        var idToken = OidcIdToken.withTokenValue("id").subject("sub-" + email).issuedAt(now).expiresAt(now.plusSeconds(60))
                .claim("email", email).claim("email_verified", true).build();
        return new OidcUserRequest(google, token, idToken);
    }
}
