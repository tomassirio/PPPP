package dev.pppp.model;

/** One chat line as stored in chat:stream and sent to browsers. kind is USER or SYSTEM. */
public record ChatMessage(String id, String user, String name, String text, String kind, boolean command, long at) {}
