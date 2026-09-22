<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring integration](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 4 — Sessions, Locks, Rate Limiting & Pub/Sub: Interview Q&A

⭐ = asked constantly.

**Q: Why store sessions in Redis instead of the application server's memory?** ⭐⭐
In-memory sessions pin a user to whichever instance first handled their login — a load
balancer has to route every subsequent request from that user to the exact same instance
("sticky sessions"), which undermines even load distribution and means an instance restart or
deploy silently logs every user on it out. Moving session state into Redis (Spring Session,
`spring.session.store-type: redis`) means any instance can serve any user's request, because
the state that matters lives outside any single process. That's what makes the app tier
genuinely **stateless**, which is the property horizontal scaling, rolling deploys, and
autoscaling (Kubernetes, track 14) all depend on. Redis's speed keeps the added round trip for
a session read/write cheap, and the session gets an idle-timeout TTL for free — Redis expires
it itself, with no separate cleanup job.

*Follow-up: what actually changes in application code to move from in-memory to Redis-backed
sessions?* With Spring Session, essentially nothing — code still reads/writes the standard
`HttpSession` API. Spring Session transparently backs that API with Redis once
`store-type: redis` is configured; the session-handling code is unaware of where the data
physically lives.

**Q: How do you build a rate limiter with Redis?** ⭐⭐
The simplest version is a fixed window: `INCR rate:<user>` on every request, and on the very
first hit in a window (`count == 1`) set an `EXPIRE` for the window length; allow the request
while the returned count is at or below the limit. `INCR` is atomic, so concurrent requests
from the same client can't race past each other — each one sees a distinct, correct count. The
fixed-window approach has a known boundary flaw: a client can send up to the limit right before
a window boundary and up to the limit again right after, getting roughly double the intended
rate across that boundary. A sliding window fixes this using a sorted set of request
timestamps (`ZADD` to record, `ZREMRANGEBYSCORE` to drop anything older than the window,
`ZCARD` to count what's left), giving a smoothly rolling limit at the cost of a bit more work
per request. Token bucket, which allows controlled bursts while enforcing a steady average
rate, is the typical production choice and is usually implemented as an atomic Lua script via
`EVAL`, since it needs to read the current token count, consume one, and write the result back
as a single indivisible step.

*Follow-up: why must the check-and-increment be atomic?* If checking "are we under the limit"
and incrementing the counter were two separate round trips, two concurrent requests could both
read "3 out of 5, still under the limit" before either one's increment becomes visible to the
other — letting both through when the limiter should have blocked one. A single atomic command
(`INCR`) or Lua script closes that race.

**Q: How do you implement a distributed lock in Redis?** ⭐⭐
`SET key owner NX PX <ttl>` — the `NX` flag makes the set succeed only if the key doesn't
already exist, so the "check if it's free" and "claim it" happen as one atomic server-side
operation with no window for a race; `PX ttl` attaches an expiry so a crashed or hung holder's
lock auto-releases instead of deadlocking every other client permanently. The stored value
should be a unique owner token (a UUID or worker ID), not a constant, because that's what makes
safe release possible: you compare the stored value against your own token before deleting, and
only delete if they still match. That compare-then-delete has to be atomic too — typically a
small Lua script executed via `EVAL` that does the `GET`, compare, and `DEL` as one server-side
step, since doing it as two separate client round trips reopens the same kind of race the lock
was supposed to prevent.

**Q: What are the pitfalls of Redis distributed locks — what would you say if an interviewer
pushed on "is this actually safe"?** ⭐
A single-node lock isn't perfectly safe across a failover: if the node holding the lock key
fails over to a replica before that write is fully propagated, the lock can briefly appear
"gone," letting a second client acquire it while the first still believes it holds it. Redis's
own proposed fix for stronger guarantees, Redlock (acquiring the same lock across a majority of
independent Redis nodes), is genuinely debated — Martin Kleppmann's well-known critique argues
it isn't safe under real clock drift and long GC/process pauses, because a client can hold a
lock it doesn't realize has already expired and act on stale assumptions. The rigorous fix for
correctness-critical resources is a fencing token: each lock acquisition returns a monotonically
increasing number, and the protected resource itself rejects any write presented with an
outdated token — so even if two clients briefly both think they hold the lock, only the one
with the newer token is honored. The honest summary: Redis locks are good for best-effort mutual
exclusion (avoid duplicate background work), not for guarding something catastrophic (like
moving money) without adding fencing or reaching for a consensus-based system (ZooKeeper, etcd).

**Q: Why must you check ownership before releasing a lock, instead of just calling `DEL`?** ⭐
Because your lock may have already expired via its TTL — a slow GC pause, a long network
hiccup, or just underestimating how long the critical section would take — and a *different*
client may have since acquired it. A blind `DEL` at that point releases *their* lock, not
yours, which reopens the resource to a third client while the second one still believes it's
protected. Comparing the stored owner token to your own before deleting, atomically via a Lua
script, ensures you only ever release a lock you can prove you currently hold.

**Q: Redis Pub/Sub vs Kafka — when do you reach for which?** ⭐⭐
Redis Pub/Sub is fire-and-forget: `PUBLISH` sends a message to whoever is currently subscribed
to that channel, and if nobody is listening at that instant, the message is simply gone — no
persistence, no history, no replay for a subscriber that connects a moment later. That makes it
ideal for transient signals you're fine occasionally losing — live notifications, presence, and
cache-invalidation broadcasts. Kafka is the opposite end of the spectrum: a durable, ordered
(per-partition), replayable log with consumer groups, offset tracking, and long configurable
retention, built for cases where losing a message or being unable to replay history is
unacceptable — event sourcing, audit trails, decoupled service-to-service event pipelines with
independent consumers reading at their own pace. The rule stated plainly: transient signals →
Pub/Sub; the durable event backbone a system's architecture actually depends on → Kafka.

**Q: What are Redis Streams, and how do they compare to both Pub/Sub and Kafka?** *nuance*
Streams (`XADD`/`XREAD`/`XREADGROUP`) are a separate Redis data type: an append-only,
persistent log supporting consumer groups and per-message acknowledgment — meaningfully closer
to Kafka's capability set than Pub/Sub is, while staying inside the Redis deployment you already
operate rather than standing up a separate system. Choose Streams over Pub/Sub when you need
durability and replay but the scale doesn't warrant Kafka; choose Kafka over Streams when you
need Kafka's partitioning-driven throughput, very long retention, or an ecosystem of connectors
and consumer tooling built around it.

**Q: How does Redis relate to idempotency?**
An idempotency key — a client-supplied unique identifier for a specific logical operation
(e.g., "submit this order") — can be stored in Redis with a TTL to detect and short-circuit
duplicate requests, which commonly happen from client retries after a timeout where the first
attempt actually succeeded server-side. The check-and-set needs to be atomic (`SET
idempotency:<key> <result> NX EX <ttl>`, conceptually): if the key already exists, return the
previously stored result instead of reprocessing the operation a second time; if it doesn't,
claim it and proceed. Redis's atomic `SET ... NX` and TTL support make it a natural fit for this
pattern, which is a core building block covered more generally in System Design Phase 4.

**Q: Suppose a client holds a lock, its TTL expires mid-operation because the work took longer
than expected, and a second client acquires the lock and starts its own work — what actually
goes wrong, and how would you have prevented it?** *nuance*
This is the classic Redis-lock failure scenario: for a window of time, two clients both believe
they exclusively hold the lock and can both act on the protected resource concurrently, which
is exactly what the lock was supposed to prevent. Prevention isn't really about the lock
mechanism itself — a longer TTL just shrinks the window without eliminating it, and guessing a
"safe" TTL is fragile. The robust prevention is a fencing token: the resource being protected
rejects any write carrying an older token than the last one it accepted, so even though both
clients believe they hold the lock, only the one presenting the newest token actually succeeds
— converting "two clients think they have exclusive access" from a correctness bug into a
harmless no-op for the stale one.

**Q: Why is a rate limiter a good example of "atomicity is a feature, not an implementation
detail"?**
Because a rate limiter's entire correctness depends on the read (current count) and the write
(increment, and conditionally set the TTL) never being observably separated in time from a
concurrent caller's perspective. If you implemented it as application-level `GET` then compare
then `SET`, two simultaneous requests could both read a count just under the limit and both
proceed, silently letting through more traffic than the configured limit. Redis's single
command (`INCR`) — or a Lua script for anything more elaborate like token bucket — collapses
the read-modify-write into one atomic server-side step, which is precisely why Redis, rather
than an ad-hoc counter in application memory or a database row, is the standard tool for this
job in a multi-instance deployment.
