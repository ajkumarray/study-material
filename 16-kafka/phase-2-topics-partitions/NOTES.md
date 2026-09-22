<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · event driven](../phase-1-event-driven/NOTES.md) | [Phase 3 · spring kafka ➡](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 2 — Topics, Partitions, Producers & Consumers: Notes

Phase 1 established *why* Kafka's log-based model exists. This phase covers the mechanics that
give it scale and ordering: how records are routed to partitions, what a producer actually
configures to control durability and throughput, and how consumer groups let many processes
cooperatively read a topic without duplicating work. The track's `examples/ExpenseEventProducer.java`,
`ExpenseEventConsumer.java`, and `application.yml` are the concrete artifacts referenced
throughout.

## 2.1 — Partitions, keys & ordering

A topic's partitions are independent ordered logs (Phase 1.2); how a given record ends up in a
particular partition is a deliberate, consequential choice.

### Key Concepts

- **Ordering guarantee is per-partition, not per-topic.** Kafka guarantees that records within
  one partition are delivered to a consumer in the exact order they were written. It makes
  **no** ordering guarantee across different partitions of the same topic.
- **The message key decides the partition** (with the default partitioner): Kafka computes
  `hash(key) % numPartitions` and routes the record there. Every record with the same key
  therefore always lands on the same partition and is ordered relative to every other record
  with that key.
- **No key (`null`)** — records are spread across partitions (modern Kafka clients use a
  *sticky* strategy: batch several records onto one partition before switching, for better
  batching efficiency, rather than strict round-robin per record). You get maximum spread and
  no per-entity ordering guarantee at all.
- **Choosing a partition key is a design decision**, not an implementation detail: pick the
  entity whose events must stay ordered relative to each other (a user id, an account id, an
  order id). Everything sharing that key is confined to one partition — good for ordering, but
  it also caps that key's throughput to what one partition/consumer can handle.
- **Partition count is expensive to change later.** Increasing partitions on an existing topic
  is possible but **changes the key→partition mapping for existing keys** (since `% numPartitions`
  changes), which can break ordering assumptions consumers relied on. Decreasing partition
  count isn't supported at all (you'd recreate the topic). Plan partition count for your target
  scale up front.

### Worked example — routing by key

```java
// examples/ExpenseEventProducer.java
public void publish(ExpenseEvent event) {
    // The KEY (userId) decides the PARTITION: all events for one user go to the
    // same partition, which preserves their relative ORDER on the consumer side.
    kafka.send(TOPIC, event.userId(), event);   // (topic, key, value)
}
```

```
publish(event for userId="u1", type="created")   → hash("u1") % 3 = 0 → partition 0
publish(event for userId="u2", type="created")   → hash("u2") % 3 = 1 → partition 1
publish(event for userId="u1", type="updated")   → hash("u1") % 3 = 0 → partition 0 (again)
publish(event for userId="u1", type="deleted")   → hash("u1") % 3 = 0 → partition 0 (again)

partition 0: [u1 created][u1 updated][u1 deleted]   ← u1's events, strictly ordered
partition 1: [u2 created]                            ← u2 spreads onto a different partition
```

In this example, keying by `userId` gives two things at once: every event about user `u1` is
guaranteed to arrive at any consumer in the order `created → updated → deleted` (critical — a
consumer that saw `deleted` before `created` would have a bug), while `u1` and `u2`'s events
can be processed fully in parallel because they're on different partitions.

### Why it's useful

Getting the key right is the single most consequential Kafka design decision in a system: it's
the knob that trades "how much do I parallelize" against "what must stay ordered." A common
mistake is keying by something too coarse (e.g. a single constant, collapsing every record onto
one partition — no parallelism) or too fine (keying by something unrelated to the events that
actually need relative ordering, silently breaking correctness).

## 2.2 — Producers

A **producer** is any client that appends records to a topic. Its configuration controls the
trade-off between durability, latency, and throughput.

### Key Concepts

