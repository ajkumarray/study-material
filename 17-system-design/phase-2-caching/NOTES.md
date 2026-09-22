<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · databases scale ➡](../phase-3-databases-scale/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching: Notes

Caching keeps a copy of expensive-to-produce data somewhere fast, so most requests avoid
the slow source entirely. It is the **highest-leverage performance lever** available
(Phase 8) — justified directly by Phase 1's latency-ratio table: memory access is on the
order of a million times faster than a cross-continent network round trip. "The fastest
query is the one you never run." `CacheDemo.java` in this directory reproduces both of
caching's genuinely hard problems — stale reads and stampedes — and fixes each.

## 2.1 — Cache-aside (lazy loading)

The most common caching pattern: the application checks the cache first, and only falls
back to the slow source on a miss.

### Key Concepts

- **Cache-aside (lazy loading)** — the application code owns the logic: check the cache;
  on a hit, return it; on a miss, load from the source (DB/API), then populate the
  cache before returning. The cache only ever holds what has actually been requested.
- **Miss penalty** — the first request for any given key always pays the full cost of
  the slow source, plus the cost of writing to the cache. This is the one-time "cold
  start" tax cache-aside accepts in exchange for simplicity.
- **Staleness window** — once populated, the cached copy can drift from the source
  until it's invalidated or expires (2.2) — cache-aside makes no freshness guarantee on
  its own.

### Worked example — `CacheDemo.cacheAside()`

```java
static Map<String, String> cache = new ConcurrentHashMap<>();
static Database db = new Database();   // counts real hits via db.hits

static String get(Map<String, String> cache, Database db, String key) {
    String v = cache.get(key);
    if (v == null) {                       // MISS -> load and populate
        v = db.load(key);
        cache.put(key, v);
    }
    return v;                              // HIT -> served from cache
}

for (int i = 0; i < 5; i++) get(cache, db, "user:1");
// 5 reads of user:1 -> DB hit 1 time (cache served the rest)
```

The first call to `get("user:1")` misses, calls `db.load("user:1")` (the simulated 20ms
DB round trip), and stores the result. Calls 2 through 5 hit the populated `cache` map
directly — `db.hits` stays at 1 no matter how many more times `get` is called for that
key. This is the shape of virtually every cache in production: a `Map` (or Redis) in
front of a slow source, checked before every read.

### Why it's useful

Cache-aside requires no changes to the data source and only caches what's genuinely
read, so memory is spent on the actual working set rather than pre-warming data that may
never be requested. Its cost is the miss penalty (a cold cache is as slow as no cache)
and the fact that freshness is entirely your responsibility — which is where 2.2 and 2.3
come in.

## 2.2 — Cache placement: the layered stack

A real system caches at multiple layers simultaneously, each trading speed for how
widely shared and how hard to invalidate it is.

### Key Concepts

- **Browser / HTTP cache** — the client itself caches responses per `Cache-Control`
  headers; zero network round trip on a hit, but only benefits that one user's browser.
- **CDN / edge cache** — a geographically distributed cache in front of your origin,
  serving static assets and cacheable API responses from a point of presence near the
  user (2.4). The single biggest latency win for globally distributed users.
- **API gateway cache** — caches whole responses for a shared route (e.g., a public,
  identical-for-everyone GET) before the request even reaches your app servers.
- **In-process (local) cache** — a `Map`/Caffeine cache living in one app server's
  memory. The fastest possible cache (no network hop at all) but **not shared** — each
  server has its own copy, so a write on server A doesn't invalidate server B's copy,
  and memory usage multiplies by the number of instances.
- **Distributed cache (Redis/Memcached)** — shared across all app servers over the
  network; one round trip slower than in-process, but consistent across the fleet and
  survives individual app server restarts.
- **Database buffer/page cache** — the database's own internal cache of hot pages in
  RAM; you don't manage it directly, but it's why a "cold" query (first time touching
  those pages) is often visibly slower than a "warm" repeat of the same query.

### Worked example — the layered request path

```
Client -> CDN (edge cache, static assets + cacheable GETs)
       -> API gateway (may cache whole responses for public routes)
       -> App server (in-process cache: per-instance, fastest, not shared)
       -> Distributed cache / Redis (shared across the whole fleet)
       -> Database (has its own internal buffer cache, then disk)

A hit at any layer short-circuits everything below it. A request for a
hot, rarely-changing asset (a user's avatar image) might be served
entirely from the CDN and never reach the app tier at all.
```

### Why it's useful

Layering means most traffic never reaches the expensive layers at all — each cache tier
absorbs load from the tier below it. The trade-off along the stack is consistent: closer
to the user is faster and cheaper per request, but harder to invalidate correctly and
narrower in scope (one browser, vs. one server, vs. the whole fleet).

## 2.3 — Write strategies: through, behind, and around

Cache-aside is a *read* strategy. Separately, you must decide how writes interact with
the cache.

### Key Concepts

- **Write-through** — every write goes to the cache and the database synchronously, in
  the same operation. The cache is never stale (for writes that went through it), but
  every write now pays the latency of both the cache write and the DB write.
- **Write-behind (write-back)** — the write lands in the cache immediately and is
  acknowledged; the cache asynchronously flushes it to the database later. Writes are
  very fast, but a crash between the cache write and the flush loses data — riskier, and
  usually reserved for workloads that can tolerate that (metrics, counters).
- **Write-around** — writes go directly to the database, bypassing the cache entirely.
  Good when written data isn't likely to be read again soon (the cache would just be
  storing something nobody re-reads); the next read for that key is a normal cache-aside
  miss.
