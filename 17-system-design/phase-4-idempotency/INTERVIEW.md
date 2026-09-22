<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · databases scale](../phase-3-databases-scale/NOTES.md) | [Phase 5 · web tier ➡](../phase-5-web-tier/NOTES.md)
<!-- /nav -->

# Phase 4 — Concurrency & Idempotency: Interview Q&A

⭐ = asked constantly (system-design and senior-backend interviews).

**Q: A user double-clicks "Submit" and two identical orders are created. How do you
prevent it — server-side, not client-side?** ⭐⭐
Client-side fixes like disabling the button after click reduce accidental duplicates
from a fast double-click, but they're not a guarantee — a network retry, a proxy replay,
or a client that simply doesn't disable the button will still hit the server twice. The
real fix is server-side: a **UNIQUE constraint** on the natural key so the database
itself rejects the second insert atomically no matter how many requests race, and/or an
**idempotency key** — the client generates a unique token per logical operation, sends
it (commonly as an `Idempotency-Key` header), and the server stores it with the
operation's result; a repeated request with the same key gets back the *original*
result instead of re-executing the side effect. This maps to why GET/PUT/DELETE are
defined as idempotent by the HTTP spec but POST isn't — POST is exactly the method used
for "create," and creates are exactly the operations that need this explicit handling.

**Q: What is an idempotency key and how does it work end to end?** ⭐⭐
A client-generated unique token (typically a UUID) identifying one logical operation,
sent with the request — most commonly as an `Idempotency-Key` header. The server, in the
same transaction that performs the side effect, records the key along with the
operation's result (or a reference to the created resource). If a request arrives with a
key that's already recorded, the server short-circuits: it returns the stored result
instead of re-executing anything. This is exactly how payment APIs like Stripe make
"charge this card" — an operation you absolutely cannot safely retry blindly — safe to
retry: if a client times out waiting for a response, it can safely resend the identical
request with the same key, and the server guarantees the card is charged at most once
regardless of how many times the request is retried.

*Follow-up: where do you store idempotency keys, and for how long?* Typically a table
(or Redis with a TTL) keyed by the idempotency key, storing the request's outcome; keys
are usually retained for a bounded window (24 hours is common for payment APIs) since a
client won't meaningfully retry a request from a week ago, and unbounded retention would
grow the table forever without benefit.

**Q: Two requests read an account balance and both write back an increment; one update
is lost. What are your fix options and how do they differ?** ⭐⭐
This is the classic read-modify-write race: both requests read the same starting value,
each computes a new value independently, and whichever writes second silently
overwrites the first — no error, the increment just doesn't count. Optimistic locking
adds a version column and makes the write conditional: `UPDATE account SET balance = ?,
version = version + 1 WHERE id = ? AND version = ?` — if the row's version already
changed since you read it, the statement affects zero rows, telling you to reload and
retry (JPA's `@Version` does this automatically and throws `OptimisticLockException` for
you to catch). Pessimistic locking instead takes an exclusive lock up front —
`SELECT ... FOR UPDATE` — so every other writer blocks until the lock holder commits,
guaranteeing correctness with zero retries but serializing all writers through that row.

*Follow-up: how does this map to Java's concurrency primitives you'd have used
in-process?* Optimistic locking is exactly `AtomicReference.compareAndSet` — read a
value, attempt to swap it only if it hasn't changed, loop-and-retry on failure.
Pessimistic locking is exactly `synchronized` — take an exclusive lock around the whole
read-modify-write critical section so no other thread can interleave. The database
versions of these are the same idea implemented across processes/machines instead of
threads.

**Q: Optimistic vs. pessimistic locking — how do you decide which to use in production?** ⭐⭐
Optimistic by default: it holds no locks while reading, so it scales well under normal,
low-contention web traffic where most concurrent requests touch different rows and
genuine conflicts are rare — the cost is wasted retry work specifically on the rows that
*do* collide. Escalate to pessimistic locking when contention is measurably high on a
specific hot resource (a single popular item's inventory count during a flash sale,
where dozens of requests genuinely target the exact same row simultaneously) or when the
work being redone on a retry is itself expensive enough that paying for a lock up front
beats paying for repeated retries. Both sit on top of the database's transaction
isolation level, not instead of it — a lower isolation level like READ COMMITTED still
allows the underlying lost-update anomaly, which is exactly why you add one of these
mechanisms on top rather than relying on isolation level alone.

