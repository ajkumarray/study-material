<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · schema design](../phase-3-schema-design/NOTES.md) | [Phase 5 · transactions ➡](../phase-5-transactions/NOTES.md)
<!-- /nav -->

# Phase 4 — Indexing & Query Performance: Interview Q&A

⭐ = asked constantly. Examples reference the 1,000,000-row `event(id, user_id,
status, amount, created_at)` table from `00-setup.sql` (`user_id` spread over
10,000 distinct values, `status` one of 4 values).

**Q: What is a database index and how does it speed up queries?** ⭐⭐
An index is a separate, sorted data structure — a B-tree by default in Postgres —
that maps column values to the physical locations of the rows containing them, so
the database can jump directly to matching rows instead of scanning the entire
table. Looking up a value via the index is roughly `O(log n)` (walking a balanced
tree); scanning the whole table is `O(n)`. On the 1M-row `event` table, filtering
`WHERE user_id = 42` without an index reads all 1,000,000 rows (`Seq Scan`); with
`CREATE INDEX idx_event_user_id ON event (user_id)`, the same query becomes an
`Index Scan` that touches roughly 100 rows — the ~100 rows belonging to that one
`user_id`, plus a small constant number of tree-node reads to find them. The primary
key is automatically indexed; every other index is a decision you make explicitly.

**Q: What's the cost of an index — why not just index every column "to be
safe"?** ⭐⭐
Every `INSERT`, `UPDATE`, or `DELETE` on a table has to update *every* index on
that table, not just the table's own storage — more indexes directly means slower
writes. Each index also consumes real disk space, and RAM once it's cached, and adds
more options for the planner to evaluate when choosing a plan. So an index is a
deliberate trade: read speed, paid for with write speed and storage. The practical
discipline is to index the columns real queries actually filter, join, or sort on —
confirmed via `EXPLAIN` — and to periodically check `pg_stat_user_indexes.idx_scan`
for indexes that are never used (`idx_scan = 0`) and drop them, since they're pure
overhead with no offsetting benefit.

**Q: What is selectivity, and why does it decide whether an index actually gets
used?** ⭐⭐
Selectivity is the fraction of a table's rows a given filter condition matches.
Indexes pay off when a filter is *highly selective* — it returns a small slice of
the table. `user_id` in the `event` table has 10,000 distinct values across 1M
rows, so `WHERE user_id = 42` matches roughly 0.01% of rows — a huge win for an
index. `status` has only 4 distinct values, so `WHERE status = 'paid'` matches
roughly 25% of the table (~250,000 rows); reading a quarter of the index and then
fetching a quarter of the table's rows one by one is actually *slower* than just
reading the table sequentially, so the planner will correctly ignore an index on
`status` even when one exists. The rule of thumb: index high-cardinality columns you
filter/join/sort on; don't bother indexing booleans or other low-cardinality columns
in isolation.

**Q: What do `EXPLAIN` and `EXPLAIN ANALYZE` show, and how do you use them to
diagnose a slow query?** ⭐⭐
`EXPLAIN` shows the query planner's chosen execution plan along with its cost and
row-count *estimates*, without actually running the query. `EXPLAIN ANALYZE`
additionally **executes** the query and layers real timings (`actual time=...`) and
real row counts (`rows=... loops=...`) onto that same plan, so you can compare
estimate against reality.
```sql
EXPLAIN ANALYZE SELECT * FROM event WHERE user_id = 42;
--  Index Scan using idx_event_user_id on event
--    (cost=0.42..8.65 rows=97 width=41) (actual time=0.024..0.089 rows=101 loops=1)
--    Index Cond: (user_id = 42)
--  Planning Time: 0.112 ms
--  Execution Time: 0.121 ms
```
To diagnose a slow query: run `EXPLAIN (ANALYZE, BUFFERS)` on it, then look for (1)
a `Seq Scan` on a large table where the `WHERE` clause is actually selective — the
classic "missing index" signal; (2) a large gap between estimated and actual row
counts — a "stale planner statistics" signal, fixed by running `ANALYZE`; and (3)
whichever node has the highest `actual time` — that's the real bottleneck, which
isn't always the outermost node in the plan. Fix the specific issue found, then
re-run `EXPLAIN ANALYZE` to confirm the plan and timing actually improved — never
assume a fix worked without re-checking the plan.

