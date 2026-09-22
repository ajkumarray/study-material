<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · middleware](../phase-1-middleware/NOTES.md) | [Phase 3 · spring integration ➡](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching Patterns: Notes

Caching is Redis's flagship job. The patterns in this phase differ in **who** reads and writes
the cache and **when** — and the genuinely hard parts, in every one of them, are **keeping the
cache correct as the underlying data changes (invalidation)** and **not letting a cache miss
turn into a database-crushing stampede**. This overlaps with System Design Phase 2 and 8; here
it's made concrete with actual Redis commands and Spring annotations.

## 2.1 — The read/write strategies

### Definition

A caching *strategy* is the answer to two questions: on a **read**, who checks the cache and
who loads on a miss? On a **write**, does the cache get updated immediately, later, or not at
all (relying on expiry)? The four classic strategies answer these differently, and each has a
distinct latency/consistency/complexity trade-off.

### Key Concepts

- **Cache-aside (a.k.a. lazy loading)** — the *application* is in charge. On a read it checks
  the cache first; on a **hit** it returns the cached value directly; on a **miss** it reads the
  database, stores the result in the cache, and then returns it. This is exactly what Spring's
  `@Cacheable` implements (Phase 3). Only data that's actually been requested ever gets cached
  — nothing is pre-populated. Downsides: the very first request for any key is always a cold
  miss, and if the underlying row changes without an explicit cache update, the cached copy
  goes stale until it either expires (TTL) or is evicted.
- **Read-through** — functionally the same effect as cache-aside, but the *loading logic* lives
  **inside the cache layer** rather than the application: the app only ever talks to the cache,
  and the cache itself is configured to fetch from the DB on a miss. Spring's basic
  `@Cacheable` is closer to cache-aside (the app-level method is the one doing the DB call on a
  miss); a purpose-built read-through cache library hides that behind the cache API entirely.
- **Write-through** — a write updates the **cache and the database synchronously**, in the same
  operation, before returning. The cache is therefore never stale for a value that's been
  written this way. The cost: every write pays the latency of *both* stores, and you may end up
  caching values that are never subsequently read.
- **Write-behind (write-back)** — a write updates the cache immediately and returns, and the
  database write is flushed **asynchronously** afterward (batched, on a timer, or via a queue).
  Writes are very fast, but there's a real window where the cache is ahead of the database — if
  the cache process dies before the flush, that write is lost. Used where write throughput
  matters more than the last few writes' durability.
- **The default for most web apps:** cache-aside for reads, combined with explicit invalidation
  (`@CacheEvict`) or a refresh (`@CachePut`) on writes. It's the simplest to reason about and
  the safest failure mode — a cache outage just means every read becomes a DB read, not data
  loss.

### Worked example — cache-aside, by hand and via Spring

```
# By hand, the exact sequence @Cacheable performs on a MISS:
GET expense:1
# (nil)                                        <- miss
# ... application reads Postgres: SELECT * FROM expenses WHERE id=1 ...
SET expense:1 '{"id":1,"desc":"Lunch","cents":1250}' EX 600
# OK                                            <- populate the cache, 10 min TTL
GET expense:1
# "{\"id\":1,\"desc\":\"Lunch\",\"cents\":1250}"  <- next call is a HIT, no DB touched
```

```java
// Spring makes exactly that sequence automatic. From examples/CachingExample.java:
@Cacheable(value = "expenses", key = "#id")
public Expense findById(Long id) {
    return repo.findById(id).orElseThrow();   // only runs on a cache MISS
}
```

In this example, the manual `GET`/`SET` sequence *is* cache-aside: check first, load on miss,
store what you loaded, return it. `@Cacheable` performs precisely that sequence for you — on a
call, Spring computes the key (`expenses::1`), checks Redis, and only executes the method body
(hitting Postgres) if the key isn't present, storing the return value afterward.

### Comparison table — the four strategies

| Strategy | Who loads on miss | Write path | Read latency (cold) | Write latency | Risk |
|---|---|---|---|---|---|
| Cache-aside | Application | DB only; cache invalidated/updated separately | Slow (miss) then fast | Fast (DB only) | Stale cache if invalidation missed |
| Read-through | Cache library | DB only (same as cache-aside) | Slow (miss) then fast | Fast (DB only) | Same staleness risk, less app code |
| Write-through | N/A (cache stays fresh) | Cache + DB, synchronous | Fast (rarely a cold miss) | Slower (two writes) | Wasted cache space on unread data |
| Write-behind | N/A | Cache immediately, DB async | Fast | Fastest | Data loss if cache dies before flush |

### Why it's useful

Picking the right strategy is picking where you want the pain: cache-aside pushes complexity
into explicit invalidation logic but keeps writes simple and fast; write-through keeps the
cache always fresh but slows every write down; write-behind is fastest for high write
throughput but trades away some durability. Most CRUD services (like the expense API) default
to cache-aside because reads vastly outnumber writes and the invalidation logic
(`@CacheEvict`/`@CachePut`) is easy to reason about per-entity.

## 2.2 — TTL & eviction

### Definition

A **TTL (time-to-live)** is an expiry set on a key so Redis automatically deletes it after a
duration, regardless of whether anything ever explicitly invalidates it. **Eviction** is a
separate mechanism: when Redis's memory usage hits its configured ceiling, it proactively
removes keys according to a policy to make room for new writes.

### Key Concepts

- **TTL is a safety net for staleness**, not just a cleanup mechanism. Even if application code
  *completely forgets* to invalidate a cache entry on a write, that entry can only ever be
  wrong for, at most, its TTL — after that it expires and the next read repopulates it from the
  source of truth. Choosing a TTL is choosing "how stale am I willing to let this data get in
  the worst case."
- **TTL granularity should match data volatility.** A stock price or an inventory count might
  get seconds; a user's profile or a rarely-changing config might get hours. The
  `expense-api`'s `application.yml` sets a blanket 10-minute default (`time-to-live: 600000`
  ms) with room to override per cache.