**Q: What does "exactly-once" actually mean in a distributed system, and why can't you
just build it directly?** ⭐
True exactly-once *delivery* is not achievable in general in an asynchronous distributed
system — a message can always be lost or duplicated by a network failure at exactly the
wrong moment, and there's no protocol that can distinguish "the ack was lost" from "the
message was never received" without retrying, which reintroduces duplication risk.
What's actually achievable, and what production systems mean when they say
"exactly-once," is **at-least-once delivery plus idempotent consumers**: the sender
retries until it gets an acknowledgment (so nothing is silently dropped), and the
receiver recognizes and no-ops duplicates by tracking a message/event id it's already
processed. So you don't design against duplicates arriving — you design for duplicates
to arrive and be harmless.

**Q: What is an upsert and what race does it eliminate?**
Insert-or-update in a single atomic database statement —
`INSERT ... ON CONFLICT (key) DO UPDATE` in Postgres, `INSERT ... ON DUPLICATE KEY
UPDATE` in MySQL, or `MERGE` more generally. It eliminates the same class of
check-then-act race as 4.1, but for "create if missing, otherwise update" instead of
"create if missing, otherwise reject": a hand-written `SELECT` to check existence
followed by a conditional `INSERT` or `UPDATE` has a race window between the check and
the write; an upsert statement evaluates the whole thing as one atomic operation with no
window for a concurrent request to interleave.

**Q: Why can't you just wrap a multi-service operation in a database transaction?** ⭐
Each service in a microservices architecture owns its own database — there's no shared
transaction boundary across them, so a single `BEGIN ... COMMIT` can't span "reserve
inventory in service A" and "charge payment in service B." Two-phase commit exists to
solve exactly this (a coordinator gets every participant to vote "ready," then tells
everyone to commit), but it's slow — extra round trips before every commit — and fragile
— if the coordinator dies mid-protocol, participants can be stuck holding locks
indefinitely. In practice, systems use the **saga pattern** instead: a sequence of local
transactions, each service committing its own step, with an explicit compensating action
per step (e.g., "release inventory") to undo prior steps if a later one fails. You trade
distributed ACID for eventual consistency plus explicit, application-level rollback
logic.

**Q: What is the outbox pattern and what specific problem does it solve?** ⭐
The dual-write problem: updating your database and publishing an event about that update
are two separate operations that aren't atomic by default — the database write can
succeed while the message broker publish fails (the event is lost even though the state
changed), or the publish can succeed while the transaction rolls back (an event goes out
for a change that never actually happened). The outbox pattern writes the event as a row
in an `outbox` table, in the **same local database transaction** as the actual state
change. A separate relay process — polling the table, or tailing the database's
write-ahead log via change-data-capture — then reads and publishes those rows to the
message broker afterward. This guarantees the event is published if and only if the
state change actually committed, because both are part of one atomic local transaction.

**Q: How do transaction isolation levels relate to these races?**
Isolation level determines which anomalies are even possible at the database layer:
dirty reads, non-repeatable reads, phantom reads (Databases Phase 5). The common default,
READ COMMITTED, still allows the lost-update race described above — two transactions can
each read the same committed value and both write, with the second silently overwriting
the first, and READ COMMITTED alone does nothing to prevent it. SERIALIZABLE isolation
would prevent it but at a real cost to concurrency (more blocking, more aborts under
contention). That's precisely why you layer optimistic or pessimistic locking on top of
whatever isolation level you're running at, rather than treating isolation level as a
substitute for explicit conflict handling.

**Q: A message queue may deliver a payment event twice. How do you avoid double-charging
a customer?** ⭐
Make the consumer idempotent rather than trying to make delivery exactly-once: record
processed event ids in a dedupe store (a table or Redis set keyed by event id, checked
in the same transaction as applying the effect) and skip any event id already seen —
this is exactly the pattern the demo's `processed.add(idempotencyKey)` implements, where
100 simulated deliveries of the same payment id apply the balance change exactly once
because 99 of them are recognized as duplicates and no-op. Where possible, also design
the effect itself to be naturally idempotent (`SET balance = X` rather than
`balance += amount`), and add a unique constraint on the transaction id as a backstop in
case the dedupe check itself has a bug.

**Q: Design a "create order" endpoint that's safe under client retries.**
Client generates an idempotency key per logical order attempt and sends it with the
request. The server, in the same database transaction that inserts the order row,
checks/inserts that key into an idempotency-key table with a unique constraint on the
key; if the insert of the order and the key both succeed, return the new order. If a
retry arrives with the same key, the unique constraint on the key table causes that
insert to conflict — the server catches this and returns the *original* order's response
instead of creating a second order or erroring out. Combined with a unique constraint on
whatever natural key the order itself has (e.g., `cart_id` for a one-order-per-checkout
rule), this makes the endpoint safe to retry any number of times with a consistent,
correct outcome each time.
