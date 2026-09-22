<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · production](../phase-7-production/NOTES.md) | [Phase 9 · mongodb ➡](../phase-9-mongodb/NOTES.md)
<!-- /nav -->

# Phase 8 — NoSQL Foundations: Interview Q&A

⭐ = asked constantly.

**Q: What is NoSQL, and why did it emerge?** ⭐⭐
NoSQL ("Not Only SQL") is an umbrella term for non-relational databases that each
relax one or more assumptions a traditional relational database makes — a fixed
up-front schema, strong consistency, or a single powerful node handling everything —
in exchange for horizontal scale, schema flexibility, or a data model specialized
for one particular access pattern. It emerged because 2000s-era web-scale workloads
ran into three pressures relational databases of that era struggled with: traffic
and data volumes beyond what one machine could serve, product/content data whose
shape needed to evolve faster than a rigid schema migration process could keep up
with, and access patterns (session lookups, social graphs, leaderboards) that map
far more naturally onto a specialized data model than onto rows and columns.

**Q: Name the four NoSQL data-model families, with an example and a concrete use
case for each.** ⭐⭐
**Document** (MongoDB) — JSON-like documents with flexible, per-document schema and
nested structure; a natural fit for a product catalog or content records whose
shape varies. **Key-value** (Redis, DynamoDB) — a value looked up by an exact key,
extremely fast; the natural fit for caching, session storage, and counters.
**Wide-column** (Cassandra, HBase) — rows with a dynamic, per-row set of columns,
built for massive write throughput; the natural fit for time-series data and logs
at large scale. **Graph** (Neo4j) — nodes and relationships as first-class
citizens, optimized for traversal; the natural fit for a social graph,
recommendation engine, or fraud-ring detection, where deep relationship hops are the
whole point of the query.

**Q: Explain the CAP theorem, and how it actually applies to choosing a
database.** ⭐⭐
In a distributed system, during a network partition (some nodes can't currently
communicate with others), you can't guarantee both Consistency (every read
reflects the latest write) and Availability (every request still gets a response)
at the same time — you have to choose one. A **CP** system (e.g. HBase, MongoDB in
its default configuration) chooses correctness: it refuses or blocks requests it
can't guarantee are correct rather than risk returning stale data. An **AP** system
(e.g. Cassandra, DynamoDB) chooses uptime: it keeps answering requests using
whatever data is locally available, accepting that the answer might be briefly
stale, and reconciles once the partition heals. The practical application: match
the choice to what the data represents — a bank balance being briefly wrong is a
real bug (favor CP), while a social media like-count being a few seconds stale
costs nothing meaningful (favor AP, and gain availability/latency in return). Note
that a single-node relational database mostly sidesteps CAP entirely, since there's
no possible network partition within one node — the trade-off only becomes real once
you replicate or distribute it.

**Q: ACID vs BASE — what's the difference, and when would you want each?** ⭐
ACID (Atomicity, Consistency, Isolation, Durability — Phase 5) is the strict
correctness contract relational databases are built around, and is typically
easiest to provide fully on a single node or a tightly-coordinated cluster. BASE
(Basically Available, Soft state, Eventual consistency) is the philosophy many
NoSQL systems are built around instead: the system stays responsive even during
failures ("basically available"), its state may be transiently inconsistent across
replicas ("soft state"), but given enough time with no new writes, it converges to
a consistent value ("eventual consistency"). Choose ACID for correctness-critical
data — money, inventory, anything where a transiently wrong value is a genuine bug.
Choose BASE for high-scale, read-heavy data that can tolerate brief staleness in
exchange for availability and lower latency — feeds, counters, view/like counts.

**Q: What's the difference between strong and eventual consistency, and how do
some systems let you tune it?** ⭐
Strong (linearizable) consistency guarantees every read returns the most recently
written value, as if there were only a single copy of the data — necessary for
correctness-critical data. Eventual consistency only guarantees that, given no
further writes, all replicas will *eventually* converge to the same value; in the
meantime, different readers hitting different replicas may briefly see different,
stale values — acceptable and often preferable for data like feeds or counters,
since it buys lower latency and higher availability. Many NoSQL systems offer
**tunable consistency** per operation via quorums: with `N` total replicas, `R`
replicas required to acknowledge a read, and `W` replicas required to acknowledge a
write, setting `R + W > N` guarantees every read's set of replicas overlaps with the
most recent write's set of replicas, giving strong-ish consistency on a per-
operation basis without requiring every single replica to respond.

