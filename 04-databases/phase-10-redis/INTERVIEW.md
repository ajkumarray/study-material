<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · mongodb](../phase-9-mongodb/NOTES.md) | [Phase 11 · neo4j ➡](../phase-11-neo4j/NOTES.md)
<!-- /nav -->

# Phase 10 — Redis: Interview Q&A

⭐ = asked constantly.

**Q: What is Redis, and what makes it so fast?** ⭐⭐
Redis is an in-memory data-structure store: all data lives in RAM by default
(with optional persistence to disk for durability), so reads and writes complete
in microseconds rather than the milliseconds a disk-backed database typically
needs. It executes commands single-threaded, which means every individual command
runs to completion without any other command interleaving partway through — giving
every command atomicity "for free," with no locking overhead, since there's no
actual concurrent access at the command-execution level to coordinate. It's more
than a plain key-value store, too — the value behind a key can be a string, hash,
list, set, sorted set, or stream, each with commands purpose-built for a specific
access pattern, which is why Redis calls itself a data-structure server rather than
just a cache.

**Q: What are the main Redis data types, and give a concrete use case for
each.** ⭐⭐
**String** — counters (`INCR`, atomic) and cached blobs/JSON. **Hash** — modeling
one object as a field→value map under a single key, with atomic per-field updates
(`HINCRBY`). **List** — ordered, duplicate-allowing sequences, the natural fit for
queues (`RPUSH` + `LPOP` = FIFO) and stacks. **Set** — unique membership with real
set algebra (`SINTER`/`SUNION`/`SDIFF`) — deduplication, tags, "who's online right
now." **Sorted set** — members each carrying a numeric score, kept automatically
ordered — the standout type, used for leaderboards, priority queues, rate-limiting
windows, and range queries. **Stream** — an append-only event log with consumer
group support, a lightweight analogue of a Kafka topic. Picking the right structure
for the actual access pattern of the problem is the core Redis skill — most of what
looks like "Redis magic" in a well-built system is really just the right built-in
data structure applied to the right problem.

**Q: What is Redis most commonly used for in a real production system?** ⭐
Caching (cache-aside in front of a primary database), session storage, counters and
rate limiting, leaderboards (via sorted sets), work queues (via lists), and pub/sub
messaging for live notifications or cache-invalidation broadcasts. It's
characteristically an *auxiliary* store sitting in front of a primary
system-of-record database, absorbing the hottest and most latency-sensitive
operations, rather than the primary database itself for data that must never be
lost.

