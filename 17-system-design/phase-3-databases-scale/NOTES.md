<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching](../phase-2-caching/NOTES.md) | [Phase 4 · idempotency ➡](../phase-4-idempotency/NOTES.md)
<!-- /nav -->

# Phase 3 — Databases at Scale: Notes

When one database node can't handle the load or data volume, you scale it. The two axes: **replication** (copies for read scaling + availability) and **partitioning/sharding** (splitting data for write scaling + capacity).

## 3.1 — Replication
Keep multiple copies of the data on different nodes.
- **Leader–follower (primary–replica):** writes go to the leader, which streams changes to followers; reads can be served by followers. Scales **reads** and provides failover (promote a follower if the leader dies). The dominant model.
- **Replication lag:** followers are slightly behind → a read after a write may not see it ("read-your-writes" problem). Fixes: read from the leader for a user's own recent writes, or use synchronous replication for critical paths (slower).
- **Sync vs async:** synchronous = the leader waits for followers to confirm (durable, consistent, slower — CP-leaning); asynchronous = leader confirms immediately (fast, risk of losing the last writes on failover — AP-leaning). Semi-sync is a middle ground.
- **Multi-leader / leaderless (Dynamo-style, quorum R+W>N):** accept writes on multiple nodes for availability/geo-distribution, at the cost of conflict resolution (last-write-wins, vector clocks, CRDTs). AP systems.

## 3.2 — Partitioning & sharding
Split one dataset across nodes so each holds a subset. Scales **writes** and storage beyond a single machine (each shard is independent).
- **Strategies:** *range* partitioning (by key ranges — great for range scans, but risks hotspots on sequential keys), *hash* partitioning (hash the key → even spread, but no range queries), *directory/lookup* (a lookup service maps keys→shards — flexible, adds a dependency), and *geographic*.
- **The rebalancing problem & consistent hashing:** naive `node = hash(key) % N` remaps ~all keys when N changes (demo: 80% moved going 4→5 nodes) — catastrophic. **Consistent hashing** places nodes and keys on a ring; a key goes to the next node clockwise, so adding/removing a node moves only ~1/N of keys (demo: 2/6). **Virtual nodes** (many ring points per physical node) even out load and smooth rebalancing. Used by Cassandra, DynamoDB, Redis Cluster, CDNs, distributed caches.
- **Hotspots / hot keys:** a celebrity user or sequential id concentrates load on one shard. Mitigate with a better shard key (hash, or key + random suffix), or caching the hot key.
- **Cross-shard pain:** joins, transactions, and aggregations across shards are hard/slow — you denormalize, scatter-gather, or avoid them. Choosing a **shard key** that keeps related data together (and spreads load) is the critical design decision.

## 3.3 — Reads, denormalization, SQL vs NoSQL
- **Indexing & denormalization for reads** (Databases Phase 4): indexes speed lookups; denormalizing (duplicating data to avoid joins) and **materialized views** trade write cost/storage for fast reads — common in read-heavy systems.
- **SQL vs NoSQL — the decision:**
  - **SQL / relational** — strong consistency, ACID transactions, flexible ad-hoc queries and joins, a fixed schema. Choose for correctness-critical, relational, transactional data (payments, orders). Scales via replicas + sharding (more effort).
  - **NoSQL** — chosen for scale, flexible/evolving schema, or a specific access pattern: **document** (Mongo — nested JSON, per-document), **key-value** (Redis/Dynamo — simple, fast), **wide-column** (Cassandra — huge write throughput, time-series), **graph** (Neo4j — relationship traversals). Typically AP/eventually consistent, horizontally scalable by design, limited joins/transactions. (Depth: Databases track 04.)
  - **Polyglot persistence:** real systems use several — e.g., Postgres for orders, Redis for sessions/cache, Elasticsearch for search, a warehouse for analytics. Pick the store per access pattern.

**The scaling ladder** (apply in order): optimize queries + add indexes → add a cache → add read replicas → shard/partition. Don't shard until you must — it adds permanent complexity.
