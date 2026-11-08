package dev.pppp.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

class SessionSerializerTest {
    private final RedisSerializer<Object> serializer = new SessionConfig().springSessionDefaultRedisSerializer();

    @Test
    void roundTripsReadableValues() {
        assertThat(serializer.deserialize(serializer.serialize("hello"))).isEqualTo("hello");
    }

    @Test
    void unreadableValueBecomesNullInsteadOfFailingEveryRequest() throws Exception {
        // A class that existed when the session was written but was renamed since (e.g. after a refactor).
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(new Gone());
        }
        byte[] stored = bytes.toByteArray();
        String renamed = new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)
                .replace("SessionSerializerTest$Gone", "SessionSerializerTest$Xone");

        assertThat(serializer.deserialize(renamed.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))).isNull();
    }

    static class Gone implements java.io.Serializable {}
}
