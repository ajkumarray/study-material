<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · jenkins](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 4 — Deployment Strategies & Best Practices: Interview Q&A

⭐ = asked constantly.

**Q: Explain recreate, rolling, blue-green, and canary deployments, and when you'd pick
each.** ⭐⭐
Recreate stops every old instance before starting any new one — simplest, but causes
real downtime; only acceptable when a gap in service is genuinely tolerable (internal
tools, batch systems). Rolling update replaces instances incrementally, a few at a
time, health-gated, so the service stays up throughout — it's the Kubernetes default
and the sensible choice for routine, everyday changes, at the cost of requiring the old
and new versions to be compatible during the brief overlap. Blue-green runs two
complete environments and switches all traffic at once at the router/load balancer —
instant cutover and instant rollback, at roughly double the infrastructure cost, best
when a fast, clean rollback matters more than infra cost. Canary sends a small
percentage of traffic to the new version first, watches real metrics, then ramps up (or
rolls back) — the safest option for genuinely risky changes because it caps blast
radius to a small, observable slice of traffic rather than eliminating risk outright.

*Follow-up: which of these has the longest version-overlap window, and why does that
matter?* Canary, by design — it can sit at a partial traffic percentage for an extended
observation period. That means the backward-compatibility requirements that apply
during any overlap (old and new code both correctly handling the current schema/API)
apply for longer with canary than with a rolling update's comparatively brief transient
overlap.

**Q: How do you achieve zero-downtime deployments in practice?** ⭐⭐
Use rolling, blue-green, or canary so old and new versions (or two full environments)
never both go fully down at once, and gate traffic to a new instance on it actually
being ready — a readiness/health check, not just "the process started." Load
balancers/orchestrators should drain connections from old instances gracefully (finish
in-flight requests) rather than killing them abruptly. And critically, any change that
touches the database must be backward-compatible during the overlap window, or the
"zero downtime" claim breaks the moment old code hits a schema it doesn't understand
(or vice versa).

**Q: Why must database migrations be backward-compatible during a rollout, and what's
the standard technique for that?** ⭐⭐
During a rolling or canary rollout, old and new application versions run simultaneously
against the *same* database schema — a migration that isn't compatible with both would
crash whichever version doesn't expect it. The standard technique is
**expand-then-contract**: first add new columns/tables additively without touching
anything old (the "expand" migration — both old and new code still work, old code
simply ignores the new column); then deploy the application code that actually uses the
new schema element; only once every instance is confirmed running the new code do you
remove the old column/table in a separate, later migration (the "contract" step).
Combining "remove the old thing" with "start requiring the new thing" in a single
release is exactly the pattern that breaks under any strategy with version overlap.

```sql
-- Release N (expand): additive only, both app versions still work
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT false;
-- Release N+1: app code starts reading/writing email_verified
-- Release N+2 (contract), only after every instance is on N+1:
ALTER TABLE users DROP COLUMN legacy_verified_flag;
```

**Q: How do you roll back a bad release, and where does that get harder than "just
redeploy the old version"?** ⭐
Application rollback is easy given immutable, versioned artifacts (Phase 1) —
redeploy the previous image tag; it's instant with blue-green (flip the router back),
instant with canary (ramp to 0%), and a reverse rolling update otherwise. It gets
harder at the database: a schema migration that ran as part of the bad release doesn't
roll back with the application code, so if that migration wasn't backward-compatible,
the *old* code you just redeployed may now be broken against the *new* schema —
turning a rollback attempt into a second incident. This is exactly why
expand-then-contract migrations matter: they keep rollback safe, not just the forward
rollout.

**Q: How should secrets be handled at deploy time, and how is that different from
CI-time secrets?** ⭐⭐
Inject secrets (DB passwords, API keys, TLS certs) at deploy/runtime from a dedicated
secrets manager (Vault, a cloud secret store, Kubernetes Secrets) — never bake them
into the Docker image or commit them to the repo. Scope each service's access to only
the specific secrets it needs (least privilege), and rotate them periodically so a
leaked credential has a limited useful lifetime. This is related to but distinct from
CI-time secrets (GitHub Actions' `secrets.*`, Jenkins' credential store, Phase 2/3):
CI-time secrets authenticate the *pipeline itself* (e.g., pushing an image to a
registry); deploy-time secrets authenticate the *running application* (e.g., reaching
its database). Same non-negotiable principle — never hardcoded, always scoped, always
rotatable — different point in the lifecycle.

**Q: What's the difference between deploying and releasing, and how do feature flags
fit in?** *nuance* ⭐
Deploying puts code in production, running; releasing exposes it to users. They're
often conflated but don't have to be the same event. Feature flags decouple them: new
code can merge and deploy to production "dark," behind a flag that's off by default, so
it's present and running but invisible to users. Flipping the flag on for a cohort
(internal users, 1%, a specific customer) is then an instant, low-risk config change —
just as instantly reversible as a kill switch — completely independent of the deploy
pipeline's cadence. This also underpins trunk-based development (Phase 1): incomplete
features can merge safely because they're inert until flagged on.

*Follow-up: how do feature flags relate to canary deployment — are they redundant?* No,
they're complementary at different layers. Canary controls which *instances* run which
*binary version*; a feature flag controls which *users* see which *behavior*,
independent of which binary they're hitting. A team might canary a new binary to
validate stability while separately using a flag to control business-level exposure of
a specific feature inside that binary.

**Q: How do you keep a CI/CD pipeline fast and trustworthy over time?** ⭐
Fast, reliable, parallelized tests with dependency and layer caching; cheap-and-
likely-to-fail checks first (lint/unit before integration/build/deploy) so failures are
caught in seconds, not after the pipeline has already spent most of its time on stages
that were always going to fail once the code was wrong; pinned tool versions and
deterministic builds so the same input reliably produces the same artifact; and
eliminating flaky tests aggressively, because a flaky pipeline is arguably worse than no
pipeline — it trains the team to re-run until green rather than trust red/green as a
real signal.

**Q: How does security fit into a CI/CD pipeline?** ⭐
"Shift left": run automated security checks as pipeline gates rather than as a manual
post-hoc audit. Dependency scanning (SCA) catches known CVEs in third-party libraries;
SAST analyzes your own source for common vulnerability patterns without running it;
container/image scanning (e.g. Trivy) checks a built Docker image's OS packages and
layers for known vulnerabilities before it's pushed or deployed; secret scanning
catches credentials accidentally committed to history. Signing artifacts and recording
build provenance adds supply-chain integrity — proof of exactly what pipeline run and
what inputs produced a given artifact. The unifying idea is catching problems as early
as possible (ideally pre-merge), since the cost of fixing an issue grows the further
downstream it's discovered.

**Q: Recreate causes downtime and canary is complex — why would you ever choose
recreate over canary?** *nuance*
Because they're solving different problems at different cost points. Canary's
complexity (traffic splitting, per-slice metrics, automated promotion/rollback logic,
strong observability) is only worth paying for changes where the risk of a bad release
genuinely warrants a controlled, observable rollout. For a service that can tolerate a
maintenance window — an internal admin tool, a batch job, low-traffic dev/staging — that
complexity buys nothing over simply stopping and starting, and recreate's operational
simplicity is a legitimate win. Strategy choice should track the actual risk and
availability requirements of the specific service, not default to "always use the most
sophisticated option."

**Q: Explain expand-then-contract with a concrete example of what goes wrong if you skip
it.** *nuance*
Suppose a release both renames a column and deploys code that only uses the new name,
in one migration/deploy. During a rolling update, some instances are still running the
old code — which expects the old column name — while the new instances (and the
migration) have already removed it. The old instances now fail on every query touching
that table, mid-rollout, with live traffic — an outage caused by the *deployment
strategy itself*, not a logic bug. Expand-then-contract avoids this by never removing
something old in the same step that starts requiring something new: add the new column
first (old code ignores it, keeps working), deploy code that uses it (old and new code
both function against the now-expanded schema), and only remove the old column once
every instance is confirmed off the old code path.
