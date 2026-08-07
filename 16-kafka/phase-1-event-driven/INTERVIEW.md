<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · topics partitions ➡](../phase-2-topics-partitions/NOTES.md)
<!-- /nav -->

# Phase 1 — Kafka & Event-Driven Architecture: Interview Q&A

⭐ = asked constantly.

**Q: What is Kafka?** ⭐⭐
A distributed, durable, append-only commit log for streaming events. Producers append records
to topics (split into partitions across brokers), and consumers read them independently at
their own pace. The log is retained and replayable, which decouples producers from consumers.

**Q: Event-driven vs request/response — why choose events?** ⭐⭐
In request/response a service calls another and waits, coupling them in time and availability.
Event-driven publishes facts to a log that consumers react to later, giving decoupling (add
consumers freely), resilience (a down consumer catches up), scalability/buffering, and
replay. The trade-off is eventual consistency and harder end-to-end tracing.

**Q: What are topics, partitions, and offsets?** ⭐⭐
A topic is a named log; it's divided into partitions, each an independent ordered sequence of
records. Every record has an offset (its position in the partition). Partitions enable
parallelism and scaling; offsets let consumers track and resume/replay their position.

**Q: How is Kafka different from a traditional message queue?** ⭐⭐
A queue (RabbitMQ) deletes a message once consumed and focuses on routing/work distribution.
Kafka is a retained log — reading doesn't delete, so many consumers can read the same data
independently and replay history. Kafka favors high-throughput streaming and replay; queues
favor task distribution and complex routing.

**Q: Kafka vs Redis Pub/Sub?** ⭐⭐
Redis Pub/Sub is in-memory fire-and-forget — no persistence, and offline subscribers miss
messages — good for ephemeral signals. Kafka persists and replays events with ordering and
consumer groups. Transient fan-out → Redis; durable, replayable event backbone → Kafka.

**Q: What does retention/replayability enable?** ⭐
Because records persist after being read, you can rewind and reprocess: rebuild a read model,
onboard a new consumer that reads all history, or reprocess after fixing a bug. The log
becomes a durable source of events, which underpins event sourcing.

**Q: What are brokers and replication?**
Brokers are the Kafka servers forming a cluster that stores partitions. Each partition is
replicated across brokers (a leader + followers) for fault tolerance — if a broker fails, a
replica takes over. Coordination used ZooKeeper historically and now uses built-in KRaft.

**Q: What are the downsides of event-driven architecture?** *nuance*
Eventual consistency (consumers lag), harder debugging/tracing across async hops, operational
complexity (running Kafka, schema evolution), and the need for idempotent consumers due to
at-least-once delivery. It's powerful for decoupling and scale but adds distributed-systems
complexity you must be ready to manage.
