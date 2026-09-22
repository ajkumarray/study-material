<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · databases scale ➡](../phase-3-databases-scale/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching: Interview Q&A

⭐ = asked constantly.

**Q: Explain cache-aside, write-through, and write-behind, and when you'd choose each.** ⭐⭐
Cache-aside: the application checks the cache first; on a miss it loads from the source
and populates the cache before returning — the cache only ever holds what's actually
been read. It's the most common pattern because it needs no changes to the data source
and doesn't waste memory on unread data, but it pays a "miss penalty" on cold keys and
makes no freshness guarantee on its own. Write-through writes to the cache and the
database synchronously in the same operation — the cache is always fresh for anything
written through it, at the cost of every write now paying both latencies. Write-behind
writes to the cache immediately and flushes to the database asynchronously — fastest
writes, but a crash between the cache write and the flush loses data, so it's reserved
for workloads that can tolerate that (counters, metrics), never financial state.

*Follow-up: what's write-around, and why would you use it?* Writes go straight to the
database, bypassing the cache entirely. It's the right choice when written data isn't
likely to be re-read soon — populating the cache on write would just waste space on
data nobody re-reads; the next read is a normal cache-aside miss instead.

**Q: What is cache invalidation and why is it "one of the two hard problems in CS"?** ⭐⭐
It's the problem of keeping a cached copy consistent with a source of truth that can
change from many places, across many cache instances, without anything automatically
enforcing that. The two standard tools are TTL (expire after N seconds, regardless of
whether the source changed — simple and self-healing, but trades freshness for miss
rate) and write-invalidation (explicitly delete/update the cache entry in the same code
path that writes the source — precise, but every write path has to remember to do it,
and a missed one silently serves stale data forever unless a TTL backstops it). Most
production systems combine both: a generous TTL as a safety net, plus explicit
invalidation on the known write paths for near-immediate freshness in practice. There's
no universally "correct" TTL — it's set by how wrong the app can be, for how long,
balanced against how much load the source can absorb from misses.

**Q: What is a cache stampede and how do you prevent it?** ⭐⭐
A hot key expires (or is requested cold) under concurrent load, and many requests miss
simultaneously, all hitting the source at once — a thundering herd that can overload the
source at exactly the moment it's busiest, which is often right after a deploy or right
after an expiry on your most popular key. The fix is single-flight / request coalescing:
only the first thread/request to miss actually loads from the source; every other
concurrent request for that key waits and reuses its result instead of issuing its own
load. In Java, `ConcurrentHashMap.computeIfAbsent` gives you this atomically per key — in
the demo, 50 threads racing on a cold key with the naive check-then-load pattern produce
50 database hits, but the same 50 threads through `computeIfAbsent` produce exactly 1.
In Redis, the equivalent is a short-lived per-key lock (`SET key val NX PX <ms>`) held
by whichever request wins the race to load. Other fixes: refresh a key slightly before
it actually expires (early/probabilistic recomputation), or serve the stale value
immediately while one background worker refreshes it (stale-while-revalidate).

*Follow-up: why does `computeIfAbsent` alone not fully solve this in a real distributed
system?* It only coordinates within one JVM/process — it's the exact mechanism Redis's
single-flight lock replicates across processes, but a plain `ConcurrentHashMap` in one
app server doesn't stop 50 different app server instances from each independently
missing the same key and hammering the source; you need the coordination to happen in
the shared cache layer (Redis), not in each app server's local memory.

**Q: LRU vs. LFU eviction — what's the difference and when would you pick each?** ⭐
LRU evicts the entry that hasn't been accessed in the longest time — it adapts naturally
to a shifting hot set and is a strong general default (Redis's `allkeys-lru`, Java's
`LinkedHashMap` with `accessOrder=true`). LFU evicts the entry with the fewest total
accesses — better when popularity is stable over the long run, but naively it can keep a
once-hugely-popular, now-dead entry around just because its historical count is high
(Redis's `allkeys-lfu` uses a decaying counter to address this). Both exist to bound
memory; a cache with no eviction policy at all is functionally a Phase 7 memory leak.

**Q: Where can you cache in a web stack, and what's the trade-off between the layers?** ⭐
Browser/HTTP cache (per-user, zero round trip on hit) → CDN/edge (shared across all
users near a location, the biggest latency win for globally distributed traffic) → API
gateway (cache whole responses before they reach app servers) → in-process/local cache
(fastest possible — no network hop — but not shared across instances, so a write on one
server doesn't invalidate another's copy) → distributed cache like Redis (shared across
the whole fleet, one network hop slower than in-process) → the database's own buffer
cache. The pattern is consistent top to bottom: closer to the user is faster and
cheaper, but narrower in scope and harder to invalidate correctly. Real systems use
several layers at once, each absorbing load from the tier below it.

**Q: What should you not cache?**
Data that must be exact and immediately current — account balances, permission/auth
checks — unless it's backed by strict, immediate invalidation on every write path,
because a stale read there is a correctness bug, not a UX nuisance. Also avoid caching
data that changes so frequently the hit ratio would be near zero anyway (you'd be paying
cache-write overhead for almost no benefit), and never cache per-user data under a
shared key — that leaks one user's response to another user's request.

**Q: How do you measure whether a cache is actually helping?**
Hit ratio: `hits / (hits + misses)`. A low ratio means the cache isn't earning its
complexity — usually wrong keys, too short a TTL, or a cache too small relative to the
actual working set, and in the worst case it's adding latency (the cache lookup itself)
without meaningfully reducing load on the source. Also watch eviction rate (high
eviction under a reasonable size suggests the working set doesn't fit) and the
resulting load reduction on the source system.

**Q: What's the hot-key problem, and how is it different from a general capacity
problem?** ⭐
One extraordinarily popular key — a celebrity's profile, a viral post — can overwhelm
the single cache node/shard responsible for serving it, even though the cache cluster
overall has plenty of aggregate capacity; sharding by key hash doesn't help because it's
one key, not a distribution problem. Mitigations: put a small local (in-process) cache
in front of the distributed cache specifically for known hot keys, or explicitly
replicate that one key's value across multiple cache nodes so reads spread out. Related:
cache penetration — repeated requests for keys that don't exist in the source always
miss (there's nothing to cache), always hitting the source; fix by caching negative
results with a short TTL, or rejecting obviously-nonexistent keys cheaply with a Bloom
filter before they reach the source.

**Q: How do HTTP caching headers work together?**
`Cache-Control` sets cacheability and freshness lifetime (`max-age`, `public`/`private`,
`no-store` to never cache, `no-cache` to always revalidate before use). `ETag` and
`Last-Modified` are conditional-request validators: the client sends `If-None-Match:
<etag>`, and if the resource is unchanged the server replies `304 Not Modified` with no
body, saving bandwidth even on a "revalidate every time" resource. CDNs and browsers
both honor these headers automatically — it's usually the single biggest, cheapest
web-latency win available (Phase 8.4) because it needs no application code changes,
just correct headers on origin responses.

**Q: Redis vs. Memcached for caching?**
Both are fast in-memory stores well suited as a cache. Memcached is simpler and
multithreaded out of the box — effectively a pure key-value LRU cache and nothing more.
Redis offers richer data types (lists, sets, sorted sets, hashes), optional persistence,
pub/sub, scripting (Lua), and clustering — a general-purpose data-structure store that's
frequently used as a cache *and* for other things (rate limiting counters, leaderboards,
session storage) in the same deployment. Default to Redis unless you specifically want
Memcached's operational simplicity and pure-cache focus. (Redis depth: track 15.)

**Q: Design the caching strategy for a product page that shows price, description, and
live inventory count.** *nuance*
Split by staleness tolerance rather than caching the whole page uniformly: the
description is essentially static — cache-aside with a long TTL (hours) or even
write-invalidation on edit. Price changes occasionally and matters for correctness —
cache-aside with a short TTL (minutes) plus explicit invalidation when price changes,
never serve a stale price past a small bound. Live inventory count changes constantly
and is exactly the kind of thing that shouldn't be cached at all, or should be cached
for milliseconds with the checkout flow re-validating against the source of truth before
committing a sale — showing "12 left" slightly stale is fine, but *selling* based on a
stale count risks overselling. This kind of per-field staleness reasoning is what
interviewers are listening for over a single blanket "cache the page" answer.
