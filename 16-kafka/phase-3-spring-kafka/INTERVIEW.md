<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · topics partitions](../phase-2-topics-partitions/NOTES.md) | [Phase 4 · delivery patterns ➡](../phase-4-delivery-patterns/NOTES.md)
<!-- /nav -->

# Phase 3 — Spring Kafka Integration: Interview Q&A

⭐ = asked constantly.

**Q: How do you produce messages in Spring Kafka?** ⭐
Inject `KafkaTemplate<K,V>` and call `send(topic, key, value)`. It's asynchronous (returns a
future) with optional callbacks for success/failure. Configure key/value serializers (String
key, JSON value) and pass a meaningful key to control partitioning and ordering.

**Q: How do you consume messages?** ⭐⭐
Annotate a method with `@KafkaListener(topics, groupId)`; Spring runs a consumer in that group
and calls the method per record. Multiple instances sharing the `groupId` form the group and
split partitions. Use manual ack to commit offsets after successful processing.

**Q: How do you serialize event payloads?**
Configure serializers/deserializers — commonly String keys and JSON values
(`JsonSerializer`/`JsonDeserializer`). For production at scale, Avro/Protobuf with a Schema
Registry gives compact messages and safe schema evolution. Restrict JSON deserialization with
`spring.json.trusted.packages` for security.

**Q: How do you get at-least-once processing in Spring Kafka?** ⭐⭐
Disable auto-commit, set `ack-mode: manual`, and call `acknowledge()` only after the record is
successfully processed. A crash before the ack redelivers the record — so processing must be
idempotent. Committing before processing would instead risk losing records (at-most-once).

**Q: How do you handle a message that keeps failing (poison message)?** ⭐
Configure an error handler (e.g. `DefaultErrorHandler`) with a retry/backoff policy, and after
N failed attempts route the record to a dead-letter topic so it doesn't block the partition.
The DLQ is inspected/reprocessed separately.

**Q: How do you scale consumer throughput in one app?**
Set listener `concurrency` to run multiple consumer threads, up to the topic's partition count
(a partition maps to one thread). Beyond that, add more app instances in the same group.
Parallelism is ultimately bounded by partitions.

**Q: What does `auto-offset-reset` do?**
It sets where a consumer group with no committed offset starts: `earliest` reads the whole
retained history, `latest` reads only records arriving after it joins. It only applies when
there's no existing committed offset for the group.

**Q: Why externalize `bootstrap-servers`?** *nuance*
So the same build points at different brokers per environment (`localhost` in dev, the
`kafka` service in Docker/K8s) via an env var — the 12-factor config principle. The brokers
list is just the cluster entry point; clients discover the full cluster from it.
