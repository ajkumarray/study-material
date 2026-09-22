<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · spring integration](../phase-3-spring-integration/NOTES.md)
<!-- /nav -->

# Phase 4 — Sessions, Locks, Rate Limiting & Pub/Sub: Notes

Beyond caching, Redis's combination of speed, single-threaded atomicity, and auto-expiring keys
makes it the tool of choice for **cross-instance coordination** — the patterns that let a
horizontally-scaled application behave correctly even though it's really N independent
processes with no shared memory. See `examples/DirectRedisExample.java` and
`examples/application.yml`.

## 4.1 — Distributed session storage

### Definition

A **session** is the server-side state tied to one user's ongoing interaction with a web app —
who they're logged in as, what's in their cart. **Distributed session storage** means that
state lives somewhere every instance of a horizontally-scaled app can reach, instead of being
pinned to whichever instance first handled that user's login.

### Key Concepts

- **The problem with in-process sessions:** if session state is held in one app instance's
  memory (the default with, e.g., Tomcat's in-memory `HttpSession`), a load balancer either has
  to pin that user to the same instance for every subsequent request ("sticky sessions" — which
  undermines load balancing and means one overloaded instance can't shed a sticky user to a
  quieter one), or the user gets silently logged out the moment a different instance handles
  their next request. A rolling deploy or an instance restart also just discards every
  in-memory session on that instance.
- **Redis-backed sessions (Spring Session)** move session state out of the app process and into
  Redis. Setting `spring.session.store-type: redis` (as in the `application.yml`) is enough for
  Spring Session to transparently back the standard `HttpSession` API with Redis — application
  code that reads/writes session attributes doesn't change at all.
- **This is what makes the app tier stateless.** "Stateless" doesn't mean the app has no state —
  it means *no state lives only in one instance's memory*. Any instance can now serve any
  request for any user, because the state that matters (the session) is externalized to a
  shared store. This is the exact property Kubernetes horizontal scaling (track 14) and rolling
  deploys depend on: instances can be added, removed, or restarted freely without users noticing.
- **Sessions get a TTL for free.** `spring.session.timeout: 30m` (the `application.yml`) maps
  onto Redis's expiry mechanism — an idle session simply expires and is garbage-collected by
  Redis itself, no separate cleanup job required.
- **This is the textbook "make a scaled-out app actually work" use of Redis** — the same
  principle that motivates externalizing any per-request state that would otherwise couple a
  client to one specific server instance.

### Worked example — conceptually, what changes

```
# In-memory sessions:  Client -> LB -> [instance A: session in RAM]
#                       Client's NEXT request must hit instance A again, or it's lost.

# Redis-backed sessions: Client -> LB -> [any instance] -> Redis (shared session store)
#                        Any instance can serve any request for this user.

HGETALL spring:session:sessions:a1b2c3...     # Spring Session's actual storage shape:
# 1) "sessionAttr:userId"
# 2) "42"
# 3) "maxInactiveInterval"
# 4) "1800"
# ...
TTL spring:session:sessions:a1b2c3...
# (integer) 1742                               <- idle-timeout countdown, managed by Redis
```

In this example, Spring Session stores the session as a Redis hash keyed by session ID, with
individual session attributes as hash fields — so a single `HGETALL` reveals the whole
session's contents (useful for debugging), and the session's TTL is a plain Redis expiry that
counts down as the idle timeout, refreshed on each request that touches the session.

### Why it's useful

Without externalized sessions, horizontal scaling and stateless deploys are fundamentally
broken — you either sacrifice load-balancing flexibility (sticky sessions) or user experience
(random logouts on failover/restart). Redis-backed sessions solve this cheaply because session
reads/writes are small and Redis serves them in microseconds, so moving sessions out of process
memory doesn't meaningfully add latency.

## 4.2 — Rate limiting

### Definition

**Rate limiting** caps how many requests a given client (user, API key, IP) can make within a
time window, protecting a service from abuse, accidental overload, or a single noisy client
starving everyone else's capacity (System Design Phase 5).

### Key Concepts

- **Why Redis fits naturally:** the two operations a rate limiter needs — "atomically increment
  a counter" and "expire that counter after a window" — are exactly `INCR` and `EXPIRE`, both
  native, atomic Redis primitives. No external locking is required to make the check-and-
  increment race-free.
- **Fixed window** (the pattern in `DirectRedisExample.allowRequest`): `INCR rate:<user>` on
  every request; if this was the very first hit (`count == 1`), set an `EXPIRE` for the window
  length; allow the request while `count <= limit`. Simple and cheap, but has a boundary flaw:
  a client can send `limit` requests right at the end of one window and another `limit`
  requests right at the start of the next, giving it up to `2× limit` requests in a short span
  straddling the boundary.
- **Sliding window** — instead of one counter per fixed window, keep a sorted set of request
  timestamps per client: `ZADD rate:<user> <now> <now>` to record a hit, `ZREMRANGEBYSCORE
  rate:<user> -inf (now - windowMs)` to drop entries older than the window, `ZCARD rate:<user>`
  to count what's left and compare to the limit. This gives a smoothly rolling limit with no
  boundary burst, at the cost of a few more commands and a bit more memory per client.
- **Token bucket / leaky bucket** — model a bucket that holds up to N tokens, refilling at a
  steady rate; a request consumes a token if one's available, and is rejected otherwise. This
  allows controlled bursts (use up saved tokens quickly) while still enforcing a steady average
  rate. It's commonly implemented as a small **Lua script** run atomically via `EVAL`, since the
  bucket's "compute current tokens, consume one, write back" logic needs to happen as a single
  atomic step. This is the typical production-grade choice.
- **Atomicity is non-negotiable here:** the check ("are we under the limit?") and the increment
  must happen as one atomic operation (a single command, or a Lua script), or two concurrent
  requests can both read "under limit" before either one's increment is visible, letting both
  through when only one should have been allowed.

### Worked example — fixed window, by hand and in Spring

```
INCR rate:user42
# (integer) 1              <- first request in this window
EXPIRE rate:user42 60
# (integer) 1               <- 60s window starts now
INCR rate:user42
# (integer) 2               <- second request, still under a limit of e.g. 5
...
INCR rate:user42
# (integer) 6               <- 6th request: over the limit of 5, application rejects it
```

```java
// examples/DirectRedisExample.java
public boolean allowRequest(String userId, int limit, Duration window) {
    String key = "rate:" + userId;
    Long count = redis.opsForValue().increment(key);   // atomic INCR
    if (count != null && count == 1L) {
        redis.expire(key, window);                      // TTL set only on the first hit
    }
    return count != null && count <= limit;
}
```

In this example, `INCR` both creates the key (on the first call) and returns the new count
atomically, which is why checking `count == 1L` reliably identifies "this is the first request
in a fresh window" — no separate `EXISTS` check is needed, and there's no race between checking
existence and setting the TTL, because a single atomic command drives both decisions.

### Why it's useful

Rate limiting is what stands between a public API and a single misbehaving client (or bug in a
retry loop) taking the whole service down. Building it on Redis's atomic primitives means the
limiter itself can't be the source of a race condition — a real risk if you tried to implement
the same check-and-increment against an ordinary SQL table without very careful locking.

## 4.3 — Distributed locks

### Definition

A **distributed lock** coordinates exclusive access to a shared resource across multiple
independent processes/instances — e.g., ensuring only one worker in a fleet processes a
particular job at a time, even though any of them could pick it up.

### Key Concepts

- **Acquisition: `SET key owner NX PX ttl`.** `NX` ("not exists") means the command only
  succeeds if the key doesn't already exist — so it atomically both checks "is this free?" and
  claims it in one step, with no window for a second caller to sneak in between the check and
  the set. `PX ttl` (or `EX` for seconds) attaches an expiry, so if the holder crashes or hangs
  without ever releasing the lock, it **auto-releases** after the TTL instead of deadlocking
  every other worker forever.
- **The `owner` value matters — it's not just a placeholder.** Storing a unique token (a UUID,
  a worker ID) as the lock's value, rather than a constant like `"locked"`, is what makes safe
  release possible: you can later verify *you* are still the one who holds it.
