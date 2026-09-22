<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · github actions ➡](../phase-2-github-actions/NOTES.md)
<!-- /nav -->

# Phase 1 — CI/CD Concepts: Notes

CI/CD automates the journey from a git commit to running software, so releasing is
frequent, boring, and safe rather than rare and terrifying. It is the process backbone
that carries everything else in this repo — the Spring Boot API, its tests, its Docker
image, its Kubernetes deployment — to production. This phase builds the vocabulary and
mental model; Phases 2–4 are *how* to implement it with GitHub Actions, Jenkins, and
specific rollout strategies.

## 1.1 — Continuous Integration (CI)

**Continuous Integration** is the practice of merging every developer's code changes
into a shared branch frequently (multiple times a day), with each merge automatically
**built and tested** by a server, so integration problems are caught within minutes
instead of at a big, scary merge days or weeks later.

- **The problem it solves:** before CI, teams worked on long-lived feature branches and
  only found out they conflicted — or broke each other's code — when someone finally
  tried to merge everything together ("integration hell"). The longer branches diverge,
  the more painful and error-prone the eventual merge.
- **Small, frequent merges.** CI pushes teams toward small commits merged often, because
  a small change is cheap to build, test, and review, and a failure is easy to localize
  ("the last commit broke it").
- **The heart of CI is a fast, reliable automated test suite.** CI without meaningful
  tests just automates *compiling* the code — it tells you nothing about correctness.
  The test suite is what turns "it built" into "it works."
- **A CI server watches the repo** (GitHub Actions, Jenkins, GitLab CI, CircleCI, …) and
  on every push/PR: checks out the code, builds it, and runs the test suite, reporting
  pass/fail back to the developer (e.g., as a PR status check) within minutes.
- **Branch protection** typically requires CI to pass (and code review to be approved)
  before a PR can be merged — CI becomes an enforced quality gate, not just a
  convenience.

```yaml
# Conceptual sketch of what a CI server does on every push/PR — see Phase 2 for the
# real, runnable GitHub Actions version of this in examples/github-actions/ci.yml.
on: [push, pull_request]
jobs:
  build-and-test:
    steps:
      - checkout code
      - install toolchain (JDK, Node, ...)
      - run the build
      - run the test suite      # <- the actual quality gate
      - report pass/fail to the PR
```

In this example: every push or pull request triggers the same sequence — checkout,
build, test — and the result is visible directly on the PR before anyone merges it. If
the test step fails, the PR is blocked (when branch protection is configured), so a
broken change never reaches the shared branch in the first place.

### Why it's useful

CI turns "does my change break anything?" from a question answered by a painful manual
merge days later into a question answered automatically within minutes of every commit.
It is the foundation every other CI/CD practice builds on: you cannot safely automate
delivery or deployment of code you haven't automatically verified.

### Summary

- CI = automatically build + test every change, on every push/PR.
- It exists to catch integration problems within minutes, not at a late manual merge.
- Its value is entirely dependent on having a fast, trustworthy test suite.
- Branch protection rules turn CI results into an enforced merge gate.

## 1.2 — Continuous Delivery vs Continuous Deployment

Both are extensions of CI that automate what happens **after** the build/test step
passes — the difference is whether a human clicks a button before production.

- **Continuous Delivery:** every change that passes CI is automatically built into a
  deployable artifact and pushed through to a **release-ready** state (often deployed
  automatically to staging) — but the final push to **production requires a human
  decision** (a click, an approval). The team is *always* in a position to ship, on
  demand, with low risk, because every merged commit has already been proven
  releasable.
- **Continuous Deployment:** goes one step further and removes the human gate — every
  change that passes the full pipeline is **automatically deployed to production**, with
  no manual approval step. This requires much higher confidence in the pipeline: a
  strong test suite, good monitoring/alerting, and a fast, reliable rollback path,
  because there is no human standing between a bug and real users.
- **Both automate everything up to "ready to release."** The distinction is entirely
  about that last mile into production — delivery ends with a decision; deployment ends
  with an action.
- **Why some teams stop at Delivery:** regulatory/compliance requirements, a desire for
  a human sanity check on customer-facing or high-risk changes, or simply not yet having
  the test coverage and observability to trust full automation.

