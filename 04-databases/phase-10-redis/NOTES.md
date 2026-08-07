<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · mongodb](../phase-9-mongodb/NOTES.md) | [Phase 11 · neo4j ➡](../phase-11-neo4j/NOTES.md)
<!-- /nav -->

# Phase 10 — Redis (Key-Value / Data-Structure Store): Notes (Theory)

Redis is an **in-memory data-structure store** — everything lives in RAM, so operations are microsecond-fast. Crucially, it's not just `key → string`: the **value can be a rich data structure**, which is why it's called a *data-structure server*. It's the most-added second database (cache/sessions/counters). Overlaps the **Redis middleware track (15)** and **System Design Phase 2** (caching).

## 10.1 — In-memory model, keys, TTL
Data is keyed by string keys (convention: colon namespaces `user:1:name`, `session:abc`). Because Redis is memory-first, the killer feature is **TTL / expiry**: `EXPIRE key seconds` or `SET key val EX seconds` auto-deletes keys — the basis of caching, sessions, OTPs, and rate-limit windows. `TTL`, `PERSIST`, `DEL`, `EXISTS` manage keys. **When to use Redis:** speed-critical, ephemeral or cacheable data — not the durable system-of-record for critical business data.

## 10.2 — The data types (choosing is the skill)
| Type | Use for |
|---|---|
| **String** | counters (`INCR` — atomic), flags, cached blobs/JSON |
| **Hash** | an object as field→value (`HSET user:2 name .. age ..`) — compact, atomic per-field |
| **List** | ordered, duplicates — queues (`RPUSH`+`LPOP` = FIFO), stacks; `BLPOP` = blocking work queue |
| **Set** | unique membership + set algebra (`SADD`, `SISMEMBER`, `SINTER`) — dedupe, tags, "online users" |
| **Sorted set** | members with scores, auto-ordered — **leaderboards**, priority queues, rate limiting, time-ranges (`ZADD`, `ZREVRANGE`, `ZRANGEBYSCORE`) |
| **Stream** | append-only event log (`XADD`), consumer groups — Kafka-lite for pipelines |

Pick the structure by the access pattern; the sorted set is the standout (rankings, ranges).

## 10.3 — Caching & eviction
Redis's #1 use. **Cache-aside** (System Design 2): on a miss, load from the source DB and `SET` with a TTL; on write, `DEL`/update the key to invalidate. Because Redis is memory-bounded, configure **`maxmemory` + an eviction policy** (`allkeys-lru`/`lfu`, `volatile-ttl`, or `noeviction`) so it evicts instead of OOMing — "a cache without eviction is a memory leak" (Phase 7 / System Design 7), made a config option here.

## 10.4 — Atomicity, transactions, pub/sub, pipelines, scripts
- **Single-threaded command execution** → every command is **atomic**, yet Redis is fast because it's all in RAM. `INCR`+`EXPIRE` is a 2-command rate limiter (System Design 5).
- **Transactions** (`MULTI`/`EXEC`) queue commands to run atomically with no interleaving; **`WATCH`** adds optimistic locking (abort if a key changed — CAS). **Lua scripts** (`EVAL`) run multi-step logic server-side atomically — the correct way to do conditional/multi-step operations (robust rate limiting).
- **Pub/Sub** (`SUBSCRIBE`/`PUBLISH`) — fire-and-forget messaging with no persistence/replay (unlike Streams/Kafka) — live notifications, chat fan-out, cache-invalidation broadcasts.
- **Pipelining** — send many commands without waiting for each reply, cutting round-trips (throughput).

## 10.5 — Persistence, HA, clustering, Spring
- **Persistence** (survive restarts despite being in-memory): **RDB** = point-in-time snapshots (compact, fast restart, a small loss window); **AOF** = append-only log of writes (durable, replayed on restart). Use RDB for backups, AOF for durability, both, or neither (pure cache).
- **HA/scale:** **Sentinel** = automatic failover for primary-replica; **Redis Cluster** = sharding across nodes by hash slots for horizontal scale.
- **With Spring** (Spring Boot 8 / track 15): `spring-boot-starter-data-redis` + `@Cacheable` puts Redis behind your service with almost no code — the caching layer of the full-stack app.

**The role of Redis:** a fast auxiliary store for cache, sessions, counters, leaderboards, rate limiting, queues, and pub/sub — sitting *in front of* your primary database (Postgres), not replacing it.
