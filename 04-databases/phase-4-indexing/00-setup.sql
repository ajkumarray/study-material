-- ============================================================================
-- Phase 4 setup — a BIG table so index effects are visible in EXPLAIN.
-- Run first:  psql -U <user> -d <db> -f 00-setup.sql
-- Generates 1,000,000 rows with generate_series (takes a few seconds).
-- ============================================================================

DROP TABLE IF EXISTS event;

CREATE TABLE event (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- PK auto-indexed
    user_id     BIGINT NOT NULL,
    status      TEXT NOT NULL,
    amount      NUMERIC(10,2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

-- One million rows: user_id 1..10000, a few statuses, random amounts/dates.
INSERT INTO event (user_id, status, amount, created_at)
SELECT
    (random() * 9999)::bigint + 1,                                   -- user_id
    (ARRAY['pending','paid','shipped','cancelled'])[floor(random()*4)+1],  -- status
    (random() * 1000)::numeric(10,2),                                -- amount
    now() - (random() * interval '365 days')                        -- created_at within a year
FROM generate_series(1, 1000000);

-- Update planner statistics so EXPLAIN estimates are accurate (see 04-maintenance).
ANALYZE event;

-- No indexes yet (except the PK) — Phase 4 lessons add them and measure the effect.
SELECT count(*) AS rows FROM event;
