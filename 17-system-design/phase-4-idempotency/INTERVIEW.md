<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · databases scale](../phase-3-databases-scale/NOTES.md) | [Phase 5 · web tier ➡](../phase-5-web-tier/NOTES.md)
<!-- /nav -->

# Phase 4 — Concurrency & Idempotency: Interview Q&A

⭐ = asked constantly (system-design and senior-backend interviews).

**Q: A user double-clicks "Submit" and two identical orders are created. How do you prevent it?** ⭐⭐
Server-side enforcement (client button-disabling is not a guarantee): a **UNIQUE constraint** on the natural key (so the DB rejects the duplicate atomically), and/or an **idempotency key** — the client sends a unique key per operation; the server records it and returns the original result on any retry. GET/PUT/DELETE are idempotent; POST isn't, which is why creates need this.

**Q: What is an idempotency key and how does it work?** ⭐⭐
A client-generated unique token (e.g. `Idempotency-Key` header) identifying one logical operation. The server stores it with the operation's result; a repeated request with the same key short-circuits to the stored result instead of re-executing the side effect. Makes unsafe operations (charge a card, create an order) safe to retry. Standard in payment APIs.

**Q: Two requests read an account balance and both write; one update is lost. Fixes?** ⭐⭐
Optimistic locking (a version column: `UPDATE ... WHERE id=? AND version=?`; 0 rows → conflict → reload & retry; JPA `@Version`) or pessimistic locking (`SELECT ... FOR UPDATE` locks the row so writers serialize). Both rely on transactions.

**Q: Optimistic vs pessimistic locking — when each?** ⭐⭐
Optimistic: no locks held; detect conflict at commit and retry. Best for low contention / read-heavy web apps; scales well; cost is retries. Pessimistic: lock the row up front; others wait. Best for high contention or when redoing work is expensive; cost is reduced throughput and deadlock risk. Default optimistic, escalate to pessimistic when conflicts are common.

**Q: What does "exactly-once" really mean in distributed systems?** ⭐
True exactly-once *delivery* is generally impossible; systems provide at-least-once (may duplicate) or at-most-once (may drop). Exactly-once *processing* is achieved by at-least-once delivery **plus idempotent consumers** that dedupe on a message/event id. So you design for duplicates, not against them.

**Q: What is an upsert?**
Insert-or-update in one atomic statement — `INSERT ... ON CONFLICT (key) DO UPDATE` (Postgres) / `INSERT ... ON DUPLICATE KEY UPDATE` (MySQL) / `MERGE`. Eliminates the check-then-insert-or-update race.

**Q: Why can't you just use a database transaction across microservices?**
Each service owns its own database; there's no shared transaction boundary, and 2PC across them is slow and blocks on coordinator failure. Instead use the **saga pattern** (local transactions + compensating actions) with idempotency, accepting eventual consistency.

**Q: What is the outbox pattern and what problem does it solve?** ⭐
The dual-write problem: updating the DB and publishing an event aren't atomic, so one can succeed while the other fails. The outbox writes the event to an `outbox` table in the *same* DB transaction as the state change; a separate relay reads and publishes it. Guarantees the event is sent iff the state changed.

**Q: How do transaction isolation levels relate to these races?**
Isolation level determines which anomalies (dirty/non-repeatable/phantom reads) are possible (Databases Phase 5). Higher isolation (e.g., SERIALIZABLE) prevents more races but reduces concurrency; READ COMMITTED (the common default) still allows lost updates, which is why you add optimistic/pessimistic locking on top.

**Q: A message queue may deliver a payment event twice. How do you avoid double-charging?**
Make the consumer idempotent: record processed event ids (a dedupe table / `processed` set) and skip already-seen ones, or make the effect naturally idempotent (set state to a value rather than increment). Combine with a unique constraint on the transaction id as a backstop.

**Q: Design a "create order" endpoint that's safe under retries.**
Client sends an idempotency key; server checks the key store in the same transaction that inserts the order (unique constraint on the key); on a duplicate key, return the existing order's response. Result: retries are safe, no duplicate orders, consistent response.
