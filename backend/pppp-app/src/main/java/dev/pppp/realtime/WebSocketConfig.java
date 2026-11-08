package dev.pppp.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Same-origin only (Spring's default), which blocks cross-site WebSocket hijacking with the session cookie. */
@Configuration
@EnableWebSocket
class WebSocketConfig implements WebSocketConfigurer {
    private final ScreenWebSocketHandler screen;
    private final AudioWebSocketHandler audio;

    WebSocketConfig(ScreenWebSocketHandler screen, AudioWebSocketHandler audio) {
        this.screen = screen;
        this.audio = audio;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(screen, "/ws/screen");
        registry.addHandler(audio, "/ws/audio");
    }
}
