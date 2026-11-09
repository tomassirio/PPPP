package dev.pppp.service.game;

import dev.pppp.model.GameEvent;
import dev.pppp.model.GameSummary;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pokémon Red (international release). Addresses are the pret/pokered symbols (wPartyCount, wPokedexOwned,
 * wObtainedBadges, wTownVisitedFlag...); Pokémon names and the Pokédex order are read from the ROM itself.
 * Party members are matched by OT id + DVs, not by slot, so reordering the party fires nothing.
 */
final class PokemonRed implements GameProfile<PokemonRed.State> {

    record Mon(int key, int species, String nick, int level, int hp, int maxHp, int status) {}

    /** BitSets are indexed by Pokédex number - 1 (bit order of the game's flag arrays). */
    record State(List<Mon> party, BitSet owned, BitSet seen, int badges, int towns) {}

    static final int DEX_SIZE = 151;
    private static final int PLAYER_NAME = 0xD158, PARTY_COUNT = 0xD163, PARTY_SPECIES = 0xD164,
            PARTY_MONS = 0xD16B, MON_SIZE = 44, PARTY_NICKS = 0xD2B5, NAME_LENGTH = 11,
            DEX_OWNED = 0xD2F7, DEX_SEEN = 0xD30A, DEX_BYTES = 19, BADGES = 0xD356, TOWNS_VISITED = 0xD70B;
    private static final int ROM_MON_NAMES = 0x1C21E, ROM_DEX_ORDER = 0x41024, SPECIES_COUNT = 190,
            ROM_BASE_STATS = 0x383DE, BASE_STATS_SIZE = 28, ROM_MEW_BASE_STATS = 0x425B;

    /** Bulbasaur, Ivysaur, Charmander, Squirtle (bits = Pokédex number - 1). */
    private static final BitSet STARTER_DEX = BitSet.valueOf(new byte[] {0b0100_1011});
    private static final String[] BADGE_NAMES = {"Boulder", "Cascade", "Thunder", "Rainbow", "Soul", "Marsh", "Volcano", "Earth"};
    private static final String[] LEADERS = {"Brock", "Misty", "Lt. Surge", "Erika", "Koga", "Sabrina", "Blaine", "Giovanni"};
    private static final String[] GYMS = {"Pewter Gym", "Cerulean Gym", "Vermilion Gym", "Celadon Gym", "Fuchsia Gym",
            "Saffron Gym", "Cinnabar Gym", "Viridian Gym"};
    /** Map ids 0-10, the bit order of wTownVisitedFlag. */
    private static final String[] TOWNS = {"Pallet Town", "Viridian City", "Pewter City", "Cerulean City",
            "Lavender Town", "Vermilion City", "Celadon City", "Fuchsia City", "Cinnabar Island", "Indigo Plateau",
            "Saffron City"};

    private final String[] speciesNames = new String[SPECIES_COUNT + 1]; // by internal species id
    private final int[] dexNumber = new int[SPECIES_COUNT + 1];
    private final String[] namesByDex = new String[DEX_SIZE + 1];
    private final int[] speciesByDex = new int[DEX_SIZE + 1];
    private final byte[] rom;
    private final Map<Integer, byte[]> icons = new ConcurrentHashMap<>();

    /** "POKEMON RED" with the non-Japanese destination code; the Japanese release has other addresses. */
    static boolean matches(byte[] rom) {
        return rom.length > 0x41024 + SPECIES_COUNT
                && new String(rom, 0x134, 11, StandardCharsets.US_ASCII).equals("POKEMON RED")
                && rom[0x14A] == 1;
    }

    PokemonRed(byte[] rom) {
        this.rom = rom;
        for (int id = 1; id <= SPECIES_COUNT; id++) {
            speciesNames[id] = titleCase(text(rom, ROM_MON_NAMES + (id - 1) * 10, 10));
            dexNumber[id] = rom[ROM_DEX_ORDER + id - 1] & 0xFF;
            if (dexNumber[id] >= 1 && dexNumber[id] <= DEX_SIZE) {
                namesByDex[dexNumber[id]] = speciesNames[id];
                speciesByDex[dexNumber[id]] = id;
            }
        }
    }

    @Override
    public int ramStart() {
        return PLAYER_NAME;
    }

    @Override
    public int ramLength() {
        return TOWNS_VISITED + 2 - PLAYER_NAME;
    }

