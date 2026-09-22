<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching patterns ➡](../phase-2-caching-patterns/NOTES.md)
<!-- /nav -->

# Phase 1 — Redis as Middleware: Notes

Redis (**RE**mote **DI**ctionary **S**erver) is an **in-memory key-value data-structure
store**. Used as *middleware*, it sits between your application and its database (or between
instances of a horizontally-scaled application) to make reads fast and to hold shared,
short-lived state. This phase builds the mental model everything else in the track sits on:
what Redis actually is, why it's fast, what roles it plays, and what durability means for
those roles. (Data-structure depth — the exact semantics of strings/hashes/sets/sorted-sets —
lives in the Databases track, 04; this track is the *patterns* built on top of them.)

## 1.1 — What Redis is, and why it's fast

### Definition

Redis is a single-process, in-memory server that stores data as key-value pairs, where the
*value* can be a rich structure (string, hash, list, set, sorted set, stream, …), and that
answers commands over the network in well under a millisecond. It is commonly deployed as a
**cache and coordination layer** in front of a slower, durable database.

### Key Concepts

- **In-memory:** the entire (working) dataset lives in RAM, not on disk. RAM access is on the
  order of **microseconds**; a disk-backed relational query is typically **milliseconds** —
  often 100–1000× slower. This single fact is the entire reason Redis exists as a caching
  layer: it trades durability/capacity for raw speed.
- **Single-threaded command execution:** the core Redis event loop processes one command at a
  time — there is no other thread mutating data concurrently with it. This means every single
  command is **atomic** by construction: no two clients can interleave mid-command and corrupt
  a value, and there's no lock contention or context-switching overhead to pay for that
  guarantee. `INCR`, `SET ... NX`, and `HSET` are all safe under concurrent access from many
  clients for exactly this reason. (Modern Redis — 6+ — uses background *I/O threads* to read
  request bytes off the socket and write response bytes back, but command *execution* against
  the data itself is still logically single-threaded.)
- **A data-structure server, not just a string cache.** Keys can map to strings, hashes
  (field→value maps), lists, sets, sorted sets (score-ordered sets), streams (append-only
  logs), bitmaps, and HyperLogLogs. Middleware patterns lean on a handful of these: strings for
  simple cached values and counters, hashes for structured session data, sorted sets for
  leaderboards/sliding windows.
- **Client-server, TCP-based.** Applications talk to Redis over a lightweight text protocol
  (RESP) via a client library (Lettuce/Jedis in Java) — every operation is a network round
  trip, so even though the *server-side* work is microseconds, the *observed* latency also
  includes network time. This is why connection pooling and keeping Redis physically close to
  the app (same AZ/data-center) matters.
- **Bounded by memory.** Because everything lives in RAM, your dataset must fit in the
  configured `maxmemory`. This is Redis's fundamental trade-off: it is *fast and volatile*,
  not a replacement for a disk-backed system of record with unbounded capacity.

### Worked example — feeling the difference

```
# redis-cli: SET + GET round-trip, typically sub-millisecond on localhost
127.0.0.1:6379> SET expense:1 '{"id":1,"desc":"Lunch","cents":1250}'
OK
127.0.0.1:6379> GET expense:1
"{\"id\":1,\"desc\":\"Lunch\",\"cents\":1250}"

# Two clients racing an atomic increment — no lost updates, no locking needed
# (run concurrently from two terminals against the same key)
127.0.0.1:6379> INCR pageviews:home
(integer) 1
127.0.0.1:6379> INCR pageviews:home
(integer) 2
```

In this example, `SET`/`GET` round-trip a JSON-serialized value the way a cache-aside read
would (Phase 2). The `INCR` calls show why single-threaded execution matters in practice: even
if a thousand app instances call `INCR pageviews:home` at the exact same instant, Redis
processes them one at a time internally, so the counter always ends up exactly correct — there
is no read-modify-write race the way there would be with a naive `GET` then `SET` in
application code.

### Comparison table — Redis vs a traditional relational database

