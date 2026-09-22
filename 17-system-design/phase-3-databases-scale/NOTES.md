<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · caching](../phase-2-caching/NOTES.md) | [Phase 4 · idempotency ➡](../phase-4-idempotency/NOTES.md)
<!-- /nav -->

# Phase 3 — Databases at Scale: Notes

Caching (Phase 2) buys you headroom, but eventually one database node runs out of either
capacity to serve reads or capacity to store/write data. There are exactly two axes to
scale a database beyond a single node: **replication** (copies of the same data, for
read scaling and availability) and **partitioning/sharding** (splitting the data itself,
for write scaling and storage capacity). `ConsistentHashingDemo.java` in this directory
demonstrates the single trickiest mechanical problem in sharding — rebalancing — and its
standard fix.

## 3.1 — Replication: leader-follower

Keep multiple copies of the same data on different nodes so more machines can serve
reads and the system survives losing any one of them.

### Key Concepts

- **Leader-follower (primary-replica)** — all writes go to one designated leader node,
  which then streams its changes to one or more follower nodes. Reads can be served by
  either the leader or any follower. This is the dominant replication model across
  relational databases (Postgres, MySQL) and many NoSQL stores.
- **Read scaling** — because followers can serve reads, adding more followers linearly
  increases read capacity without touching the write path at all. This directly targets
  the read-heavy workloads that Phase 1's back-of-envelope estimation so often surfaces
  (a 50:1 read:write ratio means most of your capacity problem is reads).
- **Failover** — if the leader dies, one follower is promoted to be the new leader
  (manually, or automatically via a consensus mechanism like Raft/Paxos-based tooling).
  This is what gives leader-follower replication its availability benefit, not just its
  read-scaling benefit.
- **Replication lag** — followers apply the leader's changes asynchronously (usually),
  so at any instant a follower may be milliseconds to seconds behind the leader. A read
  routed to a lagging follower right after a write can return stale data — the
  **read-your-writes** problem: a user updates their profile, immediately reloads the
  page, and the read hits a follower that hasn't caught up yet, showing the old value.

### Worked example — the read-your-writes problem and its fix

```
1. Client PUTs a profile update -> write goes to the LEADER, committed.
2. Leader begins replicating the change to follower-2 (say, 40ms of lag).
3. Client immediately GETs its own profile -> load balancer routes the
   read to follower-2, which HASN'T applied the update yet.
4. User sees their OLD profile data for up to ~40ms -> confusing bug report.

Fix options:
  (a) Route a user's read of their OWN recent write to the LEADER
      specifically (common pattern: "read-your-writes" routing, often via
      a short-lived sticky hint after a write).
  (b) Use SYNCHRONOUS replication for that write (leader waits for the
      follower to confirm before acknowledging) -> slower write, but the
      follower is guaranteed caught up by the time the client sees success.
  (c) Client-side: have the write response carry the new data directly,
      so the immediate re-render doesn't need a fresh read at all.
```

### Comparison table — synchronous vs. asynchronous replication

| | Synchronous | Asynchronous |
|---|---|---|
| Leader waits for follower ack? | Yes, before confirming the write | No, confirms immediately |
| Write latency | Higher (round trip to follower) | Lower |
| Data loss on leader failure | None — follower already had it | Possible — last writes not yet replicated are lost |
| Availability of writes | Lower (a slow/down follower can stall writes) | Higher |
| CAP leaning | CP-ish (favors consistency) | AP-ish (favors availability/latency) |
| Real-world use | Critical writes (financial), or a "semi-sync" hybrid | The common default for most systems |

### Why it's useful

Leader-follower replication is the same fundamental pattern used by Kafka's
leader/ISR model (track 16) and most managed cloud databases — recognizing it once means
recognizing it everywhere. In an interview, naming the read-your-writes problem and its
fix specifically (rather than just "replicas can lag") signals you've actually reasoned
about what replication lag *does* to a user, not just that it exists.

