package dev.pppp.api.admin;

import dev.pppp.api.EventPublisher;
import dev.pppp.chat.ChatService;
import dev.pppp.engine.EmulatorHost;
import dev.pppp.jobs.AutoSave;
import dev.pppp.storage.SaveStore;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Save states of the inserted cartridge, plus POST /autosave/run. */
@RestController
@RequestMapping("/api/v1/admin")
public class SaveStateController {
    private final SaveStore saves;
    private final AutoSave autoSave;
    private final EmulatorHost host;
    private final ChatService chat;
    private final EventPublisher events;

    public SaveStateController(SaveStore saves, AutoSave autoSave, EmulatorHost host, ChatService chat, EventPublisher events) {
        this.saves = saves;
        this.autoSave = autoSave;
        this.host = host;
        this.chat = chat;
        this.events = events;
    }

    @GetMapping("/save-states")
    public List<SaveStore.SaveState> list() {
        String cartridgeId = host.status().cartridgeId();
        return cartridgeId == null ? List.of() : saves.list(cartridgeId);
    }

    @PostMapping("/save-states")
    @ResponseStatus(HttpStatus.CREATED)
    public SaveStore.SaveState take() {
        var state = saves.take(SaveStore.Kind.manual);
        chat.postSystem("Save state taken (" + state.id() + ")");
        return state;
    }

    @PostMapping("/save-states/{id}/load")
    public SaveStore.SaveState load(@PathVariable String id) {
        var state = saves.load(id);
        chat.postSystem("Save state " + id + " loaded");
        events.publish("CONSOLE_CHANGED", host.status());
        return state;
    }

    @PostMapping("/autosave/run")
    @ResponseStatus(HttpStatus.CREATED)
    public SaveStore.SaveState runAutosave() {
        var state = autoSave.run();
        if (state == null) throw new IllegalStateException("Console is off");
        return state;
    }
}