- **Releasing safely requires a compare-and-delete, not a blind `DEL`.** If you just call `DEL
  lock:order99` unconditionally, you risk this sequence: your TTL expires while you're still
  "working" (a slow GC pause, a network hiccup), a *different* client acquires the now-free
  lock, and *then* your original code finally gets around to releasing — deleting a lock that
  now belongs to someone else. The fix is to read the stored owner, compare it to your own
  token, and only delete if they match — and that check-then-delete must itself be **atomic**,
  which in practice means a small **Lua script** (`EVAL`) that does the `GET`+compare+`DEL` as
  one indivisible server-side operation, since doing it as two separate client commands
  reintroduces exactly the race you're trying to close.
- **Known caveats (worth stating explicitly in an interview):**
  - A **single-node lock isn't perfectly safe across failover** — if the primary holding the
    lock key fails over to a replica before a write is acknowledged, a lock acquisition can be
    "lost" during the handover, letting two clients briefly believe they hold the same lock.
  - **Redlock** is Redis's own proposed multi-node algorithm for a stronger distributed lock
    (acquire the same lock on a majority of N independent Redis nodes), but it is genuinely
    **debated** in the distributed-systems community — Martin Kleppmann's well-known critique
    argues it isn't safe under real-world clock drift and long GC/process pauses, since a
    "held" lock can silently become invalid without anyone observing it happen.
  - **Fencing tokens** are the more rigorous fix for correctness-critical locking: the lock
    grants a monotonically increasing token on each acquisition, and the protected resource
    itself rejects any operation presented with an old token — so even if two clients briefly
    both *think* they hold the lock, only the one with the latest token's operation is accepted.
  - **The honest framing:** Redis locks are well-suited to **best-effort mutual exclusion**
    (avoid duplicate work, reduce contention) — not to guarding something where a double-execute
    would be catastrophic (moving money) without adding fencing, or reaching for a system built
    specifically for consensus (ZooKeeper, etcd) instead.

