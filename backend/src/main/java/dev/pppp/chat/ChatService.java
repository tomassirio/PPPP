package dev.pppp.chat;

import org.springframework.stereotype.Service;

/** CONTRACT: other workstreams call postSystem for admin actions and autosaves. The chat workstream replaces the body. */
@Service
public class ChatService {

    /** Stores a SYSTEM message in chat:stream and publishes CHAT_MESSAGE. Must never throw. */
    public void postSystem(String text) {
        // chat workstream
    }
}
