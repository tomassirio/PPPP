package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.AccessRequest;
import java.util.List;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** Pending sign-in requests: hash access:requests (user id -> AccessRequest JSON); rejected ids: set access:rejected. */
@Repository
public class AccessRequestRepository {
    private static final String REQUESTS = "access:requests";
    private static final String REJECTED = "access:rejected";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public AccessRequestRepository(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public long count() {
        return redis.opsForHash().size(REQUESTS);
    }

    public boolean exists(String id) {
        return redis.opsForHash().hasKey(REQUESTS, id);
    }

    public void save(AccessRequest r) {
        try {
            redis.opsForHash().put(REQUESTS, r.email(), json.writeValueAsString(r));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<AccessRequest> findAll() {
        return redis.<String, String>opsForHash().values(REQUESTS).stream().map(raw -> {
            try {
                return json.readValue(raw, AccessRequest.class);
            } catch (JsonProcessingException e) {
                return null;
            }
        }).filter(Objects::nonNull).toList();
    }

    /** Returns false if there was no request. */
    public boolean remove(String id) {
        return redis.opsForHash().delete(REQUESTS, id) > 0;
    }

    public boolean isRejected(String id) {
        return Boolean.TRUE.equals(redis.opsForSet().isMember(REJECTED, id));
    }

    public void reject(String id) {
        redis.opsForSet().add(REJECTED, id);
    }

    public void unreject(String id) {
        redis.opsForSet().remove(REJECTED, id);
    }
}