| | Redis | PostgreSQL/MySQL (RDBMS) |
|---|---|---|
| Storage | In-memory (RAM), optionally persisted | On-disk, durable by default |
| Typical latency | Microseconds | Milliseconds |
| Data model | Key-value with rich value types | Relational tables, joins, constraints |
| Durability | Optional (RDB/AOF), best-effort | ACID transactions, WAL |
| Capacity | Bounded by RAM | Bounded by disk (much larger) |
| Query power | Simple ops per data structure, no joins | Rich SQL, joins, aggregates, indexes |
| Role in this track | Fast layer *in front of* the DB | Source of truth |

### Why it's useful

Every middleware pattern in this track — caching, sessions, rate limiting, locks — is really
just "use RAM speed plus atomic single-threaded commands to do something that would be slow or
race-prone against the primary database." Understanding *why* Redis is fast (RAM) and *why* it
is safe under concurrency (single-threaded execution) is what lets you reason correctly about
which patterns are safe to build on top of it, and which ones (like a naive
"check-then-set" done as two separate commands) are not.

## 1.2 — The middleware use cases

Redis's combination of speed, atomicity, and expiring keys (TTLs) makes it the general-purpose
"fast, shared, ephemeral state" tool sitting between an app and its database. The recurring
roles:

### Key Concepts

- **Cache** (the headline use case, Phase 2) — store the result of an expensive DB query or
  computation; serve repeat requests straight from RAM instead of re-querying the database.
- **Session store** (Phase 4.1) — hold HTTP session state (login identity, cart contents)
  outside any single application instance, so *any* instance can serve *any* user's request.
  This is the key enabler of horizontally scaling a stateful web app.
- **Rate limiter** (Phase 4.2) — atomic counters with a TTL window throttle how many requests a
  client can make in a period, protecting the API from abuse or overload.
- **Distributed lock** (Phase 4.3) — `SET key value NX EX ttl` lets one instance out of many
  claim exclusive access to a resource (a job, a critical section) with an automatic release on
  crash via the TTL.
- **Queue / Pub-Sub** (Phase 4.4) — lightweight fire-and-forget messaging for fan-out
  notifications and cache-invalidation signals; Redis Streams for a durable log within Redis;
  Kafka (track 16) for a full durable event backbone.
- **Ephemeral fast data** — leaderboards (sorted sets), live counters, feature flags,
  idempotency keys (System Design Phase 4) — any small piece of state that needs to be read and
  written extremely fast and shared across a fleet of app instances.

### Worked example — one Redis instance, several roles at once

```
# Cache: an expense lookup with a 10-minute TTL
SET expense:1 '{"id":1,"cents":1250}' EX 600

# Session: a hash of fields with an idle-timeout TTL
HSET session:tok123 userId 42 role admin
EXPIRE session:tok123 1800

# Rate limit: an atomic counter with a fixed window
INCR rate:user42
EXPIRE rate:user42 60

# Lock: claim exclusive ownership of an order, auto-release after 30s
SET lock:order99 owner-abc NX EX 30
```

In this example, four completely different application concerns — a cache entry, a session, a
rate-limit counter, and a distributed lock — are each just a key in the *same* Redis instance,
using the *same* small vocabulary of commands (`SET`/`GET`/`INCR`/`EXPIRE`/`HSET`). This is the
core reason teams reach for Redis repeatedly: one operational piece of infrastructure covers
several distinct architectural needs.

### Why it's useful

In a horizontally-scaled system, application instances are stateless and disposable by design
— but *someone* has to hold the shared state that ties requests together (a session, a rate
counter, a lock). Redis is the conventional place to put exactly that kind of state: fast
enough not to become the bottleneck, and TTL-capable so ephemeral state cleans itself up
without a cron job.

## 1.3 — Persistence & durability

### Definition

Redis is in-memory first, but it *can* persist its dataset to disk so it survives a restart.
Which persistence mode (if any) you choose depends entirely on the *role* that instance is
playing.

### Key Concepts

