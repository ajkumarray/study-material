<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · postgres features](../phase-6-postgres-features/NOTES.md) | [Phase 8 · nosql foundations ➡](../phase-8-nosql-foundations/NOTES.md)
<!-- /nav -->

# Phase 7 — Production & Beyond Relational: Interview Q&A

⭐ = asked constantly.

**Q: How do you manage database schema changes safely across environments?** ⭐⭐
With a versioned migration tool (Flyway, Liquibase) that applies ordered SQL
scripts and records exactly which ones have run against each database in a
metadata table, so a developer's local database, CI, staging, and production all
converge to the same schema by construction rather than by careful manual
discipline. Migrations are treated as immutable once applied — if a mistake is
discovered in an already-run migration, you write a new migration to fix it rather
than editing the old one, because any environment that already ran the original
would silently desync from one that ran an edited version. Production schema is
never hand-edited directly; the migration history is the single source of truth for
what the schema is and how it got there.

**Q: How would you safely add a `NOT NULL` column to a large table that's actively
receiving production traffic?** ⭐
Using the expand-then-contract pattern, in separate steps rather than one atomic
change: first add the column as nullable (`ALTER TABLE ... ADD COLUMN phone TEXT`)
— this is safe to deploy immediately since it doesn't require touching any existing
row and both old and new application code keep working. Then deploy application
code that writes the new column going forward. Then backfill existing rows in
batches (not one giant `UPDATE` that would lock the whole table for its duration).
Only once every row genuinely has a value do you add the `NOT NULL` constraint
itself. Doing this in one step — `ALTER TABLE ... ADD COLUMN phone TEXT NOT NULL`
directly against a table with existing rows and no default — would either fail
outright (existing rows have no value to satisfy `NOT NULL`) or force a long,
blocking table rewrite, neither of which is acceptable against live traffic.

**Q: Partitioning vs sharding — what's the difference?** ⭐⭐
Partitioning splits one large table into smaller physical pieces **within the same
database**, by range, list, or hash of a key column — the query planner can
"prune" partitions that provably can't contain matching rows for a given query, and
dropping old data becomes an instant `DROP TABLE partition_name` instead of a slow
row-by-row `DELETE`. Sharding spreads data across **multiple separate machines**,
which is what actually lets you scale write throughput and total storage beyond
what any single node can hold — at the real cost of cross-shard queries becoming
genuinely difficult (a query that needs data from two shards can no longer be a
single simple query) and needing a rebalancing strategy (often consistent hashing)
as shards are added or removed. Partitioning is a routine tuning technique available
on a single Postgres instance; sharding is a significant architectural decision with
its own operational overhead, and the standard advice is to reach for partitioning
first and shard only once a single node genuinely can't keep up.

```sql
CREATE TABLE measurement (
    id BIGINT GENERATED ALWAYS AS IDENTITY, taken_at DATE NOT NULL, value NUMERIC
) PARTITION BY RANGE (taken_at);

CREATE TABLE measurement_2026_01 PARTITION OF measurement
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');
```
A query filtered to `taken_at >= '2026-02-01'` skips `measurement_2026_01` entirely
in its plan — that's partition pruning in action.

**Q: What's the database scaling ladder, and why does the order matter?**
Optimize queries and add missing indexes (Phase 4) → introduce a caching layer
(Redis) in front of the database → add read replicas to scale read traffic → 
partition large tables within the database → shard across multiple machines. The
order matters because each rung is progressively more complex to build and operate
— query/index tuning is nearly always the cheapest fix available and should be
exhausted first, while sharding is a substantial architectural commitment that adds
real complexity (rebalancing, cross-shard queries) you don't want to take on before
it's actually necessary. A common interview trap is jumping straight to "shard it"
for a performance problem that a missing index would have fixed in minutes.

