<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · event driven](../phase-1-event-driven/NOTES.md) | [Phase 3 · spring kafka ➡](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 2 — Topics, Partitions, Producers & Consumers: Interview Q&A

⭐ = asked constantly.

**Q: What does a partition give you?** ⭐⭐
Partitions are Kafka's unit of parallelism and ordering. Each is an independent ordered log,
and a topic's partitions spread across brokers for scale. Kafka guarantees order within a
partition but not across partitions.

**Q: How does Kafka decide which partition a record goes to?** ⭐⭐
By the message key: `hash(key) % partitionCount`, so all records with the same key go to the
same partition and preserve their relative order. With no key, records round-robin across
partitions for maximum spread but no per-key ordering.

**Q: How do you guarantee ordering for a given entity?** ⭐
Use that entity's id as the message key (e.g. `userId`), so all its events land on one
partition and stay ordered. Different entities spread across partitions for parallelism.
Ordering is per-partition, so you order per key, not globally.

**Q: What is a consumer group?** ⭐⭐
A set of consumers sharing a `group-id` that cooperatively consume a topic: Kafka assigns each
partition to exactly one member, so each record is processed once per group and work is
distributed. Different groups consume the same topic independently (fan-out).

**Q: How many consumers can usefully run in a group?** ⭐
At most the number of partitions — each partition goes to one consumer, so extra consumers
beyond the partition count sit idle. You size partitions for your desired maximum consumer
parallelism.

**Q: What is a rebalance?** ⭐
When a consumer joins, leaves, or fails, Kafka reassigns partitions among the remaining group
members (a brief pause). It provides automatic failover and elastic scaling, but because a
partition can move before offsets are committed, it can cause redelivery — so consumers must
be idempotent.

**Q: What does `acks` control on the producer?** ⭐⭐
Durability vs latency. `acks=0` doesn't wait (can lose data), `acks=1` waits for the leader
only, `acks=all` waits for all in-sync replicas (no loss if a broker fails). `acks=all` plus
`enable.idempotence` gives the strongest, duplicate-free produce.

**Q: When are offsets committed and why does it matter?** *nuance*
The consumer commits the offset up to which it has processed. Committing *after* processing
gives at-least-once (a crash before commit redelivers the record). Committing *before* gives
at-most-once (a crash after commit loses it). The timing choice is what sets your delivery
semantics.