### Worked example

```
SET lock:order99 owner-abc NX EX 30
# OK                          <- acquired: key didn't exist, now set with a 30s TTL

SET lock:order99 owner-xyz NX EX 30
# (nil)                       <- a second caller's attempt fails: key already exists

# Safe release (conceptually — the actual comparison must run as one atomic Lua script):
GET lock:order99
# "owner-abc"                 <- confirm we still own it...
DEL lock:order99              # ...only then delete. (In real code: one EVAL, not two round trips.)
```

```java
// examples/DirectRedisExample.java
public boolean tryLock(String resource, String owner, Duration ttl) {
    Boolean ok = redis.opsForValue()
        .setIfAbsent("lock:" + resource, owner, ttl);   // SET key owner NX PX ttl
    return Boolean.TRUE.equals(ok);
    // Release: delete the key ONLY if you still own it (compare-and-delete via Lua)
    // to avoid releasing someone else's lock — see the ownership-check note above.
}
```

In this example, the second `SET ... NX` call returning `nil` is the entire mutual-exclusion
guarantee in action — Redis's single-threaded execution means the `NX` check and the write
happen as one atomic step server-side, so there is no way for two `tryLock` calls racing on the
same resource to both succeed.

### Why it's useful

Distributed locks are what let a horizontally-scaled fleet of workers safely share work items
without either duplicating effort or needing a heavier coordination service for every case.
Knowing the failure modes (failover races, Redlock's debated safety, why fencing tokens exist)
is what separates "I can write `SET NX`" from actually understanding when a Redis lock is
sufficient and when it isn't.

## 4.4 — Pub/Sub — and when to use Kafka instead

### Definition

**Pub/Sub** is a messaging pattern where publishers send messages to named channels and any
currently-subscribed clients receive them immediately; Redis implements this natively as
**fire-and-forget**: there is no persistence, no message history, and no delivery guarantee to
a subscriber that wasn't actively connected at publish time.

### Key Concepts

- **Mechanics:** a subscriber runs `SUBSCRIBE channel-name` and blocks, receiving any message
  published to that channel from that point on; a publisher runs `PUBLISH channel-name
  message`. There's no queue behind a channel — if zero subscribers are currently listening,
  the message is simply dropped.
- **What this is good for:** ephemeral signals where losing an occasional message doesn't
  matter and there's no need to replay history — live notifications, presence/typing
  indicators, and **cache-invalidation broadcasts** (e.g., "cache entry `expense:1` changed,"
  so every app instance's local cache, if any, can drop it).
- **What it's a poor fit for:** anything that needs guaranteed delivery, replay for a
  newly-started consumer, ordering guarantees across many producers, or long retention — pure
  Pub/Sub has none of these.
- **Redis Streams** is a separate, newer Redis data type (`XADD`/`XREAD`/`XREADGROUP`) that
  *does* provide durability: it's an append-only log within Redis, supporting consumer groups
  and per-message acknowledgment — meaningfully closer to Kafka in capability, while staying
  inside the same Redis deployment you already operate.
- **When to reach for Kafka (track 16) instead of either:** when you need a durable,
  replayable, strictly-ordered (per-partition), high-throughput event log with long retention
  and many independent consumer groups reading the same history at their own pace — event
  sourcing, cross-service event pipelines, audit logs. The rule of thumb stated plainly: **Redis
  Pub/Sub is for transient signals you're fine losing; Kafka is the durable event backbone you
  build a system's architecture around.**

### Worked example

```
# Terminal A (subscriber) — blocks, waiting for messages:
SUBSCRIBE expense-events
# Reading messages... (press Ctrl-C to quit)

# Terminal B (publisher):
PUBLISH expense-events '{"type":"created","id":1}'
# (integer) 1              <- return value = number of subscribers that received it

# If Terminal A had NOT been subscribed yet, PUBLISH would have returned 0
# and the message would simply be gone -- no queue, no replay, no history.
```

In this example, `PUBLISH`'s return value (the count of receiving subscribers) is the clearest
illustration of "fire-and-forget": it tells you *how many* clients got the message right now,
with no mechanism to deliver it to anyone who wasn't already listening.

### Why it's useful

Pub/Sub is the right tool exactly when you don't need the guarantees a durable log provides and
want the absolute simplest possible fan-out mechanism — no topic configuration, no consumer
group management, just "publish, anyone listening gets it." Recognizing when a requirement
("we need to replay the last hour of events for a newly deployed consumer") has quietly crossed
into needing Streams or Kafka instead is the practical skill an interviewer is testing for.

## Summary / Key Takeaways

- **Redis-backed sessions (Spring Session)** move session state out of per-instance memory,
  which is what makes an app tier genuinely stateless and safe to scale horizontally / deploy
  without logging users out.
- **Rate limiting** is built on `INCR` + `EXPIRE` (fixed window, simple but bursty at
  boundaries), a sorted-set-based sliding window (smoother, more work), or a Lua-scripted token
  bucket (allows controlled bursts, the common production choice) — the check-and-increment
  must be atomic.
- **Distributed locks** use `SET key owner NX PX ttl` to acquire; release must be a
  **compare-then-delete on the owner token**, done atomically (Lua), never a blind `DEL`.
  Single-node locks aren't failover-safe; Redlock is debated; fencing tokens are the rigorous
  fix for correctness-critical cases.
- **Redis Pub/Sub is fire-and-forget** (no persistence, no replay, offline subscribers miss
  messages) — good for ephemeral fan-out; **Redis Streams** adds durability/consumer-groups
  within Redis; **Kafka** is the durable, replayable, high-throughput event backbone when you
  need those guarantees at scale.
- All four patterns are really the same underlying trick applied differently: **exploit
  atomicity + TTL to coordinate state across many independent, stateless app instances.**
