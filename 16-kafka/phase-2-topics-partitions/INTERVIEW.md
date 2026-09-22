<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · event driven](../phase-1-event-driven/NOTES.md) | [Phase 3 · spring kafka ➡](../phase-3-spring-kafka/NOTES.md)
<!-- /nav -->

# Phase 2 — Topics, Partitions, Producers & Consumers: Interview Q&A

⭐ = asked constantly.

**Q: What does a partition give you, and what ordering guarantee does Kafka actually make?** ⭐⭐
A partition is Kafka's unit of both parallelism and ordering. Each is an independent, strictly
ordered log — records within one partition arrive at any consumer in exactly the order they
were written. Kafka makes **no** ordering guarantee across different partitions of the same
topic. That's a precise, commonly-misstated point: "Kafka guarantees ordering" is only true
per-partition, not per-topic — if you need two events to be seen in order, they must be routed
to the same partition.

**Q: How does Kafka decide which partition a record goes to?** ⭐⭐
By the message key, using the default partitioner's rule: `hash(key) % numPartitions`. Every
record with the same key is deterministically routed to the same partition, which is what
preserves their relative order. If the key is `null`, records are spread across partitions
(modern Kafka clients batch several records onto one partition at a time for efficiency rather
than strict per-record round-robin) — maximizing spread, but giving up any per-entity ordering
guarantee. A custom `Partitioner` implementation can override this default routing entirely if
you need something other than hash-based assignment.

**Q: How do you guarantee ordering for a specific entity's events, e.g. all updates to one
user?** ⭐⭐
Use that entity's id as the message key — in this track's `ExpenseEventProducer`, every event
is keyed by `userId`: `kafka.send(TOPIC, event.userId(), event)`. All of `u1`'s events land on
the same partition and are therefore delivered to any consumer in the exact order they were
produced (created, then updated, then deleted) — while different users' events spread across
partitions and are processed fully in parallel. Ordering is always "ordered per key," never
globally ordered across a whole topic, unless you deliberately force everything to one
partition (which then caps you to that single partition's throughput).

**Q: What is a consumer group, and how is it different from just having multiple consumers?** ⭐⭐
A consumer group is a named set of consumers (sharing a `group-id`) that cooperatively read a
topic: Kafka guarantees each partition is assigned to exactly one member of the group at a
time, so a given record is processed **once per group**, and the group's total work is
distributed across its running instances. Without a group (i.e. each consumer using a unique,
unshared group id), every consumer would independently get every record — that's the
fan-out behavior used when you want multiple *different* subsystems to each see the full
stream, not to split load-balanced work across instances of the same subsystem.

**Q: How many consumers can usefully run in one group?** ⭐
At most the number of partitions — since each partition is assigned to exactly one member, a
(partitions+1)th instance in the same group has no partition left to be assigned and sits
completely idle. This is why partition count is a capacity-planning decision made up front:
you size it for the maximum consumer parallelism you expect to need, since increasing it later
also changes the key→partition hash mapping for existing keys.

**Q: What happens during a rebalance, and why does it matter for correctness?** ⭐⭐
When a consumer joins, leaves, or is considered dead (it stops sending heartbeats within
`session.timeout.ms`), the group coordinator reassigns the group's partitions among the
remaining live members — a brief pause while assignment settles. This gives automatic failover
(a crashed instance's partitions move to a live one) and elastic scaling (add an instance,
partitions redistribute) for free. But it also means a partition can be reassigned to a
different consumer *before* the previous owner's offset commit for records it already finished
processing makes it through — the new owner then re-reads and reprocesses those same records.
That's not a bug in Kafka; it's the direct mechanism by which "at-least-once" (Phase 4)
actually manifests, and it's exactly why consumer processing logic must be idempotent.

**Q: Can two different teams/services both consume the same topic without interfering with
each other?** ⭐
Yes — that's what separate consumer groups are for. Each group tracks its own committed offsets
independently, so an `expense-analytics` group and a `notifications` group can both consume
every record on `expense-events`, at their own pace, with zero awareness of each other. This
fan-out to multiple independent full readers of the same stream is something a classic work
queue (where a message is consumed once, by one worker, then gone) can't give you without extra
infrastructure — it falls out of Kafka's retained-log model for free.

**Q: What does `acks` control on the producer, and what would you set it to in production?** ⭐⭐
`acks` controls how many replicas must confirm a write before the producer treats it as
successful — the durability/latency trade-off. `acks=0` doesn't wait at all (fastest, can
silently lose data if the leader dies right after). `acks=1` waits for the partition leader only
(faster than `all`, but a leader crash before followers replicate loses the record). `acks=all`
waits for every replica currently in the in-sync replica set (ISR) — the strongest guarantee,
since the record survives as long as any ISR member does. For anything where losing a produced
event is unacceptable (this track's `application.yml` uses exactly this), production
configuration is `acks=all` combined with `enable.idempotence=true`.

**Q: What does `enable.idempotence=true` actually prevent, and how is it different from an
"idempotent consumer"?** ⭐
It prevents the *producer* from writing duplicate records when it has to retry a send — e.g. a
send times out because the broker's ack was lost in transit even though the write actually
succeeded; without idempotence, a naive retry could write that record a second time. With it
enabled, the broker tracks a producer ID plus a per-partition sequence number and deduplicates
retried writes of the same producer, so a retry never results in two copies on the broker side.
This is entirely separate from an *idempotent consumer* (Phase 4), which is about the
application's processing logic tolerating the same record being delivered and processed more
than once — a concern that exists regardless of producer idempotence, because rebalances and
consumer crashes can still cause redelivery on the read side.

**Q: When are offsets committed, and why does that timing matter so much?** ⭐⭐
The consumer periodically (or explicitly) tells Kafka the offset up to which it has processed a
partition. Committing *after* successful processing (this track's manual `ack.acknowledge()`
in `ExpenseEventConsumer`) gives at-least-once delivery: a crash before the commit means the
record is redelivered and reprocessed on restart/rebalance, so processing must be idempotent.
Committing *before* processing instead gives at-most-once: a crash after the commit but before
finishing means the record is never retried, and its effects are lost. This single timing
decision is what determines which of Kafka's delivery semantics (Phase 4.1) your consumer
actually exhibits — it's not a separate Kafka feature you turn on, it's a direct consequence of
when you call commit.

**Q: What does `auto-offset-reset` do, and when does it actually take effect?**
It decides where a consumer starts reading when there's **no existing committed offset** for
its group on a given partition — a brand-new group's first run, or a group whose committed
offsets have expired past the broker's offset-retention window. `earliest` starts from the very
beginning of the retained log (a full replay of history); `latest` starts from whatever is
produced after the consumer joins, skipping everything already in the log. It has no effect at
all on a group that already has a committed offset — that group simply resumes from where it
left off, regardless of this setting.

**Q: What's the difference between batching/compression settings (`linger.ms`, `batch.size`,
`compression.type`) and durability settings like `acks`?** *nuance*
They're orthogonal concerns. `acks`/`enable.idempotence` control correctness — whether a write
can be lost or duplicated. `linger.ms`/`batch.size`/`compression.type` control throughput —
the producer buffers records per partition and sends them as a compressed batch instead of one
record per network round trip, trading a small deliberate delay (a few milliseconds of
`linger.ms`) for dramatically higher throughput under load. You tune both independently: a
production producer typically wants both strong durability (`acks=all`, idempotence) *and*
reasonable batching (a small `linger.ms`), since neither setting substitutes for the other.
