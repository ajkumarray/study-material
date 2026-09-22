<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching ➡](../phase-2-caching/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: Vertical vs. horizontal scaling — walk through both and when you'd pick each.** ⭐⭐
Vertical scaling means replacing a server with a bigger one — more CPU, RAM, faster
disk. It requires no application code changes and is the fastest way to buy headroom
early on, but it has a hard ceiling (the biggest instance a cloud provider sells) and
the machine remains a single point of failure. Horizontal scaling means adding more
machines behind a load balancer; it's near-limitless and inherently more fault-tolerant,
but it only works if the application tier is stateless — any server must be able to
handle any request — which usually means externalizing sessions and caches (Phase 5.2).
In practice, teams do both in order: make a single node efficient first (better queries,
indexes, vertical resize), then scale out horizontally once a well-tuned single node
genuinely can't keep up. Horizontal scaling multiplies inefficiency — ten slow nodes are
still slow, just more expensive.

*Follow-up: why is statelessness a hard requirement for horizontal scaling, not just a
nice-to-have?* If a server holds session data in memory and the load balancer routes a
user's next request to a different server, that server has no idea who the user is.
Worse, if the server holding the state dies, that state is gone. Statelessness means any
instance can serve any request and instances are disposable — which is exactly what lets
you add and remove them freely.

**Q: Latency vs. throughput — and why do we care about p99 instead of average latency?** ⭐⭐
Latency is how long a single request takes; throughput is how many requests the system
handles per unit time (QPS). They can trade off: batching many small operations into one
larger one raises throughput but can raise the latency any individual item experiences
while it waits for the batch to fill. Averages hide tail behavior — if 1% of requests
take 5 seconds while the rest take 50ms, the mean might look fine, but at scale (say, 10
million requests a day) that 1% is 100,000 people having a bad experience, and if a
request in your system fans out to several downstream services, the odds that *at least
one* of them is having a slow moment compound quickly. That's why SLOs are defined on
p95/p99 latency, not the mean — it's what the worst-treated fraction of users actually
feels.

**Q: What do the "nines" of availability mean, and why doesn't everyone just target five
nines?** ⭐⭐
99% allows about 3.65 days of downtime a year; 99.9% ("three nines") about 8.7 hours;
99.99% about 52 minutes; 99.999% ("five nines") about 5.3 minutes. Each additional nine
costs disproportionately more engineering and operational effort — going from 99% to
99.9% might just need a second server behind a load balancer with health checks, while
99.99% to 99.999% typically requires active-active multi-region deployment, extensive
chaos testing, and a much bigger on-call/ops investment for a return of less than a
minute a year. You target the availability the business actually needs: a payments API
might justify 99.99%, while an internal analytics dashboard is fine at 99.5% — chasing
nines you don't need is wasted engineering budget.

**Q: Explain SLI, SLO, and SLA, and how error budgets are used.** ⭐
An SLI is a measured indicator — p99 latency, error rate, uptime percentage. An SLO is
your internal target for that indicator, e.g. "99.9% of requests complete under 200ms
over a rolling 30 days" — what engineering actually designs and operates against. An SLA
is the external, contractual promise to customers, usually looser than the internal SLO
(to leave margin) and backed by penalties if missed. The error budget is `1 − SLO`: if
your SLO is 99.9%, you have 0.1% of requests you're allowed to "spend" on risky
deploys, chaos experiments, or migrations. If an incident burns most of the month's
budget, the team freezes further risky changes and focuses on reliability until the
budget resets — it turns "should we be more careful?" into a number instead of a values
argument.

**Q: Explain the CAP theorem precisely — not just "pick two."** ⭐⭐
CAP applies specifically *during a network partition*. A distributed system must choose
between Consistency (every read reflects the most recent write) and Availability (every
non-failing node still responds to requests) — it cannot guarantee both while nodes
can't talk to each other. Partition tolerance isn't really an optional third choice;
partitions happen regardless of what you want, so real systems are effectively CP or AP.
CP systems (traditional RDBMS with sync replication, ZooKeeper, etcd) refuse or block
requests they can't guarantee are correct during a partition — right for money,
inventory, anything where a wrong answer is worse than an error. AP systems (Cassandra,
DynamoDB) keep serving from whatever's reachable and reconcile divergent state once the
partition heals — right for feeds, carts, presence, anywhere staleness beats an outage.
Outside of an actual partition, most systems try to offer good consistency *and*
availability — CAP only forces the trade-off during the partition itself.

*Follow-up: what's PACELC and why does it matter even without a partition?* PACELC
extends CAP: **if** Partitioned, trade Availability vs. Consistency (that's CAP); **Else**
(the normal, non-partitioned case), you still trade Latency vs. Consistency — a system
that synchronously replicates to guarantee strong consistency is slower even when
nothing is broken, because every write waits on a round trip to other replicas.

**Q: Strong vs. eventual consistency — and where does each fit?** ⭐
Strong (linearizable) consistency guarantees every read reflects the most recent write —
necessary for a bank balance, inventory counts, or username uniqueness, where a stale
read is a correctness bug. Eventual consistency only guarantees replicas *converge* to
the same value once writes stop — reads may briefly see stale data in exchange for lower
latency and higher availability, which is fine for like counts, view counters, or a
social feed. Between the two extremes sit sequential consistency (all nodes agree on
operation order, but not necessarily in real time) and causal consistency (causally
related operations are ordered; unrelated ones may not be) — most real systems tune
consistency per operation with quorums (`R + W > N`) rather than picking one extreme
globally.

**Q: What is BASE and how does it relate to ACID?**
BASE — Basically Available, Soft state, Eventual consistency — is the AP/NoSQL
philosophy: prioritize availability and accept temporary inconsistency that converges
over time. ACID (Databases track) prioritizes strict correctness within a transaction —
Atomicity, Consistency, Isolation, Durability. They aren't opposites to be graded on
purity; they're two different sets of default trade-offs, and the right choice is per
use case — ACID for money movement, BASE for a high-scale social feed where an
occasional stale read is invisible to the user.

**Q: Estimate the QPS and storage for a service with 150M daily active users each posting
twice a day and loading their feed 100 times a day.** ⭐⭐
Writes/day = 150M × 2 = 300M → write QPS ≈ 300M / 86,400 ≈ 3,472/sec (peak ≈ 2–3x, so
plan for roughly 7,000–10,000/sec). Reads/day = 150M × 100 = 15B → read QPS ≈ 15B /
86,400 ≈ 173,611/sec average, peak roughly 3x that at ~520,000/sec. The read:write
ratio here is 50:1 — heavily read-skewed, which tells you to spend your design effort on
the read path: caching, CDN, read replicas, and probably fan-out-on-write for feeds
(Phase 11), rather than worrying about sharding writes on day one. Storage: 300M writes
× ~300 bytes/tweet ≈ 90GB/day, or roughly 33TB/year of raw content — large enough that
you'll eventually think about storage tiering, but not something that forces sharding
immediately at that write volume.

**Q: Why do we cache and minimize network calls? Justify it with actual numbers.** ⭐
Using the classic latency table: main memory access is about 100ns, an SSD random read
is about 16µs (roughly 160x slower than memory), an in-datacenter network round trip is
about 500µs (roughly 30x slower than the SSD read), and a cross-continent round trip is
about 150ms (roughly 300x slower again). Stacked together, memory access is on the order
of a million times faster than a cross-continent network hop. That ratio is the entire
justification for caching (Phase 2): every layer closer to compute you can serve a
request from is orders of magnitude faster than the layer below it, and eliminating even
one network round trip (fixing an N+1 query, Phase 8.2, or co-locating a hot dependency)
is often worth more than almost any other single optimization you could make.

**Q: How do you approach an open-ended "design X" question?** ⭐⭐
A repeatable framework: clarify functional and non-functional requirements (scale,
latency, consistency, availability) → estimate scale (QPS read/write, storage,
bandwidth) → define the API (the concrete endpoints/contracts) → design the data model
(entities and, critically, the access patterns that should drive schema/DB choice) →
sketch the high-level architecture (client → LB/gateway → services → cache → DB → queue)
→ scale it by applying the toolkit where the actual bottlenecks are (cache, replicate,
shard, rate-limit, go async, add resilience) → explicitly discuss trade-offs and
bottlenecks. State assumptions out loud throughout. There's no single right answer —
what's graded is whether your decisions are justified by the requirements and numbers
you stated, not whether you drew the "correct" diagram.

*Follow-up: what's the biggest mistake candidates make with this framework?* Skipping
straight to drawing boxes and arrows without ever stating a requirement or computing a
number. It reads as having memorized a diagram rather than reasoning about this specific
problem — an interviewer will probe by changing a requirement (e.g., "what if writes are
10x higher than reads?") specifically to see whether your design actually depends on the
numbers you gave, or whether it was fixed in advance.
