package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.model.GameEvent;
import dev.pppp.model.LiveEvent;
import dev.pppp.repository.GameEventRepository;
import dev.pppp.service.CartridgeService;
import dev.pppp.service.ChatService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RamWatcherTest {
    @TempDir Path dir;

    @Test
    void baselineFirstThenDebouncedEvents() throws Exception {
        var ram = new PokemonRedTest();
        ram.setUp();
        byte[] rom = new byte[0x42000];
        System.arraycopy("POKEMON RED".getBytes(), 0, rom, 0x134, 11);
        rom[0x14A] = 1;
        PokemonRedTest.name(rom, PokemonRedTest.BULBASAUR, "BULBASAUR", 1);
        Path romFile = Files.write(dir.resolve("red.gb"), rom);

        EmulatorHost host = mock(EmulatorHost.class);
        when(host.status()).thenReturn(new EmulatorHost.Status(EmulatorHost.Power.ON, "red", false, 1));
        when(host.readBytes(anyInt(), anyInt())).thenAnswer(i -> ram.ram().bytes());
        CartridgeService cartridges = mock(CartridgeService.class);
        when(cartridges.require("red")).thenReturn(romFile);
        GameEventRepository repo = mock(GameEventRepository.class);
        when(repo.append(org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(0));
        List<Object> published = new ArrayList<>();
        var watcher = new RamWatcher(host, cartridges, repo, mock(ChatService.class), published::add);

        watcher.poll(); // title screen: not in play yet, but the box is there, empty
        assertThat(watcher.summary().party()).isEmpty();
        assertThat(watcher.summary().badges()).hasSize(8).noneMatch(b -> b.earned());

        ram.addMon(0, PokemonRedTest.BULBASAUR, "BULBASAUR", 5, 20, 1);
        ram.dexOwned(1);
        for (int i = 0; i < RamWatcher.STABLE_READS; i++) watcher.poll();
        assertThat(gameEvents(published)).isEmpty(); // existing progress is the baseline
        assertThat(watcher.summary().party()).hasSize(1);

        watcher.onLiveEvent(new LiveEvent("INPUT_EXECUTED", Map.of("user", "ash@x.y")));
        ram.mem[ram.off(0xD356)] = 1; // badge
        watcher.poll();
        watcher.poll();
        assertThat(gameEvents(published)).isEmpty(); // not held long enough yet
        watcher.poll();
        assertThat(gameEvents(published)).extracting(GameEvent::type, GameEvent::lastPlayer)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("BADGE_EARNED", "ash@x.y"));

        watcher.onLiveEvent(new LiveEvent("CONSOLE_CHANGED", null)); // e.g. a save state loaded
        ram.mem[ram.off(0xD356)] = 3;
        for (int i = 0; i < RamWatcher.STABLE_READS + 2; i++) watcher.poll();
        assertThat(gameEvents(published)).hasSize(1);
    }

    static List<GameEvent> gameEvents(List<Object> published) {
        return published.stream().map(e -> (LiveEvent) e).filter(e -> e.type().equals("GAME_EVENT"))
                .map(e -> (GameEvent) e.payload()).toList();
    }
}
