-- ============================================================================
-- Lesson 2.5 — Set operations: UNION, INTERSECT, EXCEPT
-- Run 00-setup.sql first.
-- ============================================================================

-- Set operations combine the ROWS of two queries (stacked vertically), unlike
-- JOINs (which combine COLUMNS horizontally). Both queries must have the SAME
-- number of columns with compatible types.

-- 1. UNION — all rows from both, DUPLICATES REMOVED. Cities where we have
-- either a customer or (pretend) an author office:
SELECT city FROM customer
UNION
SELECT country FROM author;        -- distinct combined list

-- 2. UNION ALL — keeps duplicates, and is FASTER (no dedupe pass). Use it
-- whenever you know there are no dupes or you want the counts:
SELECT 'customer city' AS kind, city FROM customer
UNION ALL
SELECT 'author country', country FROM author;

-- 3. INTERSECT — rows in BOTH queries. Values appearing as both a customer
-- city and an author country (none here, but the pattern):
SELECT city FROM customer
INTERSECT
SELECT country FROM author;

-- 4. EXCEPT (called MINUS in Oracle) — rows in the FIRST but NOT the second.
-- Genres that exist among books but that we'll pretend aren't "featured":
SELECT genre FROM book
EXCEPT
SELECT 'fiction';                  -- everything except fiction -> 'tech'

-- 5. A practical combo — one report labelling books as sold vs unsold using
-- set operations (compare with the anti-join in 01-joins.sql):
SELECT id AS book_id, 'sold' AS status FROM book
  WHERE id IN (SELECT book_id FROM sale)
UNION ALL
SELECT id, 'unsold' FROM book
  WHERE id NOT IN (SELECT book_id FROM sale WHERE book_id IS NOT NULL)
ORDER BY book_id;

-- ----------------------------------------------------------------------------
-- Rules & notes:
--  * Column COUNT and TYPES must line up between the two SELECTs; column names
--    come from the FIRST query.
--  * ORDER BY goes ONCE, at the very end (it orders the combined result).
--  * UNION removes duplicates (sorts to do so) -> prefer UNION ALL unless you
--    specifically need dedupe. Same for INTERSECT/EXCEPT vs their ALL variants.
--  * JOIN combines columns (widen); UNION combines rows (lengthen). Different jobs.
-- ----------------------------------------------------------------------------
