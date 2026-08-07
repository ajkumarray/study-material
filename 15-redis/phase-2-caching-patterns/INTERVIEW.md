<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · middleware](../phase-1-middleware/NOTES.md) | [Phase 3 · spring integration ➡](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching Patterns: Interview Q&A

⭐ = asked constantly.

**Q: Explain cache-aside.** ⭐⭐
The app checks the cache first; on a hit it returns the cached value, on a miss it reads the
DB, stores the result in the cache, and returns it. Only requested data is cached. It's the
most common pattern (what Spring's `@Cacheable` implements) — simple, but the first read is
always a miss and stale data needs explicit invalidation.

**Q: Cache-aside vs write-through vs write-behind?** ⭐⭐
Cache-aside: app populates the cache lazily on read misses. Write-through: writes go to cache
and DB synchronously, keeping the cache fresh at the cost of slower writes. Write-behind:
writes hit the cache and flush to the DB asynchronously — fast but risks loss if the cache
dies before flushing. Most apps use cache-aside for reads plus invalidation on writes.

**Q: Why put a TTL on cache entries?** ⭐⭐
TTL bounds staleness — even if you miss an invalidation, an entry can only be wrong for at
most its TTL before it expires and re-loads from the DB. It also naturally evicts cold data.
Tune it to the data's volatility.

**Q: What eviction policies does Redis have?** ⭐
When `maxmemory` is reached: LRU (`allkeys-lru`, evict least-recently-used — common default),
LFU (`allkeys-lfu`, least-frequently-used), `volatile-*` variants (only evict keys with a
TTL), and `noeviction` (reject writes). For a cache you want LRU/LFU to keep the hot set.

**Q: What is a cache stampede and how do you prevent it?** ⭐⭐
When a hot key expires and many concurrent requests all miss and hit the DB simultaneously,
overloading it. Prevent with single-flight locking (one request recomputes, others wait),
early/probabilistic refresh before expiry, and jittered TTLs so keys don't all expire at
once.

**Q: How do you keep the cache from serving stale data?** ⭐
Invalidate on write — evict or update the entry when the underlying data changes
(`@CacheEvict`/`@CachePut`) — and set a TTL as a backstop so staleness is bounded even if an
invalidation is missed. Choose write-time invalidation for precision or TTL-only for
simplicity.

**Q: Should you cache "not found" results?** *nuance*
Sometimes — negative caching stops repeated misses from hammering the DB for a
nonexistent key, but use a short TTL so a later-created record isn't hidden. Caching nulls
indefinitely is a classic bug; Spring's `cache-null-values: false` avoids it by default.

**Q: What's the fundamental trade-off of caching?**
Lower latency and DB load in exchange for potential staleness and added complexity
(invalidation, memory management, stampede handling). A cache is eventually consistent with
the source of truth — acceptable for most reads, but data that must be perfectly current
shouldn't be cached (or needs write-through + careful invalidation).