    @Override
    public State read(Ram ram) {
        // Armed only with a named player and a well-formed party list: rules out boot, the title screen and loading.
        int first = ram.u8(PLAYER_NAME);
        if (first < 0x80 || first > 0xBF || text(ram.slice(PLAYER_NAME, NAME_LENGTH), 0, NAME_LENGTH).length() == NAME_LENGTH) {
            return null;
        }
        int count = ram.u8(PARTY_COUNT);
        if (count > 6 || ram.u8(PARTY_SPECIES + count) != 0xFF) return null;
        List<Mon> party = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int base = PARTY_MONS + i * MON_SIZE;
            int species = ram.u8(base);
            // Adding a Pokémon, the game writes the species list, asks for a nickname, and only then the data:
            // skip a member that is mid-write rather than calling the whole game out of play.
            if (species < 1 || species > SPECIES_COUNT || species != ram.u8(PARTY_SPECIES + i)) continue;
            party.add(new Mon(ram.u16(base + 0x0C) << 16 | ram.u16(base + 0x1B), species,
                    text(ram.slice(PARTY_NICKS + i * NAME_LENGTH, NAME_LENGTH), 0, NAME_LENGTH),
                    ram.u8(base + 0x21), ram.u16(base + 1), ram.u16(base + 0x22), ram.u8(base + 4)));
        }
        BitSet owned = BitSet.valueOf(ram.slice(DEX_OWNED, DEX_BYTES));
        // Picking a starter, the game shows its Pokédex entry by briefly marking exactly these four as owned
        // (pokered StarterDex), before the player owns anything. Not a catch.
        if (owned.equals(STARTER_DEX)) owned = new BitSet();
        return new State(List.copyOf(party), owned,
                BitSet.valueOf(ram.slice(DEX_SEEN, DEX_BYTES)), ram.u8(BADGES), ram.u16le(TOWNS_VISITED));
    }

    @Override
    public List<GameEvent> diff(State before, State after) {
        List<GameEvent> events = new ArrayList<>();
        Map<Integer, Mon> was = new HashMap<>();
        before.party().forEach(m -> was.put(m.key(), m));
        for (Mon m : after.party()) {
            Mon b = was.get(m.key());
            if (b == null) continue; // joined or withdrawn; a new species shows up as POKEMON_OBTAINED
            if (b.species() != m.species()) {
                events.add(GameEvent.of("EVOLVED", display(b) + " evolved into " + speciesNames[m.species()] + "!",
                        Map.of("from", speciesNames[b.species()], "to", speciesNames[m.species()])));
            }
            if (m.level() > b.level()) {
                events.add(GameEvent.of("LEVEL_UP", display(m) + " grew to Lv. " + m.level() + "!",
                        Map.of("name", display(m), "level", m.level())));
            }
            if (b.hp() > 0 && m.hp() == 0) {
                events.add(GameEvent.of("POKEMON_FAINTED", display(m) + " fainted!", Map.of("name", display(m))));
            }
        }
        boolean allFainted = !after.party().isEmpty() && after.party().stream().allMatch(m -> m.hp() == 0);
        if (allFainted && before.party().stream().anyMatch(m -> m.hp() > 0)) {
            events.add(GameEvent.of("BLACKED_OUT", "All our Pokémon fainted... we blacked out!", Map.of()));
        }

        BitSet caught = (BitSet) after.owned().clone();
        caught.andNot(before.owned());
        caught.stream().filter(i -> i < DEX_SIZE).forEach(i -> events.add(GameEvent.of("POKEMON_OBTAINED",
                "New Pokémon: " + namesByDex[i + 1] + "! Pokédex " + owned(after) + "/" + DEX_SIZE + " owned.",
                Map.of("name", namesByDex[i + 1], "dex", i + 1, "owned", owned(after), "total", DEX_SIZE))));

        for (int bit : newBits(before.badges(), after.badges(), 8)) {
            int count = Integer.bitCount(after.badges());
            events.add(GameEvent.of("BADGE_EARNED", "Beat " + LEADERS[bit] + " and earned the " + BADGE_NAMES[bit]
                    + " Badge! Badges: " + count + "/8.", Map.of("badge", BADGE_NAMES[bit], "count", count, "total", 8)));
        }
        for (int bit : newBits(before.towns(), after.towns(), TOWNS.length)) {
            events.add(GameEvent.of("NEW_TOWN", "First time in " + TOWNS[bit] + "!", Map.of("town", TOWNS[bit])));
        }
        return events;
    }

    @Override
    public GameSummary summary(State s) {
        if (s == null) s = new State(List.of(), new BitSet(), new BitSet(), 0, 0);
        List<GameSummary.Member> party = s.party().stream().map(m -> new GameSummary.Member(display(m),
                speciesNames[m.species()], m.level(), m.hp(), m.maxHp(), status(m), dexNumber[m.species()])).toList();
        List<GameSummary.Badge> badges = new ArrayList<>();
        for (int i = 0; i < 8; i++) badges.add(new GameSummary.Badge(BADGE_NAMES[i], GYMS[i], (s.badges() & 1 << i) != 0));
        return new GameSummary("Pokémon Red", List.of(
                new GameSummary.Counter("Badges", Integer.bitCount(s.badges()), 8),
                new GameSummary.Counter("Pokédex", owned(s), DEX_SIZE),
                new GameSummary.Counter("Seen", s.seen().get(0, DEX_SIZE).cardinality(), DEX_SIZE)), party, badges,
                collection(s));
    }

    private GameSummary.Collection collection(State s) {
        // Owned implies seen, even if a glitch or the starter preview left the seen flag off.
        BitSet seen = s.seen().get(0, DEX_SIZE);
        seen.or(s.owned().get(0, DEX_SIZE));
        List<String> names = new ArrayList<>(DEX_SIZE);
        for (int i = 0; i < DEX_SIZE; i++) names.add(seen.get(i) ? namesByDex[i + 1] : null);
        return new GameSummary.Collection(DEX_SIZE, seen.stream().map(i -> i + 1).boxed().toList(),
                s.owned().get(0, DEX_SIZE).stream().map(i -> i + 1).boxed().toList(), names);
    }

    /** The front sprite of Pokédex number id, decompressed from the ROM. */
    @Override
    public byte[] icon(int id) {
        if (id < 1 || id > DEX_SIZE || speciesByDex[id] == 0) return null;
        return icons.computeIfAbsent(id, dex -> {
            int stats = dex == DEX_SIZE ? ROM_MEW_BASE_STATS : ROM_BASE_STATS + (dex - 1) * BASE_STATS_SIZE;
            int pointer = (rom[stats + 0x0B] & 0xFF) | (rom[stats + 0x0C] & 0xFF) << 8;
            return GenOnePic.png(rom, picBank(dex, stats) * 0x4000 + pointer - 0x4000, 1);
        });
    }

    /**
     * Which ROM bank holds the pictures. Colour hacks that move the sprites store the bank in the base stats'
     * last byte (unused padding in the original); otherwise by internal species id (pokered UncompressMonSprite).
     */
    private int picBank(int dex, int stats) {
        int stored = rom[stats + BASE_STATS_SIZE - 1] & 0xFF;
        if (stored != 0 && stored < rom.length / 0x4000) return stored; // original Mew has 0xFF padding there
        if (dex == DEX_SIZE) return 0x01; // Mew
        int id = speciesByDex[dex];
        return id <= 0x1E ? 0x09 : id <= 0x49 ? 0x0A : id <= 0x73 ? 0x0B : id <= 0x98 ? 0x0C : 0x0D;
    }

    private static int owned(State s) {
        return s.owned().get(0, DEX_SIZE).cardinality();
    }

    private static List<Integer> newBits(int before, int after, int width) {
        List<Integer> bits = new ArrayList<>();
        for (int i = 0; i < width; i++) if ((after & ~before & 1 << i) != 0) bits.add(i);
        return bits;
    }

    /** The nickname, or the nicely cased species name when the player kept the default. */
    private String display(Mon m) {
        String species = speciesNames[m.species()];
        return m.nick().equalsIgnoreCase(species) || m.nick().isEmpty() ? species : m.nick();
    }

    private static String status(Mon m) {
        if (m.hp() == 0) return "FNT";
        int s = m.status();
        if ((s & 0x07) != 0) return "SLP";
        if ((s & 0x08) != 0) return "PSN";
        if ((s & 0x10) != 0) return "BRN";
        if ((s & 0x20) != 0) return "FRZ";
        if ((s & 0x40) != 0) return "PAR";
        return null;
    }

    /** Decodes the game's character set up to the 0x50 terminator; unknown glyphs become '?'. */
    static String text(byte[] b, int from, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < from + max; i++) {
            int c = b[i] & 0xFF;
            if (c == 0x50) break;
            if (c >= 0x80 && c <= 0x99) sb.append((char) ('A' + c - 0x80));
            else if (c >= 0xA0 && c <= 0xB9) sb.append((char) ('a' + c - 0xA0));
            else if (c >= 0xF6) sb.append((char) ('0' + c - 0xF6));
            else sb.append(switch (c) {
                case 0x7F -> ' ';
                case 0xE0 -> '\'';
                case 0xE3 -> '-';
                case 0xE6 -> '?';
                case 0xE7 -> '!';
                case 0xE8 -> '.';
                case 0xEF -> '♂';
                case 0xF5 -> '♀';
                case 0xF4 -> ',';
                case 0xBA -> 'é';
                default -> '?';
            });
        }
        return sb.toString();
    }

    /** "MR.MIME" -> "Mr.Mime", "NIDORAN♂" -> "Nidoran♂". */
    static String titleCase(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean start = true;
        for (char c : s.toCharArray()) {
            sb.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            start = c == ' ' || c == '.' || c == '-';
        }
        return sb.toString();
    }
}
