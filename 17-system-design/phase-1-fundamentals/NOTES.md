<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching ➡](../phase-2-caching/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals & Vocabulary: Notes

## 1.1 — Scalability, latency, throughput
- **Vertical scaling** (scale up): a bigger machine. Simple, no code changes, but a hard ceiling and a single point of failure.
- **Horizontal scaling** (scale out): more machines behind a load balancer. Near-limitless, fault-tolerant — but requires **stateless** services and introduces distributed-systems complexity. The default at scale.
- **Latency** = time for one request (optimize p95/p99 tail, not just average). **Throughput** = requests handled per unit time (QPS/TPS). They trade off: batching raises throughput but can raise latency; you tune for the workload. A system can be low-latency *and* low-throughput (single fast worker) or high-throughput *and* high-latency (huge batch pipeline).

## 1.2 — Availability, reliability, durability
- **Availability** = fraction of time the system serves requests, in "nines": 99.9% ≈ 8.7h down/year, 99.99% ≈ 52 min, 99.999% ≈ 5 min. Each nine costs a lot more (redundancy, failover).
- **Reliability** = works correctly (no data loss/corruption) even under failure. **Durability** = committed data survives crashes (replication, WAL, backups).
- **SLI/SLO/SLA:** an **SLI** is a measured indicator (p99 latency, error rate); an **SLO** is your internal target (99.9% of requests < 200ms); an **SLA** is the contractual promise to customers (with penalties). Error budgets = 1 − SLO.

## 1.3 — The CAP theorem
In a distributed system, during a **network partition (P)** you must choose between **Consistency (C)** — every read sees the latest write — and **Availability (A)** — every request gets a response. You can't have both while partitioned (partitions are unavoidable, so it's really CP vs AP).
- **CP** (consistency over availability): refuse/blocks during a partition to avoid stale data — banks, inventory, anything needing correctness (e.g., traditional RDBMS with sync replication, ZooKeeper, HBase).
- **AP** (availability over consistency): keep serving, reconcile later — feeds, carts, DNS (e.g., Cassandra, DynamoDB, Riak).
**Consistency models** (a spectrum, not binary): strong (linearizable) → sequential → causal → **eventual** (replicas converge given no new writes). **BASE** (Basically Available, Soft state, Eventual consistency) is the AP/NoSQL counterpoint to ACID. Real systems often tune per-operation (e.g., quorum reads/writes: R + W > N gives strong-ish consistency). **PACELC** extends CAP: even without a partition (Else), you trade Latency vs Consistency.

## 1.4 — Back-of-the-envelope estimation
Rough numbers pick the architecture. The method (demo, a Twitter-like feed): users → DAU → writes/reads per day → **QPS** (÷ ~86,400 s/day; peak ≈ 2–3× avg) → storage/day (writes × size) → bandwidth (QPS × size). Findings drive design: a **50:1 read:write ratio** screams *cache + read replicas*; high write volume screams *sharding*; large fan-out feeds scream *fan-out-on-write*.

**Numbers to memorize** (orders of magnitude): 1K/1M/1B/1T = 1e3/1e6/1e9/1e12; ~86,400 (≈1e5) seconds/day; a char ≈ 1 byte, a typical row ≈ hundreds of bytes–1KB. **Latency ratios** (the demo's table) are the deep lesson: memory ~100ns, SSD ~16µs (~100×), datacenter round trip ~0.5ms, cross-continent ~150ms (~1,000,000× memory). *This is the entire justification for caching and minimizing network hops* — every layer you can serve from is orders of magnitude faster than the one below.

**The design-interview framework** (used in Phase 11): clarify **requirements** (functional + non-functional: scale, latency, consistency) → **estimate** (QPS, storage) → **API** design → **data model** → **high-level design** → **scale it** (cache, replicate, shard, queue) → discuss **trade-offs & bottlenecks**. Always state assumptions; there's no single right answer, only justified trade-offs.
