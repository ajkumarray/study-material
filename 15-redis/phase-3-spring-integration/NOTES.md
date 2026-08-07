<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching patterns](../phase-2-caching-patterns/NOTES.md) | [Phase 4 · patterns ➡](../phase-4-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Boot Integration: Notes

Spring makes Redis caching nearly invisible: annotate a method and its results are cached,
with the DB hit only on a miss. For patterns beyond caching, drop to `RedisTemplate`. See
`examples/CachingExample.java`, `DirectRedisExample.java`, and `application.yml`.

## 3.1 — The Spring Cache abstraction

- **`@EnableCaching`** on a config class turns on the AOP-based caching layer (a proxy wraps
  your beans — Spring Boot Proxy/AOP, Software Design Phase 5). Add
  `spring-boot-starter-cache` + `spring-boot-starter-data-redis`, set `spring.cache.type:
  redis`, and Redis becomes the cache backend.
- **The annotations** (cache-aside, automated):
  - **`@Cacheable`** — check the cache by key; on hit return it (method **not** called), on
    miss run the method and store the result. The read path.
  - **`@CachePut`** — always run the method **and** update the cache with the result. The
    write-through-style update path.
  - **`@CacheEvict`** — remove an entry (or `allEntries = true`) — invalidation on
    delete/change.
- **Keys** default to the method args; customize with SpEL (`key = "#id"`,
  `key = "#expense.id"`). Values are serialized (JSON via a configured serializer, or Java
  serialization by default — prefer JSON for readability/interop).
- Because it's **declarative**, business code stays clean — no manual `redis.get/set`; the
  caching concern is separated (Software Design: cross-cutting via AOP).

## 3.2 — `RedisTemplate` (direct access)

- For patterns the cache abstraction doesn't cover — rate limiting, locks, counters,
  leaderboards, pub/sub — inject **`RedisTemplate`** / **`StringRedisTemplate`** and call
  operations directly: `opsForValue()` (strings/counters), `opsForHash()`, `opsForZSet()`
  (sorted sets), etc. (the `DirectRedisExample`).
- **Serialization matters:** configure key/value serializers (e.g.
  `StringRedisSerializer` for keys, `GenericJackson2JsonRedisSerializer` for values) so keys
  are human-readable and values are portable — the default JDK serialization produces opaque
  binary keys.
- Atomic ops (`increment`, `setIfAbsent`) map to Redis `INCR`/`SET NX` and are the building
  blocks for Phase 4's patterns.

## 3.3 — Configuration that matters

- **Connection & pooling:** **Lettuce** (default, Netty async) vs Jedis; size the pool
  (`max-active`/`max-idle`) for concurrency (the `application.yml`).
- **TTL & key prefix:** set a default `time-to-live` and a `key-prefix` (namespacing keeps
  caches from colliding and makes them easy to inspect/flush).
- **Null handling:** `cache-null-values: false` avoids caching "not found" forever (Phase 2).
- **Per-cache config:** different caches can have different TTLs via a
  `RedisCacheManagerBuilderCustomizer` (e.g. `expenses` 10m, `users` 1h).
- **Environment wiring:** the Redis host comes from an env var (`SPRING_DATA_REDIS_HOST`),
  resolving to `localhost` in dev and the `redis` service name in Docker/K8s (tracks 12/14).

## Perspective

Spring's cache abstraction gives you **cache-aside for free** — annotate reads with
`@Cacheable`, invalidate on writes with `@CacheEvict`/`@CachePut`, and the DB is spared every
repeat read, with the caching logic cleanly separated from business code via AOP. Drop to
`RedisTemplate` (with sane serializers) for the middleware patterns in Phase 4. Configure
TTLs, key prefixes, and null handling deliberately — those are where correctness (staleness,
collisions) lives.