```yaml
# Delivery: last job requires manual approval (GitHub "Environment" with a
# required reviewer — see Phase 2). Deployment: the same job with no such gate.
deploy:
  needs: build-and-push
  environment: production   # <- configured with required reviewers = Delivery
  steps:
    - run: kubectl set image deployment/expense-api api=myimage:${{ github.ref_name }}
```

In this example: the YAML itself doesn't change between Delivery and Deployment — what
changes is whether the `production` **environment** GitHub Actions targets has a
required-reviewer protection rule configured. With the rule, the job pauses for a human
click (Delivery); without it, the job runs straight through the moment the image is
built (Deployment).

| | Continuous Delivery | Continuous Deployment |
|---|---|---|
| Up through "release-ready artifact" | Automated | Automated |
| Deploy to production | **Manual approval** required | **Fully automated**, no gate |
| Confidence bar | High | Very high (tests + monitoring + fast rollback) |
| Typical use | Regulated environments, high-risk changes, teams building trust | Mature teams shipping many small, low-risk changes per day |
| "Always shippable" | Yes, by definition | Yes — and it always *does* ship |

### Why it's useful

Knowing which one you're building changes what you need to invest in first: Delivery
needs a trustworthy pipeline up to a human gate; Deployment needs that plus the
monitoring and rollback maturity to be safe with **no** human in the loop. Interviewers
frequently probe whether you know this distinction precisely, since "CD" is
ambiguous in casual speech.

### Summary

- Delivery = automated up to a human "click to deploy" gate.
- Deployment = automated all the way to production, no gate.
- Deployment demands more confidence: tests, monitoring, fast rollback.
- The mechanism is usually the same pipeline; the difference is an approval gate.

## 1.3 — The CI/CD Pipeline: Stages and Gates

A **pipeline** is an ordered sequence of **stages**, each one gating the next — if a
stage fails, the pipeline stops there and nothing downstream runs. This is "fail fast":
put the cheapest, most likely-to-fail checks first so a bad change is rejected in
seconds, not after a slow, expensive stage has already run.

```
commit → [build] → [test] → [package] → [publish] → [deploy] → [verify]
              ↑ fail here and the rest never runs; the author gets feedback fast
```

- **Build** — compile/assemble the source into a runnable form (e.g. `mvn compile`,
  `npm run build`).
- **Test** — run the automated test suite: unit tests first (fast, isolated), then
  integration tests (slower, real dependencies like a database), and sometimes
  end-to-end tests. This is the primary quality gate.
- **Package** — produce a single deployable **artifact**: a JAR, a compiled binary, or
  (very commonly today) a **Docker image** (see track 12).
- **Publish** — push that artifact to a registry/repository (a Docker registry, an npm
  registry, a Maven repository like Nexus/Artifactory) so it can be pulled by any
  environment.
- **Deploy** — roll the published artifact out to an environment (dev, staging, or
  production).
- **Verify** — post-deploy smoke tests and health checks confirm the new version is
  actually working before you call the deploy "done" (and before, e.g., a canary ramp
  continues — Phase 4).
- **Gates** sit between stages: automated (tests must pass) or human (an approval before
  production). Gates are what make a pipeline *trustworthy* — without them, "pipeline"
  just means "a script that runs stuff."

### Why it's useful

Structuring a release as gated stages is what lets a team ship many times a day without
each release being a fire drill: every stage either proves the change is good enough to
proceed, or stops it immediately with a specific, actionable failure. Ordering matters —
a five-minute unit-test failure should never wait behind a twenty-minute deploy step.

### Summary

- A pipeline is an ordered list of stages, each gating the next.
- Standard shape: build → test → package → publish → deploy → verify.
- Order cheap/fast/likely-to-fail checks first — that's "fail fast."
- Gates (automated or human) are what make the pipeline trustworthy, not just fast.

## 1.4 — Build Once, Deploy Many: Immutable Artifacts

**"Build once, deploy many"** means you produce the deployable artifact **exactly one
time** per release, then promote that *same, unmodified, immutable* artifact through
every environment (dev → staging → production). You never rebuild the artifact for a
different environment — only its **configuration** changes per environment (env vars,
secrets, connection strings); the bits inside the artifact never do.

