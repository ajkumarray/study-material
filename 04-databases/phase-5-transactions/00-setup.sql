-- ============================================================================
-- Phase 5 setup — a bank accounts table for transaction/concurrency demos.
-- Run first:  psql -U <user> -d <db> -f 00-setup.sql
-- ============================================================================

DROP TABLE IF EXISTS account CASCADE;

CREATE TABLE account (
    id       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner    TEXT NOT NULL,
    balance  NUMERIC(12,2) NOT NULL CHECK (balance >= 0),  -- can't go negative (constraint)
    version  INT NOT NULL DEFAULT 0                        -- for optimistic locking (5.3)
);

INSERT INTO account (owner, balance) VALUES
    ('Ajay', 1000.00),
    ('Ravi',  500.00),
    ('Meera', 750.00);

SELECT * FROM account ORDER BY id;