- **Read-through** — functionally the same outcome as cache-aside, but the *cache
  library itself* (not your application code) owns the miss-then-load logic — cleaner
  application code, same behavior.

### Comparison table — cache write/read strategies

| Strategy | Write path | Freshness | Write latency | Risk |
|---|---|---|---|---|
| Cache-aside | App loads on miss, writes bypass cache | Stale until next read-populate or TTL | Normal (writes untouched) | Miss penalty on cold keys |
| Read-through | Cache library loads on miss | Same as cache-aside | Normal | Same as cache-aside |
| Write-through | Cache + DB written together | Always fresh (for writes that went through) | Higher (two synchronous writes) | None (both writes must succeed) |
| Write-behind | Cache written, DB flushed async | Fresh in cache immediately | Lowest | Data loss if crash before flush |
| Write-around | DB only, cache untouched | Cache stale until next read | Normal | Wasted cache space avoided; cold on next read |

### Why it's useful

Most web applications default to cache-aside for reads plus write-around or explicit
invalidation-on-write, because it's simple and the miss penalty is acceptable. Reach for
write-through when correctness of the cached copy matters more than write latency
(pricing data that must never be stale); reach for write-behind only when write
throughput is critical and some data-loss risk on crash is acceptable (a metrics
counter, not a financial ledger).

## 2.4 — Cache invalidation

Famously "one of the two hard problems in computer science" (the other being naming
things, and off-by-one errors). The challenge: the cached copy must track the source of
truth, but nothing forces it to.

### Key Concepts

- **TTL (time-to-live)** — every cached entry expires automatically after N seconds,
  regardless of whether the source changed. Simple, self-healing (a bug that fails to
  invalidate is bounded by the TTL), but trades freshness for miss rate: a shorter TTL
  means fresher data but more cache misses (more load on the source); a longer TTL means
  fewer misses but longer staleness windows.
- **Write-invalidation** — explicitly delete or update the cache entry whenever the
  source changes (typically in the same code path as the write, or via a
  change-data-capture stream). Precise, but requires every write path to remember to do
  it — miss one and you have silently stale data with no TTL safety net unless you also
  set one.
- **Versioned keys** — embed a version or timestamp in the cache key itself (`user:42:v3`)
  so that when the underlying data changes, the application starts writing/reading a
  new key; old keys are never explicitly deleted, just naturally abandoned and evicted
  by the eviction policy (2.6). Useful when explicit deletion is hard to coordinate
  across many cache instances.

### Worked example — choosing a TTL by staleness tolerance

```
Data              Staleness tolerance     Reasonable TTL
------------------------------------------------------------
Stock price feed        seconds                 1-5s
Product page price      minutes                 1-5 min
User's display name     hours                    1 hour
Static config/flags     until deploy       explicit invalidation, no TTL
Rendered blog post      until edited       write-invalidation on publish
```

There is no universally correct TTL — it's set by how wrong the application is allowed
to be for how long, balanced against how much load the source can tolerate from misses.
A payment amount should never be served from a TTL-based cache at all; a homepage
banner can happily be five minutes stale.

### Why it's useful

Most production caches combine both approaches: a generous TTL as a safety net (so a
missed invalidation self-heals within a bounded window) plus explicit write-invalidation
for the common, known write paths (so freshness is usually near-immediate in practice).
Choosing staleness the *use case* tolerates — not the shortest TTL you can get away with
— is the actual skill; over-caching produces wrong answers, under-caching defeats the
point of caching.

## 2.5 — Cache stampede (thundering herd)

