-- ============================================================================
-- Lesson 7.1/7.2 — Schema migrations & partitioning (practical bits)
-- ============================================================================

-- SCHEMA MIGRATIONS — how a schema evolves safely over time. Tools (Flyway,
-- Liquibase) apply VERSIONED, ordered SQL scripts and track which have run in a
-- metadata table, so every environment (dev/CI/prod) reaches the same schema.
-- A migration is just ordered DDL, e.g.:

-- V1__create_customer.sql
CREATE TABLE IF NOT EXISTS customer_v2 (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL UNIQUE
);

-- V2__add_phone.sql  (additive change — safe, backward-compatible)
ALTER TABLE customer_v2 ADD COLUMN IF NOT EXISTS phone TEXT;

-- V3__backfill_and_constrain.sql  (multi-step for a NOT NULL on existing data)
UPDATE customer_v2 SET phone = 'unknown' WHERE phone IS NULL;
-- ALTER TABLE customer_v2 ALTER COLUMN phone SET NOT NULL;   -- only after backfill

-- MIGRATION RULES:
--   * migrations are IMMUTABLE once applied (never edit V1; add V4 to fix it)
--   * prefer ADDITIVE, backward-compatible changes so old + new app versions
--     can run during a rollout (expand-then-contract): add column -> deploy code
--     that writes both -> backfill -> switch reads -> drop old column later
--   * NEVER hand-edit prod schema; migrations are the single source of truth
--     (in Spring Boot: Flyway runs them on startup; NOT ddl-auto=update, Java 7.3)

-- ============================================================
-- PARTITIONING — split one huge table into smaller physical pieces (partitions)
-- by a key, transparently. Improves manageability and query speed (the planner
-- skips irrelevant partitions = "partition pruning"). Good for time-series/logs.
-- ============================================================
DROP TABLE IF EXISTS measurement CASCADE;

CREATE TABLE measurement (
    id        BIGINT GENERATED ALWAYS AS IDENTITY,
    taken_at  DATE NOT NULL,
    value     NUMERIC
) PARTITION BY RANGE (taken_at);                       -- range partitioning by date

-- Create a partition per month:
CREATE TABLE measurement_2026_01 PARTITION OF measurement
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE measurement_2026_02 PARTITION OF measurement
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

INSERT INTO measurement (taken_at, value) VALUES
    ('2026-01-15', 10), ('2026-02-10', 20);            -- routed to the right partition

-- A query on one month scans only that partition (pruning) -> faster on big data:
EXPLAIN SELECT * FROM measurement WHERE taken_at >= '2026-02-01';
-- Also: drop old data by dropping a whole partition (instant) vs a slow DELETE.

-- Partition types: RANGE (dates/numbers), LIST (discrete values, e.g. region),
-- HASH (even spread). This is single-database partitioning; SHARDING spreads
-- partitions across MACHINES (System Design Phase 3) and is a bigger step.

-- ----------------------------------------------------------------------------
-- These are the two everyday "production schema" skills: migrations (evolve the
-- schema safely and reproducibly) and partitioning (keep big tables fast and
-- manageable). Replication/sharding/backups are covered in NOTES.md.
-- ----------------------------------------------------------------------------
