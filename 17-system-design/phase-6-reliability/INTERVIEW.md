<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · web tier](../phase-5-web-tier/NOTES.md) | [Phase 7 · resource leaks ➡](../phase-7-resource-leaks/NOTES.md)
<!-- /nav -->

# Phase 6 — Reliability & Resilience: Interview Q&A

⭐ = asked constantly.

**Q: How should you implement retries?** ⭐⭐
Exponential backoff with jitter: wait longer each attempt (100ms, 200ms, 400ms…) so you back off a struggling service, plus random jitter so clients don't retry in lockstep (retry storm). Cap attempts and total time. Crucially, only retry **idempotent** operations, or you risk duplicates (Phase 4).

**Q: Why do you need timeouts on every remote call?** ⭐
A call without a timeout can hang forever, holding a thread/connection; enough hung calls exhaust the pool and the failure cascades (Phase 7). Timeouts bound the damage and let retries/fallbacks kick in.

**Q: Explain the circuit breaker pattern.** ⭐⭐
It wraps calls to a dependency and trips on repeated failures so calls fail fast instead of piling up. CLOSED (calls pass, count failures) → OPEN (reject immediately for a cooldown, letting the dependency recover) → HALF-OPEN (one trial call; success closes it, failure re-opens). Prevents cascading failure from a slow/dead dependency. Implemented by Resilience4j / Spring Cloud Circuit Breaker.

**Q: What is a cascading failure and how do you prevent it?** ⭐
One slow/failed component causes callers to pile up (threads block on it), exhausting their resources, which fails their callers — the failure spreads. Prevent with timeouts, circuit breakers (fail fast), bulkheads (isolate resources per dependency), and load shedding.

**Q: What is the bulkhead pattern?**
Isolate resources (separate thread/connection pools) per dependency so one failing dependency can't consume all your threads and take down everything. Like ship compartments containing a breach.

**Q: What is graceful degradation? Give an example.** ⭐
Under partial failure, reduce functionality instead of failing entirely — serve stale cached data, hide a non-critical widget (recommendations), or return a default. The core feature keeps working. Better a degraded page than an error page.

**Q: How do you eliminate single points of failure?**
Redundancy at every critical layer — multiple app servers, replicated/sharded databases, redundant load balancers, multi-AZ/region — plus automatic failover and health checks so traffic routes only to healthy instances. Ask "what happens if this one thing dies?" for each component.

**Q: Liveness vs readiness health checks?**
Liveness = is the process alive (restart it if not)? Readiness = can it serve traffic right now (dependencies up, warmed up)? Orchestrators/LBs restart on liveness failure and stop routing on readiness failure — so a starting or overloaded instance isn't sent traffic.

**Q: What's a dead-letter queue?**
A holding queue for messages that repeatedly fail processing, moved aside after N attempts so they don't block the main queue or get silently lost. They can be inspected, fixed, and replayed. Part of reliable async processing (Phase 9).

**Q: RTO vs RPO?**
RTO (Recovery Time Objective) = how quickly you must be back up after an incident. RPO (Recovery Point Objective) = how much data loss is tolerable (drives backup/replication frequency). Together they set your disaster-recovery strategy.

**Q: What is chaos engineering?**
Deliberately injecting failures (killing nodes, adding latency, dropping dependencies) in controlled conditions to verify the system degrades as designed and to find weaknesses before real outages do. You aren't resilient until you've tested failure.
