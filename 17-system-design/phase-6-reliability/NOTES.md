<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · web tier](../phase-5-web-tier/NOTES.md) | [Phase 7 · resource leaks ➡](../phase-7-resource-leaks/NOTES.md)
<!-- /nav -->

# Phase 6 — Reliability & Resilience: Notes

The mindset shift this phase teaches: **at scale, everything fails eventually** —
networks blip, dependencies slow down, disks die, deploys break something. A resilient
system doesn't try to prevent every failure (impossible); it *expects* failure and
*contains* it, so one component's partial failure degrades the system gracefully instead
of cascading into a total outage. `ResilienceDemo.java` implements the two most
fundamental resilience patterns — retry with backoff, and the circuit breaker — with
real, runnable code.

## 6.1 — Timeouts

### Key Concepts

- **Every remote call needs a timeout.** A call with no timeout can hang forever waiting
  on a slow or dead dependency, holding whatever resource made the call (a thread, a
  database connection) indefinitely.
- **The cascading consequence** — enough hung calls exhaust a bounded resource (a thread
  pool, a connection pool — Phase 7), and once that resource is exhausted, *every other*
  request that needs it also blocks, even requests that have nothing to do with the
  originally-slow dependency. This is how one slow downstream service can take down an
  entire upstream service that merely calls it.
- **Set both connect and read timeouts** — a connect timeout bounds how long you'll wait
  to even establish a connection; a read timeout bounds how long you'll wait for a
  response after the connection is established. Both are needed; a dependency that
  accepts a connection but then never responds is only caught by the read timeout.

### Worked example — why a missing timeout cascades

```
Service A calls Service B on every request, using a client library with NO
configured timeout. Service B's database gets slow (say, from an unrelated
incident) and B's responses start taking 60+ seconds instead of 50ms.

A's thread pool is sized to 200 threads. Within a few seconds, all 200 of
A's threads are blocked waiting on B's slow responses. Every NEW request
into A -- even ones that don't call B at all -- has no free thread to be
handled on, and queues or times out.

A single slow DEPENDENCY (B) has now taken down A ENTIRELY, even though A
itself has no bug and B hasn't actually crashed -- it's just slow.
A 5-second timeout on A's calls to B would have bounded the damage: threads
free up after 5s instead of 60s, and A degrades but doesn't fully die.
```

### Why it's useful

This single scenario — a timeout-less call to a slow dependency exhausting a thread
pool — is one of the most common real production outages, and it's entirely preventable
with a timeout that costs nothing to add. It's also the direct setup for 6.2: a timeout
alone bounds *how long* you wait per call, but doesn't stop you from repeatedly making
that same slow call over and over; the circuit breaker is what stops calling a
known-bad dependency altogether for a while.

## 6.2 — Retries with exponential backoff and jitter

### Key Concepts

- **Retries recover from transient failures** — a network blip, a brief GC pause on the
  server, a momentary connection reset — that often succeed on a second attempt with no
  other change needed.
- **Naive immediate retry adds load exactly when it's least wanted** — retrying
  instantly, especially from many clients at once, piles more requests onto a
  dependency that may already be struggling, potentially making things worse.
- **Exponential backoff** — wait longer between each successive attempt, typically
  doubling: 100ms, 200ms, 400ms, 800ms... This gives a struggling dependency
  progressively more room to recover instead of hammering it at a constant rate.
- **Jitter** — add randomness to the wait time so that many clients retrying after the
  same failure don't all retry at exactly the same moments in lockstep (a "retry storm"
  synchronized across clients, which defeats the point of backing off — a thousand
  clients all waiting exactly 400ms and then all retrying in the same instant is just a
  delayed thundering herd).
- **Cap both attempts and total time** — retrying forever is its own failure mode (a
  request that should have failed fast instead hangs for minutes across retries); set a
  maximum attempt count and/or a maximum total elapsed time, after which you give up and
  surface the failure.
- **Only retry idempotent operations** (Phase 4) — retrying a non-idempotent write (a
  raw `POST /charge` with no idempotency key) risks duplicating its side effect. This is
  the direct link between idempotency and resilience: safe retries require idempotent
  operations, full stop.

### Worked example — `ResilienceDemo.retryWithBackoff()`

```java
static void retryWithBackoff() {
    int maxAttempts = 5;
    long baseMs = 100;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        if (flakyCall(0.6)) {                       // 60% failure rate
            System.out.println("  attempt " + attempt + ": SUCCESS");
            return;
        }
        if (attempt == maxAttempts) { /* give up */ break; }
        long backoff = baseMs * (1L << (attempt - 1));          // 100, 200, 400, 800...
        long jitter = ThreadLocalRandom.current().nextLong(backoff / 2 + 1);
        long wait = backoff / 2 + jitter;
        // attempt N: failed - retry in ~<wait>ms (backoff+jitter)
    }
}
```

