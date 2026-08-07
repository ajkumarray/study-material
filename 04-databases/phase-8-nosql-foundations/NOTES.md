<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · production](../phase-7-production/NOTES.md) | [Phase 9 · mongodb ➡](../phase-9-mongodb/NOTES.md)
<!-- /nav -->

# Phase 8 — NoSQL Foundations: Notes (Theory)

The bridge from relational to Part B. "NoSQL" ("Not Only SQL") = non-relational databases that trade some of SQL's guarantees for scale, flexibility, or a specialized data model. Much of the systems theory here was covered in **System Design Phase 1** (CAP) — this frames it from the data angle.

## 8.1 — Why NoSQL exists & the four families
Relational databases assume a fixed schema, strong consistency, and (historically) a single powerful node. The 2000s web forced three pressures relational struggled with: **massive horizontal scale**, **flexible/evolving schemas**, and **specialized access patterns**. NoSQL databases each relax some relational assumption to win one of those.

The four data-model families (detailed in Phases 9–11):
| Family | Example | Model | Sweet spot |
|---|---|---|---|
| **Document** | MongoDB | JSON-like documents, nested | flexible/evolving records, per-entity reads (Phase 9) |
| **Key-value** | Redis, DynamoDB | key → value | caching, sessions, counters, ultra-fast lookups (Phase 10) |
| **Wide-column** | Cassandra, HBase | rows with dynamic column families | huge write throughput, time-series |
| **Graph** | Neo4j | nodes + relationships | many-to-many traversals, social/fraud/recommendations (Phase 11) |

Most also default to **horizontal scaling** (sharding built in) and **schema flexibility**, unlike traditional relational.

## 8.2 — CAP theorem & consistency models
In a distributed system, during a **network partition** you must choose **Consistency** (every read sees the latest write) or **Availability** (every request gets a response) — not both (System Design 1.3). So distributed databases are effectively **CP** (refuse/block during partition to stay correct — e.g., HBase, MongoDB in its default config) or **AP** (keep serving, reconcile later — e.g., Cassandra, DynamoDB, Riak). Relational DBs on a single node sidestep CAP (no partition within one node) but face it when replicated/distributed.

**Consistency models** are a spectrum, not binary: **strong** (linearizable — every read current), **causal**, **eventual** (replicas converge given no new writes — the common NoSQL default). Many systems offer **tunable consistency** per operation (quorum reads/writes, `R + W > N` gives strong-ish reads).

## 8.3 — BASE vs ACID; horizontal scaling
- **ACID** (relational — Phase 5): Atomicity, Consistency, Isolation, Durability — strict correctness.
- **BASE** (many NoSQL): **B**asically **A**vailable, **S**oft state, **E**ventual consistency — prioritize availability and scale, accept temporary inconsistency that converges. It's the AP-leaning philosophy.
- **Horizontal scaling** is native to NoSQL: data is **sharded** across nodes (often by consistent hashing, System Design 3), and **replicated** for availability, with conflict resolution (last-write-wins, vector clocks, CRDTs) since multiple nodes may accept writes. This is what buys near-limitless scale — at the cost of the strong guarantees ACID gives.

## 8.4 — Choosing a database (the decision framework)
Ask, in order:
1. **Access pattern** — how will you read/write? Key lookups → key-value; nested per-entity docs → document; deep relationship traversal → graph; ad-hoc joins/reporting → relational.
2. **Consistency needs** — money/inventory/uniqueness → strong (SQL / CP). Feeds/likes/counters → eventual is fine (AP).
3. **Scale** — fits one node (even a big one)? → relational is simpler. Beyond it, or write-throughput-bound → a horizontally-scalable NoSQL.
4. **Schema** — stable and relational → SQL. Highly variable/evolving → document.
5. **Operational cost** — every new datastore is more to run, monitor, and learn. Prefer fewer.

**Defaults & reality:** start with **PostgreSQL** — it handles the vast majority of applications, and with **JSONB** (Phase 6) it even covers many document use cases, all with ACID and joins. Add a specialized store when a *real* requirement demands it (Redis for cache/sessions is the most common addition). Using several deliberately is **polyglot persistence** (Phase 12). Beware "resume-driven development" — choose for the problem, not the hype.
