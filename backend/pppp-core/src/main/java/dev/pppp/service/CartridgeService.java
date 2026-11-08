package dev.pppp.service;

import dev.pppp.emulator.EmulatorHost;
import dev.pppp.exception.NotFoundException;
import dev.pppp.model.Cartridge;
import dev.pppp.repository.CartridgeRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** ROM uploads (validated, id derived from the file name) and the delete-if-not-inserted rule. */
@Service
public class CartridgeService {
    private static final int MIN_SIZE = 32 * 1024, MAX_SIZE = 8 * 1024 * 1024;

    private final CartridgeRepository roms;
    private final EmulatorHost host;

    public CartridgeService(CartridgeRepository roms, EmulatorHost host) {
        this.roms = roms;
        this.host = host;
    }

    public List<Cartridge> list() {
        return roms.findAll();
    }

    public Cartridge upload(String filename, byte[] rom) {
        String name = filename == null ? "" : filename;
        name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1).toLowerCase(Locale.ROOT);
        String ext = name.endsWith(".gbc") ? ".gbc" : name.endsWith(".gb") ? ".gb" : null;
        if (ext == null) throw new IllegalArgumentException("ROM must be a .gb or .gbc file");
        String id = name.substring(0, name.length() - ext.length()).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (id.length() > 64) id = id.substring(0, 64).replaceAll("-+$", "");
        if (!Cartridge.ID.matcher(id).matches()) throw new IllegalArgumentException("ROM file name needs letters or digits");
        if (rom.length < MIN_SIZE || rom.length > MAX_SIZE) throw new IllegalArgumentException("ROM must be between 32 KB and 8 MB");
        if (!Cartridge.headerChecksumOk(rom)) throw new IllegalArgumentException("Not a Game Boy ROM (header checksum mismatch)");
        if (roms.romPath(id) != null) throw new IllegalStateException("Cartridge " + id + " already exists");
        return roms.save(id, ext, rom);
    }

    public void delete(String id) {
        Path rom = require(id);
        if (id.equals(host.status().cartridgeId())) throw new IllegalStateException("Cartridge " + id + " is inserted");
        // Battery saves and save states stay: they are the run's progress.
        roms.delete(rom);
    }

    /** The ROM path, or NotFoundException. Also guards path params against traversal. */
    public Path require(String id) {
        Path rom = id != null && Cartridge.ID.matcher(id).matches() ? roms.romPath(id) : null;
        if (rom == null) throw new NotFoundException("Unknown cartridge " + id);
        return rom;
    }

    public Path batterySave(String id) {
        return roms.batterySave(id);
    }
}
