<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · resource leaks](../phase-7-resource-leaks/NOTES.md) | [Phase 9 · messaging ➡](../phase-9-messaging/NOTES.md)
<!-- /nav -->

# Phase 8 — Performance Optimization: Notes

The golden rule of this entire phase: **measure, don't guess.** Profiling a real system
repeatedly reveals that the slow part isn't where intuition says it would be. This phase
walks the order of leverage — database, then application, then front-end delivery — and
`PerformanceDemo.java` measures three of the highest-leverage backend wins directly:
N+1 queries, caching, and pagination.

## 8.1 — Measure first

### Key Concepts

- **Never optimize a guess.** Optimizing code that "feels slow" but isn't the actual
  bottleneck is wasted effort at best, and adds risk (new bugs) for zero benefit at
  worst. Find the real bottleneck first.
- **Tools**: a CPU/allocation profiler (async-profiler, Java Flight Recorder/JFR) for
  application hot spots; APM/distributed tracing (Phase 10.1) for per-span latency
  across a request's full call chain; the database's slow-query log plus **`EXPLAIN
  ANALYZE`** (Databases Phase 4) for actual query execution plans, not assumed ones; and
  load-testing tools (k6, JMeter) to measure throughput and how tail latency behaves
  under realistic concurrent load, not just single-request latency.
- **Optimize p95/p99 tail latency, not just the average** (Phase 1.2) — the mean can
  look fine while a meaningful fraction of real users have a genuinely bad experience;
  SLOs are defined on tail percentiles for exactly this reason.
- **The order of leverage** — for a typical web request, **I/O and the database usually
  dominate** total time by a wide margin, followed by network round trips, followed by
  application CPU time. This ordering is *why* 8.2 (database) comes before 8.3
  (application) below — look at the database first, because that's where the time
  usually actually is.

### Why it's useful

"Measure first" isn't a platitude — it's a direct consequence of Phase 1.6's latency
table: memory access, SSD access, and network round trips differ by many orders of
magnitude, so *where* a request spends its time is almost never evenly distributed, and
guessing which layer is slow is a coin flip you can simply avoid by profiling instead.

## 8.2 — Database performance (usually the biggest win)

### Key Concepts

