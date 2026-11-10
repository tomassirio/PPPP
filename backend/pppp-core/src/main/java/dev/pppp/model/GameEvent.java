package dev.pppp.model;

import java.util.Map;

/**
 * Something that happened in the game, read from its RAM. Stored in game:events, sent as GAME_EVENT and posted
 * to chat. id/lastPlayer/at are null/0 until stored; lastPlayer is the user whose input ran last.
 */
public record GameEvent(String id, String type, String text, Map<String, Object> details, String lastPlayer, long at) {
    public static GameEvent of(String type, String text, Map<String, Object> details) {
        return new GameEvent(null, type, text, details, null, 0);
    }
}
