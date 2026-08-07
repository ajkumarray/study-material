<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · resource leaks](../phase-7-resource-leaks/NOTES.md) | [Phase 9 · messaging ➡](../phase-9-messaging/NOTES.md)
<!-- /nav -->

# Phase 8 — Performance Optimization: Interview Q&A

⭐ = asked constantly.

**Q: A page/endpoint is slow. Walk me through diagnosing and fixing it.** ⭐⭐
Measure first: profile / trace to find where time goes; check the DB slow-query log and `EXPLAIN ANALYZE`. Usually it's the database — look for N+1 queries, missing indexes, and huge result sets. Then app-level: add caching, move slow work async, batch calls. Then delivery: CDN, compression, caching headers. Optimize p95/p99, not just the average. Scale out only after a single node is efficient.

**Q: What is the N+1 query problem and how do you fix it?** ⭐⭐
Loading a list (1 query) then a related entity per row (N queries) → 1+N round trips, often invisible until the SQL log explodes. Fix with a JOIN, an `IN (...)` batch, or JPA `JOIN FETCH`/entity graph. Huge win because each query is a network round trip. (Demo: 11 queries → 2, ~5× faster.)

**Q: How does an index speed up queries, and what's the cost?** ⭐
A B-tree index turns O(n) scans into O(log n) lookups for the columns you filter/join/sort on. Cost: slower writes (index maintenance) and disk. Verify usage with `EXPLAIN` (a seq scan on a big filtered table signals a missing index). Index selectively.

**Q: `LIMIT/OFFSET` vs keyset (cursor) pagination?** ⭐
OFFSET must scan and discard all skipped rows, so deep pages get slow (O(offset)). Keyset pagination uses `WHERE id > last_seen ORDER BY id LIMIT n` — O(page size) regardless of depth, using the index. Prefer keyset for large/infinite scroll.

**Q: When and where do you add caching?** ⭐
Cache hot, expensive, read-mostly data that tolerates slight staleness: query results, computed aggregates, rendered fragments, sessions. Layers: in-process (fast, per-node), distributed (Redis, shared across nodes), CDN/HTTP (at the edge). The hard part is invalidation and stampede (System Design Phase 2). The best query is the one you never run.

**Q: What work should move off the request path?**
Anything slow and not needed for the response: sending email, generating thumbnails, analytics, syncing to third parties. Enqueue it and let a worker process it asynchronously (System Design Phase 9) — the user gets a fast response; the work happens reliably in the background.

**Q: What are the biggest front-end performance levers?** ⭐
CDN/edge caching for static assets; bundling/minifying + code-splitting + lazy-loading JS/CSS; HTTP caching headers (ETag/Cache-Control); image optimization; HTTP/2 or 3. Measure with Core Web Vitals (LCP, INP, CLS).

**Q: Vertical vs horizontal scaling — and which first?** ⭐
Vertical = a bigger machine (simple, but a ceiling and a single point of failure). Horizontal = more machines behind a load balancer (near-limitless, needs statelessness). But **make one node efficient first** — horizontal scaling multiplies an inefficient node's cost. Fix N+1 and add a cache before adding servers.

**Q: Why optimize p99 latency, not just the average?**
Averages hide tail latency; at scale a request often fans out to many services, so the slowest dependency dominates user-perceived latency (a 1%-slow backend can affect most page loads). Tail latency is what users actually feel — SLOs target p95/p99.

**Q: How do you make a read-heavy system scale?**
Caching (Redis/CDN), read replicas (route reads to followers), denormalization/materialized views for expensive aggregates, and pagination. For write-heavy: sharding/partitioning, batching, and async processing (System Design Phases 2–3, 9).
