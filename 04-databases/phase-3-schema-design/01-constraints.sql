-- ============================================================================
-- Lesson 3.1 — Constraints: the rules the database ENFORCES for you
-- Run:  psql -U <user> -d <db> -f 01-constraints.sql
-- ============================================================================

-- Constraints push data-integrity rules INTO the database, so bad data is
-- impossible no matter which app/script writes it. The DB is the last line of
-- defense (better than trusting every caller to validate).

DROP TABLE IF EXISTS order_item CASCADE;
DROP TABLE IF EXISTS customer_account CASCADE;

CREATE TABLE customer_account (
    -- PRIMARY KEY: unique + NOT NULL, identifies each row. Prefer a surrogate
    -- key (SERIAL/GENERATED) over a natural key.
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- modern SERIAL

    -- NOT NULL: the value is required.
    email        TEXT NOT NULL,

    -- UNIQUE: no two rows share this value (NULLs are allowed & not equal).
    -- Combine with NOT NULL for a true "required and unique" business key.
    CONSTRAINT uq_email UNIQUE (email),

    -- CHECK: an arbitrary boolean rule per row.
    age          INTEGER CHECK (age >= 18),
    balance      NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (balance >= 0),

    -- DEFAULT: value used when none is supplied on INSERT.
    status       TEXT NOT NULL DEFAULT 'active'
                 CHECK (status IN ('active', 'suspended', 'closed')),   -- enum-like check
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_item (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- FOREIGN KEY: this column must reference an existing customer_account.id.
    -- The DB refuses orphans, and the referential action controls what happens
    -- when the parent is deleted/updated:
    --   ON DELETE CASCADE   -> delete these rows too
    --   ON DELETE RESTRICT  -> block the parent delete (default)
    --   ON DELETE SET NULL  -> null out the FK
    account_id   BIGINT NOT NULL REFERENCES customer_account(id) ON DELETE CASCADE,

    product      TEXT NOT NULL,
    quantity     INTEGER NOT NULL CHECK (quantity > 0),
    unit_price   NUMERIC(10,2) NOT NULL CHECK (unit_price >= 0),

    -- A table-level UNIQUE across MULTIPLE columns (composite): one product per
    -- account per row-set. (Illustrative.)
    CONSTRAINT uq_account_product UNIQUE (account_id, product)
);

-- ---- Seed some valid rows ----
INSERT INTO customer_account (email, age) VALUES
    ('ajay@dev.io', 30),
    ('meera@dev.io', 25);

INSERT INTO order_item (account_id, product, quantity, unit_price) VALUES
    (1, 'Clean Code', 2, 38.50),
    (1, 'Refactoring', 1, 47.99);

-- ---- These would all FAIL (uncomment one at a time to see the error) ----
-- INSERT INTO customer_account (email, age) VALUES ('ajay@dev.io', 40);  -- UNIQUE violation
-- INSERT INTO customer_account (email, age) VALUES (NULL, 40);           -- NOT NULL violation
-- INSERT INTO customer_account (email, age) VALUES ('kid@dev.io', 15);   -- CHECK age >= 18
-- INSERT INTO customer_account (email, status) VALUES ('x@x.io','banned');-- CHECK status list
-- INSERT INTO order_item (account_id, product, quantity, unit_price)
--   VALUES (999, 'Ghost', 1, 10);                                        -- FK: no such account
-- INSERT INTO order_item (account_id, product, quantity, unit_price)
--   VALUES (1, 'Clean Code', 5, 38.50);                                  -- composite UNIQUE dup
-- DELETE FROM customer_account WHERE id = 1;   -- cascades: deletes account 1's order_items

SELECT * FROM customer_account;
SELECT * FROM order_item;

-- ----------------------------------------------------------------------------
-- Adding/removing constraints later (they're part of the schema = DDL):
--   ALTER TABLE order_item ADD CONSTRAINT chk_qty CHECK (quantity <= 1000);
--   ALTER TABLE order_item DROP CONSTRAINT chk_qty;
-- WHY enforce in the DB, not just the app: multiple apps/scripts/migrations
-- touch the data; the DB guarantees the invariant for ALL of them, forever.
-- ----------------------------------------------------------------------------