- **`acks`** — how many replicas must confirm a write before the producer considers it
  successful:
  - `acks=0` — fire-and-forget; the producer doesn't wait for any confirmation. Fastest,
    but a record can be silently lost if the leader fails before it's even written.
  - `acks=1` — wait for the partition **leader** to write the record locally. Faster than
    `all`, but if the leader crashes before followers replicate it, the record is lost.
  - `acks=all` (a.k.a. `-1`) — wait for **every replica currently in the ISR** (Phase 1.3) to
    acknowledge. Strongest durability: as long as at least one ISR member survives, the record
    isn't lost. The track's `application.yml` uses `acks: all`.
- **`enable.idempotence=true`** — makes retries safe. Without it, if a producer retries a send
  after a timeout (not knowing whether the broker actually got the first attempt), it can write
  the same record **twice**. With idempotence on, the broker deduplicates retries of the same
  producer using a producer ID + sequence number, so a network retry never results in a
  duplicate write. This is "exactly-once **produce**," not end-to-end exactly-once (Phase 4.2).
  Recommended default in modern Kafka (and required for transactions).
- **`retries`** — how many times the client automatically retries a send that failed for a
  retriable reason (e.g. a leader election in progress). The example sets `retries: 3`.
- **Batching & compression** (`batch.size`, `linger.ms`, `compression.type`) — Kafka producers
  buffer records per partition and send them as a batch rather than one record per network
  round trip. `linger.ms` adds a small deliberate delay (e.g. 5-10ms) to let more records
  accumulate into a batch before sending — trading a little latency for much higher throughput.
  `compression.type` (e.g. `lz4`, `snappy`, `zstd`) compresses whole batches, shrinking network
  and disk usage substantially for typical JSON/text payloads.
- **Send is asynchronous by default** — `send()` returns immediately with a
  `CompletableFuture`/callback; you can block on it (`.get()`) for a synchronous send, or attach
  a callback to react to success/failure without blocking. Blocking on every send serializes
  your producer and defeats the point of batching.

### Worked example — the durability/latency trade-off

```java
// acks=0: producer doesn't wait at all
producer.send(record);
// fastest, but: if the leader broker crashes microseconds later, this record
// may never have existed as far as any consumer is concerned.

// acks=1: producer waits for the partition LEADER only
producer.send(record).get();
// leader wrote it to its own log, but if the leader dies before followers
// replicate, and a follower becomes the new leader, the record is lost.

// acks=all + enable.idempotence=true (this track's configuration):
producer.send(record).get();
// every in-sync replica has the record before send() completes, AND a retried
// send (e.g. after a timeout) is deduplicated by the broker — no loss, no dupes.
```

### Why it's useful

`acks=all` plus `enable.idempotence=true` is the standard production baseline for anything
where losing or duplicating a produced record is unacceptable (financial events, order events)
— it costs some latency versus `acks=0`/`acks=1`, but that latency is almost always worth
paying compared to the cost of silently lost or duplicated data. Batching and compression are
what let Kafka sustain very high throughput despite that per-record safety — a small,
deliberate `linger.ms` delay lets many records amortize one network round trip.

## 2.3 — Consumer groups

The key scaling concept on the read side: how multiple consumer processes cooperatively read
one topic without duplicating work, and how independent teams can each get their own full copy
of the stream.

### Key Concepts

