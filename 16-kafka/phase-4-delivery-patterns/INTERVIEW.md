<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring kafka](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 4 — Delivery Semantics & Patterns: Interview Q&A

⭐ = asked constantly.

**Q: Explain at-most-once, at-least-once, and exactly-once — precisely, not just the names.** ⭐⭐
They describe how many times a record's effects can be applied, and the difference comes down
to *when* the consumer commits its offset relative to processing. At-most-once commits the
offset **before** processing — if the consumer crashes after the commit but before finishing,
the record is never retried and its effect is lost, but it's never duplicated either.
At-least-once commits **after** processing succeeds — a crash before the commit means the
record is redelivered on restart or rebalance and processed again, so records are never
silently lost, but the same record can be processed more than once. Exactly-once means each
record affects the final outcome exactly once, with neither loss nor duplication; Kafka
supports this internally via the idempotent producer plus transactions, but true end-to-end
exactly-once including an external system (a database write, a third-party API call) is very
hard to achieve, so the standard production approach is at-least-once combined with an
idempotent consumer.

**Q: How do you actually handle duplicate messages in a consumer?** ⭐⭐
Make the processing idempotent: dedupe on a unique identifier carried by the event (this
track's `ExpenseEvent.eventId()`) by checking — before or as part of applying the effect —
whether that id has already been processed, and skip if so. Prefer UPSERTs over plain INSERTs
so reapplying the same event converges to the same final state instead of erroring or
double-inserting. Where possible, design the event payload and handler around operations that
are naturally idempotent — "set the balance to $50" rather than "add $10 to the balance," since
the former produces the same result whether applied once or twice. Because at-least-once can
redeliver (most commonly after a rebalance reassigns a partition before the previous owner's
commit lands), idempotent processing is what turns "duplicates are possible" from a correctness
bug into a non-issue.

**Q: Is true end-to-end exactly-once achievable in practice?** *nuance* ⭐
Within Kafka itself, yes — the idempotent producer (deduping retried writes via a producer ID
and per-partition sequence number) combined with Kafka transactions (making a
consume-process-produce chain across topics atomic, with `isolation.level=read_committed`
hiding uncommitted/aborted writes from downstream consumers) gives genuine exactly-once
semantics for Kafka-to-Kafka pipelines. The moment an external system — a Postgres write, an
email send, a payment API call — is part of the outcome, true exactly-once becomes
impractically hard, because there's no way to make that external side effect and the Kafka
offset commit succeed or fail together as one atomic unit unless that system also participates
in Kafka's transaction protocol (which most don't). The pragmatic, standard answer is
at-least-once delivery with an idempotent consumer, which produces *effectively* exactly-once
outcomes without needing a true cross-system distributed transaction.

**Q: What exactly does the idempotent producer guarantee, and what does it NOT guarantee?**
It guarantees exactly-once **produce**: the broker tracks a producer ID and a sequence number
per partition, so if the same producer retries a send (say, after a network timeout where it
can't tell whether the original write actually landed), the broker recognizes the duplicate
sequence number and discards it rather than writing the record twice. It says nothing at all
about the consumer side — a consumer can still process the same successfully-produced record
more than once if it crashes between finishing processing and committing its offset. Producer
idempotence and consumer idempotence are two separate concerns solving two separate duplication
sources.

**Q: What do Kafka transactions add on top of the idempotent producer, and what's their
limitation?** ⭐
Transactions (a producer configured with a `transactional.id`) make a consume-process-produce
chain atomic across topics: a service reading from one topic, doing work, and writing to
another topic can wrap the produce and the source-topic offset commit into one transaction, so
either both become visible or neither does — even across a crash mid-way. Downstream consumers
reading with `isolation.level=read_committed` only ever see the results of committed
transactions, never a partial or later-aborted one. The limitation, and the key interview
nuance: this atomicity is scoped to **Kafka topics only**. It does not extend to a side effect
in an external database or API — a service that both writes to its own DB and produces a
reactive Kafka event cannot make both atomic through Kafka transactions alone. That gap is what
the Outbox pattern exists to close.

**Q: How does Kafka guarantee ordering, and how do retries interact with it?** ⭐
Only within a partition — records with the same key are routed to the same partition and stay
strictly ordered there; there's no ordering guarantee across different partitions of the same
topic. Retries complicate this: if a failed record is retried in place, blocking that partition
until it succeeds or is given up on, order is preserved but throughput on that partition suffers
during the retry. If instead a consumer skipped ahead and retried the failed record later
out-of-band, it risks processing subsequent records before an earlier one that's still pending
retry, breaking order. Spring Kafka's default error-handling behavior blocks the partition
through configured retries/backoff, trading some throughput for preserved order — the standard,
correct default.

**Q: What is a dead-letter topic and why use one?** ⭐
A separate topic that records failing processing get routed to after retries are exhausted, so
a permanently-unprocessable ("poison") record doesn't block its entire partition forever — since
Kafka delivers a partition's records strictly in order, a stuck offset there stalls every record
behind it too. The main flow keeps moving once the bad record is diverted; the DLQ is then
inspected and reprocessed separately, out-of-band, once the underlying issue (a malformed
payload, a permanently-broken downstream call) is understood or fixed.

**Q: What is the Outbox pattern, and what specific problem does it solve?** ⭐⭐
It solves the dual-write problem: you need to update your database and publish a Kafka event
for that same change as one atomic unit, but a database transaction and a Kafka produce are two
separate systems with no shared distributed transaction between them. A naive "commit the DB
write, then send to Kafka" has a real gap — the process can crash after the DB commit but
before the Kafka send, silently losing the event; or, less commonly, the Kafka send can go out
right before the surrounding DB transaction later rolls back, publishing a phantom event for a
change that never actually happened. The fix: write the event to an **outbox table in the same
local database transaction** as the business change, so they commit or roll back together
atomically. A separate relay process — commonly Debezium doing change-data-capture off the
database's own replication/write-ahead log, though a simpler polling job also works — watches
that outbox table and publishes new rows to Kafka, then marks them published. Because the
outbox row's existence is now guaranteed to match whether the business change actually
committed, the dual-write gap disappears without needing a real distributed transaction across
the database and Kafka.

*Follow-up: why not just publish the Kafka event, then write the DB row second?* Same problem in
reverse — the process could crash between the two steps, leaving a published event for a
database change that never happened, which is often worse (downstream consumers acting on data
that doesn't exist yet, or ever).

**Q: What is event sourcing, and what does it cost you?** ⭐
Storing the full log of events that led to the current state, as the source of truth, instead
of storing only the current state itself; you derive "current state" by replaying the log (or
from periodic snapshots, for efficiency at scale). Kafka's retained log fits this naturally.
Benefits: a complete audit trail, the ability to reconstruct any past state, and the freedom to
fix a bug in derived logic and simply replay history with the corrected code. Costs: genuine
complexity — you can no longer just `SELECT` current state from a normal table without first
materializing a read model from the events (which is exactly what CQRS is for), and replaying a
very long history efficiently requires snapshotting strategy.

**Q: How does CQRS relate to what this track's capstone actually builds?**
CQRS separates the write model (what validates and accepts changes) from read models built by
consuming the resulting events. The capstone's `expense-analytics` consumer group is a direct
example: the API's write path still writes to Postgres directly (a conventional write model),
while the analytics consumer independently builds a separate, query-optimized read model purely
by consuming `expense-events`. Note this doesn't require full event sourcing — the events here
are emitted *alongside* a conventional CRUD write path, not used as the sole source of truth —
CQRS and event sourcing are complementary patterns, not the same thing, and you can adopt one
without the other.

**Q: What is a Saga, and why not just use a distributed transaction (2PC) across services?**
A Saga coordinates a multi-service business transaction as a sequence of local transactions and
events, with explicit compensating actions defined to undo earlier steps if a later one fails —
e.g., an order-fulfillment flow spanning payment, inventory, and shipping services, where a
failed shipping step triggers a compensating "refund payment" and "release inventory" action
rather than rolling everything back with a two-phase commit. Two-phase commit across
independently-owned services couples their availability together (every participant must be up
and responsive for the commit to complete) and doesn't scale well across service boundaries or
survive partial network failures gracefully — Sagas trade strict atomicity for eventual
consistency plus explicit, application-level compensation logic, which fits how independently
deployed, independently available services actually behave in practice.

**Q: What metric tells you whether your consumers are healthy, and what do you do when it's
bad?**
Consumer lag — the difference between a partition's latest written offset and a consumer
group's committed offset, visible via `kafka-consumer-groups.sh --describe`. Small, stable lag
means the group is keeping up with production rate; steadily growing lag means it can't. The
remedies mirror Phase 2's scaling levers: add consumer instances to the group, up to the
partition count; increase partition count (planned carefully, since it changes existing keys'
partition assignment); or reduce per-record processing time in the listener itself. It's the
primary Kafka observability signal, closely tied to the System Design track's coverage of
reliability and performance monitoring.

**Q: When would you reach for Kafka vs a simpler queue or Redis Pub/Sub, given everything in
this phase?** *nuance*
Kafka for durable, replayable, high-throughput event streaming, event sourcing, and decoupling
many independent consumers with real retention — anywhere losing an event silently is
unacceptable and you may need to reprocess history. A traditional queue (RabbitMQ) for
task-distribution semantics and complex routing where a message is consumed once and gone. Redis
Pub/Sub for cheap, ephemeral, low-latency fan-out where an occasionally missed message (no
persistence at all) is genuinely fine. The delivery-semantics and pattern machinery in this
phase — idempotent consumers, transactions, the Outbox pattern, DLQs — is specifically the cost
of Kafka's durability and replay guarantees; it's not free, and it's the right trade only when
those guarantees are actually needed.
