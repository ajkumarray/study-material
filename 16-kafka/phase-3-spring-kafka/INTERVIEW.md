<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · topics partitions](../phase-2-topics-partitions/NOTES.md) | [Phase 4 · delivery patterns ➡](../phase-4-delivery-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Kafka Integration: Interview Q&A

⭐ = asked constantly.

**Q: How do you produce messages in Spring Kafka, and is the call blocking?** ⭐
Inject a `KafkaTemplate<K, V>` bean (Spring Boot auto-configures it from
`spring.kafka.producer.*` properties) and call `send(topic, key, value)` — the same
`(topic, key, value)` shape as a raw `ProducerRecord`, without building one by hand. It's
asynchronous by default: `send()` returns a `CompletableFuture<SendResult<K,V>>` immediately,
so the calling thread (e.g. an HTTP handler doing `POST /expenses`) is never blocked on network
I/O to the broker. You can `.get()` to block for confirmation if you genuinely need it
synchronously, but the idiomatic pattern is `.whenComplete((result, ex) -> ...)` to react to
success/failure asynchronously — log the partition/offset on success, alert or retry on
failure — without sacrificing the non-blocking send.

*Follow-up: does passing a key here actually matter?* Yes — it's not incidental, it's exactly
what determines the record's partition (Phase 2.1), and therefore what stays ordered relative
to what. This track's producer keys every event by `userId` deliberately, so one user's events
are guaranteed to arrive in order.

**Q: How do you consume messages with Spring Kafka?** ⭐⭐
Annotate a method with `@KafkaListener(topics = "...", groupId = "...")`; Spring runs a
consumer in that group and invokes the annotated method once per record. Multiple application
instances declaring the same `groupId` automatically form one consumer group and share the
topic's partitions among them — no manual coordination code required. The method can take the
raw value, or a `ConsumerRecord<K,V>` for access to the key/partition/offset/headers, plus an
`Acknowledgment` parameter if you're using manual ack mode to control exactly when the offset is
committed.

**Q: How do you serialize/deserialize event payloads, and what's the security concern with
JSON?**
Configure `key-serializer`/`value-serializer` (producer) and `key-deserializer`/
`value-deserializer` (consumer) in `application.yml`. The common default is a `StringSerializer`
for the key and Spring Kafka's `JsonSerializer`/`JsonDeserializer` for the value — human
readable, easy to debug with `kafka-console-consumer`. The security concern:
`JsonDeserializer` by default restricts which classes it's willing to instantiate from a
record's type metadata, and `spring.json.trusted.packages` is how you allow-list exactly the
package(s) your own event classes live in — an unrestricted deserializer is a known
deserialization attack surface. For production systems with many evolving event types, teams
typically move to Avro or Protobuf with a Schema Registry, trading JSON's readability for a
much smaller wire format and centrally-enforced, versioned schema compatibility.

**Q: How do you get at-least-once processing in Spring Kafka, concretely?** ⭐⭐
Set `enable-auto-commit: false` (so offsets are never committed on a background timer that
doesn't know whether your logic actually succeeded), set `spring.kafka.listener.ack-mode:
manual`, and call `Acknowledgment.acknowledge()` inside the listener method only after
processing completes successfully:

```java
@KafkaListener(topics = "expense-events", groupId = "expense-analytics")
public void onEvent(ConsumerRecord<String, ExpenseEvent> record, Acknowledgment ack) {
    updateRunningTotals(record.value());   // must be idempotent
    ack.acknowledge();                     // commit only after success
}
```

A crash before `acknowledge()` is called means the record's offset was never committed, so it's
redelivered on restart/rebalance and reprocessed — hence the processing logic must be
idempotent, since the same record can legitimately be handled more than once.

*Follow-up: what if you called `acknowledge()` at the top of the method, before processing?*
That flips you to at-most-once — a crash mid-processing after the commit means the record is
gone for good, never retried, and its effects (e.g. an analytics update) are silently missing.

**Q: `RECORD` vs `BATCH` vs `MANUAL` vs `MANUAL_IMMEDIATE` ack mode — what's the difference?**
*nuance*
They control when, and at what granularity, offsets actually get committed once auto-commit is
off. `RECORD` commits after every single record's listener invocation returns. `BATCH` (the
default without manual ack) commits after the whole batch returned by one `poll()` finishes.
`MANUAL` requires the listener to call `acknowledge()` itself, but the actual network commit is
still queued to happen at the container's normal commit point. `MANUAL_IMMEDIATE` is the same
except the commit is sent to the broker synchronously the instant `acknowledge()` is called —
useful when you need the commit to be durable before moving on, at the cost of an extra
round-trip per acknowledgment. This track uses `manual`.

**Q: Why is relying on Kafka's default auto-commit risky?** ⭐
Because auto-commit fires on a wall-clock timer (`auto.commit.interval.ms`), completely
independent of whether your listener actually finished processing the records it covers. A
timer can commit an offset for a record that's still mid-processing; if the app then crashes
before that record's processing actually completes, the record is never retried on restart —
you get silent, at-most-once-style data loss without ever intending at-most-once semantics.
Turning off auto-commit and committing explicitly, tied to actual processing success, is what
makes the delivery guarantee a deliberate choice instead of an accident of timing.

**Q: How do you handle a message that keeps failing — a "poison" message?** ⭐
Configure a `DefaultErrorHandler` on the listener container with a retry/backoff policy for
transient failures, and after a configured number of failed attempts, route the record to a
**dead-letter topic** via a `DeadLetterPublishingRecoverer` instead of retrying forever. This
keeps one permanently-broken record from blocking every other record behind it on that
partition — since Kafka delivers a partition's records in strict order, a stuck consumer
offset there would otherwise stall the whole partition. The dead-letter topic is then
monitored and reprocessed out-of-band, separately from the main flow.

**Q: How do you scale consumer throughput within a single application instance?**
Set the listener's `concurrency` (e.g. `@KafkaListener(..., concurrency = "3")`, or configure it
on the container factory) to run multiple consumer threads for that listener inside one process,
each independently assigned partitions by the normal group rebalance protocol. Concurrency is
still capped usefully at the topic's partition count — a 4th thread with only 3 partitions to
assign sits idle, exactly the same ceiling as adding more application instances to the group
(Phase 2.3). Beyond that ceiling, the next lever is increasing the topic's partition count
itself (planned carefully, since it changes the key→partition mapping for existing keys).

**Q: What does `auto-offset-reset` do, and does changing it affect an already-running
consumer group?**
It only decides where a consumer starts when there is **no existing committed offset** for its
group — a brand-new group's very first run, or a group whose committed offsets fell outside the
broker's offset-retention window. `earliest` replays the entire retained log; `latest` skips
straight to new records produced after joining. Changing it in config has zero effect on a
group that already has committed offsets; that group simply resumes from its last commit
regardless of the setting.

**Q: Why externalize `bootstrap-servers` as `${KAFKA_BROKERS:localhost:9092}`?** *nuance*
So the exact same build artifact can point at different broker addresses per environment —
`localhost:9092` for local development, a `kafka` service hostname inside a Docker Compose
network or a Kubernetes cluster — purely via an environment variable, with no rebuild or
per-environment code branch. It's the same 12-factor externalized-config principle used for the
database connection string elsewhere in this repo's Spring Boot track; the brokers list is just
the cluster's entry point, and the client discovers the rest of the cluster's topology from
there automatically.
