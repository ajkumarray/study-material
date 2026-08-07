# Capstone — Redis Middleware in the Expense API

The Redis track's synthesis: wire Redis into the Spring `expense-api` as a **cache**, a
**session store**, and a **rate limiter** — the three flagship middleware roles — shown as
real Spring config/code in `examples/`.

> No Redis server runs here, so the artifacts are correct-by-construction Spring config/code
> + CLI patterns, written to real-world standards.

## What it wires up

```
        HTTP request
             │  (rate limiter: INCR rate:<user> + TTL — reject over limit)
             ▼
    ┌──────────────────┐   @Cacheable(expenses)   ┌─────────────┐
    │  ExpenseService  │ ───────HIT──────────────▶ │    Redis    │
    │  (Spring @Cache) │ ◀──MISS: load from DB─────│  (cache +   │
    └────────┬─────────┘   @CacheEvict on write    │  sessions)  │
             │  source of truth                    └─────────────┘
             ▼
        ┌───────────┐
        │ Postgres  │   (Databases track 04 — the durable store)
        └───────────┘
   Sessions: Spring Session → Redis, so any app replica serves any user (stateless tier)
```

## Every phase, applied

| Phase | Concept | Where |
|---|---|---|
| 1 | Redis as the fast layer over the DB | Postgres = truth, Redis = cache/sessions |
| 2 | Cache-aside + TTL + invalidate-on-write | `@Cacheable` reads, `@CacheEvict`/`@CachePut` writes, `time-to-live` in `application.yml` |
| 3 | Spring Cache abstraction + config | `CachingExample.java`, `application.yml` (pool, TTL, key-prefix, null handling) |
| 4 | Sessions + rate limiting + locks | `application.yml` (Spring Session), `DirectRedisExample.java` (INCR limiter, SET-NX lock) |

## Run it (in the real Spring app + Redis)

```bash
# 1) Start Redis (from the Docker track's compose stack)
docker compose -f ../12-docker/examples/docker-compose.yml up redis -d
# 2) Add the starters + these config/classes to 02-spring-boot/expense-api, then:
cd ../02-spring-boot/expense-api && ./mvnw spring-boot:run
# 3) Observe cache behavior
redis-cli KEYS 'expense:*'      # cached entries appear after first read
redis-cli MONITOR               # watch GET/SET as requests flow
```

## The full-stack picture

This capstone completes the "make it fast and scalable" layer:

- **Reads** hit Redis first (cache-aside), sparing Postgres and cutting latency to
  microseconds — the DB only sees misses and writes.
- **Writes** update Postgres (source of truth) and invalidate/refresh the cache.
- **Sessions** live in Redis, so the app tier is **stateless** and scales horizontally
  behind the Kubernetes (14) Deployment/HPA.
- **Rate limiting** protects the API — the System Design (17) pattern, implemented.

## The one-sentence takeaway

Redis as middleware makes a Postgres-backed app **fast** (cache-aside with TTL + invalidation)
and **scalable** (shared sessions + coordination), turning the durable-but-slow database into
a snappy, horizontally-scalable service — with correctness guarded by TTLs, invalidation, and
atomic operations.
