<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · messaging](../phase-9-messaging/NOTES.md) | [Phase 11 · design problems ➡](../phase-11-design-problems/NOTES.md)
<!-- /nav -->

# Phase 10 — Observability & Operations: Interview Q&A

⭐ = asked constantly.

**Q: What are the three pillars of observability?** ⭐⭐
Logs (discrete timestamped events — what happened), metrics (aggregated numbers over time — how much/how often), and traces (one request's path across services with per-hop timing — where the time went). Together: metrics alert you, traces localize the problem, logs explain it.

**Q: Monitoring vs observability?**
Monitoring watches for known/predefined problems (dashboards, threshold alerts). Observability is the broader ability to ask arbitrary questions about system state from its outputs — to debug *unknown* problems without deploying new code. Monitoring is a subset.

**Q: Why structured logging?** ⭐
Logs as JSON with consistent fields (level, timestamp, service, trace id, user id) are searchable, filterable, and aggregatable across many servers — unlike free-text prose. Centralize them and include a trace/correlation id to follow a request across services. Never log secrets/PII.

**Q: What is distributed tracing and why do you need it?** ⭐⭐
Propagating a trace id through every service call so you can reconstruct one request's full path and see the latency of each hop (span). Essential in microservices, where a single slow request might touch many services and metrics/logs alone can't tell you *where* the time went. OpenTelemetry/Jaeger/Zipkin.

**Q: What metrics would you watch for a web service?** ⭐
The RED method: Rate (requests/sec), Errors (error rate), Duration (latency p50/p95/p99). For resources, USE: Utilization, Saturation, Errors. Google's four golden signals: latency, traffic, errors, saturation. Plus domain metrics (queue depth, cache hit ratio, pool usage).

**Q: What should you alert on?** ⭐
Symptoms users experience — elevated error rate, latency SLO breaches, growing queue backlog, saturation — not every internal cause. Alerts must be actionable (tied to a runbook) and few enough to avoid alert fatigue; noisy alerts get ignored.

**Q: What are SLOs and error budgets, and how do teams use them?** ⭐
An SLO is a reliability target (99.9% of requests < 200ms); the error budget (1 − SLO) is the allowed unreliability. Spending the budget → prioritize reliability work; budget remaining → ship features faster. It turns the reliability-vs-velocity debate into a data-driven decision.

**Q: Why p99 latency instead of average?**
Averages hide the tail; at scale a request often fans out to many services, so the slowest dependency dominates user-perceived latency (a 1%-slow backend affects many page loads). SLOs target p95/p99 because that's what users actually feel.

**Q: What is a blameless postmortem?**
A written analysis after an incident focused on *how the system allowed it and how to prevent recurrence*, not on blaming individuals. Blame suppresses honesty; blameless reviews surface real causes and produce durable fixes. Track MTTD/MTTR to measure improvement.

**Q: How would you debug "the site is slow" in production?**
Start with metrics (which service/endpoint, what changed, is it latency or errors, which percentile) → traces (where in the request the time goes) → logs (the specific errors/slow queries) → check recent deploys/config and dependencies (DB, cache, downstream). Correlate via trace ids. Mitigate first (rollback/scale/shed), then root-cause.
