<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · mongodb](../phase-9-mongodb/NOTES.md) | [Phase 11 · neo4j ➡](../phase-11-neo4j/NOTES.md)
<!-- /nav -->

# Phase 10 — Redis: Interview Q&A

⭐ = asked constantly.

**Q: What is Redis and what makes it fast?** ⭐⭐
An in-memory data-structure store: data lives in RAM (with optional persistence), so operations are microsecond-fast, and commands execute single-threaded so each is atomic. It's more than key-value — values can be strings, hashes, lists, sets, sorted sets, streams.

**Q: What are the main Redis data types and a use for each?** ⭐⭐
String (counters, cached blobs), Hash (objects as field maps), List (queues/stacks), Set (unique membership, tags), Sorted Set (leaderboards, ranges, priority — the standout), Stream (event logs). Choosing the right structure for the access pattern is the core skill.

**Q: What is Redis most commonly used for?** ⭐
Caching (cache-aside in front of a database), sessions, counters/rate limiting, leaderboards (sorted sets), queues (lists), and pub/sub messaging. It's an auxiliary store in front of a primary DB, not usually the system of record.

**Q: How does TTL / expiry work and why does it matter?** ⭐
`EXPIRE`/`SET ... EX` auto-deletes a key after N seconds. It's the foundation of caching (bounded staleness), sessions (auto-logout), OTPs, and rate-limit windows. `TTL` checks remaining time; `PERSIST` removes expiry.

**Q: How do you cache with Redis, and how do you invalidate?** ⭐⭐
Cache-aside: on a read miss, load from the DB and `SET` the key with a TTL; serve from Redis on hits. Invalidate by deleting/updating the key when the source changes, and rely on TTL as a backstop. Configure `maxmemory` + an eviction policy so it evicts rather than OOMs.

**Q: Is Redis durable? Explain RDB vs AOF.**
It can be. RDB takes point-in-time snapshots (compact, fast restart, small data-loss window). AOF logs every write and replays it on restart (durable, larger). Use RDB for backups, AOF for durability, both, or neither for a pure cache.

**Q: Does Redis support transactions?**
Yes — `MULTI`/`EXEC` queue commands to run atomically without interleaving, and `WATCH` provides optimistic locking (abort if a key changed). For multi-step atomic logic, Lua scripts (`EVAL`) run server-side atomically. Note it's not full ACID with rollback semantics like a relational DB.

**Q: How would you build a rate limiter or leaderboard in Redis?** ⭐
Rate limiter: `INCR` a per-user key + `EXPIRE` for the window (or a Lua script/sorted-set sliding window). Leaderboard: a sorted set — `ZADD` scores, `ZREVRANGE` for the top N, `ZRANK` for a rank, `ZINCRBY` to update — all O(log n).

**Q: How does Redis scale and stay available?**
Sentinel provides automatic failover for a primary-replica setup; Redis Cluster shards data across nodes by hash slots for horizontal scale. Replicas also serve reads. It's single-threaded per node but you scale out with clustering.

**Q: Redis pub/sub vs a message queue like Kafka?**
Redis pub/sub is fire-and-forget: messages go only to currently-connected subscribers, with no persistence or replay. Kafka (and Redis Streams) persist messages and support replay/consumer groups. Use pub/sub for live, ephemeral fan-out; Kafka/Streams for reliable event pipelines.

**Q: When should Redis NOT be your primary database?**
When you need durable, complex, queryable business data with strong relational integrity and ACID transactions. Redis is memory-first and optimized for speed/simple structures — pair it with Postgres (source of truth) rather than replacing it.
