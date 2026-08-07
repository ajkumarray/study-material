<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · postgres features](../phase-6-postgres-features/NOTES.md) | [Phase 8 · nosql foundations ➡](../phase-8-nosql-foundations/NOTES.md)
<!-- /nav -->

# Phase 7 — Production & Beyond Relational: Notes (Theory)

Running a database in production, and the map of the wider database world. Several topics here were covered from the systems angle in **System Design Phase 3** (replication/sharding) — this is the database-track view, with the practical SQL in `01-migrations-partitioning.sql`.

## 7.1 — Migrations & schema evolution
Schemas change over time; you need a *safe, reproducible* way to evolve them. **Migration tools** (**Flyway**, **Liquibase**) apply **versioned, ordered scripts** and record which have run in a metadata table, so every environment converges to the same schema. Rules: migrations are **immutable once applied** (never edit an old one — add a new one), prefer **additive/backward-compatible** changes so old and new app versions coexist during a rollout (**expand-then-contract**: add column → deploy dual-writing code → backfill → switch reads → drop old later), and **never hand-edit prod schema**. In Spring Boot, Flyway runs migrations on startup — the production alternative to `ddl-auto=update` (Java Phase 7.3 / Spring Boot Phase 4).

## 7.2 — Scaling strategies
- **Replication** (System Design 3.1): leader–follower copies for read scaling + failover; mind replication lag and sync-vs-async trade-offs.
- **Partitioning** (in the SQL file): split one big table into physical pieces by RANGE/LIST/HASH of a key; the planner prunes irrelevant partitions (faster), and you can drop old data by dropping a partition (instant vs slow DELETE). Great for time-series/logs. This is *within one database*.
- **Sharding** (System Design 3.2): spread partitions across *machines* — scales writes/storage beyond one node, at the cost of cross-shard query pain and rebalancing (consistent hashing). A big architectural step; do it only when replicas + caching + partitioning aren't enough.
- **The scaling ladder:** optimize queries + indexes (Phase 4) → cache (Redis) → read replicas → partition → shard. Each step adds complexity; climb only as needed.

## 7.3 — Backups, pooling, operations
- **Backups & recovery:** logical (`pg_dump`/`pg_restore` — portable, slower) vs physical (base backup + **WAL archiving** for **Point-In-Time Recovery** — restore to any moment). Test restores (an untested backup isn't a backup). Recovery objectives **RTO/RPO** (System Design 6) drive the strategy.
- **Connection pooling** (Java Phase 7.2 / System Design 7): connections are expensive and limited; a pool reuses them. App-side pools (**HikariCP**); a server-side pooler (**PgBouncer**) multiplexes thousands of client connections onto a few DB connections — essential at scale (Postgres connections are heavyweight processes).
- **Monitoring** (System Design 10): watch connections, slow queries (`pg_stat_statements`), cache hit ratio, replication lag, bloat/dead tuples (Phase 4.4), and disk. Autovacuum health is critical.
- **The WAL (Write-Ahead Log):** every change is written to the WAL before the data files — this is what gives **Durability** (Phase 5 ACID), crash recovery, replication, and PITR. One mechanism underpinning several guarantees.

## 7.4 — The NoSQL landscape (SQL vs NoSQL)
"NoSQL" = non-relational databases that trade some of SQL's guarantees for scale, flexibility, or a specialized data model. Four families (detailed in Phases 9–11):
- **Document** (MongoDB) — JSON-like documents; flexible schema; nested data; per-document operations. (Phase 9)
- **Key-value** (Redis, DynamoDB) — simple key → value; extremely fast; caching, sessions, counters. (Phase 10)
- **Wide-column** (Cassandra, HBase) — rows with dynamic columns; massive write throughput; time-series/logs.
- **Graph** (Neo4j) — nodes + relationships; traversals; social/recommendation/fraud. (Phase 11)

**SQL vs NoSQL — the decision** (expanded in Phase 8): choose **SQL** for strong consistency, ACID transactions, and complex/ad-hoc relational queries (payments, orders — most business data). Choose **NoSQL** for a specific access pattern, flexible/evolving schema, or scale beyond a single relational node — accepting eventual consistency and limited joins/transactions. Often the answer is **both** (polyglot persistence, Phase 12). Modern reality: Postgres (with JSONB, Phase 6) covers a lot of "NoSQL" needs, so start relational and reach for a specialized store only when a real requirement demands it. NewSQL (CockroachDB, Spanner) aims to give SQL + horizontal scale.
