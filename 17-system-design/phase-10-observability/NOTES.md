<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · messaging](../phase-9-messaging/NOTES.md) | [Phase 11 · design problems ➡](../phase-11-design-problems/NOTES.md)
<!-- /nav -->

# Phase 10 — Observability & Operations: Notes

You can't operate what you can't see. **Observability** is the ability to understand a system's internal state from its outputs — to answer "is it healthy?" and "why is it slow/broken?" without shipping new code. Distinct from **monitoring** (predefined checks/dashboards for known problems); observability lets you investigate *unknown* problems.

## 10.1 — The three pillars
- **Logs** — discrete, timestamped events ("order 42 failed: timeout"). Use **structured logging** (JSON with fields: level, timestamp, service, trace id, user id) so logs are searchable/aggregatable, not just human prose (SLF4J/Logback — Java Phase 6.3). Centralize them (ELK/Loki/Splunk) — grepping individual servers doesn't scale. Log at the right level (Java 6.3), never log secrets/PII, and include a **correlation/trace id** so you can follow one request across services.
- **Metrics** — aggregated numeric measurements over time (request rate, error rate, p50/p95/p99 latency, CPU, memory, queue depth, pool usage). Cheap to store, ideal for dashboards and alerts. **Micrometer → Prometheus → Grafana** is the common stack (Spring Actuator exposes them — Spring Boot Phase 8). Watch the **RED** method (Rate, Errors, Duration) for services and **USE** (Utilization, Saturation, Errors) for resources.
- **Traces** — the path of a single request across services, with timing per hop (span). **Distributed tracing** (OpenTelemetry, Jaeger, Zipkin) propagates a trace id through every call so you can see *where* the latency is in a request that touched 8 services. Essential in microservices.

Logs tell you *what happened*, metrics tell you *how much/how often*, traces tell you *where* in the flow. Together they let you go from "latency spiked" (metric alert) → "which requests" (trace) → "why" (logs).

## 10.2 — Alerting, dashboards, SLOs, on-call
- **Alerting** — notify humans when something needs action. **Alert on symptoms users feel** (error rate up, latency SLO breached, queue backing up), not every low-level cause (avoid alert fatigue — too many alerts = ignored alerts). Every alert should be actionable and tied to a runbook.
- **SLOs & error budgets** (Phase 1) — define a target (99.9% of requests < 200ms); the **error budget** (1 − SLO) is how much unreliability you can spend. Burning the budget → freeze features and fix reliability; budget to spare → ship faster. This balances velocity vs stability objectively.
- **Dashboards** — at-a-glance system health (the golden signals: latency, traffic, errors, saturation — Google SRE's four). One overview dashboard per service.
- **On-call & incident response** — someone is responsible when things break; a clear escalation path, runbooks for common failures, and **blameless postmortems** (fix the system, not the person) that turn incidents into prevention. Track MTTD (mean time to detect) and MTTR (mean time to recover).

## The operational picture
Instrument the app (metrics via Actuator/Micrometer, structured logs with trace ids, spans via OpenTelemetry) → ship to backends (Prometheus, a log aggregator, a tracing system) → visualize (Grafana) and alert (on SLOs/golden signals) → page on-call with a runbook → postmortem to prevent recurrence. Observability closes the loop with everything else in this track: it's how you *know* a cache is thrashing (Phase 2), the pool is exhausted (Phase 7), a dependency is failing (Phase 6), or you're about to breach an SLO (Phase 1).