The second hard problem `CacheDemo.java` demonstrates directly: when a hot key goes
cold (expires, or is requested for the first time) under concurrent load, many requests
miss simultaneously and all hammer the source at once — often overloading it at exactly
the moment it's already under the most load.

### Key Concepts

- **The race** — many concurrent threads/requests independently check the cache, all
  see a miss for the same key, and all proceed to load from the source and write back —
  the source receives N redundant loads instead of 1.
- **Single-flight / request coalescing** — only the *first* thread to miss actually loads
  from the source; every other concurrent request for the same key waits for that one
  load to finish and reuses its result, instead of issuing its own. In Redis, this is
  typically implemented with a short-lived per-key lock (`SET key val NX PX <ms>`) that
  only the "leader" request acquires.
- **Early / probabilistic recomputation** — refresh a hot key's value slightly *before*
  it actually expires (proactively, on a background schedule, or probabilistically as
  expiry approaches) so the key is effectively never cold under load.
- **Stale-while-revalidate** — serve the (slightly) stale cached value immediately to
  all requests while exactly one background worker refreshes it; nobody waits on the
  slow source, and nobody sees an outright miss.

### Worked example — `CacheDemo.stampedeUnprotected()` vs. `stampedeProtected()`

```java
// UNPROTECTED: 50 threads all racing on the same cold key.
static void stampedeUnprotected() throws InterruptedException {
    Map<String, String> cache = new ConcurrentHashMap<>();
    Database db = new Database();
    runConcurrently(50, () -> get(cache, db, "hot:key"));   // all miss together
    // unprotected: 50 concurrent misses -> DB hit 50 times  <-- STAMPEDE
}

// PROTECTED: computeIfAbsent locks per-key, so only the winner loads.
static void stampedeProtected() throws InterruptedException {
    Map<String, String> cache = new ConcurrentHashMap<>();
    Database db = new Database();
    runConcurrently(50, () ->
            cache.computeIfAbsent("hot:key", db::load));    // atomic per-key load
    // protected  : 50 concurrent misses -> DB hit 1 time  (single-flight: one loads, 49 wait & reuse)
}
```

In `stampedeUnprotected`, the `get()` helper's check-then-load is two separate steps
(`cache.get`, then `db.load`), so all 50 threads see a miss before any of them has
written back — `db.hits` ends at 50. In `stampedeProtected`,
`ConcurrentHashMap.computeIfAbsent` is **atomic per key**: the JVM guarantees only one
thread actually executes the mapping function (`db::load`) for a given key at a time;
the other 49 threads block until that call returns and then reuse its result —
`db.hits` ends at exactly 1. This is precisely what a Redis-based single-flight lock
achieves in a distributed setting, just across processes instead of threads.

### Why it's useful

A stampede is a self-inflicted denial-of-service: the very mechanism meant to protect
your database (the cache) causes an overload of it at the worst possible moment (right
after an expiry, or right after a deploy that cleared a cache). Single-flight
coalescing, early recomputation, and stale-while-revalidate are the three standard
answers, and production caching libraries (Redis's `SETNX`-based locks, Guava/Caffeine's
loading caches) typically build in one of them by default.

## 2.6 — Eviction policies

A cache is bounded memory (Phase 7); something must decide what to remove once it's
full. A cache with no eviction policy is not a cache — it's a memory leak with better
marketing.

### Key Concepts

