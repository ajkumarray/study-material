<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · indexing](../phase-4-indexing/NOTES.md) | [Phase 6 · postgres features ➡](../phase-6-postgres-features/NOTES.md)
<!-- /nav -->

# Phase 5 — Transactions & Concurrency: Interview Q&A

⭐ = asked constantly. Examples use the `account(id, owner, balance, version)` table
from `00-setup.sql`.

**Q: What does ACID stand for, and can you give a concrete example of each
property?** ⭐⭐
- **Atomicity** — a transaction's statements either all happen or none do. A bank
  transfer (`UPDATE account SET balance = balance - 100 WHERE owner='A'` then
  `... + 100 WHERE owner='B'`) must never leave the debit applied without the credit.
- **Consistency** — a transaction moves the database from one valid state to another,
  respecting every constraint (`CHECK`, `FOREIGN KEY`, `UNIQUE`); if a transfer would
  drive a `CHECK (balance >= 0)` account negative, the whole transaction is rejected.
- **Isolation** — concurrent transactions don't see each other's uncommitted, in-
  progress changes; how strictly this holds is tunable via isolation level.
- **Durability** — once `COMMIT` returns successfully, the change survives a crash
  (Postgres achieves this via write-ahead logging — the WAL is flushed to disk before
  `COMMIT` acknowledges).
Interviewers love the money-transfer example specifically because atomicity and
consistency both fail visibly if you get it wrong — a partial transfer either creates
or destroys money.

**Q: What's the difference between `COMMIT`, `ROLLBACK`, and a `SAVEPOINT`?**
`BEGIN` opens a transaction; `COMMIT` makes every change since `BEGIN` permanent;
`ROLLBACK` discards all of them as if the transaction never ran. A `SAVEPOINT` is a
named checkpoint *inside* an open transaction that lets you undo only the work since
that point (`ROLLBACK TO SAVEPOINT name`) without aborting the whole transaction.
```sql
BEGIN;
UPDATE account SET balance = balance - 100 WHERE owner = 'Ajay';
SAVEPOINT before_bonus;
UPDATE account SET balance = balance + 1000000 WHERE owner = 'Ajay';  -- oops, typo
ROLLBACK TO SAVEPOINT before_bonus;   -- undoes only the bonus update
UPDATE account SET balance = balance + 100 WHERE owner = 'Priya';
COMMIT;   -- the debit and the correct credit are both kept
```
A practical use case: bulk-loading many rows in one transaction where a handful may
fail validation — savepoint before each risky insert, roll back to the savepoint on
error, and keep going instead of aborting the entire batch.

**Q: Is autocommit on by default, and why does it matter?**
Yes — in `psql` and most client libraries, every standalone statement runs in its own
implicit transaction and commits immediately unless you explicitly `BEGIN` first. This
matters because it's easy to assume a multi-statement script is atomic when it isn't:
without an explicit `BEGIN ... COMMIT`, a script that runs three `UPDATE`s and crashes
after the second one leaves the database with only two of the three changes applied.
Wrapping related statements in `BEGIN; ...; COMMIT;` is the only way to guarantee they
succeed or fail together.

**Q: Name the read anomalies and the isolation levels that prevent them.** ⭐⭐
Anomalies, weakest to worst:
- **Dirty read** — transaction A reads a row that transaction B has modified but not
  yet committed; if B rolls back, A read data that never really existed.
- **Non-repeatable read** — A reads a row twice in the same transaction and gets
  different values because B committed an update to that row in between.
- **Phantom read** — A re-runs the same filtered query twice and gets a different
  *set* of rows because B inserted/deleted a matching row in between.
Isolation levels, weakest to strongest, each preventing one more anomaly:
`READ UNCOMMITTED` (prevents nothing, and Postgres treats it identically to
`READ COMMITTED` anyway) → `READ COMMITTED` (prevents dirty reads) →
`REPEATABLE READ` (also prevents non-repeatable reads, and in Postgres's
implementation, phantoms too) → `SERIALIZABLE` (prevents all anomalies, including
write skew).

