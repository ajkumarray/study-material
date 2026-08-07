<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching patterns](../phase-2-caching-patterns/NOTES.md) | [Phase 4 · patterns ➡](../phase-4-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Boot Integration: Interview Q&A

⭐ = asked constantly.

**Q: How do you add Redis caching to a Spring Boot app?** ⭐⭐
Add `spring-boot-starter-cache` + `spring-boot-starter-data-redis`, put `@EnableCaching` on a
config class, set `spring.cache.type: redis`, and annotate service methods with `@Cacheable`.
Spring proxies the beans and caches results in Redis — cache-aside with no manual get/set.

**Q: `@Cacheable` vs `@CachePut` vs `@CacheEvict`?** ⭐⭐
`@Cacheable` returns a cached value if present, else runs the method and caches the result
(read path). `@CachePut` always runs the method and updates the cache (write/refresh).
`@CacheEvict` removes entries (invalidation on change/delete). Together they implement
cache-aside with invalidation.

**Q: How does `@Cacheable` work under the hood?** ⭐
Spring AOP wraps the bean in a proxy; a call is intercepted, the cache is checked by the
computed key, and the real method runs only on a miss (then the result is stored). It's the
same proxy mechanism as `@Transactional` — a caveat is that self-invocation (a method calling
another in the same bean) bypasses the proxy and the cache.

**Q: How are cache keys and values handled?**
Keys default to the method arguments and are customizable via SpEL (`key = "#id"`). Values
are serialized — configure `StringRedisSerializer` for keys and a JSON serializer
(`GenericJackson2JsonRedisSerializer`) for values so entries are readable and portable rather
than opaque JDK-serialized bytes.

**Q: When do you use RedisTemplate instead of the cache abstraction?** ⭐
When you need operations beyond caching — atomic counters (rate limiting), `SET NX` locks,
sorted sets (leaderboards), hashes (sessions), pub/sub. `RedisTemplate`/`StringRedisTemplate`
expose `opsForValue`/`opsForHash`/`opsForZSet`, etc., for direct control.

**Q: How do you set different TTLs for different caches?**
Provide a `RedisCacheManagerBuilderCustomizer` (or `RedisCacheConfiguration` per cache name)
so, e.g., the `expenses` cache expires in 10 minutes and `users` in an hour. A single global
`time-to-live` sets the default; per-cache config overrides it.

**Q: Lettuce vs Jedis?** *nuance*
Both are Redis Java clients. Lettuce (Spring Boot's default) is Netty-based, asynchronous, and
thread-safe with a shared connection, scaling well under concurrency. Jedis is simpler and
synchronous, needing a connection pool. Lettuce is the modern default unless you have a
specific reason.

**Q: How do you point the app at Redis across environments?**
Externalize the host/port (e.g. `SPRING_DATA_REDIS_HOST`) so it's `localhost` in dev and the
service name (`redis`) in Docker Compose/Kubernetes. This is the 12-factor config principle —
same image, env-specific connection details.
