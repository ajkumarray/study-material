<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching patterns](../phase-2-caching-patterns/NOTES.md) | [Phase 4 · patterns ➡](../phase-4-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Boot Integration: Interview Q&A

⭐ = asked constantly.

**Q: How do you add Redis caching to a Spring Boot app, step by step?** ⭐⭐
Add `spring-boot-starter-cache` and `spring-boot-starter-data-redis` as dependencies, put
`@EnableCaching` on a `@Configuration` class, and set `spring.cache.type: redis` in
`application.yml` so Redis is the backing store rather than Spring's default in-memory
`ConcurrentMapCacheManager`. From there, annotate service methods: `@Cacheable(value =
"expenses", key = "#id")` on a read method is enough to get cache-aside behavior with zero
manual Redis calls — Spring proxies the bean and handles the check/load/store sequence for
you.

**Q: Walk through `@Cacheable` vs `@CachePut` vs `@CacheEvict`.** ⭐⭐
`@Cacheable` is the read path: Spring checks the cache by the computed key first; on a hit it
returns the cached value and the method body never runs; on a miss the method runs and its
return value is stored before being returned. `@CachePut` always runs the method — it never
short-circuits — and then writes the return value into the cache, which is what you want on an
update so a reader right after the write sees the fresh value instead of a stale cached one.
`@CacheEvict` removes an entry (or, with `allEntries = true`, the whole named cache) and is used
on deletes or bulk operations. Together, `@Cacheable` for reads plus `@CacheEvict`/`@CachePut`
on writes is exactly cache-aside-with-invalidation from Phase 2, expressed declaratively.

**Q: How does `@Cacheable` actually work under the hood?** ⭐
Spring wraps the bean in a proxy at startup (the same AOP mechanism `@Transactional` uses). A
call to an annotated method is intercepted by that proxy *before* it reaches the real object:
the proxy evaluates the key expression, checks the configured cache, and only forwards the call
to the actual method if it's a miss, caching the result afterward. The important caveat this
implies: **self-invocation bypasses the proxy**. If a method inside the same bean calls another
`@Cacheable` method directly (`this.findById(id)`), that call never goes through the proxy, so
caching (and `@Transactional`, for the same reason) is silently skipped. The fix is calling
through a separate bean/injected reference, or restructuring so the cached method is called
externally.

*Follow-up: what other Spring feature has this exact same self-invocation caveat?*
`@Transactional` — it's the same underlying proxy-based AOP mechanism, so the same bypass
applies.

**Q: How are cache keys and values actually handled?**
Keys default to the method's arguments, but are usually customized with a SpEL expression —
`key = "#id"` for a simple parameter, or `key = "#expense.id"` to pull a field out of an object
argument. Getting this right matters because two code paths meant to represent the same cached
entity need to compute the same key, or you get duplicate, inconsistent entries. Values are
serialized before being written to Redis; the default is JDK Java serialization, which produces
opaque binary blobs — unreadable from `redis-cli` and not portable outside the JVM. The
practical setup configures a `StringRedisSerializer` for keys and a
`GenericJackson2JsonRedisSerializer` for values, so cache entries are human-readable JSON.

**Q: When would you use `RedisTemplate` instead of the cache abstraction?** ⭐
Whenever you need an operation the cache abstraction has no concept of: an atomic increment for
a counter or rate limiter, a conditional `SET ... NX` for a distributed lock, sorted-set
operations for a leaderboard or sliding window, or hash operations for a session's fields. The
cache abstraction only models "get/put/evict a value by key" — anything richer means injecting
`RedisTemplate` or `StringRedisTemplate` and calling `opsForValue()`, `opsForHash()`,
`opsForZSet()`, etc. directly, each of which maps onto a specific Redis command
(`increment()` → `INCR`, `setIfAbsent()` → `SET ... NX`).

**Q: `RedisTemplate` vs `StringRedisTemplate` — what's the difference?**
`RedisTemplate<K, V>` is generic over key/value types and, without extra configuration, uses
JDK serialization for both — meaning keys show up as binary garbage in `redis-cli KEYS`.
`StringRedisTemplate` is a pre-configured specialization (`RedisTemplate<String, String>`) that
assumes string keys and values and uses `StringRedisSerializer` by default, so keys and values
are human-readable out of the box. It's the natural choice for the counter/lock patterns in
Phase 4, where both the key (`"rate:" + userId`) and the value (an owner token) are plain
strings; for caching structured objects, a `RedisTemplate` with a JSON value serializer (or
just letting the Spring Cache abstraction handle serialization) is more appropriate.

**Q: How do you configure different TTLs for different caches?**
`spring.cache.redis.time-to-live` sets one *global default*. To vary it per cache name (say,
`expenses` at 10 minutes because expense data changes often, and `users` at an hour because
profile data is stable), register a `RedisCacheManagerBuilderCustomizer` bean that builds a
distinct `RedisCacheConfiguration` (with its own `entryTtl(...)`) per cache name and registers
it with the `RedisCacheManager.Builder`. Without a customizer, every `@Cacheable`/`@CachePut`
cache shares the one global TTL.

**Q: Lettuce vs Jedis — which does Spring Boot default to, and why?** *nuance*
Spring Boot defaults to **Lettuce**. It's built on Netty and is asynchronous, and — critically
— it's thread-safe over a small number of *shared* connections, so it scales to high
concurrency without needing a large connection pool. **Jedis** is simpler and synchronous, but
each thread needs its own connection, so using it well under concurrency requires an explicit
connection pool (which is exactly what the `lettuce.pool` block in `application.yml` configures
— sized `max-active`/`max-idle`/`min-idle` for expected load, even though Lettuce needs a
pool less critically than Jedis does). Lettuce is the modern default unless you have a specific
reason (an existing Jedis-based codebase, a library that only integrates with Jedis) to choose
otherwise.

**Q: How do you point a Spring Boot app at different Redis instances across environments?**
Externalize the connection details through an environment variable —
`${SPRING_DATA_REDIS_HOST:localhost}` resolves to `localhost` for local development and to the
`redis` service/DNS name inside Docker Compose or Kubernetes in deployed environments. The same
built image/jar runs unmodified everywhere; only the environment variable changes. This is the
twelve-factor-app config principle applied specifically to the cache connection, the same
pattern used for the database URL and other externalized settings.

**Q: What does `cache-null-values: false` protect against, concretely?**
Without it, if a `@Cacheable` method's body returns `null` (e.g. a lookup that finds nothing),
Spring would cache that `null` under the computed key just like any other value — meaning a
record created a moment later would still appear "not found" to every reader until that cache
entry's TTL expires. Setting `cache-null-values: false` tells Spring to simply not cache `null`
returns at all, so a miss stays a real miss on every call until there's an actual value to
cache. It's a deliberate default here rather than an oversight, guarding against exactly the
"cached absence forever" bug from Phase 2.3.

**Q: What would you check first if `@Cacheable` "isn't working" — the method runs every time
even though the same argument is passed repeatedly?** *nuance*
Two usual suspects: self-invocation (the call is coming from inside the same bean, bypassing
the AOP proxy entirely — move the call to go through an injected reference instead), or the
key expression isn't actually producing a stable/equal key across calls (e.g. keying on an
object without a proper `equals()`/`toString()`, or on a SpEL expression that evaluates
differently than expected). Checking `redis-cli KEYS 'expenses::*'` after a call is the fastest
way to see whether an entry is being written at all, and under what key.
