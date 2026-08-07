-- ============================================================================
-- Lesson 1.3 — Data types & NULL semantics (three-valued logic)
-- The single biggest source of "why is my query wrong?" bugs.
-- ============================================================================

-- Common PostgreSQL data types (know these):
--   INTEGER / BIGINT / SMALLINT   whole numbers
--   NUMERIC(p,s) / DECIMAL        exact decimals — USE FOR MONEY (never float!)
--   REAL / DOUBLE PRECISION       approximate floating point
--   TEXT / VARCHAR(n) / CHAR(n)   strings (prefer TEXT in Postgres)
--   BOOLEAN                       true / false / NULL
--   DATE / TIME / TIMESTAMP / TIMESTAMPTZ   temporal (prefer TIMESTAMPTZ)
--   SERIAL / BIGSERIAL            auto-incrementing integer (shortcut)
--   UUID, JSONB, arrays, ranges   Postgres specialties (Phase 6)

-- ----------------------------------------------------------------------------
-- NULL means "UNKNOWN / no value" — it is NOT 0, NOT '', NOT false.
-- ----------------------------------------------------------------------------

-- 1. You CANNOT compare to NULL with = or <> . These return NULL, not true:
SELECT title FROM book WHERE genre = NULL;      -- WRONG: returns ZERO rows, always
SELECT title FROM book WHERE genre <> NULL;     -- WRONG: also zero rows

-- 2. The CORRECT way — IS NULL / IS NOT NULL:
SELECT title FROM book WHERE genre IS NULL;       -- the untitled draft
SELECT title FROM book WHERE genre IS NOT NULL;
SELECT name  FROM author WHERE country IS NULL;   -- 'Unknown Writer'

-- 3. THREE-VALUED LOGIC: every comparison is TRUE, FALSE, or UNKNOWN(NULL).
--    WHERE keeps only TRUE rows — UNKNOWN rows are dropped.
--    This bites with NOT IN, negations, and arithmetic:
SELECT title, price + NULL AS oops FROM book;     -- any math with NULL -> NULL
SELECT title FROM book WHERE in_stock <> 12;      -- does NOT include rows where in_stock is NULL
                                                  -- (here in_stock is NOT NULL, but remember the rule)

-- 4. Handling NULL — the toolbox:
-- COALESCE: first non-NULL argument (great for defaults/display):
SELECT title, COALESCE(genre, '(unclassified)') AS genre FROM book;
SELECT name, COALESCE(country, 'unknown') AS country FROM author;

-- NULLIF: return NULL if two values are equal (inverse trick):
SELECT NULLIF(in_stock, 0) AS stock_or_null FROM book;  -- turn 0 into NULL

-- IS DISTINCT FROM: NULL-safe inequality (treats NULL as a comparable value):
SELECT title FROM book WHERE genre IS DISTINCT FROM 'tech';
-- ^ includes the NULL-genre row, unlike  genre <> 'tech'  which drops it.

-- 5. NULL in aggregates: aggregate functions SKIP NULLs (important!):
SELECT count(*)        AS all_rows,       -- counts every row
       count(genre)    AS rows_with_genre, -- counts only non-NULL genre
       count(DISTINCT genre) AS distinct_genres
FROM book;

-- 6. Casting between types with :: (Postgres) or CAST(...):
SELECT '42'::INTEGER + 8            AS cast_shorthand;   -- 50
SELECT CAST('2020-01-01' AS DATE)   AS cast_standard;
SELECT price::TEXT || ' USD'        AS price_label FROM book;  -- || = string concat

-- ----------------------------------------------------------------------------
-- MENTAL MODEL: NULL is contagious in comparisons and math (NULL in -> NULL out),
-- and invisible to aggregates. When a query "loses" rows unexpectedly, suspect
-- a NULL interacting with =, <>, NOT IN, or a JOIN condition.
-- ----------------------------------------------------------------------------
