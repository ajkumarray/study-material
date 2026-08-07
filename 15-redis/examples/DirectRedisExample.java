// When you need more than the cache abstraction — rate limiting, distributed locks,
// counters, leaderboards — use RedisTemplate / StringRedisTemplate directly.
// (Illustrative snippets.)

import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;

public class DirectRedisExample {

    private final StringRedisTemplate redis;   // injected

    public DirectRedisExample(StringRedisTemplate redis) { this.redis = redis; }

    // Fixed-window RATE LIMITER: INCR a per-user counter with a TTL window.
    // First request in the window sets the key + expiry; subsequent ones increment.
    public boolean allowRequest(String userId, int limit, Duration window) {
        String key = "rate:" + userId;
        Long count = redis.opsForValue().increment(key);   // atomic INCR
        if (count != null && count == 1L) {
            redis.expire(key, window);                      // set TTL on first hit
        }
        return count != null && count <= limit;
    }

    // Simple distributed LOCK via SET NX (set-if-not-exists) + TTL (auto-release).
    public boolean tryLock(String resource, String owner, Duration ttl) {
        Boolean ok = redis.opsForValue()
            .setIfAbsent("lock:" + resource, owner, ttl);   // SET key owner NX PX ttl
        return Boolean.TRUE.equals(ok);
        // Release: delete the key ONLY if you still own it (compare-and-delete via Lua)
        // to avoid releasing someone else's lock — see NOTES 4.3.
    }
}
