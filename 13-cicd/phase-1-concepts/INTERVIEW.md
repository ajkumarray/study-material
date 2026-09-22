<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · github actions ➡](../phase-2-github-actions/NOTES.md)
<!-- /nav -->

# Phase 1 — CI/CD Concepts: Interview Q&A

⭐ = asked constantly.

**Q: What is CI/CD, and why do the two halves exist separately?** ⭐⭐
Continuous Integration is the practice of merging every change into a shared branch
frequently, with an automated server building and testing it on every push/PR so
integration problems surface within minutes rather than at a big merge weeks later. CD —
Continuous Delivery or Continuous Deployment — picks up after CI passes and automates
getting that change into production: Delivery prepares a release-ready artifact and
waits for a human to click "deploy"; Deployment removes that click entirely. They're
treated as separate concepts because they solve separate problems: CI is about catching
bugs early through automated verification; CD is about making the release process itself
fast, repeatable, and low-risk. A team can have excellent CI and still deploy manually
once a quarter — CI alone doesn't imply CD.

*Follow-up: what's the one prerequisite that makes CI actually valuable rather than just
faster busywork?* A fast, reliable, meaningfully-covered test suite. CI that only
compiles the code, or runs a thin/flaky test suite, just automates shipping bugs faster
and erodes the team's trust in red/green pipeline results.

**Q: Continuous Delivery vs Continuous Deployment — what's the precise difference?** ⭐⭐
Both automate the pipeline through to a release-ready, immutable artifact. Continuous
**Delivery** keeps a human "click to deploy" gate before production — the team is always
*able* to ship on demand, but a person decides when. Continuous **Deployment** removes
that gate entirely: every change that passes the full pipeline goes straight to
production with no approval step. Mechanically the pipeline can be identical; the
difference is typically whether the production deploy job sits behind a protected
environment with required reviewers (GitHub Actions) or an `input` step (Jenkins).
Deployment demands a much higher confidence bar — strong tests, real monitoring, and a
fast rollback path — because there's no human between a bug and real users.

*Follow-up: why would a mature team deliberately choose Delivery over Deployment?*
Regulatory/compliance requirements, high-risk or customer-facing changes where a human
sanity check adds real value, or simply not yet having the test coverage and
observability maturity to trust full automation. It's a deliberate trade-off, not always
a maturity gap.

**Q: Walk through the typical stages of a CI/CD pipeline and explain why the order
matters.** ⭐⭐
Build → test (unit → integration → sometimes e2e) → package (produce an artifact, often
a Docker image) → publish (push to a registry) → deploy (roll out to an environment) →
verify (post-deploy smoke tests/health checks). Order matters because each stage gates
the next — a failure at any stage stops the pipeline immediately, so you want the
cheapest, fastest, most-likely-to-fail checks first ("fail fast"). Running a 30-second
unit test suite before a 20-minute integration or deploy stage means a broken change is
rejected in seconds, not after burning most of the pipeline's runtime on stages that
were always going to fail anyway once the code was wrong.

```yaml
on: [push, pull_request]
jobs:
  build-and-test:
    steps:
      - checkout code
      - build
      - run unit tests      # fails fast, cheap
      - run integration tests
      - package + publish artifact
```

**Q: What does "build once, deploy many" mean, and what specifically goes wrong if you
don't follow it?** ⭐⭐
Produce the deployable artifact exactly one time per release, then promote that exact,
unmodified, immutable artifact through every environment (dev → staging → prod),
changing only its configuration (env vars, secrets) per environment — never its
contents. If you rebuild per environment instead, you break the guarantee that "what
passed staging is what's running in prod": a different dependency resolution, a
non-deterministic build step, or a stray environment difference in the build host can
produce subtly different bytes than what was actually tested. That's a classic root
cause of "worked in staging, broke in prod" bugs that show up *after* every test already
passed. Docker images are the natural implementation of this — package the app and its
runtime dependencies into one versioned, immutable artifact that behaves identically
wherever it's deployed.

*Follow-up: how does this make rollback trivial?* Because every release is an
immutable, versioned artifact (an image tag, a JAR version), rolling back a bad release
is just "redeploy the previous tag" — no recompiling an old commit and hoping the build
environment hasn't drifted since. (Database/schema changes still need their own
backward-compatibility discipline — covered in Phase 4 — but the application artifact
rollback itself is deterministic.)

