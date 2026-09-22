<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · foundations](../phase-1-foundations/NOTES.md) | [Phase 3 · schema design ➡](../phase-3-schema-design/NOTES.md)
<!-- /nav -->

# Phase 2 — Querying Multiple Tables & Aggregation: Notes (Theory)

Real data lives in multiple related, normalized tables (Phase 3 explains why). This
phase is about recombining and summarizing that data — the single most common thing
SQL is used for day-to-day, and the richest territory for interview questions.
`00-setup.sql` builds a richer bookstore: `author → book → sale ← customer`.

```sql
-- author (5 rows) — note author 5 writes no books (LEFT JOIN / anti-join demo)
--  id |     name       | country
--   1 | Robert Martin  | USA
--   2 | Martin Fowler  | UK
--   3 | Joshua Bloch   | USA
--   4 | Kathy Sierra   | USA
--   5 | Silent Author  | Canada

-- book (6 rows) — note book 6 has NULL author_id (JOIN demo)
--  id |        title        | author_id | price | genre
--   1 | Clean Code           |     1     | 38.50 | tech
--   2 | Clean Architecture   |     1     | 32.00 | tech
--   3 | Refactoring          |     2     | 47.99 | tech
--   4 | Effective Java       |     3     | 45.00 | tech
--   5 | Head First Java      |     4     | 29.99 | tech
--   6 | An Orphan Book       |   NULL    | 19.99 | fiction

-- customer (4 rows) — note customer 4 (Zoya) buys nothing (LEFT JOIN demo)
-- sale (8 rows) linking book_id + customer_id + quantity + sold_on
```

---

## 1. JOINs — Combining Rows from Multiple Tables

A **JOIN** matches rows from two (or more) tables on a condition, usually
`foreign_key = primary_key`. The **join type** is the whole story — it decides what
happens to rows on either side that *don't* find a match.

### Key Concepts

- **INNER JOIN** (what plain `JOIN` means): keeps only rows that have a match on
  *both* sides — the intersection. Non-matching rows on either side are dropped
  entirely.
- **LEFT [OUTER] JOIN**: keeps *every* row from the left table; where there's no
  match on the right, the right-hand columns come back as `NULL`. This is the
  most common outer join in real code — "give me every X, and its Y if it has one."
- **RIGHT [OUTER] JOIN**: the mirror of LEFT — keeps every row from the right table.
  Rare in practice; people just swap table order and use LEFT instead.
- **FULL OUTER JOIN**: keeps every row from both sides, NULL-filling whichever side
  didn't match — effectively the union of a LEFT and a RIGHT join.
- **CROSS JOIN**: every combination of left rows × right rows (the Cartesian
  product), with no matching condition at all. Rarely wanted on purpose — the
  classic accidental cross join is a JOIN with a *missing or wrong* `ON` condition,
  silently multiplying your row count.
- **SELF JOIN**: a table joined to itself using two different aliases — used for
  comparing rows within the same table (pairs, hierarchies, "find duplicates").
- **Anti-join**: "rows in A that have *no* match in B" — written as a `LEFT JOIN`
  followed by `WHERE right.key IS NULL`, or equivalently `WHERE NOT EXISTS (...)`.

### Worked Examples

```sql
-- INNER JOIN — book 6 (NULL author_id) is silently dropped: 5 rows out
SELECT b.title, a.name AS author
FROM book b
INNER JOIN author a ON b.author_id = a.id;
--        title        |    author
-- ----------------------+---------------
--  Clean Code           | Robert Martin
--  Clean Architecture   | Robert Martin
--  Refactoring          | Martin Fowler
--  Effective Java       | Joshua Bloch
--  Head First Java      | Kathy Sierra
```

```sql
-- LEFT JOIN — every book appears; 'An Orphan Book' gets author = NULL: 6 rows out
SELECT b.title, a.name AS author
FROM book b
LEFT JOIN author a ON b.author_id = a.id;
-- ... same 5 rows as above, PLUS:
--  An Orphan Book       | NULL
```

```sql
-- RIGHT JOIN — every author appears; 'Silent Author' gets title = NULL: 6 rows out
SELECT b.title, a.name AS author
FROM book b
RIGHT JOIN author a ON b.author_id = a.id;
-- ... same 5 matched rows, PLUS:
--  NULL                 | Silent Author
```

```sql
-- FULL OUTER JOIN — everything from both sides: 7 rows out
SELECT b.title, a.name AS author
FROM book b
FULL OUTER JOIN author a ON b.author_id = a.id;
-- the 5 matched rows + the orphan book (author NULL) + Silent Author (title NULL)
```

