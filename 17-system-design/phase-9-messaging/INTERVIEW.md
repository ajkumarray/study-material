<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · performance](../phase-8-performance/NOTES.md) | [Phase 10 · observability ➡](../phase-10-observability/NOTES.md)
<!-- /nav -->

# Phase 9 — Messaging & Async: Interview Q&A

⭐ = asked constantly.

**Q: Message queue vs. pub/sub — what's the actual difference and how do you pick?** ⭐⭐
A queue is point-to-point: each message is processed by exactly one consumer drawn from
a pool of competing workers — it distributes work, so it's the right fit for task
processing where a unit of work (send this email, process this payment) should be done
exactly once. Pub/sub is fan-out: every subscriber to a topic gets its own independent
copy of each message — it's the right fit for event broadcasting, where one fact ("order
placed") needs to trigger several independent, unrelated reactions (billing, inventory,
analytics, notifications) without any of them competing for or coordinating around the
same message. The test question is simple: "should this be done once, by one worker" →
queue; "should everyone interested get their own copy" → pub/sub. Picking the wrong one
forces awkward workarounds — using a queue for the fan-out case means the producer has to
manually publish to N separate queues, one per interested consumer, and adding a new
consumer means changing the producer; using pub/sub for the one-worker case means you
have to add your own locking/deduplication to stop multiple subscribers from all
processing the same unit of work.

**Q: When would you introduce a message queue or async processing at all?** ⭐
For work that's slow (image/video processing, report generation), spiky (checkout
confirmations during a flash sale), non-critical to the immediate response (sending an
email, writing to an analytics pipeline, syncing to a third-party service), or that
needs to fan out to multiple independent consumers. It decouples producers and consumers
(each can deploy, scale, and fail independently), levels load (a bounded consumer rate
absorbs traffic spikes instead of the producer failing under them), and adds resilience
(a temporarily down consumer just falls behind, rather than losing work or breaking the
producer). You wouldn't introduce it when the caller genuinely needs the result before
proceeding (checking whether a payment was authorized before showing a confirmation), or
when the operational cost — running a broker, accepting eventual consistency, harder
end-to-end debugging across an async flow — isn't justified for something simple and
already fast.

**Q: What delivery guarantees actually exist, and which is realistic to build against?** ⭐⭐
At-most-once (fire and forget, may silently drop on failure — rarely acceptable except
for genuinely best-effort data like a metrics sample), at-least-once (retry until
acknowledged, may duplicate if an ack is lost even though the message was actually
received — the common default in real systems), and exactly-once, which as pure
*delivery* isn't achievable in an asynchronous distributed system. What's actually
achievable, and what "exactly-once" means in production systems, is at-least-once
delivery combined with idempotent consumers: duplicates will arrive under normal
operation (a consumer crashing after processing but before acking is enough to trigger
one), and the consumer recognizes and safely no-ops them by deduping on a message/event
id — the identical pattern Phase 4's idempotency demo shows for a payment delivered 100
times and applied exactly once. Building an at-least-once-tolerant consumer, rather than
chasing exactly-once delivery, is the realistic and standard engineering target.

**Q: How do you handle message ordering in a distributed system?** ⭐
Global ordering across a distributed queue or topic is expensive to enforce — it
essentially requires serializing everything through one point, which defeats the
parallelism that's the entire reason to distribute the system. Kafka's actual guarantee
is ordering *within a partition* only, never across partitions of the same topic. The
practical fix is choosing a partition key that groups messages which must stay ordered
relative to each other — hashing on `order_id` so all of one order's events (created,
paid, shipped) land on the same partition and are consumed in the order they were
produced, while different orders' events can land on different partitions and process
fully in parallel with no ordering relationship needed between them. If you genuinely
need a strict total order across everything, you sacrifice parallelism entirely — a
single partition consumed by a single consumer is the only way to guarantee it, and it
becomes the hard throughput ceiling for the whole stream.

