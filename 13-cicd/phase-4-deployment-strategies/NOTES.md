<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · jenkins](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 4 — Deployment Strategies & Best Practices: Notes

Getting a build to pass every gate is only half the job — the final step is getting the
new version in front of users **without downtime or unacceptable risk**. How you roll
out matters as much as what you built. This phase covers the standard rollout
strategies and the surrounding practices (rollback, migrations, secrets, pipeline
hygiene, security) that make deploys routine rather than terrifying. Several of these
strategies are implemented natively by Kubernetes (track 14); this phase covers the
concepts and trade-offs that apply regardless of which orchestrator executes them.

## 4.1 — Recreate

The simplest possible rollout: **stop every instance of the old version, then start the
new version.**

- **Mechanism:** old instances are terminated first; only once they're gone do new
  instances start. There is a gap in between where the service is entirely down.
- **Guaranteed downtime**, proportional to how long the new version takes to start and
  become ready (application boot time, health checks, warm-up caches, etc.).
- **No version overlap.** Because old and new never run simultaneously, you don't need
  to worry about two different code versions hitting the database schema at the same
  time — the simplest strategy from a compatibility standpoint, at the direct cost of
  availability.
- **When it's acceptable:** internal tools with an accepted maintenance window,
  batch/offline systems, or environments where a brief outage is genuinely fine
  (low-traffic dev/staging) and simplicity is worth more than uptime.

```
old: [v1] [v1] [v1]            (all running)
              ↓ stop all
             ( — nothing running — )   ← downtime window
              ↓ start all
new:                [v2] [v2] [v2]     (all running)
```

### Why it's useful

Recreate is the baseline every other strategy is compared against — it's the simplest
possible mental model (and often the simplest to configure) and the honest default when
uptime genuinely doesn't matter for a given service.

### Summary

- Stop everything, then start everything new — simple, but causes real downtime.
- No version overlap, so no compatibility concerns between old and new.
- Reserve it for services that can tolerate a gap; never for user-facing production
  traffic that needs to stay up.

## 4.2 — Rolling Update

Replace instances **incrementally**, a few at a time, so the service stays up
throughout the rollout. This is the default strategy for Kubernetes Deployments (track
14).

- **Mechanism:** take down a small batch of old instances, start an equivalent batch of
  new instances, wait for them to pass health checks, then repeat until every old
  instance has been replaced. At every point during the rollout, *some* instances are
  old and *some* are new, both serving live traffic.
- **Zero-downtime, gradual.** The overall service capacity dips only slightly (or not
  at all, if you over-provision by one extra instance during the rollout) rather than
  hitting zero.
- **Requires backward compatibility during the overlap.** Because old and new versions
  serve traffic *simultaneously*, both must be able to work correctly against the
  current state of the world — most critically, the database schema (see 4.6, the
  expand-then-contract discipline). This is the strategy's central trade-off versus
  Recreate: you trade a downtime window for a compatibility requirement.
- **Readiness/health gating matters.** A new instance should only receive traffic once
  it reports itself healthy (a readiness probe) — otherwise the rollout can route
  requests to an instance that's still starting up, producing errors even though the
  strategy is nominally "zero downtime."
- **Rollback is itself a rolling update**, just in the opposite direction — replace the
  new (bad) instances with the previous version's instances, incrementally.

```
old: [v1] [v1] [v1] [v1]
         ↓ replace one batch at a time, health-gated
     [v2] [v1] [v1] [v1]   ← v1 and v2 both serving traffic right now
     [v2] [v2] [v1] [v1]
     [v2] [v2] [v2] [v1]
new: [v2] [v2] [v2] [v2]
```

### Why it's useful

Rolling update is the practical default for most services: it removes the downtime
window of Recreate for a manageable cost (backward-compatible changes during the
overlap window), and it's what you get automatically from Kubernetes without any extra
infrastructure — no second full environment, no separate traffic-splitting layer.

### Summary

- Replace instances incrementally; old and new both serve traffic during the rollout.
- Zero (or near-zero) downtime, at the cost of requiring backward compatibility during
  the overlap.
- The Kubernetes Deployment default strategy.
- Rollback is a rolling update in reverse.

## 4.3 — Blue-Green Deployment

Run **two complete, independent environments** — "blue" (currently live) and "green"
(the new version) — and switch all traffic from one to the other **at once**, at the
load balancer/router level.

- **Mechanism:** deploy the new version entirely into the idle environment (green)
  while blue keeps serving all live traffic. Test/verify green fully, in isolation,
  with production-equivalent infrastructure but zero live traffic risk. When satisfied,
  flip the router so 100% of traffic now hits green; blue becomes idle (kept around, not
  torn down yet).
- **Instant cutover, instant rollback.** Because the switch is a routing change, not a
  redeploy, both directions are essentially instantaneous — if green misbehaves under
  real traffic, flip back to blue immediately. This is the strategy's biggest advantage
  over rolling update, where a rollback still takes as long as a fresh rolling update in
  reverse.
- **Double infrastructure cost**, at least temporarily — you need enough capacity to run
  two full production-sized environments simultaneously (even if briefly, around the
  cutover window).
- **No prolonged version overlap** (unlike rolling update): traffic is either 100% on
  blue or 100% on green, not split between them mid-rollout — which simplifies
  reasoning about compatibility, though in-flight requests during the exact cutover
  moment and shared downstream state (the database) still need care.
- **The database is usually still shared** between blue and green (running two full
  database copies is rarely practical), so schema changes still need the
  backward-compatible discipline from 4.6 even though the *application* switch is
  instant.

```
Live traffic ──────────► [blue: v1]     (green idle, being deployed/tested)
                    ↓ flip router
Live traffic ──────────► [green: v2]    (blue idle, kept as instant rollback target)
```

### Why it's useful

Blue-green is the strategy of choice when you want the fastest possible cutover and
rollback and can afford the doubled infrastructure — common for services where even a
brief gradual-rollout window is unacceptable, or where a very fast, clean rollback
matters more than infrastructure cost.

### Summary

- Two full environments; switch all traffic at once via the router/load balancer.
- Instant cutover and instant rollback — the standout advantage over rolling update.
- Costs roughly double infrastructure (at least around the cutover).
- The database is typically still shared, so schema compatibility still matters.

## 4.4 — Canary Deployment

Release the new version to a **small percentage of traffic or users first**, watch real
production metrics, then progressively ramp up to 100% — or roll back — based on what
you observe.

- **Mechanism:** route, say, 5% of live traffic to the new version while 95% stays on
  the old version. Monitor error rates, latency, and business metrics on that 5% slice
  specifically. If healthy, increase the percentage (5% → 25% → 50% → 100%) in steps;
  if unhealthy at any point, route traffic back to 0% on the new version — no user-wide
  incident, because the blast radius was capped from the start.
- **The safest strategy for risky changes.** Because only a fraction of traffic is
  exposed to a new version at any point, a bug affects a small, bounded set of users
  instead of everyone — the core idea is limiting blast radius, not avoiding risk
  entirely.
- **Often automated with metric-based promotion.** Mature setups tie the ramp to
  automated health checks against defined thresholds (error rate, p99 latency) rather
  than a human watching a dashboard — auto-promote on healthy metrics, auto-rollback on
  breach.
- **Requires good observability** (System Design track 17) to actually be safe — canary
  without meaningful metrics on the canary slice specifically is just "rolling update
  with extra steps," since you can't tell the canary is unhealthy without measuring it.
- **Version overlap, like rolling update.** Old and new versions run simultaneously
  (for potentially longer than a rolling update's transient overlap, since a canary can
  sit at a partial percentage for an extended observation period), so the same
  backward-compatibility discipline from 4.6 applies.

```
100% ──────────────────────────────────────► old (v1)
  5% ──► new (v2), watched closely
                ↓ metrics healthy → ramp
 25% ──► new (v2)
                ↓ metrics healthy → ramp
100% ──► new (v2)      (old fully retired)
      OR at any step: metrics unhealthy → 0% new, fully rolled back
```

### Why it's useful

Canary is the go-to strategy for changes you're genuinely unsure about — a risky
refactor, a new dependency, a performance-sensitive change — because it turns "will this
break production" from an all-or-nothing bet into a controlled, observable, reversible
experiment.

### Summary

- Ramp a new version to a small traffic slice first, watch metrics, then scale up or
  roll back.
- Limits blast radius rather than eliminating risk — the safest option for uncertain
  changes.
- Strong observability on the canary slice is a hard requirement, not optional.
- Overlap duration can be longer than rolling update's, so compatibility discipline
  still applies.

## 4.5 — Comparing the Rollout Strategies

| | Recreate | Rolling Update | Blue-Green | Canary |
|---|---|---|---|---|
| Downtime | Yes | None (health-gated) | None (instant switch) | None (gradual) |
| Version overlap | None | Brief, during rollout | None (traffic-wise) | Extended, by design |
| Infra cost | 1x | ~1x (+1 instance transient) | ~2x | ~1x (+ monitoring) |
| Rollback speed | Full redeploy | Reverse rolling update | Instant (flip router) | Instant (ramp to 0%) |
| Blast radius of a bug | 100% (after cutover) | Growing, incremental | 100% (after cutover) | Small, bounded, controllable |
| Complexity | Low | Low–medium (K8s default) | Medium–high (2 environments) | High (traffic splitting + metrics) |
| Best for | Tolerant/internal services | Routine, everyday changes | Fast cutover/rollback priority | Risky or uncertain changes |

### Why it's useful

Choosing a strategy is a risk/cost trade-off, not a "which is objectively best"
question — interviewers usually want to see that you can pick the right one for a given
scenario (a routine dependency bump vs. a risky rewrite of the payment path) rather than
recite definitions.

### Summary

- Recreate: simplest, only acceptable with real downtime tolerance.
- Rolling: the sensible default for routine, everyday changes.
- Blue-green: pay double infra for instant cutover/rollback.
- Canary: highest complexity, but the safest choice for genuinely risky changes.

## 4.6 — Rollback and Backward-Compatible Database Migrations

**Rollback** means quickly reverting to the previous known-good version after a bad
release — and the hardest part of doing it safely is the database, not the application
code.

- **Application rollback is easy** given immutable, versioned artifacts (Phase 1,
  §1.4): redeploy the previous image tag. Blue-green makes this instant (flip the
  router back); canary makes it instant (ramp to 0%); rolling update makes it a
  reverse rolling update.
- **The database doesn't roll back with the code.** A schema migration that ran as part
  of the bad release is still applied even after you redeploy the old application code —
  and if that migration wasn't backward-compatible, the *old* code may now be broken
  against the *new* schema, turning a rollback into a second incident.
- **Expand-then-contract** is the standard discipline for making migrations safe across
  a rolling/canary overlap (where old and new code run simultaneously against the same
  schema) *and* safe for rollback:
  1. **Expand** — add the new column/table/index without removing or renaming anything
     old. Deploy this migration; both old and new application code still work (old code
     simply ignores the new column).
  2. **Migrate code** — deploy the application version that reads/writes the new
     column. Old and new app versions both still function correctly against the
     now-expanded schema during any rolling/canary overlap.
  3. **Contract** — only once *every* instance is confirmed running the new code (no
     rollback risk remaining) do you remove the old column/table in a later,
     separate migration.
- **Never combine "remove the old column" with "start using the new column" in the same
  release** — that's exactly the migration shape that breaks under any strategy with
  version overlap, and makes rollback impossible without a second migration.

```
Release N:   ADD COLUMN email_verified BOOLEAN DEFAULT false;   -- expand, additive only
Release N+1: application code starts reading/writing email_verified
             (old and new app versions both still run fine against this schema)
Release N+2: DROP COLUMN legacy_verified_flag;                  -- contract, only once
             every instance is confirmed on N+1 and rollback to before N+1 is off the table
```

### Why it's useful

This is consistently one of the most probed "have you actually operated a production
system" interview topics, because it's the part of zero-downtime deployment that's easy
to get wrong in a way that looks fine until the exact moment a rollback is needed under
pressure.

### Summary

- Redeploying old code is trivial; the database is what makes rollback hard.
- Expand-then-contract: add new schema elements first, migrate code, remove old schema
  elements only after every instance is safely on the new code.
- Never remove/rename something old in the same release that starts depending on
  something new.

## 4.7 — Secrets Management at Deploy Time

Credentials (database passwords, API keys, TLS certs) must reach the running
application without ever living in the image or the repo.

- **Inject at deploy/runtime**, not build time: environment variables populated from a
  secrets manager (HashiCorp Vault, a cloud provider's secret store, Kubernetes
  Secrets), never baked into a Docker image layer (track 12) or committed to source
  control.
- **Least privilege.** A given deployment/service should only be able to read the
  specific secrets it needs, not every secret in the system — scoped access, not a
  shared blanket credential.
- **Rotation.** Secrets should be rotatable without a code change or redeploy where
  possible (the app re-reads or is handed a fresh value), limiting how long a leaked
  credential stays useful.
- **This is the deployment-time counterpart to Phase 2's CI-time secrets** (GitHub
  Actions' `secrets.*`, Jenkins' credential store) — CI-time secrets authenticate the
  *pipeline* (e.g. to push an image); deploy-time secrets authenticate the *running
  application* (e.g. to reach its database). Different lifecycle, same non-negotiable
  principle: never hardcoded, always scoped, always masked/rotatable.