**Q: How does TTL/expiry work, and why does it matter so much for Redis's typical
use cases?** ⭐
`EXPIRE key seconds` sets an existing key to auto-delete after that many seconds;
`SET key value EX seconds` does the same thing atomically while also setting the
value, in one round trip. `TTL key` reports the seconds remaining (`-1` = the key
exists with no expiry set, `-2` = the key doesn't exist at all), and `PERSIST key`
removes a key's expiry, making it permanent again. This single mechanism underlies
several completely different production use cases: bounding how stale a cached
value can get before it's automatically discarded, auto-expiring session tokens
after inactivity, invalidating one-time passwords after a fixed window, and
implementing fixed-window rate limiting (pair `INCR` with `EXPIRE` on the first
hit).

**Q: Walk through the cache-aside pattern, and how you'd invalidate a Redis
cache.** ⭐⭐
On a read, the application first checks Redis for the key. On a **hit**, it serves
the cached value directly, never touching the source database. On a **miss**, it
loads the value from the source database, writes it into Redis with a TTL
(`SET book:1 <value> EX 300`), and then serves it. On a **write** to the source
database, the application invalidates the corresponding cache entry — typically
`DEL`eting the key, or updating it directly with the new value — so subsequent
reads don't serve stale data for the rest of the TTL window. TTL itself acts as a
backstop even if an invalidation is ever missed due to a bug: worst case, the
staleness is bounded to whatever the TTL is, rather than unbounded.

**Q: Why does Redis need an eviction policy, and what are the main options?** ⭐
Because Redis stores everything in RAM, its capacity is hard-bounded by the memory
available to it — without a configured limit and eviction strategy, an
ever-growing cache would eventually exhaust memory and crash the process (or start
rejecting every write), which is exactly the "unbounded cache = memory leak"
failure mode from Phase 4/System Design, just moved into Redis's own configuration
surface. You set `CONFIG SET maxmemory <size>` and then a `maxmemory-policy`:
`noeviction` rejects new writes once full (appropriate for data you can't afford to
lose to eviction), `allkeys-lru`/`allkeys-lfu` evict the least-recently/least-
frequently used key across the whole keyspace (the typical general-purpose cache
choice), and `volatile-ttl` evicts whichever key with a TTL set is closest to
expiring next (useful when a single instance mixes expiring cache keys with
non-expiring data).

**Q: Is Redis durable? Explain RDB vs AOF.**
It can be, depending on configuration — by default Redis is purely in-memory, but
it supports two persistence mechanisms that can be used together or separately.
**RDB** takes periodic point-in-time snapshots of the entire dataset to disk —
compact and fast to restart from, but with a data-loss window equal to however
long it's been since the last snapshot if the process crashes in between. **AOF**
(append-only file) logs every write command as it happens and replays that log on
restart to reconstruct the dataset — much more durable (a far smaller loss window),
at the cost of a larger file and a slower restart. The practical guidance: use RDB
for periodic backups, AOF when durability genuinely matters, both together for
maximum safety, or neither if Redis is being used purely as an ephemeral cache
where losing everything on restart is an acceptable, expected outcome.

**Q: Does Redis support transactions? How do `MULTI`/`EXEC` and `WATCH` work?**
Yes — `MULTI` begins queuing a sequence of commands, and `EXEC` runs the entire
queued batch atomically as one unit, with no other client's commands able to
interleave in the middle. `WATCH key` adds optimistic-locking/compare-and-swap
semantics on top: if the watched key is modified by any other client between the
`WATCH` and the `EXEC`, the transaction aborts (`EXEC` returns nil) instead of
applying an update based on a value that's now stale, and the application is
expected to retry. It's worth being precise that this isn't the same as full
relational ACID with rollback semantics — there's no way to roll back individual
commands partway through an already-executing `EXEC` batch (Redis commands within
a transaction don't fail for typical runtime reasons the way SQL statements might).
For genuinely conditional, multi-step atomic logic, a Lua script (`EVAL`) run
server-side is generally the more robust tool.

**Q: How would you build a rate limiter or a leaderboard in Redis?** ⭐
Rate limiter (fixed window): `INCR rate:user:42` to count the request, and
`EXPIRE rate:user:42 60` set on the first hit of each window so the counter resets
every 60 seconds — reject the request if the returned count from `INCR` exceeds the
limit. For a more precise sliding-window limiter, or for the check-and-increment
logic to be genuinely atomic as one step, wrap the logic in a Lua script instead of
two separate commands. Leaderboard: a sorted set — `ZADD leaderboard <score>
<member>` to insert or update a score, `ZREVRANGE leaderboard 0 N-1 WITHSCORES` for
the top N, `ZRANK`/`ZREVRANK` for a specific member's rank, `ZINCRBY` to adjust a
score incrementally. Every one of these sorted-set operations runs in `O(log n)`,
which is exactly why a sorted set stays fast even as the leaderboard grows to
millions of members.

**Q: How does Redis scale and stay highly available?**
**Redis Sentinel** monitors a primary-replica deployment and performs automatic
failover — promoting a replica to primary if the current primary becomes
unreachable — without requiring manual intervention. **Redis Cluster** shards data
across many nodes using hash slots, providing horizontal scale for both storage
capacity and throughput beyond a single node. Replicas can also serve reads
directly to scale read throughput, at the cost of potentially serving slightly
stale data if replication hasn't fully caught up. Redis is single-threaded for
command execution *per node*, but Cluster is exactly how you scale beyond a single
node's throughput ceiling by adding more nodes, each still single-threaded
internally but collectively handling far more aggregate load.

**Q: Redis pub/sub vs a message queue like Kafka or Redis Streams — what's the
difference?**
Redis pub/sub (`SUBSCRIBE`/`PUBLISH`) is fire-and-forget: a published message is
delivered only to clients that are actively subscribed *at that exact moment*, with
zero persistence — if nobody's listening when a message is published, that
message is simply gone, and there's no way for a client that connects a moment
later to retrieve it. Kafka (Phase 16), and Redis's own Streams data type, persist
messages and support replay and consumer groups, so a consumer that was offline
when a message arrived can still process it later, and multiple independent
consumer groups can each process the full message history at their own pace. Use
pub/sub specifically for live, genuinely ephemeral fan-out where a missed message
is truly harmless (live notifications, chat fan-out, cache-invalidation broadcasts
where a subsequent read would just recompute anyway); use Kafka or Streams for
reliable event pipelines where every message must eventually be processed.

**Q: When should Redis explicitly NOT be your primary database?**
When the data needs to be durable, complex, and richly queryable business data with
strong relational integrity and full ACID transaction guarantees across multiple
related records — Redis is fundamentally memory-first and optimized for speed and
simple, purpose-built data structures, not for arbitrary relational queries,
foreign-key integrity, or multi-record transactional consistency the way a
relational database provides natively. The standard, well-established
architecture pairs Redis as a fast auxiliary layer in front of a relational
database (Postgres) that remains the actual source of truth, rather than trying to
make Redis carry that responsibility on its own.
