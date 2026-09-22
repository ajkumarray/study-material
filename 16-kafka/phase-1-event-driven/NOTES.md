<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · topics partitions ➡](../phase-2-topics-partitions/NOTES.md)
<!-- /nav -->

# Phase 1 — Kafka & Event-Driven Architecture: Notes

Apache Kafka is a **distributed, durable, append-only commit log** for streaming events.
Instead of services calling each other directly and waiting for a response, producers
**append events** to Kafka and consumers **read them independently**, at their own pace —
decoupling the system in both time and space. This phase covers *why* that model exists and
*what* Kafka's core building blocks (topics, partitions, offsets, brokers) are, before Phase 2
digs into the producer/consumer mechanics.

## 1.1 — Event-driven vs request/response architecture

**Request/response** is the model most developers learn first: service A calls service B (a
REST call, an RPC) and blocks until B replies. **Event-driven** flips this: service A
**publishes a fact that already happened** ("expense.created") to a broker, and any number of
other services react to it later, independently, without A knowing or caring who they are.

### Key Concepts

- **Synchronous coupling (request/response)**: A must know B's address, B must be up and
  responsive *right now*, and A's request latency includes B's processing time. If B is slow
  or down, A fails or hangs.
- **Temporal decoupling (event-driven)**: A publishes and moves on immediately. Consumers can
  be down, slow, or not even exist yet — the event waits for them in the log.
- **Spatial decoupling (event-driven)**: A doesn't address any specific consumer. It publishes
  to a named topic; consumers subscribe to that topic without A's code ever referencing them.
- **Events are facts, stated in the past tense** — `ExpenseCreated`, not `CreateExpense`. A
  command ("do this") implies someone must act; an event ("this happened") is just information
  that any number of parties may or may not care about. This distinction matters: events don't
  fail if nobody is listening, but a command with no handler is a bug.

### Worked example — the same feature, two architectures

```java
// --- Request/response: the API directly calls every interested service ---
@Service
public class ExpenseService {
    private final AnalyticsClient analyticsClient;     // REST client
    private final NotificationClient notificationClient; // REST client

    public void createExpense(ExpenseRequest req) {
        Expense saved = expenseRepository.save(toEntity(req));
        analyticsClient.recordExpense(saved);        // blocks; must be up
        notificationClient.sendAlert(saved);          // blocks; must be up
        // Adding a 3rd consumer (fraud detection) means editing THIS method
        // and adding a 3rd blocking call, coupling createExpense() to it too.
    }
}
```

```java
// --- Event-driven: the API publishes one event and returns ---
@Service
public class ExpenseService {
    private final ExpenseEventProducer events;

    public void createExpense(ExpenseRequest req) {
        Expense saved = expenseRepository.save(toEntity(req));
        events.publish(new ExpenseEvent(
            UUID.randomUUID().toString(), "created", saved.getUserId(),
            saved.getId(), saved.getAmountCents(), System.currentTimeMillis()));
        // Returns immediately. Analytics, notifications, fraud-detection can each
        // add a @KafkaListener consumer WITHOUT touching this method at all.
    }
}
```

In the first version, `ExpenseService` is coupled to every downstream service's availability
and latency, and every new consumer means a code change here. In the second version,
`createExpense` only knows about Kafka; analytics and notifications (Phase 3's
`ExpenseEventConsumer`) subscribe independently, and a brand-new consumer can be added with
zero changes to the producer.

### Comparison table — request/response vs event-driven

| | Request/response | Event-driven |
|---|---|---|
| Coupling | Caller knows callee's address/API | Producer knows only the topic name |
| Availability | Callee must be up *now* | Consumer can be down; catches up later |
| Latency | Caller waits for callee's full processing | Producer returns immediately |
| Adding a new consumer | Edit the caller's code | Add a new independent consumer |
| Consistency | Often strongly consistent (one transaction) | Eventually consistent (async propagation) |
| Failure mode | Caller fails/retries synchronously | Event persists; consumer retries independently |
| Debugging | One call stack to trace | Async hops — needs correlation IDs/tracing |
| Best for | A needs B's answer to proceed | A doesn't need to wait; many may react |

### Why it's useful

- **Decoupling** — add a new consumer (fraud detection, search indexing, a data warehouse
  loader) without ever touching the producer's code or redeploying it.
- **Resilience** — a downstream consumer being down doesn't fail the producer's request; the
  event sits durably in the log and the consumer catches up once it's back.
