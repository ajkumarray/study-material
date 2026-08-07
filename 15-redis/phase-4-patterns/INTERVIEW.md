<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring integration](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 4 — Sessions, Locks, Rate Limiting & Pub/Sub: Interview Q&A

⭐ = asked constantly.

**Q: Why store sessions in Redis?** ⭐⭐
So session state lives outside any single app instance, letting any instance serve any
request — making the app tier stateless and horizontally scalable (no sticky sessions, no
loss on restart). Redis's speed keeps session lookups cheap, and TTLs give idle-timeout for
free.

**Q: How do you build a rate limiter with Redis?** ⭐⭐
Fixed window: `INCR rate:<user>`, set `EXPIRE` to the window on the first hit, allow while the
count ≤ limit — atomic and self-expiring. For smoother limits use a sliding window (sorted set
of timestamps) or token bucket (often via a Lua script). The check-and-increment must be
atomic so concurrent requests can't both slip through.

**Q: How do you implement a distributed lock in Redis?** ⭐⭐
`SET key owner NX PX <ttl>` — acquire only if absent, with a TTL so a crashed holder's lock
auto-releases. Release by deleting the key only if you still own it (compare the owner token
via a Lua script) so you don't delete someone else's lock after your TTL expired.

**Q: What are the pitfalls of Redis distributed locks?** ⭐
A single-node lock can be lost on failover; the multi-node Redlock algorithm exists but is
debated (Kleppmann's critique) for correctness under GC pauses/clock issues. Without fencing
tokens, an expired-then-reacquired lock can let two clients act. Use Redis locks for
best-effort mutual exclusion; for strict correctness add fencing or use etcd/ZooKeeper.

**Q: Why must you check ownership before releasing a lock?** ⭐
Because your lock may have expired (TTL) and been acquired by another client; a blind `DEL`
would release *their* lock. Comparing the stored owner token and deleting atomically (Lua)
ensures you only release a lock you still hold.

**Q: Redis Pub/Sub vs Kafka?** ⭐⭐
Redis Pub/Sub is fire-and-forget: no persistence, no replay, offline subscribers miss
messages — ideal for ephemeral fan-out (notifications, cache-invalidation). Kafka is a
durable, replayable, ordered log with consumer groups and retention — for event streaming,
event sourcing, and decoupled services. Transient signals → Pub/Sub; durable event backbone →
Kafka.

**Q: What are Redis Streams?** *nuance*
An append-only, persistent log data type with consumer groups and acknowledgments — a
Kafka-lite within Redis. Use it when you want durability/replay and consumer groups but don't
need Kafka's scale/retention, keeping everything in one Redis system.

**Q: How does Redis relate to idempotency?**
You can store an idempotency key (with a TTL) in Redis to detect and short-circuit duplicate
requests — check-and-set the key atomically; if it already exists, return the prior result
instead of reprocessing (System Design Phase 4). Its atomic ops and TTLs make it a natural
fit.
