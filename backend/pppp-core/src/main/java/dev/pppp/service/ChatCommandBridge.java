package dev.pppp.service;

import java.util.regex.Pattern;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** With input.chatCommands on, a chat message that looks like a command is also submitted as input. */
@Component
class ChatCommandBridge {
    private static final Pattern COMMAND_LIKE = Pattern.compile("^([a-z]+\\d?)+$", Pattern.CASE_INSENSITIVE);

    private final SettingsService settings;
    private final InputService inputs;

    // @Lazy: InputService may itself call ChatService.postSystem, which would otherwise be a constructor cycle.
    ChatCommandBridge(SettingsService settings, @Lazy InputService inputs) {
        this.settings = settings;
        this.inputs = inputs;
    }

    /** True only if InputService accepted it; rejected (400) or limited (429/503) commands stay plain chat. */
    boolean trySubmit(String userId, String text) {
        if (!COMMAND_LIKE.matcher(text).matches() || !settings.input().chatCommands()) return false;
        try {
            inputs.submit(userId, text, 1);
            return true;
        } catch (ResponseStatusException e) {
            return false;
        }
    }
}
