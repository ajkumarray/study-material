<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · observability](../phase-10-observability/NOTES.md)
<!-- /nav -->

# Phase 11 — Classic Design-Interview Problems: Notes

This phase applies everything from Phases 1–10 to the actual questions asked in system-
design interviews. Every "design X" prompt uses the same framework (Phase 1.7), and a
handful of canonical problems cover the recurring patterns interviewers reach for
repeatedly. There is no single right answer to any of these — what's being assessed is
whether you reason from requirements and numbers to a justified design, and whether you
can articulate the trade-offs you made along the way.

## The framework, applied every time

1. **Clarify requirements** — functional (what must it do?) and non-functional (scale,
   latency, consistency, availability targets). Never assume scope; ask.
2. **Estimate** — QPS (read vs. write, average vs. peak), storage growth, bandwidth
   (Phase 1.6's back-of-envelope method). These numbers justify every decision that
   follows.
3. **API** — the concrete endpoints (method, parameters, response shape). Forces the
   data flowing in and out to be nailed down precisely.
4. **Data model** — entities, relationships, and crucially the *access patterns* that
   should drive schema and database choice (Phase 3.6).
5. **High-level design** — boxes and arrows: clients → load balancer/gateway → services
   → cache → database → queue. Get something working end-to-end before optimizing
   further.
6. **Scale and deep-dive** — apply the toolkit specifically where the actual bottlenecks
   are: cache (Phase 2), replicate/shard (Phase 3), load-balance/rate-limit (Phase 5),
   add resilience (Phase 6), go async (Phase 9).
7. **Trade-offs and bottlenecks** — explicitly state what you optimized for and what you
   gave up (a CAP choice, consistency vs. latency, cost vs. simplicity). This is
   frequently what's actually being graded.

## Worked example — a URL shortener (TinyURL)

### Requirements and estimation

- **Functional**: shorten a long URL into a short code; redirecting the short code back
  to the original long URL.
- **Non-functional**: heavily read-skewed (redirects vastly outnumber creates — every
  short link is created once but potentially clicked thousands of times), needs low
  redirect latency and high availability (a broken shortener breaks every link that's
  ever been shared with it).
- **Estimate**: say 100M new URLs created per day → `100M / 86,400 ≈ 1,157` write QPS
  average. With a 100:1 read:write ratio (a conservative estimate — many shortened links
  get clicked far more than 100 times, but this establishes the order of magnitude), that's
  roughly `115,700` read QPS average, and — using Phase 1.6's ~3x peak multiplier —
  roughly `347,000` read QPS at peak. Storage: at ~500 bytes per record (short code, long
  URL, metadata), `100M * 500 bytes ≈ 50GB/day`, or roughly 18TB/year — large but
  entirely manageable with normal database storage, not itself a forcing function for
  sharding. **The read:write ratio is the single number that drives the whole design**:
  this system should spend essentially all of its engineering effort on making redirects
  fast and cacheable, and comparatively little on the write path.

### API design

```
POST /shorten { "url": "https://example.com/very/long/path?query=1" }
  -> { "shortUrl": "https://tny.co/aZ9k2" }

GET /{code}
  -> HTTP 301 or 302 redirect to the original long URL
```

### The key design decision: generating the short code

Two standard approaches, worth being able to argue both sides of:

- **(a) Hash the URL (e.g., MD5) and take the first 7 characters.** Deterministic (the
  same URL always produces the same code, which is arguably a feature for deduping
  identical submissions), but introduces a real **collision risk**: two different URLs
  can hash to the same truncated prefix, which must be detected (check before insert)
  and handled (append characters, or retry with a salt) — this is exactly the
  check-then-insert race from Phase 4.1, and needs the same fix (a unique constraint on
  the code column).
- **(b) A global counter, base62-encoded.** A monotonically increasing id (a database
  auto-increment, or a distributed id generator) is encoded into base62
  (`[0-9a-zA-Z]`, 62 symbols) — 7 base62 characters give `62^7 ≈ 3.5 trillion` unique
  codes, comfortably enough headroom, with **zero collision risk by construction** since
  each id is unique before encoding. The challenge is generating the counter itself
  without a single centralized bottleneck at real scale — solved either by a
  range-allocation service (each app server claims a block of, say, 1,000 ids at a time
  from a coordinator, then hands them out locally without a network round trip per
  request) or a dedicated distributed id generator (below).

### Data model

`code (primary key) → long_url`, plus optional metadata (created_at, expiry, click
count). This is a simple, immutable key-value lookup once created — exactly the access
pattern a key-value store (Phase 3.6) is built for, and, because the mapping never
changes after creation, an ideal fit for aggressive **cache-aside caching with a long
TTL** (Phase 2.1) or even no TTL at all combined with explicit invalidation only on
deletion.

### Scaling the design

- **Cache the hot redirects** (Phase 2) — since `code → url` is immutable, cache-aside
  with a long TTL is safe and highly effective; a CDN can even cache the redirect
  response itself at the edge for extremely popular short links.
- **Read replicas** (Phase 3.1) scale the remaining, cache-missed read traffic
  horizontally.
- **Shard by code** (Phase 3.3) if write volume or total storage eventually exceeds a
  single (replicated) database's capacity — hashing the code as the shard key spreads
  load evenly, since codes are essentially random regardless of generation method.
- Redirects are a single indexed point lookup — inherently fast even without heavy
  optimization, which is part of why this system scales comfortably with a relatively
  simple architecture.

### Trade-offs worth naming explicitly

- **301 (permanent) vs. 302 (temporary) redirect**: a 301 tells the browser it can cache
  the redirect itself, so *subsequent* clicks on the same link from the same browser
  never even hit your server again — fewer requests to handle, but you lose click
  analytics on cached hits. A 302 tells the browser not to cache it, so every single
  click hits your server — more load, but complete click-through analytics. Whether
  click analytics matters to the product is exactly the kind of clarifying question from
  step 1 that determines this choice.

## Worked example — a rate limiter (as a standalone service)

Building directly on Phase 5.5's token-bucket algorithm, but now as a distinct,
shared service rather than in-process logic:

- **Design**: a token bucket per rate-limited dimension (per user, per API key), with
  bucket state stored centrally in **Redis** so the limit is enforced consistently
  across every app server in the fleet, not per-server (which would let a limit of
  "100/min" effectively become "100/min × N servers" if enforced locally).
- **Implementation detail**: use an atomic Redis increment-with-TTL, or a Lua script for
  a genuinely atomic multi-step check-and-decrement (checking remaining tokens and
  consuming one must be one atomic operation to avoid the exact check-then-act race from
  Phase 4.1, applied here to rate-limit tokens instead of a database row).
- **Response**: return **429 Too Many Requests** with a `Retry-After` header (Phase
  5.5) when the limit is exceeded.
- **Trade-off**: strict correctness (every single request synchronously checks/decrements
  in Redis — accurate, but adds a network round trip to every request and puts load on
  Redis proportional to total request volume) vs. performance (each app server keeps a
  local, approximate count and periodically syncs with the central Redis count — much
  less Redis load and no added per-request latency, at the cost of the limit being
  approximately, not exactly, enforced — a burst could briefly exceed the nominal limit
  by a bounded amount before the local counters resync).

## Worked example — a unique ID generator

The need: generate unique, ideally roughly time-ordered, identifiers at scale, without a
single centralized bottleneck issuing every id.

- **Database auto-increment**: simplest option — but the database itself becomes both a
  single point of failure and a write bottleneck for id generation specifically, on top
  of whatever else it's doing.
- **UUID**: no coordination required at all — any node can generate a UUID
  independently with essentially zero collision probability — but at 128 bits it's
  twice the size of a 64-bit id, and standard UUIDs (v4) are randomly ordered, which is
  bad for database index locality (inserting randomly-ordered keys into a B-tree index
  causes more page splits and worse cache locality than inserting roughly sequential
  keys).
- **Snowflake-style ids** (as pioneered by Twitter): a 64-bit id composed of
  `[timestamp | machine/worker id | sequence number]` bit fields. Time-sortable (ids
  roughly increase with creation time, since the timestamp is the high-order bits),
  generated independently by each machine with no coordination needed between machines
  (each machine's own id is unique by construction because of its distinct machine-id
  field), and compact at 64 bits. The trade-off against UUID: you gain sortability and
  compactness, at the cost of needing to assign and track unique machine/worker ids
  across your fleet (a small coordination requirement, but far lighter than coordinating
  every individual id generation).

### Comparison table — ID generation strategies

| | DB auto-increment | UUID (v4) | Snowflake |
|---|---|---|---|
| Coordination needed | Centralized (the DB) | None | Minimal (unique machine id per node) |
| Size | Typically 32/64-bit | 128-bit | 64-bit |
| Time-sortable? | Yes (sequential) | No (random) | Yes (timestamp-prefixed) |
| Single point of failure? | Yes | No | No |
| Index locality | Good (sequential) | Poor (random) | Good (roughly sequential) |

## Other canonical problems and their key ideas

- **News feed / timeline** — **fan-out-on-write**: when a user posts, immediately push
  that post into every follower's precomputed feed (stored, e.g., as a list of post ids
  per user). Reads are then extremely fast (just read the precomputed list), but writes
  become expensive in proportion to follower count — a celebrity with 50 million
  followers triggers 50 million feed writes for a single post. **Fan-out-on-read**:
  instead, assemble a user's feed at read time by querying all the accounts they follow
  and merging — writes stay cheap (one post, one write), but reads become expensive,
  especially for a user following many accounts. **Hybrid** (what real large-scale
  systems, including Twitter, actually do): fan-out-on-write for the vast majority of
  normal users, but fan-out-on-read specifically for celebrity accounts above some
  follower-count threshold, avoiding the fan-out-on-write "write amplification" problem
  for exactly the accounts where it would be worst. Cache assembled feeds (Phase 2), and
  paginate with keyset pagination (Phase 8.2) rather than large offsets.
