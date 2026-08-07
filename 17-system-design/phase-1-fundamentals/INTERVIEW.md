<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching ➡](../phase-2-caching/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: Vertical vs horizontal scaling?** ⭐⭐
Vertical = bigger machine (simple, but a ceiling and a single point of failure). Horizontal = more machines behind a load balancer (near-limitless, fault-tolerant, but needs stateless services and adds distributed complexity). Scale out is the default at scale — after making a single node efficient.

**Q: Latency vs throughput?** ⭐
Latency = time per request (optimize the p95/p99 tail); throughput = requests per unit time. They can trade off — batching improves throughput but may hurt latency. Optimize for what the workload/SLA demands.

**Q: Explain the CAP theorem.** ⭐⭐
During a network partition, a distributed system must choose Consistency (reads see the latest write) or Availability (every request gets a response) — not both. Since partitions are unavoidable, systems are effectively CP (e.g., strongly-consistent DBs) or AP (e.g., Cassandra/Dynamo, reconcile later). When there's no partition, PACELC adds the latency-vs-consistency trade-off.

**Q: Strong vs eventual consistency — and when each?** ⭐
Strong: every read reflects the most recent write (needed for money, inventory, unique usernames). Eventual: replicas converge over time; reads may be briefly stale (fine for feeds, likes, view counts) and it buys availability and lower latency. Many systems tune per-operation (quorums).

**Q: What do the "nines" of availability mean?**
99.9% ≈ 8.8h downtime/year, 99.99% ≈ 52 min, 99.999% ≈ 5 min. Each extra nine costs disproportionately more (redundancy, multi-region failover). Pick the target the business actually needs.

**Q: SLI vs SLO vs SLA?** ⭐
SLI = a measured indicator (p99 latency, error rate). SLO = your internal target for it (99.9% < 200ms). SLA = the external contractual promise with penalties. Error budget = 1 − SLO, used to balance reliability work vs feature velocity.

**Q: Estimate the QPS and storage for a service with 100M DAU posting twice a day.** ⭐
Writes/day = 200M; ÷ ~86,400s ≈ ~2,300 write QPS (peak ~2–3×). Storage/day = writes × row size (e.g., 200M × 300B ≈ 60GB/day). Reads are usually 10–100× writes → read-heavy → cache + replicas. State assumptions; orders of magnitude matter, not exact figures.

**Q: Why do we cache and minimize network calls? Justify with numbers.**
Latency ratios: memory ~100ns, SSD ~16µs, same-datacenter round trip ~0.5ms, cross-continent ~150ms. Each layer down is orders of magnitude slower (memory vs cross-continent ≈ 1,000,000×). Serving from the fastest reachable layer — and cutting round trips — is the single biggest latency lever.

**Q: What is BASE, and how does it relate to ACID?**
BASE (Basically Available, Soft state, Eventual consistency) is the AP/NoSQL philosophy: prioritize availability and accept temporary inconsistency, converging over time. ACID (Databases Phase 5) prioritizes strict correctness. Choose per use case — money → ACID, high-scale feeds → BASE.

**Q: How do you approach an open-ended "design X" question?** ⭐⭐
A framework: clarify functional + non-functional requirements (scale, latency, consistency) → estimate (QPS, storage) → define the API → data model → high-level architecture → scale it (cache, replicas, sharding, queues) → discuss bottlenecks and trade-offs. Communicate assumptions throughout; there's no single right answer.
