package dev.pppp.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AllowlistOidcUserService allowlistOidcUserService) throws Exception {
        return http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/assets/**", "/actuator/health/**", "/error").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().hasRole("PLAYER"))
                .oauth2Login(o -> o
                        .userInfoEndpoint(u -> u.oidcUserService(allowlistOidcUserService))
                        .defaultSuccessUrl("/play", true)
                        .failureUrl("/?error=denied"))
                // API callers get 401, not a redirect to Google.
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        new AntPathRequestMatcher("/api/**")))
                .csrf(c -> c
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                // Tokens are deferred by default; load it on every request so the SPA always has an XSRF-TOKEN cookie.
                .addFilterAfter((req, res, chain) -> {
                    if (req.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken token) token.getToken();
                    chain.doFilter(req, res);
                }, BasicAuthenticationFilter.class)
                .logout(l -> l.logoutUrl("/api/v1/logout")
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(204)))
                .build();
    }
}
