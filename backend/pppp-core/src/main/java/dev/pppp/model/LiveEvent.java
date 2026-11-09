package dev.pppp.model;

/**
 * Something browsers should see live. Services publish it as a Spring ApplicationEvent;
 * the app module relays it to SSE clients. Types: INPUT_EXECUTED, MODE_CHANGED, MODE_VOTES,
 * VOTE_TALLY, CONSOLE_CHANGED, CHAT_MESSAGE, CHAT_DELETED, GAME_EVENT, GAME_STATE.
 */
public record LiveEvent(String type, Object payload) {}
