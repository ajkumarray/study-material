<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · postgres features](../phase-6-postgres-features/NOTES.md) | [Phase 8 · nosql foundations ➡](../phase-8-nosql-foundations/NOTES.md)
<!-- /nav -->

# Phase 7 — Production & Beyond Relational: Notes (Theory)

Running a database in production, and the map of the wider database world beyond
Postgres. Several of these topics are covered from the systems/architecture angle
in the System Design track (replication, sharding, RTO/RPO) — this phase is the
database-track view, with the practical migration/partitioning SQL living in
`01-migrations-partitioning.sql`.

---

## 1. Migrations & Schema Evolution

A schema changes constantly over a system's lifetime — new columns, new tables,
tightened constraints — and you need a **safe, reproducible** way to apply those
changes consistently across every environment (a developer's laptop, CI, staging,
production).

### Key Concepts

- **Migration tool** (Flyway, Liquibase): applies **versioned, ordered SQL
  scripts** and records which ones have already run in a metadata table, so every
  environment that runs the same migrations converges to the exact same schema.
- **Migrations are immutable once applied**: never edit a migration that has
  already run anywhere — if `V1` had a mistake, write a new `V4` that fixes it,
  rather than rewriting `V1` (which would desync any environment that already ran
  the old version of it).
- **Prefer additive, backward-compatible changes**: a change that only *adds*
  (a new nullable column, a new table) lets the *old* application code and the
  *new* application code both keep working correctly during a rolling deploy,
  since neither version depends on something the other doesn't have yet.
- **Expand-then-contract**: the standard pattern for a change that isn't naturally
  backward-compatible (e.g. adding a `NOT NULL` column to a table with existing
  rows) — expand first (add the column as nullable), deploy code that writes to it,
  backfill existing rows, switch reads over, *then* contract (add the `NOT NULL`
  constraint, and eventually drop any now-unused old column).
- **Never hand-edit the production schema**: migrations are the single source of
  truth for schema history — a manual `ALTER TABLE` run directly against
  production desyncs it from what the migration history claims the schema is,
  and the next migration run may behave unpredictably against that drifted state.

### Worked Example

```sql
-- V1__create_customer.sql
CREATE TABLE IF NOT EXISTS customer_v2 (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email TEXT NOT NULL UNIQUE
);

-- V2__add_phone.sql — additive, backward-compatible: old app code that doesn't
-- know about "phone" keeps working exactly as before; new code can start using it
ALTER TABLE customer_v2 ADD COLUMN IF NOT EXISTS phone TEXT;

-- V3__backfill_and_constrain.sql — the "contract" half of expand-then-contract,
-- run only once every existing row genuinely has a phone value
UPDATE customer_v2 SET phone = 'unknown' WHERE phone IS NULL;
-- ALTER TABLE customer_v2 ALTER COLUMN phone SET NOT NULL;   -- safe only after backfill
```
In this example: `V2` alone is completely safe to deploy at any point during a
rolling release — a server still running the old code simply never touches
`phone`, and a server running the new code can start writing to it immediately.
Only `V3`, run once every row has been backfilled, would be unsafe to run *before*
the backfill — which is exactly why expand-then-contract splits it into separate
migration steps rather than doing it all in one `ALTER TABLE ... ADD COLUMN phone
TEXT NOT NULL` that would fail immediately against existing rows (or lock the
table for a long rewrite).

### Why It's Useful

Without versioned migrations, "what does the production schema actually look
like right now" becomes a question nobody can answer with confidence — every
environment can silently drift apart after enough ad-hoc manual changes. A
migration tool makes the schema's entire history reproducible from a clean
database, which is exactly what CI, new environments, and disaster recovery all
depend on. In Spring Boot specifically, Flyway running migrations on application
startup is the production-grade replacement for Hibernate's `ddl-auto=update` —
`ddl-auto=update`'s automatic schema inference is convenient for local development
but far too unpredictable (and unauditable) to trust against a production database.

### Summary / Key Takeaways

- A migration tool applies versioned, ordered scripts and tracks which have run —
  never hand-edit production schema directly.
- Migrations already applied are immutable; fix mistakes with a new migration, not
  by editing an old one.
- Prefer additive, backward-compatible changes so a rolling deploy's old and new
  app versions can coexist safely.
