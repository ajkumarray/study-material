<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching](../phase-2-caching/NOTES.md) | [Phase 4 · idempotency ➡](../phase-4-idempotency/NOTES.md)
<!-- /nav -->

# Phase 3 — Databases at Scale: Interview Q&A

⭐ = asked constantly.

**Q: Replication vs sharding — what does each scale?** ⭐⭐
Replication = copies of the same data on multiple nodes → scales **reads** and gives availability/failover. Sharding = split the data across nodes, each holding a subset → scales **writes** and storage. They're complementary; large systems do both (sharded, each shard replicated).

**Q: Leader-follower replication — how do reads and writes flow, and what's the catch?** ⭐
Writes go to the leader, which replicates to followers; reads can hit followers to scale. Catch: **replication lag** — a follower may not yet have a just-written value (stale reads / read-your-writes problem). Fix: read a user's own recent writes from the leader, or use synchronous replication for critical data.

**Q: Synchronous vs asynchronous replication?**
Sync: leader waits for follower acknowledgment before confirming — durable and consistent, but slower and less available (a slow follower stalls writes). Async: leader confirms immediately — fast, but the last writes can be lost if the leader fails before replicating. Semi-sync balances the two.

**Q: What is consistent hashing and what problem does it solve?** ⭐⭐
`hash(key) % N` remaps almost all keys when N changes (add/remove a node) — catastrophic reshuffling of data and caches (demo: 80% moved 4→5). Consistent hashing places nodes and keys on a ring; a key maps to the next node clockwise, so a node change moves only ~1/N of keys. Virtual nodes even out the load. Used by Cassandra, DynamoDB, Redis Cluster, CDNs.

**Q: How do you choose a shard key?** ⭐
Pick a key that (1) spreads load evenly (avoid hotspots — don't shard by sequential id or a celebrity-skewed field) and (2) co-locates data you query together (to avoid cross-shard queries). It's the most important and hardest-to-change sharding decision; a bad shard key forces expensive scatter-gather.

**Q: What's hard about sharding?**
Cross-shard joins/transactions/aggregations (slow scatter-gather or not supported), rebalancing when adding nodes (mitigated by consistent hashing), hotspots on a bad shard key, and increased operational complexity. Shard only when replicas + caching aren't enough.

**Q: SQL vs NoSQL — how do you choose?** ⭐⭐
SQL for strong consistency, ACID transactions, and complex/ad-hoc relational queries (payments, orders). NoSQL for massive scale, flexible/evolving schemas, or a specific access pattern — document (Mongo), key-value (Redis/Dynamo), wide-column (Cassandra, high write throughput), graph (Neo4j, relationships). Often eventually consistent and horizontally scalable by default. Many systems use both (polyglot persistence).

**Q: How do you scale a read-heavy database?**
In order: optimize queries + indexes → add caching (Redis/CDN) → add read replicas (route reads to followers) → denormalize / materialized views for expensive aggregates. Shard only if writes/storage exceed a single node.

**Q: How do you handle a hot partition/key?**
Change the shard key to spread load (hash, or append a random/bucketed suffix), cache the hot key in front of the store, or replicate that key across nodes. Detect via per-shard metrics.

**Q: What is polyglot persistence?**
Using multiple databases, each suited to an access pattern — e.g., Postgres for transactional data, Redis for cache/sessions, Elasticsearch for search, a columnar warehouse for analytics. Trade operational complexity for the right tool per job.
