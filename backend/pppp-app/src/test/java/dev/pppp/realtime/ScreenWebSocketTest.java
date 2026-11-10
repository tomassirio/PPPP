package dev.pppp.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.model.UserRevokedEvent;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.emulator.TestRom;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@AutoConfigureMockMvc
@Import({TestRedis.class, ScreenWebSocketTest.BasicAuth.class})
class ScreenWebSocketTest {
    @Autowired MockMvc mvc;
    @Autowired EmulatorHost host;
    @Autowired ApplicationEventPublisher events;
    @LocalServerPort int port;
    @TempDir Path dir;

    /** The real login is Google; a real socket client here authenticates with Basic instead. */
    @TestConfiguration
    static class BasicAuth {
        @Bean
        @Order(0)
        SecurityFilterChain basicForTests(HttpSecurity http) throws Exception {
            return http.securityMatcher(r -> r.getHeader("Authorization") != null)
                    .authorizeHttpRequests(a -> a.anyRequest().hasRole("PLAYER"))
                    .httpBasic(b -> {})
                    .userDetailsService(new InMemoryUserDetailsManager(
                            User.withUsername("Friend@x.y").password("{noop}pw").roles("PLAYER").build()))
                    .build();
        }
    }

    @Test
    void handshakeGoesThroughTheProductionChain() throws Exception {
        mvc.perform(get("/ws/screen")).andExpect(status().isUnauthorized()); // browsers get the Google redirect
        mvc.perform(get("/ws/screen").with(user("p@x.y").roles("ADMIN"))).andExpect(status().isForbidden());
        // A player gets past security to the handshake handler, which rejects a non-upgrade GET.
        mvc.perform(get("/ws/screen").with(user("p@x.y").roles("PLAYER"))).andExpect(status().isBadRequest());
    }

    @Test
    void streamsPngFramesAndClosesOnRevoke() throws Exception {
        host.insert("test", TestRom.write(dir), dir.resolve("test.sav"));
        host.powerOn();
        try {
            BlockingQueue<byte[]> frames = new LinkedBlockingQueue<>();
            CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.setBasicAuth("Friend@x.y", "pw");
            WebSocketSession ws = new StandardWebSocketClient().execute(new AbstractWebSocketHandler() {
                @Override
                protected void handleBinaryMessage(WebSocketSession s, BinaryMessage m) {
                    byte[] b = new byte[m.getPayloadLength()];
                    m.getPayload().get(b);
                    frames.add(b);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                    closed.complete(status);
                }
            }, headers, URI.create("ws://localhost:" + port + "/ws/screen")).get(5, TimeUnit.SECONDS);

            byte[] png = frames.poll(5, TimeUnit.SECONDS);
            assertThat(png).isNotNull().startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
            assertThat(png.length).isLessThan(10_000);
            assertThat(frames.poll(5, TimeUnit.SECONDS)).isNotNull(); // keeps streaming

            events.publishEvent(new UserRevokedEvent("friend@x.y"));
            assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
        } finally {
            host.powerOff();
            host.eject();
        }
    }
}
