<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · topics partitions](../phase-2-topics-partitions/NOTES.md) | [Phase 4 · delivery patterns ➡](../phase-4-delivery-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Kafka Integration: Notes

Spring Kafka wraps the raw `kafka-clients` library in familiar Spring idioms: a template to
produce, an annotation to consume, and Spring Boot's config-driven `application.yml` for
serialization, groups, and error handling. Every concept from Phases 1-2 (partitions, keys,
`acks`, consumer groups, offset commits) is still exactly what's happening underneath — Spring
just gives it an ergonomic API. This phase walks the track's real `examples/`: `ExpenseEvent.java`
(the event), `ExpenseEventProducer.java` (produces), `ExpenseEventConsumer.java` (consumes),
and `application.yml` (config).

## 3.1 — Producing with `KafkaTemplate`

### Key Concepts

- **`KafkaTemplate<K, V>`** is Spring's wrapper around the raw `KafkaProducer` — injected as a
  bean (Spring Boot auto-configures it from `spring.kafka.producer.*` properties) and used to
  send records.
- **`send(topic, key, value)`** is the common overload — the same `(topic, key, value)` shape
  as the raw Kafka `ProducerRecord`, just without constructing one by hand. Overloads also
  exist for `send(topic, value)` (no key — round-robin/sticky partitioning, Phase 2.1),
  `send(topic, partition, key, value)` (force a specific partition), and
  `send(ProducerRecord<K,V> record)` for full control (e.g. to attach headers).
- **Asynchronous by default** — `send()` returns a `CompletableFuture<SendResult<K,V>>`
  immediately; the actual network I/O happens on the producer's internal I/O thread. You can
  `.get()` to block for confirmation, or (idiomatically) attach `.whenComplete((result, ex) -> ...)`
  to react without blocking the calling thread.
- **The key you pass is a first-class design decision**, not an afterthought — it's exactly
  what drives partitioning and ordering (Phase 2.1). `ExpenseEventProducer` deliberately keys
  by `userId` so one user's events stay ordered.

### Worked example — the track's producer

```java
// examples/ExpenseEventProducer.java
@Component
public class ExpenseEventProducer {

    private static final String TOPIC = "expense-events";
    private final KafkaTemplate<String, ExpenseEvent> kafka;

    public ExpenseEventProducer(KafkaTemplate<String, ExpenseEvent> kafka) {
        this.kafka = kafka;
    }

    public void publish(ExpenseEvent event) {
        kafka.send(TOPIC, event.userId(), event);   // (topic, key, value)
    }
}
```

```java
// Reacting to the async result without blocking (a realistic addition):
public void publish(ExpenseEvent event) {
    kafka.send(TOPIC, event.userId(), event)
        .whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {}: {}", event.eventId(), ex.getMessage());
                // e.g. increment a metric, or (carefully) retry/alert
            } else {
                log.debug("Published {} to partition {} offset {}",
                    event.eventId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
}
```

In the second example, `send()` returns immediately — the calling thread (e.g. an HTTP request
handling `POST /expenses`) is never blocked on Kafka I/O. The callback fires later, on Kafka's
own thread, once the broker actually acknowledges (or fails to acknowledge, per `acks`,
Phase 2.2) the write — giving you observability into failures without sacrificing the
asynchronous send that makes event publishing cheap for the caller.

### Why it's useful

`KafkaTemplate` turns "construct a `ProducerRecord`, hand it to a raw `KafkaProducer`, manage
its lifecycle" into a single injected bean and a one-line `send()` call, while Spring Boot
auto-configures the underlying producer entirely from `application.yml` properties — no
manual `Properties` object, no manual producer construction or `close()`.

## 3.2 — Serialization

### Key Concepts

- **Key/value serializers** turn Java objects into bytes for the wire (and deserializers turn
  bytes back into objects on the consumer side). Configured per-side in `application.yml`.
- **String keys, JSON values** is the common, human-readable default: `StringSerializer` for
  the key (`userId` is naturally a string), `JsonSerializer`/`JsonDeserializer` (Spring Kafka's
  Jackson-backed serializer) for the event value, so a message body is plain readable JSON —
  useful for debugging with `kafka-console-consumer` and for consumption by non-Java services.
- **Trusted packages** — `JsonDeserializer` by default restricts which Java classes it's
  willing to instantiate from an incoming record's type header, to prevent a malicious or
  malformed payload from deserializing into an arbitrary, unexpected class (a deserialization
  vulnerability class). `spring.json.trusted.packages: com.example.expense.events` in the
  track's config explicitly allow-lists only that package.
- **Avro/Protobuf + Schema Registry** — production systems handling many event types and
  evolving schemas over time commonly move to a binary format with a **Schema Registry**
  (Confluent Schema Registry is the most common) instead of raw JSON. Benefits: much smaller
  wire size, and centrally-enforced, versioned schema compatibility rules (e.g. "a new field
  must have a default, so old consumers can still read new messages") — catching a breaking
  schema change at produce time rather than as a runtime deserialization failure in a consumer.
  JSON is simpler to start with and perfectly fine at this track's scale; Avro/Protobuf becomes
  worth the operational overhead once you have many event types and multiple teams producing/
  consuming them.

### Worked example — the wire format

```json
// What actually goes over the wire as the VALUE for one ExpenseEvent record
// (JsonSerializer also writes a type header Spring uses on the consumer side):
{
  "eventId": "8f14e45f-ceea-467e-b3b1-f0f1e3a4a2b0",
  "type": "created",
  "userId": "u1",
  "expenseId": 101,
  "amountCents": 2599,
  "occurredAt": 1732200000000
}
```

```yaml
# application.yml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
    consumer:
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "com.example.expense.events"
```

### Why it's useful

Matching serializer/deserializer pairs across every producer and consumer of a topic is a
correctness requirement — a mismatch (e.g. a consumer expecting `ExpenseEvent` but receiving
bytes serialized as a different class) fails at deserialization time, not compile time, so it's
a common source of runtime errors in a multi-service Kafka setup. Restricting trusted packages
is a real security control, not boilerplate — an unrestricted `JsonDeserializer` is a known
attack surface if a topic could ever receive attacker-influenced payloads.

## 3.3 — Consuming with `@KafkaListener`

### Key Concepts

- **`@KafkaListener(topics = "...", groupId = "...")`** on a method turns it into a consumer
  callback: Spring runs a consumer (or several, see concurrency below) in that group and
  invokes the annotated method once per record. Multiple application instances declaring the
  same `groupId` automatically form one consumer group and share the topic's partitions
  (Phase 2.3) — no manual coordination code.
- **Method signature flexibility** — Spring can inject the raw value, a `ConsumerRecord<K,V>`
  (gives access to key, headers, partition, offset, timestamp, not just the value), or a list
  of records for batch listening. The track's consumer takes `ConsumerRecord<String, ExpenseEvent>`
  plus an `Acknowledgment` parameter for manual commit control.
- **`Acknowledgment.acknowledge()`** — with manual ack mode (3.4), this is how the listener
  method explicitly tells Spring Kafka "commit the offset for this record now." Calling it only
  after processing succeeds is what gives at-least-once semantics (Phase 4.1).
- **Concurrency** — `@KafkaListener(..., concurrency = "3")` (or configuring the container
  factory) runs multiple consumer threads for that listener within one application instance,
  each independently assigned partitions by the group's rebalance protocol — parallelism
  *within* a single process, on top of parallelism *across* processes (more app instances in
  the same group). Concurrency beyond the topic's partition count is wasted, same as Phase 2.3.
- **Error handling** — a `DefaultErrorHandler` configured on the listener container applies
  retry/backoff to a record that threw an exception, and after retries are exhausted can route
  it to a **dead-letter topic** via a `DeadLetterPublishingRecoverer` (Phase 4.2) instead of
  blocking the partition on one permanently-failing ("poison") record.

### Worked example — the track's consumer

```java
// examples/ExpenseEventConsumer.java
@Component
public class ExpenseEventConsumer {

    @KafkaListener(topics = "expense-events", groupId = "expense-analytics")
    public void onEvent(ConsumerRecord<String, ExpenseEvent> record, Acknowledgment ack) {
        ExpenseEvent event = record.value();

        // Must be IDEMPOTENT: at-least-once means the same event can arrive
        // more than once (e.g. redelivered after a rebalance before commit).
        updateRunningTotals(event);   // e.g. UPSERT into a materialized analytics table

        // Commit the offset ONLY after successful processing — at-least-once.
        ack.acknowledge();
    }

    private void updateRunningTotals(ExpenseEvent event) { /* ... */ }
}
```

```java
// With explicit concurrency and access to partition/offset metadata:
@KafkaListener(topics = "expense-events", groupId = "expense-analytics", concurrency = "3")
public void onEvent(ConsumerRecord<String, ExpenseEvent> record, Acknowledgment ack) {
    log.debug("partition={} offset={} key={}",
        record.partition(), record.offset(), record.key());
    updateRunningTotals(record.value());
    ack.acknowledge();
}
```

In this example, `concurrency = "3"` runs three consumer threads for this listener inside one
application instance. If `expense-events` has 3 partitions, each thread gets exactly one — full
utilization within a single process; running a second application instance in the same
`expense-analytics` group at that point would trigger a rebalance but leave the new instance's
threads idle, since all 3 partitions are already claimed.

### Why it's useful

`@KafkaListener` collapses "construct a `KafkaConsumer`, run a poll loop on a background thread,
handle rebalances, manage offset commits, shut down cleanly" into one annotated method — Spring
Boot wires up the underlying `ConcurrentMessageListenerContainer` from `application.yml`
entirely. You write the business logic; Spring manages the consumer lifecycle.

## 3.4 — Ack modes and offset commit control

### Key Concepts

- **`enable-auto-commit`** (raw Kafka consumer setting) — if `true` (the Kafka client default),
  offsets are committed automatically on a timer (`auto.commit.interval.ms`), *regardless* of
  whether your listener method actually finished successfully. This can silently lose records
  under at-most-once-like conditions (a commit fires, then the app crashes mid-processing) and
  makes delivery semantics hard to reason about. The track sets `enable-auto-commit: false` to
  take explicit control.
- **`spring.kafka.listener.ack-mode`** governs *when* Spring commits, once auto-commit is off:
  - `RECORD` — commit after every single record's listener invocation returns.
  - `BATCH` (default when manual ack isn't used) — commit after each batch returned by one
    `poll()` is fully processed.
  - `MANUAL` — the listener method must call `Acknowledgment.acknowledge()` itself; the actual
    commit is still batched/queued and happens on the next `poll()`'s natural commit point.
  - `MANUAL_IMMEDIATE` — same as `MANUAL`, but the commit is sent to the broker synchronously,
    immediately, as soon as `acknowledge()` is called.
  - The track uses `ack-mode: manual` with `Acknowledgment.acknowledge()` called at the end of
    successful processing (3.3) — explicit, at-least-once commit timing under the application's
    control rather than a timer's.

### Worked example — why auto-commit is risky

```
Timeline with enable-auto-commit=true, auto.commit.interval.ms=5000:

t=0s    poll() returns records[100..110]
t=1s    listener processing record 105... (still working)
t=5s    ⏰ auto-commit timer fires → commits offset up through 110 anyway
        (Spring/Kafka doesn't know or care that 105 isn't done yet)
t=6s    processing record 105 throws an exception — app crashes / listener errors out
t=7s    app restarts, resumes from committed offset 110
        → records 106-110 (and the failed 105) are NEVER retried. Silently lost.
```

```
Same timeline with ack-mode: manual, ack.acknowledge() called only on success:

t=0s    poll() returns records[100..110]
t=1s    listener processes 100..104 successfully, acknowledge()s each
t=5s    processing record 105 throws → NOT acknowledged
        (offsets committed so far: up through 104)
t=6s    app crashes
t=7s    app restarts, resumes from offset 105 — the failed record IS redelivered
        and retried. No silent loss, but 105 (and possibly a few after it, depending
        on ack granularity) may be processed more than once if it had partially
        succeeded before throwing — hence idempotency (Phase 4.1) is still required.
```

### Why it's useful

Auto-commit trades correctness for convenience by committing on a wall-clock timer that has no
idea whether your business logic actually succeeded. Manual ack, tied explicitly to "processing
this record completed successfully," is what makes at-least-once an intentional, understood
guarantee instead of an accidental one — the standard choice whenever losing a record silently
is worse than occasionally reprocessing one (which is almost always true, given idempotent
processing is comparatively cheap to build).

## 3.5 — Config that matters

From `examples/application.yml`, consolidated:

| Setting | Purpose |
|---|---|
| `bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}` | Broker list (cluster entry point); externalized via env var for dev/Docker/K8s (12-factor config). |
| `producer.acks: all` | Wait for all in-sync replicas — strongest produce durability (Phase 2.2). |
| `producer.retries: 3` | Retry transient send failures automatically. |
| `producer.properties.enable.idempotence: true` | No duplicate records from producer retries. |
| `consumer.group-id: expense-analytics` | Consumers sharing this id form one consumer group (Phase 2.3). |
| `consumer.auto-offset-reset: earliest` | A brand-new group reads the full retained history rather than only new records. |
| `consumer.enable-auto-commit: false` | Offsets are **not** committed on a timer — paired with manual ack. |
| `consumer.properties.spring.json.trusted.packages` | Restricts which classes `JsonDeserializer` may instantiate — a security control. |
| `listener.ack-mode: manual` | The listener explicitly acknowledges each record after processing (3.4). |

### Why it's useful

Externalizing `bootstrap-servers` via `${KAFKA_BROKERS:localhost:9092}` means the exact same
build artifact points at `localhost:9092` in local dev and at a `kafka` service hostname inside
Docker Compose or Kubernetes, without a code change or a separate build per environment — the
same 12-factor pattern used for the database URL elsewhere in this repo's Spring Boot track.

## Perspective

Spring Kafka reduces event plumbing to two lines of application code: `KafkaTemplate.send()` on
the producer side, and an `@KafkaListener` method with manual `Acknowledgment.acknowledge()` on
the consumer side — with serialization, group membership, concurrency, and error handling all
driven by `application.yml`. The decisions that actually determine correctness are unchanged
from raw Kafka: the **partition key** (ordering/parallelism, Phase 2.1), **`acks` +
idempotence** (produce durability, Phase 2.2), and **offset-commit timing + error handling**
(delivery semantics and poison-message handling, Phase 4). Spring just makes all of them
ergonomic to configure correctly instead of hand-wiring a raw `KafkaProducer`/`KafkaConsumer`.

## Summary / Key Takeaways

- **`KafkaTemplate.send(topic, key, value)`** is async by default (`CompletableFuture`); attach
  a callback instead of blocking to keep the caller non-blocking.
- **String keys + JSON values** (`JsonSerializer`/`JsonDeserializer`) are the readable default;
  restrict `spring.json.trusted.packages` for security; Avro/Protobuf + Schema Registry is the
  production move once schema evolution and message size matter.
- **`@KafkaListener(topics, groupId)`** turns a method into a consumer callback; matching
  `groupId` across instances forms a consumer group automatically; `concurrency` adds threads
  within one instance, capped usefully at the partition count.
- **`enable-auto-commit: false` + `ack-mode: manual` + `Acknowledgment.acknowledge()` after
  success** is the standard way to get intentional at-least-once semantics instead of
  timer-driven, correctness-unaware auto-commit.
- A `DefaultErrorHandler` with retry/backoff, routing exhausted failures to a **dead-letter
  topic**, keeps one permanently-failing record from blocking its entire partition (Phase 4.2).
