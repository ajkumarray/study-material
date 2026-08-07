-- ============================================================================
-- Lesson 4.2 — EXPLAIN / EXPLAIN ANALYZE: reading the query plan
-- Run 00-setup.sql first (indexes from 01 optional).
-- ============================================================================

-- The QUERY PLANNER decides HOW to run your SQL (which scan, which join order,
-- which index). EXPLAIN shows that plan. This is the #1 tool for diagnosing
-- slow queries — you stop guessing and see what the DB actually does.

-- 1. EXPLAIN — show the plan + the planner's ESTIMATES (no execution):
EXPLAIN
SELECT * FROM event WHERE user_id = 42;
-- Read plans BOTTOM-UP / inside-out. Key fields per node:
--   Seq Scan / Index Scan / Bitmap Heap Scan  = HOW rows are read
--   cost=0.00..12345.67   = estimated startup..total cost (arbitrary units)
--   rows=98              = estimated rows returned
--   width=64             = estimated bytes per row

-- 2. EXPLAIN ANALYZE — actually RUNS the query and shows REAL timings + row
--    counts (so you can compare estimate vs actual). Use this to diagnose.
EXPLAIN ANALYZE
SELECT * FROM event WHERE user_id = 42;
-- Now each node also shows:
--   actual time=0.05..0.42   = real ms (startup..total) for that node
--   rows=97 loops=1          = real rows
--   Planning Time / Execution Time at the bottom
-- BIG GAP between estimated rows and actual rows => stale statistics
-- (run ANALYZE, lesson 4.4) or a mis-estimate the planner can't handle.

-- 3. The scan types you'll see, worst to best for a selective filter:
--   Seq Scan          - read EVERY row. Fine for small tables or low selectivity;
--                       a red flag on a big table with a selective WHERE (missing index).
--   Index Scan        - walk the index, fetch matching rows. Great for few matches.
--   Bitmap Heap Scan  - build a bitmap of matching row locations, then fetch in
--                       physical order. Good for a medium number of matches.
--   Index Only Scan   - answer entirely FROM the index, never touch the table
--                       (the fastest; needs a covering index, lesson 4.3).

-- Compare: this selective query should use an Index Scan (if idx_event_user_id
-- exists), vs a low-selectivity one that stays a Seq Scan:
EXPLAIN ANALYZE SELECT * FROM event WHERE user_id = 42;          -- ~100 rows  -> Index Scan
EXPLAIN ANALYZE SELECT count(*) FROM event WHERE status = 'paid';-- ~250k rows -> Seq Scan

-- 4. Verbose options for real debugging:
EXPLAIN (ANALYZE, BUFFERS)                 -- + shared buffer hits/reads (I/O!)
SELECT * FROM event WHERE created_at >= now() - interval '1 day' ORDER BY created_at;

-- 5. JOIN plans — the planner also picks a JOIN STRATEGY:
--   Nested Loop   - for each outer row, look up matches (great with an index on
--                   the inner side + few outer rows).
--   Hash Join     - build a hash table of one side; good for large unsorted joins.
--   Merge Join    - merge two sorted inputs; good when both are already sorted.
EXPLAIN ANALYZE
SELECT e.status, count(*)
FROM event e
WHERE e.created_at >= now() - interval '30 days'
GROUP BY e.status;

-- ----------------------------------------------------------------------------
-- HOW TO USE IT: run EXPLAIN ANALYZE on a slow query and look for
--   * a Seq Scan on a big table where your WHERE is selective  -> add an index
--   * estimated rows wildly off from actual rows               -> ANALYZE / stats
--   * high "actual time" nodes                                 -> the bottleneck
--   * repeated work / Nested Loop over many rows               -> missing index
-- Then add the index / rewrite the query and re-run to confirm the plan changed.
-- ----------------------------------------------------------------------------
