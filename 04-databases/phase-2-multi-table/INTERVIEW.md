<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · schema design ➡](../phase-3-schema-design/NOTES.md)
<!-- /nav -->

# Phase 2 — Multi-table & Aggregation: Interview Q&A

⭐ = asked constantly. Schema: `author → book → sale ← customer` (see NOTES.md for
seed data).

**Q: Explain the JOIN types.** ⭐⭐
`INNER JOIN` keeps only rows that match on both sides — the intersection; unmatched
rows on either side vanish. `LEFT [OUTER] JOIN` keeps every row from the left table,
filling right-side columns with `NULL` where there's no match — the join people reach
for by far the most, because it says "give me all of X, plus Y if it exists." `RIGHT
JOIN` is the mirror image, and in practice people just swap the table order and use
`LEFT` instead. `FULL OUTER JOIN` keeps every row from both sides, NULL-filling
whichever side is missing. `CROSS JOIN` produces every combination of rows with no
match condition at all — usually seen by accident, when a `JOIN`'s `ON` clause is
missing or wrong, silently multiplying the row count. For an interview, know one
concrete example query for each, and be ready to explain what happens to an unmatched
row under each type.

**Q: What's the difference between putting a right-table condition in `ON` versus
`WHERE` for an outer join?** ⭐ *senior probe*
This is the sharpest gotcha in the whole topic. A condition in `ON` filters which
rows are considered a *match* — unmatched left rows still survive the LEFT JOIN, with
NULLs for the right side. The same condition in `WHERE` filters the *joined result
afterward* — and because `NULL = anything` evaluates to UNKNOWN (not TRUE), any left
row that had no match (and so got NULL right-side columns) gets silently dropped by
that `WHERE` clause, turning your LEFT JOIN back into an effective INNER JOIN.
```sql
-- keeps every book, even ones with no USA author match
SELECT b.title, a.name FROM book b
LEFT JOIN author a ON b.author_id = a.id AND a.country = 'USA';

-- silently drops books whose author isn't from the USA (or has no author at all)
SELECT b.title, a.name FROM book b
LEFT JOIN author a ON b.author_id = a.id
WHERE a.country = 'USA';
```
If someone reports "my LEFT JOIN is behaving like an INNER JOIN," this is almost
always the cause.

**Q: `WHERE` vs `HAVING`?** ⭐⭐
`WHERE` filters individual rows *before* grouping happens and cannot reference
aggregate functions. `HAVING` filters *groups* after `GROUP BY` has collapsed rows,
and is specifically where aggregate conditions belong (`HAVING sum(quantity) > 3`).
They're frequently combined in one query: `WHERE` trims rows early (cheaper, fewer
rows to group), and `HAVING` trims the resulting groups. A neat illustration of how
distinct these two are: `WHERE genre = 'tech' GROUP BY genre HAVING avg(price) > 40`
can legitimately return **zero rows** even though `WHERE` matched real rows, if the
tech genre's average price doesn't clear 40 — `HAVING` operates purely on the
post-aggregation result.

