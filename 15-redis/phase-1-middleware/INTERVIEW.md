<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching patterns ➡](../phase-2-caching-patterns/NOTES.md)
<!-- /nav -->

# Phase 1 — Redis as Middleware: Interview Q&A

⭐ = asked constantly.

**Q: What is Redis and why is it fast?** ⭐⭐
An in-memory key-value data-structure store. It's fast because data lives in RAM
(microsecond ops, ~100–1000× faster than disk/DB) and command execution is single-threaded,
so each operation is atomic with no lock contention. The trade-off is the dataset must fit
in memory.

**Q: What do you use Redis for?** ⭐⭐
Caching (in front of a DB), session storage (shared across app instances), rate limiting,
distributed locks, leaderboards/counters, idempotency keys, and lightweight pub/sub or
queues. It's the fast, ephemeral layer between the app and the durable database.

**Q: Why does single-threaded execution matter?** ⭐
It makes each command atomic without locks, so operations like `INCR` (counters/rate limits)
and `SET NX` (locks) are race-free by construction. It also keeps latency predictable. Redis
uses threads for I/O, but the logical command loop is single-threaded.

**Q: Is Redis durable? RDB vs AOF?** ⭐
It can persist. RDB takes periodic snapshots — compact and fast to restore, but you can lose
the last few minutes on a crash. AOF logs every write — more durable (≈1s loss with
`everysec`) but larger/slower. For a cache, durability is minor (repopulate from the DB); for
a store, enable AOF plus replication.

**Q: Redis as a cache vs as a primary datastore?** *nuance*
As a cache it's a volatile accelerator in front of the source of truth — losing it just
causes cache misses. As a primary store (for some ephemeral or fast-access data) you must
configure persistence (AOF), replication, and HA (Sentinel/Cluster), because now the data
loss actually matters.

**Q: What's the memory limitation and how do you handle it?**
Everything lives in RAM, so the dataset is bounded by memory. You cap it with `maxmemory` and
an eviction policy (e.g. LRU), and keep only hot/short-lived data (TTLs), letting the DB hold
the full dataset. Sharding (Redis Cluster) scales memory horizontally.

**Q: How does Redis differ from Memcached?** *nuance*
Both are in-memory caches, but Redis has rich data structures, optional persistence,
replication, pub/sub, Lua scripting, and transactions, while Memcached is a simpler, purely
volatile multi-threaded string cache. Redis is the default choice unless you specifically
want Memcached's simplicity/threading model.

**Q: Where does Redis fit relative to your database?**
In front of it: the database (e.g. Postgres) is the durable source of truth; Redis caches
hot reads and holds shared ephemeral state (sessions, counters, locks) to cut latency and
offload the DB. Writes go to the DB; the cache is populated/invalidated around them.
