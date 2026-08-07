<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · performance](../phase-8-performance/NOTES.md) | [Phase 10 · observability ➡](../phase-10-observability/NOTES.md)
<!-- /nav -->

# Phase 9 — Messaging & Async Architecture: Notes

Synchronous request/response couples caller and callee in time — the caller waits, and if the callee is slow or down, the caller suffers. **Asynchronous messaging** decouples them: the producer hands work to a broker and moves on; consumers process it independently. This buys resilience, scalability, and the ability to smooth spikes.

## 9.1 — Queues vs pub/sub; when to go async
- **Message queue (point-to-point):** a producer enqueues; **one** consumer (from a pool) processes each message. Work is distributed and load-leveled. Tools: RabbitMQ, SQS, ActiveMQ. Use for task/job processing (send email, resize image, process payment).
- **Publish/subscribe (fan-out):** a producer publishes to a topic; **every** subscriber gets a copy. Decouples one event from many independent reactions. Tools: Kafka, SNS, Redis pub/sub, Google Pub/Sub. Use for event broadcasting (an "order placed" event → billing, inventory, analytics, notifications each react).
- **When to go async:** work that's slow, spiky, or non-critical to the response — email/SMS, thumbnails, analytics, search indexing, syncing to third parties, fan-out to many consumers. Return to the user fast; do the work reliably in the background (Phase 8's "offload"). **When NOT to:** work whose result the caller needs immediately (keep it sync), or where the added complexity (a broker to run, eventual consistency, ordering issues) isn't worth it.

**Benefits:** load leveling (a bounded consumer rate absorbs producer spikes — back-pressure, Phase 7), decoupling (producers/consumers deploy and scale independently), resilience (if a consumer is down, messages wait in the queue instead of being lost), and buffering. **Costs:** operational complexity, eventual consistency, harder debugging (async flows), and message ordering/duplication concerns.

## 9.2 — Delivery guarantees, ordering, back-pressure
- **Delivery semantics** (Phase 4): **at-most-once** (fire and forget — may drop), **at-least-once** (retry until acked — may duplicate; the common default), **exactly-once** (at-least-once + idempotent consumers, since true exactly-once delivery is impractical). **Design consumers to be idempotent** — dedupe on a message id — because at-least-once means duplicates *will* happen.
- **Ordering:** global ordering across a distributed queue is expensive. Kafka guarantees order **within a partition**; choose a partition key (e.g., user id) so related messages stay ordered on one partition, while different keys parallelize across partitions.
- **Acknowledgement & redelivery:** a consumer acks after successfully processing; unacked messages are redelivered (hence duplicates). Repeatedly-failing messages go to a **dead-letter queue** (Phase 6) for inspection/replay instead of blocking the queue.
- **Back-pressure:** bounded queues and consumer concurrency limits keep a fast producer from overwhelming consumers or memory (Phase 7) — the system slows gracefully instead of falling over.

**Kafka in one breath** (depth: track 15): a distributed, partitioned, replicated **commit log**. Producers append to **topics** split into **partitions** (the unit of parallelism and ordering); **consumer groups** divide partitions among consumers for scale; messages are **retained** on disk (replayable — consumers track their own offset), enabling event sourcing and stream processing. It's pub/sub + a durable log, built for very high throughput. Contrast a traditional queue (RabbitMQ) that deletes a message once consumed.

## Async architecture patterns
- **Event-driven architecture:** services communicate by emitting/reacting to events rather than direct calls — loose coupling, easy to add new reactions (an OCP-like property, Software Design Phase 2). Choreography (services react to events) vs orchestration (a coordinator directs the flow) — the saga trade-off (Phase 4).
- **CQRS** (Command Query Responsibility Segregation): separate the write model from read models (often built from events) for independent scaling — advanced, pairs with event sourcing.
- **The outbox pattern** (Phase 4): publish events reliably by writing them to the DB in the same transaction as the state change, then relaying them — avoids the dual-write problem.
