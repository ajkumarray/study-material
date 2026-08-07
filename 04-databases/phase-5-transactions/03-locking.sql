-- ============================================================================
-- Lesson 5.3 — Locking, deadlocks, SELECT ... FOR UPDATE
-- Run 00-setup.sql first. Some parts are two-session (Session A / Session B).
-- ============================================================================

-- Locks coordinate concurrent access. Postgres takes many locks automatically;
-- the ones you REACH FOR explicitly are ROW locks to prevent lost updates
-- (System Design Phase 4). Two strategies: pessimistic and optimistic.

-- ============================================================
-- 1. THE LOST UPDATE problem (why row locks exist):
--   A reads balance 850, B reads 850, A writes 850+100=950, B writes 850-50=800
--   -> A's +100 is LOST. Both read a stale value. (Demonstrated in Java Phase 5
--      and System Design Phase 4.)
-- ============================================================

-- 2. PESSIMISTIC locking — SELECT ... FOR UPDATE locks the matched rows so no
-- other transaction can update them until you commit. Others WAIT.
--   Session A                                  | Session B
--   -------------------------------------------+-----------------------------------------
--   BEGIN;                                     |
--   SELECT balance FROM account                |
--     WHERE owner='Ajay' FOR UPDATE;  -- LOCKED|
--                                              | BEGIN;
--                                              | SELECT balance FROM account
--                                              |   WHERE owner='Ajay' FOR UPDATE; -- WAITS...
--   UPDATE account SET balance=balance+100     |
--     WHERE owner='Ajay';                      |
--   COMMIT;                                    |   ...now B unblocks, sees the NEW balance
--                                              | UPDATE ...; COMMIT;   -- no lost update
-- Variants: FOR NO KEY UPDATE (weaker), FOR SHARE (blocks writers, allows readers),
-- and FOR UPDATE SKIP LOCKED / NOWAIT (job-queue patterns: grab an unlocked row).

-- Job-queue idiom — each worker grabs a different pending row, no waiting:
--   SELECT * FROM account FOR UPDATE SKIP LOCKED LIMIT 1;

-- 3. OPTIMISTIC locking — no locks held; detect a conflict at write time using
-- a version column (the account.version from setup). Update only if unchanged:
--   -- read the row and its version:
--   SELECT balance, version FROM account WHERE id = 1;        -- say balance 850, version 3
--   -- later, write only if the version still matches:
UPDATE account
SET balance = 950, version = version + 1
WHERE id = 1 AND version = 3;          -- if 0 rows updated -> someone else won -> reload & retry
--   Check how many rows changed (GET DIAGNOSTICS / the client's update count).
-- Best for LOW contention (web apps); no waiting, but you must handle the retry.
-- This is exactly JPA @Version (Spring Boot Phase 4) done by hand.

-- 4. DEADLOCK — two transactions each hold a lock the other needs; neither can
-- proceed. Postgres DETECTS the cycle and kills one (victim gets an error to retry).
--   Session A: BEGIN; UPDATE account SET balance=balance-10 WHERE id=1;  -- locks row 1
--   Session B: BEGIN; UPDATE account SET balance=balance-10 WHERE id=2;  -- locks row 2
--   Session A: UPDATE account SET balance=balance+10 WHERE id=2;  -- waits for B (row 2)
--   Session B: UPDATE account SET balance=balance+10 WHERE id=1;  -- waits for A (row 1) = DEADLOCK
--   -> Postgres aborts one: "deadlock detected". PREVENT it by always locking rows
--      in a CONSISTENT ORDER (e.g., ascending id) — then a cycle is impossible
--      (same fix as thread deadlocks, Java Phase 5.2).

-- 5. Inspect locks and blocking (production diagnosis):
SELECT locktype, relation::regclass, mode, granted, pid
FROM pg_locks WHERE relation = 'account'::regclass;
-- pg_stat_activity shows which query each backend runs and what it waits on:
SELECT pid, state, wait_event_type, wait_event, left(query, 60) AS query
FROM pg_stat_activity WHERE datname = current_database();

-- 6. ADVISORY LOCKS — application-defined locks not tied to any row (e.g., "only
-- one worker runs this cron job"). You choose the key:
SELECT pg_try_advisory_lock(42);      -- true if acquired; pg_advisory_unlock(42) to release

-- ----------------------------------------------------------------------------
-- CHOOSING: optimistic (version column, retry) for low-contention web writes;
-- pessimistic (FOR UPDATE) for high contention or costly-to-redo work. Both sit
-- on top of the isolation levels (5.2). Keep locked sections SHORT to reduce
-- contention and deadlock risk, and lock rows in a consistent order.
-- ----------------------------------------------------------------------------
