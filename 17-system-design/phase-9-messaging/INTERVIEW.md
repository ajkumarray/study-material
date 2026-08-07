<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · performance](../phase-8-performance/NOTES.md) | [Phase 10 · observability ➡](../phase-10-observability/NOTES.md)
<!-- /nav -->

# Phase 9 — Messaging & Async: Interview Q&A

⭐ = asked constantly.

**Q: Message queue vs pub/sub?** ⭐⭐
Queue (point-to-point): each message is processed by exactly one consumer from a pool — distributes work (task processing). Pub/sub (fan-out): each message goes to every subscriber — one event triggers many independent reactions (event broadcasting). Queue = "do this task once"; pub/sub = "tell everyone this happened."

**Q: When would you introduce a message queue?** ⭐
For slow, spiky, or non-critical work you can do outside the request: emails, image processing, analytics, search indexing, third-party syncs, and fan-out to many consumers. It decouples producers/consumers, levels load (absorbs spikes), and adds resilience (work waits in the queue if a consumer is down). Don't use it when the caller needs the result immediately.

**Q: What delivery guarantees exist, and which is realistic?** ⭐⭐
At-most-once (may drop), at-least-once (may duplicate — the common default), exactly-once (impractical as pure delivery). In practice: at-least-once delivery + idempotent consumers = effectively exactly-once processing (Phase 4). Assume duplicates and dedupe on a message id.

**Q: How do you handle message ordering?**
Global ordering is expensive. Kafka orders within a partition, so pick a partition key (e.g., user id) to keep related messages ordered on one partition while different keys parallelize. If strict global order is required, you sacrifice parallelism (single partition/consumer).

**Q: What's a dead-letter queue and why use one?**
A side queue for messages that fail processing repeatedly (after N retries), so they don't block the main queue or get lost. They're inspected, fixed, and replayed. Essential for reliable async processing.

**Q: How do you make async processing reliable (no lost or double effects)?**
At-least-once delivery + idempotent consumers (dedupe on id) + acknowledgements + retries with a DLQ for poison messages. To publish events atomically with a DB change, use the outbox pattern (Phase 4).

**Q: Kafka vs RabbitMQ (or a traditional queue)?** ⭐
Kafka: a distributed, partitioned, replicated commit log — very high throughput, messages retained and replayable (consumers track offsets), great for event streaming/sourcing and fan-out. RabbitMQ/SQS: traditional broker — messages deleted once consumed, rich routing, good for task queues and lower-throughput work. Choose by throughput, retention/replay needs, and routing complexity.

**Q: What is event-driven architecture and a benefit?**
Services emit and react to events instead of calling each other directly → loose coupling; you can add a new consumer to react to an existing event without changing the producer (open/closed). Trade-offs: eventual consistency and harder end-to-end tracing.

**Q: Choreography vs orchestration for a multi-step workflow?**
Choreography: each service reacts to events, no central controller — decoupled but the overall flow is implicit/harder to follow. Orchestration: a coordinator explicitly drives each step — clearer and easier to change, but a central dependency. The saga pattern (Phase 4) uses either.

**Q: What is back-pressure in a messaging system?**
Preventing a fast producer from overwhelming consumers or memory — via bounded queues and limited consumer concurrency, so the system slows/rejects gracefully instead of running out of memory (Phase 7).
