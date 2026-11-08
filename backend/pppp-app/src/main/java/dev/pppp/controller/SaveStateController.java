package dev.pppp.controller;

import dev.pppp.model.SaveState;
import dev.pppp.service.AutoSave;
import dev.pppp.service.SaveStateService;
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
    private final SaveStateService saves;
    private final AutoSave autoSave;

    public SaveStateController(SaveStateService saves, AutoSave autoSave) {
        this.saves = saves;
        this.autoSave = autoSave;
    }

    @GetMapping("/save-states")
    public List<SaveState> list() { return saves.list(); }

    @PostMapping("/save-states")
    @ResponseStatus(HttpStatus.CREATED)
    public SaveState take() { return saves.takeManual(); }

    @PostMapping("/save-states/{id}/load")
    public SaveState load(@PathVariable String id) { return saves.load(id); }

    @PostMapping("/autosave/run")
    @ResponseStatus(HttpStatus.CREATED)
    public SaveState runAutosave() {
        var state = autoSave.run();
        if (state == null) throw new IllegalStateException("Console is off");
        return state;
    }
}
