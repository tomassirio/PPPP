package dev.pppp.controller;

import dev.pppp.model.GameEvent;
import dev.pppp.model.GameSummary;
import dev.pppp.service.game.RamWatcher;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Game events read from RAM: history, plus the status panel (party, badges, Pokédex). */
@RestController
@RequestMapping("/api/v1")
class GameController {
    private final RamWatcher watcher;

    GameController(RamWatcher watcher) { this.watcher = watcher; }

    @GetMapping("/game-events")
    List<GameEvent> history(@RequestParam(defaultValue = "50") int limit) {
        return watcher.history(limit);
    }

    /** 204 while the inserted game has no profile or has not come into play. */
    @GetMapping("/game/state")
    ResponseEntity<GameSummary> state() {
        GameSummary s = watcher.summary();
        return s == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(s);
    }
}
