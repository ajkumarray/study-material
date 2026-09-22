<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · schema design](../phase-3-schema-design/NOTES.md) | [Phase 5 · transactions ➡](../phase-5-transactions/NOTES.md)
<!-- /nav -->

# Phase 4 — Indexing & Query Performance: Notes (Theory)

Making queries fast is arguably the single highest-leverage database skill — it's
the SQL-level detail behind System Design's caching/scaling material and behind
Java/JPA's N+1 problem. `00-setup.sql` builds a **1,000,000-row `event` table**
(`id, user_id, status, amount, created_at`, `user_id` spread over 10,000 distinct
values, `status` one of 4 values) specifically so `EXPLAIN` shows real, meaningful
differences instead of toy-table noise.

---

## 1. How Indexes Work

An **index** is a separate, sorted data structure — almost always a **B-tree** in
Postgres by default — that maps a column's values to the physical locations of the
rows that hold them, so the database can *jump* directly to matching rows instead of
reading the entire table. This is the classic "book index vs reading every page"
analogy: an indexed lookup is **O(log n)**, a full table scan is **O(n)**.

### Key Concepts

- **Auto-indexing**: a `PRIMARY KEY` is automatically backed by a unique index.
  Nothing else on a table is indexed unless you explicitly create one with
  `CREATE INDEX name ON table (col)`.
- **What a B-tree index helps with**: equality (`=`), ranges (`<`, `>`,
  `BETWEEN`), `ORDER BY` (the tree is already sorted, so sorting/range-scanning
  along it is nearly free), and *prefix* pattern matches (`LIKE 'abc%'`).
- **What a B-tree index does NOT help with**: a leading wildcard
  (`LIKE '%abc'` — the starting character is unknown, so there's no sorted
  position to jump to), a function applied to the column (`WHERE lower(status) =
  'paid'` — the index is built on the raw `status` values, not on
  `lower(status)`; needs an *expression index*), and low-selectivity filters.
- **Selectivity**: the fraction of rows a filter actually matches. This alone
  decides whether the planner will even *use* an index that exists.
