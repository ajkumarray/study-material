<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 04 — Databases — Full Deep Dive

A complete, hands-on **multi-model** database course — not a revision. Where
Java Phase 7 taught *just enough* SQL/JDBC to build the capstone, this track
treats databases as a first-class subject at the same depth as the Java and JS
tracks — and covers all four major paradigms:

| Paradigm | System taught | Query language |
|----------|---------------|----------------|
| Relational | **PostgreSQL 16** | SQL |
| Document | **MongoDB** | MQL / aggregation pipeline |
| Key-value | **Redis** | Redis commands |
| Graph | **Neo4j** | Cypher |

**Format (per your ask):** each lesson is **theory + practical queries** — I
explain the *why*, then give you idiomatic, copy-pasteable queries/commands to
run yourself. Queries are written correct-by-construction now; I'll verify them
against live servers once you grant access (Postgres 16 is already running
locally; Mongo/Neo4j/Redis can be added later). Each phase has `NOTES.md` (the
theory) and `INTERVIEW.md` (Q&A + follow-ups). ⭐ = commonly asked in
interviews. Ties into Spring Data JPA (backend) and the Redis middleware track.

## Curriculum

### Phase 1 — Relational foundations & SQL basics ✅
- [x] 1.1 What an RDBMS is; tables/rows/columns/keys; connecting with `psql`
- [x] 1.2 `SELECT`, `WHERE`, `ORDER BY`, `LIMIT`, `DISTINCT`
- [x] 1.3 Data types & `NULL` semantics (three-valued logic)
- [x] 1.4 DML: `INSERT`, `UPDATE`, `DELETE`, `RETURNING`

### Phase 2 — Querying multiple tables & aggregation ✅
- [x] 2.1 JOINs: inner, left/right/full outer, cross, self-join
- [x] 2.2 Aggregation: `GROUP BY`, `HAVING`, aggregate functions
- [x] 2.3 Subqueries & CTEs (`WITH`), correlated subqueries
- [x] 2.4 Window functions: `ROW_NUMBER`/`RANK`, running totals, partitions
- [x] 2.5 Set operations: `UNION`/`INTERSECT`/`EXCEPT`

### Phase 3 — Schema design & data modeling ✅
- [x] 3.1 Constraints: PK, FK, `UNIQUE`, `CHECK`, `NOT NULL`, `DEFAULT`
- [x] 3.2 Relationships: 1-1, 1-many, many-many (junction tables)
- [x] 3.3 Normalization 1NF → 2NF → 3NF (+ BCNF), functional dependencies
- [x] 3.4 Denormalization — when and why to break the rules

### Phase 4 — Indexing & query performance ✅
- [x] 4.1 How indexes work (B-tree); `CREATE INDEX`
- [x] 4.2 `EXPLAIN` / `EXPLAIN ANALYZE` — reading query plans
- [x] 4.3 Composite, covering, partial, expression indexes; index-only scans
- [x] 4.4 When indexes hurt; `VACUUM`/`ANALYZE`, bloat, statistics

### Phase 5 — Transactions & concurrency ✅
- [x] 5.1 ACID; `BEGIN`/`COMMIT`/`ROLLBACK`; savepoints
- [x] 5.2 Isolation levels & anomalies (dirty / non-repeatable / phantom)
- [x] 5.3 Locking, deadlocks, `SELECT ... FOR UPDATE`
- [x] 5.4 MVCC — how Postgres does concurrency without read locks

### Phase 6 — PostgreSQL power features ✅
- [x] 6.1 Views & materialized views
- [x] 6.2 Functions & stored procedures (PL/pgSQL); triggers
- [x] 6.3 Advanced types: `JSONB`, arrays, enums, ranges; full-text search
- [x] 6.4 Roles, privileges, row-level security

### Phase 7 — Production & beyond relational ✅
- [x] 7.1 Migrations (Flyway/Liquibase); schema evolution
- [x] 7.2 Replication, partitioning, sharding — scaling strategies
- [x] 7.3 Backups, connection pooling (PgBouncer / HikariCP recap)
- [x] 7.4 NoSQL landscape: key-value (Redis), document (Mongo), wide-column,
      graph; the CAP theorem; SQL vs NoSQL decision-making

*(Phases 1–7 above = **Part A: Relational / PostgreSQL**. Part B below covers
the NoSQL paradigms.)*

### Phase 8 — NoSQL foundations ✅
- [x] 8.1 Why NoSQL exists; the four data-model families
- [x] 8.2 The CAP theorem; consistency models (strong vs eventual)
- [x] 8.3 BASE vs ACID; horizontal scaling, replication, sharding
- [x] 8.4 Choosing a database: the decision framework

