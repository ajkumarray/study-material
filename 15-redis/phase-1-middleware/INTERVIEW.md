<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching patterns ➡](../phase-2-caching-patterns/NOTES.md)
<!-- /nav -->

# Phase 1 — Redis as Middleware: Interview Q&A

⭐ = asked constantly.

**Q: What is Redis, and why is it so fast?** ⭐⭐
Redis is an in-memory key-value data-structure store — an application talks to it over the
network and gets back structured values (strings, hashes, lists, sets, sorted sets, streams)
rather than just plain bytes. It's fast for two independent reasons. First, the entire dataset
lives in RAM, so a read or write is a microsecond-scale memory access instead of a
millisecond-scale disk seek — often 100–1000× faster than a comparable query against a
disk-backed database like Postgres. Second, command execution is single-threaded: one command
runs to completion before the next one starts, so there's no lock contention, no
context-switching overhead, and every individual command is atomic by construction. The
trade-off for both of these is capacity and durability — the dataset has to fit in memory, and
by default data is more volatile than a disk-backed store.

*Follow-up: is Redis fully single-threaded?* The command loop that reads and mutates data is
logically single-threaded, but Redis 6+ added background I/O threads that handle reading
request bytes off the socket and writing responses back, to reduce network-handling overhead.
The part that matters for atomicity — actually touching the data — is still one command at a
time.

**Q: What do you actually use Redis for in a system?** ⭐⭐
The recurring roles are: a **cache** in front of a database to serve repeat reads from RAM
(the headline use case); a **session store** so any instance of a horizontally-scaled app can
serve any user, because session state lives outside the app process; a **rate limiter**, using
atomic counters with a TTL window to throttle abusive clients; a **distributed lock**
(`SET key val NX EX ttl`) to coordinate exclusive access to a resource across instances; and
lightweight **pub/sub** or **streams** for fan-out messaging and cache-invalidation signals.
More broadly, anything that needs to be read/written extremely fast and shared across a fleet
of stateless app instances — leaderboards, feature flags, idempotency keys — is a natural fit.

**Q: Why does single-threaded execution matter for correctness, not just speed?** ⭐
Because it makes compound operations like "increment a counter" or "set a key only if it
doesn't exist" atomic without any explicit locking in application code. `INCR` is a
read-modify-write, but since no other command can interleave in the middle of it, two clients
calling `INCR rate:user42` concurrently are guaranteed to get two different, correct results —
there's no lost-update race. Contrast that with doing `GET` then `SET newValue` from
application code across two separate round trips: another client can sneak a write in between,
and you'd silently lose an update. Rate limiters (Phase 4.2) and locks (Phase 4.3) both depend
on this guarantee.

**Q: Is Redis durable? Explain RDB vs AOF.** ⭐
Redis can optionally persist to disk in two ways. RDB takes periodic point-in-time snapshots
of the whole dataset — compact and fast to restore from, but you can lose everything written
since the last snapshot if it crashes (potentially minutes). AOF logs every write command as it
happens, with a configurable fsync policy (`always`, `everysec`, or `no`); `everysec` is the
common default and bounds data loss to about one second, at the cost of a larger file and a
slower replay on restart. Production stores often run both — AOF for durability, RDB for fast
restarts/backups — while a Redis used purely as a cache often runs with minimal or no
persistence at all, because losing it just means cold cache misses, not lost data.

**Q: Redis as a cache vs as a primary data store — does that change anything?** *nuance*
Completely changes the durability calculus. As a cache, Redis holds *derived* data — the
result of a DB query — so the database remains the single source of truth; a crash just means
every key is a miss until it's repopulated, which is a performance blip, not a correctness
problem. As a primary store for some slice of data (say, session state a business genuinely
depends on), Redis becomes the only copy of that data, so you need AOF, replication, and
ideally Sentinel/Cluster for automatic failover — otherwise losing that instance is a real
outage, not a cache-cold event.

**Q: What's Redis's fundamental limitation, and how do you work around it?**
Everything lives in RAM, so the dataset is bounded by the memory you provision — you can't
just keep writing forever. You bound it with `maxmemory` plus an eviction policy (commonly
LRU, covered in Phase 2), keep TTLs on cache entries so cold data expires on its own, and let
the database hold the full, unbounded dataset while Redis only holds the *hot* working set.
For a dataset that outgrows a single instance's memory even for the hot set, Redis Cluster
shards the keyspace across multiple nodes.

**Q: How does Redis differ from Memcached?** *nuance*
Both are in-memory caches with microsecond latency, but Redis is considerably richer: it has
multiple data structures (not just strings), optional persistence, built-in replication,
pub/sub, Lua scripting, and transactions (`MULTI`/`EXEC`). Memcached is deliberately simpler —
effectively a multi-threaded, purely volatile string/blob cache with no persistence and no
structured values. In practice Redis is the default choice today because it covers the
caching use case *and* the middleware patterns in this track (locks, rate limits, sessions)
with one piece of infrastructure; Memcached is chosen when you specifically want its simpler
multi-threaded model for a pure caching workload.

**Q: Where does Redis sit relative to your application's database?**
In front of it, as an accelerator — not a replacement. The database (Postgres, for example)
remains the durable source of truth with full query power (joins, constraints, ACID
transactions). Redis absorbs read load by caching hot query results and holds shared,
ephemeral state (sessions, counters, locks) that would be awkward or slow to coordinate
through the database directly. Writes generally still go to the database; the cache is
populated on read misses and invalidated/updated around writes (Phase 2).

**Q: What would happen if you tried to use Redis as your *only* datastore for a typical web
app?** *nuance*
You'd lose the things a relational database gives you for free: durable ACID transactions
across multiple pieces of related data, rich ad-hoc querying/joins, and a dataset size that
isn't bounded by RAM. You *can* run Redis as a system of record for narrow, well-understood
use cases (an event log via Streams, a leaderboard) if you configure AOF + replication and
accept its simpler query model — but for general relational application data, Redis in front
of Postgres remains the standard architecture, not Redis alone.

**Q: What Redis data structures would you reach for in a caching/middleware context, and
why?**
Strings for simple cached values (a serialized JSON blob per key) and atomic counters (`INCR`
for rate limits/metrics). Hashes for structured, partially-updatable data like a session
(`HSET session:tok userId 42`) without re-serializing the whole object. Sorted sets for
anything ranked or time-windowed — leaderboards (`ZADD`/`ZREVRANGE`) or a sliding-window rate
limiter (`ZADD` a timestamp, `ZREMRANGEBYSCORE` to expire old entries, `ZCARD` to count).
Lists and full streams show up less in pure caching but matter for queues (Phase 4.4).
