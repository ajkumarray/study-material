<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · mongodb](../phase-9-mongodb/NOTES.md) | [Phase 11 · neo4j ➡](../phase-11-neo4j/NOTES.md)
<!-- /nav -->

# Phase 10 — Redis (Key-Value / Data-Structure Store): Notes (Theory)

Redis is an **in-memory data-structure store** — every key and value lives in RAM,
so operations complete in microseconds. What sets it apart from a plain key-value
cache is that the *value* itself can be a rich data structure (a hash, a list, a
set, a sorted set, a stream), not just a string — which is why Redis calls itself a
*data-structure server*, not merely a cache. It's the most commonly added "second
database" in a real system's architecture: a fast auxiliary store for cache,
sessions, counters, leaderboards, rate limiting, queues, and pub/sub, sitting *in
front of* a primary database like Postgres rather than replacing it. All examples
run in `redis-cli`.

---

## 1. The In-Memory Model, Keys, and TTL

### Key Concepts

- **In-memory, key-based**: every piece of data is addressed by a string key.
  Convention favors colon-separated namespaces (`user:1:name`, `session:abc`,
  `cart:42:items`) for readability and grouping — Redis itself treats the key as
  an opaque string, the colons are purely a human convention.
- **TTL / expiry**: because Redis's data lives in memory (a resource you don't want
  to fill up with data nobody needs anymore), the killer feature is automatic
  expiration — `EXPIRE key seconds`, or `SET key value EX seconds` to set the value
  and its expiry in one command. This is the foundation of caching, session
  management, one-time codes, and rate-limit windows.
- **Key management commands**: `TTL key` (seconds remaining, `-1` = no expiry set,
  `-2` = key doesn't exist), `PERSIST key` (remove a key's TTL, keep it forever),
  `DEL key` (delete immediately), `EXISTS key` (returns `1`/`0`).
- **When to use Redis**: speed-critical, ephemeral, or safely-cacheable data. Not
  the durable system-of-record for business-critical data that must never be lost —
  that's what a relational database is for.

### Worked Example

```
SET user:1:name "Ajay"
GET user:1:name                    # "Ajay"

SET pageviews 0
INCR pageviews                     # (integer) 1  — atomic increment, great for counters
INCRBY pageviews 10                # (integer) 11

SET session:abc "user-42"
EXPIRE session:abc 3600            # (integer) 1  — key now auto-deletes in 1 hour
TTL session:abc                    # (integer) 3600  (counts down from here)

SET otp:9876 "123456" EX 300       # set the value AND a 5-minute expiry, in one command
PERSIST session:abc                # (integer) 1  — the session key's TTL is removed; it never expires now
DEL user:1:name                    # (integer) 1  — the key is gone
EXISTS session:abc                 # (integer) 1
```
In this example: `EXPIRE` and `SET ... EX` accomplish the same end state — a key
that self-destructs after N seconds — but `SET key value EX seconds` does it
atomically in one round trip, whereas `SET` followed by a separate `EXPIRE` is two
commands (and, without a transaction, a tiny window where the key briefly exists
with no expiry set at all).

### Why It's Useful

TTL is what makes Redis a *safe* cache rather than a slowly-growing memory leak:
data you load into Redis to speed up reads automatically disappears on its own once
it's old enough that you'd rather refetch it from the source than trust it's still
accurate. The same mechanism, reused, is exactly how session tokens auto-expire
after inactivity and how a one-time password becomes invalid after its window
passes — one primitive, several completely different production use cases.

### Summary / Key Takeaways

- Redis is memory-first — every command runs at RAM speed, which is the whole
  reason it exists as a separate technology from a disk-backed database.
- TTL/expiry (`EXPIRE`, `SET ... EX`) is Redis's signature feature — it's the
  mechanism behind caching, sessions, OTPs, and rate-limit windows all at once.
- Use Redis for ephemeral, cacheable, or speed-critical data — not as the durable
  system of record for data that must never be lost.

---

## 2. The Data Types — Choosing the Right One Is the Skill

Redis's real power is that a value isn't limited to a string — it can be one of
several purpose-built data structures, each with commands specialized for a
particular access pattern.

