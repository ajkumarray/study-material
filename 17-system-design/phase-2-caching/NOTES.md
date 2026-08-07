<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · databases scale ➡](../phase-3-databases-scale/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching: Notes

Caching keeps a copy of expensive data somewhere fast so most requests avoid the slow source. It's the **highest-leverage performance lever** (Phase 8) — justified by the latency ratios of Phase 1 (memory is ~1,000,000× faster than a cross-continent hop). "The fastest query is the one you never run."

## 2.1 — Where and how to cache
**Layers** (each closer to the user is faster, harder to invalidate): browser/HTTP cache → CDN/edge → API gateway → in-process (per node, fastest but not shared) → distributed cache (Redis/Memcached, shared across nodes) → the database's own buffer cache.

**Write/read strategies:**
- **Cache-aside (lazy loading)** — the app checks the cache; on a miss it loads from the DB and populates the cache (demo: 5 reads → 1 DB hit). Most common; cache only holds what's actually read; risk: stale data until TTL/invalidation, and a miss penalty.
- **Read-through** — the cache itself loads on a miss (library handles it); same effect, cleaner code.
- **Write-through** — writes go to cache *and* DB synchronously; cache always fresh, writes slower.
- **Write-behind (write-back)** — write to cache, flush to DB asynchronously; fast writes, risk of loss on crash.
- **Write-around** — write straight to the DB, skip the cache (good when written data isn't re-read soon).

## 2.2 — The two hard problems
**Cache invalidation** ("one of the two hard things in CS"): keeping the copy fresh. Approaches — **TTL** (expire after N seconds; simple, allows bounded staleness), **write-invalidation** (delete/update the entry when the source changes), and **versioned keys** (embed a version so old keys are naturally abandoned). Trade-off: shorter TTL = fresher but more misses. Choose staleness the use case tolerates.

**Cache stampede / thundering herd:** a hot key expires (or is cold) and many concurrent requests all miss and hammer the source at once — potentially overloading it exactly when it's busiest (demo: 50 misses → 50 DB hits). Fixes:
- **Single-flight / request coalescing** — only one thread loads a missing key; the rest wait and reuse its result (demo: `computeIfAbsent` → 1 DB hit). Redis: a short-lived lock per key.
- **Early/probabilistic recomputation** — refresh *before* expiry so the key is never cold.
- **Stale-while-revalidate** — serve the stale value while one worker refreshes in the background.

## 2.3 — Eviction, CDNs, and pitfalls
**Eviction** (cache is bounded — Phase 7): **LRU** (evict least-recently-used; good general default), **LFU** (least-frequently-used; good for stable hot sets), **TTL/FIFO**. Redis exposes `maxmemory` + eviction policies. A cache without eviction is a memory leak (Phase 7).

**CDN & HTTP caching:** serve static assets (and cacheable responses) from edge nodes near users — the biggest web-latency win (Phase 8). Controlled by `Cache-Control`, `ETag`, `Last-Modified` headers; the CDN caches at the edge and revalidates.

**What to cache:** hot, read-heavy, expensive-to-produce, staleness-tolerant data (query results, computed aggregates, sessions, rendered fragments). **What not to:** rapidly-changing or must-be-exact data (account balances) unless with strict invalidation.

**Metrics & pitfalls:** track **hit ratio** (low ratio = the cache isn't helping — wrong keys/TTL/size). Pitfalls: caching user-specific data under a shared key (leaks), unbounded caches (Phase 7), the **hot-key** problem (one key overwhelms a single cache node — mitigate with local caching or key replication), and cache **penetration** (queries for non-existent keys always miss — cache negative results or use a Bloom filter).