**Q: Logical vs physical backups — what's the difference, and what is Point-In-Time
Recovery?**
A logical backup (`pg_dump`/`pg_restore`) exports the database's data and structure
as portable SQL/data — inspectable, restorable into a different Postgres version or
even a different environment, but slower to restore for very large databases since
it has to rebuild everything from scratch. A physical backup is a raw copy of the
actual data files (a base backup), which combined with continuous **WAL
archiving** enables **Point-In-Time Recovery (PITR)** — the ability to restore to
the database's exact state at *any* specific moment within the archived window, by
replaying the write-ahead log forward from the base backup up to that target time
(e.g. "one minute before someone ran an accidental `DELETE FROM orders` with no
`WHERE` clause"). In either case, a backup that has never actually been restored
and verified isn't a proven backup — restore testing has to be part of the backup
strategy, not an afterthought. **RTO** (how long you're allowed to be down while
recovering) and **RPO** (how much data loss, measured in time, is acceptable) are
the business requirements that determine which strategy — and how frequently you
back up — is actually appropriate.

**Q: What is the WAL (Write-Ahead Log), and why does it matter so much?** ⭐
The WAL is an append-only log that Postgres writes every change to *before* that
change is applied to the actual data files. It's a single mechanism that underlies
several separate guarantees: **durability** (the "D" in ACID — once `COMMIT`
returns, the WAL record for that change is safely on disk, so a crash immediately
after can always recover it by replaying the WAL), **crash recovery** (on restart,
Postgres replays the WAL from the last checkpoint forward to reapply any committed
changes that weren't yet reflected in the data files), **streaming replication**
(a follower continuously receives and replays the same WAL stream the leader
writes, which is literally how it stays in sync), and **point-in-time recovery**
(replaying archived WAL forward from an old base backup, but stopping at a chosen
target moment instead of the present). Understanding the WAL explains why all four
of these seemingly separate features exist and behave the way they do — they're not
four different mechanisms, they're four different uses of the same one.

**Q: Why would you use a connection pooler like PgBouncer instead of just letting
the application open a connection per request?**
Postgres connections are relatively heavyweight — each one is backed by its own OS
process on the server — and the server has a hard, limited maximum number of
concurrent connections. Opening a fresh connection per incoming request is both
slow (connection setup has real overhead) and, at any meaningful request volume,
quickly exhausts that connection limit, especially once you have many application
instances each independently trying to hold their own pool of connections open. An
application-side pool (HikariCP in the Java/Spring world) reuses a small set of
already-open connections across requests within *one* application instance.
PgBouncer goes a step further and sits between potentially many application
instances and the database, multiplexing thousands of client-side connections down
onto a much smaller number of actual backend database connections — essential once
you're running enough application instances that even well-configured per-instance
pools would collectively exceed what the database server can handle.

**Q: What should you actively monitor on a production Postgres database?**
Active/idle connection counts (approaching the connection limit is an early warning
sign), slow queries (via the `pg_stat_statements` extension, which tracks
aggregate timing per distinct query shape), buffer cache hit ratio (a low hit ratio
means the working set doesn't fit in memory and queries are hitting disk more than
expected), replication lag on any followers, table and index bloat / dead-tuple
counts (Phase 4 — a database quietly falling behind on autovacuum is one of the
most common causes of a mysterious, slowly-worsening production incident), and disk
usage. Autovacuum health specifically deserves its own dedicated monitoring, since
its failure doesn't cause an immediate, obvious error — it causes a slow-motion
degradation that's much harder to diagnose after the fact than to catch early.

**Q: What are the four main NoSQL database families, and what's each one
optimized for?** ⭐⭐
**Document** databases (MongoDB, Phase 9) store JSON-like documents with flexible,
per-document schema and nested data — good for catalogs, content, and records that
evolve shape over time. **Key-value** stores (Redis, DynamoDB, Phase 10) are the
simplest model — a value looked up by a key — but extremely fast, the natural fit
for caching, session storage, counters, rate limiting, and leaderboards.
**Wide-column** stores (Cassandra, HBase) support rows with a dynamic per-row set of
columns and are built for massive write throughput, commonly used for time-series
data and logs at very large scale. **Graph** databases (Neo4j, Phase 11) make
nodes and relationships first-class, optimized specifically for traversal queries —
social graphs, recommendation engines, and fraud-ring detection, where the
relationships between records matter as much as the records themselves. Each family
trades away some of what a relational database offers (usually full joins and/or
strict ACID transactions across arbitrary records) to specialize hard for one access
pattern.

**Q: SQL vs NoSQL — how do you actually decide which to use for a given
system?** ⭐⭐
Reach for SQL (relational) when you need strong consistency, genuine ACID
transactions across multiple related rows or tables, and the ability to run
complex or ad-hoc relational queries — this describes most core business data:
payments, orders, inventory, anything where correctness and the ability to query
data in ways you didn't originally anticipate both matter. Reach for NoSQL when a
specific, well-understood access pattern dominates the workload, the schema needs
to evolve quickly or vary per record, or you need to scale beyond what a single
relational node can realistically handle — and you're willing to accept weaker
consistency guarantees (eventual consistency, covered in Phase 8) and limited or no
cross-record joins/transactions in exchange. In practice, the pragmatic modern
default is to start relational — Postgres with `JSONB` (Phase 6) already covers a
meaningful chunk of what used to specifically require a document database — and
introduce a specialized NoSQL store only once a concrete, measured requirement
genuinely demands it. Mature systems frequently end up using several database
technologies together rather than exactly one (polyglot persistence, Phase 12).

**Q: What is NewSQL, and what problem is it trying to solve?**
NewSQL databases (CockroachDB, Google Spanner, TiDB) aim to combine the relational
model and full ACID transactional guarantees of a traditional SQL database with the
horizontal, multi-machine scalability that historically only NoSQL systems offered.
They're the answer to "I need both strong consistency *and* to scale writes/storage
beyond one machine" — a combination that traditional single-node relational
databases and classic sharded NoSQL systems each only solve half of on their own.
The trade-off is usually architectural complexity and, depending on the specific
system, some added write latency from the distributed consensus protocols (like
Raft or Paxos-family algorithms) needed to keep multiple nodes consistent.
