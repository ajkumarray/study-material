<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · web tier](../phase-5-web-tier/NOTES.md) | [Phase 7 · resource leaks ➡](../phase-7-resource-leaks/NOTES.md)
<!-- /nav -->

# Phase 6 — Reliability & Resilience: Interview Q&A

⭐ = asked constantly.

**Q: Why do you need a timeout on every remote call, even ones that "always work"?** ⭐
A call with no timeout can hang indefinitely waiting on a slow or dead dependency,
holding whatever resource made the call — usually a thread and a connection from a
bounded pool. If enough calls hang simultaneously (say, a downstream service's database
gets slow), the caller's entire thread pool fills up with threads blocked waiting on
that one dependency, and every *other* incoming request — even ones with nothing to do
with the slow dependency — has no free thread to be served on. A dependency that's
merely slow, not even fully down, can take an entire unrelated upstream service down
this way. A timeout bounds the damage: threads free up after the timeout instead of
hanging forever, and the caller degrades instead of dying completely.

**Q: How should you implement retries, precisely?** ⭐⭐
Exponential backoff with jitter: wait progressively longer between attempts, typically
doubling each time (100ms, 200ms, 400ms, 800ms...), so a struggling dependency gets
increasing room to recover instead of being hit at a constant rate. Add random jitter to
each wait so that many clients that failed at the same moment don't all retry in
lockstep — without jitter, a thousand clients backing off identically just becomes a
delayed, synchronized thundering herd instead of a spread-out one. Cap both the number
of attempts and the total elapsed time, so a request that should fail eventually does,
rather than retrying for minutes. And critically: only retry idempotent operations
(Phase 4) — retrying a raw, non-idempotent write risks duplicating its side effect, which
is why resilience and idempotency are linked rather than separate concerns.

*Follow-up: what would go wrong if you retried immediately with no backoff at all?* A
transient failure caused by the dependency being momentarily overloaded gets *worse*,
not better — every failed client immediately resends, adding load right when the
dependency needs load reduced, which can turn a brief blip into a sustained outage (a
self-inflicted retry storm).

**Q: Explain the circuit breaker pattern in detail — states, transitions, and why it
matters.** ⭐⭐
It wraps calls to a dependency and tracks failures. In the CLOSED state, calls pass
through normally and failures are counted. Once failures cross a threshold, the breaker
trips to OPEN: it rejects every call immediately, without even attempting the
dependency, for a fixed cooldown period — this is "fail fast," and it costs essentially
nothing (no thread blocked, no timeout waited out) compared to letting the call through
to a dead dependency. After the cooldown elapses, the breaker moves to HALF-OPEN and lets
exactly one trial call through: if it succeeds, the breaker closes and resumes normal
operation; if it fails, the breaker reopens for another full cooldown. In the demo, three
consecutive failures against a dead dependency trip the breaker OPEN, the next three
calls are rejected instantly with no thread wasted, and after the cooldown a
successful HALF-OPEN trial closes it again. The core value is preventing cascading
failure: without a breaker, every caller keeps retrying a dead dependency, keeps timing
out, keeps consuming threads — the circuit breaker stops that bleeding right at the
dependency boundary instead of letting it propagate upstream through exhausted resource
pools.

