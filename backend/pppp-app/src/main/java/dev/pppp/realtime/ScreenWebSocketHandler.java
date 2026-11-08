package dev.pppp.realtime;

import dev.pppp.security.CurrentUser;
import dev.pppp.model.UserRevokedEvent;
import dev.pppp.emulator.EmulatorHost;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

/**
 * /ws/screen: pushes every second emulated frame as a PNG binary message to every socket. Each socket
 * has a latest-frame slot, so a slow client just skips frames; nothing here blocks the emulator thread.
 * The handshake goes through the normal security chain (ROLE_PLAYER), so the principal is the Authentication.
 */
@Component
public class ScreenWebSocketHandler extends AbstractWebSocketHandler {

    private final Map<WebSocketSession, LatestSlot<byte[]>> sessions = new ConcurrentHashMap<>();
    private final LatestSlot<int[]> encoder = new LatestSlot<>(this::broadcast);
    private volatile byte[] latest;

    ScreenWebSocketHandler(EmulatorHost host) {
        // ponytail: encodes even with no viewers (~30 small PNGs/s); skip when idle if CPU ever matters.
        host.onFrame(encoder::offer);
    }

    private void broadcast(int[] pixels) {
        byte[] png = FrameEncoder.png(pixels);
        latest = png;
        sessions.values().forEach(slot -> slot.offer(png));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        LatestSlot<byte[]> slot = new LatestSlot<>(png -> send(session, png));
        sessions.put(session, slot);
        byte[] last = latest;
        if (last != null) slot.offer(last);
    }

    private void send(WebSocketSession session, byte[] png) {
        if (!session.isOpen()) return;
        try {
            session.sendMessage(new BinaryMessage(png));
        } catch (IOException | IllegalStateException e) {
            sessions.remove(session);
            close(session, CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @EventListener
    public void onUserRevoked(UserRevokedEvent event) {
        sessions.keySet().stream()
                .filter(s -> s.getPrincipal() instanceof Authentication auth && CurrentUser.id(auth).equals(event.userId()))
                .forEach(s -> close(s, CloseStatus.POLICY_VIOLATION));
    }

    private static void close(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException ignored) {
            // already gone
        }
    }
}
