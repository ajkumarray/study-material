<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring kafka](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 4 — Delivery Semantics & Patterns: Notes

The hard, interview-favorite part: **what guarantees does Kafka actually give end to end**, and
the architectural patterns that get built on top of those guarantees. This directly extends
Phases 2-3 (offset-commit timing, the idempotent producer) and ties into the System Design
track (idempotency Phase 4, messaging Phase 9). The track's `ExpenseEventConsumer` (manual ack
+ dedupe on `eventId`) is the running example throughout.

## 4.1 — Delivery semantics: at-most-once, at-least-once, exactly-once

These three levels describe how many times a given record's *effects* can be applied by a
consumer, and are determined mostly by **when** offsets are committed relative to processing
(Phase 2.3, 3.4).

### Key Concepts

- **At-most-once** — commit the offset **before** processing. If the consumer crashes after
  committing but before finishing, the record is gone: it will never be redelivered, so its
  effects are **lost**. Fastest option, but lossy — rarely the right default.
- **At-least-once** — commit the offset **after** processing completes (this track's manual
  `ack.acknowledge()`). If the consumer crashes before committing, the record **is
  redelivered** on restart/rebalance — so it may be **processed more than once**. Records are
  never silently lost, but duplicates are possible. **The common production default.**
  Requires the consumer to be idempotent (4.1 continued below).
- **Exactly-once** — each record affects the final outcome exactly once, with neither loss nor
  duplication. Kafka supports this **within Kafka itself** via the idempotent producer (Phase
  2.2) combined with **transactions** (4.2 below) for atomic consume-process-produce chains.
  True end-to-end exactly-once — including a side effect in an *external* system like a
  database or a third-party API call — is fundamentally hard, because you'd need that external
  write and the Kafka offset commit to succeed or fail together atomically, and most external
  systems don't participate in Kafka's transaction protocol. In practice, the standard,
  battle-tested approach is **at-least-once delivery + an idempotent consumer**, which produces
  *effectively* exactly-once outcomes without needing a true distributed transaction.

### Worked example — the three semantics on the same failure

```
Scenario: consumer crashes right after finishing business logic for record X,
but before the next scheduled step.

At-most-once (commit BEFORE processing):
  1. commit offset for X         ← committed
  2. process X (update DB)       ← CRASH happens here, mid-processing
  3. (never reached)
  → On restart: X's offset is already committed, so X is skipped.
    X's business effect never happened. Silent data loss.

At-least-once (commit AFTER processing):
  1. process X (update DB)       ← completes successfully
  2. commit offset for X         ← CRASH happens here, before commit lands
  → On restart: offset for X was never committed, so X is redelivered.
    Business logic runs AGAIN for X — X's DB update is applied a second time.
    No data is lost, but a duplicate application of X's effect can occur.

Exactly-once effect via idempotent consumer (at-least-once + dedupe on eventId):
  1. process X: "have I already applied eventId=X.eventId? if yes, no-op."
  2. commit offset for X
  → Redelivery of X after a crash re-runs the handler, but the dedupe check
    makes the second run a no-op. Net effect: applied exactly once.
```

### Idempotent consumer — the practical key to making at-least-once safe

Since at-least-once is the standard default, and it can redeliver, the single most important
consumer-side pattern is making reprocessing **harmless**:

- **Dedupe on a unique event id** — the track's `ExpenseEvent.eventId()` exists exactly for
  this: before applying an event's effect, check (in the same transaction/table, or a fast
  lookup like Redis) whether that `eventId` has already been processed, and skip if so.
- **Use UPSERTs instead of INSERTs** — `INSERT ... ON CONFLICT DO UPDATE` (or equivalent) means
  reapplying the same event twice converges to the same final row state instead of erroring or
  double-inserting.
- **Prefer operations that are naturally idempotent** — "set the balance to $50" is idempotent
  by construction; "add $10 to the balance" is not (applying it twice adds $20). Where possible,
  design event payloads and handlers around the former.
- This connects directly to idempotency as covered in the System Design track (Phase 4 there) —
  Kafka's at-least-once default is one of the most common real-world reasons idempotency
  actually matters in a system.

### Why it's useful

Defaulting to at-least-once plus an idempotent consumer sidesteps the enormous complexity of
building true distributed exactly-once across Kafka and an external database, while still
getting a correctness guarantee that's good enough for the overwhelming majority of real
systems: no event is ever silently dropped, and reprocessing a duplicate is a cheap no-op rather
than a bug.

## 4.2 — The idempotent producer and Kafka transactions

Exactly-once *within* Kafka (not involving an external system) is a real, supported feature —
worth knowing precisely, since interviewers often probe whether "exactly-once" is being used
loosely or accurately.

### Key Concepts