### Why it's useful

Getting this wrong is one of the most common real-world security incidents — a secret
committed to git history or baked into a public image layer is effectively permanently
compromised, since removing it later doesn't undo prior exposure.

### Summary

- Inject secrets at deploy/runtime from a dedicated secrets manager — never in the
  image or the repo.
- Scope access per-service to least privilege, and rotate regularly.
- CI-time secrets (pipeline auth) and deploy-time secrets (app runtime auth) are
  related but distinct concerns.

## 4.8 — Feature Flags: Decoupling Deploy from Release

A **feature flag** (feature toggle) is a runtime switch that controls whether a piece of
code path is active — it separates **deploying** code (getting it running in
production) from **releasing** it (actually exposing it to users).

- **Ship dark.** New code can be merged and deployed to production behind a flag that's
  off by default — it's present and running, but no user sees any behavioral change
  until the flag is flipped. This supports trunk-based development (Phase 1, §1.6):
  incomplete features can merge to `main` safely.
- **Turn on independently of a deploy.** Flipping a flag on for a cohort (internal
  users, 1% of traffic, a specific customer) doesn't require a new deployment at all —
  it's an instant, low-risk config change, and just as instantly reversible (an
  immediate kill-switch if something's wrong).
- **A complement to canary, not a replacement.** Canary controls *which instances*
  serve which *version of the binary*; feature flags control *which users* see which
  *behavior*, independent of which binary they're hitting. They're often combined: a
  canary validates the new binary is stable, while a flag independently controls
  business-level exposure of a specific feature within it.
