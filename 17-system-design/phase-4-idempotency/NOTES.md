<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · databases scale](../phase-3-databases-scale/NOTES.md) | [Phase 5 · web tier ➡](../phase-5-web-tier/NOTES.md)
<!-- /nav -->

# Phase 4 — Concurrency, Consistency & Idempotency: Notes

The core problem this phase covers: **concurrent requests** (double-clicks, a client
retrying after a timeout, a load balancer replaying a request, at-least-once message
delivery) turn naive "check-then-act" and "read-modify-write" code into **duplicate
creates** and **lost updates**. `IdempotencyDemo.java` deliberately reproduces both bugs
with real threads racing on shared state (100 duplicate rows for one email; a balance of
1 instead of 100 after 100 concurrent increments), then fixes each — and every in-memory
fix in the demo maps directly onto a real database mechanism you'd use in production.

## 4.1 — Preventing duplicate CREATEs

### Key Concepts

- **The race** — `if not exists → insert` looks atomic when you read it, but it's two
  separate steps executed by the CPU/database. Between the check and the insert, another
  concurrent request can pass the same check (because the first request hasn't inserted
  yet) — both proceed to insert, and you get two rows for what should be one logical
  entity.
- **UNIQUE constraint (strongest fix, always have it)** — the database itself enforces
  uniqueness atomically: no matter how many requests race, only the first insert
  succeeds; every other concurrent insert for the same key fails immediately with a
  duplicate-key error, which the application catches and treats as "already exists,
  return the existing row." This is the *last line of defense* you should have on every
  table with a natural uniqueness requirement, even if you also have an idempotency key.
- **Idempotency key** — the client generates a unique token per *logical operation*
  (commonly sent as an `Idempotency-Key: <uuid>` HTTP header) before making the request.
  The server records that key alongside the operation's result; if the exact same key
  arrives again (a retry), the server returns the *original* stored result instead of
  re-executing the side effect. This is how Stripe, PayPal, and most payment APIs make
  "create a charge" — an inherently unsafe-to-retry operation — safe to retry.
- **Natural/derived unique key** — dedupe on a real business identity (an order number,
  `user_id + calendar_day` for a "one free trial per day" rule) rather than inventing a
  surrogate id, when the business itself already defines what "the same thing" means.
- **Client-side "disable the button after click" is UX, not a guarantee.** It reduces
  *accidental* duplicates from a fast double-click, but does nothing against a network
  retry, a proxy replay, or a malicious client — the server must enforce uniqueness
  regardless of what the client does.

### Worked example — `IdempotencyDemo.duplicateCreateRace()` and its fix

```java
static void duplicateCreateRace() throws InterruptedException {
    Map<String, String> users = new ConcurrentHashMap<>();
    AtomicInteger createsExecuted = new AtomicInteger();
    runConcurrently(() -> {
        if (!users.containsKey("ajay@dev.io")) {        // step 1: check  <-- race window
            sleepABit();                                // work that widens the window
            users.put("ajay@dev.io", "row");            // step 2: act
            createsExecuted.incrementAndGet();
        }
    });
    // naive check-then-insert -> created N rows for ONE email  (should be 1!)  <-- DUPLICATES
}

static void duplicateCreateFixed() throws InterruptedException {
    Map<String, String> users = new ConcurrentHashMap<>();
    AtomicInteger createsExecuted = new AtomicInteger();
    runConcurrently(() -> {
        // putIfAbsent is ATOMIC: check-and-insert in one indivisible step.
        if (users.putIfAbsent("ajay@dev.io", "row") == null) {
            createsExecuted.incrementAndGet();          // only the winner runs this
        }
    });
    // atomic putIfAbsent -> created 1 row  (UNIQUE constraint / idempotency key does this in the DB)
    assert createsExecuted.get() == 1;
}
```

With 100 threads all racing to register `"ajay@dev.io"`, the naive version's `sleepABit()`
call deliberately widens the window between the check and the write, so essentially every
one of the 100 threads passes the `containsKey` check before any of them has called
`put` — the demo's real output is close to 100 "duplicate" creates for one email. The
fixed version replaces the two-step check-then-act with `putIfAbsent`, which the JVM
guarantees is a single atomic operation on `ConcurrentHashMap` — exactly one thread's
call can be the one that transitions the map from "absent" to "present" for that key, and
`createsExecuted` ends at exactly 1. The real-world equivalent of `putIfAbsent` is a
`UNIQUE` constraint: `ALTER TABLE users ADD CONSTRAINT uq_email UNIQUE (email);` makes
the second concurrent `INSERT` fail with a `SQLIntegrityConstraintViolation` — the
database, not your application code, is what actually enforces the atomicity, because
only the database can see and serialize concurrent writes from every application server
at once.

### Why it's useful

