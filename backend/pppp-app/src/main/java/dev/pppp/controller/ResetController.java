package dev.pppp.controller;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.security.CurrentUser;
import dev.pppp.service.ResetService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ResetController {
    private final ResetService reset;

    public ResetController(ResetService reset) {
        this.reset = reset;
    }

    /** Factory reset of the current run (see ResetService); the console status afterwards. */
    @PostMapping("/api/v1/admin/factory-reset")
    public EmulatorHost.Status reset(Authentication auth) {
        return reset.resetAll(CurrentUser.id(auth));
    }
}