`1L << (attempt - 1)` doubles `baseMs` each attempt — `100 * 1 = 100`, `100 * 2 = 200`,
`100 * 4 = 400`, `100 * 8 = 800` for attempts 1 through 4. The jitter calculation
(`backoff/2 + random(0, backoff/2)`) picks a wait time somewhere in the *second half* of
the doubling window rather than an exact fixed value — so if a thousand clients all hit
this same flaky dependency simultaneously and all fail on attempt 1, their retries spread
out across a range of times instead of all firing again at exactly 100ms. With a 60%
failure rate simulated per attempt, most runs of this demo succeed within the first
2-3 attempts; the loop gives up cleanly after `maxAttempts` (5) rather than retrying
forever.

### Why it's useful

Exponential backoff with jitter is the standard, expected answer to "how should a client
retry a failing call?" — and naming *both* pieces (not just "retry with backoff," and
not just "add randomness") plus the idempotency requirement is what separates a complete
answer from a partial one. Every major cloud SDK (AWS SDK, gRPC clients) implements this
exact pattern by default for retryable errors.

## 6.3 — Circuit breaker

Retries handle a single call failing; the circuit breaker handles a *dependency*
failing repeatedly — it stops the calls from being attempted at all for a while, so
failures fail fast instead of piling up.

### Key Concepts

- **Three states**: **CLOSED** — calls pass through normally; the breaker counts
  failures. **OPEN** — once failures cross a threshold, the breaker rejects calls
  immediately without even attempting them (fail fast), for a fixed cooldown period,
  giving the dependency time and room to recover without being hammered by ongoing
  traffic. **HALF-OPEN** — after the cooldown elapses, the breaker allows exactly one
  trial call through; if it succeeds, the breaker closes (back to normal); if it fails,
  the breaker reopens (another full cooldown).
- **Why "fail fast" matters** — a call rejected instantly by an OPEN breaker costs
  effectively nothing (no thread blocked, no connection held); a call that's allowed
  through to a dead dependency costs a full timeout's worth of a blocked thread (6.1).
  Tripping the breaker converts a slow, resource-consuming failure into a fast, cheap
  one.
- **Prevents cascading failure** — without a breaker, every caller of a dead dependency
  keeps trying, keeps timing out, keeps consuming threads/connections — exactly the
  scenario from 6.1, except now compounded across every single request instead of
  bounded by one timeout. The breaker stops the bleeding at the dependency boundary.
- **Real libraries**: Resilience4j and Spring Cloud Circuit Breaker implement this
  pattern (with additional configuration: failure-rate thresholds over a sliding window,
  slow-call thresholds, configurable cooldowns) so you don't hand-roll it in production.

### Worked example — `ResilienceDemo.CircuitBreaker` and `circuitBreaker()`

```java
enum State { CLOSED, OPEN, HALF_OPEN }
class CircuitBreaker {
    State state = State.CLOSED;
    int failures = 0;
    final int threshold = 3;
    long openedAt = 0;
    final long cooldownMs = 300;

    boolean allowRequest() {
        if (state == State.OPEN) {
            if (System.currentTimeMillis() - openedAt >= cooldownMs) {
                state = State.HALF_OPEN;             // time to test recovery
                return true;
            }
            return false;                            // still open -> fail fast
        }
        return true;                                 // CLOSED or HALF_OPEN
    }
    void onSuccess() { failures = 0; state = State.CLOSED; }
    void onFailure() {
        failures++;
        if (state == State.HALF_OPEN || failures >= threshold) {
            state = State.OPEN; openedAt = System.currentTimeMillis();
        }
    }
}
```

```
Phase A: dependency is DOWN (always fails).
  call 1: tried dependency -> FAIL (CLOSED)
  call 2: tried dependency -> FAIL (CLOSED)
  call 3: tried dependency -> FAIL (CLOSED)     <- failures hits threshold=3, OPENS
  call 4: REJECTED (circuit OPEN - fail fast)
  call 5: REJECTED (circuit OPEN - fail fast)
  call 6: REJECTED (circuit OPEN - fail fast)
  breaker state: OPEN, 3 calls failed fast (no thread wasted)

Phase B: wait out the 300ms cooldown; dependency has recovered.
  after cooldown: HALF_OPEN trial -> dependency healthy -> SUCCESS
  breaker state: CLOSED (recovered)
```