- Expand-then-contract is the standard pattern for a change (like a new `NOT NULL`
  column) that can't be done safely in one atomic step against live data.

---

## 2. Scaling Strategies

As load grows, there's a well-established progression of techniques to keep a
relational database fast and available — each one more complex to operate than the
last, so the practical advice is to climb the ladder only as far as actual load
demands.

### Key Concepts

- **Query/index optimization** (Phase 4): the cheapest, highest-leverage step —
  most "the database is too slow" problems are actually "this specific query is
  missing an index" problems.
- **Caching** (Redis, Phase 10): keep frequently-read, rarely-changing data in a
  fast in-memory store in front of the database, so the database itself sees fewer
  reads.
- **Replication**: leader-follower copies of the same data, used for read scaling
  (route read traffic to followers) and failover (promote a follower if the leader
  fails). Comes with **replication lag** and a synchronous-vs-asynchronous
  trade-off between consistency and write latency.
- **Partitioning** (in this phase's SQL file): split one large table into smaller
  physical pieces **within the same database**, by range, list, or hash of a key
  column. The query planner can skip (**prune**) partitions that can't possibly
  contain matching rows, and dropping old data becomes an instant `DROP TABLE`
  instead of a slow row-by-row `DELETE`.
- **Sharding**: spread partitions of data across **separate machines**, scaling
  both write throughput and total storage beyond what any single node could hold —
  at the cost of cross-shard queries becoming genuinely hard, and needing a
  rebalancing strategy (often consistent hashing) as shards are added or removed.
  This is a significant architectural step, not a routine tuning knob.

### Worked Example — Range Partitioning

```sql
CREATE TABLE measurement (
    id       BIGINT GENERATED ALWAYS AS IDENTITY,
    taken_at DATE NOT NULL,
    value    NUMERIC
) PARTITION BY RANGE (taken_at);

CREATE TABLE measurement_2026_01 PARTITION OF measurement
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
CREATE TABLE measurement_2026_02 PARTITION OF measurement
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

INSERT INTO measurement (taken_at, value) VALUES
    ('2026-01-15', 10), ('2026-02-10', 20);
-- Postgres routes each row to the correct underlying partition automatically —
-- application code just inserts into `measurement`, never into a specific partition
```

```sql
-- A query that only needs one month PRUNES the other partition entirely
EXPLAIN SELECT * FROM measurement WHERE taken_at >= '2026-02-01';
--                              QUERY PLAN
-- ----------------------------------------------------------------
--  Seq Scan on measurement_2026_02 measurement
--    Filter: (taken_at >= '2026-02-01'::date)
-- (measurement_2026_01 doesn't even appear in the plan — it was pruned)
```
In this example: the planner recognizes that `taken_at >= '2026-02-01'` can only
possibly match rows inside `measurement_2026_02`'s declared range, and skips
scanning `measurement_2026_01` entirely, rather than scanning both partitions and
filtering afterward. On a table with, say, 60 monthly partitions spanning 5 years,
a query scoped to one recent month reads only 1/60th of the data a single giant
unpartitioned table would have forced it to scan.

Dropping old data is likewise instant: `DROP TABLE measurement_2026_01;` removes an
entire month of data in one fast metadata operation, versus a `DELETE FROM
measurement WHERE taken_at < '2026-02-01'` that would have to find, lock, and
individually remove every matching row.

### Comparison Table

| Strategy | Scope | Solves | Cost |
|---|---|---|---|
| Query/index tuning | one query | slow specific queries | usually cheap, do first |
| Caching | in front of the DB | repeated identical reads | cache invalidation complexity |
| Replication | multiple copies, one DB cluster | read scaling, failover | replication lag |
| Partitioning | within one database | huge-table manageability, query pruning | more complex DDL |
| Sharding | across machines | write/storage scale beyond one node | cross-shard queries, rebalancing |

### The Scaling Ladder

Optimize queries and add missing indexes → add a caching layer → add read
replicas → partition large tables → shard across machines. Each rung solves a
progressively harder scaling problem, and each one also adds real operational
complexity — the practical guidance is to climb only as far as measured load
actually requires, not to pre-emptively shard a database that a couple of missing
indexes would have fixed.

### Why It's Useful

Recognizing which rung of the ladder a given scaling problem actually needs is a
core system-design skill — reaching for sharding when the real problem is a missing
index wastes enormous engineering effort on an unnecessarily complex architecture,
while trying to index your way out of a workload that genuinely needs
horizontal write scaling won't work no matter how well-tuned the queries are.

### Summary / Key Takeaways

- Partitioning splits a table into physical pieces within one database; sharding
  spreads those pieces across multiple machines — very different scopes and costs.
- The query planner "prunes" partitions it can prove don't match the query,
  turning a scan of the whole table into a scan of just the relevant slice.
- Dropping a whole partition is a fast metadata operation; deleting the equivalent
  rows one by one is not.
- Climb the scaling ladder in order — query/index tuning, then caching, then
  replicas, then partitioning, then sharding — each step is progressively more
  complex to operate.

---

## 3. Backups, Connection Pooling, and Operations

Keeping a database *running correctly* in production is a distinct skill from
designing its schema or writing fast queries — this section covers the operational
concerns that come up once a database is live and serving real traffic.

### Key Concepts

- **Logical backups** (`pg_dump`/`pg_restore`): export the database's data and
  structure as portable SQL/data — human-inspectable, restorable into a different
  Postgres version, but slower for very large databases.
- **Physical backups**: a raw base backup of the data files, combined with
  **WAL archiving**, enabling **Point-In-Time Recovery (PITR)** — the ability to
  restore the database to its exact state at *any* moment in the archived window
  by replaying the write-ahead log forward from the base backup.
- **Test your restores**: a backup that has never actually been restored and
  verified isn't a proven backup — it's an untested assumption. Recovery
  objectives, **RTO** (how long recovery is allowed to take) and **RPO** (how much
  data loss, measured in time, is acceptable), drive which backup strategy is
  appropriate.
- **Connection pooling**: Postgres connections are relatively heavyweight (each
  one is backed by an OS process) and limited in number — opening a fresh
  connection per request is slow and can exhaust the server's connection limit
  under load. An **application-side pool** (HikariCP in the Java/Spring world)
  reuses a small set of already-open connections across requests within one
  application instance. A **server-side pooler** (PgBouncer) goes further,
  multiplexing potentially thousands of client connections down onto a much
  smaller number of actual database connections — essential once you have many
  application instances all talking to one database.
- **Monitoring**: active connection count, slow queries (via the
  `pg_stat_statements` extension), buffer cache hit ratio, replication lag,
  table/index bloat and dead-tuple counts (Phase 4), disk usage, and autovacuum
  health — a database that silently falls behind on autovacuum is one of the most
  common causes of a slow, mysterious production incident (Phase 4).
- **The WAL (Write-Ahead Log)**: Postgres writes every change to this append-only
  log *before* it's applied to the actual data files. This single mechanism is
  what underlies **durability** (the "D" in ACID, Phase 5 — a crash after `COMMIT`
  can always replay the WAL to recover the committed change), **crash recovery**,
  **streaming replication** (followers apply the same WAL stream), and
  **point-in-time recovery**.

### Worked Example — Why WAL Underpins So Much

When a transaction commits, Postgres doesn't need to have flushed every changed
data page to disk yet — it only needs the WAL record describing that change to be
safely on disk. If the server crashes immediately after, startup recovery replays
the WAL from the last checkpoint forward, reapplying every committed change exactly
as if the crash never happened. Streaming replication is the same idea extended
across machines: a follower continuously receives and replays the same WAL stream
the leader is writing, keeping its own copy of the data up to date. Point-in-time
recovery is the same mechanism used deliberately in reverse — restore an old base
backup, then replay the archived WAL forward only up to some specific target
moment (e.g. "one minute before the accidental `DELETE FROM orders` ran"), rather
than all the way to the present.

### Comparison Table

| Backup type | Speed to restore | Granularity | Portability |
|---|---|---|---|
| Logical (`pg_dump`) | slower on large DBs | whole DB / specific tables | portable across Postgres versions |
| Physical + WAL | fast base restore, then WAL replay | point-in-time (any moment) | tied to matching Postgres version/architecture |

| Pooling layer | Multiplexes | Typical tool |
|---|---|---|
| Application-side | connections within one app instance | HikariCP |
| Server-side | connections across many app instances | PgBouncer |

### Why It's Useful

An unindexed query is an inconvenience; a database that can't recover from a crash,
or that falls over under connection pressure during a traffic spike, is an outage.
These operational practices are what stand between "we have a database" and "we
have a database we can actually trust in production" — and interviewers ask about
them specifically because they separate people who've only ever queried a local
dev database from people who've operated one under real load.

### Summary / Key Takeaways

- Logical backups are portable but slower to restore; physical backups plus WAL
  archiving enable point-in-time recovery to any moment. Always test restores.
- Connection pooling (app-side and/or a server-side pooler like PgBouncer) is
  essential once connection volume approaches Postgres's practical connection
  limits — untested at low traffic, critical at scale.
- The WAL is one mechanism behind durability, crash recovery, replication, and
  point-in-time recovery — understanding it explains all four at once.
- Monitor connections, slow queries, cache hit ratio, replication lag, and
  bloat/autovacuum health continuously, not just when something is already broken.

---

## 4. The NoSQL Landscape — SQL vs NoSQL

"NoSQL" is an umbrella term for non-relational databases that each trade away some
of SQL's guarantees (strict schema, joins, full ACID transactions across arbitrary
data) in exchange for scale, schema flexibility, or a data model specialized for one
particular access pattern.

