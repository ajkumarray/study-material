<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · schema design](../phase-3-schema-design/NOTES.md) | [Phase 5 · transactions ➡](../phase-5-transactions/NOTES.md)
<!-- /nav -->

# Phase 4 — Indexing & Query Performance: Interview Q&A

⭐ = asked constantly.

**Q: What is a database index and how does it speed queries?** ⭐⭐
A separate sorted structure (usually a B-tree) mapping column values to row locations, so the DB jumps to matching rows (O(log n)) instead of scanning the whole table (O(n)). Like a book's index. The PK is auto-indexed; you add indexes on columns you filter/join/sort on.

**Q: What's the cost of an index — why not index every column?** ⭐⭐
Every INSERT/UPDATE/DELETE must update every index (slower writes), and each index consumes disk/RAM. An index trades write speed and space for read speed. Index only the columns queries actually use, and drop unused ones.

**Q: What is selectivity and why does it decide index usefulness?** ⭐⭐
Selectivity = fraction of rows a filter returns. Indexes help when few rows match (high-cardinality columns like user_id, email, timestamps). For low-cardinality columns (e.g., a status with 4 values ≈ 25% each), reading the index then fetching a quarter of the table is slower than a seq scan, so the planner ignores the index.

**Q: What does EXPLAIN / EXPLAIN ANALYZE show, and how do you use it?** ⭐⭐
EXPLAIN shows the planner's chosen plan and estimates; EXPLAIN ANALYZE runs the query and shows real timings and row counts. Use it to find a Seq Scan on a big table with a selective WHERE (add an index), estimated-vs-actual row mismatches (stale stats → ANALYZE), and the highest actual-time node (the bottleneck). Then fix and re-run.

**Q: Seq Scan vs Index Scan vs Index Only Scan vs Bitmap Heap Scan?**
Seq Scan reads every row (fine for small tables/low selectivity, bad for a selective query on a big table). Index Scan walks the index and fetches matching rows. Bitmap Heap Scan builds a bitmap of locations then fetches in physical order (medium match count). Index Only Scan answers entirely from the index without touching the table (fastest; needs a covering index).

**Q: What's the leftmost-prefix rule for composite indexes?** ⭐⭐
An index on `(a, b, c)` can serve queries on `(a)`, `(a, b)`, `(a, b, c)` — left-anchored prefixes — but not `(b)`, `(c)`, or `(b, c)` alone. So put the equality/most-selective column first and the range/sort column next. Column order is a design decision.

**Q: What is a covering index / index-only scan?**
An index that includes all columns a query needs (via key columns + `INCLUDE`), so the query is answered entirely from the index without reading the table — the fastest possible read. Needs the table's visibility map current (vacuumed).

**Q: When would you use a partial or expression index?**
Partial: index only a subset of rows (`WHERE status='pending'`) — tiny and fast for that subset, and good for conditional uniqueness. Expression: index `lower(email)` so `WHERE lower(email)=...` (a function on the column) can use an index. Both target specific query shapes.

**Q: Why doesn't `WHERE lower(name) = 'x'` or `LIKE '%abc'` use an index?**
`lower(name)` computes a value the plain index on `name` doesn't store — use an expression index on `lower(name)`. Leading-wildcard `LIKE '%abc'` can't use the sorted B-tree order (the start is unknown) — needs a trigram/full-text index. Prefix `LIKE 'abc%'` does use a B-tree.

**Q: What do VACUUM and ANALYZE do?** ⭐⭐
ANALYZE refreshes planner statistics (row counts, distributions) so it estimates rows and chooses plans correctly — stale stats cause bad plans. VACUUM reclaims dead tuples left by MVCC updates/deletes (bloat → more I/O, slower scans). Autovacuum runs both; trigger manually after bulk loads/deletes. VACUUM FULL rewrites the table to shrink disk but locks it.

**Q: What causes table bloat, and how does a long transaction relate?** ⭐
MVCC keeps old row versions on update/delete until vacuumed; if autovacuum lags they accumulate as bloat. A long-running transaction blocks vacuum from removing versions still visible to it, so one forgotten open transaction can bloat the whole database. Keep transactions short and monitor dead-tuple ratios.

**Q: How do you diagnose and fix a slow query end to end?**
Reproduce → `EXPLAIN (ANALYZE, BUFFERS)` → identify the problem (seq scan on selective filter, bad estimate, N+1, huge intermediate result) → fix (add/adjust an index, rewrite the query, ANALYZE, paginate) → re-run and confirm the plan and timing improved. Measure, don't guess (System Design Phase 8).
