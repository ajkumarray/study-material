<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · production](../phase-7-production/NOTES.md) | [Phase 9 · mongodb ➡](../phase-9-mongodb/NOTES.md)
<!-- /nav -->

# Phase 8 — NoSQL Foundations: Interview Q&A

⭐ = asked constantly.

**Q: What is NoSQL and why did it emerge?** ⭐⭐
Non-relational databases that relax some relational assumptions (fixed schema, strong consistency, single node) to gain horizontal scale, schema flexibility, or a specialized data model. It emerged when web-scale workloads outgrew what a single relational server and rigid schemas could handle.

**Q: Name the four NoSQL families with an example and use case.** ⭐⭐
Document (MongoDB) — flexible nested records. Key-value (Redis/DynamoDB) — caching, sessions, fast lookups. Wide-column (Cassandra) — massive writes, time-series. Graph (Neo4j) — relationship traversal (social, recommendations, fraud).

**Q: Explain CAP and how it applies to database choice.** ⭐⭐
During a network partition a distributed system chooses Consistency or Availability. CP systems (e.g., HBase) stay correct but may reject requests; AP systems (Cassandra, DynamoDB) stay available but may serve stale data. Pick based on whether correctness or uptime matters more for the data in question.

**Q: ACID vs BASE?** ⭐
ACID (relational): atomicity, consistency, isolation, durability — strict correctness. BASE (many NoSQL): Basically Available, Soft state, Eventual consistency — prioritize availability/scale and accept temporary inconsistency that converges. Choose ACID for money/inventory; BASE for high-scale, tolerant-of-staleness data.

**Q: Strong vs eventual consistency?** ⭐
Strong: every read returns the latest write (needed for correctness-critical data). Eventual: replicas converge over time; reads may be briefly stale (fine for feeds/counters, buys availability and lower latency). Many NoSQL systems let you tune it per operation (quorums).

**Q: How do you choose between SQL and NoSQL for a project?** ⭐⭐
By access pattern (key lookup → KV, nested docs → document, traversal → graph, ad-hoc joins → relational), consistency needs (strong → SQL/CP), scale (beyond one node → horizontally-scalable NoSQL), and schema stability. Default to Postgres (JSONB covers many document needs); add a specialized store only for a real requirement.

**Q: Do NoSQL databases support transactions/joins?**
Increasingly, partially. MongoDB added multi-document transactions; many lack multi-key transactions or rich joins by design (to enable scale). You typically denormalize/embed to avoid joins and design for single-entity operations. Assume weaker guarantees than SQL unless proven otherwise.

**Q: What is horizontal scaling and how do NoSQL DBs achieve it?**
Adding more machines rather than a bigger one. NoSQL shards data across nodes (often consistent hashing) and replicates for availability, resolving write conflicts (last-write-wins, vector clocks, CRDTs). This enables near-limitless scale at the cost of strong consistency and cross-node operations.

**Q: Is NoSQL "schema-less"? What's the catch?**
The database doesn't enforce a schema, but your application still assumes a structure — the schema moves into the code. Flexibility helps evolving data, but without discipline you get inconsistent documents and implicit coupling. "Schema-on-read" instead of "schema-on-write."

**Q: What is NewSQL?**
Distributed databases (CockroachDB, Spanner, TiDB) aiming to combine relational/ACID semantics with NoSQL horizontal scalability — SQL that scales out. An option when you need both strong consistency and scale.
