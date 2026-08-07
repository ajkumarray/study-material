-- ============================================================================
-- Lesson 5.1 — Transactions: ACID, BEGIN/COMMIT/ROLLBACK, savepoints
-- Run 00-setup.sql first.
-- ============================================================================

-- A TRANSACTION groups statements into ONE atomic unit: either ALL of them
-- happen, or NONE do. The classic case is a money transfer (debit + credit) —
-- if the credit fails after the debit, you MUST undo the debit.

-- ACID — the four guarantees a transaction gives:
--   Atomicity   - all-or-nothing (COMMIT applies everything; ROLLBACK undoes all)
--   Consistency - constraints/invariants hold before and after (CHECK, FK, etc.)
--   Isolation   - concurrent transactions don't corrupt each other (lesson 5.2)
--   Durability  - once COMMITted, it survives a crash (written to the WAL/disk)

-- 1. The transfer pattern — wrap the two updates in a transaction:
BEGIN;                                                        -- start the transaction
    UPDATE account SET balance = balance - 200 WHERE owner = 'Ajay';   -- debit
    UPDATE account SET balance = balance + 200 WHERE owner = 'Ravi';   -- credit
COMMIT;                                                       -- make BOTH permanent, atomically
-- If the DB crashed between the two updates WITHOUT a transaction, Ajay would
-- lose 200 that never reached Ravi. The transaction prevents that.

SELECT owner, balance FROM account ORDER BY id;

-- 2. ROLLBACK — undo everything since BEGIN. Rehearse a risky change safely:
BEGIN;
    UPDATE account SET balance = 0 WHERE owner = 'Ajay';     -- oops
    SELECT owner, balance FROM account;                       -- inspect within the txn
ROLLBACK;                                                     -- undo it all
SELECT owner, balance FROM account WHERE owner = 'Ajay';      -- unchanged (1 row, real balance)

-- 3. Automatic rollback on error — a failed statement aborts the whole txn.
-- The CHECK (balance >= 0) makes an overdraft fail; nothing in the txn commits:
BEGIN;
    UPDATE account SET balance = balance - 100 WHERE owner = 'Meera';  -- ok so far
    UPDATE account SET balance = balance - 999999 WHERE owner = 'Meera'; -- CHECK violation -> error
COMMIT;   -- Postgres reports "current transaction is aborted" -> effectively a ROLLBACK
SELECT owner, balance FROM account WHERE owner = 'Meera';     -- unchanged: the -100 was undone too

-- 4. SAVEPOINTS — partial rollback within a transaction (nested undo points):
BEGIN;
    UPDATE account SET balance = balance + 50 WHERE owner = 'Ajay';
    SAVEPOINT after_bonus;                                     -- a checkpoint
    UPDATE account SET balance = balance - 5000 WHERE owner = 'Ajay';  -- a step we regret
    ROLLBACK TO SAVEPOINT after_bonus;                        -- undo ONLY back to the savepoint
    -- the +50 survives; the -5000 is gone
COMMIT;
SELECT owner, balance FROM account WHERE owner = 'Ajay';

-- ----------------------------------------------------------------------------
-- AUTOCOMMIT: by default each statement is its OWN transaction (auto-committed).
-- BEGIN...COMMIT groups several into one. This is exactly the JDBC pattern from
-- Java Phase 7.2 (setAutoCommit(false) -> commit()/rollback()) and what Spring's
-- @Transactional wraps for you (Spring Boot Phase 4). Keep transactions SHORT —
-- long ones hold locks and block VACUUM (Phase 4.4 / System Design Phase 7).
-- ----------------------------------------------------------------------------
