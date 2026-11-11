package dev.pppp.service.game;

import dev.pppp.model.GameEvent;
import dev.pppp.model.GameSummary;
import java.util.List;

/**
 * Everything game-specific about RAM events: which memory to read, how to parse it into a state, and which
 * changes between two states are worth announcing. {@link RamWatcher} does the polling, debouncing and
 * publishing for every game. To support a new game, implement this and add it to {@link #forRom}.
 *
 * @param <S> the parsed game state; must implement equals (a record), since unchanged states are skipped
 */
public interface GameProfile<S> {

    /** The profile for this ROM, or null when the game has none (it then emits no events). */
    static GameProfile<?> forRom(byte[] rom) {
        if (PokemonRed.matches(rom)) return new PokemonRed(rom);
        return null;
    }

    /** First address of the memory window read on every poll. */
    int ramStart();

    int ramLength();

    /** Parses the window; null while the game is not in play (boot, title screen, loading), so nothing fires. */
    S read(Ram ram);

    /** Events for a confirmed change from before to after; both non-null. */
    List<GameEvent> diff(S before, S after);

    /** state is null before the game first comes into play: an empty summary (no party, nothing earned). */
    GameSummary summary(S state);

    /** PNG for a {@link GameSummary.Member#icon()} id, or null. Called from request threads. */
    default byte[] icon(int id) {
        return null;
    }

    /** PNG sprite sheet of the player walking around, or null. Called from request threads. */
    default byte[] playerSprite() {
        return null;
    }
}