- **Why not just rebuild per environment?** A rebuild could differ from what was
  tested — a different dependency version resolved, a different compiler flag, a race
  in a non-deterministic build step. If staging tested artifact A but production runs
  freshly-rebuilt artifact B, you have not actually proven B works; you've only proven
  something *similar to* B works.
- **The artifact is the contract.** `expense-api:1.4.2`, the exact image that passed
  every test in staging, is the exact set of bytes that gets pushed to production. There
  is no "it worked in staging but broke in prod because the build was slightly
  different" class of bug.
- **This is precisely why containers matter here (track 12).** A Docker image packages
  the application *and* its runtime dependencies into one immutable, versioned artifact
  that behaves identically wherever it runs — the ideal vehicle for "build once, deploy
  many."
- **Configuration is injected, not baked in.** Database URLs, feature flags, and secrets
  are supplied at deploy/runtime (env vars, mounted config, a secrets manager — Phase
  4), so the same image can run correctly against dev, staging, and prod resources.
- **Rollback becomes trivial.** Because every release is an immutable, versioned
  artifact, "roll back" is just "redeploy the previous tag" — no need to recompile an
  old commit and hope the build environment hasn't drifted.

```yaml
# From examples/github-actions/cd.yml: build the image ONCE, tag it from git metadata,
# push it once. Every later `deploy` step just references this same tag by name —
# it never rebuilds.
- name: Build and push
  uses: docker/build-push-action@v6
  with:
    tags: ${{ steps.meta.outputs.tags }}   # e.g. ghcr.io/org/expense-api:1.4.2
    push: true
```

In this example: the tag (`1.4.2`, derived from the git version tag) uniquely and
permanently identifies this exact set of bytes. Deploying to staging and later to
production both reference `expense-api:1.4.2` — the same image — never a fresh build.

### Why it's useful

"Build once, deploy many" is the single practice that makes "works in staging" a
meaningful statement about production. Skipping it — rebuilding per environment — is a
classic root cause of "works on my machine"-style outages that show up *after* a change
already passed every test.

### Summary

- Build the artifact exactly once per release; promote that same artifact everywhere.
- Only configuration changes per environment, never the artifact's contents.
- Containers (Docker) are the natural implementation of this idea.
- Rollback = redeploy the previous tag, because every release is immutable and versioned.

## 1.5 — Environments and Promotion

An **environment** is a distinct deployment target — typically **dev** (fast iteration,
low stakes), **staging** (a production-like environment for final validation), and
**production** (real users, real data, real consequences). **Promotion** is moving the
one already-built artifact forward through these environments as it earns trust.

- **Progressive validation.** Each environment gives a different kind of confidence:
  dev catches obvious breakage fast; staging (ideally sized/configured like prod)
  catches integration and performance issues; production is the final, real-traffic
  proof.
