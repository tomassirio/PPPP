package dev.pppp.repository;

import dev.pppp.emulator.Button;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * Votes and activity: vote:window:{n} (zset button -> votes), vote:voters:{n} (set of users who voted in window n),
 * mode:votes (hash userId -> mode) and players:active (zset userId -> last activity, epoch ms).
 */
@Repository
public class VoteRepository {
    private static final String ACTIVE = "players:active";
    private static final String MODE_VOTES = "mode:votes";

    private final StringRedisTemplate redis;

    public VoteRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** False when the user already voted in window n. */
    public boolean addVoter(String window, String userId) {
        return redis.opsForSet().add(votersKey(window), userId) != 0;
    }

    public void countVote(String window, Button button) {
        redis.opsForZSet().incrementScore(windowKey(window), button.name(), 1);
    }

    public void deleteWindow(String window) {
        redis.delete(List.of(windowKey(window), votersKey(window)));
    }

    public void markActive(String userId, long atMillis) {
        redis.opsForZSet().add(ACTIVE, userId, atMillis);
    }

    /** Players active after sinceMillis; older entries are trimmed. */
    public Set<String> activeSince(long sinceMillis) {
        redis.opsForZSet().removeRangeByScore(ACTIVE, Double.NEGATIVE_INFINITY, sinceMillis);
        return redis.opsForZSet().range(ACTIVE, 0, -1);
    }

    public void putModeVote(String userId, String mode) {
        redis.opsForHash().put(MODE_VOTES, userId, mode);
    }

    /** userId -> voted mode. */
    public Map<String, String> modeVotes() {
        return redis.<String, String>opsForHash().entries(MODE_VOTES);
    }

    public void clearModeVotes() {
        redis.delete(MODE_VOTES);
    }

    private static String windowKey(String n) { return "vote:window:" + n; }
    private static String votersKey(String n) { return "vote:voters:" + n; }
}
