<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · schema design ➡](../phase-3-schema-design/NOTES.md)
<!-- /nav -->

# Phase 2 — Querying Multiple Tables & Aggregation: Notes (Theory)

Real data lives in multiple related tables (normalization, Phase 3). This phase is how you recombine and summarize it — the daily work of SQL and the richest interview territory. Schema: `author → book → sale ← customer`.

## 2.1 — JOINs
A JOIN matches rows across tables on a condition (usually FK = PK); the **type** decides what happens to non-matching rows:
- **INNER** (the default `JOIN`) — only rows matching on both sides (intersection). Drops unmatched rows.
- **LEFT [OUTER]** — all left rows + matches; NULLs where the right has none. The most common outer join ("keep every book, show author if known").
- **RIGHT** — mirror of LEFT (rare; just flip the tables).
- **FULL OUTER** — all rows from both sides, NULL-filled (union).
- **CROSS** — every combination (cartesian product). Usually a bug from a *missing* join condition.
- **SELF JOIN** — a table joined to itself with two aliases (pairs, hierarchies).

**Anti-join** ("rows with no match"): `LEFT JOIN ... WHERE right.id IS NULL`, or `NOT EXISTS`. **Key subtlety:** with an outer join, a condition on the right table in `WHERE` filters *after* the join (can silently turn a LEFT into an INNER by dropping the NULL rows), whereas in `ON` it filters the match itself. Put right-table filters in `ON` for outer joins.

## 2.2 — Aggregation
**Aggregate functions** collapse many rows to one: `COUNT`, `SUM`, `AVG`, `MIN`, `MAX` (+ Postgres `STRING_AGG`, `ARRAY_AGG`). They **skip NULLs** (Phase 1.3) — `COUNT(*)` counts rows, `COUNT(col)` counts non-null values, `COUNT(DISTINCT col)` counts distinct non-nulls.

**`GROUP BY`** produces one summary row per group. **Rule:** every SELECT column must be in the GROUP BY or inside an aggregate. Group by the PK plus any displayed columns. **`HAVING`** filters *groups* after aggregation; **`WHERE`** filters *rows* before it (and can't use aggregates) — the two often appear together (WHERE trims rows, then HAVING trims groups). Evaluation order (Phase 1.2): `FROM → WHERE → GROUP BY → HAVING → SELECT → ORDER BY` explains all of this. Use `LEFT JOIN` + `COALESCE(SUM(...), 0)` to include groups with zero matches. `GROUP BY ROLLUP/CUBE/GROUPING SETS` add subtotals.

## 2.3 — Subqueries & CTEs
A **subquery** is a nested query:
- **Scalar** (returns one value) — usable anywhere a value is (`WHERE price > (SELECT avg(price)...)`, or as a computed column).
- **Column / `IN`** (returns a list) — `WHERE author_id IN (SELECT ...)`.
- **Correlated** (references the outer row, re-evaluated per row) — `EXISTS (SELECT 1 FROM ... WHERE b.author_id = a.id)`. `EXISTS` short-circuits at the first match.
- **Derived table** (in `FROM`) — a query result used as a table (must be aliased).

**`NOT EXISTS` over `NOT IN`:** if a `NOT IN` subquery yields any NULL, the whole predicate becomes UNKNOWN → zero rows (a classic bug). `NOT EXISTS` handles NULLs correctly — the safe anti-join.

**CTEs (`WITH name AS (...)`):** named, readable subqueries reusable within a statement — prefer them for multi-step logic (they read like sequential steps). Chain several CTEs into a pipeline. **`WITH RECURSIVE`** (anchor `UNION ALL` recursive step) walks hierarchies/sequences — org charts, category trees, graph traversals, number series. (Note: subqueries vs joins are often interchangeable and the optimizer may treat them identically — choose for clarity.)

## 2.4 — Window functions (the crown jewel)
A window function computes over a set of rows related to the current row via `OVER (...)`, **without collapsing** them — you keep every row *and* get an aggregate/ranking beside it (what GROUP BY can't do without a self-join).

`OVER (PARTITION BY ... ORDER BY ... frame)`:
- **`PARTITION BY`** — which rows share the window (like GROUP BY, but rows survive). Omit = one window over all rows.
- **`ORDER BY`** — ordering within the window (needed for ranking/running/offset).
- **frame** (`ROWS/RANGE BETWEEN ...`) — which surrounding rows count (e.g., running total = `UNBOUNDED PRECEDING AND CURRENT ROW`).

The toolkit: **ranking** — `ROW_NUMBER` (unique 1..n), `RANK` (ties skip: 1,1,3), `DENSE_RANK` (ties no skip: 1,1,2); **offset** — `LAG`/`LEAD` (previous/next row — deltas, month-over-month); **aggregate windows** — `SUM/AVG/... OVER` (running totals, group-avg-per-row); **`NTILE`** (buckets/percentiles); **`FIRST_VALUE`/`LAST_VALUE`**. The killer use: **top-N per group** (`ROW_NUMBER() OVER (PARTITION BY g ORDER BY x) = 1`) and **running totals** — both awkward or impossible with plain GROUP BY. Window functions run after GROUP BY/HAVING, before ORDER BY.

## 2.5 — Set operations
Combine the **rows** of two queries vertically (JOINs combine columns horizontally). Both must have the same column count and compatible types; names come from the first query; a single `ORDER BY` at the end orders the combined result.
- **`UNION`** — both, duplicates removed (sorts to dedupe). **`UNION ALL`** — keeps duplicates, faster — prefer it unless you need dedupe.
- **`INTERSECT`** — rows in both.
- **`EXCEPT`** (Oracle: `MINUS`) — rows in the first but not the second.
