-- ============================================================================
-- Lesson 5.2 — Isolation levels & the anomalies they prevent
-- Run 00-setup.sql first. These are TWO-SESSION scenarios: open two psql
-- windows (Session A and Session B) and run the marked steps in order.
-- ============================================================================

-- ISOLATION controls how much concurrent transactions can see each other's
-- in-progress work. Higher isolation = more correctness, less concurrency.
-- There are 3 classic READ ANOMALIES; each isolation level prevents more of them.

-- Set the level for a transaction:
--   BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;   (or REPEATABLE READ / SERIALIZABLE)

-- ============================================================
-- ANOMALY 1 — DIRTY READ: reading another transaction's UNCOMMITTED changes.
-- (Postgres NEVER allows this — its lowest level, READ UNCOMMITTED, behaves as
--  READ COMMITTED. Other DBs can exhibit it.)
--   A: BEGIN; UPDATE account SET balance = 0 WHERE owner='Ajay';   -- not committed
--   B: SELECT balance FROM account WHERE owner='Ajay';             -- sees 0? = dirty read
--   A: ROLLBACK;                                                   -- the 0 never existed
-- ============================================================

-- ============================================================
-- ANOMALY 2 — NON-REPEATABLE READ: re-reading the SAME ROW in one transaction
-- gives a DIFFERENT value because another txn committed a change in between.
--   Session A                                  | Session B
--   -------------------------------------------+-----------------------------------------
--   BEGIN TRANSACTION ISOLATION LEVEL          |
--     READ COMMITTED;                          |
--   SELECT balance FROM account                |
--     WHERE owner='Ajay';   -- say 850         |
--                                              | UPDATE account SET balance=999
--                                              |   WHERE owner='Ajay';   (auto-commits)
--   SELECT balance FROM account                |
--     WHERE owner='Ajay';   -- NOW 999 !       |   <-- non-repeatable read
--   COMMIT;                                    |
-- Under REPEATABLE READ, A's second SELECT would still see 850 (a stable snapshot).
-- ============================================================

-- ============================================================
-- ANOMALY 3 — PHANTOM READ: re-running the same QUERY returns a DIFFERENT SET
-- of rows because another txn INSERTED/DELETED matching rows.
--   Session A                                  | Session B
--   -------------------------------------------+-----------------------------------------
--   BEGIN TRANSACTION ISOLATION LEVEL          |
--     READ COMMITTED;                          |
--   SELECT count(*) FROM account               |
--     WHERE balance > 500;   -- say 2          |
--                                              | INSERT INTO account(owner,balance)
--                                              |   VALUES('Nina', 5000);  (commits)
--   SELECT count(*) FROM account               |
--     WHERE balance > 500;   -- NOW 3 !        |   <-- phantom row appeared
--   COMMIT;                                    |
-- REPEATABLE READ in Postgres also prevents phantoms (snapshot isolation);
-- the SQL standard only guarantees SERIALIZABLE does.
-- ============================================================

-- ----------------------------------------------------------------------------
-- THE LADDER (weaker -> stronger; each prevents one more anomaly):
--
--   Level             | Dirty read | Non-repeatable | Phantom
--   ------------------+------------+----------------+---------
--   READ UNCOMMITTED  |  possible* |  possible      | possible     (*never in Postgres)
--   READ COMMITTED    |  no        |  possible      | possible     <- Postgres DEFAULT
--   REPEATABLE READ   |  no        |  no            | no in PG**   (** standard: possible)
--   SERIALIZABLE      |  no        |  no            | no           <- as if run one-at-a-time
--
-- READ COMMITTED (the default) is right ~95% of the time: each STATEMENT sees a
-- fresh snapshot of committed data. REPEATABLE READ gives a stable snapshot for
-- the whole transaction (good for reports/consistent multi-read logic).
-- SERIALIZABLE is the safest (detects write-skew, retries on conflict) but can
-- abort transactions with a serialization error you must retry.
-- Higher isolation = fewer anomalies but more conflicts/aborts/less concurrency.
-- (This is the same ladder as System Design Phase 4 — now demonstrable in SQL.)
-- ----------------------------------------------------------------------------

-- A single-session peek at the current level:
SHOW transaction_isolation;