## 3.2 — Multi-leader and leaderless replication

Leader-follower isn't the only model — some systems accept writes on multiple nodes at
once, trading strong consistency for availability and geographic flexibility.

### Key Concepts

- **Multi-leader replication** — more than one node accepts writes (often one leader per
  data center, for geo-distribution), and leaders replicate to each other. Improves
  write availability and latency for geographically distributed users, but introduces
  **write conflicts**: two leaders can accept different writes to the same record
  concurrently, and something must reconcile them.
- **Leaderless / Dynamo-style replication** — any replica can accept a read or write;
  consistency is achieved through **quorums**: a write succeeds once acknowledged by `W`
  replicas, a read queries `R` replicas and reconciles. Setting `R + W > N` (where `N` is
  the total replica count) guarantees every read quorum overlaps with every write
  quorum, giving you a tunable strong-ish consistency without a single leader.
- **Conflict resolution** — when concurrent writes genuinely conflict (multi-leader or
  leaderless), you need a policy: **last-write-wins (LWW)** (simplest, but silently
  discards one of the writes — data loss by another name), **vector clocks** (track
  causality so you can at least detect true concurrent conflicts vs. sequential ones and
  surface them for merging), or **CRDTs** (Conflict-free Replicated Data Types —
  data structures mathematically designed so concurrent updates always merge
  deterministically without conflict, e.g. a grow-only counter or an OR-set).
- These are **AP-leaning systems** (Phase 1.5) — Cassandra and DynamoDB are the classic
  examples, explicitly trading strict consistency for availability and low write latency
  everywhere.

### Worked example — a quorum read/write

```
N = 3 replicas of a key. Configure W = 2, R = 2  (R + W = 4 > N = 3 -> overlap guaranteed)

Write "balance=100": client writes to all 3, waits for ANY 2 acks -> succeeds
  even if replica C is temporarily unreachable.

Read: client reads from ANY 2 replicas. Since W=2 and R=2 out of N=3, at
  least one of the 2 replicas you read from MUST have been part of the 2
  that acknowledged the write (pigeonhole: 2 + 2 > 3) -> you're guaranteed
  to see the latest write in at least one of your R responses, and can
  pick the newest by timestamp/version.

Tuning: R=1, W=N gives fast reads but slow, less available writes.
        R=N, W=1 gives fast writes but slow, less available reads.
        R=W=majority is the common balanced choice.
```

### Why it's useful

Quorums let you tune the consistency/availability/latency trade-off **per operation**
rather than picking one extreme for the whole system — this is the practical answer to
"CAP says pick one, but our system seems to offer both most of the time": most of the
time there's no partition, and quorums give you strong-ish guarantees; during an actual
partition, the system degrades gracefully rather than failing outright, which is the AP
promise.

## 3.3 — Partitioning and sharding

Replication copies the *same* data everywhere; partitioning (sharding) splits the data
itself, so each node only holds a subset. This is what scales writes and total storage
beyond what a single machine can hold.

### Key Concepts

- **Range partitioning** — split by key ranges (e.g., user IDs 1-1M on shard 1, 1M-2M on
  shard 2). Great for range queries ("all orders between dates X and Y") because a range
  scan often touches only one or a few shards, but risks **hotspots** on sequential keys
  — if IDs are assigned sequentially and most recent writes go to the highest range,
  that one shard absorbs all new write traffic while older shards sit idle.
- **Hash partitioning** — hash the shard key and use the hash to pick a shard (`shard =
  hash(key) % N`). Spreads load evenly regardless of key distribution, but destroys
  range-query locality (a range scan now has to fan out to every shard, since
  sequentially adjacent keys hash to unrelated shards).
- **Directory/lookup-based partitioning** — a separate lookup service maps each key (or
  key range) to its shard explicitly. Flexible (can rebalance individual keys without a
  formula change) but adds an extra network hop and a new dependency/SPOF to manage.
