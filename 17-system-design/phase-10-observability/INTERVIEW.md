<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 9 · messaging](../phase-9-messaging/NOTES.md) | [Phase 11 · design problems ➡](../phase-11-design-problems/NOTES.md)
<!-- /nav -->

# Phase 10 — Observability & Operations: Interview Q&A

⭐ = asked constantly.

**Q: What are the three pillars of observability, and how do they actually work
together during an incident?** ⭐⭐
Logs are discrete, timestamped events — "what happened," at a specific point in time, in
specific detail. Metrics are aggregated numbers sampled over time — "how much/how often,"
cheap to store and ideal for dashboards and alerting. Traces are the path of a single
request across multiple services, broken into timed spans — "where," specifically, the
time in a request went. In practice they chain together: a metric alert tells you
something's wrong (p99 latency for an endpoint jumped) and roughly how much; you pull a
sample of traces from the affected window to see which specific span/service is
consuming the extra time; then you filter that service's structured logs by the trace id
from one of those slow traces to find the specific root cause (say, a connection-pool
exhaustion warning). Each pillar narrows the search space for the next — logs alone,
without the trace narrowing you to the right service and time window first, would be an
impossibly large haystack.

**Q: Monitoring vs. observability — what's the actual distinction?**
Monitoring watches for known, predefined problems — dashboards and threshold alerts set
up in advance for failure modes you anticipated ("alert if error rate exceeds 1%").
Observability is the broader capability to ask arbitrary, unanticipated questions about
a system's internal state purely from its external outputs (logs, metrics, traces) —
letting you debug problems nobody specifically set up a dashboard for, without having to
ship new instrumentation first. Monitoring is effectively a subset of what a genuinely
observable system enables; a system can be well-monitored (lots of dashboards for known
failure modes) while still being poorly observable (impossible to debug a genuinely
novel problem without adding new logging and redeploying).

**Q: Why use structured logging instead of free-text log messages?** ⭐
Structured logs (JSON with consistent fields — level, timestamp, service name, trace id,
user id) are searchable and aggregatable across potentially thousands of log lines from
many servers — you can filter, group, and query on specific fields, which free-text
prose doesn't support well at any real scale. Centralizing logs into one searchable
system (ELK, Grafana Loki, Splunk) is necessary because grepping individual servers one
at a time doesn't scale past a handful of instances. Critically, every log line related
to a given request should carry that request's correlation/trace id, so you can pull
every log line touching that request across every service it passed through — without
that shared id, correlating logs from different services for the same request is
essentially guesswork based on timestamps alone. And never log secrets or PII into
these centralized, broadly-accessible systems.

**Q: What is distributed tracing and why do you specifically need it in a
microservices architecture?** ⭐⭐
Distributed tracing propagates a shared trace id through every service call involved in
handling one request, with each hop recording its own timed span (start time, duration,
metadata). This lets you reconstruct a single request's complete path across every
service it touched and see exactly how much time each individual hop consumed.
It's essential specifically in microservices because a single user-facing request might
fan out across eight or ten independently-deployed services — metrics alone can tell you
*that* an endpoint got slower, and logs alone (without a shared trace id) can't easily
tell you which of those many services, on this specific slow request, was actually
responsible; only a trace shows you the full picture of where the time is concentrated
across the whole chain. Tools: OpenTelemetry (the current standard instrumentation
API), Jaeger, Zipkin.

**Q: What metrics would you actually watch for a web service, and what frameworks
organize that?** ⭐
The RED method for services: Rate (requests per second), Errors (error rate), Duration
(latency, specifically p50/p95/p99, not just the average). USE for resources:
Utilization, Saturation, Errors — applied to CPU, memory, disk, connection pools.
Google's four golden signals generalize this further: latency, traffic, errors, and
saturation, watched together on one dashboard per service as an at-a-glance health
check. Beyond these general frameworks, add domain-specific metrics relevant to the
specific service — cache hit ratio, queue depth, connection-pool active/idle/waiting
counts — the exact numbers that would let you catch, say, a cache thrashing (Phase 2) or
a pool heading toward exhaustion (Phase 7.1) before it becomes a user-visible incident.