**Q: A `GROUP BY` query errors with "column must appear in the GROUP BY clause."
Why, and how do you fix it?** ⭐
Every column listed in `SELECT` (that isn't wrapped in an aggregate function) must
also appear in `GROUP BY`. The reason: `GROUP BY` collapses many rows into one output
row per group, so for any un-grouped, non-aggregated column, Postgres would have to
pick one of potentially many different values within that group — an ambiguous choice
it refuses to make silently. The fix is either to add the column to `GROUP BY` (the
common pattern is grouping by a table's primary key plus whatever else you display,
e.g. `GROUP BY a.id, a.name`) or to wrap it in an aggregate like `MAX(col)` if you
genuinely just want *some* representative value.

**Q: `COUNT(*)` vs `COUNT(col)` vs `COUNT(DISTINCT col)`?** ⭐
`COUNT(*)` counts every row in the group, full stop. `COUNT(col)` counts only rows
where `col` is non-NULL — all aggregate functions skip NULLs. `COUNT(DISTINCT col)`
counts the number of distinct non-NULL values of `col`. With the `book` table (6 rows,
one of which has a NULL `author_id`): `count(*) = 6`, `count(author_id) = 5`,
`count(DISTINCT genre) = 2`. This is a favorite trick question precisely because
`COUNT(*)` and `COUNT(col)` look interchangeable until NULLs enter the picture.

**Q: How would you write a query that includes groups with zero matching rows —
e.g. authors with no sales at all — instead of silently dropping them?**
Chain `LEFT JOIN`s all the way down from the table you want fully represented, and
wrap any `SUM`/aggregate you display in `COALESCE(..., 0)` so a group with nothing to
sum shows `0` instead of `NULL`:
```sql
SELECT a.name, coalesce(sum(s.quantity * b.price), 0) AS revenue
FROM author a
LEFT JOIN book b ON b.author_id = a.id
LEFT JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name;
```
If any of those joins were a plain `INNER JOIN` instead, an author with zero books
(or a book with zero sales) would be excluded from the result entirely rather than
shown with a `0`. `count(s.id)` naturally comes out `0` (not NULL) for such a group
because counting zero non-null values legitimately yields `0`.

**Q: Difference between a JOIN and a subquery — when do you use each?**
They're often interchangeable, and the query planner may even execute them
identically after optimization. As a rule of thumb: use a `JOIN` when you need actual
*columns* from the other table in your result; use `EXISTS`/`IN` (a subquery) when
you only need to test *whether a related row exists*, without pulling any of its
columns. `EXISTS` in particular can be faster than an equivalent `JOIN` + `DISTINCT`
because it short-circuits at the first match instead of producing (and then
deduplicating) one output row per match. Use a CTE when the logic naturally breaks
into readable, named steps.

**Q: Why prefer `NOT EXISTS` over `NOT IN` for an anti-join?** ⭐
If the subquery feeding `NOT IN` ever returns even one `NULL`, the *entire*
`NOT IN` predicate silently evaluates to UNKNOWN for every outer row, and the query
returns **zero rows** — no error, just wrong. This happens because `NOT IN (a, b,
NULL)` unfolds to `col <> a AND col <> b AND col <> NULL`, and the last comparison is
always UNKNOWN, poisoning the whole `AND` chain.
```sql
-- book.author_id can legitimately be NULL in this schema — if you did:
SELECT name FROM author WHERE id NOT IN (SELECT author_id FROM book);
-- this could return 0 rows for EVERY author, once a NULL author_id exists

-- NOT EXISTS is unaffected by NULLs in the inner table — the correct anti-join
SELECT name FROM author a
WHERE NOT EXISTS (SELECT 1 FROM book b WHERE b.author_id = a.id);
```
This is one of the most commonly cited "senior SQL" gotchas precisely because it
fails silently rather than with an error.

**Q: What is a CTE, and why would you use one over a subquery?**
A Common Table Expression (`WITH name AS (...)`) is a named subquery scoped to a
single statement. Functionally it's close to a derived table (a subquery in `FROM`),
but it reads top-to-bottom like a sequence of named steps, which is far more readable
for multi-stage logic, and you can reference the same CTE more than once in the outer
query without repeating its SQL. It also enables recursion, which an ordinary
subquery cannot do.
```sql
WITH author_revenue AS (
    SELECT b.author_id, sum(s.quantity * b.price) AS revenue
    FROM sale s JOIN book b ON s.book_id = b.id
    GROUP BY b.author_id
),
ranked AS (
    SELECT a.name, ar.revenue FROM author_revenue ar JOIN author a ON a.id = ar.author_id
)
SELECT * FROM ranked WHERE revenue > 100 ORDER BY revenue DESC;
```

**Q: How does a recursive CTE work? Walk through what it's doing.** ⭐
```sql
WITH RECURSIVE nums(n) AS (
    SELECT 1                             -- anchor
    UNION ALL
    SELECT n + 1 FROM nums WHERE n < 5   -- recursive step
)
SELECT n FROM nums;   -- 1, 2, 3, 4, 5
```
The **anchor** query runs exactly once and seeds the working set. The **recursive**
query then re-executes repeatedly, each time reading the *previous iteration's*
output as `nums`, until an iteration produces zero new rows (here, once `n` hits `5`
and `n < 5` fails), at which point recursion stops. This anchor-plus-self-referencing-
step shape is exactly how org charts, category trees, bill-of-materials explosions,
and graph traversals are expressed in standard SQL without any procedural loop.

**Q: What are window functions, and how do they differ from `GROUP BY`?** ⭐⭐
A window function (`... OVER (...)`) computes a value across a set of rows related to
the current row — but unlike `GROUP BY`, it does **not** collapse those rows. Every
input row survives in the output, with the window's computed value attached alongside
it. `GROUP BY` answers "one summary row per group"; a window function answers "every
detail row, plus a related aggregate/ranking computed over its group." This is why
window functions can do things `GROUP BY` structurally cannot without a self-join,
like showing each book's price next to its genre's average price on the same row.

**Q: `ROW_NUMBER()` vs `RANK()` vs `DENSE_RANK()`?** ⭐⭐
All three number rows within a window ordered by some column, but they disagree on
ties. `ROW_NUMBER()` always assigns unique, strictly sequential numbers (1,2,3,4)
regardless of ties — arbitrary among tied rows. `RANK()` gives tied rows the same
number, then **skips** ahead by the count of tied rows for the next value (e.g. two
rows tied for 2nd, then the next row is ranked 4th: 1,2,2,4). `DENSE_RANK()` gives
tied rows the same number with **no gap** afterward (1,2,2,3). Use `ROW_NUMBER` when
you need to pick exactly one row per group (top-N, dedupe); use `RANK`/`DENSE_RANK`
for genuine leaderboards where you want to represent ties meaningfully — `RANK` if
"1st, 1st, 3rd" matches your domain (like a race), `DENSE_RANK` if you want
consecutive numbers regardless of ties.

**Q: How do you get the top-N rows per group?** ⭐⭐ *the* canonical window-function
interview question
```sql
SELECT title, genre, price
FROM (
    SELECT title, genre, price,
           row_number() OVER (PARTITION BY genre ORDER BY price DESC) AS rn
    FROM book
) ranked
WHERE rn = 1;    -- most expensive book per genre
```
You can't filter on a window function directly in the same query's `WHERE` clause,
because window functions execute *after* `WHERE` in SQL's logical processing order —
so you always compute the rank in a subquery or CTE first, then filter that
computed rank in an outer query. This is precisely the problem plain `GROUP BY`
cannot solve cleanly: `MAX(price)` per genre tells you the max value, but not which
*row* (title) achieved it, without a further self-join back to the original table —
the window-function version avoids that entirely.

**Q: How would you compute a running total, or a month-over-month change?**
Running total — an aggregate window ordered by the running dimension, with an
explicit frame from the start up to the current row:
```sql
SELECT sold_on, amount,
       sum(amount) OVER (ORDER BY sold_on ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM sale_amounts;
```
Month-over-month change — `LAG()` to pull the previous row's value into the current
row, then subtract:
```sql
SELECT month, revenue,
       revenue - lag(revenue) OVER (ORDER BY month) AS change
FROM monthly_revenue;
```
The very first row's `LAG(...)` is `NULL` (no previous row exists), so `change` comes
out `NULL` for the first period — that's expected, not a bug, the same way the first
element of an array has no predecessor.

**Q: Can you filter directly on a window function's result in the same `SELECT`'s
`WHERE` clause? Why or why not?**
No. In SQL's logical evaluation order, `WHERE` runs before `SELECT`, and window
functions are evaluated as part of `SELECT` (after `GROUP BY`/`HAVING`, before
`ORDER BY`). By the time `WHERE` runs, the window function hasn't been computed yet,
so it doesn't exist to filter on. The standard workaround is to compute the window
function in a subquery or CTE and filter its result one query level up, exactly as in
the top-N-per-group pattern above.

**Q: `UNION` vs `UNION ALL`?** ⭐
`UNION` combines the rows of two (compatible) queries and removes duplicates, which
requires an implicit sort/dedupe pass over the combined result. `UNION ALL` combines
the rows and keeps every duplicate, skipping that pass — strictly cheaper. Default to
`UNION ALL` unless you specifically need distinct rows; reaching for plain `UNION`
out of habit is a common, easy-to-miss performance smell in generated/ORM SQL.
Both require the two queries to have the same number of columns with compatible
types, column names taken from the first query — a fundamentally different operation
from a `JOIN`, which combines columns (widens), not rows (lengthens).

**Q: What's the difference between `INTERSECT`/`EXCEPT` and a `JOIN`/`NOT EXISTS`
approach to the same problem?**
`INTERSECT` (rows in both queries) and `EXCEPT` (rows in the first but not the
second) operate on entire *rows* as comparable units across two structurally
compatible queries — useful for reconciling or diffing two datasets that already
have the same shape (e.g. "which customer IDs are in this month's export but not last
month's"). A `JOIN`/`EXISTS`-based equivalent can express the same logic but usually
needs an explicit join/correlation condition per column rather than treating the
whole row as the comparison unit, and can be more natural when you also want extra
columns from one side in the output. For pure set comparison of two same-shaped
result sets, `INTERSECT`/`EXCEPT` are usually the more direct, readable choice.
