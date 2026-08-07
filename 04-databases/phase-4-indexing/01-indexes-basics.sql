-- ============================================================================
-- Lesson 4.1 — How indexes work; CREATE INDEX
-- Run 00-setup.sql first.
-- ============================================================================

-- An INDEX is a separate, sorted data structure (usually a B-TREE) that maps
-- column value -> row location, so the DB can JUMP to matching rows instead of
-- scanning the whole table. Think of a book's index vs reading every page.
--   - lookup by an indexed value: O(log n)  (walk the tree)
--   - full table scan:            O(n)       (read every row)
-- The PRIMARY KEY is automatically indexed; nothing else is by default.

-- 1. Before any index: filtering by user_id must SCAN all 1M rows.
--    (Run EXPLAIN — lesson 4.2 — to see "Seq Scan on event".)
SELECT * FROM event WHERE user_id = 42;

-- 2. Create a B-tree index on the column we filter by:
CREATE INDEX idx_event_user_id ON event (user_id);
-- Now the same query uses "Index Scan" -> jumps straight to user 42's rows.
SELECT * FROM event WHERE user_id = 42;

-- 3. Index on created_at speeds up RANGE and ORDER BY queries (B-trees are
--    sorted, so ranges and sorts come nearly free):
CREATE INDEX idx_event_created_at ON event (created_at);
SELECT * FROM event WHERE created_at >= now() - interval '7 days';   -- range scan
SELECT * FROM event ORDER BY created_at DESC LIMIT 10;               -- top-N via the sorted index

-- 4. What indexes help (B-tree):
--    =  (equality),  <  >  BETWEEN  (ranges),  ORDER BY,  and prefix LIKE 'abc%'.
--    What they DON'T help:
--    leading-wildcard LIKE '%abc'  (can't use the sorted order),
--    functions on the column: WHERE lower(status) = 'paid'  (index is on status,
--       not lower(status) -> use an EXPRESSION index, lesson 4.3),
--    low-selectivity filters (see below).

-- 5. SELECTIVITY decides whether the planner even USES an index. An index helps
--    when a query returns a SMALL fraction of rows. `status` has only 4 values
--    (~25% each) -> reading the index + fetching 250k rows is SLOWER than just
--    scanning the table, so the planner may IGNORE an index on status.
CREATE INDEX idx_event_status ON event (status);
SELECT count(*) FROM event WHERE status = 'paid';   -- likely still a Seq Scan (low selectivity)
-- Rule: index high-selectivity columns (many distinct values: user_id, email,
-- timestamps). Indexing a boolean/low-cardinality column rarely helps.

-- 6. Index types beyond B-tree (Postgres):
--    B-tree   (default) - equality, ranges, sorting. 95% of cases.
--    Hash     - equality only (rarely worth it over B-tree).
--    GIN      - "contains" queries: JSONB, arrays, full-text search (Phase 6).
--    GiST/SP-GiST - geometric / range / nearest-neighbour.
--    BRIN     - huge, naturally-ordered tables (append-only logs) - tiny index.

-- List a table's indexes:
--   \d event          (psql)   or
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'event';

-- ----------------------------------------------------------------------------
-- MENTAL MODEL: an index is a sorted lookup structure that trades WRITE speed +
-- disk space for READ speed. Every INSERT/UPDATE/DELETE must also update every
-- index on the table. So: index the columns you FILTER/JOIN/SORT on and that are
-- SELECTIVE — not every column (lesson 4.4).
-- ----------------------------------------------------------------------------