- **Geographic partitioning** — shard by region (EU users' data in an EU shard) for
  data-residency compliance and to reduce cross-region latency, independent of the
  hashing scheme used within a region.

### Worked example — choosing a shard key badly vs. well

```
Bad shard key: an auto-incrementing order_id, range-partitioned.
  -> ALL new orders (today's traffic) land on the newest, highest-id
     shard. Every other shard is cold. One shard is overloaded while
     N-1 sit idle -> defeats the entire point of sharding.

Better shard key: hash(user_id).
  -> New orders spread evenly across all shards (assuming users are
     roughly evenly active), because user_id hashes are uniformly
     distributed regardless of order recency.

But: "get all orders for user X" now stays on ONE shard (good - no
scatter-gather) while "get all orders placed in the last hour across
all users" now requires querying EVERY shard (scatter-gather, unavoidable
for a query that doesn't align with the shard key).
```

The **shard key decision is the single most important, hardest-to-change decision** in a
sharded system: it determines both how evenly load spreads and which queries stay
single-shard (fast) vs. become scatter-gather (slow, and hard to make transactional).

### Why it's useful

Sharding is what makes systems like a global social network's post store, a multi-tenant
SaaS database, or a payments ledger physically possible past a certain scale — no single
machine's disk or write throughput would otherwise be enough. But it's also the point
after which the system stops being "a database" in the simple sense and starts requiring
active operational thought about rebalancing, cross-shard queries, and hotspots — which
is exactly why the scaling ladder (3.5) says shard last.

## 3.4 — The rebalancing problem and consistent hashing

`ConsistentHashingDemo.java` exists specifically to make this problem and its fix
concrete and measurable, not just described in prose.

### Key Concepts

- **The naive rebalancing catastrophe** — the simplest possible sharding rule,
  `node = hash(key) % N`, works fine until `N` changes (you add or remove a node). The
  moment `N` changes, the modulo result for *almost every key* changes too, because `%`
  has no relationship between consecutive values of `N` — nearly the entire dataset (or
  cache) has to be moved to new nodes simultaneously.
- **Consistent hashing** — place both nodes and keys onto points on a fixed hash ring
  (e.g., `0` to `2^32 - 1`). A key belongs to whichever node's point is the first one
  encountered going clockwise from the key's own hash position. Adding or removing a
  node only affects the small arc of the ring between it and its immediate neighbor —
  moving roughly `1/N` of keys instead of nearly all of them.
- **Virtual nodes** — instead of placing each physical node at one point on the ring,
  place it at many points (e.g., 150 virtual points per physical node, spread around the
  ring). This evens out load (a single physical node's real share of the ring converges
  to its fair `1/N` even with a small number of physical nodes) and smooths the size of
  what moves during a rebalance.
- **Where it's used**: Cassandra and DynamoDB (partitioning), Redis Cluster (hash slots
  are a related-but-distinct fixed-16384-slot variant), CDNs (routing a request to the
  nearest/least-loaded edge), and distributed cache clients (routing a key to a
  Memcached/Redis node without a central coordinator).

### Worked example — `ConsistentHashingDemo`, annotated

```java
// naive hash % N: changing N reshuffles almost everything
static void naiveRemapCost() {
    int keys = 10_000, moved = 0;
    for (int k = 0; k < keys; k++) {
        int before = Math.floorMod(Integer.hashCode(k), 4);   // 4 nodes
        int after  = Math.floorMod(Integer.hashCode(k), 5);   // add one -> 5 nodes
        if (before != after) moved++;
    }
}
// output: going 4 -> 5 nodes remapped 8,000 / 10,000 keys (80%)  <-- catastrophic
```