In these four queries, the join *condition* (`b.author_id = a.id`) never changes —
only the join *type* changes which unmatched rows survive. This is the single most
important mental model for this topic.

```sql
-- Chaining joins across 3 tables — revenue per sale
SELECT c.name AS customer, b.title, s.quantity, (s.quantity * b.price) AS revenue
FROM sale s
JOIN book b     ON s.book_id = b.id
JOIN customer c ON s.customer_id = c.id
ORDER BY revenue DESC;
--  customer |        title        | quantity | revenue
-- ----------+----------------------+----------+---------
--  Ravi     | Refactoring          |    3     | 143.97
--  Ajay     | Clean Code           |    2     |  77.00
--  Meera    | Head First Java      |    2     |  59.98
--  Ajay     | Effective Java       |    1     |  45.00
--  Meera    | Effective Java       |    1     |  45.00
--  Meera    | Clean Code           |    1     |  38.50
--  Ravi     | Clean Code           |    1     |  38.50
--  Ajay     | Clean Architecture   |    1     |  32.00
```
Two `JOIN`s in one query is completely ordinary — `sale` is the "fact" table that
links to two different "dimension" tables (`book`, `customer`), each joined
independently on its own FK.

```sql
-- SELF JOIN — pairs of authors sharing a country; a1.id < a2.id avoids
-- self-pairs (author paired with itself) and mirror duplicates (A,B) and (B,A)
SELECT a1.name AS author_1, a2.name AS author_2, a1.country
FROM author a1
JOIN author a2 ON a1.country = a2.country AND a1.id < a2.id;
--    author_1     |   author_2    | country
-- ------------------+---------------+---------
--  Robert Martin    | Joshua Bloch  | USA
--  Robert Martin    | Kathy Sierra  | USA
--  Joshua Bloch     | Kathy Sierra  | USA
```
Three USA authors (`Robert Martin`, `Joshua Bloch`, `Kathy Sierra`) produce
3 pairs (3 choose 2). `a1.id < a2.id` is the standard trick for turning a symmetric
self-join into each unordered pair exactly once.

```sql
-- Anti-join — books that have NEVER sold
SELECT b.title
FROM book b
LEFT JOIN sale s ON s.book_id = b.id
WHERE s.id IS NULL;
--      title
-- ------------------
--  An Orphan Book
```
The idiom is: `LEFT JOIN` to keep every left row even without a match, then filter
`WHERE right.some_not_null_column IS NULL` — that condition can only be true for the
left rows that found *no* match at all.

### Comparison Table

| Join type | Keeps | Non-matches become | Typical use |
|---|---|---|---|
| `INNER JOIN` | rows matching both sides | dropped entirely | "only give me complete pairs" |
| `LEFT JOIN` | all left rows | right columns NULL | "every X, with Y if it exists" |
| `RIGHT JOIN` | all right rows | left columns NULL | rare — flip to LEFT instead |
| `FULL OUTER JOIN` | all rows, either side | opposite side NULL | reconciliation / diffing two sets |
| `CROSS JOIN` | every combination | n/a (no match condition) | generating combinations; usually a bug if accidental |
| self join | rows from one table compared to itself | n/a | hierarchies, duplicate pairs, sequences |

### The ON vs WHERE Subtlety (Interview Favorite)

With an outer join, a condition on the *right-hand* table matters hugely depending on
whether you put it in `ON` or `WHERE`:
```sql
-- Filters the MATCH itself — orphan book still appears, with author NULL
SELECT b.title, a.name
FROM book b LEFT JOIN author a ON b.author_id = a.id AND a.country = 'USA';

-- Filters the RESULT after the join — silently turns the LEFT JOIN back into
-- an effective INNER JOIN, because rows where a.country IS NULL get dropped
-- by the WHERE clause (NULL = 'USA' is UNKNOWN, not TRUE — Phase 1.3)
SELECT b.title, a.name
FROM book b LEFT JOIN author a ON b.author_id = a.id
WHERE a.country = 'USA';
```
Putting the right-table condition in `ON` keeps unmatched left rows; putting it in
`WHERE` silently discards them. This single subtlety causes more "why is my LEFT
JOIN dropping rows" production bugs than almost anything else in SQL.

### Why It's Useful

Multi-table joins are how a normalized schema (Phase 3) — data split across many
narrow tables specifically to avoid duplication — gets reassembled into the wide,
denormalized shape an application or report actually needs. Every dashboard query,
every "show me order + customer + product" API response, and every analytics report
is built on joins.

### Summary / Key Takeaways

- INNER = intersection, LEFT = all-left-plus-matches, FULL = union — memorize this
  three-way mental model first.
