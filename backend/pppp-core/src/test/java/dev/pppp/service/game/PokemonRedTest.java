package dev.pppp.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pppp.model.GameEvent;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PokemonRedTest {
    static final int BULBASAUR = 0x99, IVYSAUR = 0x09, PIDGEY = 0x24; // internal species ids

    PokemonRed red;
    byte[] mem;

    @BeforeEach
    void setUp() {
        byte[] rom = new byte[0x42000];
        System.arraycopy("POKEMON RED".getBytes(), 0, rom, 0x134, 11);
        rom[0x14A] = 1;
        name(rom, BULBASAUR, "BULBASAUR", 1);
        name(rom, IVYSAUR, "IVYSAUR", 2);
        name(rom, PIDGEY, "PIDGEY", 16);
        assertThat(PokemonRed.matches(rom)).isTrue();
        red = new PokemonRed(rom);

        mem = new byte[red.ramLength()];
        put(0xD158, encode("ASH"));
        put(0xD163, new byte[] {0, (byte) 0xFF});
    }

    @Test
    void notInPlayWithoutAPlayerName() {
        mem = new byte[red.ramLength()];
        assertThat(red.read(ram())).isNull();
    }

    @Test
    void announcesProgress() {
        var start = red.read(ram());
        addMon(0, BULBASAUR, "BULBASAUR", 5, 20, 0x1234);
        dexOwned(1);
        var caught = red.read(ram());
        assertThat(types(red.diff(start, caught))).containsExactly("POKEMON_OBTAINED");
        assertThat(red.diff(start, caught).getFirst().text()).isEqualTo("New Pokémon: Bulbasaur! Pokédex 1/151 owned.");

        put(0xD356, new byte[] {0b1}); // Boulder badge
        put(0xD70B, new byte[] {0b100}); // Pewter City
        mem[off(0xD16B + 0x21)] = 6;
        var levelled = red.read(ram());
        assertThat(red.diff(caught, levelled)).extracting(GameEvent::text).containsExactly(
                "Bulbasaur grew to Lv. 6!", "Beat Brock and earned the Boulder Badge! Badges: 1/8.", "First time in Pewter City!");

        mem[off(0xD16B)] = IVYSAUR;
        mem[off(0xD164)] = IVYSAUR;
        dexOwned(2);
        var evolved = red.read(ram());
        assertThat(types(red.diff(levelled, evolved))).containsExactly("EVOLVED", "POKEMON_OBTAINED");

        put(0xD16B + 1, new byte[] {0, 0});
        put(0xDA41, new byte[] {3, 0, 7}); // 3 h, (not maxed), 7 min
        var fainted = red.read(ram());
        assertThat(types(red.diff(evolved, fainted))).containsExactly("POKEMON_FAINTED", "BLACKED_OUT");
        var summary = red.summary(fainted);
        assertThat(summary.party().getFirst().status()).isEqualTo("FNT");
        assertThat(summary.party().getFirst().icon()).isEqualTo(2); // Ivysaur's Pokédex number
        assertThat(summary.badges()).extracting(b -> b.earned()).containsExactly(true, false, false, false, false, false, false, false);
        assertThat(summary.badges().getFirst().place()).isEqualTo("Pewter Gym");
        assertThat(summary.collection().owned()).containsExactly(1, 2);
        assertThat(summary.playTime()).isEqualTo("3h 07m");
        assertThat(summary.collection().seen()).containsExactly(1, 2); // owned counts as seen
        assertThat(summary.collection().names().get(1)).isEqualTo("Ivysaur");
        assertThat(summary.collection().names().get(15)).isNull(); // unseen: not given away
    }

    @Test
    void starterPokedexPreviewIsNotACatch() {
        var start = red.read(ram());
        mem[off(0xD2F7)] = 0b0100_1011; // the game flags 1, 2, 4 and 7 while showing a starter's entry
        assertThat(red.diff(start, red.read(ram()))).isEmpty();
    }

    @Test
    void halfAddedPokemonIsSkippedNotOutOfPlay() {
        var start = red.read(ram());
        addMon(0, BULBASAUR, "BULBASAUR", 5, 20, 1);
        mem[off(0xD16B)] = 0; // species list says Bulbasaur, its data is not written yet (nickname prompt)
        var naming = red.read(ram());
        assertThat(naming).isNotNull();
        assertThat(naming.party()).isEmpty();
        mem[off(0xD16B)] = (byte) BULBASAUR;
        dexOwned(1);
        assertThat(types(red.diff(naming, red.read(ram())))).containsExactly("POKEMON_OBTAINED");
    }

    @Test
    void reorderingThePartyIsNotNews() {
        addMon(0, BULBASAUR, "BULBASAUR", 5, 20, 1);
        addMon(1, PIDGEY, "BIRDY", 9, 30, 2);
        var before = red.read(ram());
        addMon(0, PIDGEY, "BIRDY", 9, 30, 2);
        addMon(1, BULBASAUR, "BULBASAUR", 5, 20, 1);
        assertThat(red.diff(before, red.read(ram()))).isEmpty();
        assertThat(red.summary(before).party()).extracting(m -> m.name()).containsExactly("Bulbasaur", "BIRDY");
    }

    // ---- helpers ----

    static void name(byte[] rom, int id, String name, int dex) {
        byte[] n = encode(name);
        System.arraycopy(n, 0, rom, 0x1C21E + (id - 1) * 10, Math.min(n.length, 10));
        rom[0x41024 + id - 1] = (byte) dex;
    }

    static byte[] encode(String s) {
        byte[] b = new byte[s.length() + 1];
        for (int i = 0; i < s.length(); i++) b[i] = (byte) (0x80 + s.charAt(i) - 'A');
        b[s.length()] = 0x50;
        return b;
    }

    void addMon(int slot, int species, String nick, int level, int hp, int key) {
        int count = Math.max(mem[off(0xD163)], slot + 1);
        mem[off(0xD163)] = (byte) count;
        mem[off(0xD164 + slot)] = (byte) species;
        mem[off(0xD164 + count)] = (byte) 0xFF;
        int base = 0xD16B + slot * 44;
        mem[off(base)] = (byte) species;
        put(base + 1, new byte[] {0, (byte) hp});
        put(base + 0x0C, new byte[] {0, 0});
        put(base + 0x1B, new byte[] {(byte) (key >> 8), (byte) key});
        mem[off(base + 0x21)] = (byte) level;
        put(base + 0x22, new byte[] {0, (byte) hp});
        put(0xD2B5 + slot * 11, encode(nick));
    }

    void dexOwned(int dex) {
        mem[off(0xD2F7 + (dex - 1) / 8)] |= (byte) (1 << ((dex - 1) % 8));
    }

    void put(int address, byte[] bytes) {
        System.arraycopy(bytes, 0, mem, off(address), bytes.length);
    }

    int off(int address) {
        return address - red.ramStart();
    }

    Ram ram() {
        return new Ram(red.ramStart(), mem.clone());
    }

    static List<String> types(List<GameEvent> events) {
        return events.stream().map(GameEvent::type).toList();
    }
}
