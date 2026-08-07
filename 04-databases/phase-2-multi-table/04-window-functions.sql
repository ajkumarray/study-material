-- ============================================================================
-- Lesson 2.4 — Window functions (the most powerful, most-loved SQL feature)
-- Run 00-setup.sql first.
-- ============================================================================

-- A window function computes across a set of rows RELATED to the current row,
-- but — unlike GROUP BY — WITHOUT collapsing them. You keep every row AND get
-- the aggregate/ranking alongside it. The magic word is OVER (...).

-- 1. Aggregate as a window — show each book's price AND the genre's avg price
-- on the SAME row (GROUP BY couldn't do this without a self-join):
SELECT title, genre, price,
       round(avg(price) OVER (PARTITION BY genre), 2) AS genre_avg,
       round(price - avg(price) OVER (PARTITION BY genre), 2) AS vs_genre_avg
FROM book;
-- PARTITION BY genre = "restart the window per genre" (like GROUP BY, but
-- rows are kept). No PARTITION BY = the whole result is one window.

-- 2. RANKING functions — number rows within a partition.
-- Rank books by price within each genre:
SELECT title, genre, price,
       row_number() OVER w AS row_num,   -- 1,2,3,4 — always unique
       rank()       OVER w AS rank,      -- ties share a rank, then it SKIPS (1,1,3)
       dense_rank() OVER w AS dense_rank -- ties share a rank, NO skip (1,1,2)
FROM book
WINDOW w AS (PARTITION BY genre ORDER BY price DESC);   -- named window (reuse)

-- 3. "Top-N per group" — the classic window-function win. Most expensive book
-- per genre (impossible cleanly with GROUP BY alone):
SELECT title, genre, price
FROM (
    SELECT title, genre, price,
           row_number() OVER (PARTITION BY genre ORDER BY price DESC) AS rn
    FROM book
) ranked
WHERE rn = 1;

-- 4. RUNNING TOTAL — a cumulative sum ordered by date. Sales revenue over time:
SELECT s.sold_on,
       (s.quantity * b.price) AS sale_amount,
       sum(s.quantity * b.price) OVER (ORDER BY s.sold_on
             ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_total
FROM sale s JOIN book b ON s.book_id = b.id
ORDER BY s.sold_on;

-- 5. LAG / LEAD — peek at the PREVIOUS / NEXT row. Month-over-month change:
WITH monthly AS (
    SELECT date_trunc('month', s.sold_on)::date AS month,
           sum(s.quantity * b.price) AS revenue
    FROM sale s JOIN book b ON s.book_id = b.id
    GROUP BY 1
)
SELECT month, revenue,
       lag(revenue) OVER (ORDER BY month) AS prev_month,
       revenue - lag(revenue) OVER (ORDER BY month) AS change   -- NULL for the first month
FROM monthly
ORDER BY month;

-- 6. NTILE / percentiles — bucket rows. Split books into 2 price tiers:
SELECT title, price, ntile(2) OVER (ORDER BY price DESC) AS price_tier
FROM book;

-- 7. First/last in a window — FIRST_VALUE / LAST_VALUE:
SELECT title, genre, price,
       first_value(title) OVER (PARTITION BY genre ORDER BY price DESC) AS priciest_in_genre
FROM book;

-- ----------------------------------------------------------------------------
-- The anatomy of OVER (PARTITION BY ... ORDER BY ... frame):
--   PARTITION BY = which rows share a window (default: all rows)
--   ORDER BY     = order within the window (needed for ranking/running/lag)
--   frame        = ROWS/RANGE BETWEEN ... — which rows around the current one
-- Window functions run AFTER GROUP BY/HAVING, BEFORE ORDER BY. They're the go-to
-- for rankings, running totals, top-N-per-group, and row-to-row comparisons.
-- ----------------------------------------------------------------------------