Every "create" endpoint in a real API — sign up a user, place an order, create a
payment — is exposed to this exact race whenever a client can retry (which, per Phase 6,
should be assumed for every network call). A UNIQUE constraint costs nothing and catches
the bug even if you forget an idempotency key somewhere; an idempotency key additionally
lets you return the *correct, original* response on a retry (not just reject the
duplicate), which matters a great deal to a client that needs to know "did my payment
actually go through?"

## 4.2 — Preventing lost / duplicate UPDATEs

### Key Concepts

- **The race** — two (or more) requests each perform read-modify-write on the same
  value: read the current balance, compute `current + 1`, write it back. If two requests
  interleave — both read `0`, both compute `1`, both write `1` — the second write
  silently overwrites the first's effect, and one of the two increments is **lost**.
  This is a different bug from 4.1: nothing fails or errors, the operation just silently
  doesn't count.
- **Optimistic locking** — assume conflicts are rare; don't hold any lock while reading
  and computing, but detect a conflict *at write time* by verifying nothing has changed
  since you read. Implemented with a version column: `UPDATE account SET balance = ?,
  version = version + 1 WHERE id = ? AND version = ?` — if another writer already bumped
  the version, this statement affects 0 rows, telling you to reload and retry. In JPA/
  Hibernate, annotating a field `@Version` does this automatically and throws
  `OptimisticLockException` on conflict for you to catch and retry. This directly
  mirrors `compareAndSet` (Java Phase 5.2) — the demo's `AtomicReference.compareAndSet`
  is the exact in-memory analog of the versioned `UPDATE ... WHERE version = ?`. Best
  fit: **low contention**, web-scale traffic where most requests don't actually collide,
  because it holds no locks and scales well — the cost is paying for retries under
  contention.
- **Pessimistic locking** — assume conflicts are likely; take an exclusive lock on the
  row *before* reading, so every other writer blocks until you commit. Implemented with
  `SELECT * FROM account WHERE id = ? FOR UPDATE`; in JPA, `@Lock(LockModeType.
  PESSIMISTIC_WRITE)`. This mirrors `synchronized` (Java Phase 5.2) — the demo's
  `synchronized (rowLock)` block is the in-memory analog of the row lock. Best fit:
  **high contention**, or when redoing the work on a retry would itself be expensive —
  the cost is reduced throughput (writers now serialize instead of running concurrently)
  and the risk of deadlocks/lock-wait timeouts if multiple rows are locked in
  inconsistent orders across transactions.

### Worked example — `IdempotencyDemo`'s three balance variants

```java
// RACE (no protection): 100 threads each do balance[0] = balance[0] + 1
static void lostUpdateRace() throws InterruptedException {
    int[] balance = {0};
    runConcurrently(() -> {
        int read = balance[0];      // read
        sleepABit();                // widen the window
        balance[0] = read + 1;      // modify + write (stale read!)
    });
    // no locking -> balance = 1 (expected 100)  <-- LOST UPDATES
}

// OPTIMISTIC: retry on a failed compareAndSet
static void lostUpdateOptimistic() throws InterruptedException {
    AtomicReference<Integer> balance = new AtomicReference<>(0);
    AtomicInteger retries = new AtomicInteger();
    runConcurrently(() -> {
        int read;
        do {
            read = balance.get();
            if (!balance.compareAndSet(read, read + 1)) retries.incrementAndGet();
            else break;
        } while (true);
    });
    // optimistic (CAS) -> balance = 100 with 51 retries  (@Version + retry)
    assert balance.get() == THREADS;
}

// PESSIMISTIC: exclusive lock around the whole read-modify-write
static void lostUpdatePessimistic() throws InterruptedException {
    int[] balance = {0};
    Object rowLock = new Object();
    runConcurrently(() -> {
        synchronized (rowLock) {    // == the row lock
            int read = balance[0];
            sleepABit();
            balance[0] = read + 1;
        }
    });
    // pessimistic lock -> balance = 100  (SELECT ... FOR UPDATE serializes the writers)
    assert balance[0] == THREADS;
}
```

`lostUpdateRace` reads and writes with no coordination at all, so with 100 threads racing
and an artificial delay widening the window, most of the 100 increments are lost —
the balance ends far below 100 (the demo comment notes a result of 1). `lostUpdateOptimistic`
loops on `compareAndSet`: if another thread updated `balance` between this thread's read
and its attempted write, the CAS fails, the thread re-reads the now-current value and
tries again — the demo reports roughly 51 retries were needed across 100 threads to land
exactly 100 successful increments; every increment is eventually applied, just possibly
after a retry. `lostUpdatePessimistic` instead serializes all 100 threads through a
single lock around the entire read-modify-write, guaranteeing correctness with zero
retries, at the cost of the 100 increments happening one at a time instead of with any
parallelism.

### Comparison table — optimistic vs. pessimistic locking