- The anti-join idiom (`LEFT JOIN ... WHERE right.col IS NULL`) is how you find
  "rows with no match" — a very common real-world question.
- Right-table conditions belong in `ON` for outer joins; putting them in `WHERE`
  can silently convert a LEFT JOIN into an INNER JOIN.
- A self join is just a normal join where both tables happen to be the same table,
  aliased twice.

---

## 2. Aggregation — GROUP BY, HAVING, and Aggregate Functions

**Aggregate functions** collapse many rows down into one summary value per group:
`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`, plus Postgres extras like `STRING_AGG` and
`ARRAY_AGG`. **`GROUP BY`** is what defines the groups those functions collapse into.

### Key Concepts

- **Aggregate functions skip NULLs** (Phase 1.3) — `COUNT(*)` counts rows regardless
  of NULLs, `COUNT(col)` counts only rows where `col` is non-NULL, `AVG`/`SUM` ignore
  NULL values entirely rather than treating them as zero.
- **`GROUP BY`** produces one output row per distinct combination of the grouped
  columns. Every table row is bucketed into exactly one group.
- **The GROUP BY rule**: every column in `SELECT` must either appear in `GROUP BY`
  or be wrapped inside an aggregate function — otherwise Postgres can't decide which
  of the many per-group row values to display, and errors with `column "..." must
  appear in the GROUP BY clause or be used in an aggregate function`.
- **`HAVING`** filters *groups* after aggregation (can use aggregate conditions like
  `HAVING sum(x) > 100`); **`WHERE`** filters *rows* before grouping (cannot use
  aggregates). They're frequently used together in the same query.
- **`ROLLUP`/`CUBE`/`GROUPING SETS`** add extra subtotal/grand-total rows to a
  grouped result in one query, instead of running several separate aggregate queries
  and `UNION`-ing them together.

### Worked Examples

```sql
-- Aggregates with NO GROUP BY collapse the WHOLE table into one row
SELECT count(*) AS num_books, avg(price) AS avg_price,
       min(price) AS cheapest, max(price) AS priciest, sum(price) AS total_list_value
FROM book;
--  num_books | avg_price | cheapest | priciest | total_list_value
-- -----------+-----------+----------+----------+-------------------
--      6     |  35.58    |  19.99   |  47.99   |      213.47
```

```sql
-- GROUP BY — one row per genre
SELECT genre, count(*) AS books, round(avg(price), 2) AS avg_price
FROM book
GROUP BY genre;
--  genre  | books | avg_price
-- --------+-------+-----------
--  tech   |   5   |   38.70
--  fiction|   1   |   19.99
```

```sql
-- GROUP BY across a JOIN — the real, everyday workhorse: revenue per author
SELECT a.name AS author,
       count(s.id)                            AS num_sales,
       coalesce(sum(s.quantity), 0)           AS units_sold,
       coalesce(sum(s.quantity * b.price), 0) AS revenue
FROM author a
LEFT JOIN book b ON b.author_id = a.id     -- LEFT: keep authors with zero books
LEFT JOIN sale s ON s.book_id = b.id       -- LEFT: keep books with zero sales
GROUP BY a.id, a.name
ORDER BY revenue DESC;
--      author      | num_sales | units_sold | revenue
-- -------------------+-----------+------------+---------
--  Robert Martin     |     4     |     5      | 186.00
--  Martin Fowler     |     1     |     3      | 143.97
--  Joshua Bloch      |     2     |     2      |  90.00
--  Kathy Sierra      |     1     |     2      |  59.98
--  Silent Author     |     0     |     0      |   0.00
```
Two things make this row correct for `Silent Author`: `LEFT JOIN` all the way down
keeps them in the result even though they have zero books and zero sales, and
`COALESCE(sum(...), 0)` converts what would otherwise be a NULL (there's nothing to
sum) into a display-friendly `0`. `count(s.id)` naturally comes out `0` (not NULL)
because `COUNT` of an all-NULL group of values is `0` by definition.

```sql
-- HAVING — filter GROUPS after aggregation. "Authors whose books sold > 3 units"
-- (this version uses plain JOIN, so authors with no books/sales aren't candidates)
SELECT a.name, sum(s.quantity) AS units
FROM author a
JOIN book b ON b.author_id = a.id
JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name
HAVING sum(s.quantity) > 3;
--      name       | units
-- ------------------+-------
--  Robert Martin    |   5
```
Only Robert Martin clears the `> 3` bar (Martin Fowler and Joshua Bloch both land at
exactly the threshold or below). Note `sum(s.quantity)` in `HAVING` — that's exactly
the kind of aggregate condition `WHERE` cannot express.

