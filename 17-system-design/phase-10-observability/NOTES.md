<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · messaging](../phase-9-messaging/NOTES.md) | [Phase 11 · design problems ➡](../phase-11-design-problems/NOTES.md)
<!-- /nav -->

# Phase 10 — Observability & Operations: Notes

You can't operate what you can't see. **Observability** is the ability to understand a
system's internal state and behavior purely from its external outputs — to answer
"is it healthy?" and, more importantly, "why is it slow or broken right now?" without
having to add new instrumentation or ship new code to find out. This is a distinct,
broader concept from **monitoring** (predefined dashboards and threshold alerts for
*known* failure modes you anticipated in advance) — observability is what lets you
investigate problems nobody anticipated. This phase has no standalone Java demo; it's
the operational layer that lets you actually detect and diagnose the failure modes
described throughout every earlier phase (a thrashing cache, an exhausted pool, a
failing dependency, an SLO about to be breached).

## 10.1 — The three pillars: logs, metrics, traces

### Key Concepts

- **Logs** — discrete, timestamped records of individual events: "order 42 failed:
  timeout after 5000ms." Use **structured logging** — JSON with consistent fields
  (`level`, `timestamp`, `service`, `trace_id`, `user_id`) — rather than free-text prose,
  so logs are actually searchable and aggregatable across thousands of log lines from
  many servers (SLF4J/Logback, Java Phase 6.3). Centralize logs from every server into
  one searchable system (the ELK stack — Elasticsearch/Logstash/Kibana, or Grafana Loki,
  or Splunk) — grepping individual servers one at a time doesn't scale past a handful of
  instances. Log at the right severity level (Java Phase 6.3's distinction between DEBUG,
  INFO, WARN, ERROR), never log secrets or PII, and always include a **correlation/trace
  id** on every log line related to one request, so you can pull every log line for that
  request across every service it touched.
- **Metrics** — aggregated numeric measurements sampled over time: request rate, error
  rate, p50/p95/p99 latency, CPU, memory, queue depth, connection-pool usage. Cheap to
  store (a rolling numeric time series is far smaller than raw log text) and ideal for
  dashboards and threshold-based alerts. The standard stack is **Micrometer →
  Prometheus → Grafana**, with Spring Boot Actuator (Spring Boot Phase 8) exposing
  application metrics in a format Prometheus can scrape. Two widely used metric
  frameworks worth naming specifically: the **RED method** (Rate, Errors, Duration) for
  service-level metrics, and **USE** (Utilization, Saturation, Errors) for resource-level
  metrics (CPU, disk, connection pools).
- **Traces** — the path of a single request as it travels across multiple services,
  broken into per-hop **spans**, each with its own start time and duration. **Distributed
  tracing** (OpenTelemetry, Jaeger, Zipkin) propagates a shared trace id through every
  call in the chain, letting you reconstruct the entire request's path and see exactly
  how much time each individual hop consumed — essential once a single user-facing
  request might touch eight or ten different microservices, where logs and metrics alone
  can tell you *that* something is slow but not *where in the chain*.
- **How the three combine**: metrics tell you *something* is wrong and roughly how much
  ("p99 latency jumped from 150ms to 2s at 3:14pm"); traces tell you *where* in a
  request's path the extra time is concentrated ("span for the inventory-service call
  went from 20ms to 1.8s"); logs tell you *why*, at that specific service, in specific
  detail ("inventory-service: connection pool exhausted, 45 requests waiting").

### Worked example — going from a metric alert to a root cause

```
1. METRIC alert fires: p99 latency for /checkout jumped from 180ms to 2.4s
   over the last 10 minutes. (Rate/Errors/Duration -- Duration spiked.)

2. TRACE lookup: pull a sample of slow /checkout traces from the same
   window. Every one shows the SAME pattern: a span calling
   inventory-service now takes ~2.1s, where it normally takes ~15ms.
   Every other span (auth, payment, notification) is normal.
   -> narrowed the problem from "checkout is slow" to "inventory-service
      calls specifically are slow" without touching a single log line yet.

3. LOG lookup: filter inventory-service's structured logs by trace_id for
   one of the slow requests. Find:
   {"level":"WARN","service":"inventory-service","trace_id":"...",
    "msg":"connection pool: 0/10 available, request queued 2043ms"}
   -> root cause: inventory-service's DB connection pool is exhausted
      (Phase 7.1), not a network issue, not inventory-service's own CPU.
```