**Q: How do you actually choose between SQL and NoSQL for a given project?** ⭐⭐
Work through a decision framework in order: first, the **access pattern** — simple
key lookups point to key-value, whole-nested-entity reads point to document, deep
relationship traversal points to graph, and ad-hoc/unpredictable joins and reporting
point to relational. Second, **consistency needs** — correctness-critical data
(money, inventory, uniqueness) wants strong consistency (SQL or a CP-configured
NoSQL system); tolerant-of-staleness data (feeds, counters) can trade consistency
for availability (AP-leaning NoSQL). Third, **scale** — does the workload
comfortably fit on one node, even a large one? Relational is simpler to operate.
Does it exceed that, especially on writes? A horizontally-scalable NoSQL system.
Fourth, **schema stability** — stable and genuinely relational favors SQL; highly
variable/evolving per-record favors document. Fifth, **operational cost** — every
additional database technology is more infrastructure to run and more for the team
to learn; prefer fewer distinct systems, all else equal. In practice, default to
PostgreSQL (its `JSONB` support, Phase 6, already covers many document-style
needs) and add a specialized store only once a concrete, measured requirement
demands it.

**Q: Do NoSQL databases support transactions and joins?**
Increasingly, and partially — MongoDB, for example, added multi-document ACID
transactions in more recent versions — but many NoSQL databases still deliberately
lack rich multi-key transactions or general-purpose joins *by design*, because
supporting them well is fundamentally in tension with the horizontal-scale
architecture that's the whole point of choosing that database. The idiomatic
approach in most NoSQL systems is to denormalize/embed related data (Phase 9 covers
this directly for MongoDB) so that the common operations are single-entity reads
and writes that don't need a join or a multi-document transaction at all — designing
the data model around the access pattern, rather than designing the data model
first and hoping the database can join/transact its way through whatever queries
come later. The safe assumption going in is weaker transactional/join guarantees
than a relational database, unless you've specifically confirmed otherwise for the
system in question.

**Q: What is horizontal scaling, and how do NoSQL databases typically achieve
it?**
Horizontal scaling means adding more machines to handle more load, rather than
making one machine bigger (vertical scaling). NoSQL databases typically achieve it
by **sharding** — splitting the dataset across many nodes, often using consistent
hashing so that adding or removing a node only requires reshuffling a small
fraction of the data rather than the whole dataset — combined with **replication**
for availability and fault tolerance. Because multiple nodes may be able to accept
writes for related or even the same data (especially in AP-leaning systems), this
introduces a genuinely new problem single-node databases never face: resolving
conflicting concurrent writes, via strategies like last-write-wins (simple, but can
silently discard a legitimate concurrent write), vector clocks (track causal
history to detect genuine conflicts rather than guessing), or CRDTs
(Conflict-free Replicated Data Types, specifically designed so concurrent updates
merge deterministically without any conflict at all). This architecture is what
buys near-limitless horizontal scale, at the direct cost of the strong, simple
guarantees a single-node ACID system provides for free.

**Q: Is NoSQL genuinely "schema-less"? What's the catch?**
The *database* doesn't enforce a fixed schema — you can write a document with a
completely different shape than the one before it, and the database won't reject
it. But the *application* reading that data still assumes some structure to make
sense of it; the schema doesn't disappear, it just moves out of the database and
into application code, where it's enforced far less rigorously (and far less
visibly) than a `CREATE TABLE` statement enforces it. This is often described as
**schema-on-read** (the shape is interpreted when the data is read, by whatever
code reads it) versus a relational database's **schema-on-write** (the shape is
enforced the moment data is written, by the database itself). Flexibility genuinely
helps with rapidly evolving data, but without real discipline (careful application-
level validation, or something like a JSON Schema layer) it tends to produce a
collection full of subtly inconsistent documents and implicit, undocumented coupling
between "whatever shape the data happens to be" and "whatever shape the application
code assumes it is."

**Q: What is NewSQL, and what specific problem is it solving?**
NewSQL databases (CockroachDB, Google Spanner, TiDB) aim to combine the relational
model and full ACID transactional guarantees of a traditional SQL database with the
horizontal, multi-machine scalability that historically only NoSQL systems
offered — "distributed SQL." It's the answer for a workload that genuinely needs
*both* strong consistency across related records *and* to scale writes/storage
beyond a single node, a combination that a traditional single-node relational
database and a classic sharded NoSQL system each only solve half of on their own.
The real-world cost is usually architectural complexity and some added write
latency, since keeping multiple distributed nodes strongly consistent requires a
consensus protocol (Raft or a Paxos-family algorithm) coordinating writes across
them.