```sql
-- WHERE + HAVING together — WHERE trims rows first, HAVING trims groups after
SELECT genre, round(avg(price), 2) AS avg_price
FROM book
WHERE genre = 'tech'          -- row filter, applied before grouping
GROUP BY genre
HAVING avg(price) > 40;       -- group filter, applied after aggregation
--  (0 rows)
```
This one is a useful gotcha to see once: `tech`'s average price is `38.70`, which
does **not** clear `> 40`, so `HAVING` filters out the only group `WHERE` produced —
the query legitimately returns zero rows even though `WHERE genre = 'tech'` matched 5
real books. `HAVING` operates on the *aggregated* result, not the raw rows.

```sql
-- The COUNT variants gotcha again, now with real data
SELECT count(*) AS all_books, count(author_id) AS with_author,
       count(DISTINCT genre) AS distinct_genres
FROM book;
--  all_books | with_author | distinct_genres
-- -----------+-------------+------------------
--      6     |      5      |        2
```
`count(*) = 6` (all rows), `count(author_id) = 5` (book 6's `author_id` is NULL and
gets skipped), `count(DISTINCT genre) = 2` (`tech`, `fiction`).

```sql
-- Postgres extra: collapse a group's values into a delimited list
SELECT a.name AS author, string_agg(b.title, ', ' ORDER BY b.title) AS titles
FROM author a
JOIN book b ON b.author_id = a.id
GROUP BY a.id, a.name;
--     author       |               titles
-- -------------------+--------------------------------------
--  Robert Martin     | Clean Architecture, Clean Code
--  Martin Fowler     | Refactoring
--  Joshua Bloch      | Effective Java
--  Kathy Sierra      | Head First Java
```

```sql
-- ROLLUP — adds subtotal + grand-total rows automatically
SELECT c.name AS customer,
       date_trunc('month', s.sold_on)::date AS month,
       sum(s.quantity * b.price) AS revenue
FROM sale s
JOIN book b ON s.book_id = b.id
JOIN customer c ON s.customer_id = c.id
GROUP BY ROLLUP (c.name, date_trunc('month', s.sold_on))
ORDER BY c.name NULLS LAST, month NULLS LAST;
-- one row per (customer, month), PLUS a per-customer subtotal row (month = NULL),
-- PLUS a single grand-total row (customer = NULL AND month = NULL)
```
`ROLLUP` is the difference between writing one query and writing three (per-group,
per-parent-group, and grand-total) and `UNION ALL`-ing them together yourself — the
NULL in the `month`/`customer` column on a subtotal row is how you tell a subtotal
row apart from a normal detail row.

### Why It's Useful

`GROUP BY` + `JOIN` is the backbone of virtually every analytics/reporting query:
"revenue by region," "orders per customer this month," "average rating per product."
Interviewers ask about it constantly because the WHERE/HAVING/NULL interactions are
easy to get subtly wrong, and getting them right signals real SQL fluency rather than
memorized syntax.

### Summary / Key Takeaways

- Every non-aggregated `SELECT` column must be in `GROUP BY` — this is the #1 error
  message beginners hit.
- `WHERE` filters rows before grouping (no aggregates allowed); `HAVING` filters
  groups after aggregation (aggregates required to be meaningful).
- Aggregates skip NULLs; `COUNT(*)` doesn't. Combine `LEFT JOIN` with
  `COALESCE(sum(...), 0)` to include groups with zero matches instead of losing them.
- `ROLLUP`/`CUBE`/`GROUPING SETS` generate subtotal rows in one query instead of
  several `UNION`-ed queries.

---

## 3. Subqueries & CTEs

A **subquery** is a query nested inside another query. What it *returns* determines
where you're allowed to use it. A **CTE** (`WITH name AS (...)`) is a named,
reusable version of the same idea, scoped to a single statement.

### Key Concepts

- **Scalar subquery**: returns exactly one value (one row, one column) — usable
  anywhere a single value is expected, including inside `WHERE`, `SELECT`, or an
  arithmetic expression.
- **Column/`IN` subquery**: returns a list of values (one column, many rows) —
  usable with `IN`/`NOT IN`.
- **Correlated subquery**: references a column from the *outer* query, so it's
  logically re-evaluated once per outer row — typically paired with `EXISTS`/
  `NOT EXISTS`, which short-circuit at the first match/non-match instead of
  counting every match.
- **Derived table**: a subquery used in `FROM`, treated as if it were a table —
  must be given an alias.
- **CTE (`WITH name AS (...)`)**: a named subquery, readable top-to-bottom like a
  sequence of steps, that can be referenced (and in Postgres, reused) within the
  same statement.