- **The N+1 query problem** — the single most common hidden performance killer (Java
  Phase 7.3, Spring Boot Phase 4). Fetching a list of parent entities costs 1 query; then
  fetching each parent's related child entity **individually, in a loop**, costs N more
  queries — 1+N total round trips for what should be a small, fixed number of queries.
  This is invisible in code review (the loop looks innocent) and invisible with a small
  test dataset (11 queries vs. 2 isn't noticeable at N=10), but scales linearly with
  result size and becomes a severe, visible bottleneck at production data volumes. Fix
  by fetching all the related data in **one batched query**: a `JOIN`, an `IN (...)`
  clause across all the parent ids at once, or JPA's `JOIN FETCH` / entity graphs.
- **Indexing** — a B-tree index turns an O(n) full table scan into an O(log n) lookup
  for columns used in `WHERE`, `JOIN`, and `ORDER BY` clauses. Verify an index is
  actually being used with `EXPLAIN` — a sequential scan on a large, filtered table is
  the signature of a missing index. The cost: every index slows down writes (each
  insert/update/delete must also update every index on that table) and consumes disk, so
  index selectively for the queries that actually matter, not every column that's ever
  filtered on (Databases Phase 4).
- **Connection pooling** — reuse a small, bounded set of database connections (HikariCP,
  Java Phase 7.2) instead of opening a fresh TCP connection and authenticating per
  request, which is comparatively expensive.
- **Read replicas** — route read traffic to follower nodes to scale read capacity
  horizontally (Phase 3.1), while keeping in mind replication lag can make a read
  immediately following a write appear stale.
- **Pagination** — never load an entire large result set into memory or send it over the
  wire in one response. `LIMIT/OFFSET` is the simplest form but has to scan and discard
  every skipped row, making deep pages progressively slower (`OFFSET 100000` still reads
  through 100,000 rows before returning anything); **keyset (cursor) pagination**
  (`WHERE id > last_seen_id ORDER BY id LIMIT n`) uses the index directly and stays
  roughly constant-time regardless of how deep into the result set you are, at the cost
  of not supporting arbitrary "jump to page 500" navigation.
- **Denormalization, materialized views, and caching** for reads that are expensive to
  compute live (a multi-table aggregate, a leaderboard) — trade write cost and storage
  for read speed, appropriate specifically for read-heavy workloads.

### Worked example — `PerformanceDemo.nPlusOne()`

```java
static class Db {
    List<Integer> findOrderIds() { query(); return List.of(1,2,3,4,5,6,7,8,9,10); }
    String findCustomerForOrder(int orderId) { query(); return "cust-" + (orderId % 3); }
    Map<Integer, String> findCustomersForOrders(List<Integer> ids) {   // ONE batched query
        query();
        Map<Integer, String> m = new HashMap<>();
        for (int id : ids) m.put(id, "cust-" + (id % 3));
        return m;
    }
}

// N+1: 1 query for orders, then a query PER order for its customer
List<Integer> orders = db.findOrderIds();                 // 1 query
for (int id : orders) db.findCustomerForOrder(id);         // + N queries  <-- N+1

// Fixed: 1 query for orders, 1 BATCHED query for every customer at once
List<Integer> orders2 = db.findOrderIds();                // 1 query
db.findCustomersForOrders(orders2);                        // + 1 batched query
```

With each simulated `query()` costing `QUERY_MS = 5` ms, the N+1 version issues 1 + 10 =
11 total round trips (55ms), while the batched version issues exactly 2 (10ms) — roughly
a 5x speedup on this small, deliberately tiny example (`orders.size() == 10`); at real
production scale, where a list endpoint might return hundreds of rows, the ratio between
"1 + N" and "2" round trips grows dramatically, and each round trip in a real system
costs far more than 5ms once network latency (Phase 1.6) is included rather than a local
in-process simulation. This is exactly why N+1 is dangerous specifically in an ORM
context (Spring Data JPA, Hibernate) — lazy-loaded associations make the N extra
queries happen implicitly, one per entity, unless you explicitly ask for a batched fetch.

### Worked example — `PerformanceDemo.pagination()`

```java
int total = 1_000_000, pageSize = 50;

int[] all = new int[total];                               // load EVERYTHING
for (int i = 0; i < total; i++) all[i] = i;
// fetch all 1000000 rows : ... ms, 3906 KB in memory

int[] page = new int[pageSize];                            // load ONE page
for (int i = 0; i < pageSize; i++) page[i] = i;
// fetch 1 page (50)      : ... ms, 0 KB   (LIMIT/OFFSET or keyset)
```

Loading all 1,000,000 rows allocates and populates the full array (roughly 3.9MB just for
the `int[]`, before any per-row object/row overhead a real ORM would add); loading a
single 50-row page allocates a negligible fraction of that. The lesson generalizes
directly to `SELECT * FROM orders` with no `LIMIT` versus a paginated query: an endpoint
that returns "everything" scales its memory and network payload linearly with table
size, silently getting slower and heavier every day the table grows, while a paginated
endpoint's cost stays roughly constant regardless of how large the underlying table gets.

### Why it's useful

The database dominates request time for most web applications (8.1's "order of
leverage"), so fixing an N+1 query or adding a missing index is very often a bigger win
than any amount of application-level micro-optimization — and it's usually also the
*cheapest* fix, requiring a query change rather than an architecture change.

## 8.3 — Application-level performance

### Key Concepts

- **Caching** — skip repeated expensive work entirely by computing or querying once and
  serving subsequent requests from memory; use a distributed cache (Redis) for anything
  that must be shared across app server instances (Phase 2). "The best query is the one
  you never run" — this is the single highest-leverage lever in the whole phase, which
  is why Phase 2 is its own dedicated phase.
- **Async / offload** — move work that's slow and not required for the immediate
  response (sending an email, generating a thumbnail, logging to analytics) off the
  request path entirely, onto a queue processed by a background worker (Phase 9). The
  user gets a fast response; the slow work still happens, just not synchronously blocking
  them.
- **Batching** — group many small operations into one larger one (a bulk insert instead
  of N individual inserts, a single batched external API call instead of N separate
  calls) to amortize fixed per-call overhead (network round trip, transaction commit)
  across many items at once.
- **Concurrency** — use a concurrency model suited to I/O-bound work at scale: a
  thread-per-request model with virtual threads (Java Phase 5.5 / Spring Boot Phase 8)
  or a reactive/async stack, either of which lets a service handle many more concurrent
  blocking I/O operations than the number of OS threads would otherwise allow.
- **Network-level wins**: response compression (gzip/brotli) reduces payload size at the
  cost of CPU to compress/decompress; connection keep-alive avoids repeated TCP/TLS
  handshake overhead per request; HTTP/2's multiplexing lets many logical requests share
  one connection, cutting the overhead of many parallel small requests.
- **Efficient serialization and avoiding unnecessary allocation** in genuinely hot code
  paths (a request handler executed millions of times a day) — the kind of
  micro-optimization that only matters once the bigger levers above have already been
  applied.

### Worked example — `PerformanceDemo.caching()`

```java
Map<Integer, Long> cache = new ConcurrentHashMap<>();

// uncached: recompute the "expensive" value every single call
for (int i = 0; i < 20; i++) expensive(7);                 // 20 * 10ms = ~200ms

// cached: compute once, serve the rest from memory
for (int i = 0; i < 20; i++) cache.computeIfAbsent(7, PerformanceDemo::expensive);
// uncached (20 calls): ~200 ms
// cached   (20 calls): ~10 ms   (compute once, serve the rest)
```

With `expensive()` simulating 10ms of real work per call, calling it 20 times without a
cache costs roughly 200ms total; wrapping the exact same 20 calls in
`cache.computeIfAbsent` means only the *first* call for key `7` actually invokes
`expensive()` — the remaining 19 hit the populated map directly, and total time drops to
roughly the cost of a single computation, a roughly 20x reduction for this access
pattern. This is the same `computeIfAbsent` atomicity from Phase 2.5's stampede-fix
demo, applied here purely for the speed-up rather than the concurrency-correctness
angle.

### Why it's useful

Application-level wins are generally cheaper to implement than database schema/index
changes and don't require any infrastructure change, which makes them a good second
pass after the database-level fixes in 8.2 — but they only pay off proportionally to how
much of the total request time the application layer actually accounts for, which
8.1's "measure first" is what tells you.

## 8.4 — Front-end and web-delivery performance

### Key Concepts

- **CDN + edge caching** — serve static assets (and cacheable API responses) from a
  point of presence physically close to the user (Phase 2.7). Because network latency
  dominates page-load time for geographically distributed users (Phase 1.6's
  cross-continent round trip is ~150ms vs. an in-datacenter ~0.5ms), this is typically
  the single biggest web-latency win available, and it's essentially free once
  configured correctly.
- **Bundling, minification, and tree-shaking** (JS track Phase 6.1) reduce the total
  bytes shipped to the browser; **code-splitting and lazy-loading** defer loading
  JS/CSS for below-the-fold content or routes the user hasn't navigated to yet, so
  initial page load only pays for what's immediately needed.
- **HTTP caching headers** (`Cache-Control`, `ETag` — Phase 2.7) let browsers and CDNs
  reuse previously-downloaded assets instead of re-fetching them on every visit.
- **Image optimization** (correctly sized images, modern formats like WebP/AVIF, lazy
  loading images below the fold), **critical CSS** (inlining the minimal CSS needed for
  the initial visible viewport so the page doesn't wait on a full stylesheet download to
  render anything), and **deferring non-critical JS** so it doesn't block initial
  rendering.
- **Core Web Vitals** — the standardized, user-perceived metrics to actually optimize
  toward: **LCP** (Largest Contentful Paint — how long until the main content is
  visible), **INP** (Interaction to Next Paint — how responsive the page is to user
  input), and **CLS** (Cumulative Layout Shift — how much visible content unexpectedly
  moves around as the page loads). These are what search engines and real-user-
  monitoring tools actually measure, as opposed to internal, less user-relevant metrics.

### Why it's useful

Front-end performance work is disproportionately about eliminating and shortening
network round trips (CDN placement, fewer/smaller requests via bundling and caching)
rather than raw CPU optimization — a direct continuation of Phase 1.6's core lesson that
network hops dominate latency by orders of magnitude over almost anything else.

## The mental checklist

1. **Measure** — profile, run `EXPLAIN ANALYZE`, trace, load test. Find the real
   bottleneck before touching any code.
2. **Database** — kill N+1 queries, add missing indexes, use connection pooling,
   paginate large result sets, add read replicas for read-heavy load.
3. **Application** — cache aggressively (Phase 2), move non-critical work async (Phase
   9), batch small operations, compress and keep connections alive.
4. **Web delivery** — CDN, bundle/minify/code-split, set correct HTTP caching headers,
   lazy-load and optimize images.
5. **Scale out only after making a single node efficient** (Phase 1.1, Phase 5.4) —
   horizontal scaling multiplies whatever inefficiency already exists per node; ten
   inefficient servers are still inefficient, just more expensive to run.

## Summary / Key Takeaways

- **Measure first** — profile, use `EXPLAIN ANALYZE`, trace, and load test to find the
  actual bottleneck; optimize **p95/p99 tail latency**, not the average, since that's
  what real users at the unlucky end of the distribution actually experience.
- **I/O and the database usually dominate** total request time, which is why database
  fixes (killing **N+1 queries** — demo: 11 queries → 2, ~5x faster; adding indexes;
  **keyset pagination** over deep `OFFSET`) are typically the highest-leverage
  optimization available.
- **Caching** (demo: 20 calls uncached ≈ 200ms → cached ≈ 10ms) is the single biggest
  application-level lever — "the best query is the one you never run" — and moving slow,
  non-critical work **async** off the request path is the second biggest.
- **CDN + HTTP caching** for static/cacheable content is usually the biggest single
  web-delivery win, because it directly eliminates network round trips — the same
  latency-ratio lesson from Phase 1.6 applied to the browser tier.
- **Scale out only after a single node is efficient** — horizontal scaling multiplies
  inefficiency rather than fixing it, so exhaust query, cache, and application-level
  optimization before adding more servers.