*Follow-up: how is a circuit breaker different from a timeout, and do you need both?*
A timeout bounds how long any *single* call to a dependency can take before giving up.
A circuit breaker decides whether to even *attempt* a call at all, based on the
dependency's recent failure history. You need both: the timeout is what actually
detects a slow call is failing (and feeds that signal into the breaker's failure count),
and the breaker is what stops you from repeating that same expensive timeout over and
over once the dependency is clearly down.

**Q: What is the bulkhead pattern, and how is it different from a circuit breaker?**
A bulkhead isolates the resources — typically separate thread or connection pools — used
to call each distinct dependency, so that one dependency being slow or failing can only
exhaust its *own* pool, not resources shared with calls to unrelated, healthy
dependencies. It's named after a ship's watertight compartments containing a hull
breach. Where a circuit breaker stops you from calling one specific bad dependency, a
bulkhead stops a resource-exhaustion problem from spreading *sideways* to different
dependencies in the same service if they happen to share infrastructure — for example,
without bulkheads, a slow recommendations service sharing a thread pool with the
payments service could exhaust that shared pool and break checkout, even though payments
itself is perfectly healthy.

**Q: What is graceful degradation? Give a concrete example.** ⭐
Reducing functionality when a *non-critical* dependency is unavailable, instead of
failing the entire request. For example, an e-commerce checkout page that shows a
"customers also bought" recommendations widget: if the recommendations service is down
or slow, the page should hide that widget (or show a cached/stale version) and let
checkout — the actually critical path — complete normally, rather than returning an
error page because one non-essential widget's dependency failed. The principle is
identifying, ahead of time, which dependencies are truly load-bearing for the core
function versus merely enhancing it, and having an explicit fallback for the latter.

**Q: How do you eliminate single points of failure in a system?**
Systematically, for every component in the architecture, ask "what happens if this
specific thing dies right now?" — and add redundancy wherever the honest answer is "the
system goes down": multiple app server instances behind a load balancer, replicated
databases with automatic failover (Phase 3.1), a redundant pair of load balancers rather
than one, and for the most critical systems, multi-availability-zone or multi-region
deployment. Redundancy alone isn't enough without fast, accurate failure detection and
automatic failover — a mechanism that takes ten minutes to notice a dead component isn't
much better than having no redundancy for a service with a tight availability SLO.

**Q: Liveness vs. readiness health checks — what's the practical difference in how
they're used?** ⭐
Liveness asks "is this process alive and not deadlocked?" — a failed liveness check
triggers a restart of the instance. Readiness asks "can this instance serve traffic
*right now* correctly?" — its dependencies reachable, its startup/warm-up finished — and
a failed readiness check tells the load balancer or orchestrator to simply stop routing
traffic to it, without necessarily restarting anything. Getting this backwards causes
real incidents: restarting an instance that's merely still warming up (failing
readiness, not liveness) wastes the warm-up work and can create a restart loop; routing
traffic to a process that's technically alive but genuinely deadlocked (passing
liveness, failing readiness) sends real users to a broken instance.

**Q: What's a dead-letter queue and why is it part of a reliability strategy?**
A separate holding queue that messages are moved into after they've repeatedly failed
processing (after N retry attempts), so they stop blocking the main queue — a single
persistently failing "poison" message shouldn't prevent every message behind it in the
queue from ever being processed — and aren't silently lost either. Messages in the DLQ
can be inspected to diagnose the failure, fixed, and replayed once the underlying issue
is resolved. It's the reliability pattern that specifically covers async/queued
processing (Phase 9), complementing the synchronous-call patterns (timeouts, retries,
circuit breakers) covered above.

**Q: RTO vs. RPO — what do they mean and how do they drive actual design decisions?**
RTO (Recovery Time Objective) is how quickly the system must be back up after an
incident; RPO (Recovery Point Objective) is how much data loss, measured in time, is
acceptable — an RPO of 5 minutes means losing at most the last 5 minutes of writes is
tolerable. These aren't abstract: a tight RPO forces synchronous or near-synchronous
database replication (Phase 3.1, since asynchronous replication can lose the last few
seconds of unreplicated writes on a leader failure), and a tight RTO forces automated,
fast failover rather than a manual process that waits for a human to be paged and
investigate. Together they're the concrete inputs that determine your backup frequency
and replication topology, rather than "reliable" being a vague, unquantified goal.

**Q: What is chaos engineering, and why would a team deliberately break their own
production system?**
Deliberately injecting real failures — killing a node, adding artificial network
latency, cutting off a dependency — under controlled conditions, to verify the system
actually degrades the way it was designed to, instead of just assuming the
resilience code works because it compiles. Netflix's Chaos Monkey, which randomly
terminates production instances, is the canonical example. The underlying reasoning is
the same as for any other untested code path: you don't actually know your circuit
breakers, retries, and failover trip correctly under a real failure until you've
watched them do it — and it's far better to discover a gap during a controlled chaos
experiment than during a genuine, unplanned outage.

**Q: The interviewer keeps asking "what if this component fails?" throughout a design
interview — what are they actually testing for?** ⭐
Resilience thinking, end to end: whether you proactively identify single points of
failure and design redundancy for them, whether you put timeouts, retries with backoff,
and circuit breakers between services rather than assuming every call succeeds, whether
you have a graceful-degradation story for non-critical dependencies, and whether your
writes are idempotent so retries triggered by a failure are actually safe to make. It's
testing whether your design accounts for failure as the normal case at scale, not an
edge case you'll handle later — a design that only works when nothing ever goes wrong
isn't a complete answer to a system-design question.