**Q: What's Postgres's default isolation level, and why is that the right default for
most applications?** ⭐
`READ COMMITTED`. Each individual *statement* within the transaction gets a fresh
snapshot of whatever has committed as of that statement's start, so you never see
another transaction's uncommitted data, but you can see different data across two
statements in the same transaction if something else committed in between. It's the
default because it gives a good balance: no dirty reads, high concurrency (very little
blocking), and it matches how most simple CRUD operations behave intuitively. You
reach for `REPEATABLE READ` or `SERIALIZABLE` specifically when your logic reads a
value and later writes based on it and can't tolerate that value changing underneath
you (e.g. computing a running balance across several queries in the same transaction).

**Q: `REPEATABLE READ` — what exactly does it add over `READ COMMITTED`?**
It takes **one snapshot for the entire transaction**, not one per statement, so every
`SELECT` inside that transaction sees the database exactly as it looked at the moment
the transaction (or its first statement) began — other transactions' commits during
your transaction are invisible to your reads. This prevents non-repeatable reads by
construction, and Postgres's particular implementation (true snapshot isolation, unlike
the SQL standard's minimum requirement) also prevents phantom reads. The trade-off:
if you try to `UPDATE` a row that another transaction already committed a change to
since your snapshot was taken, Postgres raises a serialization error you must catch
and retry, rather than silently overwriting.

