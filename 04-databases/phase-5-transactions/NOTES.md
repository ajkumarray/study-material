<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · indexing](../phase-4-indexing/NOTES.md) | [Phase 6 · postgres features ➡](../phase-6-postgres-features/NOTES.md)
<!-- /nav -->

# Phase 5 — Transactions & Concurrency: Notes (Theory)

How databases stay **correct** when many clients read and write at once. This is the SQL-level counterpart to Java Phase 5 (threads) and System Design Phase 4 (idempotency/locking) — now with the exact mechanisms.

## 5.1 — ACID & transaction control
A **transaction** groups statements into one atomic unit — all commit or none do. **ACID**:
- **Atomicity** — all-or-nothing (`COMMIT` applies everything, `ROLLBACK` undoes all). The transfer example: debit + credit must both happen or neither.
- **Consistency** — constraints/invariants (CHECK, FK, unique) hold before and after.
- **Isolation** — concurrent transactions don't corrupt each other (5.2).
- **Durability** — once committed, it survives a crash (written to the WAL/disk).

**Control:** `BEGIN … COMMIT` / `ROLLBACK`. A **failed statement aborts the whole transaction** (Postgres: "current transaction is aborted" until you rollback). **Savepoints** (`SAVEPOINT` / `ROLLBACK TO SAVEPOINT`) give partial undo within a transaction. **Autocommit** is the default — each statement is its own transaction; `BEGIN` groups several. This is exactly JDBC's `setAutoCommit(false)` pattern (Java Phase 7.2) and what Spring's `@Transactional` wraps (Spring Boot Phase 4). **Keep transactions short** — long ones hold locks and block VACUUM (Phase 4.4 / System Design Phase 7).

## 5.2 — Isolation levels & anomalies
Isolation controls how much concurrent transactions see each other's in-progress work. Three classic **read anomalies**, each prevented by a higher level:
- **Dirty read** — reading another transaction's *uncommitted* data (Postgres never allows this).
- **Non-repeatable read** — re-reading the *same row* yields a different value because another transaction committed a change in between.
- **Phantom read** — re-running the same *query* returns a different *set of rows* because another transaction inserted/deleted matching rows.

**The ladder** (weaker → stronger; each prevents one more anomaly):

| Level | Dirty | Non-repeatable | Phantom |
|---|---|---|---|
| READ UNCOMMITTED | possible* | possible | possible |
| **READ COMMITTED** (PG default) | no | possible | possible |
| REPEATABLE READ | no | no | no in PG** |
| SERIALIZABLE | no | no | no |

*never in Postgres (behaves as READ COMMITTED). **PG's REPEATABLE READ uses snapshot isolation and also blocks phantoms; the SQL standard only requires SERIALIZABLE to.

**Choosing:** READ COMMITTED (default) is right ~95% of the time — each *statement* sees a fresh snapshot of committed data. REPEATABLE READ gives one stable snapshot for the whole transaction (consistent reports, multi-read logic). SERIALIZABLE is safest — behaves as if transactions ran one at a time, detecting write-skew — but can abort with a **serialization error you must retry**. Higher isolation = fewer anomalies but more conflicts/aborts and less concurrency. (Same ladder as System Design Phase 4.)

## 5.3 — Locking, deadlocks, SELECT FOR UPDATE
The problem locks solve: the **lost update** (two transactions read the same value, each writes, one overwrites the other — Java Phase 5, System Design Phase 4). Two strategies:
- **Pessimistic** — `SELECT … FOR UPDATE` locks the matched rows; other writers *wait* until you commit. Variants: `FOR SHARE` (block writers, allow readers), `FOR UPDATE SKIP LOCKED`/`NOWAIT` (job-queue patterns — grab an unlocked row without waiting). Best for high contention or costly-to-redo work; cost is reduced throughput.
- **Optimistic** — no locks held; keep a **version column** and `UPDATE … WHERE id=? AND version=?`; if 0 rows change, someone else won → reload and retry. Best for low-contention web writes; this *is* JPA `@Version` (Spring Boot Phase 4) done by hand.

**Deadlock** — two transactions each hold a lock the other needs; neither proceeds. Postgres **detects** the cycle and aborts one (victim retries). **Prevent** by locking rows in a **consistent order** (e.g., ascending id) so a cycle is impossible — the same fix as thread deadlocks (Java Phase 5.2). Keep locked sections short.

**Advisory locks** (`pg_try_advisory_lock(key)`) are application-defined locks not tied to a row — e.g., "only one worker runs this job." **Diagnose** contention with `pg_locks` (what's locked) and `pg_stat_activity` (what each backend runs and waits on).

## 5.4 — MVCC (the mechanism behind it all)
**MVCC (Multi-Version Concurrency Control)** is how Postgres gets high concurrency: **readers don't block writers and writers don't block readers.** Instead of read locks, the DB keeps **multiple versions** of each row and shows each transaction the version valid for *its snapshot*.

Every row has hidden columns **`xmin`** (creating transaction id) and **`xmax`** (deleting/superseding id). An **UPDATE doesn't overwrite in place** — it marks the old version expired (sets `xmax`) and inserts a new version (fresh `xmin`). A transaction sees versions committed and visible to its snapshot. **Isolation levels just choose when the snapshot is taken:** READ COMMITTED = a fresh snapshot per statement; REPEATABLE READ = one snapshot for the whole transaction. So the isolation ladder (5.2) is *implemented on top of MVCC snapshots*.

**The cost:** old versions become **dead tuples** → **bloat**, which **VACUUM** reclaims (Phase 4.4 / System Design Phase 7). MVCC also needs **freezing** of old transaction ids to prevent **transaction-id wraparound** — so vacuum protects *correctness*, not just space. One mechanism (MVCC) explains Postgres's concurrency, the isolation levels, and the vacuum/bloat story all at once. (Lock-based DBs, by contrast, let a long read block writes — MVCC avoids that, trading bloat for concurrency.)