- **Idempotent producer** (`enable.idempotence=true`, Phase 2.2) — the broker assigns each
  producer a **producer ID (PID)** and tracks a **sequence number per partition**; if the same
  producer retries a send (e.g. after a timeout), the broker recognizes the sequence number it
  already has and discards the duplicate rather than writing it again. This alone gives
  exactly-once **produce** — no duplicate writes from producer-side retries — but says nothing
  about consumer-side duplicate processing.
- **Transactions** (`transactional.id` configured on the producer) extend this to atomic
  **consume-process-produce** chains: a service that reads from one topic, does some processing,
  and writes to another topic (or several) can wrap the reads' offset commits and the writes in
  one Kafka transaction, so either **all** of it becomes visible or **none** of it does — even
  across a crash mid-way.
- **Read-committed isolation** — a downstream consumer configured with
  `isolation.level=read_committed` only ever sees records from **committed** transactions,
  never from one that was aborted or is still in-flight. Consumers using the default
  `read_uncommitted` would see every write immediately, including ones later rolled back.
- **What this does NOT cover** — a side effect outside Kafka (writing to a Postgres table, calling
  a third-party API) is not part of the Kafka transaction. A consumer that both writes to its own
  database *and* produces a new Kafka event as a reaction cannot make both atomic through Kafka
  transactions alone — this is exactly the gap the Outbox pattern (4.3) exists to close.

### Worked example — why transactions matter for chained topics

```
Service reads from "orders" topic, validates, and produces to "orders-validated" topic.

WITHOUT transactions:
  1. process order record from "orders"
  2. produce result to "orders-validated"   ← succeeds
  3. CRASH before committing the "orders" offset
  → On restart, the same order record is redelivered from "orders" and reprocessed,
    producing a SECOND, duplicate record to "orders-validated".

WITH a Kafka transaction wrapping steps 2 and the offset commit:
  1. process order record from "orders"
  2. beginTransaction()
  3. produce result to "orders-validated"
  4. send offsets for "orders" as part of the same transaction
  5. commitTransaction()   ← both the produce AND the offset commit become visible atomically
  → A crash before commitTransaction() means NEITHER the produce nor the offset advance
    happened — on restart, the original "orders" record is safely reprocessed from scratch,
    with no partial, duplicated "orders-validated" record left behind.
```

### Why it's useful

Kafka transactions are what real exactly-once claims about Kafka actually rest on — they make a
multi-topic consume-process-produce sequence atomic, which is genuinely valuable for
stream-processing pipelines chaining several topics together. The key interview nuance:
transactions guarantee atomicity **within Kafka**, not across Kafka and an arbitrary external
system — that broader problem needs a different pattern (Outbox, 4.3).

## 4.3 — Ordering, retries, and dead-letter topics

### Key Concepts

- **Ordering** is guaranteed only **within a partition** (Phase 2.1) — this doesn't change once
  delivery semantics enter the picture, but retries interact with it in subtle ways.
- **Naive retries can reorder or block a partition.** If a consumer retries a failed record
  in-place (blocking the partition until it succeeds or gives up), later records on that
  partition wait behind it — correct ordering, but at the cost of throughput. If instead a
  consumer skips ahead and retries the failed record out-of-band later, it risks processing
  later records *before* an earlier one that's still being retried — breaking order. Spring
  Kafka's `DefaultErrorHandler` blocks the partition through its configured retry/backoff by
  default, preserving order at the cost of some throughput during retries.
- **Retries + backoff** absorb transient failures (a downstream service blip, a momentary DB
  connection issue) without treating every failure as fatal on the first attempt.
- **Dead-letter topic (DLQ)** — after retries are exhausted, route the record to a separate
  topic instead of blocking its partition forever on a record that can never succeed (a
  malformed payload, a permanent downstream 4xx). The main partition then keeps moving; the DLQ
  is inspected and reprocessed manually or by a separate recovery job, out-of-band from the
  primary flow.
- **Consumer lag** — the gap between the latest offset written to a partition and a consumer
  group's committed offset (`kafka-consumer-groups.sh --describe`, Phase 2.3) is the primary
  Kafka health signal. Small, stable lag means a group is keeping up; steadily growing lag means
  it can't (scale out consumers up to the partition count, add partitions, or optimize
  per-record processing time).

### Why it's useful

Retries + a DLQ is the standard, production-proven pattern for "don't let one bad record take
down an entire partition's throughput forever, but also don't silently drop it" — the DLQ gives
you a durable, inspectable record of exactly what failed and why, instead of a log line that
scrolls away.

## 4.4 — Architectural patterns built on Kafka

### Event-driven microservices

Services communicate by publishing and consuming events instead of calling each other directly
(Phase 1.1) — loose coupling, independent scaling and deployment, and easy extension (add a new
consumer without touching the producer). The trade-off is eventual consistency and the added
complexity of distributed tracing across asynchronous hops (System Design, Phase 9).

### Event sourcing

