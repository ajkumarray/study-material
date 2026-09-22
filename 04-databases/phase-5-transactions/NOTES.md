<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · indexing](../phase-4-indexing/NOTES.md) | [Phase 6 · postgres features ➡](../phase-6-postgres-features/NOTES.md)
<!-- /nav -->

# Phase 5 — Transactions & Concurrency: Notes (Theory)

How databases stay **correct** when many clients read and write at once. Every example
below runs against the `account` table from `00-setup.sql`:

```sql
CREATE TABLE account (
    id       INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner    TEXT NOT NULL,
    balance  NUMERIC(12,2) NOT NULL CHECK (balance >= 0),
    version  INT NOT NULL DEFAULT 0
);
-- id=1 Ajay 1000.00 | id=2 Ravi 500.00 | id=3 Meera 750.00
```

This is the SQL-level counterpart to Java Phase 5 (threads/locks) and System Design
Phase 4 (idempotency/locking) — now with the exact mechanisms a relational database
uses under the hood.

---

## 5.1 — ACID Properties

**ACID** is the set of four guarantees a database transaction makes so that concurrent,
possibly-failing operations still leave the data correct. It's the contract a
transaction promises the application.

**Key Concepts**
- **Atomicity**: A transaction's statements are all-or-nothing. `COMMIT` applies every
  change since `BEGIN`; `ROLLBACK` (or a mid-transaction error) undoes all of them —
  never a partial result.
- **Consistency**: The database moves from one valid state to another. Constraints
  (`CHECK`, `NOT NULL`, foreign keys, `UNIQUE`) are enforced at the end of the
  transaction (or per-statement), so an invariant that held before the transaction
  still holds after it commits.
- **Isolation**: Concurrent transactions don't see each other's uncommitted, in-progress
  work in ways that corrupt results. How *much* isolation is tunable (5.5–5.9).
- **Durability**: Once a transaction reports `COMMIT`, the result survives a crash — it
  has been written to the Write-Ahead Log (WAL) and will be replayed on recovery even
  if the server dies before the data pages hit disk.

**Worked example — the money-transfer problem atomicity solves**

```sql
BEGIN;                                                        -- start the transaction
    UPDATE account SET balance = balance - 200 WHERE owner = 'Ajay';   -- debit
    UPDATE account SET balance = balance + 200 WHERE owner = 'Ravi';   -- credit
COMMIT;                                                       -- make BOTH permanent, atomically

SELECT owner, balance FROM account ORDER BY id;
--  owner | balance
-- -------+---------
--  Ajay  |  800.00
--  Ravi  |  700.00
--  Meera |  750.00
```

If these two `UPDATE`s ran outside a transaction (each auto-committing on its own,
5.4) and the server crashed between them, Ajay would have lost 200 that never reached
Ravi — money would simply vanish. Wrapping both statements in `BEGIN … COMMIT` makes
them one atomic unit: either the whole transfer happens, or none of it does.

**Consistency in action — a CHECK constraint refusing an invalid state**

```sql
BEGIN;
    UPDATE account SET balance = balance - 100    WHERE owner = 'Meera';  -- ok so far, 650.00
    UPDATE account SET balance = balance - 999999 WHERE owner = 'Meera';  -- CHECK violation!
-- ERROR:  new row for relation "account" violates check constraint "account_balance_check"
-- DETAIL:  Failing row contains (3, Meera, -999249.00, 0).
COMMIT;
-- ERROR:  current transaction is aborted, commands ignored until end of transaction block
SELECT owner, balance FROM account WHERE owner = 'Meera';     -- 750.00 — unchanged, even the -100
```

In this example, the second `UPDATE` violates `CHECK (balance >= 0)`, so Postgres
rejects it and marks the *entire transaction* as aborted — every later statement,
including the `COMMIT` itself, is refused until you `ROLLBACK`. Because the first
`UPDATE` (the `-100`) was never committed either, `Meera`'s balance is still `750.00`.
This is consistency and atomicity working together: the constraint stopped an invalid
state, and atomicity ensured the transaction didn't half-apply.

**Why it's useful**
- Every multi-statement business operation (transfer funds, place an order and
  decrement stock, create a user and their default settings row) needs atomicity —
  without it, a crash or error mid-sequence leaves the database in an impossible state.
- Durability is why you can tell a user "your payment succeeded" the instant `COMMIT`
  returns, even though the underlying disk write may still be in flight (WAL already
  has it).
- This is exactly what JDBC's `Connection.setAutoCommit(false)` +
  `commit()`/`rollback()` (Java Phase 7.2) and Spring's `@Transactional` (Spring Boot
  Phase 4) wrap for you in application code.

**Summary**
- ACID = Atomicity, Consistency, Isolation, Durability — the four promises a
  transaction makes.
- Atomicity: all-or-nothing. A failed statement aborts the whole transaction in
  Postgres, not just that statement.
- Consistency: constraints hold before and after — `CHECK`/`NOT NULL`/`UNIQUE`/FK.
- Durability: committed = survives a crash, because it's in the WAL before `COMMIT`
  returns.
- Isolation is the deep one — it gets levels, anomalies, and MVCC (rest of this file).

---

## 5.2 — Transaction Control: BEGIN, COMMIT, ROLLBACK

`BEGIN` opens a transaction block; every statement until `COMMIT` or `ROLLBACK`
belongs to that one atomic unit. `COMMIT` makes the changes permanent; `ROLLBACK`
discards them as if they never ran.

**Key Concepts**
- **`BEGIN` (or `START TRANSACTION`)**: Opens a transaction. Statements run inside it
  are not visible to other sessions until `COMMIT`.
- **`COMMIT`**: Ends the transaction, making every change since `BEGIN` permanent and
  visible to other transactions.
- **`ROLLBACK`**: Ends the transaction, undoing every change since `BEGIN` — the table
  looks exactly as it did before `BEGIN`.
- **Implicit rollback on error**: In Postgres, a statement that errors (constraint
  violation, syntax error, etc.) doesn't just fail itself — it poisons the whole
  transaction. Every subsequent statement, including `COMMIT`, errors with "current
  transaction is aborted" until you issue `ROLLBACK`.
- **You can read your own writes mid-transaction**: A `SELECT` inside the same
  transaction sees the uncommitted changes you just made — isolation only concerns
  *other* transactions (5.5).

**Worked example — rehearsing a risky change with ROLLBACK**

```sql
BEGIN;
    UPDATE account SET balance = 0 WHERE owner = 'Ajay';    -- oops, wipe it out
    SELECT owner, balance FROM account WHERE owner = 'Ajay'; -- 0.00 — you see your own write
ROLLBACK;                                                     -- undo it all
SELECT owner, balance FROM account WHERE owner = 'Ajay';      -- 1000.00 — unchanged, real balance
```

Inside the transaction, the `SELECT` shows `balance = 0` because a transaction always
sees its own uncommitted changes. But once `ROLLBACK` runs, it's as if the `UPDATE`
never happened — the final `SELECT` (run after the transaction ended) sees the
original `1000.00`. This is a safe way to try a change, inspect the result, and bail
out before anything becomes visible to anyone else.

