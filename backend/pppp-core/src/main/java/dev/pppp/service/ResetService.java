package dev.pppp.service;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.LiveEvent;
import dev.pppp.service.game.RamWatcher;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Admin factory reset: everything about the current run back to zero. The input queue and mode votes are dropped,
 * the inserted cartridge loses its save states and battery save and boots fresh, the game event history and party
 * box are emptied, and chat is wiped. Players, roles, bans, mutes, settings and ROMs stay.
 */
@Service
public class ResetService {
    private final InputService inputs;
    private final ModeService modes;
    private final ConsoleService console;
    private final RamWatcher game;
    private final ChatService chat;
    private final ApplicationEventPublisher events;

    public ResetService(InputService inputs, ModeService modes, ConsoleService console, RamWatcher game, ChatService chat,
                        ApplicationEventPublisher events) {
        this.inputs = inputs;
        this.modes = modes;
        this.console = console;
        this.game = game;
        this.chat = chat;
        this.events = events;
    }

    public EmulatorHost.Status resetAll(String adminId) {
        inputs.flush(adminId);
        modes.clearVotes();
        EmulatorHost.Status status = console.newGame();
        game.newGame();
        chat.clearAll();
        // Browsers drop what they hold locally (chat, last presses, game event list) on APP_RESET.
        events.publishEvent(new LiveEvent("APP_RESET", Map.of("by", adminId)));
        chat.postAdmin(adminId + " reset everything. A brand new adventure begins!");
        return status;
    }
}
