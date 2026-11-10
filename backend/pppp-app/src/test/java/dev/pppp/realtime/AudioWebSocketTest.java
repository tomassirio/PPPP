package dev.pppp.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.pppp.TestRedis;
import dev.pppp.emulator.EmulatorHost;
import dev.pppp.emulator.TestRom;
import dev.pppp.model.UserRevokedEvent;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@AutoConfigureMockMvc
@Import({TestRedis.class, ScreenWebSocketTest.BasicAuth.class})
class AudioWebSocketTest {
    @Autowired MockMvc mvc;
    @Autowired EmulatorHost host;
    @Autowired ApplicationEventPublisher events;
    @LocalServerPort int port;
    @TempDir Path dir;

    @Test
    void handshakeGoesThroughTheProductionChain() throws Exception {
        mvc.perform(get("/ws/audio")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ws/audio").with(user("p@x.y").roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(get("/ws/audio").with(user("p@x.y").roles("PLAYER"))).andExpect(status().isBadRequest());
    }

    @Test
    void sendsHeaderThenPcmAndClosesOnRevoke() throws Exception {
        host.insert("test", TestRom.write(dir), dir.resolve("test.sav"));
        host.powerOn();
        try {
            CompletableFuture<String> header = new CompletableFuture<>();
            BlockingQueue<byte[]> chunks = new LinkedBlockingQueue<>();
            CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.setBasicAuth("Friend@x.y", "pw");
            WebSocketSession ws = new StandardWebSocketClient().execute(new AbstractWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession s, TextMessage m) {
                    header.complete(m.getPayload());
                }

                @Override
                protected void handleBinaryMessage(WebSocketSession s, BinaryMessage m) {
                    assertThat(header).isDone(); // header always comes first
                    byte[] b = new byte[m.getPayloadLength()];
                    m.getPayload().get(b);
                    chunks.add(b);
                }

                @Override
                public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                    closed.complete(status);
                }
            }, headers, URI.create("ws://localhost:" + port + "/ws/audio")).get(5, TimeUnit.SECONDS);

            assertThat(header.get(5, TimeUnit.SECONDS))
                    .isEqualTo("{\"sampleRate\":22050,\"channels\":1,\"format\":\"s16le\"}");
            byte[] chunk = chunks.poll(5, TimeUnit.SECONDS);
            assertThat(chunk).isNotNull();
            assertThat(chunk.length).isEven().isBetween(600, 900); // ~1 frame: 22050 / 59.73 * 2 bytes

            events.publishEvent(new UserRevokedEvent("friend@x.y"));
            assertThat(closed.get(5, TimeUnit.SECONDS).getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
            assertThat(ws.isOpen()).isFalse();
        } finally {
            host.powerOff();
            host.eject();
        }
    }
}
