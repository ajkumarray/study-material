<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · databases scale](../phase-3-databases-scale/NOTES.md) | [Phase 5 · web tier ➡](../phase-5-web-tier/NOTES.md)
<!-- /nav -->

# Phase 4 — Concurrency, Consistency & Idempotency: Notes

The core problem: **concurrent requests** (double-clicks, client retries on timeout, load-balancer replays, at-least-once message delivery) turn naive "check-then-act" and "read-modify-write" code into **duplicate creates** and **lost updates**. The demo reproduces both (100 duplicate rows; a balance of 1 instead of 100) and fixes each. Every in-memory fix maps to a database mechanism.

## 4.1 — Preventing duplicate CREATES

**The race:** `if not exists → insert` is two steps; between them, other requests pass the same check and all insert. (Demo: 100 threads, 100 rows for one email.)

**Fixes (strongest first):**
1. **UNIQUE constraint** — the database enforces it: the second insert fails with a duplicate-key error, atomically, no matter how many threads race. This is the *last line of defense you should always have*.
   ```sql
   ALTER TABLE users ADD CONSTRAINT uq_email UNIQUE (email);
   -- concurrent 2nd insert -> SQLIntegrityConstraintViolation; catch -> return the existing row
   ```
2. **Idempotency key** — the client generates a unique key per logical operation (`Idempotency-Key: <uuid>` header) and you store it; a retry with the same key returns the *original* result instead of acting again. How Stripe/PayPal make "create charge" safe to retry. (Demo: `putIfAbsent` on the key.)
3. **Natural/derived unique key** — dedupe on business identity (order number, `user_id + day`) rather than a surrogate id.

Client-side "disable the button" is UX, not a guarantee — the server must enforce it.

## 4.2 — Preventing lost / duplicate UPDATES

**The race:** two requests read the same value, each computes a new one, each writes — the second overwrites the first. (Demo: balance 1 vs 100.)

**Optimistic locking** — assume conflicts are rare; detect them at write time.
```sql
-- a version column; the UPDATE only succeeds if nobody changed the row since you read it
UPDATE account SET balance = ?, version = version + 1 WHERE id = ? AND version = ?;
-- 0 rows updated => someone else won => reload and retry
```
JPA/Spring: add `@Version` and Hibernate does this automatically, throwing `OptimisticLockException` on conflict; you catch and retry. Maps to `compareAndSet` (Java Phase 5.2). Best for **low contention** and web apps (no held locks, scales well). Cost: retries under contention (demo: 51 retries for 100 writes).

**Pessimistic locking** — assume conflicts; lock the row up front so writers serialize.
```sql
SELECT * FROM account WHERE id = ? FOR UPDATE;   -- others block until you commit
```
JPA: `@Lock(LockModeType.PESSIMISTIC_WRITE)`. Maps to `synchronized` (Java Phase 5.2). Best for **high contention** or when a retry is expensive. Cost: reduced throughput (serialized), and risk of deadlocks/lock-wait timeouts.

**Choosing:** optimistic by default (web scale, rare conflicts); pessimistic when conflicts are frequent or the work between read and write is costly to redo. (Both build on transaction isolation levels — Databases Phase 5.)

## 4.3 — Upsert, delivery guarantees, idempotent APIs

**Upsert** = insert-or-update atomically (Databases Phase 1):
```sql
INSERT INTO inventory (sku, qty) VALUES (?, ?)
ON CONFLICT (sku) DO UPDATE SET qty = EXCLUDED.qty;
```
Removes the check-then-insert-or-update race entirely.

**Delivery guarantees:** distributed systems generally offer **at-least-once** (may duplicate) or **at-most-once** (may drop); true **exactly-once** processing is achieved by **at-least-once delivery + idempotent consumers** (dedupe on a message/event id). So idempotency isn't optional — it's how you survive the retries reality forces on you.

**Idempotent by HTTP method:** GET/PUT/DELETE are idempotent by definition; **POST is not** (two POSTs create two resources) — which is exactly why "create" endpoints need idempotency keys or a unique constraint. Design writes so a **safe retry** yields the same state (demo: 100 deliveries of `pay-42` → applied once).

## 4.4 — Distributed transactions (when one DB isn't enough)

Across services/databases you can't use a single ACID transaction. Options:
- **Saga** — a sequence of local transactions, each with a **compensating action** to undo prior steps if a later one fails (e.g., cancel-payment if reserve-inventory fails). Choreographed (events) or orchestrated (a coordinator).
- **Outbox pattern** — to update the DB *and* publish an event atomically, write the event to an `outbox` table in the *same* transaction, then a relay publishes it. Avoids the "DB committed but the message was lost" (or vice-versa) dual-write problem.
- **Two-phase commit (2PC)** — a coordinator-driven atomic commit across resources; strong but slow and fragile (blocks on coordinator failure) — usually avoided at scale in favor of sagas + idempotency.

**The unifying idea:** at scale you trade distributed ACID for **idempotency + eventual consistency + compensations**. Make every write safe to retry, dedupe on identity, and reconcile asynchronously.