**Q: What's a dead-letter queue and why is it necessary?**
A separate holding queue that a message is moved into after it's failed processing
repeatedly (after a configured number of retry attempts), so a single persistently
failing "poison" message doesn't block every message queued behind it, and isn't just
silently dropped either. Messages in the DLQ can be inspected to understand the failure,
the underlying bug or bad data fixed, and the message replayed once resolved. It's the
messaging-specific instance of Phase 6's broader reliability pattern of isolating and
containing a single failure rather than letting it degrade the whole pipeline.

**Q: How do you make async processing reliable — no lost effects and no double effects?**
At-least-once delivery (so nothing is silently dropped) combined with idempotent
consumers that dedupe on a message/event id (so duplicates are safely no-op'd),
explicit acknowledgements with retry-on-failure, and a dead-letter queue for messages
that fail persistently so they don't block the pipeline. To reliably publish an event as
a consequence of a database write in the first place — rather than risking the database
commit and the message publish succeeding or failing independently of each other — use
the outbox pattern (Phase 4.5): write the event to an outbox table in the same local
transaction as the state change, and have a separate relay process publish it afterward.

**Q: Kafka vs. a traditional message queue like RabbitMQ — how do you choose?** ⭐
Kafka is a distributed, partitioned, replicated commit log: very high throughput,
messages retained on disk for a configurable window (or indefinitely with compaction)
rather than deleted on consumption, so any number of independent consumer groups can
each read the full stream, and any of them can replay from the beginning — ideal for
event streaming, event sourcing, and high-volume pipelines with multiple independent
consumers. A traditional broker like RabbitMQ or SQS deletes a message once it's
consumed and instead offers rich content-based routing (exchanges: direct, topic,
fanout, headers) — a better fit for classic task-queue work distribution and lower-
throughput workloads where retention and replay aren't needed. Choose based on
throughput requirements, whether you need retention/replay, and how much routing
complexity the use case genuinely requires.

**Q: What is event-driven architecture, and what's a concrete benefit of it?**
Services communicate primarily by emitting and reacting to events rather than calling
each other directly. The concrete benefit: you can add a brand-new consumer that reacts
to an existing event stream — fraud detection reacting to "payment processed," say —
without any changes to the producer or to any other existing consumer, because the
producer never had to know who's listening in the first place. This is an
open/closed-principle-like property applied at the architecture level. The trade-offs
are eventual consistency (a consumer may react well after the event actually happened)
and harder end-to-end debugging, since a single logical business operation now spans an
asynchronous chain of independently-processed hops instead of one traceable call stack —
which is exactly why distributed tracing (Phase 10.1) becomes essential in event-driven
systems.

**Q: Choreography vs. orchestration for a multi-step async workflow — how do you
decide?**
Choreography has each service react to events independently with no central controller —
fully decoupled and each service only needs to know about the events it cares about, but
the overall end-to-end flow is implicit, scattered across many services' individual
event handlers, and harder to trace or modify as a whole. Orchestration has a central
coordinator service explicitly call each step of the workflow in sequence — the entire
flow is visible and easy to change in one place, at the cost of introducing a new
central dependency that itself needs to be reliable. A small, stable number of steps
with clear, unlikely-to-change ownership favors choreography's simplicity; a complex
workflow that business stakeholders frequently want to reorder or extend favors
orchestration's single, explicit source of truth for the flow. The saga pattern (Phase
4.5) can be implemented with either.

**Q: What is back-pressure in a messaging system, and why does it matter?**
Preventing a producer that generates messages faster than consumers can process them
from growing the queue or topic without bound — which is exactly Phase 7.4's
unbounded-queue memory-leak problem, just occurring inside a message broker instead of
an in-process collection. The fix is the same in spirit: bounded queue capacity and
limited consumer concurrency, so the system either slows the producer (blocking or
explicit rejection it must handle) or accepts a controlled, monitored backlog, rather
than growing until the broker itself runs out of memory or disk. In a partitioned
system like Kafka, the number of partitions also caps how many consumers within one
consumer group can process in parallel, which is a related throughput-scaling lever
distinct from back-pressure itself.
