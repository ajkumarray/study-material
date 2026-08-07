<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · resource leaks](../phase-7-resource-leaks/NOTES.md) | [Phase 9 · messaging ➡](../phase-9-messaging/NOTES.md)
<!-- /nav -->

# Phase 8 — Performance Optimization: Notes

## 8.1 — Measure first
**The golden rule: measure, don't guess.** Optimize the actual bottleneck — profiling repeatedly shows the slow part isn't where you'd assume. Tools: a profiler (async-profiler, JFR) for CPU/alloc hot spots, APM/tracing (latency per span), the DB's slow-query log + **`EXPLAIN ANALYZE`** (Databases Phase 4) for query plans, and load tests (k6/JMeter) for throughput/tail latency. Optimize **p95/p99 tail latency**, not just the average — tails dominate user experience. Premature optimization of the wrong thing is wasted effort (and risk).

**The order of leverage** (biggest wins first): usually **I/O and the database** dominate a web request's time, then network round trips, then app CPU. So look there first.

## 8.2 — Database (usually the biggest win)
- **N+1 queries** — the #1 hidden killer (Java Phase 7.3, Spring 4). Fetching a list (1 query) then a related row per item (N queries) = 1+N round trips. Fix with a JOIN / `IN (...)` batch / `JOIN FETCH` / entity graph. (Demo: 5× faster going from 11 queries to 2.)
- **Indexing** — turn O(n) scans into O(log n) lookups on filtered/joined/sorted columns; verify with `EXPLAIN` that the index is used (watch for seq scans on big filtered tables). Balance: indexes slow writes (Databases Phase 4).
- **Connection pooling** — reuse connections (HikariCP) instead of reconnecting per request (Java Phase 7.2).
- **Read replicas** — route reads to followers to scale read-heavy load (System Design Phase 3); mind replication lag.
- **Pagination** — never load a huge result set into memory; `LIMIT/OFFSET` or keyset (cursor) pagination. (Demo: one 50-row page vs a million rows.) Keyset scales better than large OFFSETs.
- **Denormalization / materialized views / caching** for expensive aggregate reads.

## 8.3 — Application / server
- **Caching** — skip repeated expensive work (compute/query once, serve from memory; Redis for a shared cache — System Design Phase 2). (Demo: 20× on a hot value.) The best query is the one you don't run.
- **Async / offload** — move slow, non-critical work (emails, thumbnails, analytics) off the request path onto a queue/worker (System Design Phase 9). Return fast; process later.
- **Batching** — group N operations into one (bulk insert, batched API calls) to amortize per-call overhead.
- **Concurrency** — thread-per-request with virtual threads (Java Phase 5.5 / Spring 8) or a reactive/async stack to handle blocking I/O at scale.
- **Compression** (gzip/brotli responses), **connection keep-alive**, **HTTP/2** (multiplexing) to cut network overhead.
- **Efficient serialization** and avoiding needless object allocation in hot paths.

## 8.4 — Front-end / web delivery
- **CDN + edge caching** — serve static assets (and cacheable responses) from a location near the user; the single biggest web latency win.
- **Bundle / minify / tree-shake** JS/CSS (JS Phase 6.1); **code-split** and **lazy-load** below-the-fold and route-level chunks.
- **HTTP caching headers** (`Cache-Control`, `ETag`) so browsers/CDNs reuse assets.
- **Image optimization** (right size, modern formats, lazy loading), **critical CSS**, defer non-critical JS.
- **Core Web Vitals** — LCP (load), INP (interactivity), CLS (visual stability) — the user-perceived metrics to optimize.

## The mental checklist
1. **Measure** — find the real bottleneck (profile, `EXPLAIN`, trace, load test).
2. **Database** — kill N+1, add indexes, pool, paginate, replicate.
3. **App** — cache, go async, batch, compress.
4. **Web** — CDN, bundle, lazy-load, cache headers.
5. **Scale out** only after making a single node efficient — horizontal scaling multiplies an inefficient node's cost (System Design Phase 5). Efficiency first, then scale.