**Why it's useful**
- Lets you stage several dependent writes and only commit if every one of them
  succeeded — the core building block behind ORMs' and web frameworks' request-scoped
  transactions.
- `ROLLBACK` is how you safely test a destructive `UPDATE`/`DELETE` in production: run
  it inside `BEGIN`, `SELECT` to check the damage, `ROLLBACK` if it's wrong.

**Summary**
- `BEGIN … COMMIT` = an atomic unit; `BEGIN … ROLLBACK` = rehearse and discard.
- A failed statement aborts the *whole* transaction in Postgres — you must `ROLLBACK`
  before you can run anything else in that session.
- Reads inside a transaction always see that transaction's own uncommitted writes.

---

## 5.3 — SAVEPOINT (Partial Rollback)

A **savepoint** is a named checkpoint inside a transaction that you can roll back to
without discarding everything since `BEGIN` — a nested "undo point" for partial
recovery from an error.

**Key Concepts**
- **`SAVEPOINT name`**: Marks the current point in the transaction.
- **`ROLLBACK TO SAVEPOINT name`**: Undoes everything since that savepoint, but keeps
  everything before it and keeps the transaction open (not aborted) — you can keep
  working and eventually `COMMIT`.
- **`RELEASE SAVEPOINT name`**: Forgets the savepoint (keeps its changes); rarely
  needed explicitly since `COMMIT`/`ROLLBACK` clean up all savepoints anyway.
- **Recovers from errors mid-transaction**: Unlike a bare failed statement (which
  aborts the whole transaction, 5.2), a statement that fails after a savepoint can be
  undone with `ROLLBACK TO SAVEPOINT`, leaving the rest of the transaction usable.

**Worked example**

```sql
BEGIN;
    UPDATE account SET balance = balance + 50 WHERE owner = 'Ajay';   -- a bonus: 1050.00
    SAVEPOINT after_bonus;                                            -- checkpoint here
    UPDATE account SET balance = balance - 5000 WHERE owner = 'Ajay'; -- a step we regret
    ROLLBACK TO SAVEPOINT after_bonus;                                -- undo ONLY back to here
    -- the +50 survives; the -5000 is gone
COMMIT;
SELECT owner, balance FROM account WHERE owner = 'Ajay';   -- 1050.00
```

In this example, the bonus (`+50`) happens first and is checkpointed with
`SAVEPOINT after_bonus`. The next `UPDATE` (`-5000`) is a mistake, so
`ROLLBACK TO SAVEPOINT after_bonus` undoes just that statement while keeping the
bonus and keeping the transaction alive. The final `COMMIT` persists only the `+50`.
Without the savepoint, an *error* in that second statement (not just a "mistake" we
catch ourselves) would have aborted the entire transaction, losing the bonus too.

**Why it's useful**
- ORMs use savepoints to implement nested `@Transactional` boundaries — an inner
  transaction that fails doesn't necessarily have to abort the outer one.
- Batch scripts can savepoint before each risky step and roll back just that step on
  error, continuing the batch instead of aborting everything already validated.

**Summary**
- `SAVEPOINT` = a checkpoint you can roll back to without losing earlier work in the
  same transaction.
- `ROLLBACK TO SAVEPOINT` keeps the transaction alive (not aborted); a plain failed
  statement without a savepoint aborts the whole transaction.
- Nested savepoints are how ORMs implement nested transaction semantics on top of a
  single flat database transaction.

---

## 5.4 — Autocommit Mode

**Autocommit** is Postgres's default: every individual statement is its own implicit
transaction, committed immediately if it succeeds. `BEGIN` turns this off until the
matching `COMMIT`/`ROLLBACK`, letting you group multiple statements into one
transaction.

**Key Concepts**
- **Default behavior**: Run `UPDATE account SET balance = 900 WHERE id = 1;` with no
  `BEGIN` around it, and it commits the instant it finishes — as if you'd typed
  `BEGIN; UPDATE …; COMMIT;`.
- **`BEGIN` suspends autocommit**: statements inside a `BEGIN…COMMIT` block are not
  individually committed; only the final `COMMIT` makes them all visible together.
- **Client-driven equivalent**: this maps directly to JDBC's
  `connection.setAutoCommit(false)` → run statements → `connection.commit()`, and to
  what Spring's `@Transactional` annotation manages automatically.

**Worked example — the difference autocommit makes**

```sql
-- Without BEGIN: each statement commits on its own (autocommit ON)
UPDATE account SET balance = balance - 200 WHERE owner = 'Ajay';   -- commits immediately
-- (if the app crashes right here, Ravi never gets credited — no atomicity)
UPDATE account SET balance = balance + 200 WHERE owner = 'Ravi';   -- commits immediately

-- With BEGIN: both updates are one atomic unit
BEGIN;
    UPDATE account SET balance = balance - 200 WHERE owner = 'Ajay';
    UPDATE account SET balance = balance + 200 WHERE owner = 'Ravi';
COMMIT;   -- only now does either change become visible to other sessions
```