### Key Concepts

- **String**: the simplest value — text, numbers, JSON blobs. `INCR`/`INCRBY`
  give you atomic counters "for free," with no read-modify-write race condition.
- **Hash**: a field → value map, letting you model one object (like a user
  record) compactly under a single key, with atomic per-field operations, instead
  of scattering many separate string keys.
- **List**: an ordered collection allowing duplicates — the natural fit for
  queues (`RPUSH` + `LPOP` gives FIFO order) and stacks (`RPUSH` + `RPOP` gives
  LIFO order). `BLPOP` blocks until an element is available, turning a list into
  a simple blocking work queue.
- **Set**: unique, unordered membership, with real set algebra (`SINTER` for
  intersection, `SUNION` for union, `SDIFF` for difference) — good for
  deduplication, tagging, and "who's currently online" style membership checks.
- **Sorted set (ZSET)**: like a set, but every member also carries a numeric
  **score**, and the set is kept automatically ordered by that score. This is
  Redis's standout structure — leaderboards, priority queues, rate-limiting
  windows, and any "give me the top/bottom N" or "give me everything in this
  score range" query.
- **Stream**: an append-only log of entries, each with an auto-generated or
  explicit ID, supporting consumer groups for reliable, at-least-once processing —
  a lightweight, Redis-native analogue of a Kafka topic (Phase 16 covers Kafka
  proper).

### Worked Examples

```
-- HASH: model an object compactly under one key
HSET user:2 name "Meera" city "Mumbai" age 25
HGET user:2 name                   # "Meera"
HGETALL user:2                     # 1) "name" 2) "Meera" 3) "city" 4) "Mumbai" 5) "age" 6) "25"
HINCRBY user:2 age 1               # (integer) 26 — atomic increment of just that field
```
A hash lets an entire small object live under one key with one memory allocation,
rather than as several separate `user:2:name`, `user:2:city`, `user:2:age` string
keys — more compact, and `HINCRBY` still gives you an atomic per-field increment.

```
-- LIST: a FIFO job queue
RPUSH queue:jobs "job-1" "job-2"   # push onto the tail (right)
LPUSH queue:jobs "job-0"           # push onto the head (left) -> queue is now [job-0, job-1, job-2]
LRANGE queue:jobs 0 -1             # 1) "job-0" 2) "job-1" 3) "job-2"
LPOP queue:jobs                    # "job-0" — popped from the head: RPUSH+LPOP = FIFO
-- BLPOP queue:jobs 5              -- BLOCKS up to 5s waiting for an element: a simple work queue
```

```
-- SET: membership and set algebra
SADD tags:book:1 "oop" "clean-code" "oop"    # (integer) 2 — the duplicate "oop" is silently ignored
SISMEMBER tags:book:1 "oop"                  # (integer) 1
SADD online:users "u1" "u2"
SADD premium:users "u2" "u3"
SINTER online:users premium:users            # 1) "u2"  — online AND premium, in one command
SCARD tags:book:1                            # (integer) 2
```

```
-- SORTED SET: a leaderboard — the standout Redis type
ZADD leaderboard 100 "ajay" 250 "meera" 175 "ravi"
ZREVRANGE leaderboard 0 2 WITHSCORES
-- 1) "meera" 2) "250" 3) "ravi" 4) "175" 5) "ajay" 6) "100"    (top 3, descending score)
ZRANK leaderboard "ajay"                     # (integer) 0 — ajay's rank ascending (lowest score = rank 0)
ZINCRBY leaderboard 50 "ajay"                # (double) 150 — ajay's new score after the increment
ZRANGEBYSCORE leaderboard 150 300            # everyone with a score in [150, 300]
```
In this example: every one of these sorted-set operations — insert with a score,
get the top N, get a member's rank, increment a score, range-query by score — runs
in `O(log n)` time, because a sorted set is internally backed by a skip list plus a
hash table. That's exactly why it's the go-to structure for a leaderboard: updating
one player's score and re-reading the current top 10 are both cheap, even as the
leaderboard grows large.

```
-- STREAM: an append-only event log
XADD events * type "login" user "u1"         # * = auto-generate a timestamp-based id
XLEN events                                   # (integer) 1
-- XREAD / consumer groups (XREADGROUP) enable reliable, replayable event processing
```

