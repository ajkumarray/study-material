<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · resource leaks](../phase-7-resource-leaks/NOTES.md) | [Phase 9 · messaging ➡](../phase-9-messaging/NOTES.md)
<!-- /nav -->

# Phase 8 — Performance Optimization: Interview Q&A

⭐ = asked constantly.

**Q: A page or endpoint is slow. Walk me through how you'd actually diagnose and fix
it.** ⭐⭐
Measure first, never guess: profile the request or check distributed traces to see where
time is actually going, and check the database's slow-query log plus `EXPLAIN ANALYZE`
on the queries involved. For most web applications, the database dominates total request
time, so that's where I'd look first — specifically for N+1 queries and missing indexes,
and for endpoints returning unbounded result sets that should be paginated. If the
database checks out, move to the application layer: is there repeated expensive work
that should be cached, is there slow non-critical work (an email send, a webhook call)
happening synchronously on the request path that should be offloaded to a queue instead.
Finally, look at delivery: CDN caching, response compression, HTTP caching headers.
Throughout, I'd be optimizing p95/p99 latency specifically, not the average, since tail
latency is what determines whether the worst-treated fraction of users has an acceptable
experience. And only after a single request path is genuinely efficient would I consider
scaling out more servers — horizontal scaling just multiplies whatever inefficiency
already exists.

**Q: What is the N+1 query problem, and how do you fix it?** ⭐⭐
Loading a list of parent records costs one query; then loading each parent's related
child record individually, inside a loop, costs N more queries — 1+N total database
round trips where a small, fixed number should suffice. It's dangerous specifically
because it's invisible in code review (the loop looks perfectly reasonable) and
invisible with a small test dataset, but scales linearly and becomes a severe bottleneck
at real production data volumes — and it happens implicitly with ORMs like Hibernate/JPA
whenever a lazy-loaded association is accessed per-entity in a loop, unless you
explicitly request a batched fetch. Fix it with a `JOIN`, an `IN (...)` clause covering
all parent ids at once, or JPA's `JOIN FETCH`/entity graphs. In the demo, fetching 10
orders and each order's customer individually costs 11 simulated round trips versus 2
for the batched version — roughly a 5x speedup at that tiny scale, and the ratio only
grows as result size grows.

**Q: How does an index actually speed up a query, and what's the cost of adding one?** ⭐
A B-tree index turns what would otherwise be an O(n) full table scan into an O(log n)
lookup for the columns it covers, used for filtering (`WHERE`), joining, and sorting
(`ORDER BY`). The cost is on the write side: every `INSERT`/`UPDATE`/`DELETE` on that
table must also update every index defined on it, so indexes slow down writes and
consume additional disk space — which is why you index the specific columns your actual
query patterns need, not every column that's ever filtered on. Verify an index is
actually being used with `EXPLAIN` — seeing a sequential scan on a large, filtered table
is the direct signal that a needed index is missing.

**Q: `LIMIT/OFFSET` vs. keyset (cursor) pagination — what's the difference and when does
it matter?** ⭐
`LIMIT/OFFSET` is the simplest form, but the database still has to scan and discard
every row before the offset — `OFFSET 100000 LIMIT 50` reads through 100,000 rows before
it can return anything, so deep pages get progressively slower as the offset grows
(effectively O(offset)). Keyset pagination instead filters on the last-seen key —
`WHERE id > last_seen_id ORDER BY id LIMIT n` — which uses the index directly and stays
roughly constant time (O(page size)) regardless of how deep into the result set you are.
The trade-off is that keyset pagination doesn't naturally support jumping to an
arbitrary page number (page 500 directly) the way offset-based pagination does — it's
built for sequential "next page" navigation, like infinite scroll, which is exactly the
UI pattern where the performance difference matters most anyway.

