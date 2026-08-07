<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · postgres features](../phase-6-postgres-features/NOTES.md) | [Phase 8 · nosql foundations ➡](../phase-8-nosql-foundations/NOTES.md)
<!-- /nav -->

# Phase 7 — Production & Beyond Relational: Interview Q&A

⭐ = asked constantly.

**Q: How do you manage database schema changes across environments?** ⭐⭐
Versioned migration scripts run by a tool (Flyway/Liquibase) that records which have applied, so dev/CI/prod converge to the same schema. Migrations are immutable once applied (add a new one to fix), prefer additive/backward-compatible changes, and are the single source of truth — never hand-edit production schema.

**Q: How do you add a NOT NULL column to a large live table safely?** ⭐
Expand-then-contract: add the column nullable, deploy code that populates it, backfill existing rows in batches, then add the NOT NULL constraint — avoiding a long lock and keeping old/new app versions compatible during rollout.

**Q: Partitioning vs sharding?** ⭐⭐
Partitioning splits one table into pieces within a single database (by range/list/hash); the planner prunes irrelevant partitions and you can drop old data instantly. Sharding spreads data across multiple machines to scale writes/storage beyond one node, at the cost of cross-shard queries and rebalancing. Partitioning first; shard only when necessary.

**Q: What's the database scaling ladder?**
Optimize queries + indexes → add caching → add read replicas (scale reads) → partition big tables → shard across machines (scale writes/storage). Each rung adds complexity; climb only as load demands.

**Q: Logical vs physical backups; what is PITR?**
Logical (`pg_dump`) exports SQL/data — portable, slower to restore. Physical (base backup + WAL archiving) enables Point-In-Time Recovery — restore to any moment by replaying the write-ahead log. Always test restores. RTO/RPO set the strategy.

**Q: What is the WAL and what does it give you?** ⭐
The Write-Ahead Log records every change before it hits the data files. It provides durability (committed data survives crashes — ACID), crash recovery, streaming replication, and point-in-time recovery — one mechanism behind several guarantees.

**Q: Why use a connection pooler like PgBouncer?**
Postgres connections are heavyweight (a process each) and limited; opening one per request is slow and exhausts the server. A pooler multiplexes many client connections onto a few DB connections (and app-side HikariCP reuses connections), keeping the DB from being overwhelmed at scale.

**Q: What are the four NoSQL families and a use for each?** ⭐⭐
Document (MongoDB) — flexible nested records. Key-value (Redis/DynamoDB) — fast caching/sessions/counters. Wide-column (Cassandra) — huge write throughput, time-series. Graph (Neo4j) — relationship traversals (social, recommendations, fraud). Each optimizes a specific access pattern.

**Q: SQL vs NoSQL — how do you choose?** ⭐⭐
SQL for strong consistency, ACID transactions, and complex/ad-hoc relational queries (payments, orders). NoSQL for a specific access pattern, flexible schema, or scale beyond a single relational node — accepting eventual consistency and limited joins. Often both (polyglot). And Postgres with JSONB covers many "NoSQL" needs, so start relational unless a real requirement forces otherwise.

**Q: What is NewSQL?**
Databases (CockroachDB, Google Spanner, TiDB) that aim to combine SQL's relational model and ACID transactions with NoSQL-style horizontal scalability — distributed SQL. An option when you need both strong consistency and scale-out.