- **LRU (Least Recently Used)** — evict the entry that hasn't been accessed in the
  longest time. Adapts naturally to a shifting hot set — a strong, simple, commonly
  correct default (used by `LinkedHashMap.removeEldestEntry` with `accessOrder=true` in
  Java, and Redis's `allkeys-lru`).
- **LFU (Least Frequently Used)** — evict the entry accessed the fewest total times.
  Better for a stable, long-term-popular hot set, but can keep a once-hugely-popular but
  now-dead entry around simply because its historical count is high (Redis's
  `allkeys-lfu` addresses this with a decaying frequency counter).
- **TTL / FIFO** — evict by absolute age (oldest entry, or entries past their explicit
  TTL) regardless of access pattern; simplest to reason about, worst at adapting to
  actual popularity.
- **`maxmemory` + policy (Redis)** — Redis is bounded by `maxmemory`; once reached, it
  evicts according to the configured policy (`allkeys-lru`, `volatile-lru`,
  `allkeys-lfu`, `noeviction`, etc.) rather than growing unbounded (Redis track, phase
  4).

### Why it's useful

Choosing an eviction policy is choosing what you're betting on about your access
pattern: LRU bets that "recently accessed" predicts "will be accessed again soon" (true
for most web traffic); LFU bets that long-run popularity is more predictive than
recency (better for stable catalogs). A cache without any bound at all is Phase 7's
"unbounded collection" memory leak wearing a different hat — always size the cache and
pick a policy deliberately.

## 2.7 — CDNs and HTTP caching

The specific, extremely high-leverage application of caching to static and
semi-static web content.

### Key Concepts

- **CDN (Content Delivery Network)** — a globally distributed network of edge servers
  that cache and serve content physically close to the requesting user, cutting the
  network round trip from potentially hundreds of milliseconds (cross-continent, Phase
  1.6) to single-digit milliseconds. Used for static assets (images, JS, CSS) and,
  increasingly, cacheable API responses.
- **`Cache-Control`** — the HTTP header controlling cacheability: `max-age=3600` (cache
  for an hour), `public`/`private` (shared caches like CDNs may/may not store it),
  `no-store` (never cache), `no-cache` (cache it, but always revalidate before using it).
- **`ETag` / `Last-Modified`** — conditional-request validators. A client sends
  `If-None-Match: <etag>`; if the resource hasn't changed, the server replies **304 Not
  Modified** with no body — the client reuses its cached copy without re-downloading it,
  saving bandwidth even when the cache technically has to "check."

### Why it's useful

CDN + HTTP caching is usually the single biggest web-latency win available (Phase 8.4)
because it eliminates the network round trip almost entirely for cacheable content, and
it's essentially free once configured — no application code changes, just correct
headers on the origin responses.

## 2.8 — What to cache, and cache pitfalls

### Key Concepts

- **Good caching candidates**: hot (frequently read), expensive to produce (a complex
  query, an aggregation, a rendered fragment), and staleness-tolerant (a slightly old
  answer is acceptable). Sessions, computed aggregates, and query results are classic
  examples.
- **Bad caching candidates**: data that must be exact and current — account balances,
  permission checks — unless backed by strict, immediate invalidation; and data that
  changes so often the hit ratio would be near zero anyway.
- **Hit ratio** — `hits / (hits + misses)`. The primary health metric for any cache. A
  low hit ratio means the cache isn't earning its complexity — likely wrong keys, too
  short a TTL, or too small a size relative to the working set.
- **Hot-key problem** — one extraordinarily popular key (a celebrity's profile, a viral
  post) can overwhelm the single cache node/shard responsible for it, even though the
  cache overall has plenty of capacity. Mitigate with a local (in-process) cache in
  front of the distributed cache for that key, or replicate the hot key across multiple
  nodes.
- **Cache penetration** — repeated queries for keys that don't exist in the source
  always miss the cache (there's nothing to cache) and always hit the source — an
  attacker (or a bug) can exploit this to bypass the cache entirely. Mitigate by caching
  a negative result ("this key doesn't exist") with a short TTL, or using a **Bloom
  filter** to cheaply reject queries for keys that are known not to exist before they
  ever reach the source.
- **Leaking per-user data under a shared key** — caching a response that includes
  user-specific data (their name, their permissions) under a key that doesn't include
  the user identity is a serious bug: user B can be served user A's cached response.
  Always include the identity dimension in the cache key for personalized data.

### Why it's useful

Every one of these pitfalls has shown up as a real production incident somewhere: a
misconfigured shared cache key leaking one user's data to another, a hot-key overload
taking down an otherwise-healthy cluster, or an attacker using cache-penetrating
requests as a cheap denial-of-service vector against the origin. Naming them explicitly
in an interview signals you've thought about caching as a system with failure modes, not
just a performance trick.

## Summary / Key Takeaways

- **Cache-aside** (check cache → miss → load source → populate) is the default pattern;
  **write-through/behind/around** are separate decisions about how writes interact with
  the cache, trading write latency against freshness and durability risk.
- Cache at **multiple layers** (browser, CDN, gateway, in-process, distributed,
  DB buffer) — each layer closer to the user is faster but harder to invalidate and
  narrower in scope.
- **Invalidation** (TTL, write-invalidation, versioned keys) trades freshness against
  miss rate — pick the staleness the specific use case actually tolerates, not the
  shortest TTL you can get away with.
- **Cache stampede** (many concurrent misses hammering the source at once) is fixed with
  **single-flight/request coalescing** (demo: 50 concurrent misses → 1 DB hit), early
  recomputation, or stale-while-revalidate.
- **Eviction policies** (LRU/LFU/TTL) bound cache memory — a cache with no eviction is a
  Phase 7 memory leak; track **hit ratio** as the health signal, and watch for
  **hot-key** overload and **cache-penetration** attacks on nonexistent keys.