- **Eviction policies (triggered by `maxmemory`):**
  - `allkeys-lru` — evict the **l**east-**r**ecently-**u**sed key across the whole keyspace,
    regardless of whether it has a TTL. The common default for a pure-cache Redis instance.
  - `allkeys-lfu` — evict the **l**east-**f**requently-**u**sed key — better than LRU when
    "popular but not recently touched" keys should survive over "just touched once."
  - `volatile-lru` / `volatile-lfu` / `volatile-ttl` / `volatile-random` — only ever evict keys
    that **have a TTL set**, leaving keys without a TTL untouched. Useful when the same Redis
    instance holds both cache entries (with TTLs, evictable) and something that must never be
    evicted (without a TTL).
  - `noeviction` — reject new writes with an error once `maxmemory` is hit, rather than evict
    anything. Appropriate when Redis is a primary store where silently dropping data would be
    worse than a write failing loudly.
- **A cache is bounded, deliberately.** The point isn't to hold everything — it's to hold the
  *hot* subset that's actually being read repeatedly. TTL and LRU/LFU eviction both push toward
  that same goal from different angles: TTL bounds staleness, eviction bounds memory.

### Worked example

```
SET expense:1 '{"id":1}' EX 600      # expires in 10 minutes
TTL expense:1
# (integer) 600                       <- seconds remaining
TTL expense:missing-key
# (integer) -2                        <- key doesn't exist
SET counter:x 1
TTL counter:x
# (integer) -1                        <- key exists but has NO expiry (persists forever
#                                         unless evicted or explicitly deleted)
EXPIRE expense:1 300                  # reset TTL to 5 minutes (e.g. after a fresh read)
# (integer) 1                         <- 1 = TTL was successfully set
```

In this example, `TTL` returns three different things: a positive number of seconds
remaining, `-2` for a key that simply doesn't exist (already expired or never set), and `-1`
for a key that exists but has no expiry at all — a common bug source, since a `-1` key will
sit in memory forever unless an eviction policy removes it or something explicitly `DEL`s it.

### Why it's useful

Every cached entry should have an intentional TTL — "no TTL" should be a deliberate choice
(e.g. a config value updated only via explicit invalidation), not an oversight. Combined with
an LRU/LFU eviction policy sized to `maxmemory`, TTLs are what keep a cache self-healing:
staleness is bounded, memory is bounded, and the hot working set naturally survives while cold
entries fall away — with zero manual cleanup code.

## 2.3 — Invalidation, staleness & cache stampede

### Definition

**Invalidation** is the act of removing or refreshing a cached entry when the underlying data
it represents changes, so readers don't keep seeing an outdated value. A **cache stampede**
(a.k.a. thundering herd) is a failure mode where a popular key expires (or is evicted) and a
large number of concurrent requests all miss at once and hammer the database simultaneously to
recompute the same value.

### Key Concepts

- **"There are only two hard things in computer science: cache invalidation and naming things"**
  — invalidation is hard because you have to catch *every* code path that changes the
  underlying data and remember to evict or refresh the corresponding cache entry, forever, as
  the codebase grows.