- **`WITH RECURSIVE`**: a CTE that refers to itself — an *anchor* query
  (`UNION ALL`) combined with a *recursive* step that builds on the previous
  iteration's output, used to walk hierarchies, trees, and generate sequences.

### Worked Examples

```sql
-- Scalar subquery in WHERE — "books priced above the average"
SELECT title, price
FROM book
WHERE price > (SELECT avg(price) FROM book);   -- subquery yields ONE number: 35.58
--        title        | price
-- ----------------------+-------
--  Clean Code           | 38.50
--  Refactoring          | 47.99
--  Effective Java       | 45.00
```

```sql
-- Scalar subquery in SELECT — a computed column comparing each row to the average
SELECT title, price,
       round(price - (SELECT avg(price) FROM book), 2) AS diff_from_avg
FROM book;
```

```sql
-- IN subquery — "books by USA authors" (subquery returns a column/list)
SELECT title
FROM book
WHERE author_id IN (SELECT id FROM author WHERE country = 'USA');
--        title
-- ----------------------
--  Clean Code
--  Clean Architecture
--  Effective Java
--  Head First Java
```

```sql
-- Correlated subquery with EXISTS — "authors who have at least one book"
SELECT name
FROM author a
WHERE EXISTS (SELECT 1 FROM book b WHERE b.author_id = a.id);
--      name
-- ------------------
--  Robert Martin
--  Martin Fowler
--  Joshua Bloch
--  Kathy Sierra
```
`b.author_id = a.id` references `a`, the *outer* query's current row — that's what
makes this correlated. `EXISTS` only needs to find one matching book per author and
stops (`SELECT 1` is idiomatic since the actual selected value is discarded).

```sql
-- NOT EXISTS — the SAFE anti-join. "Authors with NO books"
SELECT name
FROM author a
WHERE NOT EXISTS (SELECT 1 FROM book b WHERE b.author_id = a.id);
--      name
-- ------------------
--  Silent Author
```
Prefer `NOT EXISTS` over `NOT IN` for this: if the `NOT IN` subquery's result set
ever contains a NULL (e.g. `book.author_id` legitimately has NULLs in this schema),
every `<>` comparison against that NULL evaluates to UNKNOWN, and the entire `NOT IN`
predicate silently returns **zero rows** for every outer row — a classic, hard-to-spot
bug (Phase 1.3). `NOT EXISTS` tests row existence, not value equality, so it isn't
affected by NULLs in the inner table at all.

```sql
-- Derived table — a subquery used as a FROM source, must be aliased
SELECT genre, round(avg(sale_revenue), 2) AS avg_sale
FROM (
    SELECT b.genre, s.quantity * b.price AS sale_revenue
    FROM sale s JOIN book b ON s.book_id = b.id
) AS per_sale
GROUP BY genre;
```

```sql
-- The same query, rewritten as a CTE — same result, much more readable
WITH per_sale AS (
    SELECT b.genre, s.quantity * b.price AS sale_revenue
    FROM sale s JOIN book b ON s.book_id = b.id
)
SELECT genre, round(avg(sale_revenue), 2) AS avg_sale
FROM per_sale
GROUP BY genre;
```

```sql
-- Multiple chained CTEs — a readable pipeline of named steps
WITH author_revenue AS (
    SELECT b.author_id, sum(s.quantity * b.price) AS revenue
    FROM sale s JOIN book b ON s.book_id = b.id
    GROUP BY b.author_id
),
ranked AS (
    SELECT a.name, ar.revenue
    FROM author_revenue ar JOIN author a ON a.id = ar.author_id
)
SELECT * FROM ranked WHERE revenue > 100 ORDER BY revenue DESC;
--      name       | revenue
-- ------------------+---------
--  Robert Martin    | 186.00
--  Martin Fowler    | 143.97
```

```sql
-- WITH RECURSIVE — generate a number sequence 1..5 (the canonical minimal example)
WITH RECURSIVE nums(n) AS (
    SELECT 1                             -- anchor: the starting row(s)
    UNION ALL
    SELECT n + 1 FROM nums WHERE n < 5   -- recursive step: builds on previous output
)
SELECT n FROM nums;
--  n
-- ---
--  1
--  2
--  3
--  4
--  5
```
The anchor runs once and seeds the recursion; the recursive term then re-runs
against the *previous iteration's* output (`nums`) until it produces zero new rows
(here, once `n = 5` fails `n < 5`). The exact same shape — anchor + recursive step
referencing the parent row — is what powers org-chart/category-tree/graph-traversal
queries in SQL.

### Comparison Table