### Comparison Table

| Type | Use for | Killer command(s) |
|---|---|---|
| String | counters, flags, cached blobs/JSON | `INCR`, `SET ... EX` |
| Hash | one object as field→value | `HSET`, `HGETALL`, `HINCRBY` |
| List | queues, stacks | `RPUSH`+`LPOP` (FIFO), `BLPOP` (blocking) |
| Set | unique membership, set algebra | `SADD`, `SISMEMBER`, `SINTER` |
| Sorted set | leaderboards, ranges, priority | `ZADD`, `ZREVRANGE`, `ZRANGEBYSCORE` |
| Stream | event log, message pipelines | `XADD`, consumer groups |

### Why It's Useful

Picking the right structure for the access pattern is what makes Redis feel almost
"too easy" for problems that would otherwise require real application-level
engineering — a leaderboard implemented with a sorted set is a handful of `O(log
n)` commands; the equivalent built from scratch with a relational database and
application code would need careful indexing, ranking queries, and probably caching
of its own.

### Summary / Key Takeaways

- Redis values aren't just strings — hashes, lists, sets, sorted sets, and streams
  each specialize for a different access pattern.
- `INCR` gives atomic counters with no race condition; a hash models one object
  compactly; a sorted set is the standout type for anything ranked or range-based.
- Choosing the right data structure for the job is the entire skill of using Redis
  well — most of Redis's "magic" is really just picking the right built-in tool.

---

## 3. Caching and Eviction

Caching is Redis's single most common production use case.

### Key Concepts

- **Cache-aside pattern**: on a cache miss, the application loads the data from
  the source database and writes it into Redis with a TTL; on a cache hit, it
  serves straight from Redis without touching the source database at all.
  **Invalidation** on write means deleting (or updating) the cached key when the
  underlying source row changes, so stale data doesn't linger past a real change —
  with TTL acting as an automatic backstop even if an invalidation is ever missed.
- **`maxmemory` + an eviction policy**: because Redis is memory-bounded, you
  configure a hard memory cap and a policy for what happens once it's reached,
  rather than letting Redis simply run out of memory and crash or reject every
  write.
- **Eviction policies**: `noeviction` (reject new writes once full — safest for
  non-cache data, worst for availability), `allkeys-lru`/`allkeys-lfu` (evict the
  least-recently/least-frequently used key across the whole dataset — the typical
  pure-cache choice), `volatile-ttl` (evict whichever key is closest to expiring
  next, only among keys that have a TTL set at all).

### Worked Example

```
-- Cache-aside, application-side pseudocode:
--   value = GET book:1
--   if value is null:                 # cache MISS
--       value = <query Postgres for book id=1>
--       SET book:1 <value> EX 300     # populate the cache with a 5-minute TTL
--   return value

SET book:1 '{"title":"Clean Code","price":41}' EX 300
GET book:1
-- '{"title":"Clean Code","price":41}'
```
```
-- Bounding memory so Redis evicts instead of running out of memory
CONFIG SET maxmemory 256mb
CONFIG SET maxmemory-policy allkeys-lru
```
In this example: `book:1` is cached JSON with a 5-minute TTL — if the underlying
`book` row is updated in Postgres in the meantime, the application should also
`DEL book:1` (or overwrite it) so the next read doesn't serve stale data for the
rest of that 5-minute window; if the invalidation is somehow missed, the TTL still
guarantees the staleness is bounded to at most 5 minutes rather than unbounded.

### Comparison Table

| Eviction policy | Evicts | Best for |
|---|---|---|
| `noeviction` | nothing — rejects new writes once full | non-cache data you can't afford to lose to eviction |
| `allkeys-lru` | least-recently-used key, any key | a general-purpose pure cache |
| `allkeys-lfu` | least-frequently-used key, any key | a cache with a skewed, "hot key" access pattern |
| `volatile-ttl` | the key closest to its own expiry | mixed cache + non-expiring keys in the same instance |

### Why It's Useful