### Why it's useful

This exact metric → trace → log narrowing sequence is the practical, day-to-day
workflow observability exists to enable — and it directly demonstrates why all three
pillars are needed together: the metric alone tells you "something's wrong" without
telling you where; the trace alone (without a shared trace id propagated through logs)
would tell you *which service* is slow but not *why* that service is slow; the log
alone, without the trace narrowing you to the right service and time window first,
would be an impossibly large haystack to search through.

## 10.2 — Alerting

### Key Concepts

- **Alert on symptoms users actually feel, not every possible low-level cause** — an
  elevated error rate, an SLO-breaching latency, a backing-up queue are symptom-level
  alerts worth waking someone up for; "CPU on host `web-47` hit 85% for 30 seconds" is
  usually not, on its own, something that needs immediate human action.
- **Alert fatigue is a real, serious failure mode** — too many alerts, especially
  low-signal ones, trains on-call engineers to start ignoring or reflexively dismissing
  alerts, which means the one alert that actually mattered gets missed along with all
  the noise. Fewer, higher-quality, higher-confidence alerts beat comprehensive but noisy
  coverage.
- **Every alert should be actionable and tied to a runbook** — if an alert fires and
  there's genuinely nothing a human can or should do in response, it shouldn't be paging
  anyone; it might belong on a dashboard instead, reviewed during business hours rather
  than waking someone at 3am.

### Why it's useful

The discipline of "alert on symptoms, not causes" is what keeps an on-call rotation
sustainable at scale — a system with hundreds of services generates an enormous number
of possible low-level anomalies at any given moment, and paging on all of them (rather
than on the small number of user-visible symptoms those anomalies might eventually
cause) burns out on-call engineers and, paradoxically, makes the system *less* reliable
because real signals get lost in noise.

## 10.3 — Dashboards and golden signals

### Key Concepts

- **Google SRE's four golden signals**: **latency** (how long requests take), **traffic**
  (how much demand the system is under), **errors** (the rate of failed requests), and
  **saturation** (how "full" the system is — how close to its resource limits, which
  predicts future trouble before it becomes visible in latency or errors). These four
  numbers, watched together on one dashboard per service, give an at-a-glance read of
  system health without needing to dig into details.
- **One overview dashboard per service** as a standard convention makes it fast, during
  an incident, to check any given service's health without having to first figure out
  which of many ad-hoc dashboards has the relevant numbers.

### Why it's useful

The four golden signals are deliberately a *minimal* set — not "every metric you could
possibly collect," but specifically the four that together answer "is this service
healthy right now?" for almost any kind of service, which is exactly what you want
available at a glance during an incident when time matters.

## 10.4 — SLOs and error budgets, revisited

### Key Concepts