| Approach | Returns | Typical use |
|---|---|---|
| Scalar subquery | one value | comparing a row to an aggregate (`price > (SELECT avg...)`) |
| `IN` subquery | a list | "is this value among these?" |
| Correlated + `EXISTS` | boolean, per outer row | "does a related row exist?" — stops at first match |
| Derived table (`FROM (...)`) | a table | reusing a computed result once |
| CTE (`WITH ...`) | a table | multi-step pipelines, readability, recursion |
| `JOIN` | combined rows | you need actual columns from the other table |

### Why It's Useful

Subqueries and CTEs let you build up complex logic as small, named, testable steps
rather than one unreadable nested query — and `WITH RECURSIVE` is the only
standard-SQL way to walk a variable-depth hierarchy (categories, org charts, bill-of-
materials, folder trees, transitive permission graphs) without procedural code.

### Summary / Key Takeaways

- A subquery vs a join are often interchangeable — the query planner may execute
  them identically — so choose whichever reads more clearly for the task.
- Use `EXISTS`/`NOT EXISTS` when you only need to test for a match; use `JOIN` when
  you need columns from the other table.
- `NOT EXISTS` is the NULL-safe replacement for `NOT IN` — prefer it for anti-joins.
- CTEs are named, readable, reusable subqueries; `WITH RECURSIVE` walks hierarchies
  and sequences via an anchor query plus a self-referencing recursive step.

---

## 4. Window Functions — the "Crown Jewel" of SQL

A **window function** computes a value across a *set of rows related to the current
row* — but, unlike `GROUP BY`, it does **not** collapse those rows into one. Every
input row survives, and you get the aggregate/ranking sitting right alongside it.
This is something `GROUP BY` fundamentally cannot do without an awkward self-join.

### Key Concepts

- **`OVER (...)`**: the syntax that turns an ordinary aggregate function (or a
  ranking function) into a window function.
- **`PARTITION BY`**: which rows share a window — conceptually like `GROUP BY`,
  except rows aren't collapsed. Omit it and the whole result set is one window.
- **`ORDER BY`** (inside `OVER`): the ordering *within* the window — required for
  ranking functions, running totals, and `LAG`/`LEAD`.
- **Frame clause** (`ROWS`/`RANGE BETWEEN ... AND ...`): which rows *around* the
  current row participate — e.g. `UNBOUNDED PRECEDING AND CURRENT ROW` for a running
  total.
- **Ranking functions**: `ROW_NUMBER()` (always unique, 1..n), `RANK()` (ties share
  a rank, then the next rank *skips* ahead), `DENSE_RANK()` (ties share a rank, no
  gap in the next rank).
- **Offset functions**: `LAG()`/`LEAD()` — read a value from the previous/next row
  in the window's order (deltas, month-over-month change).
- **Aggregate windows**: `SUM`/`AVG`/`COUNT`/... `OVER (...)` — running totals, or a
  group's aggregate repeated on every row of that group.
- **`NTILE(n)`**: splits the partition into `n` roughly-equal buckets (quartiles,
  price tiers).
- **`FIRST_VALUE`/`LAST_VALUE`**: the first/last value in the window's frame.
- Window functions run **after** `GROUP BY`/`HAVING` but **before** `ORDER BY` in
  logical query order — so you can `ORDER BY` a window function's result.

### Worked Examples

```sql
-- Aggregate as a WINDOW — each book's price AND its genre's average, same row
SELECT title, genre, price,
       round(avg(price) OVER (PARTITION BY genre), 2) AS genre_avg,
       round(price - avg(price) OVER (PARTITION BY genre), 2) AS vs_genre_avg
FROM book;
--        title        | genre  | price | genre_avg | vs_genre_avg
-- ----------------------+--------+-------+-----------+---------------
--  Clean Code           | tech   | 38.50 |   38.70   |    -0.20
--  Clean Architecture   | tech   | 32.00 |   38.70   |    -6.70
--  Refactoring          | tech   | 47.99 |   38.70   |     9.29
--  Effective Java       | tech   | 45.00 |   38.70   |     6.30
--  Head First Java      | tech   | 29.99 |   38.70   |    -8.71
--  An Orphan Book       | fiction| 19.99 |   19.99   |     0.00
```
Compare this to `GROUP BY genre`: that would collapse to just 2 rows (one per
genre). Here, all 6 book rows survive, each carrying its genre's average alongside
its own price — exactly what a report that needs both the detail row *and* a group
statistic requires.