**Q: When and where do you add caching, and why does it matter so much?** ⭐
Cache data that's hot (frequently read), expensive to produce (a complex query, a
computed aggregate, a rendered fragment), and tolerant of some staleness: query results,
computed aggregates, sessions, rendered page fragments. Cache at whatever layer fits the
scope needed — in-process for per-instance speed, a distributed cache like Redis when it
needs to be shared and consistent across the whole fleet, CDN/HTTP caching at the edge
for static or broadly cacheable content. In the demo, computing the same "expensive"
value 20 times uncached takes roughly 200ms total; wrapping the same 20 calls in
`computeIfAbsent` so only the first call actually computes it drops that to roughly the
cost of a single computation — about a 20x reduction, and that ratio only grows with more
repeated calls. The genuinely hard parts, covered in Phase 2, are invalidation (keeping
the cached copy correct) and stampede protection (avoiding many concurrent misses
hammering the source at once) — caching is easy to add and easy to get subtly wrong.

**Q: What kind of work should be moved off the request path entirely?**
Anything slow and not actually required to produce the response the user is waiting on:
sending a confirmation email, generating a thumbnail, logging to an analytics pipeline,
syncing data to a third-party service. Enqueue that work and let a background worker
process it asynchronously (Phase 9) — the user gets a fast response immediately, and the
slower work still happens reliably, just not blocking them. The judgment call is
distinguishing genuinely non-critical work (safe to defer) from work the response
actually depends on (which has to stay synchronous, or the endpoint would return before
the operation it's reporting on has actually happened).

**Q: What are the biggest front-end/delivery performance levers, and how do you measure
success?** ⭐
CDN and edge caching for static assets and cacheable responses — usually the single
biggest win because it directly eliminates network round trips for geographically
distributed users, the same latency-ratio lesson (memory ≪ SSD ≪ network) from Phase 1
applied to the browser tier. Bundling/minifying/tree-shaking plus code-splitting and
lazy-loading so initial page load only pays for what's immediately needed. Correct HTTP
caching headers (`ETag`/`Cache-Control`) so repeat visits reuse already-downloaded
assets. Image optimization and deferring non-critical JS/CSS so they don't block initial
rendering. Measure success with Core Web Vitals — LCP (how fast the main content
becomes visible), INP (how responsive the page is to interaction), CLS (how much content
unexpectedly shifts as the page loads) — because these are the standardized,
user-perceived metrics, as opposed to internal numbers that may not reflect what a real
user actually experiences.

**Q: Vertical vs. horizontal scaling — and why should you make a single node efficient
first?** ⭐
Vertical scaling means a bigger machine — simple, no code changes, but a hard ceiling
and still a single point of failure. Horizontal scaling means more machines behind a
load balancer — near-limitless, but it requires statelessness (Phase 5.3) and adds
distributed-systems complexity. The reason to fix inefficiency first, before scaling out
either way, is that horizontal scaling specifically *multiplies* whatever
inefficiency already exists per node — ten servers each wastefully running N+1 queries
are still running N+1 queries, just costing ten times as much to do it. Fixing the N+1
query or adding the missing index before adding servers is almost always both cheaper
and more effective than adding capacity to work around an inefficiency.

**Q: Why optimize for p99 latency specifically, instead of the average?**
Averages hide tail behavior — if 1% of requests are dramatically slower than the rest,
the mean can still look acceptable while a meaningful number of real users have a
genuinely bad experience. This effect compounds in any system where a single user-facing
request fans out to multiple internal service calls: if each of five downstream calls
has a 1% chance of being slow, the odds that *at least one* of them is slow on any given
request is close to 5%, not 1% — so tail latency in a multi-service architecture is
worse than any single service's own tail latency would suggest. SLOs are defined on
p95/p99 specifically because that's what determines the experience of the
worst-treated fraction of users, which at real traffic volumes is still a very large
absolute number of people.

**Q: How do you make a read-heavy system scale, versus a write-heavy one?**
Read-heavy: caching (Redis and/or CDN, Phase 2) absorbs the bulk of read traffic before
it reaches the database at all, read replicas (Phase 3.1) scale out remaining database
read capacity horizontally, and denormalization/materialized views turn expensive live
aggregates into cheap precomputed reads. Write-heavy: sharding/partitioning (Phase 3.3)
splits write load and storage across multiple nodes, batching amortizes per-write
overhead across many records at once, and async processing (Phase 9) moves
non-essential write-adjacent work off the synchronous write path so the write itself
stays fast. The two problems call for genuinely different toolkits, which is why
diagnosing which one you actually have — via the read:write ratio from Phase 1.6's
estimation — comes before reaching for either.
