<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 11 · neo4j](../phase-11-neo4j/NOTES.md)
<!-- /nav -->

# Phase 12 — Polyglot Persistence: Notes (Theory)

The synthesis of the whole track: real systems use **multiple databases**, each chosen for a specific job — this is **polyglot persistence**. One size does not fit all; you match the store to the access pattern.

## 12.1 — Using multiple databases together
A single non-trivial application often has several data shapes with conflicting needs. Example — an e-commerce platform:
| Data / need | Best store | Why |
|---|---|---|
| Orders, payments, inventory | **PostgreSQL** | ACID transactions, strong consistency, relational integrity |
| Product catalog (variable attributes) | **PostgreSQL JSONB** or **MongoDB** | flexible/nested per-product attributes |
| Sessions, cart, cache, rate limits | **Redis** | microsecond reads, TTL, counters |
| Full-text product search | **Elasticsearch** (or Postgres FTS) | ranked text search |
| "Customers who bought X also bought Y" | **Neo4j** | relationship traversal |
| Clickstream / analytics | **columnar / warehouse** (ClickHouse, BigQuery) | aggregate-heavy scans |
| Event log between services | **Kafka** | durable, replayable event stream (track 16) |

Each store holds the data it's best at; the application (or events) keeps them coordinated.

**The cost:** every additional datastore is more to run, monitor, back up, secure, and learn; more places for data to drift and for consistency bugs. So the guiding principle is **"as few as possible, as many as necessary."** Start with **PostgreSQL** — it covers the majority of needs (relational + JSONB documents + FTS + arrays, all ACID). Add a specialized store only when a *real, measured* requirement demands it. The most common first addition is **Redis** (cache/sessions). Beware **resume-driven development** — choose for the problem, not novelty.

## Keeping polyglot stores consistent
When the same fact lives in multiple stores (e.g., an order in Postgres, its search doc in Elasticsearch, a cache in Redis), you need to keep them in sync — and you can't use a single ACID transaction across them (System Design 4). Patterns:
- **Cache invalidation** — delete/refresh the Redis key on write (Phase 10 / System Design 2).
- **The outbox pattern + events** — write the change to Postgres and an outbox in one transaction; a relay publishes an event (Kafka) that updates the other stores (System Design 4/9). Avoids the dual-write problem.
- **CQRS** — a write model (Postgres) and separate read models (search index, cache) built from the event stream (System Design 9).
- Accept **eventual consistency** between stores; make updates **idempotent** so retries are safe.

## 12.2 — Modeling one domain across paradigms (the mental exercise)
Take a simple social/bookstore domain (users, books, purchases, friendships) and see how each paradigm models it:
- **Relational (Postgres):** normalized tables — `user`, `book`, `purchase` (junction), `friendship` (junction). Joins reassemble. Best for transactional integrity and ad-hoc queries.
- **Document (Mongo):** a `user` document embedding recent purchases; books referenced by id. Fast per-user reads; denormalized. Best for entity-centric reads.
- **Key-value (Redis):** `user:42:session`, `book:1` cached JSON, `leaderboard` of bestsellers (sorted set). Best for speed/ephemeral/derived data — a *complement*, not the source of truth.
- **Graph (Neo4j):** users and books as nodes; `:FRIEND`, `:PURCHASED`, `:SIMILAR_TO` as edges. Best for "friends who bought this," recommendations, and degrees of separation.

The lesson: **the same domain has different "best" models depending on the question you ask most.** Relational is the safe, general default; the others are specialists. A mature system often runs several, each answering the queries it's best at — coordinated by events and accepting eventual consistency between them.

**The decision framework (recap, Phase 8):** access pattern → consistency needs → scale → schema stability → operational cost. Answer those, default to Postgres, and add specialists deliberately.