- **Cost:** flags accumulate as technical debt if not cleaned up — stale flags and the
  dead code paths behind them should be removed once a feature is fully released and
  stable.

### Why it's useful

Feature flags give you a release lever that's completely decoupled from the deploy
pipeline's cadence — you can deploy on your normal schedule and decide exactly when
(and to whom) a feature actually goes live, independent of any pipeline run, with an
instant off-switch if it doesn't go well.

### Summary

- Deploy = code is running in production; release = users can actually see/use it.
- Flags let you ship dark and release later, to a chosen cohort, without a new deploy.
- Complements canary (binary-level control) rather than replacing it (feature-level
  control).
- Remove stale flags once a feature is fully released — they're debt if left behind.

## 4.9 — Pipeline Hygiene: Fast, Reliable, Fail-Fast

A deployment strategy is only as trustworthy as the pipeline that feeds it — a slow or
flaky pipeline undermines every safety property discussed above.

- **Fast, reliable tests are non-negotiable** (Phase 1, §1.7's test pyramid): a slow
  pipeline gets worked around (batching changes, skipping steps under deadline
  pressure) and a flaky one gets ignored (re-run until green), both of which quietly
  erode the exact safety net CI/CD exists to provide.
- **Fail fast, ordered by cost/likelihood.** Put cheap, fast, likely-to-catch-something
  checks (lint, unit tests) before slow, expensive ones (integration tests, image
  builds, deploys) — a broken change should be rejected in seconds, not after the
  pipeline has already spent ten minutes building an image for code that fails a unit
  test.
- **Parallelize and cache** wherever independent (Phase 2, §2.5–2.6): matrix builds and
  parallel jobs cut wall-clock time; dependency and layer caching cut redundant work.
- **Deterministic, reproducible builds.** Pin tool versions, cache dependencies
  consistently, and avoid non-deterministic build steps — you want the exact same input
  to always produce the exact same artifact, which is also a precondition for "build
  once, deploy many" actually meaning anything (Phase 1, §1.4).
- **Observability of the pipeline itself.** Clear logs, published test reports (the
  `junit`/`upload-artifact` patterns from Phases 2–3), and failure notifications so the
  team reacts to a broken pipeline quickly rather than discovering it stale hours later.

### Why it's useful

A pipeline nobody trusts is worse than no pipeline — it creates a false sense of safety
while teams quietly route around it. Hygiene work (speed, reliability, fail-fast
ordering) is what keeps the pipeline in the loop as an actual, respected gate rather
than ceremony.

### Summary

- Slow pipelines get bypassed; flaky pipelines get ignored — both defeat the point.
- Order stages cheapest/most-likely-to-fail first.
- Parallelize and cache aggressively; keep builds deterministic.
- Make pipeline failures loud and immediately visible to the team.

## 4.10 — Shifting Security Left in the Pipeline

Security checks belong **inside** the pipeline, as automated gates, not as a manual
audit that happens after code is already in production.

- **Dependency scanning (SCA — Software Composition Analysis)** — scans third-party
  dependencies for known CVEs; fails the build (or at least warns loudly) on
  vulnerable versions before they ship.
- **SAST (Static Application Security Testing)** — analyzes your own source code for
  common vulnerability patterns (injection, hardcoded secrets, unsafe deserialization)
  without running it.
- **Container/image scanning** (e.g. Trivy) — scans a built Docker image's OS packages
  and layers for known vulnerabilities before it's pushed to a registry or deployed.
- **Secret scanning** — checks commits/history for accidentally committed credentials
  (API keys, passwords) so they're caught (and rotated) immediately instead of sitting
  exposed in git history.
- **Supply-chain integrity** — signing artifacts and recording build provenance (what
  commit, what pipeline run, what inputs produced this exact artifact) so a consumer can
  verify an artifact hasn't been tampered with after the pipeline built it.
- **"Shift left"** is the general principle behind all of these: catch a problem as
  early as possible in the pipeline (ideally before merge) rather than after it reaches
  production, because the cost of fixing an issue grows the further downstream it's
  found.

### Why it's useful

Automated security gates turn "we hope someone reviews for security issues" into "the
pipeline mechanically checks for known classes of problems on every single change" —
consistent, fast, and impossible to accidentally skip under deadline pressure the way a
manual review step can be.

### Summary

- SCA (dependencies), SAST (your code), container scanning (the built image), and
  secret scanning each catch a different class of issue.
- "Shift left" = catch problems as early in the pipeline as possible, ideally pre-merge.
- Signing/provenance protects supply-chain integrity — proving what produced an
  artifact.

## Perspective

Building the artifact is table stakes; **releasing it safely** is the craft. Choose a
rollout strategy by risk tolerance — **rolling** for routine changes, **canary** for
risky ones, **blue-green** when instant cutover/rollback matters more than
infrastructure cost, **recreate** only when downtime is genuinely acceptable — and back
whichever you choose with fast/reliable tests, immutable build-once artifacts (Phase 1),
backward-compatible expand-then-contract migrations, properly scoped and rotated
secrets, feature flags to decouple deploy from release, and pipeline security scanning
shifted as far left as possible. Done well, deploys become frequent, boring non-events,
and recovery from a bad one is a redeploy — or a flag flip — away, not an emergency.
