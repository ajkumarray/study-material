<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 15 — Redis (as Middleware)

**An in-memory data store used as the fast layer in front of your database** — caching,
sessions, rate limiting, distributed locks, and lightweight messaging. Redis makes a
slow, stateful backend feel fast and lets a horizontally-scaled app share state.

> **Scope note:** the **Databases track (04)** already covers Redis as a *data store* — its
> data structures (strings/hashes/sets/sorted-sets/streams), persistence, and commands.
> **This track is Redis as *middleware*** wired into the **Spring Boot API (02)**: the
> caching/session/lock/rate-limit *patterns* and their Spring integration. Minimal overlap
> by design.
>
> **Format:** no Redis server runs here, so it's **theory + real Spring config/code + CLI
> patterns** (`examples/`), correct-by-construction — the DB/Docker/K8s-track approach.

## The artifacts: `examples/`

| File | What it shows |
|---|---|
| `application.yml` | Spring config: Redis connection, **Spring Cache** (TTL, key prefix), **Spring Session** in Redis |
| `CachingExample.java` | `@Cacheable`/`@CachePut`/`@CacheEvict` — automated cache-aside on the service layer |
| `DirectRedisExample.java` | `RedisTemplate` for a **rate limiter** (INCR+TTL) and a **distributed lock** (SET NX) |
| `redis-commands.redis` | CLI cheat-sheet for the middleware patterns (TTL, counters, locks, pub/sub) |

## Curriculum

### Phase 1 — Redis as middleware ✅
- [x] 1.1 What Redis is (in-memory, single-threaded, microsecond ops); why it's fast
- [x] 1.2 The middleware use cases: cache, session store, rate limiter, lock, queue/pub-sub
- [x] 1.3 Persistence & durability (RDB/AOF) and what it means for a cache vs a store
- NOTES · INTERVIEW

### Phase 2 — Caching patterns ✅
- [x] 2.1 Cache-aside (lazy) vs read-through vs write-through vs write-behind
- [x] 2.2 TTL & eviction policies (LRU/LFU); the memory bound
- [x] 2.3 Invalidation, stale data, and cache stampede (cross-ref System Design 2)
- NOTES · INTERVIEW

### Phase 3 — Spring Boot integration ✅
- [x] 3.1 The Spring Cache abstraction: `@EnableCaching`, `@Cacheable`/`@CachePut`/`@CacheEvict`
- [x] 3.2 `RedisTemplate`/`StringRedisTemplate`, serialization, key design
- [x] 3.3 Config: connection pool (Lettuce), TTL, key prefixes, cache-null handling
- NOTES · INTERVIEW

### Phase 4 — Sessions, locks, rate limiting, pub/sub ✅
- [x] 4.1 Distributed session storage (Spring Session) for a scaled-out app
- [x] 4.2 Rate limiting (fixed/sliding window) with atomic INCR + TTL
- [x] 4.3 Distributed locks (SET NX + TTL); the pitfalls (Redlock, fencing)
- [x] 4.4 Pub/Sub messaging — and when to reach for Kafka (16) instead
- NOTES · INTERVIEW

### Capstone ✅
- [x] Wire caching + sessions into the expense-api: `@Cacheable` reads, evict on write,
  Redis-backed sessions, a rate-limited endpoint. See `CAPSTONE.md`.

## How this connects

- **← Spring Boot (02):** Redis plugs into the service layer via the cache abstraction.
- **← Databases (04):** Postgres is the source of truth; Redis is the fast cache in front.
- **↔ System Design (17):** caching, stampede protection, rate limiting, and idempotency
  keys are core system-design tools — this track is their concrete Redis implementation.
- **→ Kafka (16):** Redis Pub/Sub is fire-and-forget; Kafka is durable, replayable
  streaming — Phase 4.4 draws the line.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · middleware** — [Notes](phase-1-middleware/NOTES.md) · [Interview](phase-1-middleware/INTERVIEW.md)
- **Phase 2 · caching patterns** — [Notes](phase-2-caching-patterns/NOTES.md) · [Interview](phase-2-caching-patterns/INTERVIEW.md)
- **Phase 3 · spring integration** — [Notes](phase-3-spring-integration/NOTES.md) · [Interview](phase-3-spring-integration/INTERVIEW.md)
- **Phase 4 · patterns** — [Notes](phase-4-patterns/NOTES.md) · [Interview](phase-4-patterns/INTERVIEW.md)
<!-- /phases-nav -->