**Q: What should you actually alert on, and why does over-alerting make a system less
reliable, not more?** ⭐
Alert on symptoms real users actually experience — an elevated error rate, latency
breaching its SLO, a queue backlog that's genuinely growing — rather than every possible
low-level internal anomaly. Every alert should be actionable, tied to a specific runbook
telling the responder what to do; an alert nobody can meaningfully act on shouldn't be
paging anyone at 3am. The reason over-alerting is actively counterproductive is alert
fatigue: too many low-signal alerts trains on-call engineers to start reflexively
dismissing or delaying response to alerts, which means the rare alert that actually
represents a real, urgent problem gets lost in the noise along with everything else —
fewer, higher-confidence alerts genuinely produce better outcomes than exhaustive but
noisy coverage.

**Q: What are SLOs and error budgets, and how do teams actually use the error budget
day to day?** ⭐
An SLO is an internal reliability target — "99.9% of requests complete under 200ms,
measured over a rolling 30 days." The error budget is `1 − SLO`: the amount of
unreliability the team is allowed to "spend" within that period. Teams use it as an
explicit, objective decision rule rather than a vague aspiration: if an incident (or a
series of degraded periods) consumes most or all of a month's error budget, the team
freezes further risky deploys and shifts focus to reliability work until the budget
recovers; if there's budget to spare, the team has objective justification to ship
faster or take on riskier changes, including running chaos experiments (Phase 6.6). It
converts "should we prioritize reliability or feature velocity right now" from a
subjective, often political argument into a number everyone can look at and agree on.

**Q: Why optimize and alert on p99 latency instead of the average?**
Averages hide tail behavior — a small percentage of dramatically slow requests can leave
the mean looking fine while a meaningful absolute number of real users (at any real
traffic volume) have a genuinely bad experience. This gets worse in a system where a
single request fans out to multiple internal service calls: if each of several
downstream dependencies independently has even a small chance of being slow on any given
call, the probability that *at least one* of them is slow on a given request compounds
above any individual dependency's own tail rate — which is exactly why distributed
tracing (this phase) matters so much for diagnosing tail-latency problems in a
multi-service system. SLOs and alerts are defined on p95/p99 specifically because that's
what determines the experience of the worst-treated fraction of users, not the typical
one.

**Q: What is a blameless postmortem, and why does the "blameless" part actually
matter, not just sound nice?**
A written analysis conducted after an incident, focused explicitly on how the system and
processes allowed the incident to happen and what concrete changes prevent it from
recurring — deliberately not on assigning blame to the specific individual(s) involved.
The practical reason it matters: a blame-oriented culture causes people to downplay,
omit, or hide details about their own role in an incident because they reasonably expect
punishment for full honesty, which means the actual, complete sequence of contributing
factors never surfaces and the same class of incident recurs later. A blameless culture
removes that incentive to hide information, which is what actually produces detailed,
honest incident reports and durable systemic fixes rather than a scapegoat and a repeat
incident. Teams track MTTD (mean time to detect) and MTTR (mean time to recover) over
time as the concrete measure of whether this process is actually improving outcomes.

**Q: Walk through how you'd debug "the site is slow" in production, using this
phase's tools.** ⭐
Start with metrics: which specific service or endpoint is affected, what changed
recently (a deploy, a config change, a traffic pattern shift), is it latency specifically
or also errors, and at which percentile (p50 looking fine but p99 spiking points
somewhere different than a uniform slowdown across all percentiles). Then traces: pull a
sample of slow requests from the affected window and see which span in the chain is
consuming the extra time — this narrows "the site is slow" down to a specific
service or dependency. Then logs: filter that specific service's structured logs by the
trace ids of the slow sample requests to find the concrete root cause (a connection pool
exhaustion warning, a specific slow query, an exception). Throughout, check recent
deploys and configuration changes and the health of immediate dependencies (database,
cache, downstream services) as likely triggers. In a real incident, mitigate first —
rollback the suspicious deploy, scale out, shed load — and do the deeper root-cause
investigation in parallel or after, rather than leaving users in a degraded state while
you fully diagnose the issue.