**Q: What does `SERIALIZABLE` guarantee, and what's the catch?**
It guarantees the outcome is equivalent to *some* serial (one-at-a-time) execution
order of all concurrent transactions — no anomaly is possible, including **write
skew** (two transactions each read overlapping data, each makes a decision based on
what they read, and both commit changes that are individually valid but jointly
violate an invariant neither transaction's own writes touched). The catch: to
guarantee this, Postgres monitors for conflicting access patterns and will abort one
of the conflicting transactions with a serialization failure (`SQLSTATE 40001`) even
when nothing overtly "wrong" happened — the application **must** catch that error and
retry the transaction. It's the strongest correctness guarantee and the lowest
concurrency/highest abort rate, so it's reserved for logic that genuinely can't
tolerate any anomaly (e.g. enforcing an invariant across multiple rows, like "the sum
of two accounts must stay non-negative").

**Q: What is a lost update, and how do you prevent it?** ⭐⭐
Two transactions read the same row, each computes a new value from what it read, and
each writes that new value back — the second write silently overwrites the first,
losing its change, even though neither transaction did anything individually wrong.
```
Session A: reads balance = 850
Session B: reads balance = 850
Session A: writes balance = 850 + 100 = 950
Session B: writes balance = 850 - 50  = 800   -- A's +100 is gone
```
`READ COMMITTED` alone does not prevent this — both reads are of genuinely committed
data, just stale by the time each write happens. You prevent it with either:
- **Pessimistic locking** — `SELECT ... FOR UPDATE` on the row up front, so the second
  transaction's `SELECT ... FOR UPDATE` blocks until the first commits (and then sees
  the updated value).
- **Optimistic locking** — a `version` column; write only `WHERE id = ? AND
  version = ?`, and if zero rows changed, someone else won the race — reload and
  retry.

**Q: Pessimistic vs optimistic locking — how do they differ and when would you pick
each?** ⭐⭐
```sql
-- pessimistic: lock the row up front, others WAIT
BEGIN;
SELECT balance FROM account WHERE owner = 'Ajay' FOR UPDATE;
UPDATE account SET balance = balance + 100 WHERE owner = 'Ajay';
COMMIT;

-- optimistic: no lock; detect the conflict at write time via a version column
SELECT balance, version FROM account WHERE id = 1;   -- balance 850, version 3
UPDATE account
SET balance = 950, version = version + 1
WHERE id = 1 AND version = 3;   -- 0 rows updated -> someone else already won -> reload & retry
```
Pessimistic locking guarantees no wasted work (the second transaction simply waits its
turn) but reduces throughput under high contention, since writers queue up one behind
another and a slow holder blocks everyone. Optimistic locking never blocks — it scales
much better under low contention — but under high contention you pay for repeated
failed retries, and the caller must implement the retry loop. Optimistic locking is
what JPA's `@Version` annotation implements under the hood (Spring Boot Phase 4); as a
rule of thumb, use pessimistic locking for genuinely high-contention resources (a
single hot counter, a job queue slot) and optimistic locking for ordinary web-app edits
where two users rarely touch the exact same row at the same instant.

**Q: What do `FOR UPDATE`, `FOR NO KEY UPDATE`, and `FOR SHARE` each lock, and what's
`FOR UPDATE SKIP LOCKED` for?**
`FOR UPDATE` takes the strongest row lock — it blocks any other transaction from
locking, updating, or deleting that row until you commit or roll back. `FOR SHARE` is
weaker: it lets other transactions also read-lock the row (`FOR SHARE`) but blocks
anyone trying to `UPDATE`/`DELETE`/`FOR UPDATE` it — useful when you need to guarantee
a referenced row won't change or disappear while you use it, without blocking other
readers. `FOR NO KEY UPDATE` is a slightly weaker variant of `FOR UPDATE` that doesn't
conflict with `FOR KEY SHARE` locks taken by foreign-key checks, reducing unnecessary
blocking. `FOR UPDATE SKIP LOCKED` is the classic **job-queue pattern**: instead of
waiting on a row another worker already locked, it silently skips already-locked rows
and grabs the next available one, so many workers can pull distinct jobs off the same
table concurrently with zero contention:
```sql
SELECT * FROM job_queue
WHERE status = 'pending'
ORDER BY id
FOR UPDATE SKIP LOCKED
LIMIT 1;
```
`NOWAIT` is the opposite choice — fail immediately with an error instead of waiting or
skipping, useful when you'd rather surface "busy, try later" than block.

**Q: What is a deadlock, and how do you avoid one?** ⭐
Two (or more) transactions each hold a lock the other one is waiting for, forming a
cycle where neither can ever proceed.
```
Session A: BEGIN; UPDATE account SET balance = balance - 10 WHERE id = 1;  -- locks row 1
Session B: BEGIN; UPDATE account SET balance = balance - 10 WHERE id = 2;  -- locks row 2
Session A: UPDATE account SET balance = balance + 10 WHERE id = 2;  -- waits on B's lock on row 2
Session B: UPDATE account SET balance = balance + 10 WHERE id = 1;  -- waits on A's lock on row 1
-- neither can proceed -> Postgres's deadlock detector aborts one:
-- ERROR: deadlock detected
```
Postgres periodically checks the lock-wait graph for cycles and, once found, kills one
of the participating transactions (returning an error the app should retry) so the
other can proceed. The fix is the same as avoiding deadlocks between application
threads: always acquire locks on multiple rows in a **consistent order** (e.g. always
by ascending `id`), so a cyclic wait can never form, and keep transactions/locked
sections as short as possible to shrink the window where a conflict can happen.

**Q: What's an advisory lock, and when would you use one over a row lock?**
An advisory lock is an application-defined lock keyed by an arbitrary number you
choose — it isn't tied to any table row and Postgres doesn't enforce any meaning on
it; your application decides what the key represents.
```sql
SELECT pg_try_advisory_lock(42);   -- true if acquired, false if someone else holds it
-- ... do the exclusive work ...
SELECT pg_advisory_unlock(42);
```
It's useful for coordinating access to something that isn't a database row at all —
e.g. "only one instance of this cron job / migration / batch process should run at a
time across all app servers." `pg_try_advisory_lock` is non-blocking (returns
immediately), while `pg_advisory_lock` blocks until it can acquire the lock.

