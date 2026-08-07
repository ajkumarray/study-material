# Capstone — Event-Driven Expenses with Kafka

The Kafka track's synthesis: make the expense system **event-driven**. The Spring API
publishes an `ExpenseEvent` whenever an expense changes; an independent **analytics consumer
group** builds a read model by consuming those events — decoupled, ordered per user,
idempotent, and manually committed. Real Spring Kafka code in `examples/`.

> No Kafka broker runs here, so the artifacts are correct-by-construction Spring Kafka
> config/code, written to real-world standards.

## The flow

```
  POST /expenses ──▶ ExpenseService (writes Postgres) ──▶ ExpenseEventProducer
                                                              │ send("expense-events",
                                                              │       key=userId, event)
                                                              ▼
                                              Topic: expense-events  (partitioned by userId)
                                              ├─ partition 0 (users hash→0) ─ ordered
                                              ├─ partition 1 ...
                                              └─ partition N
                                                              │
                            ┌─────────────────────────────────┼─────────────────────────┐
                   group: expense-analytics           group: notifications (independent — gets all events)
                   @KafkaListener → build read model   @KafkaListener → send alerts
                   (idempotent on eventId, manual ack)
```

## Every phase, applied

| Phase | Concept | Where |
|---|---|---|
| 1 | Event-driven decoupling; durable log | API publishes and moves on; consumers react later, independently |
| 2 | Partition key = ordering; consumer group = scale | `send(topic, userId, event)`; `groupId = expense-analytics` |
| 3 | `KafkaTemplate` + `@KafkaListener` + manual ack | `ExpenseEventProducer`, `ExpenseEventConsumer`, `application.yml` |
| 4 | At-least-once + idempotent consumer; keyed ordering | manual `ack.acknowledge()` after processing; dedupe on `eventId` |

## Why each design choice

- **Key = `userId`** → all of a user's events land on one partition and stay **ordered**,
  while different users spread across partitions for **parallelism**.
- **`acks=all` + idempotent producer** → no lost or duplicated *produces*, even on retry.
- **Manual ack after processing** → **at-least-once**; a crash redelivers rather than loses.
- **`eventId` idempotency key** → redelivery is a harmless no-op (UPSERT the read model) —
  effectively exactly-once *effects* (System Design Phase 4).
- **Separate consumer groups** → add analytics, notifications, search-indexing, each getting
  *all* events independently, without touching the producer.

## Run it (in the real Spring app + Kafka)

```bash
# Start Kafka (add a kafka service to the Docker compose stack, track 12), then:
cd ../02-spring-boot/expense-api && ./mvnw spring-boot:run
# Watch the topic:
kafka-console-consumer --bootstrap-server localhost:9092 --topic expense-events --from-beginning
```

## The repo-wide picture

Kafka completes the middleware/infra half of the stack:

- **Redis (15)** made reads fast and the app tier stateless (cache + sessions).
- **Kafka (16)** decouples the write path from everything that *reacts* to it — analytics,
  notifications, search — via a durable, replayable log.
- Together with **Docker (12)** / **CI-CD (13)** / **Kubernetes (14)**, the expense app is now
  containerized, pipelined, orchestrated, cached, and event-driven — the full production shape
  the **System Design (17)** track theorizes.

## The one-sentence takeaway

Publishing expense changes as events to a durable Kafka log turns a tightly-coupled
request/response app into an **event-driven system** — new consumers plug in freely, failures
replay instead of vanish, and per-user ordering plus idempotent at-least-once processing keep
it correct at scale.
