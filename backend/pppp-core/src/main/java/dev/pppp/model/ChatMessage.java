package dev.pppp.model;

import java.util.List;
import java.util.Map;

/**
 * One chat line as stored in chat:stream and sent to browsers. kind is USER, SYSTEM (everyone) or ADMIN (admins only).
 * reactions: reaction -> ids of the users who picked it (one reaction per user per message).
 */
public record ChatMessage(String id, String user, String name, String text, String kind, boolean command, long at,
                          Map<String, List<String>> reactions) {}