- **Strategies for invalidation:**
  - **Write-time invalidation** — explicitly `@CacheEvict`/`DEL` (or `@CachePut`/`SET` to
    refresh) the exact key whenever the write path that changes it runs. Precise, but requires
    discipline — every write path must remember to do it.
  - **TTL-only** — never explicitly invalidate; rely purely on expiry. Simple to implement,
    bounded staleness, but readers can see up-to-TTL-old data even right after a write.
  - **Event-based invalidation** — a change event (e.g. a Kafka message, a DB trigger, a pub/sub
    signal) triggers invalidation in a decoupled consumer, useful when multiple services/caches
    need to react to the same change.
- **Cache stampede** — the classic trigger is a hot key's TTL expiring under high concurrent
  read load: request 1 misses and starts recomputing (a slow DB query), but before it finishes,
  requests 2 through 10,000 *also* miss on the same now-empty key and *also* start recomputing
  — multiplying database load by the request concurrency, right when the DB is already under
  pressure. Mitigations:
  - **Locking / single-flight** — only the first miss actually recomputes; concurrent misses on
    the same key wait for (and then reuse) that one result instead of each hitting the DB.
  - **Early / probabilistic recomputation** — refresh a hot key's value slightly *before* it
    actually expires (e.g., a background refresh, or probabilistically on read as the TTL
    approaches zero), so under sustained load the key effectively never goes fully cold.
  - **Jittered TTLs** — add a small random offset to each entry's TTL (e.g. `600 ± 30s`) so a
    batch of keys set at the same time doesn't all expire in the exact same instant.
  - **Negative caching, carefully** — cache a "not found" result briefly to stop repeated
    misses for a key that legitimately doesn't exist from hammering the DB on every request;
    use a short TTL so a record created moments later isn't hidden behind a stale negative
    cache. (`cache-null-values: false` in the `application.yml` deliberately turns this
    *off* by default — caching nulls indefinitely is a much more common bug than a missing
    negative-cache optimization.)
- **Consistency reality check:** a cache is, by construction, *eventually* consistent with the
  database — there is always some window (bounded by TTL, or by however fast your invalidation
  runs) where they can briefly disagree. If a piece of data genuinely cannot tolerate any
  staleness (e.g. a real-time balance check before authorizing a transaction), the correct
  answer is often "don't cache that specific read," not "cache it more cleverly."

### Worked example — stampede, illustrated

```
# T=0: a hot key expires
GET product:bestseller
# (nil)                       <- 500 concurrent requests all see this at once

# Naive: all 500 requests independently query Postgres for the same row,
# then all independently SET the same key — 500x the necessary DB load.

# With single-flight locking (conceptually — see Phase 4.3 for the lock primitive):
SET lock:recompute:product:bestseller worker-1 NX EX 5
# OK for exactly one caller           <- that one recomputes and repopulates the cache
# (nil) for the other 499             <- they wait briefly and re-read the now-populated key
```

In this example, an unprotected cache miss under load turns one popular key's expiry into
hundreds of duplicate, simultaneous database queries. Wrapping the recompute step in a
`SET ... NX` lock (Phase 4.3) means only one caller actually queries the database; everyone
else either waits and re-reads the freshly populated cache, or briefly serves the previous
(slightly stale) value — either is far cheaper than a 500x spike in DB load.

### Why it's useful

Invalidation and stampede protection are where caching's theoretical simplicity ("just cache
it") meets production reality. A cache without deliberate TTLs, invalidation, and stampede
mitigation isn't actually safe under real traffic — it's a ticking bug that only shows up when
a hot key expires under load, which is exactly the moment you can least afford a database
spike.

## Summary / Key Takeaways

- **Cache-aside** (app checks cache, loads DB on miss, stores result) is the default pattern —
  it's what `@Cacheable` implements. **Write-through** keeps the cache always fresh at the cost
  of slower writes; **write-behind** is fastest but risks losing recent writes.
- **TTL bounds staleness** — every cache entry should have an intentional expiry; a `-1` TTL
  (no expiry) is usually a bug, not a feature.
- **Eviction policies** (`allkeys-lru`/`lfu`, `volatile-*`, `noeviction`) decide what happens
  when `maxmemory` is hit — `allkeys-lru` is the typical cache default.
- **Invalidate on write** (`@CacheEvict`/`@CachePut`) for precision; rely on TTL as the
  backstop for whatever invalidation misses.
- **Cache stampede** — a hot key expiring under concurrent load can multiply DB queries;
  mitigate with single-flight locking, early/probabilistic refresh, and jittered TTLs.
- A cache is **eventually consistent** with the source of truth by design — accept a bounded
  staleness window, or don't cache data that genuinely can't tolerate one.