### Key Concepts

- **Document databases** (MongoDB — Phase 9): store JSON-like documents with
  flexible, per-document schema and nested data; queries and updates are typically
  scoped to a single document.
- **Key-value stores** (Redis, DynamoDB — Phase 10): the simplest model, a value
  looked up by a key, but extremely fast — the natural fit for caching, session
  storage, counters, and rate limiting.
- **Wide-column stores** (Cassandra, HBase): rows with a dynamic, per-row set of
  columns, built for massive write throughput — a common fit for time-series data
  and logs at very large scale.
- **Graph databases** (Neo4j — Phase 11): nodes and typed relationships as
  first-class citizens, optimized specifically for traversal queries — social
  graphs, recommendation engines, fraud-ring detection.

### Comparison Table

| Family | Example | Strength | Typical use |
|---|---|---|---|
| Relational (SQL) | PostgreSQL | ACID, complex joins/queries, strong consistency | payments, orders, most core business data |
| Document | MongoDB | flexible nested schema | catalogs, content, evolving records |
| Key-value | Redis, DynamoDB | extreme speed, simple access | caching, sessions, counters, leaderboards |
| Wide-column | Cassandra | massive write throughput | time-series, logs, IoT telemetry |
| Graph | Neo4j | relationship traversal | social graphs, recommendations, fraud detection |