The first 3 calls actually reach the (dead) dependency and fail, tripping `failures >=
threshold` and setting `state = OPEN`. Calls 4-6 never even attempt the dependency —
`allowRequest()` sees `state == OPEN` and the cooldown hasn't elapsed, so it returns
`false` immediately, costing nothing. After sleeping past the 300ms cooldown, the next
`allowRequest()` call transitions to `HALF_OPEN` and allows exactly one trial through; it
succeeds (the dependency has recovered by this point in the demo), `onSuccess()` resets
`failures` to 0 and returns the breaker to `CLOSED`. Notice `onFailure()`'s special case:
a failure *while HALF_OPEN* reopens immediately regardless of the failure count — one
bad trial call is enough evidence the dependency isn't actually recovered yet, no need to
accumulate 3 more failures first.

### Why it's useful

The circuit breaker is what turns "our payment service is down" from "every service
that calls it also grinds to a halt, one exhausted thread pool at a time" into "every
service that calls it gets a fast, clean rejection it can handle gracefully (6.4)."
It's a load-shedding mechanism specifically scoped to one dependency, complementing
system-wide rate limiting (Phase 5.5) which protects against overload from *callers*
rather than failure of a *callee*.

## 6.4 — Bulkheads and graceful degradation

### Key Concepts

- **Bulkhead** — isolate the resources (thread pools, connection pools) used to call
  each dependency, so that one dependency being slow or failing can only exhaust *its
  own* pool, not the resources shared by calls to every other dependency. Named after a
  ship's watertight compartments: a hull breach floods one compartment, not the whole
  ship. Without bulkheads, a single misbehaving dependency sharing a thread pool with
  everything else can starve unrelated, perfectly healthy calls of threads too.
