package dev.pppp.config;

import dev.pppp.emulator.EmulatorHost;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmulatorConfig {
    /** The emulator module is plain Java; Spring owns its lifecycle (shutdown() runs on context close). */
    @Bean(destroyMethod = "shutdown")
    EmulatorHost emulatorHost() {
        return new EmulatorHost();
    }
}