- **Production needs protection.** Unlike dev/staging, production typically sits behind
  an **approval gate** and stricter access controls — not everyone who can push code
  should be able to unilaterally deploy to prod (see GitHub **Environments** in Phase
  2, and Jenkins' `input` step in Phase 3).
- **Environment parity matters.** The more staging differs from production (different
  data volume, different infra, different config), the less a "staging passed" result
  actually tells you about production behavior. Chasing parity is a real, ongoing cost
  worth investing in.
- **Config, not code, differs per environment.** Following 1.4, the same artifact is
  deployed everywhere; only environment variables, secrets, and infrastructure sizing
  change.

### Why it's useful

Environments let you buy confidence incrementally and cheaply — catch a bug in dev
before it costs you a staging cycle, catch it in staging before it costs you an
incident in production. Skipping straight to prod (or having a staging environment that
doesn't resemble prod) removes exactly the safety net environments exist to provide.

### Summary

- dev → staging → prod, each giving progressively stronger confidence.
- Promotion moves the *same* built artifact forward; it is never rebuilt per stage.
- Production is protected with approval gates and stricter access.
- Staging's value depends on how closely it resembles production.

## 1.6 — Branching Strategy: Trunk-Based Development vs GitFlow

How a team branches directly affects how well CI can do its job — CI works best when
branches are short-lived and merge often.

- **Trunk-based development:** everyone commits to (or merges frequently into) a single
  shared branch (`main`/`trunk`), using short-lived feature branches (hours to a couple
  of days) or even committing straight to trunk behind a **feature flag**. This keeps
  integration pain minimal because there is never much to integrate at once — it is the
  branching model CI/CD is designed around.
- **GitFlow (and similar long-lived-branch models):** separate long-lived `develop`,
  `release`, and `feature/*` branches, merged in a defined sequence. Gives more
  structure for versioned/release-train products, but branches can diverge for weeks,
  which reintroduces the "integration hell" CI was invented to prevent, and doesn't play
  as naturally with Continuous Deployment.
- **Feature flags decouple merging from releasing** (see Phase 4): trunk-based teams
  merge incomplete work behind a flag that's off by default, so `main` is always
  deployable even while a feature is still being built.
- **Interview framing:** "trunk-based development is what makes true Continuous
  Deployment practical" is a common talking point — you can't deploy every merge to
  prod automatically if merges only happen once a month from a long-lived branch.

### Why it's useful

The branching model is a prerequisite decision for how aggressive your CI/CD can be.
Teams doing genuine Continuous Deployment are almost always trunk-based; teams on
GitFlow-style branching usually land on Continuous Delivery at best, with a release
branch as the natural human gate.

### Summary

- Trunk-based = short-lived branches, frequent merges to a single trunk — CI/CD-native.
- GitFlow = long-lived branches with structured merge order — more ceremony, slower
  integration.
- Feature flags let trunk-based teams merge unfinished work safely.
- Continuous Deployment essentially requires trunk-based development to work well.

## 1.7 — The Test Pyramid Inside a Pipeline

Not all tests are equal cost, and a pipeline should exploit that: run the cheapest,
fastest, most numerous tests first, and the expensive, slow, few tests last.

```
        ▲  slow, expensive, few        e2e / UI tests
       ╱ ╲
      ╱   ╲                            integration tests (real DB, real HTTP)
     ╱     ╲
    ╱───────╲  fast, cheap, many       unit tests
```

- **Unit tests** — test one function/class in isolation, no network/DB/filesystem.
  Milliseconds each, run in the thousands; the base of the pyramid and the first gate
  in the pipeline.
- **Integration tests** — test real interactions between components (e.g. the Spring
  Boot app against a real Postgres via a service container — see `ci.yml`'s `services:`
  block, Phase 2). Slower, fewer, catch what unit tests structurally cannot (wiring,
  SQL, serialization).
- **End-to-end (e2e) tests** — drive the whole system as a user would (a browser, a full
  API call chain). Slowest and most brittle, so kept fewest — usually a small smoke
  suite rather than exhaustive coverage.
- **Pipeline ordering follows the pyramid:** fail on a unit test in 30 seconds, not
  after waiting 15 minutes for an e2e suite to spin up a whole environment. This is the
  concrete application of "fail fast" (1.3) to testing specifically.
- **Flaky tests are worse than no tests.** A test that fails intermittently for reasons
  unrelated to the code teaches the team to ignore red pipelines — which defeats CI's
  entire purpose. Eliminating flakiness is a standing pipeline-hygiene priority (Phase
  4).

### Why it's useful

The pyramid shape is a direct cost/confidence trade-off: it maximizes how much
confidence you get per second of pipeline time. A pipeline that runs e2e tests before
unit tests, or has more e2e tests than unit tests ("ice cream cone" anti-pattern), is
slow *and* less reliable.

### Summary

- Pyramid: many fast unit tests, fewer integration tests, fewest e2e tests.
- Order pipeline stages cheap-and-likely-to-fail first.
- Integration tests validate real wiring (DB, HTTP) that unit tests can't.
- Flaky tests erode trust in the pipeline faster than missing tests do.

## 1.8 — Versioning and Artifact Registries

Every artifact produced by "build once" (1.4) needs a durable name and a place to live
so any environment can retrieve exactly the right bytes.

- **Semantic versioning (SemVer)** — `MAJOR.MINOR.PATCH` (e.g. `1.4.2`): increment
  `MAJOR` for breaking changes, `MINOR` for backward-compatible features, `PATCH` for
  backward-compatible fixes. Common for tagged releases (`v1.4.2` → triggers `cd.yml`
  in this repo's example).
- **Git SHA / build-number tags** — `expense-api:sha-a1b2c3d` or `expense-api:42`
  (Jenkins' `${env.BUILD_NUMBER}` in `examples/Jenkinsfile`) — always unique, traceable
  straight back to the exact commit/build, useful even for changes that don't warrant a
  semantic release (e.g. every merge to `main`).
- **Artifact registries** store the published, versioned artifact: a **Docker/OCI
  registry** (Docker Hub, GHCR, ECR) for images, a **Maven repository** (Nexus,
  Artifactory, Maven Central) for JARs, an **npm registry** for JS packages. The
  registry is what makes "pull the exact artifact that passed CI" possible from any
  environment.
- **Immutable tags.** Once `1.4.2` is pushed, it should never be overwritten — if you
  need to fix something, you cut `1.4.3`. Mutable tags (like re-pushing `latest` or
  reusing a version number) defeat the entire "build once, deploy many" guarantee,
  because the same tag could now mean different bytes in different environments.

### Why it's useful

A clear versioning + registry strategy is what makes an artifact addressable,
traceable, and safely promotable: anyone (a teammate, an incident responder, an
automated pipeline) can look at a deployed version string and know exactly which commit
and which test run it corresponds to.

### Summary

- SemVer tags communicate the nature of a change; SHA/build-number tags are always
  unique and traceable.
- Registries (Docker/OCI, Maven, npm) are where published, versioned artifacts live.
- Tags must be treated as immutable — never overwrite a published version.

## 1.9 — DORA Metrics: Measuring CI/CD Maturity

The **DORA metrics** (from Google's DevOps Research and Assessment program) are the
industry-standard way to measure how well a team's CI/CD practice is actually working —
frequently referenced in interviews as the objective definition of "good" delivery
performance.

| Metric | What it measures | Elite performance (rough DORA benchmark) |
|---|---|---|
| **Deployment frequency** | How often code is deployed to production | Multiple times per day |
| **Lead time for changes** | Time from a commit landing to it running in production | Under one hour |
| **Change failure rate** | % of deployments that cause a production failure | 0–15% |
| **Mean time to recovery (MTTR)** | How fast service is restored after a failure/incident | Under one hour |

- **The four metrics balance speed against stability.** Deployment frequency and lead
  time measure *throughput*; change failure rate and MTTR measure *stability*. A
  healthy CI/CD practice improves both together — elite teams are not "moving fast and
  breaking things," they're moving fast *because* they can recover quickly and rarely
  break things.
- **Everything in this track feeds these metrics directly:** build-once + immutable
  artifacts and fast rollback (1.4) improve MTTR; automated gates and a strong test
  pyramid (1.7) reduce change failure rate; trunk-based development (1.6) and
  well-automated pipelines improve deployment frequency and lead time.

### Why it's useful

DORA metrics give teams (and interviewers) a concrete, measurable answer to "is our
CI/CD actually good?" instead of a vague feeling. If you're asked to justify a CI/CD
investment, framing it in terms of which DORA metric it improves is a strong,
evidence-based answer.

### Summary

- DORA: deployment frequency, lead time for changes, change failure rate, MTTR.
- Two throughput metrics, two stability metrics — elite teams are strong on both.
- Every practice in this track (immutable artifacts, gates, trunk-based dev, fast
  rollback) maps to improving one or more of these.

## Perspective

CI/CD's value is **confidence and speed**: automated build+test on every change (CI)
plus an automated, repeatable path to release (Delivery/Deployment) means you ship
small changes often and recover fast when something's wrong. The non-negotiables
running through every section above are a **trustworthy test suite** (or the automation
just ships bugs faster), a **single immutable artifact** promoted through environments,
and short-lived branches that keep integration cheap. Phases 2–4 are the concrete
implementation of all of this: GitHub Actions and Jenkins as pipeline engines, and
deployment strategies as the "how" of getting a new artifact live without downtime or
risk.
