<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · observability](../phase-10-observability/NOTES.md)
<!-- /nav -->

# Phase 11 — Design Problems: Interview Q&A

⭐ = asked constantly. These are *the* interview questions; practice narrating the
framework out loud, not just knowing the answer.

**Q: How do you approach any "design X" question?** ⭐⭐
A repeatable framework: clarify functional and non-functional requirements (scale,
latency, consistency, availability) → estimate scale (QPS read/write, storage,
bandwidth, using Phase 1.6's DAU-to-QPS method) → design the API (the concrete
endpoints and their contracts) → design the data model (entities and, critically, the
access patterns that should drive schema/database choice) → sketch the high-level
architecture (client → LB/gateway → services → cache → DB → queue) → scale it by
applying the toolkit specifically where the bottlenecks actually are (cache, replicate,
shard, rate-limit, go async, add resilience) → explicitly discuss trade-offs and
bottlenecks. State assumptions out loud throughout, start with something simple that
works end-to-end, then scale it deliberately. There's no single correct answer — what's
graded is whether your decisions are justified by the requirements and numbers you
stated, not whether you drew a memorized diagram.

**Q: Design a URL shortener.** ⭐⭐
It's a heavily read-skewed key-value mapping problem: `code → long_url`, where
redirects vastly outnumber creates. Estimate first — say 100M new URLs/day gives roughly
1,150 write QPS average, and with a conservative 100:1 read:write ratio, roughly 115,000
read QPS average (and around 3x that at peak) — the read-skew is the number that tells
you to spend your design effort on the read path. Generate codes either via a global
counter encoded in base62 (7 characters gives ~3.5 trillion unique codes with zero
collision risk, but needs a range-allocation scheme so the counter itself isn't a
bottleneck) or by hashing the URL and truncating (simpler, but needs explicit collision
detection — the same check-then-insert race as Phase 4.1, fixed the same way with a
unique constraint). Store the mapping in a KV store; since the mapping never changes
once created, cache it aggressively with cache-aside and a long TTL, and add read
replicas and sharding by code if write volume or storage eventually require it. Trade-off
worth naming: a 301 (permanent) redirect lets browsers cache it themselves, reducing your
server's load but losing click-through analytics on cached hits; a 302 (temporary)
redirect means every click hits your server, giving full analytics at higher load cost.

**Q: Design a news feed, like Twitter or Instagram's timeline.** ⭐⭐
The central trade-off is fan-out-on-write vs. fan-out-on-read. Fan-out-on-write
precomputes each follower's feed at post time — pushing the new post into every
follower's stored feed list — which makes reads extremely fast (just read the
precomputed list) but makes writes expensive in proportion to follower count, badly so
for a celebrity account with tens of millions of followers. Fan-out-on-read instead
assembles a user's feed at request time by querying everyone they follow and merging —
writes stay cheap regardless of follower count, but reads become expensive, especially
for users following many accounts. Real large-scale systems hybridize: fan-out-on-write
for the vast majority of normal-follower-count users, fan-out-on-read specifically for
celebrity accounts above some threshold, avoiding the worst case of either pure
strategy. Cache assembled feeds, and paginate with keyset pagination (Phase 8.2) rather
than large offsets for infinite scroll.

**Q: Design a rate limiter.** ⭐
Token bucket (Phase 5.5) per rate-limited dimension — per user or per API key — with the
bucket's state centralized in Redis, not kept per-app-server, so the limit is enforced
consistently across the whole fleet rather than effectively multiplying by server count.
The check-and-decrement needs to be atomic (an atomic Redis increment-with-TTL, or a Lua
script for a multi-step atomic operation) to avoid the same check-then-act race Phase
4.1 covers, applied here to rate-limit tokens. Return 429 with a `Retry-After` header
when exceeded. The trade-off to name explicitly: strict correctness (every request
synchronously checks Redis — accurate, but adds a network round trip and load
proportional to total traffic) versus performance (each server keeps a local
approximate count, periodically synced to Redis — far less load and latency, at the cost
of the limit being approximately rather than exactly enforced).

**Q: How do you generate unique IDs at scale, without a single bottleneck issuing every
id?** ⭐
Database auto-increment is simplest but makes the database a single point of failure and
a write bottleneck specifically for id generation. UUIDs need zero coordination between
nodes — each one is generated independently with negligible collision probability — but
are 128 bits (twice the size of a 64-bit id) and, as standard v4 UUIDs, randomly
ordered, which hurts database index locality on insert. Snowflake-style ids (Twitter's
approach) pack `[timestamp | machine/worker id | sequence number]` into 64 bits —
time-sortable (since the timestamp occupies the high-order bits), generated
independently per machine with no per-id coordination needed, and compact — at the cost
of needing to assign and track a unique machine id per node in your fleet, a much
lighter coordination requirement than coordinating every single id generation. Choose
based on whether you need sortability (favors Snowflake) and how much coordination
overhead you can tolerate (UUID needs none at all).