**Q: Walk through the different scan types — Seq Scan, Index Scan, Bitmap Heap
Scan, Index Only Scan.**
`Seq Scan` reads every row in the table in physical order — perfectly fine for
small tables or low-selectivity filters, but a red flag on a large table with a
genuinely selective `WHERE` clause. `Index Scan` walks the index's sorted structure
to find matching entries, then fetches each corresponding row individually from the
table — efficient when relatively few rows match. `Bitmap Heap Scan` is a middle
ground: it uses the index to build an in-memory bitmap of matching row locations
first, then visits the table in physical (not index) order — good for a moderate
number of matches, where fetching rows one-by-one via a plain Index Scan would incur
too much scattered I/O, but a full Seq Scan would still read too much. `Index Only
Scan` answers the query entirely from the index's own data, never touching the
table's heap at all — the fastest option, but only possible with a *covering*
index that includes every column the query needs.

**Q: What's the leftmost-prefix rule for composite indexes, and why does column
order matter?** ⭐⭐
A composite (multi-column) index on `(a, b, c)` can serve any query that filters on
a *left-anchored prefix* of that column list — `(a)`, `(a, b)`, or `(a, b, c)` — but
it **cannot** serve a query that filters only on `(b)`, `(c)`, or `(b, c)`, because
those skip over the leading column the index is physically sorted by first.
```sql
CREATE INDEX idx_event_user_created ON event (user_id, created_at);

SELECT * FROM event WHERE user_id = 42;                              -- USES the index
SELECT * FROM event WHERE user_id = 42 AND created_at >= now() - interval '30 days';  -- USES it fully
SELECT * FROM event WHERE created_at >= now() - interval '1 day';    -- does NOT use it
```
Because of this, column order in a composite index is a real design decision: put
the column(s) you always filter on with equality first, and the column you range-
filter or sort by next — matching the shape of the queries you actually run, not
alphabetical or arbitrary order.

