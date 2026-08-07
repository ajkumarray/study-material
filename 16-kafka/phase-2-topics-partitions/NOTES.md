<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · event driven](../phase-1-event-driven/NOTES.md) | [Phase 3 · spring kafka ➡](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 2 — Topics, Partitions, Producers & Consumers: Notes

The mechanics that give Kafka its scale and ordering. Partitions are the unit of parallelism;
consumer groups are how you scale reading; keys are how you control ordering. See
`examples/ExpenseEventProducer.java` / `ExpenseEventConsumer.java`.

## 2.1 — Partitions, keys & ordering

- A topic is split into **partitions**; each partition is an independent, strictly **ordered**
  log. Kafka guarantees order **within a partition**, *not* across partitions.
- The **message key** decides the partition: `hash(key) % partitions`. So **all records with
  the same key land on the same partition and stay ordered.** The example keys events by
  `userId`, so every event for one user is ordered relative to each other — while different
  users spread across partitions for parallelism.
- **No key** → round-robin across partitions (max spread, no per-entity ordering).
- **Choosing a partition key** is a design decision: pick the entity whose event order matters
  (userId, accountId, orderId). More partitions = more parallelism but the ordering scope
  shrinks to per-key. Partition count is hard to reduce later — plan for growth.

## 2.2 — Producers

- A **producer** appends records to a topic. Key settings (the example's `application.yml`):
  - **`acks`** — durability vs latency: `acks=0` (fire-and-forget, may lose), `acks=1` (leader
    only), **`acks=all`** (all in-sync replicas — strongest, no loss if a broker dies). The
    example uses `all`.
  - **`enable.idempotence=true`** — dedupes producer **retries** so a network retry doesn't
    write the record twice (exactly-once *produce*). Recommended default.
  - **Batching/compression** (`linger.ms`, `batch.size`, `compression.type`) — Kafka batches
    records for very high throughput; a small linger trades a little latency for big throughput.
- Producers are async by default (`send()` returns a future); you can await/callback for
  confirmation.

## 2.3 — Consumer groups

The key scaling concept:

- A **consumer group** (`group-id`) is a set of consumers that **cooperatively** read a topic.
  Kafka assigns each **partition to exactly one consumer** in the group, so records are
  processed **once per group** and work is spread across instances.
- **Parallelism is bounded by partitions:** N partitions → at most N useful consumers in a
  group (extras sit idle). Size partitions for your target consumer parallelism.
- **Rebalancing:** when a consumer joins/leaves/dies, Kafka **reassigns** partitions across
  the remaining members (brief pause). This gives automatic failover and elastic scaling — and
  is *why* processing must be idempotent (a partition can move mid-flight, redelivering
  uncommitted records).
- **Multiple groups** read the **same** topic **independently** — analytics and notifications
  groups each get *all* the events, each tracking its own offsets. This is the fan-out that a
  queue can't do.
- **Offset commits:** a consumer commits the offset it has processed up to. **Commit *after*
  processing** (manual ack in the example) for **at-least-once**; commit before for at-most-
  once. `auto-offset-reset` (earliest/latest) decides where a brand-new group starts.

## Perspective

Three levers to internalize: **partitions** = parallelism + ordering unit; **keys** = which
records stay ordered together (and thus your parallelism granularity); **consumer groups** =
how you scale and fail over reading, with fan-out across groups. Get the **partition key**
right (order what must be ordered, spread the rest) and the **offset-commit timing** right (it
determines your delivery semantics — Phase 4), and Kafka scales cleanly.