**Q: How do environments and promotion work together?** ⭐
Changes flow through distinct environments — typically dev, staging, production — each
giving progressively stronger confidence: dev catches obvious breakage fast, staging
(ideally sized and configured like prod) catches integration/performance issues,
production is the real-traffic proof. Promotion means moving the *same* already-built
artifact forward through these environments rather than rebuilding at each stage.
Production is usually protected with an approval gate (a GitHub Environment with
required reviewers, or Jenkins' `input` step) and stricter access control than dev or
staging.

*Follow-up: why does staging's value depend on environment parity?* The more staging
differs from production — different data volume, different infra sizing, different
configuration — the less a "staging passed" result actually predicts about production
behavior. Teams invest ongoing effort in closing that gap precisely because a
non-representative staging environment gives false confidence.

**Q: What's the difference between trunk-based development and GitFlow, and how does
that choice affect CI/CD?** ⭐
Trunk-based development keeps everyone merging frequently into a single shared branch
via short-lived feature branches (hours to a couple of days), sometimes committing
directly to trunk behind a feature flag — integration pain stays minimal because there's
never much unmerged work at once. GitFlow uses long-lived `develop`/`release`/`feature/*`
branches merged in a defined sequence, which gives more release-train structure but lets
branches diverge for weeks, reintroducing the integration pain CI exists to prevent.
Genuine Continuous Deployment essentially requires trunk-based development — you can't
auto-deploy every merge to prod if merges only happen once a month from a long-lived
branch; teams on GitFlow-style branching typically land on Continuous Delivery at best.

**Q: Explain the test pyramid and how it shapes pipeline design.** ⭐
Many fast, cheap unit tests at the base (isolated, no network/DB, milliseconds each),
fewer slower integration tests in the middle (real dependencies — e.g. a real Postgres
via a service container), and the fewest, slowest end-to-end tests at the top. A
pipeline should run stages in that same cheap-to-expensive order so failures are caught
as early and cheaply as possible — the "fail fast" principle applied specifically to
testing. Inverting the shape (many slow e2e tests, few unit tests — the "ice cream cone"
anti-pattern) makes a pipeline both slower and less reliable.

*Follow-up: why are flaky tests described as worse than no tests at all?* A test that
fails intermittently for reasons unrelated to the actual code change teaches the team to
ignore red pipeline results — re-running until it's green. That habit defeats CI's
entire purpose: the pipeline stops being trustworthy feedback and becomes noise.

**Q: How should artifacts be versioned, and why must version tags be immutable?**
Semantic versioning (`MAJOR.MINOR.PATCH`, e.g. `1.4.2`) communicates the nature of a
release — breaking, feature, or fix — and is typical for tagged releases. Git SHA or
build-number tags (e.g. `expense-api:sha-a1b2c3d`, or Jenkins' `$BUILD_NUMBER`) are
always unique and trace straight back to the exact commit/build, useful for every merge
even when it doesn't warrant a semantic release. Either way, once a tag is published it
must never be overwritten — if `1.4.2` could later point to different bytes, "build
once, deploy many" is broken, because the same tag could mean different things in
different environments. Fix forward with a new tag (`1.4.3`), never by re-pushing an old
one.

**Q: What are the DORA metrics, and how do they relate to everything else in this
phase?** ⭐
Deployment frequency and lead time for changes measure throughput (how often you ship,
how fast a commit reaches production); change failure rate and mean time to recovery
(MTTR) measure stability (how often a deploy causes an incident, how fast you recover).
Elite teams are strong on both pairs simultaneously — they aren't moving fast by
tolerating breakage, they're moving fast because they can detect and recover from
breakage quickly. Concretely: build-once + immutable artifacts + fast rollback improves
MTTR; a strong, well-ordered test pyramid and automated gates reduce change failure
rate; trunk-based development and well-automated pipelines improve deployment frequency
and lead time. If asked to justify a CI/CD investment, framing it against which DORA
metric it moves is a strong, evidence-based answer.

**Q: Why automate deployment instead of doing it manually?** ⭐
Automation is repeatable, fast, and auditable, and it eliminates the forgotten-step and
fat-finger mistakes that are a common cause of deploy-time outages. It also makes small,
frequent releases practical — and small, frequent releases are far less risky than
large, infrequent ones, because each one changes less and is easier to reason about and
roll back.

**Q: How does CI/CD enable easy rollback, and where does that guarantee break down?**
*nuance*
Because each release is an immutable, versioned artifact (1.4), rolling back is just
redeploying the previous known-good version — no reverse-compiling or rebuilding
anything. It breaks down at the data layer: if a deploy included a destructive or
non-backward-compatible schema migration, redeploying the old application code doesn't
undo the schema change, and the old code may not even run against the new schema. That's
why safe rollback strategy pairs immutable-artifact rollback with backward-compatible,
expand-then-contract database migrations (covered in depth in Phase 4).
