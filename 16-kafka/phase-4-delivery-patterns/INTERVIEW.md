<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring kafka](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 4 — Delivery Semantics & Patterns: Interview Q&A

⭐ = asked constantly.

**Q: Explain at-most-once, at-least-once, and exactly-once.** ⭐⭐
At-most-once commits offsets before processing — records can be lost, never duplicated.
At-least-once commits after processing — records are never lost but may be redelivered
(processed more than once). Exactly-once ensures each record affects the outcome once, via
Kafka's idempotent producer + transactions. At-least-once is the common default.

**Q: How do you handle duplicate messages?** ⭐⭐
Make the consumer idempotent: dedupe on a unique event id, use UPSERTs/idempotent operations,
or track processed ids (in Redis/DB) and skip repeats. Since at-least-once can redeliver
(e.g. after a rebalance before commit), idempotent processing makes reprocessing harmless —
effectively exactly-once outcomes.

**Q: Is true end-to-end exactly-once achievable?** *nuance*
Within Kafka, yes — idempotent producer plus transactions give atomic consume-process-produce.
But once an external system (a database) is involved, exactly-once is very hard, so teams
usually implement at-least-once with idempotent consumers, which yields exactly-once *effects*
without the full distributed-transaction cost.

**Q: How does Kafka guarantee ordering?** ⭐
Only within a partition. Records with the same key go to the same partition and stay ordered;
there's no global order across partitions. To keep an entity's events ordered, key by that
entity's id. Retries must be handled carefully to avoid reordering within a partition.

**Q: What is a dead-letter topic and why use one?** ⭐
A separate topic where records that fail processing after retries are sent, so a poison
message doesn't block its partition forever. You monitor and reprocess the DLQ out-of-band.
It's the standard way to isolate unprocessable messages while keeping the main flow moving.

**Q: What is the outbox pattern and what problem does it solve?** ⭐⭐
It solves the dual-write problem — updating the DB and publishing an event atomically. You
write the event to an outbox table in the *same* DB transaction as the business change, then a
relay (CDC/Debezium) reads the outbox and publishes to Kafka. This guarantees the event is
published iff the DB change committed, avoiding lost or phantom events.

**Q: What is event sourcing?** ⭐
Storing state as an append-only log of events (the source of truth) instead of only current
state; you rebuild state by replaying events. Kafka's retained log fits naturally. Benefits:
full history/audit and replay; costs: complexity and eventual consistency. Often paired with
CQRS (separate read models built from the events).

**Q: What metric tells you consumers are healthy?**
Consumer lag — how far behind the latest offset a group is. Steady/low lag means consumers
keep up; growing lag means they can't (scale out, add partitions, or optimize processing). It's
the primary Kafka health/observability signal (System Design Phase 10).

**Q: When would you use Kafka vs a simpler queue or Redis Pub/Sub?** *nuance*
Kafka for durable, replayable, high-throughput event streaming, event sourcing, and decoupling
many consumers with retention. A queue (RabbitMQ) for task distribution and complex routing
where messages are consumed once. Redis Pub/Sub for ephemeral, low-latency fan-out where
missing a message is acceptable. Match the tool to durability, replay, and ordering needs.
