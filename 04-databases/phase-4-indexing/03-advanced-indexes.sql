-- ============================================================================
-- Lesson 4.3 — Composite, covering, partial & expression indexes
-- Run 00-setup.sql first.
-- ============================================================================

-- 1. COMPOSITE (multi-column) index — indexes several columns TOGETHER, in order.
CREATE INDEX idx_event_user_created ON event (user_id, created_at);
-- Great for queries filtering user_id AND ranging/sorting by created_at:
SELECT * FROM event WHERE user_id = 42 AND created_at >= now() - interval '30 days';
SELECT * FROM event WHERE user_id = 42 ORDER BY created_at DESC LIMIT 5;

-- THE LEFTMOST-PREFIX RULE: a composite index on (a, b, c) can serve queries on
--   (a), (a,b), (a,b,c)  -- prefixes from the left
-- but NOT (b), (c), or (b,c) alone. So column ORDER matters:
SELECT * FROM event WHERE user_id = 42;                    -- USES the index (prefix a)
SELECT * FROM event WHERE created_at >= now() - interval '1 day';  -- does NOT use it (skips a)
-- Put the equality/most-selective column FIRST, the range/sort column next.

-- 2. COVERING index (INCLUDE) — an index-only scan answers the query ENTIRELY
-- from the index, never touching the table (the fastest possible read). Add the
-- SELECTed columns via INCLUDE so they ride along in the index:
CREATE INDEX idx_event_user_incl ON event (user_id) INCLUDE (amount, status);
-- This query can now be an "Index Only Scan" (check with EXPLAIN):
SELECT amount, status FROM event WHERE user_id = 42;
-- (Requires the table's visibility map to be up to date -> VACUUM, lesson 4.4.)

-- 3. PARTIAL index — index only a SUBSET of rows (a WHERE on the index). Smaller
-- and faster when you only ever query that subset. E.g., mostly-'paid' lookups:
CREATE INDEX idx_event_pending ON event (created_at) WHERE status = 'pending';
-- Only queries that include the same predicate use it:
SELECT * FROM event WHERE status = 'pending' AND created_at >= now() - interval '7 days';
-- The index holds ONLY pending rows -> tiny, and perfect for a "work queue" of
-- unprocessed rows. Also great to enforce conditional uniqueness:
--   CREATE UNIQUE INDEX one_primary_per_user ON address (user_id) WHERE is_primary;

-- 4. EXPRESSION (functional) index — index the RESULT of an expression, so
-- queries that filter by that expression can use an index. Fixes the
-- "function on the column kills the index" problem (lesson 4.1):
CREATE INDEX idx_event_lower_status ON event (lower(status));
SELECT * FROM event WHERE lower(status) = 'paid';   -- now index-able
-- The query's expression must MATCH the index's expression exactly.

-- 5. UNIQUE index — enforces uniqueness AND provides a fast lookup (a UNIQUE
-- constraint is implemented as a unique index under the hood):
--   CREATE UNIQUE INDEX uq_user_email ON app_user (email);

-- Inspect what exists and their sizes:
SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'event';
SELECT indexrelname, pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes WHERE relname = 'event';

-- ----------------------------------------------------------------------------
-- CHOOSING: one well-designed composite index often beats several single-column
-- ones. Order columns as: equality filters -> range/sort -> INCLUDE the rest for
-- covering. Partial/expression indexes target specific query shapes. But every
-- index costs write speed + space (lesson 4.4) — add them for real query
-- patterns you've confirmed with EXPLAIN, not speculatively.
-- ----------------------------------------------------------------------------
