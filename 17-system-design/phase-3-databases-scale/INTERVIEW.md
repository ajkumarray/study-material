<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching](../phase-2-caching/NOTES.md) | [Phase 4 · idempotency ➡](../phase-4-idempotency/NOTES.md)
<!-- /nav -->

# Phase 3 — Databases at Scale: Interview Q&A

⭐ = asked constantly.

**Q: Replication vs. sharding — what does each actually scale, and do you need both?** ⭐⭐
Replication keeps copies of the *same* data on multiple nodes — it scales **reads**
(more nodes can serve them) and buys **availability** (promote a replica if the leader
dies). Sharding splits the data itself across nodes, each holding a *subset* — it scales
**writes** and total **storage**, since no single node has to hold or write all the
data. They're complementary, not alternatives: large systems typically shard the data
*and* replicate each shard, so you get both write/storage scale and per-shard
availability/read-scaling at the same time.

**Q: Walk through leader-follower replication — how do reads and writes flow, and
what's the catch?** ⭐
All writes go to the leader; the leader streams its changes to one or more followers;
reads can be served by the leader or by any follower. The catch is replication lag —
followers apply changes asynchronously (usually), so a follower can be milliseconds to
seconds behind at any instant. This produces the read-your-writes problem: a user writes
something, immediately re-reads it, and the read lands on a follower that hasn't caught
up — they see stale data right after their own write. Fixes: route a user's read of
their own very-recent write specifically to the leader, use synchronous replication for
that write (trading latency for guaranteed follower freshness), or just have the write
response itself carry the new state so the client doesn't need an immediate re-read at
all.

**Q: Synchronous vs. asynchronous replication — how do they trade off?**
Synchronous: the leader waits for a follower's acknowledgment before confirming the
write to the client — durable (the follower already has it before you say "success")
and consistent, but slower and less available (a slow or unreachable follower can stall
every write). Asynchronous: the leader confirms immediately without waiting — fast and
highly available for writes, but the most recent writes can be permanently lost if the
leader dies before it finishes replicating them. Semi-synchronous replication (wait for
just one follower, not all) is a common middle ground. This maps directly onto CAP/
PACELC (Phase 1.5): synchronous leans CP/consistency-favoring, asynchronous leans
AP/latency-favoring.

**Q: What is quorum-based (leaderless) replication and how does `R + W > N` give you
consistency without a single leader?** ⭐
In a leaderless system with `N` replicas, a write succeeds once `W` replicas acknowledge
it, and a read queries `R` replicas and reconciles their answers (picking the newest by
version/timestamp). If you set `R + W > N`, any read quorum and any write quorum are
guaranteed to overlap by at least one replica — by pigeonhole, you can't pick `R`
replicas for a read and `W` replicas for a write, both subsets of `N`, without at least
one replica being in both, if `R + W > N`. That overlapping replica is guaranteed to
have seen the latest write, so at least one of your `R` read responses reflects it. This
lets you tune consistency, availability, and latency per operation — fast reads with
`R=1, W=N`, fast writes with `R=N, W=1`, or a balanced `R=W=majority`, all without a
single point-of-failure leader.

**Q: What is consistent hashing and what problem does it actually solve?** ⭐⭐
The naive sharding rule `node = hash(key) % N` breaks catastrophically the moment `N`
changes — adding or removing even one node changes the modulo result for almost every
key, because there's no relationship between `hash(k) % N` and `hash(k) % (N+1)`. In the
demo, going from 4 nodes to 5 remaps 80% of 10,000 sample keys — for a sharded database
that's a massive, simultaneous data migration; for a distributed cache it's a stampede
across nearly the entire keyspace at once. Consistent hashing fixes this by placing both
nodes and keys as points on a fixed hash ring; a key belongs to whichever node's point
comes first going clockwise from the key's hash. Adding or removing a node only affects
the ring arc near it, moving roughly `1/N` of keys instead of nearly all of them — in the
demo, adding a 4th node to a 3-node ring only moves 2 of 6 sample keys. Virtual nodes
(many ring points per physical node, e.g. 150) even out the load distribution and smooth
out how much moves per rebalance. Used by Cassandra, DynamoDB, Redis Cluster, CDNs, and
distributed cache clients.