- **RDB (snapshotting):** periodically dumps the entire dataset to a compact binary file (e.g.
  every N seconds if M keys changed). Fast to restart from (just load one file), small on disk,
  but you can **lose every write since the last snapshot** on a crash — potentially minutes of
  data.
- **AOF (append-only file):** logs every write command as it happens. Configurable fsync
  policy: `always` (fsync every write — safest, slowest), `everysec` (fsync once per second —
  the common default, loses at most ~1 second of writes), or `no` (let the OS decide — fastest,
  least safe). AOF files are larger and slower to replay on restart than an RDB snapshot, but
  far more durable.
- **Both, together:** production Redis used as a store often runs AOF for durability plus
  periodic RDB snapshots for fast restarts and compact backups; Redis can rewrite/compact the
  AOF file in the background to keep it from growing unbounded.
- **No persistence at all** is also a valid choice for a pure cache: if Redis is only ever
  holding *derived* data (a cache of DB query results), losing it on crash just means every key
  is a cold miss afterward — the database is unaffected and still has the truth.
- **Replication + Sentinel/Cluster** provide high availability (a replica takes over if the
  primary dies) and horizontal scale (Cluster shards the keyspace across nodes). These are
  infrastructure concerns detailed in the Databases track (04); worth knowing they exist and
  that a middleware Redis for sessions/locks in a serious production system should be deployed
  with replication for availability, not as a single point of failure.

### Worked example — reasoning about the two roles

```
# As a CACHE (default expense-api usage): no special persistence config needed.
# A cold restart just means every GET is a miss until the cache is repopulated:
GET expense:1
# (nil)                      <- miss after a restart; app falls back to Postgres,
#                                re-populates the cache on the next read

# As a SESSION STORE with real user-facing consequences: you'd want AOF (everysec)
# and replication, because losing sessions means every logged-in user is logged out.
```

In this example, the same `GET` miss means two very different things depending on the role:
for a cache it's a normal, cheap event (the source of truth in Postgres is untouched); for a
session store it means real users get logged out. This is why "does Redis need persistence?"
has no single answer — it depends entirely on whether Redis is holding *derived*, reproducible
data or the *only* copy of something that matters.

### Comparison table — RDB vs AOF

| | RDB (snapshot) | AOF (append-only log) |
|---|---|---|
| What it stores | Periodic full dataset snapshot | Every write command, replayed on restart |
| Durability | Can lose minutes of writes | Can lose ~1s (`everysec`) to ~0 (`always`) |
| Restart speed | Fast (load one compact file) | Slower (replay the log) |
| File size | Compact | Larger (mitigated by background rewrite) |
| Best for | Caches, periodic backups | Data you genuinely can't afford to lose |

### Why it's useful

Deciding "does this Redis instance need AOF and replication?" is really the question "is Redis
the source of truth here, or just an accelerator in front of one?" A cache in front of Postgres
needs neither — a cold cache just repopulates. A Redis instance holding session state or a
distributed-lock table that the business genuinely depends on needs both, because now data
loss has a real user-facing or correctness cost.

## Summary / Key Takeaways

- Redis is fast because it's **in-memory** (RAM vs disk = microseconds vs milliseconds) and
  **single-threaded** for command execution, which makes every individual command atomic with
  no locking overhead — the foundation `INCR`-based counters and `SET NX` locks rely on.
  Modern Redis threads I/O, not command execution.
- It's a **data-structure server**: strings, hashes, sets, sorted sets, streams — middleware
  patterns use a handful of these (strings/hashes for cache/session values, sorted sets for
  leaderboards/windows).
- The recurring middleware roles are **cache, session store, rate limiter, distributed lock,
  pub/sub, and ephemeral fast data** — all exploiting the same speed + atomicity + TTL
  properties.
- **Persistence is a choice tied to role:** RDB (fast restart, can lose minutes) vs AOF
  (durable, slower) vs none. A cache typically needs neither (repopulate from the DB); a store
  needs AOF + replication.
- The mental model for the whole track: **the durable database is the source of truth; Redis
  is the fast, volatile layer that makes reads fast and lets a scaled-out app share ephemeral
  state.**