Without a `maxmemory` cap and an eviction policy, an unbounded, ever-growing Redis
cache is precisely the "memory leak" story covered generally in Phase 4/System
Design — the difference is Redis turns that failure mode into an explicit,
one-line configuration decision (evict, or don't) rather than something that
silently accumulates until the process runs out of memory and crashes.

### Summary / Key Takeaways

- Cache-aside is the standard pattern: read through the cache on hit, load-and-
  populate on miss, invalidate (delete/update) the cached key on write.
- TTL bounds worst-case staleness even if an invalidation is ever missed.
- Always configure `maxmemory` and an eviction policy — an unbounded cache is a
  memory leak with extra steps.
- `allkeys-lru`/`allkeys-lfu` are the typical pure-cache choices; `volatile-ttl`
  fits a mixed workload of expiring and non-expiring keys sharing one instance.

---

## 4. Atomicity, Transactions, Pub/Sub, Pipelines, and Scripts

### Key Concepts

- **Single-threaded command execution**: Redis processes commands one at a time
  on a single thread (per instance/shard), which means every individual command is
  inherently **atomic** — no other command can interleave with it partway through.
  This is also *why* Redis can be so fast despite being single-threaded: there's no
  locking overhead for concurrent access, since there's no concurrent access to
  begin with at the command level.
- **`MULTI`/`EXEC`**: queues a batch of commands, then executes all of them
  atomically as a single unit, with no other client's commands interleaving
  between them.
- **`WATCH`**: adds optimistic locking to a transaction — if any watched key
  changes between the `WATCH` and the `EXEC`, the whole transaction is aborted
  (compare-and-swap semantics), and the application is expected to retry.
- **Lua scripts (`EVAL`)**: run multi-step, conditional server-side logic
  atomically, as a single unit — the correct tool for logic that's more
  complicated than `MULTI`/`EXEC` alone can express cleanly (e.g. a rate limiter
  that needs to check a value and conditionally act on it in one atomic step).
- **Pub/Sub** (`SUBSCRIBE`/`PUBLISH`): fire-and-forget messaging — a published
  message goes only to clients that are subscribed *at that exact moment*, with no
  persistence and no replay for anyone who wasn't listening.
- **Pipelining**: sending many commands to the server without waiting for each
  individual reply before sending the next one, cutting the number of network
  round trips and significantly increasing throughput for batches of commands.

### Worked Examples

```
-- A 2-command atomic-per-command rate limiter (fixed window)
INCR rate:user:42          # count this request; first call also creates the key at 1
EXPIRE rate:user:42 60     # the window resets 60s after the FIRST hit sets this
-- if the returned count from INCR exceeds the limit, the application rejects the request (429)
```
Each of `INCR` and `EXPIRE` is individually atomic (single-threaded execution
guarantees that), but the *pair* of them together isn't automatically atomic as a
unit — a more robust rate limiter would wrap this in a Lua script so the
check-and-increment logic can't race across two separate round trips.

```
-- MULTI/EXEC — queue commands, then run them all atomically as one unit
MULTI
INCR pageviews
SET last_view "now"
EXEC
-- both commands apply as a single atomic unit; no other client's command can
-- interleave between the INCR and the SET
```

```
-- WATCH — optimistic locking / compare-and-swap
WATCH balance:42
val = GET balance:42        -- read the current value
MULTI
SET balance:42 <val - 10>
EXEC
-- if balance:42 changed between WATCH and EXEC (someone else wrote to it), EXEC
-- returns nil (aborted) instead of applying the stale-based update — the app retries
```

### Comparison Table

| | `MULTI`/`EXEC` | Lua script (`EVAL`) | Pub/Sub |
|---|---|---|---|
| Guarantees | atomic as a batch, no interleaving | atomic, arbitrary logic | none — fire-and-forget |
| Can read-then-conditionally-act | only with `WATCH` (CAS, may abort/retry) | yes, natively, in one round trip | n/a |
| Best for | simple batched writes | conditional/multi-step atomic logic (robust rate limiting) | live notifications, fan-out, cache-invalidation broadcasts |

### Why It's Useful

Single-threaded, atomic-per-command execution is what makes Redis trustworthy for
things like counters and locks without any application-level locking code at
all — `INCR` simply cannot race with itself, ever, by construction. Lua scripts
extend that same atomicity guarantee to genuinely multi-step logic, which is the
difference between a rate limiter that's "probably fine" and one that's actually
correct under real concurrent load.

### Summary / Key Takeaways

- Every individual Redis command is atomic because execution is single-threaded —
  this is a large part of why Redis is both simple and fast.
- `MULTI`/`EXEC` batches commands atomically; `WATCH` adds optimistic
  locking/CAS semantics on top.
- Lua scripts (`EVAL`) are the correct tool for atomic, multi-step, conditional
  logic that `MULTI`/`EXEC` alone can't cleanly express.
- Pub/Sub is fire-and-forget with zero persistence/replay — for reliable,
  replayable event processing, reach for Redis Streams or a dedicated system like
  Kafka (Phase 16) instead.
- Pipelining cuts round-trip overhead for sending many commands, independent of
  any atomicity concern.

---

## 5. Persistence, High Availability, Clustering, and Redis with Spring

### Key Concepts

- **RDB (Redis Database) persistence**: periodic point-in-time snapshots of the
  entire dataset written to disk — compact, fast to restart from, but with a
  potential data-loss window equal to however long it's been since the last
  snapshot.
- **AOF (Append-Only File) persistence**: a log of every write command, replayed
  on restart to reconstruct the dataset — more durable (a much smaller potential
  loss window) but a larger file and slower restart than RDB.
- Use RDB for periodic backups, AOF for stronger durability, both together for
  maximum safety, or neither if Redis is being used purely as an ephemeral cache
  where losing everything on restart is an acceptable, expected outcome.
- **Redis Sentinel**: monitors a primary-replica deployment and performs automatic
  failover — promoting a replica to primary if the current primary becomes
  unreachable — providing high availability without manual intervention.
- **Redis Cluster**: shards data across many nodes using hash slots, providing
  horizontal scale for both storage and throughput beyond what one node can
  handle.
- **Redis with Spring**: `spring-boot-starter-data-redis` combined with
  `@Cacheable` puts Redis behind a service method with almost no application code —
  the standard way Redis shows up as the caching layer of a full-stack Spring
  application.

### Worked Example

```
CONFIG SET save "900 1"        # take an RDB snapshot if there's been >= 1 change in 900s
CONFIG SET appendonly yes      # also enable AOF for stronger durability
```
In this example: enabling both means Redis gets AOF's stronger durability as the
primary recovery mechanism, with RDB snapshots still available as a compact backup
format that's faster to transfer/restore from than replaying a large AOF file from
scratch.

### Comparison Table

| | RDB | AOF |
|---|---|---|
| What it stores | periodic full snapshots | every write command, replayed on restart |
| Data-loss window | since the last snapshot | much smaller — near the last fsync |
| Restart speed | fast (load one snapshot) | slower (replay the whole log) |
| File size | compact | larger |

### The Role of Redis in a Real Architecture

Redis is a fast auxiliary store for cache, sessions, counters, leaderboards, rate
limiting, queues, and pub/sub — it sits *in front of* a primary database like
Postgres, absorbing the hottest, most latency-sensitive reads and simple
structured operations, rather than replacing the primary database as the system
of record for business-critical, durable data.

### Why It's Useful

Understanding RDB vs AOF, Sentinel vs Cluster, and where Redis fits architecturally
is what separates "I know Redis commands" from "I know how to actually operate
Redis in production" — the commands are the easy half; knowing what durability and
availability guarantees you're actually getting (and giving up) is the half that
matters when something goes wrong.

### Summary / Key Takeaways

- RDB = compact periodic snapshots, faster restart, larger loss window; AOF = a
  full write log, smaller loss window, slower restart — use one, both, or neither
  depending on how much durability the use case actually needs.
- Sentinel handles automatic failover for primary-replica setups; Cluster handles
  horizontal sharding across many nodes.
- `spring-boot-starter-data-redis` + `@Cacheable` is the near-zero-code way Redis
  becomes a Spring application's caching layer.
- Redis's role is a fast auxiliary store sitting in front of a primary database —
  not a replacement for one, for data that genuinely must never be lost.
