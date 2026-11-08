package dev.pppp.config;

import java.nio.file.Path;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pppp")
public record PpppProperties(List<String> admins, Path dataDir) {
    public PpppProperties {
        admins = admins == null ? List.of() : admins;
        dataDir = dataDir == null ? Path.of("./data") : dataDir;
    }
}
