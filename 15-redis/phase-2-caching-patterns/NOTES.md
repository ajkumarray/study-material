<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · middleware](../phase-1-middleware/NOTES.md) | [Phase 3 · spring integration ➡](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching Patterns: Notes

Caching is Redis's flagship job. The patterns differ in **who** reads/writes the cache and
**when** — and the hard parts are always **invalidation** and **failure under load**. This
overlaps with System Design Phase 2; here it's concrete with Redis.

## 2.1 — The read/write strategies

- **Cache-aside (lazy loading)** — the default, what `@Cacheable` does. The *app* checks the
  cache; on a **miss** it reads the DB, stores the result, and returns it. Only requested
  data is cached; the cache and DB are separate. Downsides: the first request is always a
  miss (cold), and stale data if the DB changes without invalidation.
- **Read-through** — the *cache library* fetches from the DB on a miss (the app only talks to
  the cache). Same effect as cache-aside, but the loading logic lives in the cache layer.
- **Write-through** — writes go to the cache **and** the DB synchronously, so the cache is
  always fresh. Slower writes; wasted caching of data that's never read.
- **Write-behind (write-back)** — writes go to the cache immediately and are flushed to the
  DB **asynchronously**. Fast writes, but risk of data loss if the cache dies before flush;
  more complex. Used for high write throughput where some loss is tolerable.
- **Most web apps: cache-aside for reads + explicit invalidation on writes** — simple and
  robust. (The example service uses `@Cacheable` reads + `@CacheEvict`/`@CachePut` on writes.)

## 2.2 — TTL & eviction

- **TTL (time-to-live):** every cache entry should **expire** (`EX`/`time-to-live`). TTL is
  the safety net that bounds staleness even if you miss an invalidation — a cache entry can
  only be wrong for at most its TTL. Choose per data volatility (seconds for hot/volatile,
  minutes/hours for stable).
- **Eviction policies** (when `maxmemory` is hit): **LRU** (least-recently-used — the common
  default `allkeys-lru`), **LFU** (least-frequently-used), `volatile-*` (only evict keys with
  a TTL), `noeviction` (reject writes). For a cache, `allkeys-lru`/`lfu` keeps the hot set.
- Cache is **bounded memory** — you're always keeping the *hot* subset, not everything.

## 2.3 — Invalidation, staleness & stampede

The two hard problems:

- **Invalidation** — "there are only two hard things… cache invalidation and naming." On a
  write, you must **evict or update** the cached entry (`@CacheEvict`/`@CachePut`), or readers
  get stale data until TTL. Strategies: write-time invalidation (precise), TTL-only (simple,
  bounded staleness), or event-based (invalidate on a change event).
- **Cache stampede / thundering herd** — a popular key expires and *thousands* of concurrent
  requests all miss and hammer the DB at once (System Design Phase 2/8). Mitigations:
  - **Locking / single-flight** — only one request recomputes; others wait for the result.
  - **Early/probabilistic recomputation** — refresh *before* expiry so it never fully expires
    under load.
  - **Jittered TTLs** — randomize expiry so many keys don't expire simultaneously.
  - **Negative caching (carefully)** — cache "not found" briefly to stop repeated misses, but
    with a short TTL (the config sets `cache-null-values: false` by default to avoid caching
    absence forever).
- **Consistency reality:** a cache is eventually consistent with the DB. Accept a small
  staleness window (bounded by TTL) — if you truly can't, don't cache that data.

## Perspective

Caching is a **latency/load win in exchange for a controlled staleness risk**. Pick
**cache-aside + TTL + invalidate-on-write** as the default; size memory with an LRU/LFU
eviction policy; and design against **stampede** for hot keys (locking, early refresh,
jitter). The discipline is: cache the hot, expire everything, invalidate on change, and
never let the cache become a second source of truth.
