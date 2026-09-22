<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching patterns](../phase-2-caching-patterns/NOTES.md) | [Phase 4 · patterns ➡](../phase-4-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Boot Integration: Notes

Spring makes Redis caching nearly invisible: annotate a service method and its results are
cached automatically, with the database only ever touched on a miss. For anything the cache
abstraction doesn't cover — rate limiting, locks, counters, leaderboards, pub/sub — you drop
down to `RedisTemplate` and talk to Redis directly. This phase covers both layers, plus the
configuration that makes them correct and performant. See `examples/CachingExample.java`,
`examples/DirectRedisExample.java`, and `examples/application.yml`.

## 3.1 — The Spring Cache abstraction

### Definition

Spring's cache abstraction is a **declarative, AOP-based caching layer**: you annotate a
method, and Spring wraps the containing bean in a proxy that intercepts calls to that method,
checking/updating a configured cache (Redis, in this track) around the real method invocation
— without the method's own code ever calling a cache API directly.

### Key Concepts

- **`@EnableCaching`** on a `@Configuration` class turns the abstraction on. Combined with the
  `spring-boot-starter-cache` and `spring-boot-starter-data-redis` dependencies and
  `spring.cache.type: redis` in `application.yml`, Redis becomes the backing store for every
  `@Cacheable`/`@CachePut`/`@CacheEvict` in the app.
- **`@Cacheable`** — the read path. Before running the method, Spring computes a cache key and
  checks Redis. On a **hit**, the method body is **never executed** and the cached value is
  returned directly. On a **miss**, the method runs, its return value is stored in the cache
  under the computed key, and then returned. This is cache-aside (Phase 2.1), automated.
- **`@CachePut`** — always runs the method, then **updates** the cache with the return value,
  regardless of whether an entry already existed. Used on update paths so the cache reflects
  the just-written value without waiting for the next read to repopulate it (Phase 2.1's
  write-through-*ish* update).
- **`@CacheEvict`** — removes an entry (`key = "#id"`) or, with `allEntries = true`, clears an
  entire named cache. Used on delete paths, and on bulk operations where a single fine-grained
  key doesn't make sense (e.g. after a bulk import invalidates many entries at once).
- **Keys default to the method's arguments** and can be customized with **SpEL** expressions:
  `key = "#id"` uses the `id` parameter directly; `key = "#expense.id"` reaches into a field of
  an object parameter. Getting the key expression right matters — two different method
  signatures that should represent "the same" cached entity need to compute to the *same* key,
  or you'll get duplicate/inconsistent cache entries.
- **Values are serialized** before being stored in Redis. The default is JDK serialization,
  which produces opaque binary blobs that aren't human-inspectable from `redis-cli` and aren't
  portable across languages. Configuring a JSON serializer (3.2) is the practical default.
- **Why this matters architecturally:** the caching concern is fully separated from business
  logic via AOP (the same proxy mechanism `@Transactional` uses, Software Design track 05) — a
  service method reads like plain business logic with no `redis.get`/`redis.set` calls
  scattered through it, and caching can be added, removed, or retuned (a different TTL, a
  different cache name) without touching the method body at all.

### Worked example

```java
// From examples/CachingExample.java
@Service
public class ExpenseService {

    private final ExpenseRepository repo;   // Spring Data JPA (Postgres) — source of truth

    public ExpenseService(ExpenseRepository repo) { this.repo = repo; }

    // READ path. Key = "expenses::<id>" in Redis. Miss -> runs repo.findById, caches result.
    @Cacheable(value = "expenses", key = "#id")
    public Expense findById(Long id) {
        return repo.findById(id).orElseThrow();   // only runs on a cache MISS
    }

    // WRITE path. Always saves to Postgres, then refreshes the cache entry to match.
    @CachePut(value = "expenses", key = "#expense.id")
    public Expense update(Expense expense) {
        return repo.save(expense);
    }

    // DELETE path. Removes the DB row and the corresponding cache entry.
    @CacheEvict(value = "expenses", key = "#id")
    public void delete(Long id) {
        repo.deleteById(id);
    }

    // Bulk invalidation: clear the whole "expenses" cache after e.g. a bulk import.
    @CacheEvict(value = "expenses", allEntries = true)
    public void clearCache() { /* no-op body; the annotation does the work */ }
}
```

```
# What actually happens in Redis when findById(1) is called twice in a row:
GET expense:1        # (nil) -- MISS: method body runs, SELECT * FROM expenses WHERE id=1
SET expense:1 '{"id":1,...}' EX 600
GET expense:1        # HIT -- returns cached JSON, repo.findById is NEVER called this time
```

In this example, `findById` looks like an ordinary repository call with no caching code in
sight — the `@Cacheable` annotation is doing all the cache-aside work described in Phase 2.
`update` demonstrates the "always run + refresh" pattern (`@CachePut`), so a client that reads
right after writing sees the new value instead of a stale cached one. `delete` shows
invalidation on removal (`@CacheEvict`) so a deleted expense can never be served from a
lingering cache entry.

### Why it's useful

Declarative caching means the *decision* of what to cache and how long to keep it lives in one
annotation, next to the method it applies to, rather than scattered through business logic as
manual `redis.get`/`redis.set` calls. It also composes cleanly with everything else Spring's
proxy-based AOP does (transactions, security, validation) without any of them needing to know
about each other.

## 3.2 — `RedisTemplate` (direct access)

### Definition

`RedisTemplate<K, V>` (and its common specialization `StringRedisTemplate`, for
`RedisTemplate<String, String>`) is Spring Data Redis's low-level client: a thin, typed wrapper
around the Redis command set, injected as a bean and called directly from application code for
anything the declarative cache abstraction doesn't model.

### Key Concepts

- **When to use it:** the cache abstraction only models "get/put/evict a value by key" — it has
  no concept of atomic increments, conditional sets, sorted-set operations, or pub/sub. Rate
  limiting, distributed locks, counters, and leaderboards (Phase 4) all need direct command
  access, so they go through `RedisTemplate`/`StringRedisTemplate`.
- **The `opsFor*()` views map onto Redis's data structures:** `opsForValue()` for
  strings/counters (`GET`/`SET`/`INCR`), `opsForHash()` for hashes (`HSET`/`HGETALL`),
  `opsForZSet()` for sorted sets (`ZADD`/`ZRANGE`), `opsForList()`, `opsForSet()`, and so on —
  each a thin, typed Java surface over the corresponding Redis commands.
- **Serialization matters here too, and by default is worse than you'd want.** Out of the box,
  `RedisTemplate` uses JDK Java serialization for both keys and values, which produces
  binary-garbled keys when you inspect them with `redis-cli KEYS`, and values that can't be
  read by a non-JVM client. The practical fix is configuring a `StringRedisSerializer` for keys
  (so `redis-cli GET expense:1` shows a readable key) and a
  `GenericJackson2JsonRedisSerializer` for values (so values are portable JSON, inspectable and
  interoperable). `StringRedisTemplate` already assumes `String` keys/values and uses a string
  serializer by default, which is why it's the natural choice for the counter/lock patterns in
  Phase 4 where both key and value are plain strings.
- **Atomic operations map directly onto atomic Redis commands** — `opsForValue().increment()`
  is `INCR`, `opsForValue().setIfAbsent(key, value, ttl)` is `SET key value NX PX ttl`. These
  are the exact building blocks Phase 4's rate limiter and lock are built from.

### Worked example

```java
// From examples/DirectRedisExample.java
public class DirectRedisExample {

    private final StringRedisTemplate redis;   // injected

    public DirectRedisExample(StringRedisTemplate redis) { this.redis = redis; }

    // Fixed-window rate limiter: INCR a per-user counter with a TTL window.
    public boolean allowRequest(String userId, int limit, Duration window) {
        String key = "rate:" + userId;
        Long count = redis.opsForValue().increment(key);   // atomic INCR
        if (count != null && count == 1L) {
            redis.expire(key, window);                      // set TTL on the first hit only
        }
        return count != null && count <= limit;
    }

    // Distributed lock via SET NX (set-if-absent) + TTL (auto-release on crash).
    public boolean tryLock(String resource, String owner, Duration ttl) {
        Boolean ok = redis.opsForValue()
            .setIfAbsent("lock:" + resource, owner, ttl);   // SET key owner NX PX ttl
        return Boolean.TRUE.equals(ok);
    }
}
```

In this example, `allowRequest` calls `increment()`, which Spring Data Redis translates into a
raw `INCR rate:user42` command — atomic on the server side, so concurrent callers never
corrupt the count. The `if (count == 1L)` check only sets the TTL on the very first increment
in a window, so subsequent calls in the same window don't keep resetting the expiry.
`tryLock`'s `setIfAbsent` compiles down to `SET lock:order99 owner-abc NX PX 30000` — a single
atomic command, so two callers racing to acquire the same lock can never both succeed.

### Why it's useful

`RedisTemplate` is the escape hatch that makes Redis a general-purpose middleware tool instead
of "just what `@Cacheable` does." Every pattern in Phase 4 — sessions (via Spring Session, a
separate integration), rate limiting, locking, leaderboards — is built on this same small set
of `opsFor*()` calls mapping onto atomic Redis commands.

## 3.3 — Configuration that matters

### Definition

The Spring/Redis configuration surface (`application.yml`) controls the connection itself, how
the cache abstraction behaves by default, and operational details (pooling, key naming) that
determine whether the integration performs and behaves correctly in production, not just in a
demo.

### Key Concepts

- **Connection & client choice:** Spring Boot defaults to **Lettuce**, a Netty-based,
  asynchronous client built around a small number of shared, thread-safe connections — it
  scales well under high concurrency without needing a large connection pool. **Jedis** is the
  alternative: simpler and synchronous, but each thread needs its own connection, so it
  typically requires an explicit connection pool (`max-active`/`max-idle`/`min-idle`, as
  configured in `application.yml`) sized to expected concurrency.
- **TTL & key prefix:** `spring.cache.redis.time-to-live` sets the **default** TTL applied to
  every cache unless overridden per-cache (below); `key-prefix`/`use-key-prefix` namespace
  every cache key (e.g. `expense:expenses::1`), which keeps multiple caches or multiple
  services sharing one Redis instance from colliding, and makes `redis-cli SCAN`/`KEYS` output
  easy to reason about by service.
- **Null handling:** `cache-null-values: false` (the `application.yml` default here) stops
  Spring from caching a `null` return value at all — directly preventing the "cached absence
  forever" bug discussed in Phase 2.3. Flip it on only if you deliberately want short-TTL
  negative caching and configure that TTL explicitly.
- **Per-cache configuration:** a single global `time-to-live` is a blunt instrument — a
  `RedisCacheManagerBuilderCustomizer` bean (or building named `RedisCacheConfiguration`
  entries) lets different cache names have different TTLs, e.g. `expenses` at 10 minutes
  (volatile, user-editable data) and `users` at an hour (stable, rarely-changing data).
- **Environment-driven connection details:** `SPRING_DATA_REDIS_HOST` (defaulting to
  `localhost` for local dev) resolves to the `redis` service name inside Docker Compose or a
  Kubernetes cluster (tracks 12/14) — the same image runs unmodified in every environment, only
  the environment variable changes, which is the twelve-factor config principle applied to the
  cache connection.

### Worked example

```yaml
# examples/application.yml
spring:
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: 6379
      timeout: 2s
      lettuce:
        pool:
          max-active: 16
          max-idle: 8
          min-idle: 2

  cache:
    type: redis
    redis:
      time-to-live: 600000        # default TTL = 10 min (ms)
      cache-null-values: false    # never cache "not found" indefinitely
      key-prefix: "expense:"
      use-key-prefix: true

  session:
    store-type: redis             # Spring Session -> Redis (Phase 4.1)
    timeout: 30m
```

In this example, every setting maps to a concrete correctness or operational concern: `timeout:
2s` bounds how long a call waits on a slow/unreachable Redis before failing instead of hanging
the request thread; the Lettuce pool sizing bounds how many concurrent connections the app can
open; `time-to-live` and `cache-null-values` are the TTL/staleness controls from Phase 2, now
wired into config instead of left to defaults; `key-prefix` keeps this service's cache entries
visually and operationally distinct from any other service sharing the same Redis instance.

### Why it's useful

None of this configuration is decorative — a missing `timeout` means a Redis outage can hang
every request thread waiting on a dead connection; a missing `key-prefix` means two services
sharing a Redis instance can silently collide on cache keys; `cache-null-values: true` left on
by accident is a real production bug (a "not found" result cached forever). Reading this config
block is reading a checklist of "what could go wrong with this integration" and how it's
guarded against.

## Summary / Key Takeaways

- **`@EnableCaching` + `@Cacheable`/`@CachePut`/`@CacheEvict`** gives you cache-aside (and
  write-through-style refresh, and invalidation) for free, via an AOP proxy — no manual
  `redis.get`/`redis.set` in business code.
- **Cache keys are computed via SpEL** (`key = "#id"`); **values are serialized** — prefer a
  JSON serializer over the JDK default for readability and portability.
- **`RedisTemplate`/`StringRedisTemplate`** is the escape hatch for anything beyond
  get/put/evict — rate limiting, locks, counters, leaderboards — via `opsForValue()`,
  `opsForHash()`, `opsForZSet()`, etc., each mapping directly onto atomic Redis commands.
- **Lettuce** (async, Netty, few shared connections) is Spring Boot's default client;
  **Jedis** (sync, needs an explicit pool) is the alternative.
- **Configuration is where correctness lives:** connection timeout, TTL, `key-prefix`,
  `cache-null-values`, and per-cache TTL overrides are not boilerplate — each one closes off a
  specific production failure mode.
