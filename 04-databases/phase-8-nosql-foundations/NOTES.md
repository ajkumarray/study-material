<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 7 · production](../phase-7-production/NOTES.md) | [Phase 9 · mongodb ➡](../phase-9-mongodb/NOTES.md)
<!-- /nav -->

# Phase 8 — NoSQL Foundations: Notes (Theory)

This phase is the bridge from the relational world (Phases 1–7) to the "Part B" of
this track — MongoDB, Redis, and Neo4j. "NoSQL" ("Not Only SQL") is an umbrella
term for non-relational databases that each trade away some of SQL's guarantees —
a fixed schema, strong consistency, or general-purpose joins — in exchange for
massive horizontal scale, schema flexibility, or a data model specialized for one
particular access pattern. Much of the systems theory here overlaps with the
System Design track's CAP/consistency material — this phase frames the same ideas
from the data-modeling angle.

---

## 1. Why NoSQL Exists, and the Four Data-Model Families

Traditional relational databases assume a fixed, up-front schema, strong
consistency, and (historically) a single powerful node doing all the work. The
2000s-era explosion of web-scale applications ran into three pressures relational
databases of that era struggled with: **massive horizontal scale** (more traffic
and data than one machine, however powerful, could handle), **flexible/evolving
schemas** (product data, user-generated content, and rapidly changing feature sets
that don't fit a rigid upfront table design), and **specialized access patterns**
(a social graph, a leaderboard, a session store — each naturally modeled far more
simply by something other than rows and columns). Each NoSQL family relaxes a
different relational assumption to win one of these three pressures.

### Key Concepts

- **Document databases** (MongoDB — Phase 9): store JSON-like documents, each of
  which can have a different shape, with support for deeply nested structures. Best
  suited to flexible or evolving records, and to access patterns centered on
  reading/writing one whole entity at a time.
- **Key-value stores** (Redis, DynamoDB — Phase 10): the simplest possible model —
  look up a value by an exact key — but extremely fast, since there's essentially
  no query planning involved. Best suited to caching, session storage, counters,
  and any workload dominated by simple key lookups.
- **Wide-column stores** (Cassandra, HBase): rows that can each have a different,
  dynamic set of columns, engineered for enormous write throughput. Best suited to
  time-series data and log-style workloads at very large scale.
- **Graph databases** (Neo4j — Phase 11): nodes and the relationships between them
  are both first-class citizens, and the storage engine is built specifically to
  make traversing those relationships fast regardless of how many hops deep the
  traversal goes. Best suited to many-to-many, deeply connected data — social
  graphs, recommendation engines, fraud-ring detection.
- Most NoSQL databases also default to **horizontal scaling** (sharding data across
  many commodity machines is built into the architecture from day one) and
  **schema flexibility** (the database itself doesn't enforce a fixed structure),
  in contrast to a traditional single-node relational database.

### Worked Example — the Same Data, Two Shapes

A relational, normalized shape for a blog post and its comments (Phase 3's
normalization applied):
```sql
-- post(id, title, author_id)
-- comment(id, post_id, author_id, body)
SELECT p.title, c.body FROM post p JOIN comment c ON c.post_id = p.id WHERE p.id = 1;
```

The same data, denormalized as one MongoDB-style document, embedding comments
directly inside the post they belong to:
```js
// a single document in a "posts" collection
{
  _id: 1,
  title: "Why Indexes Matter",
  author: "ajay",
  comments: [
    { author: "meera", body: "Great explanation!" },
    { author: "ravi",  body: "Finally makes sense." }
  ]
}
```
In this example: the relational version needs a `JOIN` to reassemble one post with
its comments, because normalization (Phase 3) deliberately split them into two
tables to avoid duplicating data and to support querying comments independently.
The document version needs no join at all — the whole post, with every comment
that belongs to it, is fetched by ID in a single read, because the document model
embraces embedding related data directly rather than normalizing it out. This
single before/after is the entire philosophical difference between the two data
models in miniature: normalize for update integrity and flexible cross-cutting
queries (relational), or embed for fast, simple reads of one whole entity at a time
(document).

### Comparison Table

| Family | Example | Data unit | Sweet spot |
|---|---|---|---|
| Document | MongoDB | a JSON-like document | flexible/evolving records, whole-entity reads |
| Key-value | Redis, DynamoDB | key → value | caching, sessions, counters, ultra-fast lookups |
| Wide-column | Cassandra, HBase | a row with dynamic columns | huge write throughput, time-series/logs |
| Graph | Neo4j | nodes + relationships | many-to-many traversal, social/fraud/recommendations |

### Why It's Useful

Recognizing which family a given problem actually maps onto is the single most
important decision in NoSQL database selection — a leaderboard implemented as
documents in MongoDB works, but a sorted set in Redis (`ZADD`/`ZRANGE`, Phase 10)
solves it with a few commands and near-zero latency, purpose-built for exactly that
shape of problem.

### Summary / Key Takeaways

- NoSQL relaxes a relational assumption (schema rigidity, strong consistency, or
  single-node joins) to specialize for scale, flexibility, or a specific access
  pattern.
- Document = flexible nested records; key-value = blazing-fast simple lookups;
  wide-column = massive write throughput; graph = relationship traversal.
- Most NoSQL databases build horizontal scaling and schema flexibility in from the
  start, unlike a traditional single-node relational database.
- Choosing the right family starts with asking "what does this workload's access
  pattern actually look like?" — not "which database is popular right now."

---

## 2. CAP Theorem and Consistency Models

In a **distributed** system — one with multiple nodes that can, in principle, fail
to talk to each other — the CAP theorem says that during a **network partition**
(some nodes can't communicate with others), a system must choose between
**Consistency** (every read reflects the most recent write) and **Availability**
(every request still gets *some* response) — it cannot guarantee both at once
during that partition.

### Key Concepts

- **CP systems**: choose consistency over availability during a partition — they
  refuse or block requests that can't be guaranteed correct rather than risk
  returning stale data. Examples: HBase, MongoDB in its default configuration.
- **AP systems**: choose availability over consistency during a partition — they
  keep serving requests using whatever local data is available, and reconcile
  differences once the partition heals. Examples: Cassandra, DynamoDB, Riak.
- A single-node relational database mostly **sidesteps** CAP entirely — there's no
  network partition possible within one node — but the moment you replicate or
  distribute it across multiple nodes, it faces the exact same trade-off.
- **Consistency models are a spectrum, not a binary choice**:
  - **Strong (linearizable)** consistency — every read returns the most recent
    write, as if there were only one copy of the data.
  - **Causal** consistency — operations that are causally related (a reply to a
    comment) are seen in the correct order by everyone, but unrelated operations
    may be seen in different orders by different readers.
  - **Eventual** consistency — given enough time with no new writes, all replicas
    converge to the same value; in the meantime, different readers may briefly see
    different, stale values. This is the common NoSQL default.
- **Tunable consistency**: many NoSQL systems let you choose consistency strength
  per operation rather than system-wide, typically via **quorum** reads/writes —
  if `R` replicas must acknowledge a read, `W` replicas must acknowledge a write,
  and `N` is the total replica count, then `R + W > N` guarantees every read
  overlaps with the most recent write's set of acknowledging replicas, giving
  strong-ish consistency without requiring *all* replicas to respond.

### Worked Example

Illustrating the CP vs AP choice with a concrete scenario — three replicas of a
value, `A`, `B`, `C`, and a network partition that splits `A` off from `B` and `C`:
```
Before partition: A=B=C=42

Partition happens: A is isolated; B and C can still talk to each other.
A client writes 99 to the side with B and C (a majority, 2 of 3): B=C=99, A still =42.

A client now reads from A (the isolated node):
  CP behavior: A refuses to answer (or returns an error) rather than risk
               returning the stale value 42 as if it were current.
  AP behavior: A answers immediately with 42 — available, but stale/inconsistent
               with what B and C now hold.
```
In this example: neither behavior is "wrong" in the abstract — it's a deliberate
design choice baked into the database, and the right choice depends entirely on
what the data represents. A bank balance being briefly wrong is unacceptable (favor
CP); a social media "like" count being briefly a few seconds stale is a completely
acceptable trade for staying available (favor AP).

### Comparison Table

| | Strong consistency | Eventual consistency |
|---|---|---|
| Guarantee | every read sees the latest write | replicas converge, given no new writes |
| Latency/availability cost | higher (may wait for quorum/leader) | lower (any replica can answer immediately) |
| Typical data | money, inventory, uniqueness constraints | social feeds, view/like counts, caches |
| Example systems | traditional RDBMS, CP-configured NoSQL | Cassandra, DynamoDB (default), DNS |

### Why It's Useful

CAP isn't an abstract theorem to memorize for its own sake — it directly predicts
the concrete, observable behavior of a distributed database during a real network
event (which happens regularly at scale: a rack loses power, a data center link
flakes). Knowing whether your database is CP- or AP-leaning tells you exactly what
kind of bug/behavior to expect during an incident, and lets you design the
application layer (retries, idempotency, conflict resolution) around that reality
instead of being surprised by it.

### Summary / Key Takeaways

- During a network partition, a distributed system must choose Consistency or
  Availability — not both. CP systems refuse/block; AP systems keep serving
  possibly-stale data.
- A single-node database sidesteps CAP; replicating or distributing it reintroduces
  the trade-off.
- Consistency is a spectrum (strong → causal → eventual), and many systems offer
  tunable consistency per operation via quorum reads/writes (`R + W > N`).
- Choose based on the data: correctness-critical data (money, inventory, uniqueness)
  wants strong consistency; tolerant-of-staleness data (feeds, counters, likes)
  can trade consistency for availability and lower latency.

---

## 3. BASE vs ACID, and Horizontal Scaling

Where relational databases (Phase 5) are built around **ACID** guarantees, many
NoSQL databases are instead built around a philosophy summarized as **BASE** —
prioritizing availability and scale over strict, immediate correctness.

### Key Concepts

- **ACID** (relational, Phase 5): **A**tomicity, **C**onsistency, **I**solation,
  **D**urability — a strict correctness contract, typically easiest to provide
  fully on a single node (or a tightly-coordinated cluster).
- **BASE** (common NoSQL philosophy): **B**asically **A**vailable, **S**oft
  state, **E**ventual consistency — the system stays responsive even during
  failures or partitions ("basically available"), its state may be in flux and not
  immediately consistent across replicas ("soft state"), but given enough time it
  converges to a consistent value ("eventual consistency"). This is the practical,
  engineering-level expression of the AP side of the CAP trade-off.
- **Horizontal scaling** is native to most NoSQL databases: data is **sharded**
  (split and distributed) across many nodes — often using **consistent hashing**
  so that adding or removing a node only requires reshuffling a small fraction of
  the data — and **replicated** across nodes for availability and fault tolerance.
- **Write conflict resolution**: because multiple nodes may be able to accept
  writes independently (especially in AP systems), conflicting concurrent writes to
  the same key have to be resolved somehow: **last-write-wins** (simplest, but can
  silently lose a legitimate concurrent write), **vector clocks** (track causal
  history to detect true conflicts rather than guessing), or **CRDTs**
  (Conflict-free Replicated Data Types — data structures specifically designed so
  concurrent updates merge automatically and deterministically, without conflict).

### Worked Example — ACID vs BASE, Concretely

```
ACID transaction (a bank transfer, relational):
  BEGIN;
  UPDATE account SET balance = balance - 100 WHERE id = 1;
  UPDATE account SET balance = balance + 100 WHERE id = 2;
  COMMIT;
  -- Either BOTH updates land, or NEITHER does. No reader ever sees a half-applied
  -- transfer. This is atomicity + isolation, guaranteed by the database.

BASE-style operation (a "like" counter, typical NoSQL AP system):
  increment_like_count(post_id=42)
  -- Applied optimistically on whichever replica received the write. It propagates
  -- to other replicas asynchronously. For a brief window, two different readers
  -- hitting two different replicas might see DIFFERENT like counts for the SAME
  -- post. Given enough time with no new writes, every replica converges to the
  -- same final count.
```
In this example: the bank transfer's correctness is worth the coordination cost of
a strict transaction — a half-applied transfer is a real, unacceptable bug. The
like-counter's brief inconsistency is a cost worth paying in exchange for the system
staying fast and available under massive concurrent load — nobody is meaningfully
harmed by seeing "1,204 likes" on one page load and "1,206 likes" a moment later on
a refresh.

### Comparison Table

| | ACID | BASE |
|---|---|---|
| Priority | strict correctness | availability + scale |
| Consistency | immediate, guaranteed | eventual, given no new writes |
| Typical home | relational databases, single-node/CP systems | many NoSQL, AP-leaning systems |
| Good fit for | money, inventory, anything correctness-critical | feeds, counters, caches, high-scale reads |

### Why It's Useful

Horizontal scaling via sharding + replication is genuinely how systems handle
traffic and data volumes far beyond what any single machine could serve — but it's
not "free" scale; it's scale purchased specifically by relaxing ACID down to BASE.
Understanding this trade-off explicitly is what lets you decide, per piece of data
in a real system, whether that trade is worth making.

### Summary / Key Takeaways

- ACID prioritizes strict, immediate correctness; BASE prioritizes availability and
  scale, accepting temporary inconsistency that resolves itself over time.
- Horizontal scaling — sharding (often via consistent hashing) plus replication —
  is how NoSQL systems achieve scale ACID-strict single-node systems can't easily
  match.
- Because multiple nodes can accept writes, conflict resolution (last-write-wins,
  vector clocks, CRDTs) becomes a real design concern that a single-node ACID
  system never has to face.
- This isn't "NoSQL is worse" — it's a deliberate, explicit trade of some
  correctness guarantees for availability and horizontal scale, appropriate for
  some data and not others.

---

## 4. Choosing a Database — a Decision Framework

Given the range of options, this is a practical, ordered framework for actually
deciding what to reach for on a real project.

### Key Concepts — Ask, in Order

1. **Access pattern** — how will the data actually be read and written? Simple key
   lookups → key-value. Reading/writing one whole nested entity at a time →
   document. Deep, many-hop relationship traversal → graph. Ad-hoc joins,
   reporting, and queries you can't fully predict up front → relational.
2. **Consistency needs** — does the data represent something like money, inventory,
   or a uniqueness constraint, where being briefly wrong is a real bug? → favor
   strong consistency (SQL, or a CP-configured NoSQL system). Is it something like
   a feed, a like count, or a view counter, where brief staleness is genuinely
   harmless? → eventual consistency (AP) is a perfectly reasonable, often better,
   choice.
3. **Scale** — does the workload comfortably fit on one (even a large) node? →
   relational is simpler to build, operate, and reason about. Does it exceed what
   one node can handle, or is it fundamentally write-throughput-bound at a scale a
   single node can't sustain? → a horizontally-scalable NoSQL system.
4. **Schema** — is the data's shape stable and genuinely relational (rows with
   consistent columns, meaningful foreign-key relationships)? → SQL. Is it highly
   variable or rapidly evolving per record? → document.
5. **Operational cost** — every additional datastore technology in a system is more
   infrastructure to run, monitor, back up, secure, and for the team to actually
   know how to operate well. All else being close to equal, prefer fewer distinct
   database technologies, not more.

### Worked Example — Applying the Framework

A social app needs: user accounts and payments, a news feed, and a "people you may
know" feature.
```
User accounts + payments:
  -> correctness-critical (money, uniqueness of email/username), ad-hoc reporting
     queries likely needed -> RELATIONAL (Postgres)

News feed (each post: text, author, timestamp, nested comments/reactions):
  -> flexible, evolving shape per post, read/written as whole entities
     -> DOCUMENT (MongoDB) — or Postgres + JSONB if the team wants to stay on one system

"People you may know" (friend-of-a-friend, mutual-connections traversal):
  -> deep relationship traversal, the access pattern relational JOINs handle
     increasingly poorly as hop count grows -> GRAPH (Neo4j)
```
In this example: no single database family is "correct" for the whole
application — each subsystem's access pattern and consistency needs point toward a
different natural fit. This is exactly the reasoning covered fully in Phase 12
(polyglot persistence): using several databases deliberately, each for the part of
the system it's genuinely best suited to, rather than forcing one database to serve
every access pattern equally poorly.

### Defaults and Reality

Start with **PostgreSQL**. It handles the overwhelming majority of real
applications well, and with `JSONB` (Phase 6) it already covers a meaningful slice
of what used to specifically require a document database — all while keeping ACID
transactions, real joins, and one system to learn, operate, and monitor. Add a
specialized store only when a *real*, concrete requirement demands it — Redis for
caching/sessions (Phase 10) is by far the most common and lowest-risk addition to
make. Using several databases deliberately, each for what it's genuinely best at,
is **polyglot persistence** (Phase 12). The one thing to actively guard against is
"resume-driven development" — adopting a trendy NoSQL database because it's
interesting to learn or looks good on a resume, rather than because the actual
problem in front of you demands it.

### Why It's Useful

This framework is precisely what a "design a system with database X, Y, Z" style
interview question is testing: not whether you know NoSQL trivia, but whether you
can map a described access pattern and consistency requirement onto the right tool,
and articulate *why*, in the same structured way this section walks through.

### Summary / Key Takeaways

- Choose based on access pattern first (lookups, whole-entity reads, deep
  traversal, or ad-hoc joins), then consistency needs, then scale, then schema
  stability, then operational cost.
- Default to relational (Postgres); introduce a specialized NoSQL store only for a
  concrete, proven requirement — not speculatively or because it's trendy.
- Different subsystems within the same application can and often should use
  different databases, each matched to that subsystem's actual access pattern.
- Every additional database technology is real, ongoing operational cost — weigh
  it deliberately, not casually.
