-- Lesson 7.1 — SQL essentials, in the schema our JDBC code will drive.
-- DDL (Data Definition Language): defines STRUCTURE.

CREATE TABLE IF NOT EXISTS account (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,   -- surrogate key, DB-generated
    owner    VARCHAR(100) NOT NULL,
    balance  DECIMAL(12, 2) NOT NULL DEFAULT 0     -- DECIMAL, never DOUBLE, for money (1.2!)
);

CREATE TABLE IF NOT EXISTS ledger_entry (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id  BIGINT NOT NULL,
    amount      DECIMAL(12, 2) NOT NULL,
    note        VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- FOREIGN KEY: referential integrity — a ledger row MUST point at a
    -- real account; the DB refuses orphans and cascades deletes.
    CONSTRAINT fk_entry_account FOREIGN KEY (account_id)
        REFERENCES account(id) ON DELETE CASCADE
);