- Building directly on Phase 1.4: an **SLO** (e.g., "99.9% of requests complete under
  200ms, measured over a rolling 30 days") is the reliability target; the **error
  budget** (`1 − SLO`) is how much unreliability the team is allowed to "spend" before
  it must prioritize reliability work over new features.
- **Burning the budget** (an incident or a series of degraded periods consuming most or
  all of the allotted budget for the period) is the trigger to **freeze risky changes**
  and focus engineering effort on reliability until the budget recovers. **Budget to
  spare** is the signal that the team can ship faster, take on riskier changes, or run
  chaos experiments (Phase 6.6), because there's genuine margin.
- This mechanism turns "should we prioritize reliability or feature velocity this
  sprint?" from a subjective, often political argument into an objective, data-driven
  decision derived directly from the error-budget number.

### Why it's useful

Error budgets are the concrete operational mechanism that connects an abstract
reliability target (the SLO) to day-to-day engineering prioritization decisions — it's
the difference between "we should be more careful" as a vague value statement and "we've
burned 80% of this month's error budget in one incident, so no risky deploys until it
resets" as an unambiguous, actionable rule.

## 10.5 — On-call and incident response

### Key Concepts

- **On-call rotation and escalation** — someone is designated as responsible when
  something breaks, with a clear escalation path if the primary on-call can't resolve it
  or needs help, so incidents don't sit unaddressed waiting for someone to notice.
- **Runbooks** — documented, step-by-step response procedures for known failure modes
  ("if the checkout error rate spikes, first check X, then Y"), so responding to a
  familiar incident doesn't depend on one specific engineer's memory of how they fixed
  it the last time.
- **Blameless postmortems** — a written analysis after an incident that focuses
  explicitly on *how the system and processes allowed the incident to happen* and *how
  to prevent recurrence*, deliberately avoiding blaming the specific individual(s)
  involved. Blame culture suppresses honest, detailed incident reporting (people
  downplay or hide their own mistakes when blame is the expected consequence);
  blameless culture surfaces the real, complete sequence of contributing factors, which
  is what actually produces durable systemic fixes rather than a scapegoat and a
  repeat incident later.
- **MTTD and MTTR** — **Mean Time To Detect** (how long between a problem starting and
  someone/something noticing it) and **Mean Time To Recover** (how long from detection
  to full resolution). Tracked over time as the primary measures of whether an
  organization's observability and incident-response investments are actually improving
  outcomes.

### Why it's useful

Blameless postmortems and runbooks are cultural/process investments, not purely
technical ones, but they show up constantly in system-design and behavioral interviews
because they directly determine whether an organization's MTTR trends down over time
(learning from each incident and encoding that learning into a runbook and system fixes)
or stays flat (each incident treated as an isolated, un-generalized one-off).

## The operational picture

Instrument the application (metrics via Actuator/Micrometer, structured logs carrying
trace ids, spans emitted via OpenTelemetry) → ship that data to backends (Prometheus for
metrics, a centralized log aggregator, a tracing backend like Jaeger) → visualize on
dashboards (Grafana, organized around the four golden signals) and alert on symptom-level
SLO breaches → page on-call with an actionable runbook → run a blameless postmortem to
turn the incident into a durable fix and, often, a new alert or dashboard panel that
would have caught it sooner next time. Observability is the thread that closes the loop
on every earlier phase in this track: it's how you actually *know*, in production, that
a cache is thrashing (Phase 2's hit-ratio metric), a connection pool is exhausted (Phase
7.1's pool metrics), a dependency is failing (Phase 6's error-rate and circuit-breaker-
state metrics), or you're about to breach an SLO (10.4) — none of Phase 2 through 9's
reliability and performance work is verifiable in production without it.

## Summary / Key Takeaways

- **Logs** (discrete events, structured/JSON, centralized, correlated by trace id),
  **metrics** (aggregated numbers over time — RED for services, USE for resources), and
  **traces** (one request's full path across services, broken into timed spans) are the
  three pillars — together they let you go from "something's wrong" (metric) → "where"
  (trace) → "why, specifically" (log).
- **Observability** (answering arbitrary, unanticipated questions from a system's
  outputs) is broader than **monitoring** (predefined dashboards/alerts for known
  problems) — monitoring is a subset of what observability enables.
- **Alert on symptoms users feel** (error rate, SLO-breaching latency, backing-up
  queues), keep every alert **actionable and tied to a runbook**, and be deliberately
  sparing — alert fatigue causes the one alert that matters to get lost in noise.
- The **four golden signals** (latency, traffic, errors, saturation) give an at-a-glance
  health read per service; **SLOs and error budgets** (Phase 1.4) turn "how reliable
  should we be, and how careful should we be right now" into an explicit, data-driven,
  spendable number.
- **Blameless postmortems** (fix the system and process, not the person) and **runbooks**
  are what turn individual incidents into durable, compounding improvements — tracked
  via **MTTD/MTTR** trending down over time as the measure of whether the whole
  observability + incident-response investment is actually working.