```java
// consistent hashing: nodes and keys on a ring (a TreeMap keyed by hash)
class ConsistentHash {
    SortedMap<Integer, String> ring = new TreeMap<>();
    void addNode(String node) {
        for (int i = 0; i < vnodes; i++) ring.put(hash(node + "#" + i), node);
    }
    String getNode(String key) {
        int h = hash(key);
        SortedMap<Integer, String> tail = ring.tailMap(h);   // first vnode clockwise
        int point = tail.isEmpty() ? ring.firstKey() : tail.firstKey();  // wrap around
        return ring.get(point);
    }
}
// with 3 nodes (150 vnodes each), then adding a 4th node:
//   add nodeD -> only keys near D move:
//     cart:9 nodeC -> nodeD
//   2/6 keys moved (naive % N would move almost ALL)
```

The naive version's `moved` count (80% of 10,000 keys) is the whole problem stated
plainly: a routine capacity change (adding one server) would force moving 8 of every 10
keys/cached entries to a new home simultaneously — for a cache, that's a stampede
(Phase 2.5) across nearly the entire keyspace at once; for a sharded database, that's
petabytes of data movement. The `ConsistentHash` ring, by contrast, moves only the keys
whose position on the ring falls in the arc newly claimed by the added node — in the
demo, 2 of 6 sample keys move when a 4th node joins a 3-node ring, and the other 4 stay
exactly where they were.

### Why it's useful

Consistent hashing is the standard, interview-expected answer to "how do you add a node
to a sharded/cached system without a massive reshuffle?" — and being able to state *why*
the naive approach fails (no relationship between `hash(k) % N` and `hash(k) % (N+1)`)
rather than just naming "consistent hashing" as a buzzword is what separates a shallow
answer from a strong one.

## 3.5 — Hotspots, cross-shard operations, and the scaling ladder

### Key Concepts