- **A consumer group** (`group-id`, e.g. the example's `expense-analytics`) is a named set of
  consumers that cooperatively consume a topic. Kafka guarantees each **partition is assigned
  to exactly one consumer within the group** at a time, so every record is processed **once per
  group**, and the group's total work is spread across however many consumer instances are
  running.
- **Parallelism is capped by partition count.** With N partitions, at most N consumer instances
  in one group are doing useful work simultaneously — a (N+1)th instance in the same group sits
  completely idle, because there's no partition left to assign it. This is *the* reason
  partition count is chosen as a capacity-planning decision (2.1).
- **Rebalancing** — when a consumer joins, leaves, or is considered dead (missed heartbeats),
  the group coordinator **reassigns partitions** among the remaining members. This is what
  gives automatic failover (a dead instance's partitions move to a live one) and elastic scaling
  (add instances, partitions redistribute). During a rebalance, affected partitions briefly
  pause processing.
- **Rebalancing is why consumers must be idempotent**: a partition can be reassigned to a
  different consumer *before* the previous owner's offset commit for records it already
  processed makes it through — the new owner then re-reads and reprocesses those same records.
  This is not a bug; it's the direct consequence of at-least-once delivery (Phase 4).
- **Multiple, independent consumer groups** can each read the **same topic in full**,
  completely independently, each tracking its own offsets. The capstone's `expense-analytics`
  group and a hypothetical `notifications` group both consume every `expense-events` record,
  each oblivious to the other's existence or progress. This fan-out — many independent full
  copies of the stream — is something a traditional work queue cannot do for free.
- **Offset commits** — a consumer periodically (or explicitly) tells Kafka "I've processed up to
  this offset" by committing it. **When** you commit relative to processing determines your
  delivery semantics (Phase 4.1): commit *after* successful processing (the example's manual
  ack) for at-least-once; commit *before* processing for at-most-once.
- **`auto-offset-reset`** (`earliest` | `latest`) only matters when a group has **no previously
  committed offset** for a partition (a brand-new group, or one whose committed offsets expired):
  `earliest` starts from the very beginning of the retained log (full replay); `latest` starts
  from whatever is newly produced after the consumer joins, skipping all history.

### Worked example — one topic, two independent groups

```
Topic: expense-events (3 partitions, records for u1, u2, u3, ...)

Consumer group "expense-analytics" (2 instances running):
  instance A → assigned partitions [0, 1]
  instance B → assigned partitions [2]
  → together they process EVERY record, exactly once per record, split across instances.

Consumer group "notifications" (1 instance running):
  instance C → assigned partitions [0, 1, 2]
  → processes EVERY record too — completely independently of "expense-analytics";
    it has its own committed offsets and doesn't know the other group exists.
```

```bash
# Inspecting a group's assignment and lag
kafka-consumer-groups.sh --bootstrap-server localhost:9092 \
  --describe --group expense-analytics
# GROUP              TOPIC            PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# expense-analytics  expense-events   0          8420            8420            0
# expense-analytics  expense-events   1          8391            8402            11
# expense-analytics  expense-events   2          8355            8355            0
```

In this output, `CURRENT-OFFSET` is what the group has committed as processed, `LOG-END-OFFSET`
is the latest offset actually written to that partition, and `LAG` is the difference — partition
1's group member is 11 records behind. Consumer lag (Phase 4.2) is the primary Kafka health
metric: growing lag means a group can't keep up with production rate.

### Why it's useful

Consumer groups are how Kafka gives you both **horizontal scaling** (add instances to a group,
up to the partition count, and they split the work) and **fan-out to independent subsystems**
(add a whole new group, and it gets its own full copy of the stream) — with zero coordination
code written by the application. You get exactly the two behaviors a request/response system
would need custom infrastructure to build: load-balanced work distribution, and pub/sub fan-out,
from the same mechanism.

## Perspective

Three levers to internalize for Phase 2: **partitions** are the unit of both parallelism and
ordering; **keys** decide which records are confined to one partition (and therefore stay
ordered, at the cost of that key's throughput being bounded by one partition); and **consumer
groups** are how you scale reads (more instances, up to partition count) and fan out
independently (more groups, each getting everything). Get the **partition key** right (order
what genuinely needs ordering; spread the rest) and understand **offset-commit timing**
(it directly determines your delivery semantics, Phase 4), and the rest of Kafka's read-side
behavior follows from these two decisions.

## Summary / Key Takeaways

- Kafka guarantees order **within a partition only** — never across partitions of the same
  topic.
- The **message key** determines the partition (`hash(key) % partitions`): same key → same
  partition → preserved relative order. No key → spread for max parallelism, no ordering
  guarantee.
- **`acks=all` + `enable.idempotence=true`** is the standard durable, duplicate-free producer
  baseline; `acks=0`/`acks=1` trade durability for lower latency.
- A **consumer group** assigns each partition to exactly one member — parallelism is capped at
  the partition count, and **rebalancing** (on join/leave/failure) reassigns partitions, which
  is why consumers must be idempotent.
- **Multiple consumer groups** independently read the *same* topic in full — this fan-out is
  Kafka's answer to "many different subsystems all need every event."
- **When** you commit an offset relative to processing (before vs after) is what sets your
  delivery semantics — the subject of Phase 4.
