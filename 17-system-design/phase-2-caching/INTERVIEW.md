<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · databases scale ➡](../phase-3-databases-scale/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching: Interview Q&A

⭐ = asked constantly.

**Q: Explain cache-aside vs write-through vs write-behind.** ⭐⭐
Cache-aside: app loads from DB on a miss and populates the cache (lazy; most common). Write-through: writes go to cache and DB synchronously (always fresh, slower writes). Write-behind: write to cache, flush to DB async (fast writes, risk of loss). Pick by read/write mix and staleness tolerance.

**Q: What is cache invalidation and why is it hard?** ⭐⭐
Keeping the cached copy consistent with the source. Hard because updates happen in many places and across nodes; strategies (TTL, explicit delete-on-write, versioned keys) trade freshness against miss rate and complexity. Short TTL = fresher but more misses; there's no free lunch.

**Q: What is a cache stampede and how do you prevent it?** ⭐⭐
A hot key expires (or is cold) and many concurrent requests all miss and hit the source at once, overloading it. Fixes: single-flight/request coalescing (one loader, others wait — demo: 50 misses → 1 DB hit), early/probabilistic refresh before expiry, and stale-while-revalidate (serve stale while refreshing).

**Q: LRU vs LFU eviction?**
LRU evicts the least-recently-used entry — a strong general default, adapts to changing hot sets. LFU evicts the least-frequently-used — better for stable popularity but can keep once-popular junk. Both bound memory; a cache without eviction is a memory leak.

**Q: Where can you cache in a web stack?** ⭐
Browser/HTTP cache, CDN/edge, API gateway, in-process (per node), distributed cache (Redis/Memcached, shared), and the DB's own buffer cache. Closer to the user = faster but harder to invalidate. Use several layers.

**Q: What should you not cache?**
Data that must be exact and current (balances, permissions) unless you have strict invalidation, rapidly-changing data with poor hit ratios, and per-user data under shared keys (leakage). Cache hot, read-heavy, expensive, staleness-tolerant data.

**Q: How do you measure whether a cache is working?**
Hit ratio (hits / total). A low ratio means wrong keys, too-short TTL, or too-small a cache — it may add latency without benefit. Also watch eviction rate and the source's load reduction.

**Q: What's the hot-key problem and how do you handle it?**
One extremely popular key overwhelms a single cache node's capacity. Mitigations: an in-process/local cache in front of the distributed cache, replicating the key across nodes, or sharding the value. Related: cache penetration (many misses for non-existent keys) — cache negative results or use a Bloom filter.

**Q: How do HTTP caching headers work?**
`Cache-Control` (max-age, public/private, no-store) sets cacheability/TTL; `ETag`/`Last-Modified` enable conditional revalidation (304 Not Modified) so unchanged assets aren't re-downloaded. CDNs and browsers honor these — the front-end caching lever (Phase 8).

**Q: Redis vs Memcached for caching?**
Both are fast in-memory stores. Memcached: simpler, multithreaded, pure key-value LRU cache. Redis: richer data types, persistence, pub/sub, scripting, clustering — a data-structure store often used as cache + more. Default to Redis unless you specifically want Memcached's simplicity. (Redis depth: track 15.)