**Q: How would you diagnose what's blocking what in a live database?**
`pg_locks` shows every lock currently held or awaited, and `pg_stat_activity` shows
what each backend (connection) is running and what it's waiting on:
```sql
SELECT locktype, relation::regclass, mode, granted, pid
FROM pg_locks WHERE relation = 'account'::regclass;

SELECT pid, state, wait_event_type, wait_event, left(query, 60) AS query
FROM pg_stat_activity WHERE datname = current_database();
```
`granted = false` rows in `pg_locks` are the ones waiting; cross-referencing their
`pid` against `pg_stat_activity` shows exactly which query is stuck and which query is
holding the lock it wants — the first step in diagnosing a "why is everything hanging"
production incident before deciding whether to just wait it out or kill (`pg_terminate
_backend`) the blocking session.

**Q: What is MVCC, and why does Postgres use it instead of read locks?** ⭐⭐
MVCC (Multi-Version Concurrency Control) means the database keeps multiple physical
versions of each row and shows every transaction the version that's valid for *its own
snapshot*, instead of using locks to make readers wait for writers. The result:
**readers never block writers, and writers never block readers.** Every row has hidden
system columns `xmin` (the id of the transaction that created this row version) and
`xmax` (the id of the transaction that superseded/deleted it, `0` if still current).
```sql
SELECT owner, xmin FROM account WHERE owner = 'Ajay';   -- note current xmin
UPDATE account SET balance = balance + 1 WHERE owner = 'Ajay';
SELECT owner, xmin FROM account WHERE owner = 'Ajay';   -- xmin changed: a NEW row version was created
```
An `UPDATE` never overwrites a row in place — it marks the old version's `xmax` and
inserts a brand-new row version with a fresh `xmin`. Each transaction's snapshot
determines which version it's allowed to see, so a long-running report can read a
perfectly consistent point-in-time view of the table while other transactions keep
writing new versions concurrently, with nobody blocked.

**Q: How do isolation levels relate to MVCC?**
The isolation level is really just a policy for **when a transaction's MVCC snapshot
is taken and how long it's held**. `READ COMMITTED` takes a brand-new snapshot at the
start of every *statement* (so later statements in the same transaction can see
newer commits). `REPEATABLE READ` takes exactly one snapshot at the start of the
*transaction* and reuses it for every statement inside that transaction, which is
precisely why it eliminates non-repeatable reads. In other words, isolation levels
aren't a separate mechanism bolted on top — they're different consumption patterns of
the same underlying MVCC row-versioning machinery.

**Q: What's the downside of MVCC, and what does VACUUM do about it?** ⭐
Because an `UPDATE`/`DELETE` doesn't remove the old row version immediately (some
other still-open transaction's snapshot might still need to see it), old versions pile
up as **dead tuples** — this is MVCC bloat. `VACUUM` scans the table and reclaims dead
tuples whose old version is no longer visible to *any* active transaction, making that
space reusable for new rows.
```sql
SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'account';
VACUUM account;   -- reclaims dead tuples for reuse (does not shrink the file on disk; VACUUM FULL does, but locks the table)
```
Beyond bloat, Postgres transaction ids (`xid`) are 32-bit and wrap around; if vacuuming
is neglected for long enough, the database risks **transaction ID wraparound**, a far
more serious failure mode where old, unfrozen rows could appear to belong to a
transaction in the future. `autovacuum` runs continuously in the background
specifically to freeze old rows and prevent this — it is correctness-critical
maintenance, not just a disk-space nicety, which is why disabling or falling behind on
autovacuum is a common cause of Postgres production incidents.

**Q: Why should you keep transactions as short as possible?**
A long-running open transaction has three separate costs: it holds any row locks it
acquired for its entire duration, blocking other writers; it pins an old MVCC snapshot
in place, which prevents `VACUUM` from reclaiming dead tuples that are newer than that
snapshot (causing bloat to accumulate even on unrelated, frequently-updated tables);
and it widens the time window in which a deadlock can form. In practice, a single
forgotten `BEGIN` left open by a buggy connection-pooled application can quietly
degrade an entire database's performance system-wide, long before anyone notices an
explicit error — a classic on-call incident, and why monitoring for long-running/idle-
in-transaction sessions (visible in `pg_stat_activity`) is standard production
practice.