- **Chat / messaging system** — persistent bidirectional connections (**WebSocket**, or
  long-polling as a fallback) for real-time message delivery, since HTTP's normal
  request/response model isn't built for a server pushing unsolicited messages to a
  client. A message service backed by storage optimized for the access pattern (a
  wide-column store like Cassandra fits "append messages to a conversation, read the
  most recent N" extremely well at scale, Phase 3.6). A queue or pub/sub layer (Phase 9)
  fans a message out to all of a conversation's currently-connected participants, and
  handles offline delivery (queued until the recipient reconnects). Presence tracking
  (who's online), message ordering per conversation (Phase 9.4's partition-key idea,
  applied per conversation id), and delivery/read receipts round out the feature set.
  Scaling connections specifically (millions of long-lived WebSocket connections) is its
  own concern, typically handled by a dedicated connection-server tier backed by a
  shared pub/sub layer that routes messages to whichever connection server currently
  holds a given user's socket.
- **Notification system** — producers (any service with something to notify a user
  about) enqueue a notification request; workers dequeue and fan it out across the
  user's preferred channels (push, email, SMS) via per-channel provider adapters
  (Firebase for push, an email API, an SMS gateway). **Idempotency** (Phase 4) is
  essential here specifically because retries are expected in this kind of pipeline, and
  a retry must not double-send the same notification to the user. Respect user
  preferences (channel opt-outs, quiet hours) and rate limits (don't flood a user), use
  templating for consistent message formatting, and route persistently-failing sends to
  a dead-letter queue (Phase 6.5, Phase 9.3) for inspection rather than silently dropping
  them or retrying forever.
- **Distributed key-value store (Dynamo-style)** — **consistent hashing** (Phase 3.4)
  partitions keys across nodes without a catastrophic reshuffle on membership changes;
  **replication with quorum reads/writes** (`R + W > N`, Phase 3.2) gives tunable
  consistency without a single leader; **versioning/vector clocks** (or simpler
  last-write-wins) resolve conflicts from concurrent writes across replicas; **gossip
  protocols** let nodes learn about cluster membership changes without a centralized
  coordinator; **hinted handoff** lets a write destined for a temporarily-unreachable
  node be held by another node and delivered once the target recovers, preserving
  availability during a transient failure. Overall an explicitly **AP** system (Phase
  1.5) — trading strict consistency for high availability and low latency, by design.
- **Web crawler, typeahead/autocomplete, payment system, video streaming** — each of
  these stresses a different, specific subset of the toolkit: a web crawler is
  fundamentally a large-scale, politeness-respecting BFS/queue-based traversal problem
  with deduplication (a Bloom filter, Phase 2.8, to cheaply check "have I already queued
  this URL?"); typeahead/autocomplete is a **trie** data structure serving prefix
  queries, aggressively cached (Phase 2) since the same few prefixes get queried
  constantly; a payment system is fundamentally an **idempotency-and-saga** problem
  (Phase 4) layered under whatever UI sits on top; video streaming leans almost entirely
  on **CDN delivery** (Phase 2.7) plus **adaptive bitrate** (serving different quality
  encodings of the same video depending on the viewer's measured bandwidth, switching
  dynamically as conditions change).

## Meta-advice for the interview itself

Drive the conversation yourself rather than waiting to be led — state your assumptions
out loud as you make them, start with a simple, working design before optimizing any
part of it, and consistently articulate the trade-off behind each significant decision
rather than presenting it as the single obviously-correct choice. Know the toolkit built
across Phases 1–10 well enough to reach for the right specific piece under pressure:
cache, replicate, shard, queue, load-balance, rate-limit, make-idempotent, add-a-
circuit-breaker, instrument-for-observability. The interviewer is assessing structured
engineering judgment under ambiguity, not whether you've memorized one specific
"correct" diagram for "design Twitter" — the same underlying toolkit, applied with
different emphasis based on the specific requirements and numbers for that prompt, is
what a strong answer to almost any of these problems actually looks like.

## Summary / Key Takeaways

- Every "design X" question uses the same seven-step framework: **clarify → estimate →
  API → data model → high-level design → scale/deep-dive → trade-offs**. The framework
  itself is more valuable to have memorized than any specific system's diagram.
- **URL shortener**: read-heavy KV mapping; generate codes via **base62-encoded counter**
  (no collisions, needs a range-allocation service to avoid a bottleneck) or
  **hash+collision-check** (simpler, needs Phase 4.1's unique-constraint handling); cache
  aggressively since the mapping is immutable; **301 vs. 302** trades caching efficiency
  against click analytics.
- **Rate limiter as a service**: token bucket (Phase 5.5) with state centralized in
  **Redis** so the limit holds across the whole fleet; trade strict per-request
  correctness against local-approximate-counter performance.
- **Unique ID generation**: DB auto-increment (simple, but a SPOF/bottleneck), UUID (no
  coordination, but large and unordered), or **Snowflake** (`timestamp | machine id |
  sequence` — compact, time-sortable, minimal coordination) — the standard answer for
  distributed systems that need sortable ids without a central generator.
- **News feed** hinges on **fan-out-on-write vs. fan-out-on-read**, with real systems
  hybridizing (push for normal users, pull for celebrities) to avoid write amplification
  at the extremes; **chat** hinges on WebSockets + pub/sub fan-out + per-conversation
  ordering; **notifications** hinge on idempotent, multi-channel, DLQ-backed async
  fan-out; a **Dynamo-style KV store** hinges on consistent hashing + quorum replication
  as a deliberately AP system.
- Across every one of these problems, the graded skill is the same: **reason from
  requirements and numbers to a design, and explicitly justify the trade-offs** — there
  is no single correct diagram, only a well-argued one.