```sql
-- Ranking functions — rank books by price within each genre
SELECT title, genre, price,
       row_number() OVER w AS row_num,
       rank()       OVER w AS rank,
       dense_rank() OVER w AS dense_rank
FROM book
WINDOW w AS (PARTITION BY genre ORDER BY price DESC);   -- named window, reused 3x
--        title        | genre |  price | row_num | rank | dense_rank
-- ----------------------+-------+--------+---------+------+------------
--  Refactoring          | tech  | 47.99  |    1    |  1   |    1
--  Effective Java       | tech  | 45.00  |    2    |  2   |    2
--  Clean Code           | tech  | 38.50  |    3    |  3   |    3
--  Clean Architecture   | tech  | 32.00  |    4    |  4   |    4
--  Head First Java      | tech  | 29.99  |    5    |  5   |    5
--  An Orphan Book       | fiction| 19.99 |    1    |  1   |    1
```
With no price ties in this dataset all three columns match here — the difference
only shows up with tied values: if two books tied for 2nd place, `ROW_NUMBER` would
still give them `2` and `3` (arbitrarily), `RANK` would give both `2` then the next
row `4` (skipping 3), and `DENSE_RANK` would give both `2` then the next row `3` (no
gap).

```sql
-- Top-N per group — the single most common real-world use of window functions.
-- "Most expensive book per genre" — impossible to do cleanly with plain GROUP BY.
SELECT title, genre, price
FROM (
    SELECT title, genre, price,
           row_number() OVER (PARTITION BY genre ORDER BY price DESC) AS rn
    FROM book
) ranked
WHERE rn = 1;
--        title        | genre  | price
-- ----------------------+--------+-------
--  Refactoring          | tech   | 47.99
--  An Orphan Book       | fiction| 19.99
```
You cannot filter directly on a window function in `WHERE` (window functions run
*after* `WHERE` in logical order), which is why this always needs a wrapping
subquery or CTE — compute the rank first, then filter on it one level up.

```sql
-- Running total — a cumulative sum ordered by date
SELECT s.sold_on, (s.quantity * b.price) AS sale_amount,
       sum(s.quantity * b.price) OVER (ORDER BY s.sold_on
             ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM sale s JOIN book b ON s.book_id = b.id
ORDER BY s.sold_on;
--  sold_on    | sale_amount | running_total
-- ------------+-------------+----------------
--  2026-01-10 |   77.00     |    77.00
--  2026-01-12 |   38.50     |   115.50
--  2026-02-01 |   45.00     |   160.50
--  2026-02-05 |  143.97     |   304.47
--  2026-02-20 |   38.50     |   342.97
--  2026-03-01 |   59.98     |   402.95
--  2026-03-03 |   45.00     |   447.95
--  2026-03-15 |   32.00     |   479.95
```

```sql
-- LAG — month-over-month revenue change
WITH monthly AS (
    SELECT date_trunc('month', s.sold_on)::date AS month,
           sum(s.quantity * b.price) AS revenue
    FROM sale s JOIN book b ON s.book_id = b.id
    GROUP BY 1
)
SELECT month, revenue,
       lag(revenue) OVER (ORDER BY month) AS prev_month,
       revenue - lag(revenue) OVER (ORDER BY month) AS change
FROM monthly
ORDER BY month;
--   month    | revenue | prev_month | change
-- -----------+---------+------------+---------
--  2026-01-01|  115.50 |    NULL    |  NULL
--  2026-02-01|  227.47 |   115.50   |  111.97
--  2026-03-01|  136.98 |   227.47   |  -90.49
```
`change` is `NULL` for the very first month — there's no previous row for `LAG` to
look at, exactly analogous to how the first element of an array has no predecessor.

```sql
-- NTILE — split books into 2 price tiers
SELECT title, price, ntile(2) OVER (ORDER BY price DESC) AS price_tier
FROM book;
```

### Comparison Table

| Function | Behavior on ties | Use case |
|---|---|---|
| `ROW_NUMBER()` | always unique (1,2,3,4,5) | pick exactly one row per group (top-N, dedupe) |
| `RANK()` | ties share, then skips (1,1,3) | leaderboards where tied entries share a place |
| `DENSE_RANK()` | ties share, no gap (1,1,2) | leaderboards where you want consecutive rank numbers |
| `LAG`/`LEAD` | n/a | compare a row to the previous/next row in order |
| `SUM(...) OVER` | n/a | running totals, per-group totals shown on every row |

### Why It's Useful

Window functions solve exactly the class of problem that trips up people who only
know `GROUP BY`: rankings, running totals, "top N per group," period-over-period
comparisons, and moving averages. They're heavily tested in interviews because they
separate people who've internalized SQL from people who've only memorized `SELECT`/
`JOIN`/`GROUP BY`.

### Summary / Key Takeaways

- Window functions keep every row while adding an aggregate/ranking computed over a
  related set of rows — `GROUP BY` collapses rows, `OVER (...)` does not.
- `PARTITION BY` = grouping without collapsing; `ORDER BY` inside `OVER` = ordering
  within the window (needed for ranking/running/offset functions).