- **Scalability & buffering** — Kafka absorbs traffic bursts; each consumer processes at its
  own sustainable rate instead of forcing the producer to slow down to match the slowest
  consumer.
- **Replayability** — because the log is retained (1.3), you can replay history to rebuild a
  read model, backfill a brand-new consumer, or reprocess after fixing a bug.

The trade-off is real: event-driven systems are eventually consistent and harder to trace
end-to-end than a single synchronous call stack. Choose events when decoupling, scale, and
replay matter more than an immediate, guaranteed answer.

## 1.2 — The core model: the log

Kafka's single big idea is **the log** — an ordered, append-only, immutable sequence of
records. Almost every other Kafka concept is in service of making that log fast, durable, and
scalable.

### Key Concepts

- **Topic** — a named, logical stream of events (e.g. `expense-events`). Producers append
  records to a topic; consumers read from it. A topic is conceptually similar to a table name
  or a queue name, but backed by an append-only log rather than mutable storage.
- **Partition** — a topic is split into one or more partitions, and **each partition is its
  own independent, strictly ordered log**. This is *the* mechanism that lets Kafka scale:
  partitions are the unit of parallelism (Phase 2) and can live on different brokers.
- **Record (message)** — the unit stored in a partition: a **key**, a **value**, optional
  **headers** (metadata, like HTTP headers), a **timestamp**, and its **offset**. In the
  track's `ExpenseEvent`, the value is the JSON-serialized event and the key is `userId`.