*Follow-up: why does sortability matter for a database id?* Inserting roughly
sequential keys into a B-tree index (as Snowflake or DB auto-increment ids do) keeps
new inserts clustered near the "end" of the index, minimizing page splits and keeping
recently-inserted rows physically close together on disk — good for cache locality and
range queries over recent data. Randomly-ordered keys (UUID v4) scatter inserts across
the entire index space, causing more page splits and worse locality.

**Q: Design a chat system.** ⭐
Real-time bidirectional delivery needs a persistent connection — WebSocket (or
long-polling as a fallback) — since normal HTTP request/response can't have the server
push an unsolicited message to a client. A message service persists conversation history
in storage suited to the access pattern (append messages to a conversation, read the
most recent N — a wide-column store like Cassandra fits this well at scale, Phase 3.6).
A queue or pub/sub layer (Phase 9) fans a new message out to all of a conversation's
currently-connected participants and handles offline delivery, holding the message until
a disconnected recipient reconnects. Presence (who's online), per-conversation message
ordering (using the conversation id as the partition key, exactly as Phase 9.4
describes), and delivery/read receipts round out the design. Scaling millions of
concurrent long-lived connections is its own specific concern, typically handled by a
dedicated connection-server tier backed by a shared pub/sub layer that routes a message
to whichever connection server currently holds the target user's socket.

**Q: Design a notification system.**
Producers — any internal service with something to notify a user about — enqueue a
notification request; workers dequeue and fan it out across the user's preferred
channels (push, email, SMS) via per-channel provider adapters. Idempotency (Phase 4) is
essential here specifically because retries are expected in this kind of pipeline, and a
retry must not double-send the same notification to a user. Respect user channel
preferences and rate limits (don't flood a user with notifications), use templating for
consistent formatting across channels, and route persistently-failing sends to a
dead-letter queue (Phase 6.5, Phase 9.3) for inspection rather than silently dropping or
endlessly retrying them. The whole system is fundamentally async and at-least-once with
consumer-side dedupe, exactly Phase 4.3's pattern applied to a specific product feature.

**Q: Design a distributed key-value store, Dynamo-style.**
Consistent hashing (Phase 3.4) partitions keys across nodes so membership changes only
move roughly `1/N` of the data instead of a catastrophic reshuffle. Replication with
quorum reads/writes (`R + W > N`, Phase 3.2) gives tunable consistency without a single
leader — any replica can accept a read or write. Concurrent writes to the same key
across replicas are reconciled via versioning/vector clocks (or simpler last-write-wins)
for conflict resolution. Gossip protocols let nodes learn about cluster membership
changes without a centralized coordinator. Hinted handoff lets a write destined for a
temporarily-unreachable node be accepted by another node and delivered once the target
recovers, preserving write availability during a transient failure. The overall system
is explicitly AP (Phase 1.5) — it trades strict, always-consistent reads for high
availability and low latency by design, which is the whole point of the Dynamo
architecture as opposed to a leader-based, CP-leaning store.

**Q: A design question mentions "millions of concurrent users." What specifically
changes in your answer versus a smaller-scale version of the same system?**
Emphasize horizontal scale at every layer rather than treating it as an afterthought:
stateless services behind load balancers (Phase 5.3) so the fleet can grow freely,
aggressive caching plus a CDN (Phase 2) to keep the vast majority of traffic from
reaching the database at all, database read replicas and, if write volume genuinely
requires it, sharding (Phase 3), async processing for anything not required in the
immediate response path (Phase 9), and rate limiting to protect the system from both
legitimate spikes and abuse (Phase 5.5). Back the whole design with actual
back-of-envelope numbers (Phase 1.6) to justify component counts rather than gesturing
vaguely at "it scales" — and explicitly call out the CAP/consistency trade-off (Phase
1.5) you're making, since at genuine multi-million-user scale that choice becomes load-
bearing rather than a theoretical footnote.

**Q: The interviewer keeps asking "what if this component fails?" throughout the
design — what are they testing, and how should you respond?**
Resilience thinking end to end (Phase 6): systematically identifying single points of
failure and adding redundancy for them, putting timeouts, retries with backoff, and
circuit breakers between services rather than assuming every call succeeds, having an
explicit graceful-degradation story for non-critical dependencies, and ensuring writes
are idempotent (Phase 4) so that retries triggered by a failure are actually safe to
make rather than risking duplicated side effects. The right response isn't a one-time
addendum at the end of the design — it's woven through the design from the start,
showing that you treat failure as the expected, normal condition of a distributed
system at scale rather than an edge case to bolt on if there's time left in the
interview.
