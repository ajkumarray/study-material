<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 10 · observability](../phase-10-observability/NOTES.md)
<!-- /nav -->

# Phase 11 — Classic Design-Interview Problems: Notes

This phase applies the whole track. Every "design X" question uses the same framework, and a handful of canonical problems cover the recurring patterns. There's no single right answer — interviewers assess how you reason and trade off.

## The framework (use it every time)
1. **Clarify requirements** — *functional* (what it does) + *non-functional* (scale, latency, consistency, availability). Ask about scope; don't assume.
2. **Estimate** — QPS (read vs write), storage, bandwidth (Phase 1's back-of-envelope). These numbers justify your choices.
3. **API** — the key endpoints (method, params, response). Defines the contract.
4. **Data model** — entities, relationships, and the access patterns that drive schema/DB choice.
5. **High-level design** — boxes and arrows: clients → LB/gateway → services → cache → DB → queue. Get a working design first.
6. **Scale & deep-dive** — apply the toolkit where the bottlenecks are: cache (Phase 2), replicate/shard (Phase 3), load-balance/rate-limit (Phase 5), queue/async (Phase 9), resilience (Phase 6).
7. **Trade-offs & bottlenecks** — state what you optimized for and what you gave up (CAP choice, consistency vs latency, cost). This is what they're really grading.

## Worked example — a URL shortener (TinyURL)
- **Requirements:** shorten a long URL → short code; redirect short → long. Read-heavy (redirects ≫ creates). Low latency, high availability.
- **Estimate:** say 100M new URLs/day (~1,200 write QPS) and 100:1 read:write → ~120K read QPS. → cache + read replicas.
- **API:** `POST /shorten {url}` → `{shortUrl}`; `GET /{code}` → 301 redirect.
- **Key design decision — generating the code:** (a) hash the URL (MD5) and take the first 7 chars — risk of collisions, needs a check; (b) a **counter + base62 encode** — a global auto-increment id encoded to `[0-9a-zA-Z]` gives short, unique, collision-free codes (7 base62 chars ≈ 3.5 trillion). Distribute the counter with a range-allocation service (each server grabs a block of ids) or a unique-id generator (below).
- **Data model:** `code → long_url` — a simple key-value lookup → a KV store (or an indexed table). Perfect for caching (immutable mapping → cache-aside with long TTL; Phase 2).
- **Scale:** CDN/cache the hot redirects (immutable, so caching is easy), read replicas, shard by code. Redirects are a single indexed lookup → very fast.
- **Trade-offs:** 301 (permanent, cacheable by browsers — fewer hits but no click analytics) vs 302 (temporary — every click hits you, enables analytics).

## Worked example — a rate limiter (Phase 5, as a service)
Token bucket per user/API-key, counters in **Redis** (shared so the limit holds across all app servers), atomic increment + TTL (or a Lua script for correctness). Return 429 + `Retry-After` when exceeded. Trade-off: strict correctness (sync to Redis every call) vs performance (local approximate counters synced periodically).

## Worked example — a unique ID generator
Need unique, roughly time-ordered 64-bit ids at scale without a central bottleneck. Options: DB auto-increment (simple, single point/bottleneck), UUID (no coordination, but 128-bit and unordered), or **Snowflake** — `[timestamp | machine id | sequence]` — time-sortable, distributed, no coordination. Trade sortability vs coordination-freedom.

## Other canonical problems & their key ideas
- **News feed / timeline:** *fan-out-on-write* (push a new post into followers' precomputed feeds — fast reads, expensive for celebrities) vs *fan-out-on-read* (assemble the feed at read time — cheap writes, slower reads); real systems **hybrid** (push for normal users, pull for celebrities). Cache feeds; paginate.
- **Chat / messaging:** persistent connections (**WebSocket**) for real-time delivery; a message queue per user/room; store messages (wide-column DB for scale); presence and delivery/read receipts; ordering per conversation.
- **Notification system:** a queue + workers fanning out across channels (push/email/SMS) via provider adapters; idempotency (Phase 4) so retries don't double-send; rate limiting; user preferences; DLQ for failures.
- **Key-value store (Dynamo-style):** consistent hashing for partitioning (Phase 3), replication with quorum reads/writes (R+W>N), versioning/vector clocks for conflict resolution, gossip for membership — an AP system.
- **Web crawler, typeahead/autocomplete (trie + cache), payment system (idempotency + saga), video streaming (CDN + adaptive bitrate)** — each stresses a different subset of the toolkit.

## Meta-advice
Drive the conversation, state assumptions, start simple then scale, and always articulate **trade-offs**. Know the toolkit cold (from this track) so you can reach for the right piece: cache, replicate, shard, queue, load-balance, rate-limit, make-idempotent, add-a-circuit-breaker. The interviewer wants to see structured thinking and judgment, not a memorized diagram.
