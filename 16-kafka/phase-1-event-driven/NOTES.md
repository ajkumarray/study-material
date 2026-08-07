<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · topics partitions ➡](../phase-2-topics-partitions/NOTES.md)
<!-- /nav -->

# Phase 1 — Kafka & Event-Driven Architecture: Notes

Apache Kafka is a **distributed, durable, append-only commit log** for streaming events.
Instead of services calling each other directly, producers append **events** to Kafka and
consumers read them independently — decoupling the system in time and space.

## 1.1 — Event-driven vs request/response

- **Request/response (REST):** service A **calls** service B and waits; A must know B, B must
  be up, and A is coupled to B's latency/availability. Synchronous, point-to-point.
- **Event-driven (Kafka):** service A **publishes an event** ("expense.created") to a topic
  and moves on; any number of consumers react **later**, at their own pace. A doesn't know or
  wait for them. This gives:
  - **Decoupling** — add a new consumer (analytics, notifications, search index) without
    touching the producer.
  - **Resilience** — a consumer being down doesn't fail the producer; it catches up later
    (the log persists).
  - **Scalability & buffering** — Kafka absorbs bursts; consumers process at their rate.
  - **Replayability** — the event history is retained and can be re-read (rebuild a read
    model, onboard a new service, reprocess after a bug).
- Events are **facts that happened** (past tense) — the source of truth becomes the log of
  events (leads to event sourcing, Phase 4).

## 1.2 — The core model: the log

Kafka's one big idea is **the log** — an ordered, append-only, immutable sequence of records:

- A **topic** is a named log (e.g. `expense-events`). Producers append; consumers read.
- A topic is split into **partitions** — each an independent ordered log. This is how Kafka
  **scales** (partitions spread across brokers) and **parallelizes** consumers (Phase 2).
- Each record in a partition has a monotonically increasing **offset** (its position).
  Consumers track "where am I" by committing offsets — so they can stop/resume/replay.
- **Brokers** are the Kafka servers; a **cluster** of brokers holds the partitions, each
  **replicated** across brokers for fault tolerance. (Older Kafka used **ZooKeeper** for
  coordination; newer versions use built-in **KRaft**.)
- **Retention:** unlike a queue, reading **does not delete** — records stay for a configured
  time/size (hours, days, or forever). Many consumers can read the same data independently,
  and you can rewind. This *retained, replayable log* is Kafka's defining property.

## 1.3 — Kafka vs message queue vs Redis Pub/Sub

| | **Kafka** | **RabbitMQ (queue)** | **Redis Pub/Sub** |
|---|---|---|---|
| Model | durable partitioned **log** | broker routes to **queues** | in-memory **fan-out** |
| Retention | retains (replayable) | deleted on ack | none (transient) |
| Offline consumer | catches up later | queued for it | **misses** messages |
| Throughput | very high (log, batched) | high | high (but volatile) |
| Ordering | per-partition | per-queue | none guaranteed |
| Best for | event streaming, sourcing, pipelines, decoupling at scale | task queues, complex routing, RPC | ephemeral signals, cache invalidation |

- **vs RabbitMQ:** a traditional queue *deletes* a message once consumed and focuses on
  routing/work distribution. Kafka *retains* and lets many consumers replay — it's a *log*,
  not a queue. Use RabbitMQ for task queues/complex routing; Kafka for high-throughput event
  streaming and replay.
- **vs Redis Pub/Sub (track 15):** Redis Pub/Sub is fire-and-forget with no persistence —
  offline subscribers miss messages. Kafka persists and replays. **Redis = transient signal;
  Kafka = durable event backbone.**

## Perspective

Kafka reframes integration around **an append-only log of events** that many independent
consumers read and replay. That single design choice yields decoupling, resilience,
scalability, and replay — the properties that make event-driven architecture work at scale.
Everything else (partitions, consumer groups, delivery semantics) is mechanics in service of
"append facts to a durable, ordered, replayable log."
