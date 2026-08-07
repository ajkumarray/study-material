-- ============================================================================
-- Lesson 1.2 — SELECT, WHERE, ORDER BY, LIMIT, DISTINCT
-- Practical queries. Run 00-setup.sql first, then try these one at a time.
-- ============================================================================

-- 1. SELECT: choose COLUMNS (projection). * = all columns (avoid in real code).
SELECT title, price FROM book;
SELECT * FROM book;

-- 2. WHERE: choose ROWS (selection / filtering).
SELECT title, price FROM book WHERE price > 40;
SELECT title FROM book WHERE genre = 'tech';          -- = is equality (not == )
SELECT title FROM book WHERE in_stock = 0;            -- out of stock

-- Combine conditions with AND / OR / NOT (parenthesize to be explicit):
SELECT title, price, in_stock
FROM book
WHERE genre = 'tech' AND price < 40 AND in_stock > 0;

-- 3. Useful WHERE operators:
SELECT title, price FROM book WHERE price BETWEEN 30 AND 48;   -- inclusive range
SELECT title FROM book WHERE genre IN ('tech', 'fiction');    -- set membership
SELECT title FROM book WHERE title LIKE 'Clean%';             -- pattern: starts with 'Clean'
SELECT title FROM book WHERE title ILIKE '%java%';            -- ILIKE = case-insensitive
SELECT title FROM book WHERE published >= '2015-01-01';       -- date comparison

-- 4. ORDER BY: sort the result (default ASC; DESC for descending).
SELECT title, price FROM book ORDER BY price DESC;
SELECT title, published FROM book ORDER BY published DESC NULLS LAST;  -- control NULL position
-- Multi-key sort: by genre, then price within each genre:
SELECT title, genre, price FROM book ORDER BY genre, price DESC;

-- 5. LIMIT / OFFSET: take a slice (top-N, pagination).
SELECT title, price FROM book ORDER BY price DESC LIMIT 3;             -- 3 most expensive
SELECT title, price FROM book ORDER BY price DESC LIMIT 3 OFFSET 3;    -- "page 2"

-- 6. DISTINCT: remove duplicate rows from the result.
SELECT DISTINCT genre FROM book;                     -- unique genres (incl. NULL once)
SELECT DISTINCT genre, author_id FROM book;          -- distinct COMBINATIONS

-- 7. Column aliases (AS) and computed columns:
SELECT title,
       price,
       price * in_stock AS inventory_value          -- expression + alias
FROM book
WHERE in_stock > 0
ORDER BY inventory_value DESC;

-- ----------------------------------------------------------------------------
-- IMPORTANT — logical order of evaluation (NOT the written order):
--   FROM -> WHERE -> GROUP BY -> HAVING -> SELECT -> DISTINCT -> ORDER BY -> LIMIT
-- This is WHY you can ORDER BY an alias defined in SELECT, but you CANNOT use
-- that alias in WHERE (WHERE runs before SELECT). Try it:
--   SELECT price*in_stock AS v FROM book WHERE v > 100;   -- ERROR: v doesn't exist yet
--   SELECT price*in_stock AS v FROM book ORDER BY v;      -- OK: ORDER BY runs after SELECT
-- ----------------------------------------------------------------------------
