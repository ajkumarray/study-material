-- ============================================================================
-- Lesson 2.2 — Aggregation: GROUP BY, HAVING, aggregate functions
-- Run 00-setup.sql first.
-- ============================================================================

-- Aggregate functions COLLAPSE many rows into one summary value.
-- COUNT, SUM, AVG, MIN, MAX (+ STRING_AGG, ARRAY_AGG in Postgres).

-- 1. Aggregates over the WHOLE table (no GROUP BY -> one row):
SELECT count(*)        AS num_books,
       avg(price)      AS avg_price,
       min(price)      AS cheapest,
       max(price)      AS priciest,
       sum(price)      AS total_list_value
FROM book;

-- 2. GROUP BY — one summary row PER GROUP. "Books and avg price per genre":
SELECT genre, count(*) AS books, round(avg(price), 2) AS avg_price
FROM book
GROUP BY genre;

-- RULE: every column in SELECT must be either in GROUP BY or inside an
-- aggregate. (Otherwise: "column must appear in the GROUP BY clause".)

-- 3. GROUP BY across a JOIN — the real workhorse. Revenue per author:
SELECT a.name AS author,
       count(s.id)                    AS num_sales,
       coalesce(sum(s.quantity), 0)   AS units_sold,
       coalesce(sum(s.quantity * b.price), 0) AS revenue
FROM author a
LEFT JOIN book b ON b.author_id = a.id     -- LEFT so authors with 0 books still show
LEFT JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name                       -- group by id (safe) + name (for SELECT)
ORDER BY revenue DESC;

-- 4. HAVING — filter GROUPS after aggregation (WHERE filters rows BEFORE).
-- "Authors whose books sold more than 3 total units":
SELECT a.name, sum(s.quantity) AS units
FROM author a
JOIN book b ON b.author_id = a.id
JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name
HAVING sum(s.quantity) > 3;          -- can't use an aggregate in WHERE -> use HAVING

-- WHERE vs HAVING together: WHERE trims rows first, HAVING trims groups after.
-- "Among tech books, genres with avg price above 40":
SELECT genre, round(avg(price), 2) AS avg_price
FROM book
WHERE genre = 'tech'                 -- row filter (before grouping)
GROUP BY genre
HAVING avg(price) > 40;              -- group filter (after aggregation)

-- 5. COUNT variants (the classic gotcha, Phase 1.3):
SELECT count(*)              AS all_books,      -- every row
       count(author_id)      AS with_author,    -- non-NULL author_id only
       count(DISTINCT genre) AS distinct_genres -- unique non-NULL genres
FROM book;

-- 6. Postgres aggregate extras — collapse a group into a list:
SELECT a.name AS author, string_agg(b.title, ', ' ORDER BY b.title) AS titles
FROM author a
JOIN book b ON b.author_id = a.id
GROUP BY a.id, a.name;

-- 7. Multi-level grouping + ROLLUP for subtotals (per customer per month):
SELECT c.name AS customer,
       date_trunc('month', s.sold_on)::date AS month,
       sum(s.quantity * b.price) AS revenue
FROM sale s
JOIN book b ON s.book_id = b.id
JOIN customer c ON s.customer_id = c.id
GROUP BY ROLLUP (c.name, date_trunc('month', s.sold_on))   -- adds subtotal + grand-total rows
ORDER BY c.name NULLS LAST, month NULLS LAST;

-- ----------------------------------------------------------------------------
-- Evaluation order (Phase 1.2): FROM -> WHERE -> GROUP BY -> HAVING -> SELECT
-- -> ORDER BY. This is WHY WHERE can't see aggregates (runs before grouping)
-- and HAVING can. Aggregates SKIP NULLs (Phase 1.3), so use COALESCE/COUNT(*)
-- deliberately.
-- ----------------------------------------------------------------------------