### SQL vs NoSQL — How to Choose

Choose **SQL** (relational) when you need strong consistency, real ACID
transactions across multiple related rows/tables, and complex or genuinely ad-hoc
relational queries — this describes most core business data (payments, orders,
inventory) where correctness matters more than raw throughput. Choose **NoSQL**
when a specific, well-understood access pattern dominates, the schema needs to stay
flexible or evolve quickly, or you need to scale beyond what a single relational
node can handle — accepting, in exchange, weaker consistency guarantees
(eventual consistency, Phase 8) and limited or no cross-record transactions/joins.

The modern practical reality: Postgres with `JSONB` (Phase 6) already covers a
meaningful slice of what teams historically reached for a document database to get,
so the pragmatic default is to start relational and introduce a specialized NoSQL
store only once a concrete, measured requirement genuinely demands it — not
speculatively. Often the real answer for a mature system is **both**: a relational
database as the system of record plus one or more specialized stores for specific
hot paths (Phase 12 covers this polyglot-persistence approach directly).

### Why It's Useful

Recognizing "what problem does this database family actually solve" prevents two
common, expensive mistakes: forcing a genuinely document-shaped, rapidly-evolving
dataset into a rigid relational schema out of habit, and reaching for a trendy
NoSQL store for data that's fundamentally relational (and would benefit enormously
from real joins and transactions) just because it's the newer technology.

### Summary / Key Takeaways

- The four common NoSQL families — document, key-value, wide-column, graph — each
  optimize for one specific access pattern; know one concrete use case for each.
- SQL wins on strong consistency, ACID transactions, and complex relational
  queries; NoSQL wins on schema flexibility, a specific access pattern, or scale
  beyond a single relational node.
- Postgres's `JSONB` already covers many "we need document flexibility" needs
  without adopting a second database — start relational, specialize only when a
  real requirement demands it.
- Mature systems often end up using several database technologies together
  (polyglot persistence) rather than picking exactly one for everything.
