-- ============================================================================
-- Lesson 2.3 — Subqueries & CTEs (WITH)
-- Run 00-setup.sql first.
-- ============================================================================

-- A SUBQUERY is a query nested inside another. Three flavors by what it returns.

-- 1. SCALAR subquery — returns ONE value; usable anywhere a value is expected.
-- "Books priced above the average":
SELECT title, price
FROM book
WHERE price > (SELECT avg(price) FROM book);   -- the subquery yields one number

-- Scalar subquery in SELECT (a per-row computed column):
SELECT title, price,
       round(price - (SELECT avg(price) FROM book), 2) AS diff_from_avg
FROM book;

-- 2. Subquery with IN — returns a COLUMN (a list). "Books by USA authors":
SELECT title
FROM book
WHERE author_id IN (SELECT id FROM author WHERE country = 'USA');

-- 3. CORRELATED subquery — references the OUTER row, so it re-runs per row.
-- "Authors who have at least one book" via EXISTS (stops at the first match):
SELECT name
FROM author a
WHERE EXISTS (SELECT 1 FROM book b WHERE b.author_id = a.id);

-- NOT EXISTS — the SAFE anti-join (handles NULLs correctly, unlike NOT IN;
-- Phase 1.3). "Authors with NO books" — 'Silent Author':
SELECT name
FROM author a
WHERE NOT EXISTS (SELECT 1 FROM book b WHERE b.author_id = a.id);

-- WHY prefer NOT EXISTS over NOT IN: if the NOT IN subquery returns any NULL,
-- the whole predicate becomes UNKNOWN and you get ZERO rows (a classic bug).

-- 4. Subquery in FROM (a "derived table") — treat a query result as a table.
-- "Average revenue per sale, per genre":
SELECT genre, round(avg(sale_revenue), 2) AS avg_sale
FROM (
    SELECT b.genre, s.quantity * b.price AS sale_revenue
    FROM sale s JOIN book b ON s.book_id = b.id
) AS per_sale                                   -- derived tables MUST be aliased
GROUP BY genre;

-- ============================================================================
-- CTEs (Common Table Expressions) — WITH ... AS (...). Same power as a derived
-- table, but NAMED and readable, and reusable within the statement. Prefer CTEs
-- for anything non-trivial: they read top-to-bottom like steps.
-- ============================================================================

-- 5. The derived-table query above, rewritten as a CTE (clearer):
WITH per_sale AS (
    SELECT b.genre, s.quantity * b.price AS sale_revenue
    FROM sale s JOIN book b ON s.book_id = b.id
)
SELECT genre, round(avg(sale_revenue), 2) AS avg_sale
FROM per_sale
GROUP BY genre;

-- 6. MULTIPLE CTEs chained — build a pipeline of named steps:
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

-- 7. RECURSIVE CTE — a query that refers to itself; walks hierarchies/sequences.
-- (No hierarchy in our schema, so here's the canonical "generate 1..5":)
WITH RECURSIVE nums(n) AS (
    SELECT 1                       -- anchor: the starting row
    UNION ALL
    SELECT n + 1 FROM nums WHERE n < 5   -- recursive step: build on the previous
)
SELECT n FROM nums;
-- Recursive CTEs power org charts, category trees, and graph traversals in SQL.

-- ----------------------------------------------------------------------------
-- Subquery vs JOIN: often interchangeable; the optimizer may run them
-- identically. Use EXISTS/IN for "does a match exist"; JOIN when you need
-- COLUMNS from the other table; CTEs for readability of multi-step logic.
-- ----------------------------------------------------------------------------