### Phase 9 — MongoDB (document database) ✅
- [x] 9.1 Documents, collections, BSON; the Mongo shell
- [x] 9.2 CRUD: `insertOne/Many`, `find` + query operators, `updateOne`, `deleteOne`
- [x] 9.3 The aggregation pipeline (`$match`/`$group`/`$lookup`/`$project`...)
- [x] 9.4 Indexing; schema design — embedding vs referencing
- [x] 9.5 Transactions, replica sets, sharding

### Phase 10 — Redis (key-value / data structure store) ✅
- [x] 10.1 In-memory model; keys, `TTL`/expiry; when to use Redis
- [x] 10.2 Data types: strings, hashes, lists, sets, sorted sets, streams
- [x] 10.3 Caching patterns (cache-aside, write-through); eviction policies
- [x] 10.4 Pub/sub, pipelines, transactions (`MULTI`/`EXEC`), Lua scripts
- [x] 10.5 Persistence (RDB/AOF), clustering; Redis + Spring recap

### Phase 11 — Neo4j (graph database) ✅
- [x] 11.1 The property-graph model: nodes, relationships, properties
- [x] 11.2 Cypher basics: `MATCH`, `CREATE`, `MERGE`, `WHERE`, `RETURN`
- [x] 11.3 Traversals & variable-length paths; when graphs beat joins
- [x] 11.4 Indexing, constraints; graph modeling patterns

### Phase 12 — Polyglot persistence ✅
- [x] 12.1 Using multiple databases together; the right tool per job
- [x] 12.2 Model one domain across all four paradigms — a direct contrast

### Capstone
- [x] **Part A:** design a normalized PostgreSQL schema for a real domain, load
      seed data, write analytical queries (joins + window functions), add
      indexes and **prove** the speedup with `EXPLAIN ANALYZE`, wrap a
      multi-step operation in a transaction.
- [x] **Part B:** model the *same* domain in MongoDB (documents), Redis (as a
      cache + leaderboard), and Neo4j (relationships), then write up which
      paradigm fits which access pattern and why.

## Structure

```
04-databases/
├── README.md
├── phase-1-foundations/
│   ├── NOTES.md          <- theory: the "why"
│   ├── INTERVIEW.md      <- Q&A + follow-ups
│   └── *.sql             <- practical queries to run
├── ...
├── phase-9-mongodb/      <- *.js / *.mongodb query files
├── phase-10-redis/       <- *.redis command files
└── phase-11-neo4j/       <- *.cypher query files
```

## Environment

- **PostgreSQL 16** — running locally (`localhost:5432`); access to be granted
  later. MongoDB / Redis / Neo4j — to be set up when we reach Part B (Docker
  makes this trivial, which the DevOps track will cover).
- Until then: lessons are **theory + copy-pasteable queries**; verification
  against live servers happens once access is granted.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · foundations** — [Notes](phase-1-foundations/NOTES.md) · [Interview](phase-1-foundations/INTERVIEW.md)
- **Phase 2 · multi table** — [Notes](phase-2-multi-table/NOTES.md) · [Interview](phase-2-multi-table/INTERVIEW.md)
- **Phase 3 · schema design** — [Notes](phase-3-schema-design/NOTES.md) · [Interview](phase-3-schema-design/INTERVIEW.md)
- **Phase 4 · indexing** — [Notes](phase-4-indexing/NOTES.md) · [Interview](phase-4-indexing/INTERVIEW.md)
- **Phase 5 · transactions** — [Notes](phase-5-transactions/NOTES.md) · [Interview](phase-5-transactions/INTERVIEW.md)
- **Phase 6 · postgres features** — [Notes](phase-6-postgres-features/NOTES.md) · [Interview](phase-6-postgres-features/INTERVIEW.md)
- **Phase 7 · production** — [Notes](phase-7-production/NOTES.md) · [Interview](phase-7-production/INTERVIEW.md)
- **Phase 8 · nosql foundations** — [Notes](phase-8-nosql-foundations/NOTES.md) · [Interview](phase-8-nosql-foundations/INTERVIEW.md)
- **Phase 9 · mongodb** — [Notes](phase-9-mongodb/NOTES.md) · [Interview](phase-9-mongodb/INTERVIEW.md)
- **Phase 10 · redis** — [Notes](phase-10-redis/NOTES.md) · [Interview](phase-10-redis/INTERVIEW.md)
- **Phase 11 · neo4j** — [Notes](phase-11-neo4j/NOTES.md) · [Interview](phase-11-neo4j/INTERVIEW.md)
- **Phase 12 · polyglot** — [Notes](phase-12-polyglot/NOTES.md) · [Interview](phase-12-polyglot/INTERVIEW.md)
<!-- /phases-nav -->