*Follow-up: why do you need virtual nodes at all — why not just place each physical node
at one point on the ring?* With only a handful of physical nodes at single ring
positions, the arcs between them can be wildly uneven by chance (one node might "own" 60%
of the ring, another 5%), so load wouldn't actually spread evenly. Spreading each
physical node across many ring positions (virtual nodes) averages this out — statistically
each physical node ends up owning close to its fair `1/N` share regardless of how few
physical nodes there are.

**Q: How do you choose a shard key?** ⭐
It needs to do two things at once: spread load evenly (avoid a sequential key like an
auto-incrementing order ID under range partitioning, where all new traffic piles onto
the newest, highest-id shard while every other shard sits idle — hash the key, or a
composite like `user_id + random_suffix`, instead), and co-locate data that's commonly
queried together (shard by `user_id` so "all of this user's orders" stays on one shard
and doesn't require scatter-gather, even though "all orders placed in the last hour
across every user" now genuinely has to fan out to every shard). It's the hardest
decision to change later — resharding an entire dataset in production is a major
operational undertaking — so get it right based on the dominant access pattern up front.

**Q: What's hard about sharding, beyond picking the key?**
Cross-shard joins, multi-row transactions, and aggregations are slow (scatter-gather:
query every shard, merge in the application) or simply unsupported by the database
engine. Rebalancing when you add/remove shards is the consistent-hashing problem above.
A poorly chosen key creates hotspots. And operationally, you now have N databases to
back up, monitor, and migrate schema changes across instead of one. Because of all this,
shard only after query optimization, caching, and read replicas genuinely aren't enough
— it's the last, hardest-to-undo rung of the scaling ladder.

**Q: SQL vs. NoSQL — how do you actually decide?** ⭐⭐
SQL/relational for strong consistency, full ACID transactions, and complex/ad-hoc
relational queries — payments, orders, anything where a partial or inconsistent write is
unacceptable. NoSQL when you need massive horizontal scale, a flexible/evolving schema,
or the access pattern specifically matches one of the NoSQL families: document (Mongo)
for nested, variable-shape entities usually read/written whole; key-value (Redis/Dynamo)
for simple, extremely fast lookup-by-id (caches, sessions); wide-column (Cassandra) for
enormous write throughput on time-series-shaped data; graph (Neo4j) for
relationship-traversal-heavy queries that would need many expensive joins relationally.
NoSQL stores are typically eventually consistent and horizontally scalable by design,
with limited joins/multi-row transactions. Many real systems use several of these at
once (polyglot persistence) rather than forcing one store to do everything.

**Q: How do you scale a read-heavy database, step by step?**
In order, because each step is cheaper and more reversible than the next: optimize
queries and add indexes (often solves the whole problem, Phase 8.2) → add a cache in
front of the database (Phase 2, absorbs load with zero database architecture change) →
add read replicas and route reads to followers (3.1) → denormalize or add materialized
views for genuinely expensive aggregates → shard only if write volume or storage,
specifically, exceed what a single (replicated) node can handle. Don't shard to solve a
read-scaling problem — replicas solve that more cheaply.

**Q: How do you handle a hot partition or hot key?**
Change the shard key to spread that entity's load (hash-based, or append a random/bucketed
suffix so one hot entity's writes fan out across several shards instead of concentrating
on one), cache the hot key/entity in front of the store so most reads never reach the
shard at all, or explicitly replicate that key's value across multiple nodes. Detect it
with per-shard/per-key load metrics — aggregate cluster metrics can look healthy while
one specific shard is saturated.

**Q: What is polyglot persistence, and what's the trade-off?**
Using multiple databases in the same system, each chosen for the access pattern it's
actually best at — e.g., Postgres for the transactional order/payment core, Redis for
sessions and caching, Elasticsearch for full-text search, a columnar warehouse for
analytics — rather than forcing one general-purpose database to serve every workload.
The trade-off is operational: more systems to run, monitor, back up, and keep data
consistent across (often via events/CDC rather than shared transactions), in exchange
for each workload getting a store genuinely suited to it instead of a compromise.
