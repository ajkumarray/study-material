<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching patterns ➡](../phase-2-caching-patterns/NOTES.md)
<!-- /nav -->

# Phase 1 — Redis as Middleware: Notes

Redis (REmote DIctionary Server) is an **in-memory key-value store** that, used as
middleware, sits between your app and its database to make reads fast and to hold shared,
short-lived state. This track is about those *roles*, not Redis's data structures (Databases
track 04).

## 1.1 — What Redis is; why it's fast

- **In-memory:** data lives in RAM, so operations are **microsecond**-fast (vs
  millisecond disk/DB access) — often 100–1000× faster than hitting Postgres.
- **Single-threaded** command execution: one command at a time, no locks, no race
  conditions on a single key. Each operation is effectively **atomic**, which is *why*
  INCR-based counters and SET-NX locks work correctly (Phase 4). (Modern Redis uses threads
  for I/O, but the command loop is logically single-threaded.)
- **Rich values:** keys map to strings, hashes, lists, sets, sorted sets, streams, etc. —
  so it's a *data-structure server*, not just a string cache (structures covered in the DB
  track; here we use strings/hashes/sorted-sets for middleware patterns).
- Trade-off: dataset must fit in **memory** (it's bounded), and it's primarily a
  fast-but-volatile layer, not your durable system of record.

## 1.2 — The middleware use cases

Redis's speed + atomicity + TTLs make it the Swiss-army knife between app and DB:

- **Cache** (the headline) — store the results of expensive DB queries/computations;
  serve repeats from RAM (Phase 2). Cross-ref System Design Phase 2.
- **Session store** — hold HTTP session state so *any* app instance can serve *any* user —
  the key to horizontal scaling of a stateful web app (Phase 4.1).
- **Rate limiter** — atomic counters with TTL windows to throttle abusive clients
  (Phase 4.2; System Design Phase 5).
- **Distributed lock** — SET-NX + TTL to coordinate exclusive access across instances
  (Phase 4.3).
- **Queue / Pub-Sub** — lightweight messaging and job queues (Phase 4.4; Kafka 16 for
  durable streaming).
- **Ephemeral fast data** — leaderboards (sorted sets), counters, feature flags, idempotency
  keys (System Design Phase 4).

## 1.3 — Persistence & durability

Redis is in-memory but can persist, and the choice depends on the *role*:

- **RDB (snapshots):** periodic point-in-time dumps — compact, fast restart, but you can
  **lose the last few minutes** on a crash. Good for a cache (loss is tolerable).
- **AOF (append-only file):** logs every write; **more durable** (down to ~1s of loss with
  `everysec`), larger, slower restart. For data you can't lose.
- **As a cache:** durability barely matters — a cold cache just repopulates from the DB
  (the source of truth). As a **primary store** for some data, enable AOF (and replication).
- **Replication + Sentinel/Cluster** provide HA and horizontal scale (sharding) — mention;
  detail in the DB track.

## Perspective

Treat Redis as the **fast, volatile layer in front of a durable database**: it absorbs read
load, holds shared ephemeral state (sessions, counters, locks), and does it in microseconds
because it's in-memory and single-threaded-atomic. The mental rule for this track: **Postgres
is the source of truth; Redis makes it fast and lets a scaled-out app share state.** The rest
of the phases are the specific patterns that exploit those properties.