- **Hot keys/shards** — even with a good hashing scheme, a single celebrity user, a
  viral item, or a sequential id can concentrate disproportionate load on one shard.
  Mitigate by choosing a better shard key up front (hash-based, or a composite key like
  `user_id + random_suffix` to spread one hot entity's writes across multiple shards),
  or by caching the hot key/entity in front of the shard (Phase 2).
- **Cross-shard pain** — joins, multi-row transactions, and aggregations that span
  multiple shards are slow (scatter-gather: query every shard, then merge in the
  application) or outright unsupported by the database. Design around this by
  denormalizing data that's commonly queried together onto the same shard, or by
  accepting eventual consistency for cross-shard aggregates (a separate analytics
  pipeline rather than a live cross-shard `SUM()`).
- **The scaling ladder** — apply these in order, because each step is cheaper and less
  permanently complex than the next: **(1)** optimize queries and add indexes (Phase
  8.2) — often the whole problem; **(2)** add a cache (Phase 2) — absorbs read load
  without touching the database's architecture at all; **(3)** add read replicas (3.1) —
  scales reads horizontally, still one logical dataset; **(4)** shard/partition (3.3) —
  scales writes and storage, but is the most operationally complex and hardest step to
  undo. **Don't shard until you must** — it's a one-way architectural door.

### Why it's useful

Interviewers specifically probe whether candidates reach for sharding immediately
("scale = shard everything") or understand it as the *last* resort after cheaper,
reversible options are exhausted. Citing the ladder in order — and explaining *why*
sharding is last (cross-shard queries, rebalancing operational cost, the fact that it's
hard to un-shard) — is a strong, senior-sounding answer.

## 3.6 — SQL vs. NoSQL, and polyglot persistence

Scaling a database is also a decision about *which kind* of database fits the access
pattern, not purely a mechanical replication/sharding question.

### Key Concepts

- **SQL / relational** — strong consistency, full ACID transactions, a fixed (but
  evolvable via migrations) schema, and flexible ad-hoc queries with joins across
  normalized tables. Choose it for correctness-critical, genuinely relational,
  transactional data — payments, orders, anything where a partial or inconsistent write
  is unacceptable. Scales via replicas (3.1) and, with real effort, sharding (3.3).
- **NoSQL — document** (MongoDB) — schema-flexible, nested JSON-like documents, queried
  and typically sharded per-document. Good fit when an entity's data naturally nests
  (a product with variable attributes) and is usually read/written as a whole unit.
- **NoSQL — key-value** (Redis, DynamoDB) — the simplest and fastest access pattern: get
  and put by key. Good fit for caches, sessions, and any lookup-by-id workload with no
  need for complex queries.
- **NoSQL — wide-column** (Cassandra) — optimized for enormous write throughput and
  time-series-shaped data, partitioned and sorted by a composite key. Good fit for
  event logs, sensor data, and anything append-heavy at very high scale.
- **NoSQL — graph** (Neo4j) — optimized for traversing relationships (friend-of-friend,
  recommendation paths) that would require many expensive joins in a relational model.
- NoSQL stores are typically **AP-leaning** (eventually consistent by default),
  **horizontally scalable by design** (sharding is built in, not bolted on), and offer
  **limited joins/multi-row transactions** compared to SQL — you trade relational power
  for scale and schema flexibility.
- **Polyglot persistence** — real systems at scale rarely use one database for
  everything: Postgres for the transactional order/payment core, Redis for sessions and
  caching, Elasticsearch for full-text search, a columnar warehouse (BigQuery/Snowflake)
  for analytics. Each store is picked for its specific access pattern rather than forced
  to be the one-size-fits-all system of record.

### Comparison table — SQL vs. the NoSQL families

| | SQL (Postgres/MySQL) | Document (Mongo) | Key-Value (Redis/Dynamo) | Wide-Column (Cassandra) | Graph (Neo4j) |
|---|---|---|---|---|---|
| Schema | Fixed, migrated | Flexible | Flexible/none | Flexible per row | Flexible (nodes/edges) |
| Consistency | Strong (ACID) | Tunable, often eventual | Tunable, often eventual | Tunable, often eventual | Usually strong |
| Joins | Rich | Limited (embedding preferred) | None | None | Native (traversals) |
| Scale model | Vertical + effortful sharding | Sharded by design | Sharded by design | Sharded by design, very high write throughput | Harder to shard |
| Best for | Transactional, relational data | Nested/variable-shape entities | Cache, sessions, simple lookups | Time-series, huge write volume | Relationship-heavy queries |

### Why it's useful

"SQL vs. NoSQL" is one of the most commonly asked framing questions precisely because
the honest answer is "it depends on the access pattern, and real systems use several" —
naming the specific NoSQL family (not just "NoSQL" as one blob) and matching it to a
concrete access pattern is what turns a memorized buzzword list into a real design
argument. (Full depth on each of these: Databases track 04.)

## Summary / Key Takeaways

- **Replication** (leader-follower, or multi-leader/leaderless with quorums) scales
  **reads** and buys **availability** via failover; watch for **replication lag** and the
  **read-your-writes** problem, and choose **sync vs. async** replication based on how
  much write latency you'll trade for durability.
- **Sharding/partitioning** scales **writes** and **storage** by splitting data across
  nodes; the **shard key** is the hardest decision to change later — pick one that
  spreads load evenly and keeps commonly-co-queried data together.
- **`hash(key) % N` catastrophically reshuffles nearly all data when `N` changes**
  (demo: 80% of keys moved going 4→5 nodes). **Consistent hashing** (a ring, keys/nodes
  hashed onto it, next-node-clockwise ownership) moves only ~1/N of keys on a node
  change (demo: 2/6); **virtual nodes** even out the load.
- **Cross-shard joins/transactions are expensive or unsupported** — denormalize or
  accept eventual consistency for cross-shard aggregates rather than fighting the
  architecture.
- **The scaling ladder**: optimize queries/indexes → cache → read replicas → shard, in
  that order — sharding is a one-way door, so exhaust the cheaper, reversible options
  first.
- **SQL vs. NoSQL** is an access-pattern decision, not a maturity ranking — real systems
  practice **polyglot persistence**, picking the right store (relational, document,
  key-value, wide-column, graph) per workload rather than forcing one database to do
  everything.
