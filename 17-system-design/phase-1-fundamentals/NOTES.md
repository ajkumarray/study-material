<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · caching ➡](../phase-2-caching/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals & Vocabulary: Notes

System design interviews (and real architecture decisions) run on a shared vocabulary:
scalability, latency, availability, consistency. This phase nails down what each word
precisely means, how they trade off against each other, and how to turn a vague
"design X for millions of users" prompt into concrete numbers you can design against.
The `EstimationDemo.java` in this directory computes a real worked example (a
Twitter-like feed) and prints the latency numbers every engineer should have memorized —
everything else in this track is built on top of these fundamentals.

## 1.1 — Vertical vs. horizontal scaling

**Scaling** is how you grow a system's capacity to handle more load. There are exactly
two axes: make the one machine you have bigger, or add more machines.

### Key Concepts

- **Vertical scaling (scale up)** — replace a server with a bigger one: more CPU, more
  RAM, a faster disk. No code changes needed; a single-node system (a single Postgres
  instance, a single app server) is trivially vertically scaled by resizing the box.
- **The vertical ceiling** — there's a biggest machine money can buy, and you hit it.
  Cloud providers cap out around a few TB of RAM and a few hundred cores per instance —
  enormous, but finite, and price grows faster than capacity near the top of the range.
- **Single point of failure (SPOF)** — a vertically-scaled system is still *one* machine.
  If it crashes, the whole service is down; there's no redundancy by construction.
- **Horizontal scaling (scale out)** — add more machines behind a load balancer (Phase
  5) instead of growing one. Near-limitless (add another box), and inherently more
  fault-tolerant (one box dying doesn't take down the fleet).
- **Statelessness is the prerequisite** — horizontal scaling only works if any server
  can handle any request. That requires the app tier to hold no server-local state
  (Phase 5.2) — sessions, in-memory caches tied to one instance, and local file writes
  all break this and must be externalized.
- **Distributed-systems complexity is the cost** — once you have N machines instead of
  1, you inherit network partitions, partial failures, coordination problems, and
  consistency questions (1.3) that a single machine never had to worry about.

### Worked example — the two paths for a struggling database

```
Single Postgres instance, CPU pegged at 100%, users see slow queries.

Path A — vertical:
  db.large (2 vCPU, 8GB) -> db.4xlarge (16 vCPU, 128GB)
  One config change / instance resize. Zero application code changes.
  New ceiling exists (db.16xlarge, etc.) but it IS a ceiling, and this
  box is still a single point of failure.

Path B — horizontal:
  Add read replicas (Phase 3.1); route SELECT traffic to them via the app's
  data-access layer or a proxy (PgBouncer/pgpool). Writes still go to the
  one leader (still a SPOF for writes, fixed by leader election).
  Requires: the app can route reads vs writes, replicas can tolerate lag.
```

In practice teams do both, in this order: first make each node efficient (vertical, or
just better queries/indexes — Phase 8), *then* scale out once a single well-tuned node
genuinely can't keep up. Horizontal scaling multiplies whatever inefficiency you have —
10 slow nodes are still slow, just more expensive.

### Comparison table — vertical vs. horizontal scaling

| | Vertical (scale up) | Horizontal (scale out) |
|---|---|---|
| Mechanism | Bigger machine | More machines |
| Code changes | Usually none | Requires statelessness |
| Ceiling | Hard limit (biggest instance) | Near-limitless |
| Fault tolerance | None — one box, one failure domain | High — one node dying doesn't sink the fleet |
| Cost curve | Superlinear near the top | Roughly linear (add commodity nodes) |
| Complexity added | None | Load balancing, coordination, partial failure |
| Typical use | Get started fast, single-node DBs | The default at real scale |

### Why it's useful

Every "design X at scale" interview answer eventually says "and then we scale out
horizontally" — but knowing *why* that requires statelessness, *why* it's usually
preferred over vertical scaling past a certain point, and *what it costs* (distributed
complexity) is what separates a memorized buzzword from a defensible design decision.

## 1.2 — Latency and throughput

**Latency** is how long one request takes. **Throughput** is how many requests the
system handles per unit time. They sound similar but are genuinely different axes, and
optimizing one can hurt the other.

### Key Concepts

- **Latency** — the time from request sent to response received, for a *single*
  request. Measured in milliseconds. What one user experiences.
- **Throughput** — requests (or transactions) processed per second — QPS (queries per
  second) or TPS. What the system handles in aggregate.
- **Percentiles, not averages** — always look at **p50** (median), **p95**, and **p99**
  (or p99.9) latency, not just the mean. The mean hides tail latency: if 1% of your
  requests take 5 seconds, the average might still look fine, but 1 in 100 users is
  having a terrible time — and at scale (millions of requests), that's a lot of users.
  SLOs (1.2 below) are almost always defined on p95/p99, because that's what determines
  whether the *worst-treated* users have an acceptable experience.
- **The trade-off** — batching (process 1,000 records in one pass instead of one at a
  time) raises throughput (more work done per unit time overall) but can raise the
  latency of any individual item in the batch (it waits for the batch to fill). A single
  very fast worker has low latency and low throughput; a huge batch pipeline has high
  throughput and high latency. Neither is "better" — it depends on the workload.
- **Concurrency vs. latency** — running more requests in parallel raises throughput
  without necessarily changing per-request latency, until you saturate a shared
  resource (CPU, DB connections, network) — then contention starts *raising* latency
  too. This is why load testing measures the throughput at which p99 latency starts to
  degrade, not just peak theoretical throughput.

### Worked example — reading the estimation demo's QPS output

```
$ java EstimationDemo.java
=== back-of-the-envelope: a Twitter-like feed ===
  DAU               : 150,000,000
  write QPS (avg)   : 3,472  (posts/sec)
  read  QPS (avg)   : 173,611  (feed loads/sec)
  read  QPS (peak)  : 520,833  (~3x avg -> size for peak)
  read:write ratio  : 50:1  (read-heavy -> cache + replicas)
```

`writeQps` is a **throughput** number — how many posts per second the write path must
sustain on average. `readQPS (peak)` matters even more for capacity planning: you must
provision for the busiest moment (a launch, a viral event, evening peak hours), not the
daily average, which is why the demo multiplies the average by ~3x. Neither number says
anything about how long any *one* post or feed load takes — that's latency, and you'd
measure it separately (with a profiler or load-test tool, Phase 8.1) once the system
exists. In an interview, quoting both — "we need to sustain ~520K read QPS at peak, and
each read should complete in under, say, 150ms at p99" — shows you understand they're
independent requirements that both drive the design.

### Why it's useful

Latency budgets tell you how deep your call chain can be (each hop costs milliseconds,
1.4) and drive caching decisions (Phase 2). Throughput requirements tell you how many
servers/partitions/shards you need. A design that nails p50 latency but falls over at
peak throughput (or vice versa) fails the actual requirement — always state both when
sizing a system, and always ask "average or peak?" when given a number.

## 1.3 — Availability, reliability, durability

These three sound interchangeable in casual speech but mean distinct things in system
design, and interviewers expect you to keep them straight.

### Key Concepts

- **Availability** — the fraction of time the system successfully responds to requests.
  Expressed in "nines": 99% (two nines), 99.9% (three nines), 99.99% (four nines),
  99.999% (five nines — "five nines").
- **Reliability** — the system behaves *correctly* even in the presence of faults: no
  data loss, no corruption, no wrong answers — as opposed to merely responding (which is
  availability; a system that's "up" but returning corrupted data is available but not
  reliable).
- **Durability** — once a write is acknowledged as committed, it survives crashes,
  power loss, and disk failure. Achieved via write-ahead logs, replication, and backups
  (Phase 3, Databases track). A system can be highly available yet not durable (an
  in-memory cache that's always up but loses everything on restart).
- **Downtime budget per nines level** — this is worth memorizing cold:

### Worked example — what the nines actually cost you

| Availability | Downtime / year | Downtime / month | Downtime / day |
|---|---|---|---|
| 99% | ~3.65 days | ~7.2 hours | ~14.4 min |
| 99.9% ("three nines") | ~8.7 hours | ~43.2 min | ~1.4 min |
| 99.99% ("four nines") | ~52.6 min | ~4.3 min | ~8.6 sec |
| 99.999% ("five nines") | ~5.3 min | ~26 sec | ~0.86 sec |

Going from 99% to 99.9% buys you back about 3.5 days a year — usually achievable with
a second server and a load balancer with health checks. Going from 99.99% to 99.999%
buys back less than a minute a year but typically requires multi-region active-active
deployment, extensive chaos testing, and a much larger operations budget. **Each
additional nine costs disproportionately more** — this is why you scope the target to
what the business actually needs (a payments API might need 99.99%; an internal
analytics dashboard is fine at 99.5%) rather than reflexively chasing five nines.

### Why it's useful

When an interviewer asks "what availability would you target?", the answer isn't
"five nines" by default — it's "depends what this system does and what breaks if it's
down." Durability vs. availability also drives real trade-offs: a payment record must be
durable even if that costs some availability (better to reject a write than silently
lose it); a "typing..." indicator in a chat app can be neither durable nor highly
available without anyone noticing.

## 1.4 — SLI, SLO, SLA, and error budgets

Once you've picked a target, you need a way to measure it and a contract that describes
what happens if you miss it. This is standard SRE (Site Reliability Engineering)
vocabulary and shows up constantly in both design and behavioral interviews.

### Key Concepts

- **SLI (Service Level Indicator)** — a measured metric: p99 latency, error rate,
  availability over the last 28 days. The raw number.
- **SLO (Service Level Objective)** — your internal target for an SLI, e.g. "99.9% of
  requests complete in under 200ms, measured over a rolling 30 days." This is what
  engineering teams design and operate against.
- **SLA (Service Level Agreement)** — the external, contractual promise to customers,
  usually with financial penalties for breach (e.g., a cloud provider crediting your bill
  if uptime falls below 99.95%). SLAs are typically *looser* than the internal SLO —
  you want margin before you're contractually on the hook.
- **Error budget** — `1 − SLO`. If your SLO is 99.9% availability, your error budget is
  0.1% of requests/time allowed to fail. Teams spend the budget deliberately: ship a
  risky feature, run a chaos experiment, do a risky migration. If the budget is
  exhausted before the period ends, the team freezes feature work and focuses on
  reliability until it recovers.

### Worked example — turning an SLO into an error budget

```
SLO: 99.95% of API requests succeed, measured over 30 days.
Error budget = 1 - 0.9995 = 0.05% of requests may fail.

Assume 500M requests over 30 days:
  budget = 500,000,000 * 0.0005 = 250,000 failed requests allowed

If a bad deploy causes 180,000 failures in one day, the team has burned
72% of the month's entire error budget in a single incident -> the
reasonable response is an immediate freeze on further risky changes
until the budget resets, even if the deploy is technically "fixed."
```

This turns "should we be more careful before shipping?" from a values argument into a
data-driven one: the number tells you whether you have budget to spend or not.

### Why it's useful

SLI/SLO/SLA gives you a shared, quantitative language for reliability trade-offs instead
of vague words like "should be pretty reliable." In an interview, stating "I'd target a
p99 latency SLO of 300ms and 99.9% availability for this service, backed by an SLA to
customers of 99.5% to leave margin" demonstrates you think about reliability as an
engineered, negotiated property — not an afterthought.

## 1.5 — The CAP theorem

**CAP** describes a fundamental limitation of any distributed system that replicates
data across multiple nodes: during a network partition, you must choose between
consistency and availability. You cannot have both.

### Key Concepts

- **Consistency (C)** — every read receives the most recent write (or an error) — all
  nodes see the same data at the same time. This is the strong, linearizable sense of
  "consistency" (not the C in ACID, which is a different concept about database
  invariants).
- **Availability (A)** — every request to a non-failing node receives a (non-error)
  response — the system keeps serving, even if it can't guarantee that response is the
  latest data.
- **Partition tolerance (P)** — the system continues operating despite network messages
  being dropped or delayed between nodes. Partitions are a fact of distributed
  networking — you don't get to opt out of them, which is why the real choice is CP vs.
  AP, not "should we have P."
- **CP systems** (consistency over availability) — during a partition, refuse requests
  that can't be guaranteed correct rather than risk serving stale data. Examples:
  traditional RDBMS with synchronous replication, ZooKeeper, etcd, HBase. Right for
  banking, inventory counts, anything where a wrong answer is worse than no answer.
- **AP systems** (availability over consistency) — during a partition, keep serving
  requests from whatever nodes are reachable, and reconcile divergent copies once the
  partition heals. Examples: Cassandra, DynamoDB, Riak. Right for social feeds, shopping
  carts, DNS — anywhere a slightly stale answer beats an error page.

### Worked example — CAP during an actual network split

```
3-node cluster, replicating a "user balance" value. A network partition
splits it into {node1} | {node2, node3}.

CP choice: node1 (isolated) REFUSES writes and reads that it can't confirm
are consistent with the majority side. Clients talking only to node1 get
errors until the partition heals. node2/node3 (majority) can keep serving,
because they can still form a quorum among themselves.
  -> correctness preserved, availability reduced on the minority side.

AP choice: node1 KEEPS serving reads/writes using its local (possibly
stale) copy. A client reading from node1 might see an old balance; a
client writing to node1 creates a value that must be reconciled with
node2/node3's value once the partition heals (last-write-wins, vector
clocks, CRDTs, or an application-level merge).
  -> availability preserved everywhere, correctness temporarily relaxed.
```

Note that outside of a partition (the common case), most systems try to offer both
reasonable consistency and availability — CAP only forces the choice specifically *when*
a partition is actually happening.

### Comparison table — consistency models (a spectrum, not a binary)

| Model | Guarantee | Cost | Example use |
|---|---|---|---|
| Strong / linearizable | Every read sees the latest committed write, globally ordered | Highest latency, lowest availability under partition | Bank balance, inventory count |
| Sequential | All nodes see operations in the same order (not necessarily real-time) | High | Distributed locks, config systems |
| Causal | Causally-related operations are seen in order; unrelated ones may reorder | Medium | Comment threads, collaborative editing |
| Eventual | Replicas converge to the same value *given no new writes* — no ordering guarantee in between | Lowest latency, highest availability | Like counts, view counts, DNS, caches |

### Why it's useful

**BASE** (Basically Available, Soft state, Eventual consistency) is the AP/NoSQL
philosophy — the explicit counterpoint to ACID's strict correctness. Real production
systems rarely pick one extreme: **quorum reads/writes** (`R + W > N`, where N is
replica count, W is write-acknowledgment count, R is read-fan-out count) let you tune
consistency *per operation* — e.g., write with `W=2` for durability, but read with
`R=1` for speed when staleness is tolerable, and `R=N` when you need the latest value.
**PACELC** extends CAP for the *non-partitioned* common case: **E**lse, you still trade
**L**atency for **C**onsistency — a synchronously-replicated system is more consistent
but slower even when nothing is broken.

## 1.6 — Back-of-the-envelope estimation

Rough numbers, not precise ones, pick the architecture. This is the single most
practical skill this phase teaches, and `EstimationDemo.java` walks through exactly this
method for a Twitter-like feed.

### Key Concepts

- **The method**: users → **DAU** (daily active users) → actions per user per day
  (writes, reads) → **QPS** (divide by ~86,400 seconds/day; multiply by ~2–3x for peak)
  → **storage/day** (writes × average row size) → **bandwidth** (QPS × payload size).
- **Numbers worth memorizing**: 1 thousand = 1e3, 1 million = 1e6, 1 billion = 1e9, 1
  trillion = 1e12. Seconds per day ≈ 86,400 ≈ 1e5 — so `QPS ≈ daily_count / 1e5` is a
  fast mental shortcut. A character is roughly 1 byte; a typical structured row is
  hundreds of bytes to low single-digit KB.
- **Peak vs. average** — always compute both. Provisioning for the average leaves you
  falling over during actual peak load (evenings, launches, viral moments) — the demo
  uses a 3x multiplier as a reasonable rule of thumb absent better data.
- **The read:write ratio is the single most decision-driving number** — a heavily
  read-skewed ratio (50:1, 100:1) screams "cache aggressively, add read replicas";
  a heavy write load screams "you'll need to shard (Phase 3) well before storage or read
  traffic becomes the bottleneck."

### Worked example — the full estimation demo output, annotated

```
$ java EstimationDemo.java
=== back-of-the-envelope: a Twitter-like feed ===
  DAU               : 150,000,000
  write QPS (avg)   : 3,472  (posts/sec)
  read  QPS (avg)   : 173,611  (feed loads/sec)
  read  QPS (peak)  : 520,833  (~3x avg -> size for peak)
  read:write ratio  : 50:1  (read-heavy -> cache + replicas)
  new storage/day   : 90.0 GB  (~33 TB/year of raw tweets)
  read bandwidth    : 52.1 MB/s avg  (readQPS x tweetSize)
  takeaway: read-heavy + high QPS -> CDN/cache, read replicas,
            fan-out-on-write for feeds; storage grows -> sharding.
```

Derivation, following the code: `dau = mau * dailyActiveFrac = 300M * 0.5 = 150M`.
`writesPerDay = dau * writesPerUser = 150M * 2 = 300M` → `writeQps = 300M / 86,400 ≈
3,472`. `readsPerDay = dau * readsPerUser = 150M * 100 = 15B` → `readQps = 15B / 86,400
≈ 173,611`, and peak is `readQps * 3 ≈ 520,833`. Storage: `writesPerDay * tweetBytes =
300M * 300 bytes = 90GB/day`, annualized to ~33TB/year. **Every one of these numbers
directly justifies a design decision**: a 50:1 read:write ratio means the system should
spend its engineering effort on the read path (cache, CDN, replicas, Phase 2 & 3) far
more than the write path; 3,472 writes/sec is well within what a single well-tuned
leader database can sustain without sharding on writes alone; 90GB/day of new data means
you *will* eventually need to think about storage tiering or sharding, just not on day
one.

### Worked example — the latency numbers every engineer should know

```
$ java EstimationDemo.java  (excerpt)
=== latency numbers every programmer should know ===
  L1 cache reference               1 ns
  Branch mispredict                3 ns
  L2 cache reference                4 ns
  Mutex lock/unlock                17 ns
  Main memory reference            100 ns
  Compress 1KB (fast)               2.0 us
  Read 1MB sequentially from RAM    3.0 us
  SSD random read                  16.0 us
  Read 1MB from SSD                49.0 us
  Round trip within datacenter     500.0 us
  Read 1MB from disk (HDD)         825.0 us
  Round trip CA <-> Netherlands    150.0 ms
```

The ratios are the entire lesson: main memory (~100ns) is roughly **160x faster** than
an SSD random read (~16µs), which is itself roughly **30x faster** than an in-datacenter
network round trip (~500µs), which is roughly **300x faster** than a cross-continent
round trip (~150ms). Stacking those up, memory access is on the order of **1.5 million
times** faster than a cross-continent network hop. This single fact is the entire
justification for caching (Phase 2) and for minimizing network hops (why N+1 queries,
Phase 8.2, are so expensive, and why co-locating services that talk to each other
matters): every layer you can serve a request from is orders of magnitude faster than
the layer beneath it, and a design that avoids a round trip is worth more than almost
any other single optimization.

### Why it's useful

Estimation is what turns "design a URL shortener for millions of users" from a vague
prompt into a concrete engineering problem: once you know it's ~1,200 writes/sec and
~120,000 reads/sec (Phase 11's worked example), the architecture almost designs itself —
cache the redirects, don't bother sharding writes yet, do worry about the read path.
Interviewers use estimation as a filter for whether you reason quantitatively at all,
independent of whether your exact numbers are "correct" (they never are, and that's
fine — the point is the orders of magnitude and the design decisions they justify).

## 1.7 — The design-interview framework

Every phase after this one is a specific tool in a general-purpose toolbox. This
framework (deepened with worked examples in Phase 11) is how you apply that toolbox to
an open-ended prompt.

### Key Concepts

1. **Clarify requirements** — functional (what must the system do?) and non-functional
   (scale, latency targets, consistency needs, availability target). Never assume scope;
   ask.
2. **Estimate** — QPS (read and write, average and peak), storage growth, bandwidth
   (1.6). These numbers justify every subsequent decision.
3. **API design** — the concrete endpoints/contracts a client would call. Forces you to
   nail down the data flowing in and out.
4. **Data model** — entities, relationships, and crucially the *access patterns* (how
   will this be queried?) that should drive schema and database choice (Phase 3.3).
5. **High-level design** — boxes and arrows: client → load balancer/gateway → services
   → cache → database → queue. Get something *working* end-to-end before optimizing.
6. **Scale it** — apply the toolkit where bottlenecks actually are: cache (Phase 2),
   replicate/shard (Phase 3), rate-limit/load-balance (Phase 5), add resilience (Phase
   6), go async (Phase 9).
7. **Trade-offs and bottlenecks** — explicitly state what you optimized for and what you
   gave up (a CAP choice, consistency vs. latency, cost vs. simplicity). This is usually
   what's actually being graded — there is no single right answer, only a well-justified
   one.

### Why it's useful

Interviewers see dozens of candidates jump straight to drawing boxes without ever
stating a requirement or a number — that reads as pattern-matching, not engineering
judgment. Narrating this framework out loud, in order, signals that you can turn
ambiguity into a scoped, numbers-backed design — which is the actual skill being
assessed, independent of the specific system in the prompt.

## Summary / Key Takeaways

- **Horizontal scaling** is the default at real scale, but it *requires* statelessness
  (Phase 5.2) and trades a hard vertical ceiling for distributed-systems complexity.
- **Latency** (time per request, judged at p95/p99, not the mean) and **throughput**
  (requests/sec) are independent axes that can trade off — batching raises throughput
  but can raise individual latency.
- **Availability** (nines), **reliability** (correctness under fault), and
  **durability** (committed data survives crashes) are three distinct properties; each
  additional nine of availability costs disproportionately more.
- **SLI/SLO/SLA** give reliability a shared, quantitative vocabulary; the **error
  budget** (`1 − SLO`) turns "should we be careful?" into a number you can spend or
  freeze on.
- **CAP** forces a **CP vs. AP** choice only *during* a network partition; consistency
  is a spectrum (strong → sequential → causal → eventual), and quorums (`R+W>N`) let you
  tune it per operation. **PACELC** extends the trade-off to latency-vs-consistency even
  without a partition.
- **Back-of-the-envelope estimation** (DAU → QPS → storage → bandwidth, with peak ≈
  2–3x average) is what makes a design's decisions justifiable rather than guessed — and
  the **latency-ratio table** (memory ≪ SSD ≪ network) is the entire reason caching and
  minimizing hops matter so much (Phase 2, Phase 8).