| | Optimistic locking | Pessimistic locking |
|---|---|---|
| Assumption | Conflicts are rare | Conflicts are common |
| Mechanism | Version check on write; retry on conflict | Exclusive lock held for the critical section |
| Java analog | `compareAndSet` | `synchronized` |
| DB mechanism | `@Version` column / `WHERE version = ?` | `SELECT ... FOR UPDATE` |
| Concurrency | High — no blocking | Lower — writers serialize |
| Failure mode | Wasted retry work under high contention | Reduced throughput; deadlock risk |
| Best for | Low-contention, web-scale (the default) | High contention, or expensive-to-redo work |

### Why it's useful

Lost updates are one of the most common real-world data-integrity bugs precisely because
the code *looks* correct — `balance += amount` reads fine — and the bug only appears
under real concurrent load, which is exactly what local testing rarely exercises.
Choosing optimistic by default (it's cheap, scales, and most real workloads have low
per-row contention) and escalating to pessimistic only where contention is measurably
high (a single hot inventory row during a flash sale) is the standard, defensible
default. Both build on the database's transaction isolation level underneath — the
`@Version` check and `FOR UPDATE` are tools layered *on top of* whatever isolation level
you're running at, not substitutes for it (Databases Phase 5).

## 4.3 — Idempotent operations and delivery guarantees

### Key Concepts

- **Idempotent operation** — applying it once or N times produces the *same end state*.
  `SET balance = 100` is idempotent (applying it twice leaves the balance at 100);
  `balance += 100` is not (applying it twice leaves the balance at 200). This distinction
  is the crux of the whole phase: retries are unavoidable in distributed systems, so
  every operation that might be retried needs to either be naturally idempotent, or made
  idempotent via an idempotency key.
- **Idempotent by HTTP method** — GET, PUT, and DELETE are idempotent *by definition* in
  the HTTP spec (repeating them has the same effect as doing it once — `PUT /user/5
  {name: "Ajay"}` twice still leaves the name as "Ajay"; `DELETE /order/9` twice still
  leaves order 9 deleted). **POST is explicitly not idempotent** — two identical POSTs
  are defined to create two resources — which is exactly why "create" endpoints (which
  are almost always POST) are the ones that need an idempotency key or unique constraint;
  the HTTP spec itself gives you no safety net there.
- **Delivery guarantees** — a distributed call (an HTTP request, a message delivery) can
  practically offer **at-most-once** (send once, don't retry — may silently drop on
  failure) or **at-least-once** (retry until acknowledged — may duplicate). True
  **exactly-once delivery** is not achievable in general in an asynchronous distributed
  system (a classic result related to the Two Generals' Problem); what's achievable, and
  what production systems actually mean by "exactly-once," is **at-least-once delivery
  plus idempotent consumers** — duplicates *do* arrive, but the consumer recognizes and
  no-ops them, so the *effect* is exactly-once even though the *delivery* wasn't.

### Worked example — `IdempotencyDemo.idempotentRetries()`

```java
static void idempotentRetries() throws InterruptedException {
    Set<String> processed = ConcurrentHashMap.newKeySet();   // remembers handled request ids
    AtomicInteger balance = new AtomicInteger(0);

    // Simulate the SAME payment (idempotency key "pay-42") delivered 100x
    runConcurrently(() -> {
        String idempotencyKey = "pay-42";
        if (processed.add(idempotencyKey)) {             // add() returns false if already present
            balance.addAndGet(100);                      // the real side effect, guarded
        }
        // else: a duplicate/retry -> no-op, return the original result.
    });
    // 100 deliveries of payment 'pay-42' -> balance = 100  (applied exactly once via the idempotency key)
    assert balance.get() == 100;
}
```

`Set.add()` on a `ConcurrentHashMap`-backed set returns `true` only for the *first*
caller to add a given key and `false` for every subsequent caller with the same key —
exactly the semantics needed here. Even though this simulates 100 independent deliveries
of the *same logical payment* (as would happen with a flaky network causing repeated
client retries, or an at-least-once message queue redelivering the same event), the
balance only ever increases by 100 once — 99 of the 100 deliveries are recognized as
duplicates and safely no-op. This is the idempotency-key pattern in miniature: `processed`
is what a real system would persist as a dedupe table keyed by `idempotency_key` (or
`message_id`/`event_id`), checked in the same transaction as the side effect.

### Why it's useful

This reframes "design a reliable payment/notification/order system" from "how do I
guarantee exactly-once delivery" (which isn't possible) to "how do I make my consumer
tolerate at-least-once delivery" (which is entirely achievable, and what every real
payments API — Stripe's idempotency keys, Kafka consumer offset + dedupe tables — actually
does). Design writes so a **safe retry converges to the same state** rather than
compounding — that single principle underlies almost everything else in this phase.

## 4.4 — Upsert

### Key Concepts

