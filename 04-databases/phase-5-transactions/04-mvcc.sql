-- ============================================================================
-- Lesson 5.4 — MVCC: how Postgres does concurrency without read locks
-- Run 00-setup.sql first.
-- ============================================================================

-- MVCC = Multi-Version Concurrency Control. The key idea that makes Postgres
-- fast under concurrency:  READERS DON'T BLOCK WRITERS, AND WRITERS DON'T BLOCK
-- READERS.  Instead of locking rows for reads, the DB keeps MULTIPLE VERSIONS of
-- each row and shows each transaction the version valid for ITS snapshot.

-- 1. How it works — every row has hidden system columns:
--   xmin = the transaction id that CREATED this row version
--   xmax = the transaction id that DELETED/superseded it (0 if still live)
SELECT id, owner, balance, xmin, xmax FROM account ORDER BY id;

-- An UPDATE does NOT overwrite in place. It:
--   1. marks the old row version as expired (sets its xmax), and
--   2. inserts a NEW row version (with a fresh xmin).
-- A transaction sees the version whose xmin is committed & visible to its
-- snapshot and whose xmax is not — so each transaction reads a consistent
-- point-in-time view WITHOUT taking read locks.

-- 2. See it happen — xmin changes after an update (new version created):
SELECT owner, xmin FROM account WHERE owner = 'Ajay';   -- note xmin
UPDATE account SET balance = balance + 1 WHERE owner = 'Ajay';
SELECT owner, xmin FROM account WHERE owner = 'Ajay';   -- xmin is DIFFERENT -> a new row version

-- 3. The consequence you already met (Phase 4.4 / System Design Phase 7):
-- the OLD versions become DEAD TUPLES. They pile up as BLOAT until VACUUM
-- reclaims them. This is the price of MVCC's lock-free reads.
SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'account';
--   VACUUM account;   -- reclaims the dead versions for reuse

-- 4. Snapshots & isolation (ties to 5.2): a transaction's ISOLATION LEVEL just
-- decides WHEN its snapshot is taken:
--   READ COMMITTED  -> a fresh snapshot per STATEMENT (sees others' commits between statements)
--   REPEATABLE READ -> ONE snapshot for the WHOLE transaction (stable reads; PG snapshot isolation)
-- So the "isolation levels" of 5.2 are implemented ON TOP of MVCC snapshots.

-- 5. Why this matters vs lock-based DBs: in a system that locks rows for reads,
-- a long-running report can block writes (and vice versa). Under MVCC, the
-- report reads a consistent snapshot while writes proceed freely — much higher
-- concurrency. The trade-off is bloat + the need for VACUUM.

-- 6. Transaction id housekeeping (advanced, worth knowing the term):
-- xids are 32-bit and wrap around; autovacuum "freezes" old rows to prevent
-- "transaction id wraparound" (a catastrophic failure if vacuum is neglected).
-- Just know: VACUUM isn't optional maintenance — it protects correctness too.

-- ----------------------------------------------------------------------------
-- SUMMARY: MVCC keeps multiple row versions so each transaction reads a
-- consistent snapshot without read locks -> readers and writers don't block
-- each other. Isolation levels choose the snapshot timing. The cost is dead
-- tuples (bloat) that VACUUM must reclaim. This single mechanism explains
-- Postgres's concurrency behavior, the isolation levels (5.2), and the vacuum/
-- bloat story (4.4) all at once.
-- ----------------------------------------------------------------------------
