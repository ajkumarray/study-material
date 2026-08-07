-- ============================================================================
-- Lesson 2.1 — JOINs: combining rows from multiple tables
-- Run 00-setup.sql first.
-- ============================================================================

-- A JOIN matches rows from two tables on a condition (usually FK = PK).
-- The join TYPE decides what happens to rows that DON'T match.

-- 1. INNER JOIN — only rows with a match on BOTH sides.
-- Book 6 ('An Orphan Book', NULL author) is DROPPED (no matching author).
SELECT b.title, a.name AS author
FROM book b
INNER JOIN author a ON b.author_id = a.id;      -- INNER is the default; "JOIN" alone = INNER

-- 2. LEFT (OUTER) JOIN — ALL left rows; NULLs where the right has no match.
-- Now 'An Orphan Book' appears with author = NULL. Use when you want to keep
-- every left row regardless of matches (the most common outer join).
SELECT b.title, a.name AS author
FROM book b
LEFT JOIN author a ON b.author_id = a.id;

-- 3. RIGHT JOIN — ALL right rows; the mirror of LEFT. 'Silent Author' (writes
-- no books) appears with title = NULL. (Rare in practice; just flip to LEFT.)
SELECT b.title, a.name AS author
FROM book b
RIGHT JOIN author a ON b.author_id = a.id;

-- 4. FULL OUTER JOIN — all rows from BOTH sides; NULLs where either is missing.
-- Shows the orphan book AND the silent author together.
SELECT b.title, a.name AS author
FROM book b
FULL OUTER JOIN author a ON b.author_id = a.id;

-- 5. Multi-table JOIN — chain them. Revenue per sale needs sale -> book (price)
-- and sale -> customer (name):
SELECT c.name AS customer, b.title, s.quantity, (s.quantity * b.price) AS revenue
FROM sale s
JOIN book b     ON s.book_id = b.id
JOIN customer c ON s.customer_id = c.id
ORDER BY revenue DESC;

-- 6. SELF JOIN — a table joined to itself (alias it twice). Find pairs of
-- authors from the same country:
SELECT a1.name AS author_1, a2.name AS author_2, a1.country
FROM author a1
JOIN author a2 ON a1.country = a2.country AND a1.id < a2.id;   -- id < id avoids dupes/self-pairs

-- 7. CROSS JOIN — every combination (cartesian product). Rarely wanted on
-- purpose; a MISSING join condition accidentally creates one (the classic bug).
SELECT a.name, g.genre
FROM author a
CROSS JOIN (SELECT DISTINCT genre FROM book) g
LIMIT 5;

-- 8. ANTI-JOIN — "rows with NO match". Books that have NEVER sold: LEFT JOIN
-- then keep the NULLs (or NOT EXISTS, lesson 2.3). 'An Orphan Book' qualifies.
SELECT b.title
FROM book b
LEFT JOIN sale s ON s.book_id = b.id
WHERE s.id IS NULL;        -- the anti-join idiom: left join + "right side is NULL"

-- ----------------------------------------------------------------------------
-- MENTAL MODEL:
--   INNER = intersection (matches only)
--   LEFT  = all of the left + matches
--   FULL  = union (everything, NULL-filled)
-- The JOIN condition (ON) filters PAIRS; the WHERE filters the RESULT. With
-- outer joins, putting a right-table condition in WHERE vs ON changes the
-- answer (WHERE can turn a LEFT JOIN back into an INNER by dropping NULL rows).
-- ----------------------------------------------------------------------------