- **Index types beyond B-tree**: **Hash** (equality-only, rarely worth it over
  B-tree in Postgres), **GIN** (JSONB containment, arrays, full-text search —
  Phase 6), **GiST**/**SP-GiST** (geometric/range/nearest-neighbor data), **BRIN**
  (tiny index for huge, naturally-ordered, append-only tables like time-series
  logs).

### Worked Examples

```sql
-- Before any index on user_id: this must scan all 1,000,000 rows
SELECT * FROM event WHERE user_id = 42;
-- EXPLAIN shows: Seq Scan on event  (cost estimate proportional to table size)

CREATE INDEX idx_event_user_id ON event (user_id);

-- Same query, after the index exists — jumps straight to user 42's ~100 rows
SELECT * FROM event WHERE user_id = 42;
-- EXPLAIN now shows: Index Scan using idx_event_user_id on event
```
In this example: creating the index doesn't change the query's SQL at all — the
planner decides on its own, based on cost estimates, whether to use the new index.
With 1,000,000 rows spread across 10,000 distinct `user_id` values, filtering to one
`user_id` returns roughly 100 rows (0.01% of the table) — exactly the kind of highly
selective filter an index is built for.

```sql
-- An index on a date column speeds up BOTH range filters and ORDER BY,
-- because the B-tree is already sorted
CREATE INDEX idx_event_created_at ON event (created_at);

SELECT * FROM event WHERE created_at >= now() - interval '7 days';    -- range scan
SELECT * FROM event ORDER BY created_at DESC LIMIT 10;                -- top-N via sorted index
```
Both queries benefit from the same index for the same underlying reason: a B-tree
stores its keys in sorted order, so "give me everything after this point" and "give
me the top N in this order" are both cheap tree walks rather than full scans
followed by a sort.

```sql
-- Selectivity decides whether the planner bothers using an index at all.
-- status has only 4 distinct values (~250,000 rows each, ~25% of the table)
CREATE INDEX idx_event_status ON event (status);
SELECT count(*) FROM event WHERE status = 'paid';
-- likely STILL a Seq Scan, even with the index present
```
The planner isn't being lazy here — it's making the *right* call. Reading a quarter
of the index and then fetching a quarter of the table's rows one by one is genuinely
slower than just reading the table sequentially in physical order. An index on a
low-cardinality column like this frequently goes unused, and unused indexes still
cost every write (section 4) — a strong argument for not creating it in the first
place.

```sql
-- See what indexes exist on a table
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'event';
```

### Comparison Table

| Index type | Best for | Notes |
|---|---|---|
| B-tree (default) | `=`, `<`/`>`/`BETWEEN`, `ORDER BY`, prefix `LIKE` | ~95% of real-world indexing needs |
| Hash | equality only | rarely chosen over B-tree in Postgres |
| GIN | JSONB `@>`, arrays, full-text search | Phase 6 |
| GiST / SP-GiST | geometric data, ranges, nearest-neighbor | specialized workloads |
| BRIN | huge, naturally-ordered append-only tables | tiny index size, coarse-grained |

### Why It's Useful

Nearly every "why is this query slow" investigation in a real production system
starts with "is there an index on the column(s) this query filters/joins/sorts by,
and is it actually selective enough for the planner to use?" Indexing correctly is
often a 100x-or-more speedup for zero application code changes.

### Summary / Key Takeaways

- An index trades some write cost and disk space for dramatically faster reads on
  the columns it covers — it's a deliberate trade-off, not a free win.
- Only the primary key is indexed automatically; every other index is a decision
  you make.
- B-trees serve equality, ranges, sorting, and prefix matches; they can't help a
  leading-wildcard `LIKE` or a raw function applied to the column.
- Selectivity (how few rows a filter matches) decides whether the planner actually
  uses an index that exists — low-cardinality columns often go unused even with an
  index present, and rightly so.

---

## 2. EXPLAIN / EXPLAIN ANALYZE — Reading the Query Plan

The **query planner** decides *how* to physically execute your SQL — which scan
type, which join strategy, which index (if any). `EXPLAIN` shows you that decision;
it's the single most important tool for diagnosing a slow query, because it replaces
guessing with actually seeing what the database intends to do (or, with `ANALYZE`,
actually did).

### Key Concepts

- **`EXPLAIN`**: shows the chosen plan and the planner's *estimates* (row counts,
  cost) — without running the query.
- **`EXPLAIN ANALYZE`**: actually **executes** the query and shows *real* timings
  and row counts alongside the estimates, so you can compare estimate vs. reality.
- Read a plan **bottom-up / inside-out** — the innermost/lowest nodes run first and
  feed their output up to the nodes above them.
- **Scan types**, roughly worst to best for a selective filter:
  - **Seq Scan** — reads every row in the table. Fine for small tables or
    low-selectivity queries; a red flag on a big table with a genuinely selective
    `WHERE` clause (usually means a missing or unused index).
  - **Index Scan** — walks the index, then fetches each matching row from the
    table. Great when few rows match.
  - **Bitmap Heap Scan** — builds an in-memory bitmap of matching row locations
    from the index, then visits the table in physical order. A middle ground, good
    for a moderate number of matches (too many for a plain Index Scan to stay
    efficient, too few to justify a full Seq Scan).
  - **Index Only Scan** — answers the query entirely from the index itself,
    without touching the table at all. The fastest possible read; requires a
    *covering* index (section 3).
- **Join strategies**: **Nested Loop** (good when the outer side has few rows and
  the inner side has a usable index), **Hash Join** (good for large, unsorted
  inputs — builds a hash table of one side), **Merge Join** (good when both inputs
  are already sorted).

### Worked Examples

```sql
EXPLAIN
SELECT * FROM event WHERE user_id = 42;
--                                    QUERY PLAN
-- ---------------------------------------------------------------------------
--  Index Scan using idx_event_user_id on event  (cost=0.42..8.65 rows=97 width=41)
--    Index Cond: (user_id = 42)
```
Key fields: `cost=0.42..8.65` is the estimated startup..total cost (arbitrary
planner units, useful for *comparing* plans, not as real time); `rows=97` is the
estimated row count; `width=41` is the estimated average row size in bytes. No
actual execution happened — these are pure estimates from the planner's statistics.

```sql
EXPLAIN ANALYZE
SELECT * FROM event WHERE user_id = 42;
--                                    QUERY PLAN
-- --------------------------------------------------------------------------------
--  Index Scan using idx_event_user_id on event
--    (cost=0.42..8.65 rows=97 width=41) (actual time=0.024..0.089 rows=101 loops=1)
--    Index Cond: (user_id = 42)
--  Planning Time: 0.112 ms
--  Execution Time: 0.121 ms
```
Now each node also shows `actual time=0.024..0.089` (real milliseconds) and
`rows=101 loops=1` (the real row count). The estimate (`rows=97`) and reality
(`rows=101`) are close here — a healthy sign of up-to-date planner statistics. A
*large* gap between estimated and actual rows is itself a diagnostic signal
(section 4): it usually means the table's statistics are stale and `ANALYZE` needs
to be re-run.

```sql
-- Comparing a selective query against a low-selectivity one on the same table
EXPLAIN ANALYZE SELECT * FROM event WHERE user_id = 42;           -- ~100 rows  -> Index Scan
EXPLAIN ANALYZE SELECT count(*) FROM event WHERE status = 'paid'; -- ~250k rows -> Seq Scan
```
Same table, same indexing effort available, two completely different plans — purely
because of how selective each filter is.

```sql
-- BUFFERS adds real I/O detail: how many pages came from cache vs. disk
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM event WHERE created_at >= now() - interval '1 day' ORDER BY created_at;
--    ... Buffers: shared hit=12 read=3 ...
```
`shared hit` = pages already in Postgres's shared buffer cache (fast); `shared
read` = pages that had to be fetched from disk (much slower) — this is how you tell
whether a slow query's cost is CPU-bound (planning/row processing) or genuinely
I/O-bound.

```sql
-- Join strategy shows up in the plan too
EXPLAIN ANALYZE
SELECT e.status, count(*)
FROM event e
WHERE e.created_at >= now() - interval '30 days'
GROUP BY e.status;
```

### How to Diagnose a Slow Query

1. Reproduce the query and run `EXPLAIN (ANALYZE, BUFFERS)` on it.
2. Look for a **Seq Scan** on a large table where the `WHERE` clause is actually
   selective → usually means a missing (or unused) index.
3. Look for **estimated vs. actual row counts** that are wildly different → stale
   planner statistics; run `ANALYZE` (section 4).
4. Look for the node with the **highest `actual time`** → that's the real
   bottleneck, not necessarily the outermost/top node.
5. Apply the fix (add/adjust an index, rewrite the query, `ANALYZE` the table,
   paginate a huge result), then re-run `EXPLAIN ANALYZE` and confirm the plan
   actually changed and got faster — never assume a fix worked without re-checking.

### Why It's Useful

Guessing at performance problems wastes time and often "fixes" the wrong thing
(adding an index that never gets used, for instance). `EXPLAIN ANALYZE` turns
performance debugging into an evidence-based process — you can see, in black and
white, exactly which operation is slow and why, before writing a single line of a
fix.

### Summary / Key Takeaways

- `EXPLAIN` shows the planner's intended plan and estimates; `EXPLAIN ANALYZE`
  actually runs the query and shows real timings alongside those estimates.
- Read plans bottom-up: the lowest nodes execute first and feed the nodes above.
- A Seq Scan on a big table with a selective filter is the #1 "add an index" signal;
  a big estimate-vs-actual gap is the #1 "run ANALYZE" signal.
- Always re-run `EXPLAIN ANALYZE` after a fix to confirm the plan actually changed —
  don't assume.

---

## 3. Advanced Indexes — Composite, Covering, Partial, Expression

Beyond a single-column B-tree, Postgres supports several index shapes that target
specific, real query patterns far more precisely than a plain single-column index.

### Key Concepts

- **Composite (multi-column) index**: indexes several columns together, in a
  specific declared order, e.g. `(user_id, created_at)`.
- **The leftmost-prefix rule**: a composite index on `(a, b, c)` can serve queries
  filtering on `(a)`, `(a, b)`, or `(a, b, c)` — any *left-anchored prefix* of the
  column list — but **cannot** serve a query filtering only on `(b)`, `(c)`, or
  `(b, c)` alone, since those skip the leading column. This makes column **order** a
  real design decision, not a cosmetic one.
- **Covering index (`INCLUDE`)**: adds extra columns to an index purely so their
  values "ride along," without making them part of the sort key — enough for an
  **Index Only Scan** to answer a query entirely from the index, never touching the
  table.
- **Partial index**: indexes only the subset of rows matching a `WHERE` predicate —
  much smaller and faster for queries that always filter on that same predicate
  (e.g. an operational "work queue" of only `status = 'pending'` rows). Can also
  enforce *conditional* uniqueness.
- **Expression (functional) index**: indexes the *result* of an expression applied
  to a column, rather than the raw column — fixes the "a function on the column
  defeats a plain index" problem from section 1. The query's expression must match
  the index's expression exactly for the planner to use it.
- **Unique index**: enforces uniqueness *and* provides a fast lookup path — a
  `UNIQUE` constraint is implemented internally as a unique index.

### Worked Examples

```sql
-- Composite index — order columns: equality filter first, range/sort column next
CREATE INDEX idx_event_user_created ON event (user_id, created_at);

SELECT * FROM event WHERE user_id = 42 AND created_at >= now() - interval '30 days';
-- can use the FULL composite index: filters user_id, then range-scans within it

SELECT * FROM event WHERE user_id = 42 ORDER BY created_at DESC LIMIT 5;
-- can also use it: filters user_id via the index, then walks it in sorted order for the LIMIT
```

```sql
-- The leftmost-prefix rule in action
SELECT * FROM event WHERE user_id = 42;
-- USES idx_event_user_created — user_id is the leading (leftmost) column

SELECT * FROM event WHERE created_at >= now() - interval '1 day';
-- does NOT use idx_event_user_created — created_at alone SKIPS the leading column
-- (a separate single-column index on created_at, from section 1, is what serves this)
```
In this example: the same composite index serves the first query but not the
second, purely because of which column is on the left. This is why the standard
advice is to order composite index columns as *equality filter(s) first, then the
range/sort column* — matching how the query itself is shaped.

```sql
-- Covering index (INCLUDE) — enables an Index Only Scan
CREATE INDEX idx_event_user_incl ON event (user_id) INCLUDE (amount, status);

SELECT amount, status FROM event WHERE user_id = 42;
-- EXPLAIN shows: Index Only Scan using idx_event_user_incl on event
-- (amount and status are read straight from the index; the table itself is never touched)
```
`amount` and `status` are in `INCLUDE`, not the index's sort key — they don't
participate in the leftmost-prefix rule at all, they're just carried along so this
exact query never needs a second trip to the table's heap. An index-only scan still
depends on the table's *visibility map* being up to date (a side effect of `VACUUM`,
section 4) to confirm a row version is visible without checking the heap.

```sql
-- Partial index — indexes only a targeted subset of rows
CREATE INDEX idx_event_pending ON event (created_at) WHERE status = 'pending';

SELECT * FROM event WHERE status = 'pending' AND created_at >= now() - interval '7 days';
-- uses the small, targeted partial index — only pending rows, so it's a fraction
-- of the size of a full index on created_at
```
A partial index holding only `pending` rows is perfect for a "work queue" access
pattern — you almost always query the pending subset, never the whole table, so
there's no reason to pay for indexing the other 75% of rows. Partial indexes can
also enforce conditional uniqueness, e.g. `CREATE UNIQUE INDEX one_primary_per_user
ON address (user_id) WHERE is_primary` — "at most one primary address per user,"
enforced by the database, without a full unique constraint across all addresses.

```sql
-- Expression index — fixes "a function on the column defeats the index"
CREATE INDEX idx_event_lower_status ON event (lower(status));

SELECT * FROM event WHERE lower(status) = 'paid';   -- now index-able
```
The index stores `lower(status)` values, not raw `status` values — so the query
must use the *exact same expression* (`lower(status)`, not `upper(status)` or
`status` alone) for the planner to recognize the match and use this index.

```sql
-- Inspecting what exists and how big it is
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'event';
SELECT indexrelname, pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes WHERE relname = 'event';
```

### Comparison Table

| Index shape | Solves | Example |
|---|---|---|
| Composite `(a, b)` | queries filtering/sorting on multiple columns together | `(user_id, created_at)` |
| Covering (`INCLUDE`) | avoiding a second trip to the table (Index Only Scan) | `(user_id) INCLUDE (amount, status)` |
| Partial (`WHERE ...`) | indexing only a frequently-queried subset | `(created_at) WHERE status='pending'` |
| Expression | indexing a computed/transformed value | `(lower(status))` |

### Why It's Useful

A well-targeted composite/covering/partial index frequently outperforms several
separate single-column indexes, while also costing less in write overhead and disk
space (section 4) — because it's built specifically for the query shapes a real
system actually runs, not speculatively for every column in isolation.

### Summary / Key Takeaways

- The leftmost-prefix rule means composite index *column order* is a real design
  decision — equality columns first, range/sort columns after.
- `INCLUDE` turns a regular index into a covering index, enabling the fastest
  possible read: an Index Only Scan.
- Partial indexes target a subset of rows you actually query, staying small and
  fast, and can enforce conditional uniqueness.
- Expression indexes fix the "a function on the column defeats the index" problem —
  but the query must use the exact same expression to benefit.
- Add these for *confirmed* query patterns verified with `EXPLAIN` — never
  speculatively.

---

## 4. Maintenance — When Indexes Hurt, VACUUM, ANALYZE

Every index is a **write-cost and disk-space investment made in exchange for
read speed**. This section is about managing that trade-off, and about the ongoing
housekeeping (`VACUUM`, `ANALYZE`) Postgres needs to keep both queries fast and
storage lean.

### Key Concepts

- **The cost of indexes**: every `INSERT`/`UPDATE`/`DELETE` must update *every*
  index on the affected table, not just the table itself — more indexes means
  slower writes. Each index also consumes disk space (and RAM, once cached).
- **Unused indexes**: `pg_stat_user_indexes.idx_scan = 0` (or very low) means an
  index is rarely or never used by the planner — a strong candidate to drop, since
  it's pure write-cost overhead with no offsetting read benefit.
- **`ANALYZE`**: refreshes the planner's **statistics** — row counts, value
  distributions, the most common values per column, roughly how many distinct
  values a column has (`n_distinct`). The planner uses these purely to *estimate*
  row counts and choose a plan; stale statistics lead directly to bad plans (e.g.
  choosing a Seq Scan when an Index Scan would actually win, or vice versa).
  `autovacuum` runs `ANALYZE` automatically in the background, but it's good
  practice to run it manually right after a large bulk data load.
- **`VACUUM`**: reclaims space from **dead tuples**. Because Postgres uses MVCC
  (Phase 5), an `UPDATE`/`DELETE` never overwrites a row in place — it marks the
  old row version dead and writes a new one. Dead tuples accumulate as **bloat**
  (wasted disk space, extra I/O, slower scans) until `VACUUM` reclaims them for
  reuse. `VACUUM` runs online without blocking normal reads/writes; `VACUUM FULL`
  physically rewrites the table to shrink it on disk, but takes an exclusive lock —
  rarely used on a live production table.
- **Long-running transactions block vacuum**: `VACUUM` cannot remove a row version
  that's still potentially visible to some open transaction's snapshot — so one
  forgotten, idle, open transaction can prevent vacuum from doing its job across the
  entire database, letting bloat accumulate everywhere.
- **Index bloat**: indexes accumulate their own bloat over time too; `REINDEX`
  rebuilds a bloated index from scratch. `CREATE INDEX CONCURRENTLY` and `REINDEX
  CONCURRENTLY` do the equivalent work without taking the exclusive lock that would
  otherwise block concurrent writes — the standard choice in production.

### Worked Examples

```sql
-- Finding indexes that are never actually used
SELECT indexrelname, idx_scan
FROM pg_stat_user_indexes WHERE relname = 'event' ORDER BY idx_scan;
--       indexrelname       | idx_scan
-- ---------------------------+-----------
--  idx_event_status          |     0      <- never used by the planner; drop it
--  idx_event_created_at      |   1,204
--  idx_event_user_id         |  48,910

DROP INDEX IF EXISTS idx_event_status;
```
`idx_event_status` is the exact index from section 1 that the planner refused to
use because `status` has only 4 low-selectivity values — this is the direct,
observable confirmation that it's pure overhead and safe to remove.

```sql
-- Refresh planner statistics after a big load, and inspect what the planner knows
ANALYZE event;

SELECT attname, n_distinct, most_common_vals
FROM pg_stats WHERE tablename = 'event' AND attname IN ('user_id', 'status');
--  attname |  n_distinct  |         most_common_vals
-- ---------+--------------+-----------------------------------
--  user_id |     10000    | NULL  (too many distinct values to list a few)
--  status  |        4     | {paid,pending,shipped,cancelled}
```
`n_distinct` near the table's row count (or close to a known cardinality like
`10000` here) signals high selectivity — a good index candidate. `n_distinct = 4`
for `status` is the statistical confirmation of exactly why the planner won't use
an index on it.

```sql
-- Check dead tuples / bloat and when this table was last vacuumed
SELECT relname, n_live_tup, n_dead_tup, last_autovacuum
FROM pg_stat_user_tables WHERE relname = 'event';
--  relname | n_live_tup | n_dead_tup |        last_autovacuum
-- ---------+------------+------------+---------------------------------
--  event   |  1000000   |   4213     |  2026-09-21 03:12:44.102+00

VACUUM event;            -- reclaim dead tuple space for reuse (online)
VACUUM ANALYZE event;    -- do both in one pass — common after a bulk load
-- VACUUM FULL event;    -- shrinks on disk, but takes an exclusive lock — rare on live tables
```

```sql
-- Rebuild a bloated index without blocking concurrent writes
REINDEX INDEX idx_event_user_id;                          -- blocks writes on this table briefly
CREATE INDEX CONCURRENTLY idx_new ON event (user_id);      -- builds without blocking writes
```

```sql
-- Capacity awareness — how much space is the table and its indexes actually using
SELECT pg_size_pretty(pg_total_relation_size('event')) AS total,   -- table + indexes + TOAST
       pg_size_pretty(pg_relation_size('event'))        AS table_only,
       pg_size_pretty(pg_indexes_size('event'))         AS indexes;
```

### Comparison Table

| Tool | Fixes | Runs automatically? |
|---|---|---|
| `ANALYZE` | stale planner statistics → bad plan choices | yes, via autovacuum |
| `VACUUM` | dead-tuple bloat from MVCC updates/deletes | yes, via autovacuum |
| `VACUUM FULL` | disk-space bloat that plain VACUUM can't reclaim | no — manual, locks the table |
| `REINDEX` / `REINDEX CONCURRENTLY` | bloated index structures | no — manual |

### Why It's Useful

This is the exact mechanism behind the "database bloat / memory" incidents covered
conceptually in System Design — here it's the concrete SQL to *diagnose*
(`pg_stat_user_tables`, `pg_stat_user_indexes`, the size functions) and *fix*
(`VACUUM`, `ANALYZE`, `REINDEX`, dropping unused indexes, keeping transactions
short) the same underlying problem, rather than treating it as an abstract idea.

### Summary / Key Takeaways

- Every index costs write throughput and disk space — index only the columns real
  queries actually filter/join/sort on, and drop indexes with `idx_scan = 0`.
- `ANALYZE` keeps the planner's statistics accurate, which keeps its plan choices
  good; `VACUUM` reclaims MVCC dead-tuple bloat, which keeps scans fast and storage
  lean.
- Both run automatically via `autovacuum`, but trigger them manually after bulk
  loads or large deletes.
- A single long-running, forgotten open transaction can block vacuum system-wide —
  keep transactions short.
- Use `CONCURRENTLY` variants of `CREATE INDEX`/`REINDEX` in production to avoid
  blocking writes.