- **Offset** — a monotonically increasing integer identifying a record's position *within its
  partition* (offsets are not unique across partitions — partition 0's offset 5 and partition
  1's offset 5 are unrelated records). Consumers track "where am I" by committing offsets, so
  they can stop and resume — or deliberately rewind to replay.
- **Broker** — a single Kafka server process; it stores some subset of the cluster's
  partitions and serves produce/fetch requests for them.
- **Cluster** — a set of brokers working together. A topic's partitions are spread across the
  cluster's brokers, and each partition is **replicated** to multiple brokers for fault
  tolerance (1.3).
- **Append-only, immutable** — once written, a record at a given offset is never modified
  (only deleted by retention, 1.4). This is what makes the log simple, fast to write
  (sequential disk I/O), and safe to read concurrently from many consumers.

### Worked example — a topic as partitioned logs

```
Topic: expense-events (3 partitions)

partition 0:  [offset 0][offset 1][offset 2][offset 3] ...  → appended to, never rewritten
partition 1:  [offset 0][offset 1][offset 2] ...
partition 2:  [offset 0][offset 1][offset 2][offset 3][offset 4] ...
```

```bash
# Creating a topic (illustrative — the CLI ships with a Kafka install)
kafka-topics.sh --create --topic expense-events \
  --bootstrap-server localhost:9092 \
  --partitions 3 --replication-factor 3

# Inspecting it
kafka-topics.sh --describe --topic expense-events --bootstrap-server localhost:9092
# Topic: expense-events   PartitionCount: 3   ReplicationFactor: 3
#   Partition: 0  Leader: 1  Replicas: 1,2,3  Isr: 1,2,3
#   Partition: 1  Leader: 2  Replicas: 2,3,1  Isr: 2,3,1
#   Partition: 2  Leader: 3  Replicas: 3,1,2  Isr: 3,1,2
```

Each row of the `--describe` output is one partition: `Leader` is the broker currently
serving reads/writes for it, `Replicas` is every broker holding a copy, and `Isr` (in-sync
replicas, 1.3) is the subset of those replicas fully caught up with the leader. A producer
appends to whichever partition a given record is routed to (Phase 2.1 covers the routing
rule); a consumer reading partition 0 sees its records strictly in offset order — 0, 1, 2,
3 — but has no guarantee about how that interleaves with partition 1's or 2's records.

### Why it's useful

Splitting a topic into partitions is what lets Kafka scale horizontally (more partitions,
more brokers, more parallel consumers) while still giving a strong, useful guarantee: order is
preserved *within* each partition. Designing a Kafka-based system is largely about deciding
what belongs in the same partition (so it stays ordered) and what can spread across partitions
(so it scales) — the subject of Phase 2.

## 1.3 — Brokers, clusters, and replication

A single broker is a single point of failure; Kafka's answer is to **replicate every
partition** across several brokers.

### Key Concepts

- **Replication factor** — how many copies of each partition exist across the cluster (e.g.
  `--replication-factor 3` above means 3 copies of every partition). Losing `replication-factor - 1`
  brokers still leaves at least one copy.
- **Leader / follower** — for each partition, one replica is the **leader**: all produces and
  (by default) all consumes for that partition go through it. The other replicas are
  **followers**, which continuously fetch from the leader to stay in sync.
- **ISR (in-sync replicas)** — the subset of a partition's replicas that are fully caught up
  with the leader within an allowed lag. `acks=all` (Phase 2.2) means "wait until every replica
  currently in the ISR has the record" — not necessarily every replica that theoretically
  exists, which matters if a follower has fallen behind or is down.
- **Controller** — one broker in the cluster acts as controller, responsible for detecting
  broker failures and electing new partition leaders from the ISR when the current leader dies.
- **Coordination — ZooKeeper vs KRaft**: older Kafka versions used an external **ZooKeeper**
  ensemble to store cluster metadata and elect the controller. Modern Kafka (post-KIP-500,
  Kafka 3.x+ as the default) uses **KRaft** — a built-in Raft-based consensus protocol among a
  subset of the brokers themselves — removing the separate ZooKeeper dependency and
  simplifying operations.

### Worked example — what failover looks like

```
Before broker 1 fails:
  Partition 0 — Leader: broker 1   Replicas: [1,2,3]   ISR: [1,2,3]

Broker 1 crashes. The controller detects the failure (via KRaft/ZooKeeper heartbeats)
and picks a new leader from the ISR (say broker 2):

After failover:
  Partition 0 — Leader: broker 2   Replicas: [1,2,3]   ISR: [2,3]
  # broker 1 drops out of the ISR until it comes back and catches up
```

Producers and consumers that were talking to broker 1 for partition 0 get a
`NotLeaderForPartitionException`-style error on their next request, refresh their metadata
(which brokers own which partitions), discover broker 2 is now the leader, and transparently
retry against it. This failover is automatic and typically completes in seconds — the
application code never has to know which broker is currently the leader; the Kafka client
library handles metadata refresh and retry internally.

### Why it's useful

Replication plus automatic leader election is what makes Kafka **durable and highly
available**: a broker (or a whole rack) can fail without losing committed data or requiring a
human to intervene, as long as enough replicas are in the ISR. This is the same leader/follower
replication idea used by Postgres, Redis, and most distributed databases (Databases track,
System Design track) — Kafka is one concrete, very widely deployed instance of the pattern.

## 1.4 — Retention and replayability

Unlike a queue, **reading a record from Kafka does not delete it.** Records stay in the log
according to a retention policy, independent of whether any consumer has read them — this is
Kafka's most distinctive property, and the one that unlocks replay.

### Key Concepts

- **Time-based retention** (`retention.ms`, default 7 days) — records older than this are
  eligible for deletion, regardless of consumption.
- **Size-based retention** (`retention.bytes`) — caps a partition's size on disk; the oldest
  segments are deleted once the cap is exceeded.
- **Log compaction** (`cleanup.policy=compact`) — an alternative to time/size deletion: Kafka
  keeps only the **latest record per key**, deleting older records with the same key
  (tombstones — a record with a `null` value — mark a key for eventual full removal). Used for
  topics that represent "current state per key" (e.g. a changelog of the latest known address
  per `userId`) rather than a pure event history.
- **Multiple independent readers** — because reading doesn't consume/delete, any number of
  consumer groups can read the same topic from the beginning, each tracking its own offset
  (Phase 2.3), completely independently of each other.
- **Rewinding** — a consumer can deliberately seek to an earlier offset (or use
  `auto-offset-reset: earliest` on a fresh group) and reprocess history it has already seen.

### Worked example — replay from the beginning

```bash
# A brand-new consumer group (or one explicitly seeking to the start) can read
# the ENTIRE retained history of a topic, even though it wasn't running when
# those events were originally produced:
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic expense-events --from-beginning

# {"eventId":"...","type":"created","userId":"u1","expenseId":101,"amountCents":2599,...}
# {"eventId":"...","type":"updated","userId":"u1","expenseId":101,"amountCents":3199,...}
# {"eventId":"...","type":"created","userId":"u2","expenseId":102,"amountCents":899,...}
# ... every retained record, oldest first, per partition
```

This is exactly how you'd bootstrap a brand-new analytics consumer group months after the
`expense-events` topic went live: point it at `auto-offset-reset: earliest`, and it rebuilds
its entire read model by replaying every historical event — no special "backfill" API needed,
because the events were never deleted on read.

### Why it's useful

- **Rebuilding a read model** — if a bug corrupts an analytics table, drop it and replay the
  topic from the beginning to regenerate it correctly.
- **Onboarding a new service** — a brand-new consumer (search indexing, a fraud model) can
  read the full history on day one, not just events from the moment it started.
- **Reprocessing after a bug fix** — fix a consumer's processing logic, reset its committed
  offset, and reprocess the affected window of events with the corrected code.
- **Event sourcing** (Phase 4.3) is a direct consequence of retention: if the log is retained
  long enough (or forever), it *is* the durable source of truth, and current state is just "the
  log replayed to now."

## 1.5 — Kafka vs message queue vs Redis Pub/Sub

| | **Kafka** | **RabbitMQ (queue)** | **Redis Pub/Sub** |
|---|---|---|---|
| Model | durable, partitioned **log** | broker routes to **queues** | in-memory **fan-out** |
| Retention | retained (replayable, configurable) | deleted once acked | none — transient |
| Offline consumer | catches up later (reads from its offset) | message waits queued for it | **misses** the message entirely |
| Throughput | very high (sequential disk I/O, batched) | high | high, but volatile |
| Ordering | guaranteed per-partition | guaranteed per-queue | none guaranteed |
| Multiple independent readers of the same data | yes — each consumer group gets everything | no — one queue, message consumed once (unless fanned out via exchanges) | yes, but only while subscribed |
| Routing | consumer-side filtering; topics are simple | rich (exchanges: direct/topic/fanout/headers) | simple channel-based fan-out |
| Best for | event streaming, event sourcing, high-throughput pipelines, replay | task queues, work distribution, RPC-style messaging, complex routing | ephemeral notifications, cache invalidation, real-time signals where a miss is fine |

### Why the distinction matters

- **vs RabbitMQ**: a traditional message queue's core job is *routing and work distribution* —
  a message is typically consumed once and then gone, and RabbitMQ has rich routing (exchanges,
  bindings) tailored to that. Kafka's core job is a *retained, replayable log* — the same data
  can be read by many independent consumer groups, replayed, and kept for days or forever.
  Use RabbitMQ when you want classic task-queue semantics (a job is picked up by exactly one
  worker) or complex routing; use Kafka when you want high-throughput event streaming, replay,
  or many independent consumers of the same stream.
- **vs Redis Pub/Sub** (track 15): Redis Pub/Sub is fire-and-forget — there is no persistence
  at all, so a subscriber that isn't connected at publish time simply never sees that message.
  It's extremely low-latency and simple, appropriate for ephemeral signals (e.g. "invalidate
  this cache key," "a user went online") where losing a message occasionally is acceptable.
  Kafka persists every record and lets consumers catch up or replay — the right choice when
  losing an event is not acceptable (money moved, an order placed).

**Rule of thumb**: Redis Pub/Sub = transient signal; RabbitMQ = task queue / routing; Kafka =
durable, replayable event backbone.

## Perspective

Kafka reframes integration around **an append-only log of events** that many independent
consumers can read, at their own pace, with the option to replay. That single design choice —
retain instead of delete on read — is what yields decoupling, resilience, scalability, and
replay all at once. Everything covered in later phases (partitions and keys, consumer groups,
delivery semantics, the outbox pattern) is mechanics in service of one sentence: **append facts
to a durable, ordered, replayable log, and let consumers read it independently.**

## Summary / Key Takeaways

- **Event-driven** trades a caller's synchronous wait for temporal + spatial decoupling: the
  producer publishes a fact and moves on; any number of consumers react independently, later.
- A **topic** is a named log split into **partitions**, each an independent, strictly ordered
  sequence of records identified by monotonically increasing **offsets** (unique per
  partition, not globally).
- **Brokers** form a **cluster**; each partition is **replicated** with one **leader** serving
  traffic and followers staying in sync via the **ISR** — this is what makes Kafka durable and
  available through broker failures. Modern Kafka coordinates this via built-in **KRaft**
  (older clusters used **ZooKeeper**).
- **Retention** (time/size-based, or **log compaction** for latest-value-per-key topics) means
  reading never deletes — many consumer groups can read the same data independently, and any
  of them can **replay** from the beginning.
- Reach for **Kafka** when you need durable, replayable, high-throughput event streaming with
  many independent readers; reach for a **queue** (RabbitMQ) for task distribution/complex
  routing; reach for **Redis Pub/Sub** for cheap, ephemeral fan-out where a missed message is
  acceptable.
