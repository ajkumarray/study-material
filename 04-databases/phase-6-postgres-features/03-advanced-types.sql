-- ============================================================================
-- Lesson 6.3 — Advanced types: JSONB, arrays, enums, ranges, full-text search
-- These are what make Postgres a "multi-model" database — document + relational.
-- ============================================================================

DROP TABLE IF EXISTS product CASCADE;

CREATE TABLE product (
    id       SERIAL PRIMARY KEY,
    name     TEXT NOT NULL,
    tags     TEXT[],                    -- ARRAY column
    attrs    JSONB,                     -- schemaless JSON (document-style)
    price    NUMERIC(10,2)
);

INSERT INTO product (name, tags, attrs, price) VALUES
    ('Laptop',  ARRAY['electronics','computers'],
     '{"brand":"Dell","ram_gb":16,"ports":["usb-c","hdmi"]}', 85000),
    ('Coffee',  ARRAY['grocery','beverage'],
     '{"brand":"Blue Tokai","roast":"medium","organic":true}', 599),
    ('Keyboard',ARRAY['electronics','accessories'],
     '{"brand":"Keychron","switches":"brown","wireless":true}', 7999);

-- 1. ARRAYS — store and query multiple values in one column:
SELECT name, tags FROM product WHERE 'electronics' = ANY(tags);   -- membership
SELECT name, array_length(tags, 1) AS num_tags FROM product;
SELECT name, unnest(tags) AS tag FROM product;                    -- expand to rows

-- 2. JSONB — binary JSON: schemaless nested data, queryable and indexable.
--    -> returns JSON, ->> returns text:
SELECT name, attrs->>'brand' AS brand, (attrs->>'ram_gb')::int AS ram
FROM product;

-- Filter by a JSON field:
SELECT name FROM product WHERE attrs->>'brand' = 'Dell';
-- Containment (@>) — "attrs contains this JSON" (uses a GIN index, Phase 4):
SELECT name FROM product WHERE attrs @> '{"wireless": true}';
-- Nested / array access:
SELECT name FROM product WHERE attrs->'ports' ? 'hdmi';           -- key/element exists
-- Update a JSON field in place:
UPDATE product SET attrs = jsonb_set(attrs, '{ram_gb}', '32') WHERE name = 'Laptop';

-- Index JSONB for fast containment queries:
CREATE INDEX idx_product_attrs ON product USING GIN (attrs);

-- JSONB gives you document-database flexibility INSIDE a relational DB — great
-- for semi-structured/variable attributes without a rigid schema (Phase 9 Mongo
-- does this as its whole model). Trade-off: less integrity than real columns.

-- 3. ENUM type — a fixed set of allowed values (a real type, not just a CHECK):
CREATE TYPE order_status AS ENUM ('pending','paid','shipped','delivered');
-- CREATE TABLE ... (status order_status NOT NULL DEFAULT 'pending');
SELECT 'shipped'::order_status;   -- valid; 'banned'::order_status would error

-- 4. RANGE types — a value spanning a range (dates, numbers):
SELECT '[2026-01-01,2026-12-31]'::daterange @> '2026-06-15'::date AS in_range;  -- true
-- Great for booking systems with an EXCLUSION constraint (no overlapping ranges):
--   EXCLUDE USING gist (room_id WITH =, during WITH &&)

-- 5. FULL-TEXT SEARCH — search documents by words, with stemming/ranking:
SELECT name FROM product
WHERE to_tsvector('english', name || ' ' || coalesce(attrs->>'brand','')) @@ to_tsquery('english', 'dell');
-- to_tsvector = normalized searchable doc; to_tsquery = the search terms; @@ = match.
-- Index it with GIN on to_tsvector(...) for fast search (beats LIKE '%..%').

-- ----------------------------------------------------------------------------
-- These features let Postgres cover many use cases that would otherwise need a
-- separate database: JSONB (document store), arrays (simple lists), full-text
-- (basic search engine), ranges (scheduling). Reach for a specialized DB only
-- when Postgres's version genuinely isn't enough (scale, specialized queries).
-- ----------------------------------------------------------------------------
