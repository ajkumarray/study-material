<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring kafka](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 4 — Delivery Semantics & Patterns: Notes

The hard, interview-favorite part: **what guarantees does Kafka actually give**, and the
architectural patterns built on it. This ties directly to System Design (idempotency Phase 4,
messaging Phase 9).

## 4.1 — Delivery semantics

Three levels, determined mostly by **when you commit offsets** (Phase 2.3):

- **At-most-once** — commit the offset **before** processing. If you crash after committing but
  before finishing, the record is **lost** (never redelivered). Fastest, lossy. Rare.
- **At-least-once** — commit **after** processing (the example's manual ack). If you crash
  before committing, the record is **redelivered** — so you may process it **more than once**.
  **The common default.** Requires **idempotent** consumers.
- **Exactly-once** — each record affects the outcome exactly once. Kafka supports it via the
  **idempotent producer** + **transactions** (atomic "consume-process-produce" within Kafka).
  But end-to-end exactly-once *including an external DB* is hard; in practice people do
  **at-least-once + idempotent processing**, which is *effectively* exactly-once for the
  outcome.

**Idempotent consumer** (the practical key): design processing so handling the same event
twice yields the same result — dedupe on the **event id** (the example's `eventId`), use
UPSERTs, or track processed ids (in Redis/DB). This is why the consumer carries an idempotency
key. (Idempotency — System Design Phase 4.)

## 4.2 — Ordering, retries, dead-letter topics

- **Ordering** is per-partition (Phase 2). Retentioning order under **retries** is subtle: a
  naive retry of a failed record can reorder or block; error handlers retry with care, and for
  strict ordering you pause the partition. Keep related events on one key/partition.
- **Retries + backoff** handle transient failures (a downstream blip). After exhausting
  retries, send the record to a **dead-letter topic (DLQ)** — a separate topic for failed
  records — so a single poison message doesn't block its partition indefinitely. Monitor and
  reprocess the DLQ.
- **Consumer lag** (how far behind the latest offset a group is) is *the* key health metric —
  growing lag means consumers can't keep up (scale out / optimize). Observability, System
  Design Phase 10.

## 4.3 — Architectural patterns

- **Event-driven microservices** — services communicate via events, not direct calls:
  loose coupling, independent scaling/deploy, easy extension (add a consumer). The trade-off is
  eventual consistency and distributed tracing complexity (System Design Phase 9).
- **Event sourcing** — store state as the **log of events** (the source of truth) rather than
  just current state; rebuild state by replaying. Kafka's retention makes it a natural event
  store. Powerful (full history, audit, replay) but complex.
- **CQRS** — separate the write model from **read models** built by consuming events (the
  example's analytics consumer builds a read model). Pairs with event sourcing.
- **The Outbox pattern** — the classic "how do I update my DB *and* publish an event
  atomically?" answer: write the event to an **outbox table in the same DB transaction** as
  the business change, then a relay (e.g. Debezium/CDC) publishes it to Kafka. Avoids the
  dual-write problem (DB commit succeeds but Kafka publish fails, or vice versa). Interview
  gold.
- **Saga** — coordinate a multi-service transaction as a sequence of events with compensating
  actions (no distributed 2PC). For long-running cross-service workflows.

## Perspective

Default to **at-least-once + idempotent consumers** — accept possible redelivery and make
reprocessing harmless (dedupe on event id). Keep related events on one partition key for
ordering, use retries + a DLQ for failures, and watch consumer lag. Architecturally, Kafka
enables event-driven services, event sourcing, CQRS, and the outbox pattern — the toolkit for
decoupled, scalable, replayable systems (System Design Phase 9). The recurring theme:
**idempotency makes at-least-once safe, and the log makes everything replayable.**