- `ROW_NUMBER` = always unique; `RANK` = ties skip ahead; `DENSE_RANK` = ties, no gap.
- Top-N-per-group is the canonical window-function interview question: rank in a
  subquery/CTE, then filter the rank in an outer query.
- Window functions run after `GROUP BY`/`HAVING`, before `ORDER BY` — you can't
  filter on one directly in the same-level `WHERE`.

---

## 5. Set Operations — UNION, INTERSECT, EXCEPT

Set operations combine the **rows** of two (or more) queries, stacking them
vertically. This is fundamentally different from a `JOIN`, which combines
**columns** horizontally by matching rows across tables.

### Key Concepts

- Both queries must return the **same number of columns**, with **compatible
  types** column-by-column. Result column names come from the *first* query.
- **`UNION`**: all rows from both queries, with **duplicates removed** (requires an
  implicit sort/dedupe pass).
- **`UNION ALL`**: all rows from both queries, **keeping duplicates** — cheaper,
  since there's no dedupe pass. Prefer it unless you specifically need distinct rows.
- **`INTERSECT`**: only rows that appear in *both* queries.
- **`EXCEPT`** (Oracle calls this `MINUS`): rows in the first query that do **not**
  appear in the second.
- A single `ORDER BY` goes once, at the very end, and orders the *combined* result.

### Worked Examples

```sql
-- UNION — distinct combined list of two single-column result sets
SELECT city FROM customer
UNION
SELECT country FROM author;
-- combined, deduplicated list of every city + every country value
```

```sql
-- UNION ALL — faster, keeps duplicates, lets you tag which query a row came from
SELECT 'customer city' AS kind, city FROM customer
UNION ALL
SELECT 'author country', country FROM author;
--       kind        |  city/country
-- --------------------+----------------
--  customer city      | Pune
--  customer city      | Mumbai
--  customer city      | Pune
--  customer city      | Delhi
--  author country     | USA
--  author country     | UK
--  author country     | USA
--  author country     | USA
--  author country     | Canada
```
Notice `Pune` and `USA` each appear twice with `UNION ALL` (their real duplicate
count preserved) — `UNION` would have collapsed each to one row.

```sql
-- INTERSECT — rows appearing in BOTH queries
SELECT city FROM customer
INTERSECT
SELECT country FROM author;
-- (0 rows here — no value is both a customer city and an author's country)
```

```sql
-- EXCEPT — rows in the first query, absent from the second
SELECT genre FROM book
EXCEPT
SELECT 'fiction';
--  genre
-- -------
--  tech
```

```sql
-- A practical combo — label every book sold vs unsold using set operations
-- (compare with the LEFT JOIN anti-join version in section 1)
SELECT id AS book_id, 'sold' AS status FROM book
  WHERE id IN (SELECT book_id FROM sale)
UNION ALL
SELECT id, 'unsold' FROM book
  WHERE id NOT IN (SELECT book_id FROM sale WHERE book_id IS NOT NULL)
ORDER BY book_id;
--  book_id | status
-- ---------+---------
--     1    |  sold
--     2    |  sold
--     3    |  sold
--     4    |  sold
--     5    |  sold
--     6    | unsold
```
Note the defensive `WHERE book_id IS NOT NULL` inside the second branch's subquery —
without it, if `sale.book_id` ever legitimately contained a NULL, the outer
`NOT IN` would hit the exact NULL-poisoning bug from section 3 and silently return
zero rows for the "unsold" branch.

### Comparison Table

| Operation | Result | Duplicate-safe variant |
|---|---|---|
| `UNION` | rows in either, deduplicated | `UNION ALL` (keeps dupes, faster) |
| `INTERSECT` | rows in both | `INTERSECT ALL` |
| `EXCEPT` | rows in first, not in second | `EXCEPT ALL` |
| `JOIN` | combined columns (horizontal) | n/a — a completely different operation |

### Why It's Useful

Set operations are how you combine results from structurally different queries into
one report (e.g. "all cities we operate in, from two separate source tables"), or
diff two versions of a dataset (`EXCEPT` is a natural "what changed" tool: rows in
the new snapshot but not the old one).

### Summary / Key Takeaways

- `UNION` stacks rows and dedupes (costs a sort); `UNION ALL` stacks rows and keeps
  everything (cheaper) — default to `UNION ALL` unless you need distinct rows.
- `INTERSECT` = rows in both; `EXCEPT` = rows in the first but not the second.
- Column count and types must line up between the queries being combined; names come
  from the first query.
- `JOIN` widens (combines columns); set operations lengthen (combine rows) — they
  solve different problems and are not interchangeable.