**Q: What is a covering index / index-only scan?**
A covering index is one that includes every column a specific query needs, either
as part of its sort key or via `INCLUDE`, so that query can be answered entirely
from the index's own storage without a second trip to fetch each matching row from
the table's heap — that's an **Index Only Scan**, the fastest possible read.
```sql
CREATE INDEX idx_event_user_incl ON event (user_id) INCLUDE (amount, status);
SELECT amount, status FROM event WHERE user_id = 42;   -- Index Only Scan
```
`amount` and `status` ride along via `INCLUDE` without being part of the index's
sort order (they don't participate in the leftmost-prefix rule). One subtlety: an
Index Only Scan still needs the table's *visibility map* — maintained by
`VACUUM` — to be current, so Postgres can confirm a given row version is visible to
the current transaction without checking the actual heap page.

**Q: When would you reach for a partial index, or an expression index?**
A **partial index** (`CREATE INDEX ... ON t (col) WHERE predicate`) indexes only
the subset of rows matching that predicate — much smaller and faster than a full
index when your queries always filter on that same condition. The example from this
phase: `CREATE INDEX idx_event_pending ON event (created_at) WHERE status =
'pending'` gives you a tiny, fast index for a "work queue" that only ever looks at
pending rows, instead of indexing all 1M rows including the 75% that are irrelevant
to that access pattern. Partial indexes can also enforce *conditional* uniqueness,
e.g. "at most one primary address per user" via `CREATE UNIQUE INDEX ... ON address
(user_id) WHERE is_primary`. An **expression index** indexes the *result* of an
expression rather than a raw column — `CREATE INDEX ON event (lower(status))` lets
`WHERE lower(status) = 'paid'` use an index, whereas a plain index on `status` can't
help that query at all, since it never stores the lowercased value. The query's
expression has to match the index's expression exactly for the planner to recognize
it.

**Q: Why doesn't `WHERE lower(name) = 'x'` use a plain index on `name`, and why
doesn't `LIKE '%abc'` use one either?** ⭐
A plain B-tree index on `name` stores the raw values of `name`, sorted as-is —
`lower(name)` computes a *different* value the index simply doesn't contain, so the
planner can't use that index for the transformed comparison; you need an expression
index built on `lower(name)` specifically. `LIKE '%abc'` fails for a completely
different reason: a B-tree's sort order lets you jump to a *known starting prefix*
efficiently, but with a leading wildcard there's no known prefix to jump to — the
match could start anywhere, so the planner falls back to scanning every row. A
trailing-wildcard pattern like `LIKE 'abc%'` *does* use a plain B-tree index, since
`'abc'` gives it exactly the starting point it needs. A leading-wildcard search that
must stay fast needs a trigram (`pg_trgm`) or full-text index instead.

**Q: What do `VACUUM` and `ANALYZE` do, and why do you need both?** ⭐⭐
`ANALYZE` refreshes the planner's **statistics** about a table — row counts, value
distributions, most-common values, roughly how many distinct values each column has.
The planner relies entirely on these statistics to estimate row counts and choose a
plan; stale statistics lead directly to bad plans, like choosing a Seq Scan where an
Index Scan would actually be faster, because the planner's cost model is working off
outdated assumptions. `VACUUM` reclaims space from **dead tuples**: because Postgres
implements MVCC (Phase 5) by never overwriting a row in place, every `UPDATE`/
`DELETE` leaves behind an old row version that becomes "dead" once no open
transaction can see it any longer. Dead tuples pile up as bloat — wasted disk space,
extra I/O per scan, slower queries — until `VACUUM` reclaims that space for reuse.
Autovacuum runs both automatically in the background, but a bulk data load or a
large delete is worth triggering `VACUUM ANALYZE` for manually right afterward,
rather than waiting for autovacuum's next scheduled pass.

**Q: What causes table bloat, and why does a single long-running transaction make
it worse?** ⭐
Bloat is the accumulation of dead tuples that `VACUUM` hasn't yet reclaimed — every
`UPDATE`/`DELETE` under MVCC leaves an old row version behind, and if vacuuming
falls behind the rate of writes, those dead versions pile up faster than they're
cleared. The specific way a long-running transaction makes this worse: `VACUUM`
cannot remove a row version that might still be visible to *some* open transaction's
snapshot, no matter how old that transaction is or how briefly it's been idle. A
single forgotten, idle "open transaction" left dangling by a buggy connection pool
can therefore prevent vacuum from reclaiming *any* dead tuples that were created
after that transaction's snapshot was taken — across the whole database, not just
the tables that transaction touched. This is why monitoring for long-running/idle-
in-transaction sessions (via `pg_stat_activity`) and keeping transactions short is
standard production practice, not just a locking concern (Phase 5).

**Q: How do you diagnose and fix a slow query end to end?**
Reproduce the slow query, run `EXPLAIN (ANALYZE, BUFFERS)` on it, and read the plan
bottom-up looking for the specific problem: a Seq Scan on a large table with a
genuinely selective filter (missing index), an estimated-vs-actual row mismatch
(stale statistics — run `ANALYZE`), an inefficient join strategy caused by a missing
index on the inner side of a Nested Loop, or simply a huge intermediate result that
needs pagination or a narrower filter rather than any indexing fix at all. Apply the
targeted fix — add or adjust an index, rewrite the query, run `ANALYZE`, add
pagination — then re-run `EXPLAIN ANALYZE` to confirm the plan changed and the
timing actually improved. The discipline throughout is: measure with `EXPLAIN`, form
a hypothesis about the specific bottleneck node, fix that specific thing, and
measure again — never guess-and-check by adding indexes speculatively.

**Q: When would `CREATE INDEX CONCURRENTLY` matter in production, versus a plain
`CREATE INDEX`?**
A plain `CREATE INDEX` takes a lock that blocks concurrent writes to the table for
the duration of the build — acceptable on a small table or during a maintenance
window, but unacceptable on a large, actively-written production table, where it
could stall writes for minutes. `CREATE INDEX CONCURRENTLY` builds the same index
without taking that blocking lock, at the cost of taking noticeably longer overall
and requiring two full table scans internally. The same trade-off applies to
rebuilding an existing (bloated) index: `REINDEX` locks the table, `REINDEX
CONCURRENTLY` does not. The standard production rule is: always use the
`CONCURRENTLY` variant for any index operation on a table that's actively receiving
writes.
