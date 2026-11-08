package dev.pppp.service;

import dev.pppp.config.PpppProperties;
import dev.pppp.emulator.EmulatorHost;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * ROMs at {dataDir}/roms/{id}.gb|.gbc, battery saves at {dataDir}/saves/{id}.sav.
 * The id is the sanitized file name stem, so it is stable and filesystem-safe.
 */
@Component
public class CartridgeStore {
    public static final Pattern ID = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int MIN_SIZE = 32 * 1024, MAX_SIZE = 8 * 1024 * 1024;

    public record Cartridge(String id, String title, long size, boolean cgb) {}

    private final Path roms, saves;
    private final EmulatorHost host;

    public CartridgeStore(PpppProperties props, EmulatorHost host) {
        this.roms = props.dataDir().resolve("roms");
        this.saves = props.dataDir().resolve("saves");
        this.host = host;
    }

    public List<Cartridge> list() {
        if (!Files.isDirectory(roms)) return List.of();
        try (Stream<Path> files = Files.list(roms)) {
            return files.map(p -> {
                        String id = p.getFileName().toString().replaceFirst("\\.gbc?$", "");
                        return ID.matcher(id).matches() ? describe(id, p) : null;
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Cartridge::id))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Cartridge upload(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).toLowerCase(Locale.ROOT);
        String ext = name.endsWith(".gbc") ? ".gbc" : name.endsWith(".gb") ? ".gb" : null;
        if (ext == null) throw bad("ROM must be a .gb or .gbc file");
        String id = name.substring(0, name.length() - ext.length()).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (id.length() > 64) id = id.substring(0, 64).replaceAll("-+$", "");
        if (!ID.matcher(id).matches()) throw bad("ROM file name needs letters or digits");
        if (file.getSize() < MIN_SIZE || file.getSize() > MAX_SIZE) throw bad("ROM must be between 32 KB and 8 MB");
        byte[] rom = file.getBytes();
        if (!headerChecksumOk(rom)) throw bad("Not a Game Boy ROM (header checksum mismatch)");
        if (romPath(id) != null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Cartridge " + id + " already exists");
        Files.createDirectories(roms);
        Path target = roms.resolve(id + ext);
        Files.write(target, rom);
        return describe(id, target);
    }

    public void delete(String id) throws IOException {
        Path rom = require(id);
        if (id.equals(host.status().cartridgeId())) throw new IllegalStateException("Cartridge " + id + " is inserted");
        // Battery saves and save states stay: they are the run's progress.
        Files.delete(rom);
    }

    /** The ROM path, or 404. Also guards path params against traversal. */
    public Path require(String id) {
        Path rom = id != null && ID.matcher(id).matches() ? romPath(id) : null;
        if (rom == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown cartridge " + id);
        return rom;
    }

    public Path batterySave(String id) {
        return saves.resolve(id + ".sav");
    }

    private Path romPath(String id) {
        for (String ext : List.of(".gb", ".gbc")) {
            Path p = roms.resolve(id + ext);
            if (Files.isRegularFile(p)) return p;
        }
        return null;
    }

    private static Cartridge describe(String id, Path rom) {
        try {
            byte[] header = new byte[0x150];
            try (var in = Files.newInputStream(rom)) {
                in.readNBytes(header, 0, header.length);
            }
            boolean cgb = (header[0x143] & 0x80) != 0;
            int end = cgb ? 0x143 : 0x144;
            String title = new String(header, 0x134, end - 0x134, StandardCharsets.US_ASCII).replaceAll("[^\\x20-\\x7e]", "").trim();
            return new Cartridge(id, title, Files.size(rom), cgb);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Header checksum at 0x14D over 0x134..0x14C; the boot ROM refuses carts where it is wrong. */
    static boolean headerChecksumOk(byte[] rom) {
        if (rom.length < 0x150) return false;
        int x = 0;
        for (int i = 0x134; i <= 0x14C; i++) x = x - rom[i] - 1;
        return (x & 0xFF) == (rom[0x14D] & 0xFF);
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