Without `BEGIN`, each `UPDATE` is its own transaction, so a crash between the two
statements loses money (5.1's atomicity problem). Wrapping them in `BEGIN … COMMIT`
suspends autocommit so both statements succeed or fail together, and neither is
visible to another session until the `COMMIT`.

**Why it's useful**
- Explains why a raw `psql`/JDBC statement outside a transaction "just works" without
  ever calling commit — you're already in autocommit mode.
- Understanding autocommit is essential for debugging "why did my change already show
  up in another session" (autocommit committed it instantly) vs. "why isn't my change
  visible anywhere" (you're inside an uncommitted `BEGIN` block).

**Summary**
- Autocommit is Postgres's default: no `BEGIN` needed for a single statement to be
  durable.
- `BEGIN` groups several statements into one atomic transaction, deferring visibility
  until `COMMIT`.
- Keep transactions **short** — a long open transaction holds locks (5.11) and blocks
  `VACUUM` from reclaiming old row versions (5.20).

---

## 5.5 — Isolation Levels & Read Anomalies

**Isolation** controls how much a transaction can see of other transactions' concurrent,
in-progress work. The SQL standard defines three classic **read anomalies**; each
isolation level, from weakest to strongest, prevents one more of them.

**Key Concepts**
- **Dirty read**: reading another transaction's *uncommitted* data. If that transaction
  rolls back, you've read a value that never truly existed.
- **Non-repeatable read**: re-reading the *same row* within one transaction returns a
  *different value* because another transaction committed a change to it in between.
- **Phantom read**: re-running the same *query* (a `WHERE` filter, not a single row)
  within one transaction returns a *different set of rows* because another
  transaction inserted or deleted matching rows in between.
- **Write skew** (a fourth, less-classic anomaly): two transactions each read
  overlapping data, each independently makes a decision that's individually valid, but
  the *combination* violates an invariant neither transaction's own check would have
  caught. Only `SERIALIZABLE` prevents this (5.8).
- **Setting the level**: `BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;` (or
  `REPEATABLE READ` / `SERIALIZABLE`). Check the current session's level with
  `SHOW transaction_isolation;`.

**Worked example — non-repeatable read (two sessions)**

```
Session A                                    | Session B
----------------------------------------------+------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL              |
  READ COMMITTED;                              |
SELECT balance FROM account                    |
  WHERE owner='Ajay';   -- 1000.00             |
                                               | UPDATE account SET balance = 1200
                                               |   WHERE owner='Ajay';  (autocommits)
SELECT balance FROM account                    |
  WHERE owner='Ajay';   -- NOW 1200.00 !       |   <-- non-repeatable read
COMMIT;                                        |
```

In this example, Session A reads `Ajay`'s balance twice within the same transaction
and gets two different answers, because Session B committed a change in between and
`READ COMMITTED` gives each statement a fresh look at committed data. Under
`REPEATABLE READ`, A's second `SELECT` would still return `1000.00` — its snapshot is
fixed for the whole transaction (5.7).

**Worked example — phantom read (two sessions)**

```
Session A                                    | Session B
----------------------------------------------+------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL              |
  READ COMMITTED;                              |
SELECT count(*) FROM account                   |
  WHERE balance > 500;   -- 2 (Ajay, Meera)    |
                                               | INSERT INTO account(owner,balance)
                                               |   VALUES('Nina', 5000);  (commits)
SELECT count(*) FROM account                   |
  WHERE balance > 500;   -- NOW 3 !            |   <-- a phantom row appeared
COMMIT;                                        |
```

Here the *query itself* is re-run, not a single row lookup. Session B inserts a new
row that matches Session A's `WHERE` filter and commits it mid-transaction, so A's
second count includes a row that didn't exist when the transaction started. Postgres's
`REPEATABLE READ` also prevents this (it uses snapshot isolation, which is stronger
than the SQL standard strictly requires at that level); the standard only mandates
`SERIALIZABLE` block phantoms.

**Comparison table — anomalies prevented per level**

| Level | Dirty Read | Non-repeatable Read | Phantom Read | Write Skew |
|---|---|---|---|---|
| READ UNCOMMITTED | possible* | possible | possible | possible |
| READ COMMITTED (Postgres default) | no | possible | possible | possible |
| REPEATABLE READ | no | no | no in Postgres** | possible |
| SERIALIZABLE | no | no | no | no |

\* Never actually happens in Postgres — its `READ UNCOMMITTED` behaves identically to
`READ COMMITTED`; Postgres has no true dirty-read level.
\*\* Postgres's `REPEATABLE READ` is snapshot isolation, which happens to block
phantoms too; the SQL standard only *requires* `SERIALIZABLE` to.

**Why it's useful**
- Lets you reason precisely about what bugs are even *possible* at a given isolation
  level instead of guessing — "could this report show inconsistent numbers" is a
  question about non-repeatable/phantom reads, and the answer depends only on the
  isolation level in use.
- This is the same anomaly ladder used in System Design Phase 4 (idempotency/
  correctness under concurrency) — here it's demonstrable in real SQL.

**Summary**
- Three classic anomalies: dirty read, non-repeatable read, phantom read — plus write
  skew, caught only by `SERIALIZABLE`.
- Each isolation level going up the ladder prevents one more anomaly, at the cost of
  more conflicts/aborts and less concurrency.
- Postgres never allows dirty reads, even at its weakest level.

---

## 5.6 — READ COMMITTED (Postgres Default)

**READ COMMITTED** is the isolation level Postgres uses if you don't specify one: each
individual *statement* within a transaction sees a fresh snapshot of whatever data was
committed at the moment that statement started.

**Key Concepts**
- **Per-statement snapshot**: Every `SELECT`/`UPDATE`/`DELETE` gets its own snapshot,
  taken right before it runs — not one snapshot for the whole transaction.
- **Never sees uncommitted data**: it blocks dirty reads (no isolation level in
  Postgres allows them).
- **Allows non-repeatable and phantom reads**: because each statement's snapshot can
  differ from the previous statement's, if you re-read between statements, you can see
  another transaction's committed changes.
- **An `UPDATE` re-checks its `WHERE` clause against the latest committed row** if the
  row was concurrently modified — Postgres will retry the `WHERE` evaluation against
  the newer version rather than raising an error (this is part of why READ COMMITTED
  rarely produces serialization failures).

**Worked example**

```sql
SHOW transaction_isolation;   -- read committed   (Postgres default with no BEGIN level set)

BEGIN;  -- implicitly READ COMMITTED
    SELECT balance FROM account WHERE owner = 'Ajay';   -- snapshot #1, say 1000.00
    -- ... meanwhile another session commits a change to Ajay's row ...
    SELECT balance FROM account WHERE owner = 'Ajay';   -- snapshot #2, sees the new committed value
COMMIT;
```

Each `SELECT` above is a separate statement, so each gets its own snapshot of
committed data at the instant it runs. If another transaction committed a change to
`Ajay`'s row between the two `SELECT`s, the second one sees the new value — this is
exactly the non-repeatable-read scenario from 5.5, and it's expected, not a bug, at
this level.

**Why it's useful**
- It's the right choice roughly 95% of the time: each statement working off "whatever
  is currently committed" matches how most CRUD applications actually want to behave
  (see the latest state, don't hold a stale view for the whole request).
- Because it doesn't hold a long-lived snapshot, it minimizes MVCC bloat and
  serialization-failure risk compared to `REPEATABLE READ`/`SERIALIZABLE` (5.20).

**Summary**
- Postgres's default; no `BEGIN TRANSACTION ISOLATION LEVEL …` needed to get it.
- Fresh snapshot per statement, not per transaction.
- Never dirty reads; can have non-repeatable and phantom reads.

---

## 5.7 — REPEATABLE READ

**REPEATABLE READ** gives a transaction one single snapshot, taken at its first
query, that it keeps for its entire duration — every statement in the transaction sees
the database exactly as it looked at that first moment, no matter what else commits
meanwhile.

**Key Concepts**
- **One snapshot for the whole transaction**: unlike `READ COMMITTED`'s per-statement
  snapshot, all reads in a `REPEATABLE READ` transaction are consistent with each
  other.
- **Prevents non-repeatable reads by definition**: re-reading the same row always
  returns what it was at snapshot time.
- **Prevents phantom reads in Postgres**: because Postgres implements this level via
  true snapshot isolation (MVCC, 5.18) rather than just row-level locking, newly
  inserted rows from other transactions are invisible to the whole transaction, not
  just to already-read rows.
- **Can still throw a serialization failure**: if this transaction tries to *write* a
  row that another transaction has concurrently committed a change to, Postgres raises
  `ERROR: could not serialize access due to concurrent update` (SQLSTATE `40001`) —
  the application must catch this and retry the whole transaction.
- **Does not prevent write skew**: two REPEATABLE READ transactions can each read
  data, each make an independently "safe" decision based on their own snapshot, and
  commit a combination that violates an invariant — because neither transaction wrote
  to a row the other read.

**Worked example — stable reads across the whole transaction**

```
Session A                                    | Session B
----------------------------------------------+------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL              |
  REPEATABLE READ;                             |
SELECT balance FROM account                    |
  WHERE owner='Ajay';   -- 1000.00             |
                                               | UPDATE account SET balance = 1200
                                               |   WHERE owner='Ajay';  (commits)
SELECT balance FROM account                    |
  WHERE owner='Ajay';   -- STILL 1000.00       |   <-- stable, unlike READ COMMITTED
COMMIT;                                        |
```

Session A's whole transaction is pinned to the snapshot taken at its first statement,
so even though Session B commits a change in between, A's second read still shows the
original `1000.00`. This is the fix for the non-repeatable-read example in 5.5.

**Why it's useful**
- Ideal for reports or multi-step read logic that needs internally consistent numbers
  — e.g., computing a total from several `SELECT`s that must agree with each other,
  even if the underlying table changes while the report runs.
- Cheaper than `SERIALIZABLE` (no full conflict-graph tracking) while still giving
  strong read consistency — a good middle ground when you need stable reads but the
  writes in your workload rarely target the exact same rows.

**Summary**
- One snapshot for the entire transaction, taken at the first statement.
- Fixes non-repeatable reads and (in Postgres specifically) phantom reads.
- Concurrent writes to the *same row* can still cause a serialization failure your app
  must retry; write skew across *different* rows is still possible — only
  `SERIALIZABLE` closes that gap.

---

## 5.8 — SERIALIZABLE & Serialization Failures

**SERIALIZABLE** is the strongest isolation level: Postgres guarantees the outcome is
equivalent to *some* serial (one-at-a-time) execution order of the concurrent
transactions, even though they actually ran concurrently. It achieves this using
Serializable Snapshot Isolation (SSI), which monitors read/write dependencies between
transactions and aborts one if it detects a pattern that could produce a non-serializable
result.

**Key Concepts**
- **Strongest guarantee**: no dirty reads, no non-repeatable reads, no phantom reads,
  and no write skew — the full anomaly ladder is closed.
- **Detects dangerous structures, not just direct conflicts**: SSI can abort a
  transaction even if it never touched the same row another transaction wrote, if the
  pattern of reads and writes across transactions could only have happened in an
  order that isn't truly serial.
- **Serialization failure**: Postgres raises
  `ERROR: could not serialize access due to read/write dependencies among transactions`
  (SQLSTATE `40001`) when it detects this. This is not a bug in your query — it's the
  database refusing to let a genuinely unsafe interleaving stand.
- **Retry is mandatory**: application code using `SERIALIZABLE` must be written to
  catch this error and re-run the entire transaction from `BEGIN` — the transaction
  that failed did nothing wrong, it just lost a race that the database can only
  resolve by aborting one side.
- **Lower concurrency, by design**: because it can abort transactions that don't even
  overlap on the same rows, `SERIALIZABLE` sees more aborts under contention than
  `REPEATABLE READ`.

**Worked example — the write-skew scenario `SERIALIZABLE` closes**

Imagine a rule: "the sum of Ajay's and Ravi's balances must never both drop below 200
at once" — each session only checks *its own* account before withdrawing, trusting the
other account is fine.

```
Session A (SERIALIZABLE)                     | Session B (SERIALIZABLE)
----------------------------------------------+------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL              | BEGIN TRANSACTION ISOLATION LEVEL
  SERIALIZABLE;                                |   SERIALIZABLE;
SELECT balance FROM account                    | SELECT balance FROM account
  WHERE owner='Ravi';    -- 500 (>= 200, ok)   |   WHERE owner='Ajay';   -- 1000 (>= 200, ok)
UPDATE account SET balance = balance - 900     | UPDATE account SET balance = balance - 900
  WHERE owner='Ajay';    -- Ajay now 100       |   WHERE owner='Ravi';   -- Ravi now -400 (blocked by CHECK anyway,
COMMIT;   -- succeeds                          |   -- but even without the CHECK, SSI would detect
                                               |   -- the dependency cycle and abort one txn here
COMMIT;                                        | ERROR: could not serialize access due to
                                               | read/write dependencies among transactions
```

In this example, each transaction reads the *other* account to decide whether its own
withdrawal is safe, then writes its *own* account. Under `REPEATABLE READ`, both
could commit — each saw a snapshot where the rule held, even though the combined
result might violate it. `SERIALIZABLE`'s SSI detects the cross-transaction read/write
dependency cycle (A read what B wrote, B read what A wrote) and aborts one side,
forcing the application to retry it — at which point it re-reads the *actual* current
state and makes a safe decision.

**Why it's useful**
- The only isolation level that lets you write "check an invariant across multiple
  rows, then act" logic without hand-rolling explicit locks, and be sure the
  invariant survives concurrent transactions.
- Common in financial and inventory systems where a subtle cross-row invariant (not
  just a single-row constraint) must never be violated, and retry-on-conflict is
  cheaper to build than getting every lock ordering right by hand.

**Summary**
- Behaves as if transactions ran one at a time; the only level that prevents write
  skew.
- Uses SSI to detect dependency cycles, not just direct row conflicts.
- Applications **must** catch SQLSTATE `40001` and retry the whole transaction — this
  is expected, normal operation, not an edge case to ignore.
- Trade-off: correctness for concurrency — expect more aborts under contention than
  `REPEATABLE READ`.

---

## 5.9 — Choosing an Isolation Level

**Key Concepts**
- **Default to `READ COMMITTED`** unless you have a specific reason not to — it's what
  Postgres uses with no `BEGIN TRANSACTION ISOLATION LEVEL` clause, and it matches
  most application code's expectations.
- **Reach for `REPEATABLE READ`** when a single transaction needs multiple reads to
  agree with each other (reports, multi-step read-then-compute logic) but you're not
  worried about cross-row write-skew invariants.
- **Reach for `SERIALIZABLE`** when correctness of a cross-row invariant matters more
  than raw throughput, and your application is written to retry on serialization
  failure.
- **Higher isolation always trades concurrency for correctness** — more anomalies
  prevented means more conflicts detected means more aborts/waits under load.

**Comparison table**

| Level | Snapshot Scope | Concurrency | Typical Use | Retry Needed? |
|---|---|---|---|---|
| READ COMMITTED | Per statement | Highest | Default CRUD workloads | Rarely |
| REPEATABLE READ | Per transaction | Medium | Reports, stable multi-read logic | Sometimes (same-row write conflicts) |
| SERIALIZABLE | Per transaction + dependency tracking | Lowest | Cross-row invariants, financial correctness | Yes, by design |

**Why it's useful**
- Picking the isolation level is a real production decision, not a formality — too
  low and you ship subtle bugs (stale reads, lost invariants); too high and you ship a
  system that throws unnecessary retries under load it could otherwise handle.

**Summary**
- READ COMMITTED (default) for almost everything.
- REPEATABLE READ when a transaction needs a stable, self-consistent view.
- SERIALIZABLE when a cross-row invariant must never be violated and your app can
  retry.
- This is the same ladder used in System Design Phase 4 — now demonstrable in SQL.

---

## 5.10 — The Lost Update Problem

The **lost update** anomaly is the concrete bug that row-level locking and optimistic
concurrency both exist to prevent: two transactions read the same value, each computes
a new value from it, and the second write silently overwrites the first — one of the
two updates is lost as if it never happened.

**Key Concepts**
- **Root cause**: read-then-write logic (`read balance, compute new balance, write
  balance`) done in the application, where the write doesn't check whether the value
  changed since the read.
- **Not prevented by `READ COMMITTED` alone**: each individual statement is
  consistent, but the *sequence* "read, compute in app code, write" spans multiple
  statements/round-trips, and nothing stops another transaction from writing in
  between.
- **Two fixes**: pessimistic locking (5.11 — lock the row so nobody else can write
  until you're done) or optimistic locking (5.13 — don't lock, but detect the
  conflict at write time and retry).

**Worked example — how it happens**

```
Session A                                    | Session B
----------------------------------------------+------------------------------------------
SELECT balance FROM account WHERE id=1;        | SELECT balance FROM account WHERE id=1;
  -- reads 1000.00                             |   -- also reads 1000.00
-- app computes 1000 + 100 = 1100              | -- app computes 1000 - 50 = 950
UPDATE account SET balance = 1100              |
  WHERE id = 1;   -- writes 1100               |
                                               | UPDATE account SET balance = 950
                                               |   WHERE id = 1;   -- writes 950, OVERWRITES A's +100
-- final balance: 950.00 — A's deposit of 100 is LOST, even though no error occurred
```

Both sessions read the same starting value (`1000.00`) before either had written
anything, so each computed its new value from a value that was about to become stale.
Session B's write clobbers Session A's, and there's no error, no warning — the
`+100` deposit simply vanishes. Neither `BEGIN…COMMIT` alone nor `READ COMMITTED`
prevents this, because the problem spans multiple statements with application logic
in between them.

**Why it's useful**
- This is the exact bug behind "I updated my profile but my change disappeared," or
  "two orders decremented the same inventory count and it ended up wrong" — a
  read-modify-write race is one of the most common concurrency bugs in real systems.
- Recognizing this pattern (read a value in one step, write a derived value in
  another) is the trigger for reaching for `FOR UPDATE` or a version column.

**Summary**
- Lost update = read-modify-write done by two transactions concurrently, second write
  wins, first write's effect disappears silently.
- `READ COMMITTED` alone does not prevent it — the race is between application-level
  steps, not within one statement.
- Fixed by pessimistic locking (`FOR UPDATE`) or optimistic locking (version column) —
  next two sections.

---

## 5.11 — Pessimistic Locking: SELECT ... FOR UPDATE / FOR SHARE

**Pessimistic locking** assumes conflicts are likely, so it locks rows up front:
`SELECT ... FOR UPDATE` takes a row-level lock that makes other transactions *wait*
until this one commits or rolls back before they can lock or modify the same rows.

**Key Concepts**
- **`FOR UPDATE`**: locks the selected rows against concurrent `UPDATE`, `DELETE`, or
  another `FOR UPDATE`/`FOR SHARE` — other transactions trying to lock the same rows
  block until this transaction ends.
- **`FOR SHARE`**: a weaker lock — blocks other transactions from *writing* to the
  locked rows, but multiple transactions can hold `FOR SHARE` on the same row at once
  (readers don't block readers).
- **`FOR NO KEY UPDATE`**: a weaker variant of `FOR UPDATE`, automatically used by
  plain `UPDATE`s that don't touch key columns; conflicts with fewer lock modes than
  full `FOR UPDATE`, allowing slightly more concurrency.
- **Blocking, not failing**: by default, a transaction that can't get the lock *waits*
  — it doesn't error. Use `NOWAIT` to fail immediately instead of waiting, or
  `SKIP LOCKED` to silently skip already-locked rows (5.12).
- **Locks are released at transaction end**: `COMMIT` or `ROLLBACK` releases every
  lock the transaction held — there is no separate "unlock" statement for row locks.

**Worked example — FOR UPDATE preventing the lost update**

```
Session A                                    | Session B
----------------------------------------------+------------------------------------------
BEGIN;                                         |
SELECT balance FROM account                    |
  WHERE owner='Ajay' FOR UPDATE;  -- LOCKED    |
  -- 1000.00                                   |
                                               | BEGIN;
                                               | SELECT balance FROM account
                                               |   WHERE owner='Ajay' FOR UPDATE; -- BLOCKS, waits...
UPDATE account SET balance = balance + 100     |
  WHERE owner='Ajay';   -- 1100.00             |
COMMIT;                                        |   ...B now unblocks, and its FOR UPDATE returns 1100.00
                                               | UPDATE account SET balance = balance - 50
                                               |   WHERE owner='Ajay';  -- 1050.00, correct
                                               | COMMIT;
```

Session A locks `Ajay`'s row with `FOR UPDATE` before reading it. Session B's own
`FOR UPDATE` on the same row has to *wait* — it doesn't get a stale `1000.00`; it
blocks until A commits, then reads the *updated* `1100.00`. Because B's read now
reflects A's write, B's subsequent `-50` correctly lands on `1050.00` instead of
clobbering A's change — the lost update from 5.10 cannot happen.

**Comparison table — FOR UPDATE vs FOR SHARE**

| | `FOR UPDATE` | `FOR SHARE` |
|---|---|---|
| Blocks other writers | Yes | Yes |
| Blocks other readers using the same clause | Yes (blocks other `FOR UPDATE`/`FOR SHARE`) | No (multiple `FOR SHARE` can coexist) |
| Typical use | "I'm about to modify this row" | "I read this row as part of a decision; don't let it change under me, but other readers are fine" |
| Example | Debit before credit in a transfer | Checking a foreign-key-referenced row won't be deleted mid-transaction |

**Why it's useful**
- The direct fix for the lost-update problem in high-contention workloads (many
  transactions frequently touching the same rows) — you'd rather pay a wait than risk
  a silent data-corruption bug.
- Best for scenarios where the locked work is cheap to hold and expensive to redo
  (e.g., you're about to do real work after the read and don't want to discover a
  conflict after doing it, as optimistic locking would).

**Summary**
- `FOR UPDATE` locks rows for writing; other lockers wait.
- `FOR SHARE` locks against writers only; multiple readers can share the lock.
- Locks release automatically at `COMMIT`/`ROLLBACK`.
- Best for high contention or costly-to-redo work; the cost is reduced throughput
  (waiting transactions).

---

## 5.12 — FOR UPDATE SKIP LOCKED / NOWAIT (Job Queue Pattern)

`SKIP LOCKED` and `NOWAIT` change what happens when a row you're trying to lock is
already locked by someone else — instead of the default (wait), you can skip it or
fail immediately. This is the standard pattern for building a job queue where many
workers pull different rows concurrently.

**Key Concepts**
- **`FOR UPDATE SKIP LOCKED`**: if the row a query would lock is already locked by
  another transaction, silently skip it and move to the next matching row, rather than
  waiting.
- **`FOR UPDATE NOWAIT`**: if the row is already locked, immediately raise an error
  instead of waiting — useful when the application would rather fail fast and retry
  later than block.
- **Job-queue idiom**: `LIMIT 1` combined with `SKIP LOCKED` lets each of many workers
  grab a *different* unclaimed row without any of them waiting on each other.

**Worked example — multiple workers, no contention**

```sql
-- Each worker session runs this to claim one row for processing:
BEGIN;
SELECT * FROM account FOR UPDATE SKIP LOCKED LIMIT 1;
-- Worker 1 gets id=1 (Ajay) and locks it
-- Worker 2, running the same query concurrently, skips id=1 (already locked)
-- and gets id=2 (Ravi) instead -- no waiting, no contention
-- ... process the row ...
COMMIT;   -- releases the lock
```

Because each worker's `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` skips rows another
worker already has locked, two workers running the identical query at the same moment
end up claiming *different* rows instead of one blocking behind the other. This is
the difference between a queue that serializes all workers (`FOR UPDATE` alone,
5.11) and one that lets them run in parallel.

**Why it's useful**
- This is exactly how you build a work queue on top of a plain table — background job
  processors, outbox-pattern message dispatch, task schedulers — without needing a
  separate message broker for simple cases.
- `NOWAIT` is useful for interactive operations where blocking indefinitely would be
  worse than failing fast and telling the user to retry.

**Summary**
- `SKIP LOCKED`: skip already-locked rows instead of waiting — enables concurrent
  workers pulling from the same table.
- `NOWAIT`: fail immediately instead of waiting on a lock.
- Both sit on top of `FOR UPDATE`; they change the *waiting* behavior, not the locking
  itself.

---

## 5.13 — Optimistic Locking (Version Column)

**Optimistic locking** assumes conflicts are rare, so it doesn't lock anything up
front. Instead, it detects a conflict at write time: read the row along with a version
number, then write only if the version hasn't changed — if it has, someone else won
the race and you must reload and retry.

**Key Concepts**
- **No locks held between read and write**: the row is freely readable and writable
  by anyone else the whole time — pure concurrency, no waiting.
- **Version column**: `account.version` (from `00-setup.sql`) starts at `0` and is
  incremented on every successful update.
- **Conditional `UPDATE`**: `WHERE id = ? AND version = ?` — if the row's version
  changed since you read it, the `WHERE` clause matches zero rows and the `UPDATE`
  silently updates nothing.
- **Check the affected row count**: the application must check "did this `UPDATE`
  actually change a row?" — zero rows changed means a conflict, and the app should
  reload the current row and retry the whole read-compute-write cycle.

**Worked example**

```sql
-- Step 1: read the row and its version
SELECT balance, version FROM account WHERE id = 1;   -- balance=1000.00, version=0

-- Step 2: (app computes new balance) write only if the version still matches
UPDATE account
SET balance = 1100.00, version = version + 1
WHERE id = 1 AND version = 0;
-- if this affects 1 row -> success, version is now 1
-- if this affects 0 rows -> someone else updated first (version moved past 0) -> reload & retry
```

If another transaction had already updated the row (bumping `version` to `1`) between
this session's `SELECT` and `UPDATE`, the `WHERE id = 1 AND version = 0` clause
matches nothing, so the `UPDATE` reports zero rows affected. The application checks
that count (via the client driver's update-count API, or `GET DIAGNOSTICS` in
PL/pgSQL) and, seeing zero, knows it lost the race — it re-reads the row (getting the
new `version`) and retries the computation instead of blindly overwriting.

**Why it's useful**
- Scales far better than pessimistic locking under low contention, since no
  transaction ever waits on another — most web-application form-edit scenarios ("open
  a record, edit it, save") fit this perfectly.
- This is exactly what JPA/Hibernate's `@Version` annotation implements automatically
  (Spring Boot Phase 4) — the pattern above is that mechanism done by hand in SQL.

**Comparison table — Optimistic vs Pessimistic Locking**

| | Optimistic (version column) | Pessimistic (`FOR UPDATE`) |
|---|---|---|
| Locks held | None | Row locked from read until commit |
| Behavior under conflict | Write fails (0 rows updated); app retries | Second transaction waits |
| Best for | Low contention (web app edits) | High contention or costly-to-redo work |
| Throughput under low contention | High — no waiting | Lower — even uncontended readers pay lock overhead |
| Throughput under high contention | Low — frequent retries, wasted work | Higher — no wasted work, just waiting |
| Application complexity | Must detect 0-row update and retry | Must handle lock wait/timeout and deadlocks |

**Why it's useful** (comparison)
- Choosing between them is a real design decision: optimistic locking wastes CPU on
  retries when contention is high (many failed attempts), while pessimistic locking
  wastes time on waiting when contention is low (locks held for no reason). Match the
  strategy to your workload's actual contention level.

**Summary**
- Optimistic locking = no locks, detect conflict via a version check at write time,
  retry on failure.
- The pattern: `SELECT` row + version, then `UPDATE ... WHERE id = ? AND version = ?`,
  check the affected-row count.
- Best for low-contention workloads; this is JPA's `@Version` done by hand.

---

## 5.14 — Deadlocks

A **deadlock** happens when two (or more) transactions each hold a lock the other
needs, forming a cycle where neither can ever proceed. Postgres detects this
automatically and breaks the cycle by aborting one of the transactions.

**Key Concepts**
- **The cycle**: Transaction A holds a lock on row 1 and waits for row 2; Transaction
  B holds a lock on row 2 and waits for row 1. Neither will ever release what the
  other needs.
- **Detection, not prevention**: Postgres runs a deadlock detector that finds this
  cycle (after a wait, governed by `deadlock_timeout`) and kills one transaction with
  `ERROR: deadlock detected`, letting the other proceed.
- **The victim must retry**: the aborted transaction's application code should catch
  this error and re-run its transaction from the start — same discipline as retrying a
  `SERIALIZABLE` failure (5.8), though a different underlying cause.
- **Prevention**: always acquire locks on rows in a **consistent order** (e.g.,
  ascending `id`) across every transaction that might touch them — if every
  transaction locks row 1 before row 2, a cycle like the one above becomes
  impossible.
- **Keep locked sections short**: the shorter the window between acquiring a lock and
  releasing it (`COMMIT`), the less chance another transaction has to form a cycle
  with it.

**Worked example — how the cycle forms**

```
Session A                                    | Session B
----------------------------------------------+------------------------------------------
BEGIN;                                         | BEGIN;
UPDATE account SET balance = balance - 10      | UPDATE account SET balance = balance - 10
  WHERE id = 1;   -- locks row 1               |   WHERE id = 2;   -- locks row 2
                                               |
UPDATE account SET balance = balance + 10      | UPDATE account SET balance = balance + 10
  WHERE id = 2;   -- WAITS for B's lock on row 2 |   WHERE id = 1;   -- WAITS for A's lock on row 1
                                               |
-- DEADLOCK: A waits on B, B waits on A. Postgres's detector fires:
-- ERROR:  deadlock detected
-- DETAIL: Process 1234 waits for ShareLock on transaction 5678; blocked by process 5678.
--         Process 5678 waits for ShareLock on transaction 1234; blocked by process 1234.
-- Postgres aborts one transaction (say B); A's UPDATE then proceeds and A commits normally.
```

Both sessions update the same two rows but in *opposite order* — A does row 1 then
row 2, B does row 2 then row 1. Each ends up waiting on a lock the other holds, with
no way forward. Postgres's built-in deadlock detector notices the cycle and kills one
transaction so the other can finish. If both sessions had instead updated row 1
*before* row 2 (consistent ordering), B would simply have waited for A to fully
commit and release row 1's lock — no deadlock, just a brief wait.

**Why it's useful**
- Deadlocks are one of the few database errors that are *expected* under high
  concurrency with multi-row transactions — production code touching more than one
  row per transaction should be written to catch and retry `deadlock detected` errors.
- The "consistent lock order" fix is identical to the classic thread-deadlock fix
  (Java Phase 5.2) — same root cause (circular wait), same solution, different layer.

**Summary**
- Deadlock = a cycle of transactions each waiting on a lock the other holds.
- Postgres detects and breaks it automatically by aborting one transaction (which must
  retry).
- Prevent by locking rows in a consistent order across all transactions, and keeping
  transactions short.

---

## 5.15 — Advisory Locks

**Advisory locks** are application-defined locks that aren't tied to any row or table
— Postgres just tracks "is this arbitrary integer key locked or not," and it's up to
your application to agree on what the key means and to actually check the lock before
proceeding.

**Key Concepts**
- **Not tied to data**: unlike `FOR UPDATE`, an advisory lock doesn't protect a
  specific row — it protects whatever logical resource your application decides the
  key represents (e.g., "job #42 is running," "only one instance runs this cron task").
- **`pg_try_advisory_lock(key)`**: attempts to acquire the lock immediately; returns
  `true` if acquired, `false` if already held by someone else — never waits.
- **`pg_advisory_lock(key)`**: the blocking variant — waits until the lock is free.
- **`pg_advisory_unlock(key)`**: releases a lock acquired with either function.
  Session-level advisory locks (the plain functions above) persist until explicitly
  unlocked or the session ends — they are *not* released at `COMMIT` like row locks
  are.

**Worked example**

```sql
SELECT pg_try_advisory_lock(42);   -- true: acquired the lock on key 42
-- ... do the "only one worker" work here ...
SELECT pg_advisory_unlock(42);     -- release it; another session's pg_try_advisory_lock(42) can now succeed
```

If another session calls `pg_try_advisory_lock(42)` while this one still holds it, it
gets back `false` immediately (no waiting) and can decide to skip its work, entirely
outside of any table. This is useful precisely because it needs no table, no row, and
no transaction to coordinate — just an agreed-upon integer key.

**Why it's useful**
- The standard way to implement "only one instance of this scheduled job runs at a
  time" without a separate distributed-lock service — a cron job checks
  `pg_try_advisory_lock` at the start and simply exits if it returns `false`.
- Useful for coordinating access to a *logical* resource that has no natural row to
  lock (e.g., "rebuild the cache" or "run migration step 3").

**Summary**
- Advisory locks are keyed by an arbitrary integer, chosen and interpreted entirely by
  the application.
- `pg_try_advisory_lock` never waits (returns false if unavailable);
  `pg_advisory_lock` blocks until free.
- Session-level advisory locks outlive a single transaction — you must explicitly
  `pg_advisory_unlock` them.

---

## 5.16 — Diagnosing Locks: pg_locks & pg_stat_activity

Postgres exposes what's locked and who's waiting on what through two system views —
essential for diagnosing "why is this query hanging" in production.

**Key Concepts**
- **`pg_locks`**: one row per lock currently held or awaited, including the lock's
  `mode`, the `relation` it's on, and whether it's `granted` (held) or still pending.
- **`pg_stat_activity`**: one row per active backend (connection), showing its current
  `query`, `state`, and — critically — `wait_event_type`/`wait_event` if it's blocked
  waiting on something.

**Worked example**

```sql
-- What's locked on the account table right now, and by whom?
SELECT locktype, relation::regclass, mode, granted, pid
FROM pg_locks WHERE relation = 'account'::regclass;
--  locktype | relation | mode              | granted | pid
-- ----------+----------+-------------------+---------+-------
--  relation | account  | RowExclusiveLock  | t       | 1234
--  tuple    | account  | ExclusiveLock     | t       | 1234    <- row-level lock from FOR UPDATE

-- What is every backend doing, and is any of them blocked?
SELECT pid, state, wait_event_type, wait_event, left(query, 60) AS query
FROM pg_stat_activity WHERE datname = current_database();
--  pid  | state  | wait_event_type | wait_event | query
-- ------+--------+------------------+------------+-------------------------------------------
--  1234 | active |                  |            | UPDATE account SET balance = ...
--  5678 | active | Lock             | tuple      | UPDATE account SET balance = ...  <- blocked
```

In this output, `pid 5678` is `active` but its `wait_event_type` is `Lock` — it's not
actually executing, it's waiting to acquire a tuple lock that `pid 1234` currently
holds. Joining `pg_locks` on `pid` shows exactly which lock and which row is the
bottleneck, which is the first step in diagnosing a "queries are hanging" incident.

**Why it's useful**
- This is the real, production diagnostic path for "why is everything slow" — find
  the blocked backends in `pg_stat_activity`, then find what they're waiting on in
  `pg_locks`, then find (and possibly terminate) the blocking session.
- Understanding these views turns "the database is stuck" from a mystery into a
  two-query investigation.

**Summary**
- `pg_locks` shows every lock, held or awaited, and its mode.
- `pg_stat_activity` shows every connection's current query and whether it's blocked
  (`wait_event_type = 'Lock'`).
- Together they're how you diagnose lock contention and blocking chains in a live
  database.

---

## 5.17 — MVCC Fundamentals (xmin, xmax, Row Versions)

**MVCC (Multi-Version Concurrency Control)** is the mechanism that lets Postgres
achieve high concurrency without read locks: **readers never block writers, and
writers never block readers.** Instead of locking rows for reads, Postgres keeps
*multiple physical versions* of each row and shows each transaction only the version
that's valid for its own snapshot.

**Key Concepts**
- **`xmin`**: a hidden system column on every row holding the ID of the transaction
  that *created* this row version.
- **`xmax`**: a hidden system column holding the ID of the transaction that *deleted
  or superseded* this row version (`0` if the row is still the current, live version).
- **An `UPDATE` never overwrites in place**: it (1) marks the old row version expired
  by setting its `xmax`, and (2) inserts a brand-new row version with a fresh `xmin`.
  The table physically accumulates versions, not in-place mutations.
- **Visibility rule**: a transaction sees exactly the row version whose `xmin` is
  committed and visible to its own snapshot, and whose `xmax` is *not* visible to it
  (i.e., not yet deleted from its point of view) — this is how each transaction gets a
  consistent point-in-time view without ever taking a read lock.

**Worked example — watching a new row version get created**

```sql
SELECT id, owner, balance, xmin, xmax FROM account ORDER BY id;
--  id | owner | balance | xmin | xmax
-- ----+-------+---------+------+------
--   1 | Ajay  | 1000.00 |  731 |    0

SELECT owner, xmin FROM account WHERE owner = 'Ajay';   -- xmin = 731 (note it)
UPDATE account SET balance = balance + 1 WHERE owner = 'Ajay';
SELECT owner, xmin FROM account WHERE owner = 'Ajay';   -- xmin = 732 -- a DIFFERENT value!
```

The `xmin` changes after the `UPDATE` because Postgres didn't modify the existing row
in place — it created an entirely new row version (owned by the new transaction, ID
`732`) and marked the old version (`xmin=731`) as expired by setting its `xmax`. Any
transaction whose snapshot still considers `731` the latest visible version continues
to see the *old* balance — this is exactly how a `REPEATABLE READ` transaction can
keep reading `1000.00` even while another transaction commits an update (5.7).

**Why it's useful**
- Explains, mechanically, why "readers don't block writers" in Postgres: a `SELECT`
  never has to wait for an `UPDATE` to finish, because it can just read the older
  still-visible row version while the new one is being written.
- Explains why isolation levels in Postgres are "free" in terms of read blocking — the
  isolation ladder (5.5–5.9) is really just a policy for *which* row versions a
  transaction's snapshot considers visible.

**Summary**
- MVCC keeps multiple row versions instead of locking rows for reads.
- `xmin`/`xmax` mark which transaction created/expired each version.
- `UPDATE` = insert a new version + expire the old one, never an in-place overwrite.
- This single mechanism is *why* readers and writers don't block each other in
  Postgres.

---

## 5.18 — MVCC and Isolation Levels

The isolation levels from 5.5–5.9 aren't a separate mechanism — they're a policy for
**when a transaction's MVCC snapshot is taken**, layered directly on top of the
`xmin`/`xmax` machinery from 5.17.

**Key Concepts**
- **`READ COMMITTED`**: takes a **new snapshot before every statement**. Each
  statement independently sees "whatever is committed right now," which is exactly
  why two `SELECT`s in the same transaction can see different values (5.6).
- **`REPEATABLE READ`**: takes **one snapshot at the transaction's first statement**
  and reuses it for every subsequent statement, which is exactly why all reads in
  that transaction stay consistent with each other (5.7).
- **`SERIALIZABLE`**: uses the same one-snapshot-per-transaction mechanism as
  `REPEATABLE READ`, plus an additional layer (SSI) that tracks read/write
  dependencies across transactions to catch anomalies snapshotting alone can't (5.8).
- **No read locks needed at any level**: because visibility is determined by
  comparing a row version's `xmin`/`xmax` against the transaction's snapshot, not by
  acquiring a lock, `SELECT` statements never block on concurrent writers regardless
  of isolation level.

**Worked example — the same UPDATE seen differently depending on isolation level**

```sql
-- Row starts with xmin=731, balance=1000.00

-- Transaction X (REPEATABLE READ) takes its snapshot here, sees xmin=731 as current.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT balance FROM account WHERE id = 1;   -- 1000.00 (sees version xmin=731)

-- Meanwhile, another transaction commits an UPDATE:
--   UPDATE account SET balance = 1200 WHERE id = 1;
--   -> old version's xmax set to the new txn id; new version inserted with new xmin

SELECT balance FROM account WHERE id = 1;   -- STILL 1000.00
-- X's snapshot still considers xmin=731 the visible version for it,
-- even though a newer version (xmin=<new txn>) now exists and is
-- what a fresh READ COMMITTED statement would see.
COMMIT;
```

Physically, both the old and new row versions coexist in the table at the same time
after the concurrent `UPDATE` commits. Which one a given `SELECT` sees is purely a
function of that transaction's snapshot rules: a `READ COMMITTED` statement running
at that moment would see the new version (`1200.00`), while transaction X's
`REPEATABLE READ` snapshot — fixed at its first statement — keeps resolving to the
older version (`1000.00`) for its entire duration.

**Why it's useful**
- Ties the abstract "isolation level" concept to something concrete and inspectable
  (`xmin`/`xmax`) — a useful mental model for reasoning about exactly what any given
  `SELECT` will see in a concurrent system.
- Explains why raising isolation level in Postgres costs *snapshot/version-tracking
  overhead and serialization-failure risk*, not *read locking overhead* — there never
  were read locks to begin with.

**Summary**
- Isolation level = policy for when a snapshot is taken, not a separate locking
  mechanism.
- `READ COMMITTED`: new snapshot per statement. `REPEATABLE READ`/`SERIALIZABLE`: one
  snapshot per transaction.
- Visibility is decided by comparing row versions' `xmin`/`xmax` to the snapshot — no
  read locks involved at any level.

---

## 5.19 — Dead Tuples, Bloat, VACUUM, and Transaction ID Wraparound

MVCC's lock-free concurrency isn't free: every `UPDATE`/`DELETE` leaves the *old* row
version behind as a **dead tuple**, since nothing physically removes it at the moment
it's superseded. These dead tuples accumulate as **bloat** until `VACUUM` reclaims
them — and vacuuming also protects against a much more serious failure mode,
transaction ID wraparound.

**Key Concepts**
- **Dead tuple**: an old row version whose `xmax` is set (superseded) and that no
  currently-running transaction's snapshot can possibly still need — it's pure waste
  taking up space.
- **Bloat**: the accumulation of dead tuples in a table (and its indexes), which wastes
  disk space and can slow down sequential scans and index lookups that have to skip
  over them.
- **`VACUUM`**: reclaims the space used by dead tuples so it can be reused by future
  inserts/updates. `autovacuum` normally does this automatically in the background;
  neglecting it (or holding a long-running transaction that prevents old versions from
  being considered dead) lets bloat grow unbounded.
- **Transaction ID wraparound**: Postgres transaction IDs (`xid`s) are 32-bit and
  eventually wrap around. `VACUUM` also "freezes" old row versions (marking them as
  permanently visible, independent of `xid` comparison) so that wraparound doesn't
  make old committed data look like it's from the future and become invisible —
  neglected vacuuming risks this, which is a correctness failure, not just a
  performance one.

**Worked example — observing bloat directly**

```sql
SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'account';
--  n_live_tup | n_dead_tup
-- ------------+------------
--           3 |          5    <- 5 old versions from prior UPDATEs, not yet reclaimed

VACUUM account;   -- reclaims the 5 dead tuples' space for reuse

SELECT n_live_tup, n_dead_tup FROM pg_stat_user_tables WHERE relname = 'account';
--  n_live_tup | n_dead_tup
-- ------------+------------
--           3 |          0
```

Every prior `UPDATE` in this file's examples left a dead tuple behind — the table
still physically contains `1000.00`, `1050.00`, `1100.00`, etc. as separate row
versions until `VACUUM` runs. Running `VACUUM account` explicitly reclaims that space
immediately; in production, `autovacuum` does this on a schedule/threshold basis so
you rarely run it by hand.

**Why it's useful**
- Explains a real production failure mode: a long-running transaction (even an idle
  one left open by a buggy connection pool) holds its snapshot open indefinitely,
  which prevents `VACUUM` from reclaiming *anything* newer than that snapshot,
  causing unbounded bloat until the connection is closed or killed.
- Understanding that MVCC's concurrency benefit has this specific, quantifiable cost
  (dead tuples) is what makes "why is this table 10x bigger than its actual data"
  and "why did autovacuum just lock this huge table" answerable questions instead of
  mysteries.

**Summary**
- Every `UPDATE`/`DELETE` leaves a dead tuple; that's the price of MVCC's lock-free
  reads.
- `VACUUM` (usually via `autovacuum`) reclaims dead tuples and also *freezes* old
  versions to prevent transaction ID wraparound — it protects correctness, not just
  disk space.
- Long-running/idle transactions block vacuum progress by keeping an old snapshot
  alive — this is why "keep transactions short" (5.4) is a hard production rule, not
  just a style preference.
- One mechanism — MVCC — explains Postgres's concurrency model, its isolation levels,
  and its vacuum/bloat story all at once.
