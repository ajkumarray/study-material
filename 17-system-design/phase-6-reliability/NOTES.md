<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · web tier](../phase-5-web-tier/NOTES.md) | [Phase 7 · resource leaks ➡](../phase-7-resource-leaks/NOTES.md)
<!-- /nav -->

# Phase 6 — Reliability & Resilience: Notes

The mindset: **at scale, everything fails eventually** — networks blip, services slow down, disks die, deploys break. Resilient systems *expect* failure and *contain* it, so a partial failure degrades gracefully instead of cascading into an outage.

## 6.1 — Timeouts, retries, backoff
- **Timeouts** — never wait forever on a dependency. A call with no timeout holds a thread/connection indefinitely; enough of them exhaust the pool (Phase 7) and the failure spreads. Always set connect + read timeouts.
- **Retries** — transient failures (a blip, a brief timeout) often succeed on a second try. But naive immediate retries *add* load to an already-struggling service.
- **Exponential backoff + jitter** — wait, and *double* the wait each attempt (100ms, 200ms, 400ms…) to back off a struggling dependency; add random **jitter** so many clients don't retry in lockstep (a synchronized "retry storm"). Cap attempts and total time.
- **Retry only idempotent operations** (Phase 4) — retrying a non-idempotent write can duplicate it. This is why idempotency and resilience are linked: safe retries require idempotent operations.

## 6.2 — Circuit breakers, bulkheads, degradation
- **Circuit breaker** — wraps calls to a dependency and trips when it's failing, so calls **fail fast** instead of piling up. Three states (demo): **CLOSED** (calls pass, count failures) → **OPEN** (too many failures → reject immediately for a cooldown, giving the dependency room to recover) → **HALF-OPEN** (after cooldown, allow one trial; success → CLOSED, failure → OPEN). Prevents a slow/dead dependency from exhausting your threads and cascading upstream. Libraries: Resilience4j, Spring Cloud Circuit Breaker.
- **Bulkhead** — isolate resources per dependency (separate thread pools/connection pools) so one failing dependency can't consume all threads and sink everything else. Named after ship compartments that stop one breach from flooding the whole hull.
- **Graceful degradation / fallbacks** — when a non-critical dependency is down, degrade instead of failing: serve stale cache, hide a recommendations widget, return a default. Keep the core function working.
- **Load shedding** — under overload, deliberately reject low-priority requests (429/503) to keep the system alive for the rest (related to rate limiting, Phase 5).

## 6.3 — Redundancy, failover, health checks
- **No single point of failure (SPOF):** every critical component needs redundancy — multiple app servers, replicated DBs (Phase 3), redundant LBs, multi-AZ/region. Find SPOFs by asking "what happens if *this* dies?"
- **Failover** — automatically route around a dead component: promote a DB replica, shift traffic off an unhealthy server. Needs fast, accurate failure detection.
- **Health checks** — liveness (is the process up?) and readiness (can it serve traffic — dependencies OK, warmed up?). LBs and orchestrators (K8s) use them to route only to healthy instances. (Actuator `/health` — Spring Boot Phase 8.)
- **Dead-letter queue (DLQ)** — messages that repeatedly fail processing are moved aside for inspection/replay instead of blocking the queue or being lost (Phase 9).

## 6.4 — Blast radius & failure thinking
- **Blast radius** — how much breaks when one thing fails. Shrink it: partition/shard (a bad shard affects 1/N of users), cell-based architecture, rate limits, bulkheads, gradual rollouts (canary/blue-green deploys so a bad release hits few users).
- **Chaos engineering** — deliberately inject failures (kill nodes, add latency) in controlled conditions to verify the system degrades as designed (Netflix's Chaos Monkey). You don't know you're resilient until you've tested failure.
- **Idempotency + at-least-once + retries + DLQ** together give reliable processing (Phase 4). Resilience is layered: timeouts and retries at the call, circuit breakers and bulkheads at the dependency, redundancy and failover at the infrastructure, and graceful degradation at the product.

**Recovery objectives:** **RTO** (Recovery Time Objective — how fast you must recover) and **RPO** (Recovery Point Objective — how much data loss is acceptable) drive backup/replication strategy.
