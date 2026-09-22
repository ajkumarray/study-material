<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · middleware](../phase-1-middleware/NOTES.md) | [Phase 3 · spring integration ➡](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 2 — Caching Patterns: Interview Q&A

⭐ = asked constantly.

**Q: Explain cache-aside end to end.** ⭐⭐
Cache-aside puts the application in charge of the cache. On a read, the app checks the cache
first by key; on a hit it returns the cached value directly and never touches the database. On
a miss, the app queries the database itself, stores the result back into the cache (usually
with a TTL), and then returns it. Only data that's actually been requested ever ends up in the
cache — nothing is pre-warmed. It's the most common pattern in practice, and it's exactly what
Spring's `@Cacheable` automates: the annotation intercepts the method call, checks Redis for
the computed key, and only executes your method body — which hits the database — on a miss.

*Follow-up: what are the two downsides of cache-aside?* The very first request for any given
key is always a cold miss (there's no way around that without pre-warming), and if the
underlying row changes without an explicit invalidation, the cached copy silently goes stale
until it either expires or is evicted.

**Q: Cache-aside vs write-through vs write-behind — compare all three.** ⭐⭐
Cache-aside only populates the cache lazily, on read misses; writes go straight to the
database and the cache is invalidated or refreshed separately. Write-through updates the cache
and the database synchronously on every write, so the cache is never stale for that value, at
the cost of every write paying two stores' worth of latency (and potentially caching data
that's never read again). Write-behind writes to the cache immediately and returns, flushing to
the database asynchronously afterward — writes are very fast, but there's a real window where a
crash loses the not-yet-flushed write. Most CRUD web apps default to cache-aside for reads plus
explicit invalidation on writes (`@CacheEvict`/`@CachePut`), because reads dominate writes and
the failure mode of a cache outage is just "every read becomes a DB read," not data loss.

**Q: Why put a TTL on every cache entry?** ⭐⭐
A TTL bounds staleness: even in the worst case — where application code completely forgets to
invalidate an entry on a write — that entry can only be wrong for, at most, its TTL duration
before Redis expires it and the next read repopulates it from the source of truth. It's a
safety net underneath whatever explicit invalidation logic exists, not a replacement for it.
TTL length should track data volatility: seconds for fast-changing data, minutes to hours for
stable data. A key with `TTL` returning `-1` has no expiry at all, which is almost always an
oversight rather than an intentional choice for a cache entry.

**Q: What eviction policies does Redis support, and which do you pick for a cache?** ⭐
When `maxmemory` is reached, Redis needs a policy for what to remove to make room:
`allkeys-lru` evicts the least-recently-used key across the whole keyspace (the common cache
default); `allkeys-lfu` evicts the least-frequently-used key, which does better than LRU when
"popular but not recently touched" should survive; the `volatile-*` variants only ever evict
keys that have a TTL set, leaving TTL-less keys untouched (useful when the same instance also
holds non-evictable data); and `noeviction` rejects new writes outright once memory is full,
which you'd choose if Redis is a primary store where silently dropping data is worse than a
write erroring out. For a pure cache, `allkeys-lru` or `allkeys-lfu` is the right call — you
want the hot set to survive and cold entries to fall away automatically.

**Q: What is a cache stampede, and how do you prevent it?** ⭐⭐
A stampede happens when a popular key expires (or is evicted) while under heavy concurrent
read load: the first request misses and starts a slow recompute (typically a DB query), but
before that finishes, every other concurrent request *also* misses on the same now-empty key
and *also* starts recomputing — multiplying database load by the request concurrency at
exactly the moment the DB is already busy. The standard mitigations are single-flight locking
(only the first miss actually recomputes; concurrent misses wait for, or briefly serve slightly
stale data while waiting for, that one result — built on `SET ... NX`, Phase 4.3), early or
probabilistic recomputation (refresh a hot key slightly before its TTL actually hits zero so it
effectively never goes fully cold under sustained traffic), and jittered TTLs (randomizing each
entry's expiry by a few percent so a batch of keys set together doesn't all expire in the same
instant).

*Follow-up: why does jittering TTLs help even without locking?* Because the root problem is
many keys expiring at the exact same moment and being recomputed at the exact same moment.
Spreading expirations out in time spreads the recompute load out in time too, even if you do
nothing else.

**Q: How do you keep a cache from serving stale data?** ⭐
Invalidate on write: whenever the code path that changes the underlying data runs, explicitly
evict (`@CacheEvict`) or refresh (`@CachePut`) the corresponding cache key, so readers never
see a value older than the last real write. Set a TTL as a backstop regardless, so staleness is
bounded even for a write path someone forgot to instrument. Write-time invalidation is precise
but requires discipline across every write path; TTL-only is simpler to implement but tolerates
more staleness right after a write.

**Q: Should you cache a "not found" result?** *nuance*
Sometimes — it's called negative caching, and it stops repeated lookups for a nonexistent key
from hitting the database on every single request. The catch is you must use a short TTL,
because if the record is created a moment later, a longer-lived negative cache entry would hide
it from readers. Caching `null`/"not found" *indefinitely* is a classic and much more damaging
bug than the miss you were trying to save — which is exactly why Spring's Redis cache config
defaults `cache-null-values` to `false`, refusing to cache nulls at all unless you opt in with
a deliberately short TTL for that specific case.

**Q: What is the fundamental trade-off of caching, stated precisely?**
You trade a bounded amount of staleness and added system complexity (TTL tuning, invalidation
logic, memory sizing, stampede handling) for lower read latency and reduced load on the
database. A cache is, by construction, only *eventually* consistent with the source of truth —
there's always some window where they can disagree, whether that window is bounded by a TTL or
by how fast an invalidation event propagates. Data that genuinely cannot tolerate any staleness
at all — a balance check right before authorizing a money transfer, for instance — is a signal
that the read shouldn't be cached in the first place, not a signal to cache it more cleverly.

**Q: Read-through vs cache-aside — is there a real difference?** *nuance*
They produce the same externally-observed behavior — a miss triggers a DB load and populates
the cache — but the loading logic lives in different places. In cache-aside, the *application*
code is the one that, on a miss, queries the database and writes the result back to the cache;
`@Cacheable` is this pattern, since your annotated method body is what performs the DB call.
In a true read-through cache, the *cache layer itself* is configured with a loader function, so
application code only ever talks to the cache API and never sees the miss path directly. The
distinction mostly matters for where you put the loading logic and how testable/swappable it
is, not for the resulting consistency behavior.

**Q: Why might you choose write-behind despite the data-loss risk?** *nuance*
When write throughput is the bottleneck and the data being written can tolerate losing the
last few unflushed writes on a crash — think high-volume metrics, view counters, or activity
logs, where losing a handful of recent increments on a rare crash is an acceptable cost for
consistently low write latency across millions of writes. It's the wrong choice for anything
where every individual write matters (an order, a payment), where write-through or a direct
synchronous DB write is the safer option even at higher latency.
