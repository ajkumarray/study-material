<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 16 — Kafka (Event-Driven Architecture)

**A distributed, durable, replayable log for streaming events between services.** Kafka is
the backbone of event-driven systems: producers append events to topics, consumers read them
at their own pace, and the log persists — decoupling who *emits* data from who *reacts* to it.

Taught for someone with the **Spring Boot API (02)** and the **Redis (15)** track (whose
Pub/Sub is the fire-and-forget contrast). Kafka is where the repo goes from request/response
to **event-driven** — the architecture the System Design track (17) calls for at scale.

> **Format:** no Kafka broker runs here, so it's **theory + real Spring Kafka code/config**
> (`examples/`), correct-by-construction — the DB/Docker/K8s/Redis-track approach.

## The artifacts: `examples/`

| File | What it shows |
|---|---|
| `application.yml` | Spring Kafka config: brokers, JSON ser/de, `acks=all`, idempotent producer, manual-commit consumer group |
| `ExpenseEventProducer.java` | `KafkaTemplate` publishing events, **keyed by userId** (partition → ordering) |
| `ExpenseEventConsumer.java` | `@KafkaListener` in a consumer group, **manual ack** + **idempotent** processing |
| `ExpenseEvent.java` | an immutable domain event (record) with an `eventId` (idempotency key) |

## Curriculum

### Phase 1 — Kafka & event-driven architecture ✅
- [x] 1.1 What Kafka is: the distributed commit log; why event-driven vs request/response
- [x] 1.2 Core model: topics, partitions, offsets, brokers, the retained log
- [x] 1.3 Kafka vs a message queue (RabbitMQ) vs Redis Pub/Sub — durability & replay
- NOTES · INTERVIEW

### Phase 2 — Topics, partitions, producers & consumers ✅
- [x] 2.1 Partitions & keys: parallelism + per-key ordering; offsets
- [x] 2.2 Producers: keys, `acks`, batching, idempotence
- [x] 2.3 Consumer groups: scaling, partition assignment, rebalancing, offset commits
- NOTES · INTERVIEW

### Phase 3 — Spring Kafka integration ✅
- [x] 3.1 `KafkaTemplate` producer; serialization (JSON)
- [x] 3.2 `@KafkaListener` consumers; groups; manual ack; error handling
- [x] 3.3 Config: `acks`, `auto-offset-reset`, trusted packages, concurrency
- NOTES · INTERVIEW

### Phase 4 — Delivery semantics & patterns ✅
- [x] 4.1 At-most-once / at-least-once / exactly-once; idempotent consumers
- [x] 4.2 Ordering guarantees, retries, dead-letter topics (DLQ)
- [x] 4.3 Patterns: event-driven microservices, event sourcing, CQRS, the outbox pattern
- NOTES · INTERVIEW

### Capstone ✅
- [x] Event-driven expenses: the API publishes `ExpenseEvent`s; an analytics consumer group
  builds a read model — idempotent, keyed, manually committed. See `CAPSTONE.md`.

## How this connects

- **← Spring Boot (02):** producer/consumer live in the service layer via Spring Kafka.
- **↔ Redis (15):** Redis Pub/Sub = transient fan-out; Kafka = durable, replayable log —
  the two messaging tools, and when to use each.
- **↔ System Design (17):** event-driven architecture, decoupling, idempotency, and
  messaging (Phase 9) — Kafka is the concrete implementation.
- **↔ Databases (04):** event sourcing/CQRS and the outbox pattern bridge Kafka with the DB.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · event driven** — [Notes](phase-1-event-driven/NOTES.md) · [Interview](phase-1-event-driven/INTERVIEW.md)
- **Phase 2 · topics partitions** — [Notes](phase-2-topics-partitions/NOTES.md) · [Interview](phase-2-topics-partitions/INTERVIEW.md)
- **Phase 3 · spring kafka** — [Notes](phase-3-spring-kafka/NOTES.md) · [Interview](phase-3-spring-kafka/INTERVIEW.md)
- **Phase 4 · delivery patterns** — [Notes](phase-4-delivery-patterns/NOTES.md) · [Interview](phase-4-delivery-patterns/INTERVIEW.md)
<!-- /phases-nav -->
