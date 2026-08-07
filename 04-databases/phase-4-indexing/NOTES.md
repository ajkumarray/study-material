<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · schema design](../phase-3-schema-design/NOTES.md) | [Phase 5 · transactions ➡](../phase-5-transactions/NOTES.md)
<!-- /nav -->

# Phase 4 — Indexing & Query Performance: Notes (Theory)

The single highest-leverage database skill: making queries fast. This is the SQL-level detail behind System Design Phase 8 (and Java/JPA Phase 7's N+1). Setup uses a **1,000,000-row `event` table** so `EXPLAIN` shows real differences.

## 4.1 — How indexes work
An **index** is a separate, sorted data structure (usually a **B-tree**) mapping column value → row location, so the DB can *jump* to matching rows instead of scanning the whole table — **O(log n)** lookup vs **O(n)** scan. Like a book's index vs reading every page. The **primary key is auto-indexed**; nothing else is by default. Create one with `CREATE INDEX name ON table (col)`.

**What a B-tree index helps:** equality (`=`), ranges (`<`, `>`, `BETWEEN`), `ORDER BY` (the tree is sorted, so sorts/ranges are cheap), and prefix `LIKE 'abc%'`.
**What it doesn't:** leading-wildcard `LIKE '%abc'`, a function on the column (`lower(status)` — needs an *expression index*), and **low-selectivity** filters.

**Selectivity is everything.** An index helps only when a query returns a *small fraction* of rows. `user_id` (10,000 distinct values) → an index is a big win. `status` (4 values, ~25% each) → reading the index then fetching 250k rows is *slower* than a seq scan, so the planner **ignores** the index. Rule: index **high-cardinality** columns you filter/join/sort on; don't index booleans or low-cardinality columns.

**Index types (Postgres):** B-tree (default, ~95% of cases), Hash (equality only), **GIN** (JSONB/arrays/full-text — Phase 6), GiST/SP-GiST (geometric/range), BRIN (huge naturally-ordered/append-only tables — tiny index).

## 4.2 — EXPLAIN / EXPLAIN ANALYZE
The **query planner** decides how to run SQL (which scan, join order, index). **`EXPLAIN`** shows the plan + estimates (no execution); **`EXPLAIN ANALYZE`** actually runs it and shows *real* timings and row counts. This is the #1 tool for diagnosing slow queries — you *see* what the DB does instead of guessing. Read plans **bottom-up/inside-out**.

**Scan types** (worst→best for a selective filter): **Seq Scan** (read every row — a red flag on a big table with a selective WHERE = missing index), **Index Scan** (walk the index, fetch matches), **Bitmap Heap Scan** (bitmap of locations then fetch in physical order — for a medium number of matches), **Index Only Scan** (answer entirely from the index — fastest; needs a covering index). **Join strategies:** Nested Loop (few outer rows + indexed inner), Hash Join (large unsorted), Merge Join (both sorted).

**Diagnosing:** run `EXPLAIN (ANALYZE, BUFFERS)` and look for a Seq Scan on a big table with a selective WHERE (→ add an index), estimated vs actual rows wildly off (→ stale stats, run `ANALYZE`), and the highest "actual time" node (→ the bottleneck). Add the fix, re-run, confirm the plan changed.

## 4.3 — Advanced indexes
- **Composite (multi-column)** `(a, b, c)` — the **leftmost-prefix rule**: serves `(a)`, `(a,b)`, `(a,b,c)` but *not* `(b)` or `(c)` alone. So **column order matters**: put the equality/most-selective column first, the range/sort column next. One good composite index often beats several single-column ones.
- **Covering (`INCLUDE`)** — add SELECTed columns so an **index-only scan** answers the query without touching the table (the fastest read). Needs an up-to-date visibility map (→ VACUUM).
- **Partial** (`... WHERE predicate`) — index only a subset of rows; tiny and fast for that subset (e.g., a "work queue" of `status='pending'` rows), and can enforce conditional uniqueness (`UNIQUE ... WHERE is_primary`).
- **Expression (functional)** — index `lower(status)` so `WHERE lower(status)='paid'` is index-able; the query expression must match the index expression exactly.
- **Unique index** — enforces uniqueness *and* speeds lookups (a UNIQUE constraint is a unique index underneath).

Design: order composite columns equality → range/sort → INCLUDE the rest. Target specific query shapes with partial/expression indexes. But add them for **confirmed** query patterns (verified via EXPLAIN), never speculatively.

## 4.4 — Maintenance: when indexes hurt, VACUUM, ANALYZE
**Indexes cost writes + space:** every INSERT/UPDATE/DELETE must update *every* index; each index takes disk/RAM. An index is a read optimization paid for with write cost. **Drop unused indexes** — `pg_stat_user_indexes.idx_scan = 0` means it's never used.

- **`ANALYZE`** refreshes **planner statistics** (row counts, value distributions, n_distinct). Stale stats → bad estimates → bad plans (a Seq Scan where an Index Scan would win). Run it after big data loads; autovacuum does it routinely. Inspect via `pg_stats`.
- **`VACUUM`** reclaims **dead tuples**. Postgres **MVCC** (Phase 5) makes UPDATE/DELETE mark old row versions dead and write new ones; dead tuples accumulate as **bloat** (wasted space, more I/O, slower scans). VACUUM reclaims them for reuse (online); `VACUUM FULL` shrinks on disk but **locks** the table (rare). Autovacuum handles this in the background.
- **Long-running transactions block vacuum** (System Design Phase 7) — vacuum can't remove versions still visible to an open transaction, so one forgotten transaction can bloat the whole DB. **Keep transactions short.**
- **Index bloat / rebuilds:** `REINDEX` (or `CREATE INDEX CONCURRENTLY` / `REINDEX CONCURRENTLY` to avoid blocking writes in production).

**Mindset:** ANALYZE keeps the planner smart; VACUUM keeps the table lean; both run via autovacuum but trigger manually for bulk loads/deletes. This *is* the "database bloat / memory" story from System Design Phase 7 — now with the exact SQL to diagnose (`pg_stat_user_tables`, `pg_stat_user_indexes`, size functions) and fix it.
