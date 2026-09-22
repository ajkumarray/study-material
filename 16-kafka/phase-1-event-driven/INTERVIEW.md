<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · topics partitions ➡](../phase-2-topics-partitions/NOTES.md)
<!-- /nav -->

# Phase 1 — Kafka & Event-Driven Architecture: Interview Q&A

⭐ = asked constantly.

**Q: What is Kafka, in one explanation you'd give a non-Kafka engineer?** ⭐⭐
Kafka is a distributed, durable, append-only commit log used to stream events between
services. Producers append records to named **topics**; each topic is split into
**partitions**, which are independent, strictly ordered logs spread across the brokers in a
cluster. Consumers read those partitions **independently and at their own pace**, tracking
their position with an **offset** they commit. Crucially, reading does not delete — records
stay retained for a configured window (or forever, with compaction), so any number of
consumers can read the same data, and any of them can rewind and replay it. That combination —
durable, ordered-per-partition, retained, replayable — is what distinguishes Kafka from a
traditional message queue.

*Follow-up: what problem does that actually solve, architecturally?* It decouples the service
that produces data from every service that reacts to it — new consumers can be added without
touching the producer, and a consumer being temporarily down doesn't lose data, it just falls
behind and catches back up.

**Q: Event-driven vs request/response — why would you choose one over the other?** ⭐⭐
In request/response, the caller is synchronously coupled to the callee: it must know the
callee's address, the callee must be up right now, and the caller's latency includes however
long the callee takes to process the request. In event-driven architecture, a service publishes
a fact ("expense.created") to a log and returns immediately; any number of consumers react to
it later, independently, without the publisher knowing who they are or waiting for them.

The upside is decoupling (add consumers freely), resilience (a down consumer catches up rather
than breaking the producer), better handling of bursty load (the log buffers), and replay. The
downside is eventual consistency — a consumer might process an event seconds or minutes after
it happened — and harder debugging, since a single business operation now spans an async chain
of hops instead of one call stack, usually requiring correlation IDs and distributed tracing to
follow. Choose request/response when the caller genuinely needs an answer to proceed (e.g. "is
this payment authorized?"); choose events when the caller doesn't need to wait and multiple
things may want to react.

**Q: What are topics, partitions, and offsets, and how do they relate?** ⭐⭐
A **topic** is a named, logical stream (e.g. `expense-events`). It's split into one or more
**partitions**, and each partition is its own independent, strictly ordered append-only log.
Every record within a partition gets a monotonically increasing **offset** marking its position
— offsets are only meaningful within a single partition; partition 0's offset 5 and partition
1's offset 5 are unrelated records. Partitions are the unit of both parallelism (they can live
on different brokers and be consumed by different consumers in parallel) and ordering (Kafka
only guarantees order *within* a partition, never across partitions of the same topic).

**Q: How is Kafka different from a traditional message queue like RabbitMQ?** ⭐⭐
A queue's model is centered on routing and work distribution: a message typically gets consumed
by exactly one worker and is then deleted, and brokers like RabbitMQ offer rich routing
(exchanges: direct, topic, fanout, headers) for that purpose. Kafka's model is a retained,
partitioned log: reading a record does not delete it, so many independent consumer groups can
each read the same topic in full, and any of them can rewind and replay history. Use a queue
when you want classic task-queue semantics or complex routing; use Kafka when you want
high-throughput event streaming, replay, or multiple independent consumers of the same event
stream.

*Follow-up: could you build a task queue on top of Kafka?* You can approximate it — a single
consumer group gives you "each record processed by exactly one member" — but you lose per-
message ack/nack semantics, priority queues, and Kafka's ordering guarantee only holds within a
partition, so it's usually the wrong tool for fine-grained task distribution; a purpose-built
queue is a better fit there.

**Q: Kafka vs Redis Pub/Sub — when would you pick each?** ⭐⭐
Redis Pub/Sub is in-memory and fire-and-forget: there's no persistence at all, so a subscriber
that isn't actively connected when a message is published simply never receives it. It's very
low latency and simple — good for ephemeral signals like "invalidate this cache key" or
presence notifications, where an occasional missed message is acceptable. Kafka persists every
record to disk, replicates it, and lets consumers catch up or replay from any point — the right
choice whenever losing an event is not acceptable, such as anything tied to money, orders, or
an audit trail. Rule of thumb: Redis Pub/Sub for transient signals, Kafka for a durable,
replayable event backbone.

**Q: What does retention and replayability actually enable in practice?** ⭐
Because Kafka doesn't delete a record on read, you can point a brand-new consumer group at a
topic with `auto-offset-reset: earliest` and it will read the entire retained history from the
beginning — as if it had been running the whole time. That lets you: rebuild a corrupted read
model by dropping it and replaying the topic; onboard a brand-new service (search indexing,
fraud detection) that needs full historical context on day one; and reprocess a window of
events after fixing a bug in the consumer's logic. It's also the foundation of event sourcing
(Phase 4), where the log itself — not a separate table — is treated as the source of truth.

**Q: What's log compaction, and when would you use it instead of time-based retention?**
Log compaction (`cleanup.policy=compact`) keeps only the **latest record per key** in a
partition, deleting older records that share that key rather than deleting by age. It's used
for topics that represent "current state per key" — e.g. a changelog of each user's latest
profile — where you don't care about every historical value, only the newest one per key,
and you want that latest state to be retained forever rather than expiring. Time/size-based
retention (`retention.ms` / `retention.bytes`) is the default and fits pure event-history
topics, where you do care about every event and just want to bound storage by age or size.

**Q: What are brokers, clusters, and replication, and why do they matter?** ⭐
A broker is a single Kafka server storing some subset of the cluster's partitions; a cluster is
a set of brokers working together. Each partition is replicated across multiple brokers — one
replica is the leader (all produces/consumes for that partition go through it) and the rest are
followers that continuously fetch from the leader to stay caught up. The **ISR** (in-sync
replicas) is the subset of replicas fully caught up; `acks=all` (Phase 2) means waiting for
every replica currently in the ISR, not necessarily every replica that nominally exists. If the
leader's broker dies, the cluster's controller automatically elects a new leader from the ISR —
producers and consumers refresh their metadata and transparently retry against the new leader.
This is what gives Kafka durability and availability through broker failures without manual
intervention.

*Follow-up: ZooKeeper or KRaft?* Older Kafka clusters used an external ZooKeeper ensemble to
store cluster metadata and run leader election. Modern Kafka (KIP-500, default from the 3.x/4.x
era) replaced that with **KRaft**, a built-in Raft-based consensus protocol run by the brokers
themselves, removing the separate ZooKeeper dependency and simplifying cluster operations.

**Q: What are the downsides of event-driven architecture?** *nuance*
Eventual consistency — a consumer may process an event well after it occurred, so different
parts of the system can briefly disagree about state. Harder debugging and tracing, since a
single logical operation now spans an asynchronous chain of independently-processed hops
instead of one call stack — you generally need correlation IDs and distributed tracing to
follow a request end to end. Operational complexity — running and operating a Kafka cluster,
managing schema evolution for event payloads, monitoring consumer lag. And because Kafka's
default delivery guarantee is at-least-once (Phase 4), every consumer needs to be written
idempotently, which is an easy requirement to forget until duplicates actually appear in
production. Event-driven architecture is powerful for decoupling and scale, but it's a genuine
trade against the simplicity of synchronous request/response — pick it deliberately, not by
default.
