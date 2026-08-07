-- ============================================================================
-- Lesson 6.1 — Views & materialized views
-- Uses the Phase 2 bookstore schema (author/book/customer/sale). Run its
-- 00-setup.sql first, or adapt to any tables you have.
-- ============================================================================

-- A VIEW is a NAMED, SAVED QUERY that behaves like a virtual table. It stores no
-- data — it runs its underlying query each time you select from it. Great for
-- encapsulating complex joins/logic behind a simple name, and for security
-- (expose a view instead of raw tables).

-- 1. A view over a multi-table join (revenue per author):
CREATE OR REPLACE VIEW author_revenue AS
SELECT a.id, a.name,
       COALESCE(SUM(s.quantity * b.price), 0) AS revenue,
       COUNT(s.id)                            AS num_sales
FROM author a
LEFT JOIN book b ON b.author_id = a.id
LEFT JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name;

-- Query it like a table — the join is hidden:
SELECT name, revenue FROM author_revenue WHERE revenue > 0 ORDER BY revenue DESC;

-- Views compose: build simpler queries on top of them.
SELECT AVG(revenue) FROM author_revenue;

-- Note: simple views are UPDATABLE (INSERT/UPDATE through them); views with
-- joins/aggregates are read-only unless you add an INSTEAD OF trigger.

-- 2. MATERIALIZED VIEW — stores the RESULT on disk (a cached snapshot). Reads
-- are fast (no recompute), but the data is STALE until you refresh it. Use for
-- expensive aggregates/reports that don't need to be real-time.
CREATE MATERIALIZED VIEW author_revenue_cached AS
SELECT a.id, a.name, COALESCE(SUM(s.quantity * b.price), 0) AS revenue
FROM author a
LEFT JOIN book b ON b.author_id = a.id
LEFT JOIN sale s ON s.book_id = b.id
GROUP BY a.id, a.name;

SELECT * FROM author_revenue_cached ORDER BY revenue DESC;   -- instant (precomputed)

-- Refresh to update the snapshot (recomputes the query):
REFRESH MATERIALIZED VIEW author_revenue_cached;
-- CONCURRENTLY avoids locking readers during refresh (needs a unique index):
--   CREATE UNIQUE INDEX ON author_revenue_cached (id);
--   REFRESH MATERIALIZED VIEW CONCURRENTLY author_revenue_cached;

-- ----------------------------------------------------------------------------
-- VIEW vs MATERIALIZED VIEW:
--   view              -> always fresh, recomputed each read (no storage)
--   materialized view -> fast reads from a stored snapshot, but STALE until refresh
-- This is the DENORMALIZATION / read-optimization idea (Phase 3.4) and the
-- caching idea (System Design Phase 2), built into the database.
-- Cleanup:  DROP VIEW author_revenue;  DROP MATERIALIZED VIEW author_revenue_cached;
-- ----------------------------------------------------------------------------
