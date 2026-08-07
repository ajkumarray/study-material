-- ============================================================================
-- Lesson 1.4 — DML: INSERT, UPDATE, DELETE, RETURNING
-- DML = Data Manipulation Language (changes ROWS).
-- (Contrast DDL = Data Definition Language: CREATE/ALTER/DROP — changes STRUCTURE.)
-- ============================================================================

-- 1. INSERT — add rows.
INSERT INTO author (name, country) VALUES ('Erich Gamma', 'Switzerland');

-- Multi-row insert (one statement, faster than many):
INSERT INTO book (title, author_id, price, genre, published, in_stock) VALUES
    ('Design Patterns', 6, 49.99, 'tech', '1994-10-31', 4),
    ('Test Driven Dev', 3, 39.99, 'tech', '2002-11-08', 6);

-- RETURNING — get back values the DB generated (e.g. the SERIAL id) WITHOUT
-- a second query. This is what JDBC's getGeneratedKeys() did under the hood:
INSERT INTO author (name, country)
VALUES ('New Author', 'India')
RETURNING id, name;

-- 2. UPDATE — modify existing rows. ALWAYS include a WHERE, or you update ALL rows.
UPDATE book SET price = price * 1.10 WHERE genre = 'tech';   -- 10% price rise on tech
UPDATE book SET in_stock = in_stock + 5 WHERE title = 'Clean Code';

-- Update multiple columns at once, with RETURNING to confirm:
UPDATE book
SET price = 19.99, genre = 'fiction'
WHERE title = 'An Untitled Draft'
RETURNING id, title, price, genre;

-- 3. DELETE — remove rows. ALSO always WHERE (or you empty the table).
DELETE FROM book WHERE in_stock = 0 AND genre = 'fiction';
DELETE FROM book WHERE published < '2006-01-01' RETURNING title;  -- see what went

-- 4. UPSERT — insert, or update if the row already exists (ON CONFLICT).
-- Requires a UNIQUE/PK to detect the conflict. Hugely useful for "save":
-- (illustrative — assumes a unique constraint on book.title)
-- INSERT INTO book (title, author_id, price)
-- VALUES ('Clean Code', 1, 41.00)
-- ON CONFLICT (title) DO UPDATE SET price = EXCLUDED.price;
--   EXCLUDED = the row you tried to insert.

-- 5. TRUNCATE — delete ALL rows fast (no per-row work, resets the table).
-- TRUNCATE book;      -- (careful! empties the whole table)

-- ----------------------------------------------------------------------------
-- SAFETY HABITS:
--  * Write the WHERE first, run it as a SELECT to see which rows match,
--    THEN change SELECT to UPDATE/DELETE. Catches "oops, all rows" disasters.
--  * DML runs inside a transaction (Phase 5). Until you COMMIT, you can
--    ROLLBACK. In psql: BEGIN; <your DELETE>; -- inspect --; ROLLBACK or COMMIT.
--  * RETURNING turns a blind change into a verified one.
-- ----------------------------------------------------------------------------
