package dev.pppp.controller;

import dev.pppp.model.GameSummary;
import dev.pppp.realtime.EventPublisher;
import dev.pppp.realtime.ScreenWebSocketHandler;
import dev.pppp.service.game.RamWatcher;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Anonymous teaser for the sign-in page: how many are playing, progress counters, the party's species and levels
 * (no nicknames), and a screenshot that is a few seconds old. Nothing about who plays: no names, photos, chat or
 * inputs.
 */
@RestController
@RequestMapping("/api/v1/public")
class PublicController {
    private final EventPublisher events;
    private final RamWatcher watcher;
    private final ScreenWebSocketHandler screen;

    PublicController(EventPublisher events, RamWatcher watcher, ScreenWebSocketHandler screen) {
        this.events = events;
        this.watcher = watcher;
        this.screen = screen;
    }

    record Teaser(int online, String playTime, List<GameSummary.Counter> counters, List<Mon> party) {}

    /** icon: id for GET /game/icons/{icon}.png, 0 = none. */
    record Mon(String species, int level, int icon) {}

    @GetMapping("/teaser")
    Teaser teaser() {
        GameSummary s = watcher.summary();
        int online = events.viewers().size();
        if (s == null) return new Teaser(online, null, List.of(), List.of());
        return new Teaser(online, s.playTime(), s.counters(),
                s.party().stream().map(m -> new Mon(m.species(), m.level(), m.icon())).toList());
    }

    /** 204 until the first frame. Cached for a few seconds, so anonymous polling never sees the live game. */
    @GetMapping("/screen.png")
    ResponseEntity<byte[]> screen() {
        byte[] png = screen.latest();
        if (png == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.maxAge(Duration.ofSeconds(5))).body(png);
    }
}
