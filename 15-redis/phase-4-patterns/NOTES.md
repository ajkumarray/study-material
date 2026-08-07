<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring integration](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 4 — Sessions, Locks, Rate Limiting & Pub/Sub: Notes

Beyond caching, Redis's speed + atomicity + TTLs make it the tool for cross-instance
coordination. These are the patterns that let a horizontally-scaled app behave correctly. See
`examples/DirectRedisExample.java`.

## 4.1 — Distributed session storage

- A stateful web app stores session data (login, cart) somewhere. If it's **in-process
  memory**, a user is pinned to one instance ("sticky sessions") and a restart loses the
  session — both bad for scaling.
- **Redis-backed sessions** (Spring Session, `store-type: redis`) put session state in Redis
  so **any instance can serve any request**. This makes the app tier **stateless** (the
  property Kubernetes/scaling needs — track 14). Sessions get a TTL (idle timeout) for free.
- This is *the* classic "make a scaled-out app work" use of Redis.

## 4.2 — Rate limiting

- Protect APIs from abuse/overload by capping requests per client per window (System Design
  Phase 5). Redis is ideal because counters are **atomic** and keys **auto-expire**.
- **Fixed window** (the example): `INCR rate:<user>`; on the first hit set `EXPIRE` = window;
  allow while `count <= limit`. Simple, but bursty at window boundaries (2× limit across the
  edge).
- **Sliding window** — use a sorted set of request timestamps (`ZADD` now, `ZREMRANGEBYSCORE`
  to drop old, `ZCARD` to count) for a smooth rolling limit. More accurate, more work.
- **Token bucket / leaky bucket** — allow bursts up to a bucket size, refill at a rate;
  implementable with Lua for atomicity. The common production choice.
- Do the check-and-increment **atomically** (single command or a Lua script) so concurrent
  requests can't both slip under the limit.

## 4.3 — Distributed locks

- Coordinate exclusive access to a resource across instances (e.g. only one worker processes
  a job). **`SET key owner NX PX ttl`** — set only if absent (acquire), with a TTL so a
  crashed holder's lock **auto-releases** (no permanent deadlock). The example's `tryLock`.
- **Release safely:** delete the key **only if you still own it** — compare the stored owner
  token, ideally via a **Lua script** (atomic check-and-delete), or you might delete a lock a
  *different* client acquired after your TTL expired.
- **Caveats (say these in interviews):** a single-node lock isn't perfectly safe under
  failover; **Redlock** is the multi-node algorithm, and it's **debated** (Kleppmann's
  critique). For correctness-critical locking, add **fencing tokens** or use a system built
  for consensus (ZooKeeper/etcd). Redis locks are great for "best-effort mutual exclusion,"
  not for guarding money without fencing.

## 4.4 — Pub/Sub — and when to use Kafka instead

- **Redis Pub/Sub** — publishers `PUBLISH` to a channel, subscribers `SUBSCRIBE`; delivery is
  **fire-and-forget**: no persistence, no replay, no consumer groups. If a subscriber is
  offline, it **misses** the message. Great for **ephemeral** fan-out (live notifications,
  cache-invalidation signals, presence).
- **Redis Streams** — a newer, **durable, append-only log** with consumer groups and
  acknowledgments (closer to Kafka-lite) for when you need persistence within Redis.
- **When to reach for Kafka (track 16) instead:** durable, replayable, ordered, high-
  throughput event streaming with consumer groups and long retention — event sourcing, log
  pipelines, decoupled microservices. **Redis Pub/Sub = transient signals; Kafka = the
  durable event backbone.**

## Perspective

These patterns turn Redis into the **coordination layer** for a distributed app: shared
sessions make the app tier stateless (scaling), atomic counters enforce rate limits,
`SET NX` + TTL gives best-effort distributed locks (with fencing caveats), and Pub/Sub does
lightweight fan-out. Reach for each when you need *fast, shared, ephemeral* state or
signaling — and step up to Kafka when you need *durable, replayable* events.
