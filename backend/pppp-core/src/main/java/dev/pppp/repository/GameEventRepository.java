package dev.pppp.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pppp.model.GameEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/** game:events, a stream of {type, text, details (JSON), lastPlayer} capped at ~1,000 entries. */
@Repository
public class GameEventRepository {
    static final String STREAM = "game:events";

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public GameEventRepository(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = redis;
        this.json = json;
    }

    public GameEvent append(GameEvent e) {
        Map<String, String> fields = new HashMap<>(Map.of("type", e.type(), "text", e.text(), "details", write(e.details())));
        if (e.lastPlayer() != null) fields.put("lastPlayer", e.lastPlayer());
        RecordId id = redis.opsForStream().add(StreamRecords.newRecord().in(STREAM).ofMap(fields),
                XAddOptions.maxlen(1000).approximateTrimming(true));
        return new GameEvent(id.getValue(), e.type(), e.text(), e.details(), e.lastPlayer(), id.getTimestamp());
    }

    public List<GameEvent> newest(int limit) {
        List<MapRecord<String, Object, Object>> records =
                redis.opsForStream().reverseRange(STREAM, Range.unbounded(), Limit.limit().count(limit));
        return records == null ? List.of() : records.stream().map(this::toEvent).toList();
    }

    private GameEvent toEvent(MapRecord<String, Object, Object> r) {
        Map<Object, Object> f = r.getValue();
        Map<String, Object> details;
        try {
            details = json.readValue((String) f.get("details"), new TypeReference<>() {});
        } catch (JsonProcessingException | IllegalArgumentException e) {
            details = Map.of();
        }
        return new GameEvent(r.getId().getValue(), (String) f.get("type"), (String) f.get("text"), details,
                (String) f.get("lastPlayer"), r.getId().getTimestamp());
    }

    private String write(Map<String, Object> details) {
        try {
            return json.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
