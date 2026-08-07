-- ============================================================================
-- Lesson 4.4 — When indexes hurt; VACUUM, ANALYZE, bloat, statistics
-- Run 00-setup.sql first.
-- ============================================================================

-- 1. THE COST OF INDEXES — why you don't index everything:
--   * Every INSERT/UPDATE/DELETE must update EVERY index on the table -> slower writes.
--   * Each index consumes disk space (and RAM when cached).
--   * More indexes = more for the planner to consider.
-- So an index is a READ optimization paid for with WRITE cost + space. Index the
-- columns you actually filter/join/sort on; drop indexes nothing uses:
SELECT indexrelname, idx_scan   -- idx_scan = 0 -> the index is never used, consider dropping
FROM pg_stat_user_indexes WHERE relname = 'event' ORDER BY idx_scan;

DROP INDEX IF EXISTS idx_event_status;   -- example: a low-selectivity index rarely used

-- 2. ANALYZE — refresh the PLANNER STATISTICS (row counts, value distributions,
-- most-common values). The planner uses these to estimate rows and CHOOSE plans.
-- STALE stats -> bad estimates -> bad plans (e.g., a Seq Scan where an Index Scan
-- would win). Autovacuum runs ANALYZE automatically, but run it manually after
-- big data loads:
ANALYZE event;
-- See the stats the planner has for a column:
SELECT attname, n_distinct, most_common_vals
FROM pg_stats WHERE tablename = 'event' AND attname IN ('user_id','status');
-- n_distinct near the row count = high selectivity (good index candidate);
-- n_distinct = 4 (status) = low selectivity (index rarely helps).

-- 3. VACUUM — reclaim space from DEAD TUPLES. Because Postgres uses MVCC
-- (Phase 5), an UPDATE/DELETE doesn't overwrite a row — it marks the old version
-- dead and writes a new one. Dead tuples accumulate as BLOAT (wasted space, more
-- I/O, slower scans). VACUUM reclaims them for reuse; autovacuum does this in the
-- background, but a neglected/append-heavy table can bloat.
--   VACUUM event;            -- reclaim dead space for reuse (online, non-blocking)
--   VACUUM ANALYZE event;    -- vacuum + refresh stats in one go
--   VACUUM FULL event;       -- rewrite the table to shrink on disk (LOCKS it! rare)

-- Check bloat / dead tuples and last (auto)vacuum:
SELECT relname, n_live_tup, n_dead_tup, last_autovacuum
FROM pg_stat_user_tables WHERE relname = 'event';

-- 4. LONG-RUNNING TRANSACTIONS block vacuum (System Design Phase 7): vacuum can't
-- remove row versions still "visible" to an open transaction. One forgotten open
-- transaction can bloat the whole DB. Keep transactions short.

-- 5. INDEX bloat & rebuilds: indexes bloat too; REINDEX rebuilds a bloated index
-- (or CREATE INDEX CONCURRENTLY / REINDEX CONCURRENTLY to avoid locking writes):
--   REINDEX INDEX idx_event_user_id;
--   CREATE INDEX CONCURRENTLY idx_new ON event (user_id);   -- build without blocking writes

-- 6. Table & index sizes (capacity awareness):
SELECT pg_size_pretty(pg_total_relation_size('event'))  AS total,   -- table + indexes + toast
       pg_size_pretty(pg_relation_size('event'))         AS table_only,
       pg_size_pretty(pg_indexes_size('event'))          AS indexes;

-- ----------------------------------------------------------------------------
-- THE MAINTENANCE MINDSET:
--   * ANALYZE keeps the planner smart (accurate estimates -> good plans).
--   * VACUUM keeps the table lean (reclaims MVCC dead tuples -> less bloat).
--   * Both run via autovacuum by default; tune/trigger manually for big loads,
--     update-heavy tables, or bulk deletes.
--   * Drop unused indexes; keep transactions short; use CONCURRENTLY in prod.
-- This maintenance IS the "database memory leak / bloat" story from System
-- Design Phase 7 — now with the exact SQL to diagnose and fix it.
-- ----------------------------------------------------------------------------
