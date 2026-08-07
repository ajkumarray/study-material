<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 17 — System Design & Backend Engineering

The practical engineering competency that ties every other track together:
how to build systems that are **scalable, reliable, fast, and correct under
concurrency** — and how to reason about the trade-offs in a design interview.

This is deliberately the **capstone track**: it synthesizes databases (04),
Spring Boot (02), Redis/Kafka (14–15), and the software-design principles (05).
You design systems *using* the pieces the other tracks taught you to build.

Format: theory-first (this is a reasoning discipline more than a coding one),
with concrete Java/SQL/config snippets and diagrams where they help, plus the
classic design-interview problems worked end to end. `NOTES.md` per phase +
`INTERVIEW.md` (the "design X" questions). ⭐ = commonly asked.

> Directly answers questions like: *how do I prevent duplicate creates/updates?*
> (Phase 4), *what causes "memory leaks" in a database / connection exhaustion?*
> (Phase 7), *how do I optimize a slow website/server?* (Phase 8).

## Curriculum

### Phase 1 — Fundamentals & vocabulary ✅
- [x] 1.1 Scalability (vertical vs horizontal); latency vs throughput
- [x] 1.2 Availability, reliability, durability; SLA/SLO/SLI
- [x] 1.3 The CAP theorem; consistency models (strong vs eventual)
- [x] 1.4 Back-of-the-envelope estimation (QPS, storage, bandwidth)

### Phase 2 — Caching ✅
- [x] 2.1 Why/where to cache; cache-aside, write-through, write-back
- [x] 2.2 Eviction (LRU/LFU/TTL); the two hard problems — invalidation & stampede
- [x] 2.3 CDN & edge caching; client/HTTP caching; Redis as a cache (→ track 14)

### Phase 3 — Databases at scale ✅
- [x] 3.1 Replication (leader/follower); read replicas; replication lag
- [x] 3.2 Partitioning & sharding; hot keys; consistent hashing
- [x] 3.3 Indexing & denormalization for reads; SQL vs NoSQL choice (→ track 04)

### Phase 4 — Concurrency, consistency & idempotency ✅ ⭐
- [x] 4.1 Race conditions; **preventing duplicate creates** (unique constraints, idempotency keys)
- [x] 4.2 **Preventing lost/duplicate updates** (optimistic `@Version` vs pessimistic locking)
- [x] 4.3 Upsert; exactly-once vs at-least-once; idempotent APIs & retries
- [x] 4.4 Distributed transactions; the saga pattern; the outbox pattern
      *(`IdempotencyDemo.java` reproduces both races + all fixes; NOTES + INTERVIEW)*

### Phase 5 — Scaling the web tier ✅
- [x] 5.1 Load balancing (algorithms, L4 vs L7); reverse proxies
- [x] 5.2 Stateless services & session strategies; sticky sessions
- [x] 5.3 Horizontal autoscaling; rate limiting & throttling (token/leaky bucket)
- [x] 5.4 API gateways; the API design & versioning surface

### Phase 6 — Reliability & resilience ✅
- [x] 6.1 Timeouts, retries (with backoff + jitter), idempotent retries
- [x] 6.2 Circuit breakers, bulkheads, graceful degradation, fallbacks
- [x] 6.3 Dead-letter queues; redundancy, failover, health checks
- [x] 6.4 Single points of failure; blast radius; chaos thinking

### Phase 7 — Resource management & "leaks" ✅ ⭐
- [x] 7.1 **Connection-pool exhaustion** (the #1 "DB memory leak" in practice); leaked connections/cursors
- [x] 7.2 Memory leaks (app: unbounded caches/collections, listeners, ThreadLocals; DB: plan cache, bloat)
- [x] 7.3 Thread/file-handle leaks; back-pressure; bounded queues & pools
- [x] 7.4 DB maintenance: VACUUM/bloat, long-running txns, lock contention
      *(`ResourceLeaksDemo.java` reproduces pool exhaustion + a cache leak, then fixes both)*

### Phase 8 — Performance optimization ✅ ⭐
- [x] 8.1 Measure first: profiling, the N+1 problem, slow-query analysis (`EXPLAIN`)
- [x] 8.2 DB: indexing, query tuning, connection pooling, read replicas, pagination
- [x] 8.3 App/server: async processing, batching, compression, keep-alive, HTTP/2
- [x] 8.4 Front-end/web: CDN, asset bundling/minify, lazy loading, caching headers, Core Web Vitals
      *(`PerformanceDemo.java` measures N+1, caching, pagination wins)*

### Phase 9 — Messaging & async architecture ✅
- [x] 9.1 Queues vs pub/sub; when to go async; event-driven architecture
- [x] 9.2 Delivery guarantees, ordering, backpressure; Kafka concepts (→ track 15)

### Phase 10 — Observability & operations ✅
- [x] 10.1 The three pillars: logs, metrics, traces; structured logging
- [x] 10.2 Alerting, dashboards, SLOs & error budgets; on-call basics

### Phase 11 — Classic design-interview problems ✅
- [x] 11.1 URL shortener; rate limiter; unique-ID generator
- [x] 11.2 News feed / timeline; chat/messaging; notification system
- [x] 11.3 Key-value store; a design framework (requirements → estimate → API → data → scale → trade-offs)

## Relationship to other tracks
This track is the *synthesis* layer — it references rather than repeats:
transactions/isolation & indexing (04 Databases), DI & the REST API (02 Spring
Boot), caching (14 Redis), event streaming (15 Kafka), SOLID/patterns (05).
Best studied once those foundations exist — which is why it's numbered last.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · fundamentals** — [Notes](phase-1-fundamentals/NOTES.md) · [Interview](phase-1-fundamentals/INTERVIEW.md)
- **Phase 2 · caching** — [Notes](phase-2-caching/NOTES.md) · [Interview](phase-2-caching/INTERVIEW.md)
- **Phase 3 · databases scale** — [Notes](phase-3-databases-scale/NOTES.md) · [Interview](phase-3-databases-scale/INTERVIEW.md)
- **Phase 4 · idempotency** — [Notes](phase-4-idempotency/NOTES.md) · [Interview](phase-4-idempotency/INTERVIEW.md)
- **Phase 5 · web tier** — [Notes](phase-5-web-tier/NOTES.md) · [Interview](phase-5-web-tier/INTERVIEW.md)
- **Phase 6 · reliability** — [Notes](phase-6-reliability/NOTES.md) · [Interview](phase-6-reliability/INTERVIEW.md)
- **Phase 7 · resource leaks** — [Notes](phase-7-resource-leaks/NOTES.md) · [Interview](phase-7-resource-leaks/INTERVIEW.md)
- **Phase 8 · performance** — [Notes](phase-8-performance/NOTES.md) · [Interview](phase-8-performance/INTERVIEW.md)
- **Phase 9 · messaging** — [Notes](phase-9-messaging/NOTES.md) · [Interview](phase-9-messaging/INTERVIEW.md)
- **Phase 10 · observability** — [Notes](phase-10-observability/NOTES.md) · [Interview](phase-10-observability/INTERVIEW.md)
- **Phase 11 · design problems** — [Notes](phase-11-design-problems/NOTES.md) · [Interview](phase-11-design-problems/INTERVIEW.md)
<!-- /phases-nav -->