Instead of storing only current state (a row that gets overwritten), store the full **log of
events** that led to that state as the source of truth; current state is derived by replaying
the log (or from periodically-computed snapshots, for efficiency). Kafka's retention (Phase 1.4)
makes it a natural fit for an event store. Benefits: a complete audit history, the ability to
rebuild any past state, and easy reprocessing when business logic changes. Costs: real
complexity — querying "current state" directly requires materializing a read model (see CQRS
below), and replaying a very long history can be slow without snapshotting.

### CQRS (Command Query Responsibility Segregation)

Separate the **write model** (what accepts and validates changes) from **read models** built by
consuming the resulting events. The track's `expense-analytics` consumer group is exactly this:
it builds a materialized, query-optimized read model by consuming `expense-events`, entirely
separate from the write-side Postgres table the API writes to directly. CQRS pairs naturally
with event sourcing (the events are both the write-side source of truth and the read-side
model's input) but doesn't strictly require it — you can build CQRS read models from events
emitted alongside a conventional CRUD write model, which is what this track's capstone actually
does.

### The Outbox pattern

Answers the classic **dual-write problem**: how do you update your database *and* publish a
Kafka event as one atomic unit, when a database transaction and a Kafka produce are two
separate systems that can't share a single distributed transaction? A naive
"commit the DB write, then send to Kafka" has a gap where the DB commits but the process
crashes before the Kafka send — the event is silently never published, and downstream
consumers never learn the change happened (or the reverse: the Kafka send happens, but the DB
transaction later rolls back, publishing a phantom event for a change that never actually
took effect).

The Outbox pattern's fix: write the event to an **outbox table in the exact same database
transaction** as the business change. A separate relay process (commonly **CDC via Debezium**,
reading the database's write-ahead/replication log) watches that table and publishes new rows
to Kafka, then marks them published. Since the outbox row and the business change commit or
roll back **together** (one local DB transaction), the event is guaranteed to be published if
and only if the business change actually took effect — no dual-write gap, no distributed
transaction across two different systems required.

```
Business transaction (one local DB transaction, atomic):
  BEGIN;
    UPDATE expenses SET amount_cents = 3199 WHERE id = 101;
    INSERT INTO outbox (event_id, payload, published) VALUES ('...', '{...}', false);
  COMMIT;
  -- Both rows commit together, or neither does. No gap.

Debezium (or a polling relay), running separately, asynchronously:
  reads new outbox rows (via CDC on the DB log, or a polling query)
  → publishes each to Kafka
  → marks published = true (or deletes the row)
```

### Saga

Coordinates a multi-service transaction as a sequence of local transactions and events, with
explicit **compensating actions** to undo earlier steps if a later step fails — instead of a
distributed two-phase commit (2PC) across services, which doesn't scale well and couples
services' availability together. Each service commits its own local change and publishes an
event; the next service reacts. If step 3 of 4 fails, previously-completed steps 1-2 run their
compensating actions (e.g. "cancel reservation" undoing "reserve inventory"). Used for
long-running, cross-service workflows (an order-fulfillment pipeline spanning payment,
inventory, and shipping services) where a single ACID transaction across all of them isn't
feasible.

## Perspective

Default to **at-least-once + idempotent consumers** — accept that redelivery can happen and
make reprocessing a harmless no-op (dedupe on an event id, use UPSERTs) rather than chasing true
end-to-end exactly-once, which is genuinely hard once an external system is involved. Keep
related events on one partition key for ordering, use retries + a DLQ so one bad record can't
block a whole partition forever, and watch consumer lag as your primary health signal.
Architecturally, Kafka enables event-driven microservices, event sourcing, CQRS, and (via the
Outbox pattern) safely bridges the durability gap between a database transaction and an event
publish. The recurring theme across this entire track: **idempotency is what makes
at-least-once safe, and the retained log is what makes everything replayable.**

## Summary / Key Takeaways

- **At-most-once** (commit before processing) can lose records; **at-least-once** (commit after,
  this track's default) can redeliver/duplicate but never silently loses; **exactly-once** is
  achievable *within* Kafka via the idempotent producer + transactions, but end-to-end exactly-
  once including an external system is impractical — use at-least-once + an idempotent consumer
  instead.
- **Idempotent consumer** = dedupe on a unique event id (or use UPSERTs / naturally-idempotent
  operations) so redelivery is a safe no-op.
- **Kafka transactions** (`transactional.id`, `isolation.level=read_committed`) make a
  multi-topic consume-process-produce chain atomic — but only across Kafka topics, not an
  external database.
- **Retries + backoff + a dead-letter topic** keep one poison record from blocking its
  partition forever while preserving order for everything else; **consumer lag** is the primary
  health metric to watch.
- **The Outbox pattern** (write the event to an outbox table in the same DB transaction as the
  business change; a CDC relay like Debezium publishes it) is the standard fix for the
  dual-write problem between a database and Kafka.
- **Event sourcing**, **CQRS**, and **Saga** are the architectural patterns event-driven systems
  reach for once state, reads, and multi-service workflows all need to be built on top of a
  durable event log.
