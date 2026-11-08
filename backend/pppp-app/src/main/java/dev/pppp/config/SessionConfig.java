package dev.pppp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

@Configuration
public class SessionConfig {
    private static final Logger log = LoggerFactory.getLogger(SessionConfig.class);

    /**
     * Sessions are Java-serialized into Redis. After a deploy that renames a serialized class, old sessions
     * can't be read; without this every request carrying that cookie fails with 500. Treat an unreadable
     * value as absent instead: the user is simply signed out and logs in again.
     */
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer() {
        var jdk = new JdkSerializationRedisSerializer(getClass().getClassLoader());
        return new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) {
                return jdk.serialize(value);
            }

            @Override
            public Object deserialize(byte[] bytes) {
                try {
                    return jdk.deserialize(bytes);
                } catch (SerializationException e) {
                    log.info("Ignoring unreadable session attribute: {}", e.getMostSpecificCause().getMessage());
                    return null;
                }
            }
        };
    }
}