- **Graceful degradation / fallbacks** — when a *non-critical* dependency is unavailable,
  degrade the response instead of failing the whole request: serve a stale cached value,
  hide a recommendations widget, return a sensible default. The core function (checkout
  still works even if the "customers also bought" widget's service is down) keeps
  working.
- **Load shedding** — under genuine overload, deliberately and explicitly reject
  low-priority requests (429/503) to preserve capacity for higher-priority ones, rather
  than trying to serve everything and having the whole system degrade for everyone.
  Related to rate limiting (Phase 5.5), but framed around *prioritization under load*
  rather than per-client fairness.

### Worked example — bulkheads preventing cross-dependency failure

```
Without bulkheads: ONE shared thread pool (size 50) serves calls to both
the Recommendations service and the Payments service.

Recommendations service goes slow (each call takes 10s instead of 50ms).
Within a couple seconds, most of the 50 shared threads are blocked waiting
on Recommendations calls. Payments calls -- for a TOTALLY UNRELATED,
perfectly healthy dependency -- now also have no free thread and start
failing too. Checkout breaks because the recommendations widget is slow.

With bulkheads: Recommendations gets its OWN pool (say, 10 threads);
Payments gets its OWN separate pool (say, 20 threads). Recommendations
being slow exhausts its 10-thread pool -- the recommendations widget
fails/degrades -- but Payments' 20-thread pool is completely untouched.
Checkout still works.
```

### Why it's useful

Bulkheads answer the specific follow-up an interviewer often asks right after circuit
breakers: "OK, but what if the dependency that's failing shares infrastructure with a
dependency that isn't?" — the circuit breaker alone stops calls to the bad dependency,
but only bulkheads stop a shared-resource exhaustion from spreading sideways to
unrelated, healthy dependencies in the meantime.

## 6.5 — Redundancy, failover, and health checks

### Key Concepts

- **No single point of failure (SPOF)** — every component whose failure would take down
  the whole system needs redundancy: multiple app server instances, replicated databases
  (Phase 3.1), redundant load balancers (Phase 5.1), multi-availability-zone or
  multi-region deployment for the most critical systems. Systematically find SPOFs by
  asking, for every box in your architecture diagram, "what happens if *this specific
  thing* dies right now?"
- **Failover** — automatically routing traffic away from a dead or unhealthy component
  to a healthy replacement: promoting a database replica to leader (Phase 3.1), shifting
  load-balancer traffic off an unhealthy server. Requires fast, accurate failure
  detection — a failover mechanism that takes 10 minutes to notice a dead leader isn't
  much better than no failover at all for a service with a tight availability SLO.
- **Liveness vs. readiness health checks** — **liveness**: is the process itself alive
  and not deadlocked? A failed liveness check triggers a restart. **Readiness**: can
  this instance *currently* serve traffic correctly — are its dependencies reachable, has
  it finished warming up? A failed readiness check tells the load balancer/orchestrator
  to stop routing traffic to it, without necessarily restarting it (a Java process doing
  a slow startup is alive but not yet ready). Both are exposed via Spring Boot Actuator's
  `/health` endpoint (Phase 8, Spring track) and consumed by load balancers and
  Kubernetes.
- **Dead-letter queue (DLQ)** — messages that repeatedly fail processing are moved aside
  into a separate holding queue after N attempts, rather than blocking the main queue
  indefinitely or being silently dropped. They can be inspected, fixed, and replayed
  later (Phase 9).

### Why it's useful

"Liveness restarts, readiness stops routing" is a precise distinction interviewers
specifically probe for, because getting it backwards (restarting an instance that's
merely still warming up, or continuing to route traffic to a genuinely deadlocked
process) causes real operational incidents in Kubernetes-based deployments — it's not a
purely academic distinction.

## 6.6 — Blast radius and failure-testing culture

### Key Concepts

- **Blast radius** — how much of the system, and how many users, are actually affected
  when one specific thing fails. Deliberately shrink it: partitioning/sharding (Phase
  3.4 — a bad shard affects `1/N` of users, not everyone), cell-based architecture
  (fully isolated, independently-deployed copies of the whole stack, each serving a
  subset of users, so a cell-wide failure only affects that cell's users), rate limits
  and bulkheads (containing a failure to one dependency or one client), and gradual
  rollouts (canary or blue-green deploys, so a bad release is caught after affecting a
  small fraction of traffic rather than everyone at once).
- **Chaos engineering** — deliberately injecting real failures (killing a node, adding
  artificial network latency, cutting off a dependency) under controlled conditions, in
  order to verify the system actually degrades the way you *designed* it to, rather than
  assuming it will. Netflix's Chaos Monkey (randomly terminating production instances)
  is the canonical example. The underlying philosophy: you don't actually know a system
  is resilient until you've watched it fail and recover under a real, injected failure —
  untested failure-handling code is exactly as trustworthy as untested code in general.
- **RTO vs. RPO** — **RTO** (Recovery Time Objective): how quickly the system must be
  back up after an incident. **RPO** (Recovery Point Objective): how much data loss,
  measured in time, is tolerable (e.g., an RPO of 5 minutes means losing at most the
  last 5 minutes of writes is acceptable). Together these two numbers drive concrete
  backup frequency and replication strategy decisions — a tight RPO forces synchronous
  or near-synchronous replication (Phase 3.1); a tight RTO forces automated, fast
  failover rather than a manual, paged-human recovery process.

### Why it's useful

Everything in this phase composes into one layered picture: **timeouts and retries**
operate at the level of a single call; **circuit breakers and bulkheads** operate at the
level of a dependency; **redundancy and failover** operate at the level of
infrastructure; **graceful degradation** operates at the level of the product/user
experience; and **chaos engineering** is how you verify all of the above actually work
together, rather than trusting that they do because you wrote the code. Combined with
**idempotency** (Phase 4) and **dead-letter queues** (Phase 9), this is the full toolkit
for "design a reliable system" — and stating it as a layered stack, not a flat list, is
what shows an interviewer you understand how the pieces fit together.

## Summary / Key Takeaways

- **Timeouts on every remote call** are non-negotiable — without one, a single slow
  dependency can exhaust a caller's thread/connection pool and cascade the failure
  upstream to unrelated requests.
- **Retries need exponential backoff + jitter** (demo: 100ms → 200ms → 400ms...,
  randomized within each window) to recover from transient failures without adding load
  to an already-struggling dependency or synchronizing a retry storm — and must be
  restricted to **idempotent** operations (Phase 4).
- The **circuit breaker** (CLOSED → OPEN → HALF-OPEN) makes calls to a known-failing
  dependency **fail fast** instead of piling up, converting a slow, resource-consuming
  failure into a cheap, instant one (demo: 3 failures trip it OPEN; a HALF-OPEN trial
  after cooldown closes it again on success).
- **Bulkheads** isolate resources per dependency so one dependency's failure can't
  starve calls to unrelated, healthy dependencies sharing the same infrastructure.
- **Redundancy + failover + health checks** (liveness restarts; readiness stops routing)
  eliminate single points of failure at the infrastructure level; **graceful degradation**
  and **load shedding** keep the core product working when a non-critical dependency or
  overall capacity is compromised.
- **Blast radius reduction** (sharding, cell-based architecture, canary deploys) and
  **chaos engineering** (deliberately injecting failure to verify resilience) are how you
  bound and *prove* the damage any single failure can do, rather than hoping it stays
  contained; **RTO/RPO** turn "how reliable" into concrete backup and failover
  requirements.
