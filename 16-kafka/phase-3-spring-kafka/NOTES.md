<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · topics partitions](../phase-2-topics-partitions/NOTES.md) | [Phase 4 · delivery patterns ➡](../phase-4-delivery-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Kafka Integration: Notes

Spring Kafka wraps the raw Kafka clients in familiar Spring idioms: a template to produce,
an annotation to consume, and config-driven serialization and groups. See the `examples/`
producer, consumer, and `application.yml`.

## 3.1 — Producing with `KafkaTemplate`

- Inject **`KafkaTemplate<K, V>`** and call **`send(topic, key, value)`** (the example
  producer). It's async (returns a `CompletableFuture`); add a callback to handle
  success/failure or `.get()` to block for confirmation.
- **Serialization:** configure key/value serializers. Strings for keys; **JSON**
  (`JsonSerializer`) for the event value so it's language-agnostic and readable. (Production
  systems often use **Avro/Protobuf + a Schema Registry** for compact, schema-evolvable
  messages — mention.)
- The **key** you pass drives partitioning/ordering (Phase 2) — a first-class design choice,
  not an afterthought.

## 3.2 — Consuming with `@KafkaListener`

- Annotate a method with **`@KafkaListener(topics = "...", groupId = "...")`** and Spring runs
  a consumer in that group, invoking the method per record (the example consumer). Multiple
  app instances with the same `groupId` form the group and share partitions.
- **Manual ack** (`ack-mode: manual` + `Acknowledgment.acknowledge()`) — commit the offset
  **after** successful processing for **at-least-once** (Phase 4). Auto-commit is easier but
  can lose or double-process around failures.
- **Concurrency:** `concurrency = "3"` (or the container factory) runs several consumer
  threads in one app, up to the partition count — parallelism within a process.
- **Error handling:** a `DefaultErrorHandler` with backoff retries transient failures and can
  route poison messages to a **dead-letter topic** (Phase 4.2) after N attempts, so one bad
  record doesn't block the partition forever.

## 3.3 — Config that matters

From `application.yml`:

- **`bootstrap-servers`** — the broker list (externalized via `KAFKA_BROKERS` env for
  Docker/K8s).
- **`acks=all` + `enable.idempotence=true`** — durable, duplicate-free produce.
- **`auto-offset-reset: earliest|latest`** — where a brand-new group starts (earliest = read
  all history; latest = only new records).
- **`spring.json.trusted.packages`** — security: restrict which classes the JSON deserializer
  will instantiate (don't allow arbitrary deserialization).
- **`enable-auto-commit: false`** — pair with manual ack for controlled offset commits.

## Perspective

Spring Kafka reduces event plumbing to **`KafkaTemplate.send`** on the producer side and
**`@KafkaListener` + manual ack** on the consumer side, with serialization, groups, and error
handling in config. The decisions that actually matter are the same as raw Kafka —
**partition key** (ordering/parallelism), **`acks`/idempotence** (durability), and
**offset-commit timing + error handling** (delivery semantics and poison-message handling,
Phase 4). Spring just makes them ergonomic.
