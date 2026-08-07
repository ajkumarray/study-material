<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · schema design ➡](../phase-3-schema-design/NOTES.md)
<!-- /nav -->

# Phase 2 — Multi-table & Aggregation: Interview Q&A

⭐ = asked constantly.

**Q: Explain the JOIN types.** ⭐⭐
INNER = only matching rows (intersection). LEFT OUTER = all left rows + matches, NULLs where no right match. RIGHT = mirror. FULL OUTER = all rows from both, NULL-filled. CROSS = cartesian product. Know a one-line use for each; INNER and LEFT cover most real work.

**Q: `WHERE` vs `HAVING`?** ⭐⭐
WHERE filters individual rows *before* grouping and can't reference aggregates. HAVING filters *groups* after `GROUP BY` and is where aggregate conditions go (`HAVING SUM(x) > 100`). They're often used together — WHERE to trim rows, then HAVING to trim the resulting groups.

**Q: A GROUP BY query errors with "column must appear in GROUP BY." Why?** ⭐
Every selected column must be either grouped or wrapped in an aggregate — otherwise the DB can't decide which of the many row values to show per group. Fix: add the column to GROUP BY (group by the PK plus displayed columns) or aggregate it.

**Q: `COUNT(*)` vs `COUNT(col)` vs `COUNT(DISTINCT col)`?** ⭐
`COUNT(*)` counts all rows; `COUNT(col)` counts rows where col is non-NULL (aggregates skip NULLs); `COUNT(DISTINCT col)` counts distinct non-NULL values. A frequent trick question.

**Q: Difference between a JOIN and a subquery — when do you use each?**
Often interchangeable (the optimizer may run them identically). Use a JOIN when you need columns from the other table; use `EXISTS`/`IN` when you only need to test whether a match exists; use a CTE for readability of multi-step logic. `EXISTS` can be faster (short-circuits at the first match).

**Q: Why prefer `NOT EXISTS` over `NOT IN`?** ⭐
If the `NOT IN` subquery returns any NULL, every comparison becomes UNKNOWN and the query returns no rows — a silent bug. `NOT EXISTS` handles NULLs correctly. It's the safe way to write an anti-join.

**Q: What is a CTE and why use one?**
A Common Table Expression (`WITH name AS (...)`) — a named subquery scoped to the statement. Improves readability (reads as sequential steps), lets you reference the same subquery multiple times, and enables recursion (`WITH RECURSIVE`) for hierarchies/trees.

**Q: What are window functions and how do they differ from GROUP BY?** ⭐⭐
They compute across a set of related rows via `OVER(...)` **without collapsing** them — you keep every row and get the aggregate/ranking alongside. GROUP BY collapses rows into one per group. Windows enable running totals, rankings, top-N-per-group, and row-to-row comparisons that GROUP BY can't do cleanly.

**Q: `ROW_NUMBER` vs `RANK` vs `DENSE_RANK`?** ⭐⭐
On ties (with an ORDER BY): ROW_NUMBER gives unique sequential numbers (1,2,3,4); RANK gives ties the same rank then skips (1,1,3); DENSE_RANK gives ties the same rank with no gap (1,1,2). ROW_NUMBER for "pick one per group," RANK/DENSE_RANK for leaderboards.

**Q: How do you get the top-N rows per group?** ⭐⭐
Window function: `ROW_NUMBER() OVER (PARTITION BY group ORDER BY metric DESC)` in a subquery/CTE, then filter `WHERE rn <= N`. The canonical window-function problem — plain GROUP BY can't do it.

**Q: How do you compute a running total or a month-over-month change?**
Running total: `SUM(x) OVER (ORDER BY date ROWS UNBOUNDED PRECEDING)`. MoM change: `LAG(revenue) OVER (ORDER BY month)` and subtract. Both are window functions with an ORDER BY inside OVER.

**Q: `UNION` vs `UNION ALL`?** ⭐
UNION removes duplicates (extra sort/dedupe cost); UNION ALL keeps them and is faster. Use UNION ALL unless you specifically need distinct rows. Both stack rows vertically and require matching column counts/types — different from a JOIN, which combines columns.