- **Upsert** — insert-or-update as a single atomic database statement, eliminating the
  check-then-insert-or-update race entirely (the same class of race as 4.1, but for
  "create if missing, otherwise update" instead of "create if missing, otherwise
  reject").
  ```sql
  -- Postgres
  INSERT INTO inventory (sku, qty) VALUES (?, ?)
  ON CONFLICT (sku) DO UPDATE SET qty = EXCLUDED.qty;

  -- MySQL
  INSERT INTO inventory (sku, qty) VALUES (?, ?)
  ON DUPLICATE KEY UPDATE qty = VALUES(qty);
  ```
- The database evaluates the conflict check and the resulting insert-or-update as one
  atomic operation under the hood — no window exists for a concurrent request to
  interleave, unlike a hand-written `SELECT` then `INSERT`-or-`UPDATE` in application
  code.

### Why it's useful

Upsert is the right tool whenever "the logical operation" is naturally "set this to X,
creating the row if it doesn't exist yet" — inventory counts, user preference settings,
idempotent event-sourced projections — and it removes an entire category of race
condition for the cost of a slightly different SQL statement, with no application-level
locking or retry logic needed at all.

## 4.5 — Distributed transactions

A single ACID transaction only works within one database. Once a logical operation spans
multiple services (each with its own database, in a microservices architecture), you
lose that guarantee — and the whole phase's idempotency toolkit is what you build on to
compensate.

### Key Concepts

- **The problem** — "reserve inventory, charge the payment, create the order" might
  touch three different services' databases. There's no single transaction boundary that
  can atomically commit or roll back all three.
- **Saga pattern** — model the multi-step operation as a sequence of local transactions,
  each service committing its own step independently, with an explicit **compensating
  action** defined for each step to undo it if a later step in the sequence fails (e.g.,
  if charging payment fails after inventory was reserved, run "release-inventory" as the
  compensation). Sagas can be **choreographed** (each service listens for the previous
  step's event and reacts — no central coordinator, but the overall flow is implicit and
  harder to trace) or **orchestrated** (a central coordinator service explicitly calls
  each step in order — clearer and easier to change, but a new central dependency).
- **Outbox pattern** — solves the specific "dual-write" problem: updating your own
  database *and* publishing an event about that update aren't atomic by default (the DB
  write can succeed while the message publish fails, or vice versa). The fix: write the
  event to an `outbox` table in the **same local transaction** as the state change, then
  have a separate relay process (polling the table, or reading its write-ahead log via
  CDC) publish the event afterward. This guarantees the event is published *if and only
  if* the state change actually committed.
- **Two-phase commit (2PC)** — a coordinator-driven protocol for atomically committing
  across multiple resources: every participant votes "ready to commit," and only if
  *all* vote yes does the coordinator tell everyone to actually commit. Strongly
  consistent, but slow (extra round trips) and fragile — if the coordinator crashes
  mid-protocol, participants can be left blocked holding locks indefinitely. Rarely used
  at real scale in favor of sagas plus idempotency.

### Why it's useful

The unifying idea across this entire section: at real distributed-system scale, you
trade "one big ACID transaction across everything" for **idempotency + eventual
consistency + explicit compensations**. You make every individual write safe to retry
(4.3), dedupe on identity (4.1), and reconcile multi-step failures asynchronously with
compensating actions (saga) rather than chasing an atomic distributed commit that's slow
and fragile in practice (2PC). This is the direct bridge into Phase 9's messaging
patterns and the outbox pattern's role there.

## Summary / Key Takeaways

- **Duplicate creates** come from a "check, then act" race — fix with a **UNIQUE
  constraint** (always have it) and/or an **idempotency key** so a retry returns the
  original result instead of duplicating the side effect (demo: `putIfAbsent` → 1 row
  instead of ~100).
- **Lost updates** come from a "read, modify, write" race — fix with **optimistic
  locking** (`@Version`, `compareAndSet` — default for low-contention, web-scale
  workloads; costs retries under contention) or **pessimistic locking** (`FOR UPDATE`,
  `synchronized` — better for high contention; costs reduced throughput).
- **POST is not idempotent by the HTTP spec; GET/PUT/DELETE are** — which is exactly why
  create endpoints need explicit idempotency handling that the protocol doesn't give you
  for free.
- **True exactly-once delivery is not achievable** in an async distributed system —
  what's achievable, and what "exactly-once" means in practice, is **at-least-once
  delivery + idempotent consumers** that dedupe on a message/idempotency key (demo: 100
  deliveries of one payment → applied exactly once).
- **Upsert** eliminates the insert-or-update race atomically at the database level; for
  cross-service operations that can't share a transaction, use the **saga pattern**
  (local transactions + compensating actions) and the **outbox pattern** (write the event
  in the same transaction as the state change) instead of a fragile, slow **2PC**.
