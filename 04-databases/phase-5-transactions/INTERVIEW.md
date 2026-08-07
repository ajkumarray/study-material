<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · indexing](../phase-4-indexing/NOTES.md) | [Phase 6 · postgres features ➡](../phase-6-postgres-features/NOTES.md)
<!-- /nav -->

# Phase 5 — Transactions & Concurrency: Interview Q&A

⭐ = asked constantly.

**Q: What does ACID stand for?** ⭐⭐
Atomicity (all-or-nothing), Consistency (constraints hold before and after), Isolation (concurrent transactions don't interfere), Durability (committed data survives crashes). The transfer example (debit + credit must both happen or neither) illustrates atomicity.

**Q: What's the difference between COMMIT, ROLLBACK, and a SAVEPOINT?**
COMMIT makes all changes since BEGIN permanent; ROLLBACK undoes them all; a SAVEPOINT is a checkpoint within a transaction that you can ROLLBACK TO for a *partial* undo without aborting the whole transaction.

**Q: Name the read anomalies and the isolation levels.** ⭐⭐
Anomalies: dirty read (uncommitted data), non-repeatable read (same row changes on re-read), phantom read (same query returns different rows). Levels weakest→strongest: READ UNCOMMITTED, READ COMMITTED, REPEATABLE READ, SERIALIZABLE — each prevents one more anomaly. READ COMMITTED is Postgres's default.

**Q: What's the default isolation level and why?** ⭐
READ COMMITTED. Each statement sees a fresh snapshot of committed data — a good balance of correctness and concurrency for most workloads. You raise it (REPEATABLE READ / SERIALIZABLE) only when you need stable multi-read consistency or full serializability.

**Q: What does SERIALIZABLE guarantee, and what's the catch?**
Transactions behave as if run one at a time (no anomalies, including write-skew). The catch: the DB may abort a transaction with a serialization error under conflicting concurrent access, so the application must catch it and retry. Highest correctness, lowest concurrency.

**Q: What is a lost update and how do you prevent it?** ⭐⭐
Two transactions read the same value, each computes and writes a new one, so one overwrites the other. Prevent with pessimistic locking (`SELECT … FOR UPDATE` serializes writers) or optimistic locking (a version column: `UPDATE … WHERE version = ?`, retry if 0 rows changed). Same problem as System Design Phase 4.

**Q: Optimistic vs pessimistic locking?** ⭐⭐
Pessimistic: lock the row up front (`FOR UPDATE`); others wait — good for high contention, costs throughput. Optimistic: no lock, detect conflicts at write time via a version column and retry — good for low contention, scales well. Optimistic is JPA's `@Version`.

**Q: What is a deadlock and how do you avoid it?** ⭐
Two transactions each hold a lock the other needs, so neither proceeds. The DB detects the cycle and aborts one (retry it). Avoid by acquiring locks in a consistent order (e.g., always ascending id) and keeping transactions short — same principle as thread deadlocks.

**Q: What is MVCC and why does Postgres use it?** ⭐⭐
Multi-Version Concurrency Control: the DB keeps multiple versions of each row and shows each transaction the version valid for its snapshot, so **readers don't block writers and writers don't block readers** — high concurrency without read locks. Implemented via hidden `xmin`/`xmax` columns; an UPDATE creates a new version rather than overwriting.

**Q: What's the downside of MVCC?** ⭐
Old row versions (dead tuples) accumulate as bloat and must be reclaimed by VACUUM. Neglected vacuum causes bloat and, eventually, transaction-id wraparound risk. So MVCC trades some maintenance overhead (and disk) for lock-free reads.

**Q: How do isolation levels relate to MVCC?**
The isolation level decides *when* a transaction takes its MVCC snapshot: READ COMMITTED takes a new snapshot per statement; REPEATABLE READ takes one snapshot for the whole transaction. Isolation is implemented on top of MVCC's row versions and snapshots.

**Q: `SELECT ... FOR UPDATE SKIP LOCKED` — what's it for?**
A job-queue pattern: each worker locks and grabs a different unlocked row without waiting on rows other workers hold, so many workers process a queue concurrently without contention.

**Q: Why keep transactions short?**
Long transactions hold locks (blocking other writers), keep an old MVCC snapshot open (preventing VACUUM from reclaiming dead tuples → bloat), and increase deadlock risk. A single forgotten open transaction can degrade the whole database (System Design Phase 7).
