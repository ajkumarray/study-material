<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · observability](../phase-10-observability/NOTES.md)
<!-- /nav -->

# Phase 11 — Design Problems: Interview Q&A

⭐ = asked constantly. These are *the* interview questions; practice narrating the framework.

**Q: How do you approach any "design X" question?** ⭐⭐
Framework: clarify functional + non-functional requirements → estimate scale (QPS, storage) → design the API → data model → high-level architecture → scale/deep-dive on bottlenecks (cache, replicate, shard, queue, LB) → discuss trade-offs. State assumptions, start simple, then scale. They grade your reasoning and trade-offs, not a single correct answer.

**Q: Design a URL shortener.** ⭐⭐
Read-heavy KV mapping (code→URL). Generate codes via a counter + base62 encoding (short, unique, collision-free) or hash+check. API: `POST /shorten`, `GET /{code}` → redirect. Store in a KV store; cache aggressively (mapping is immutable → cache-aside with long TTL, CDN); read replicas; shard by code. Trade-off: 301 (cacheable, no analytics) vs 302 (analytics, more load).

**Q: Design a news feed (Twitter/Instagram timeline).** ⭐⭐
Fan-out-on-write (precompute each follower's feed on post — fast reads, costly for celebrities) vs fan-out-on-read (build the feed at read time — cheap writes, slow reads). Hybrid: push for normal users, pull for celebrities. Cache feeds, paginate (keyset), rank. Discuss the write-amplification trade-off.

**Q: Design a rate limiter.** ⭐
Token bucket per user/API key; counters in Redis so the limit is global across servers; atomic increment + TTL (or Lua script). Return 429 + Retry-After. Trade-off: strict correctness (every call hits Redis) vs performance (local approximate counts synced periodically).

**Q: How do you generate unique IDs at scale?** ⭐
DB auto-increment (simple, but a bottleneck/SPOF), UUID (no coordination, 128-bit, unordered), or Snowflake — `timestamp | machineId | sequence` — 64-bit, time-sortable, distributed, coordination-free. Choose by whether you need sortability and how much coordination you can tolerate.

**Q: Design a chat system.** ⭐
WebSocket (or long-poll) for real-time bidirectional delivery; a message service + per-conversation storage (wide-column DB for scale); a queue/pub-sub for fan-out to participants and offline delivery; presence, ordering per conversation, delivery/read receipts. Scale connections with a connection-server tier + a pub/sub backbone.

**Q: Design a notification system.**
Producers enqueue notifications; workers fan out across channels (push/email/SMS) via provider adapters; idempotency so retries don't double-send (Phase 4); user preferences and rate limits; DLQ for failed sends; templating. Async and at-least-once with dedupe.

**Q: Design a distributed key-value store (Dynamo-style).**
Consistent hashing to partition (Phase 3), replication with quorum reads/writes (R + W > N) for tunable consistency, versioning/vector clocks (or LWW) for conflict resolution, gossip for membership, hinted handoff for temporary failures. An AP system — highly available, eventually consistent.

**Q: A design question mentions "millions of concurrent users." What changes in your answer?**
Emphasize horizontal scale everywhere: stateless services behind LBs, aggressive caching + CDN, DB read replicas and sharding, async processing for non-critical work, rate limiting, and back-of-envelope numbers to justify component counts. Call out the CAP/consistency choice explicitly.

**Q: The interviewer keeps asking "what if this component fails?" — what are they testing?**
Resilience thinking (Phase 6): identify SPOFs and add redundancy/failover, timeouts/retries/circuit breakers between services, graceful degradation for non-critical paths, and idempotency so retries are safe. Show you design for failure, not just the happy path.
