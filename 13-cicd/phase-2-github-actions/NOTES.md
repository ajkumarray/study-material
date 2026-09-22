<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · concepts](../phase-1-concepts/NOTES.md) | [Phase 3 · jenkins ➡](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 2 — GitHub Actions: Notes

GitHub Actions is a **hosted CI/CD platform built into GitHub**: you commit YAML
workflow files to `.github/workflows/`, and GitHub runs them on its own managed runners
in response to events in the repo. There's no server to install, patch, or maintain —
you write YAML, GitHub does the rest. The two worked examples for this phase,
`examples/github-actions/ci.yml` and `examples/github-actions/cd.yml`, implement exactly
the CI and CD/Delivery concepts from Phase 1 for the Spring Boot `expense-api`.

## 2.1 — The model: workflows, jobs, steps, actions

GitHub Actions has a strict four-level hierarchy, and interview questions about "how
does Actions work" are almost always testing whether you know these levels precisely.

- **Workflow** — one YAML file under `.github/workflows/`, triggered by one or more
  **events** declared under `on:`. `ci.yml` and `cd.yml` are two separate workflows,
  each with its own trigger.
- **Job** — a unit of work inside a workflow. Jobs run **in parallel by default** and
  each gets its own fresh **runner** (VM). Use `needs:` to force one job to wait for
  another.
- **Step** — an ordered instruction inside a job, executed sequentially on the same
  runner (so steps share filesystem state within a job, but jobs do not share state with
  each other unless you explicitly pass it along — e.g. via artifacts or job outputs).
- **Action** — a reusable, packaged unit of automation, invoked with `uses:` (e.g.
  `actions/checkout@v4`). A step is *either* a shell command (`run:`) *or* an action
  (`uses:`), never both on the same step. The public Marketplace of pre-built actions
  (checkout, language setup, Docker build/push, cloud deploys, …) is the platform's
  biggest practical advantage — most common CI/CD tasks already have a maintained
  action, so you rarely write raw shell for them.

```yaml
name: CI
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  build-and-test:              # <- a job
    runs-on: ubuntu-latest     # <- runs on a fresh runner VM
    steps:
      - uses: actions/checkout@v4          # <- a step using an action
      - name: Build and test
        run: mvn -B verify                 # <- a step running a shell command
```

In this example: `on:` declares two triggers (push to `main`, PRs targeting `main`);
`build-and-test` is a single job that will get its own clean Ubuntu VM; its two steps
run in order on that VM — first the `checkout` action pulls the repo's code onto the
runner's disk, then `run: mvn -B verify` executes as a shell command against that
checked-out code.

### Why it's useful

This hierarchy is what makes a workflow both readable and composable: events decide
*when* anything runs at all, jobs decide *what can run in parallel*, steps decide
*ordering within one machine*, and actions let you reuse battle-tested automation
instead of hand-rolling shell scripts for common tasks like checking out code or
authenticating to a registry.

### Summary

- Workflow (YAML file) → jobs (parallel by default) → steps (sequential) → actions
  (reusable `uses:` units) or shell (`run:`).
- `needs:` sequences jobs that would otherwise run in parallel.
- Steps within a job share the runner's filesystem; jobs don't, by default.
- The Marketplace of existing actions is the platform's core productivity advantage.

## 2.2 — Triggers: the `on:` key

The `on:` key decides which repository events cause a workflow to run — getting this
right is what separates "runs on every irrelevant push" from a pipeline that actually
matches the team's release process.

- **`push`** — runs when commits are pushed. Scope with `branches:` (e.g. only `main`)
  or `tags:` (e.g. `tags: ["v*"]`, used by `cd.yml` so it fires only on version-tag
  pushes, not every commit) and optionally `paths:` to ignore irrelevant file changes
  (e.g. skip CI for docs-only changes).
- **`pull_request`** — runs when a PR is opened/updated, scoped to target `branches:`.
  This is what powers "required status checks" on a PR before merge.
- **`schedule`** — cron-based triggers (`- cron: "0 3 * * *"`) for nightly builds,
  scheduled dependency scans, etc.
- **`workflow_dispatch`** — a manual "Run workflow" button in the GitHub UI, optionally
  with typed input parameters — useful for on-demand deploys or ops tasks.
- **`workflow_call`** — makes the workflow itself **reusable**, callable from another
  workflow (`uses: org/repo/.github/workflows/build.yml@main`) — the mechanism behind
  reusable workflows (2.10).
- **Multiple triggers can combine** in one `on:` block; each trigger can have its own
  filters.

```yaml
# cd.yml's trigger: only on a semantic version tag push — never on ordinary commits.
on:
  push:
    tags: ["v*"]   # e.g. v1.2.0
```

In this example: pushing a normal commit to `main` does **not** trigger `cd.yml` at
all — only `git tag v1.2.0 && git push origin v1.2.0` does. This directly implements
Phase 1's "build once, deploy many": the CD workflow only fires at the moment a release
is explicitly cut.

### Why it's useful

Precise triggers keep pipelines meaningful and fast: CI should run on every change that
could break something (push/PR to main); CD should run only when a deliberate release
decision has been made (a tag), not on every commit. Sloppy triggers either waste
runner minutes on irrelevant runs or, worse, silently skip runs that should have
happened.

### Summary

- `push`/`pull_request` scoped by `branches`/`tags`/`paths` are the everyday CI
  triggers.
- `schedule` (cron) and `workflow_dispatch` (manual button) cover recurring/ad-hoc runs.
- `workflow_call` makes a workflow reusable from other workflows.
- Tag-only triggers (`tags: ["v*"]`) are the standard way to gate CD to explicit
  releases.

## 2.3 — Runners: hosted vs self-hosted

A **runner** is the machine that actually executes a job's steps.

- **GitHub-hosted runners** (`runs-on: ubuntu-latest`, `windows-latest`,
  `macos-latest`) are managed, ephemeral VMs GitHub provisions fresh for every job and
  destroys afterward. Zero maintenance, pre-installed common tooling, and a free-tier
  minute allowance — the default choice for most projects.
- **Self-hosted runners** are machines you register and maintain yourself (on-prem
  hardware, a cloud VM, GPUs, machines inside a private network). You own the
  patching, security, and capacity of these machines in exchange for capabilities
  GitHub-hosted runners can't offer (special hardware, network-restricted resources, or
  cost control at very high volume).
- **Ephemeral by default.** GitHub-hosted runners start clean every run — nothing
  persists between jobs unless you explicitly cache or upload it (2.4 caching,
  artifacts). This guarantees reproducibility but means every run pays "cold start"
  costs (downloading dependencies, etc.) unless caching is used.

| | GitHub-hosted | Self-hosted |
|---|---|---|
| Maintenance | None — fully managed | You patch/secure/scale the machines |
| Cost model | Included free-tier minutes, then billed per minute | Your own infra cost, no per-minute billing |
| Environment | Fresh VM every run, standard tooling preinstalled | Whatever you configure — custom hardware, private network access |
| Best for | Most projects, especially public/open-source | Special hardware (GPU), air-gapped/private networks, high-volume cost control |

### Why it's useful

Runner choice is a direct trade of convenience against control — the same axis that
distinguishes GitHub Actions from Jenkins as a whole platform (Phase 3). Most teams
default to hosted runners and only reach for self-hosted when there's a concrete
requirement hosted runners can't meet.

### Summary

- Hosted runners = zero-ops managed VMs, fresh every run, the default choice.
- Self-hosted runners = your own machines, needed for special hardware/network/cost
  reasons, at the cost of owning their upkeep.
- Runners are ephemeral — nothing survives between runs without caching/artifacts.

## 2.4 — Sequencing Jobs: `needs:` and Job Outputs

Jobs run in parallel by default; `needs:` introduces explicit dependencies between them,
which is how a multi-stage pipeline (build → gated deploy) is expressed across jobs
rather than within a single job's steps.

```yaml
# From cd.yml: deploy only runs after build-and-push finishes successfully.
jobs:
  build-and-push:
    runs-on: ubuntu-latest
    steps: [ ... ]

  deploy:
    needs: build-and-push        # <- gate: waits for, and requires success of, build-and-push
    runs-on: ubuntu-latest
    environment: production
    steps: [ ... ]
```

In this example: without `needs: build-and-push`, GitHub Actions would start both jobs
at the same time — `deploy` could try to deploy an image that hasn't finished building
yet. `needs:` makes `deploy` wait, and if `build-and-push` fails, `deploy` is skipped
entirely rather than running against a missing/broken artifact.

- **`needs:` can list multiple jobs** (`needs: [lint, test]`) — the dependent job waits
  for all of them.
- **Job outputs** let a job pass small values (not files) to a downstream job: define
  `outputs:` on the upstream job (sourced from a step's `outputs`), then reference
  `needs.<job>.outputs.<name>` downstream. This is how, e.g., a computed version string
  can flow from a "meta" job into a "deploy" job.
- **A failed upstream job skips downstream `needs:` jobs by default** — exactly the
  "gate" behavior Phase 1 describes; add `if: always()` on the downstream job only if
  you deliberately want it to run regardless (e.g. a cleanup/notify job).

### Why it's useful

`needs:` is what turns a bag of independent jobs into an actual pipeline with gates,
mirroring the stage-gates-stage model from Phase 1 at the job level — most importantly,
it's the mechanism that lets a `deploy` job sit behind both a successful build *and* (via
`environment:`, 2.9) a human approval.

### Summary

- Jobs run in parallel unless sequenced with `needs:`.
- `needs:` also gates: a failed/skipped upstream job skips the downstream job.
- Job outputs pass small values (not files) between jobs via `needs.<job>.outputs`.
- This is the job-level implementation of Phase 1's "stages gate the next stage."

## 2.5 — Matrix Builds

A **matrix** runs the *same* job definition multiple times, once per combination of
variables you declare — the standard way to test broad compatibility without
duplicating YAML.

```yaml
jobs:
  test:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        java: [17, 21]
        os: [ubuntu-latest, windows-latest]
      fail-fast: false        # don't cancel other combos if one fails
    runs-on: ${{ matrix.os }}
    steps:
      - uses: actions/setup-java@v4
        with:
          java-version: ${{ matrix.java }}
      - run: mvn -B verify
```

In this example: `matrix.java: [17, 21]` combined with `matrix.os` (two values)
produces **four parallel jobs** (17+ubuntu, 17+windows, 21+ubuntu, 21+windows), each a
full independent run of the same steps with different `matrix.java`/`matrix.os` values
substituted in. `fail-fast: false` means one combination failing doesn't cancel the
other three — useful when you want the full compatibility picture rather than stopping
at the first failure.

- **`include`/`exclude`** let you add one-off combinations or remove specific pairings
  from the generated matrix instead of the full cross-product.
- **`fail-fast: true` (the default)** cancels all in-progress matrix jobs the moment one
  fails — faster feedback, but you lose visibility into whether *other* combinations
  also failed.
- **`max-parallel`** caps how many matrix jobs run concurrently, useful to avoid
  overwhelming shared resources (e.g. a rate-limited external service every combo hits).

### Why it's useful

Matrix builds turn "does this work on every supported Java version / OS / Node
version" from an expensive manual checklist into a handful of extra lines of YAML,
running all combinations in parallel for roughly the cost of the slowest single
combination in wall-clock time.

### Summary

- `strategy.matrix` runs the same job once per combination of declared variables.
- `${{ matrix.<var> }}` substitutes the current combination's value into steps.
- `fail-fast` controls whether one failure cancels the rest of the matrix.
- `include`/`exclude` fine-tune the generated combination set.

## 2.6 — Caching Dependencies

Caching persists data (typically downloaded dependencies) between separate workflow
runs on otherwise-ephemeral runners — usually the single biggest lever for pipeline
speed.

```yaml
# From ci.yml — setup-java has a built-in cache flag for the most common case.
- name: Set up JDK 21
  uses: actions/setup-java@v4
  with:
    java-version: "21"
    distribution: "temurin"
    cache: maven        # caches ~/.m2 keyed by the pom.xml hash
```

In this example: `cache: maven` automatically saves `~/.m2` (Maven's local dependency
cache) at the end of the run, keyed by a hash of the `pom.xml`/lockfile, and restores it
at the start of the *next* run if the key matches — so dependencies already resolved in
a previous run don't need to be re-downloaded from Maven Central every single time.

- **`actions/cache`** is the general-purpose caching action for anything `setup-*`
  doesn't cover out of the box — you specify the `path` to cache and a `key` (usually
  derived from a lockfile hash) yourself.
- **Cache keys should include a hash of the dependency manifest** (`pom.xml`,
  `package-lock.json`) so a cache is only reused when dependencies haven't changed, and
  a new cache is created automatically when they have.
- **Docker layer caching** (`cache-from: type=gha` / `cache-to: type=gha,mode=max` in
  `cd.yml`) applies the same idea to image builds — unchanged layers (e.g. dependency
  installation) are reused instead of rebuilt from scratch on every push.

### Why it's useful

Without caching, every run re-downloads the entire dependency tree from scratch — often
the majority of total pipeline time for typical projects. Caching turns a 5-minute
"download the internet" step into a few seconds most of the time, directly improving
DORA's lead-time-for-changes metric (Phase 1).

### Summary

- `setup-*` actions often have a built-in `cache:` flag for the common case (Maven,
  npm, Gradle, …).
- `actions/cache` is the general mechanism — path + a manifest-hash key.
- Docker builds get their own layer cache (`type=gha`) for the same speed win.
- Caching is usually the single biggest lever for pipeline speed.

## 2.7 — Service Containers

A **service container** spins up a real dependency (a database, a queue) alongside a
job, so integration tests run against the genuine thing instead of a mock — and it's
torn down automatically when the job ends.

```yaml
# From ci.yml: a real Postgres for the Spring Boot integration tests.
services:
  postgres:
    image: postgres:16-alpine
    env:
      POSTGRES_DB: expenses
      POSTGRES_USER: app
      POSTGRES_PASSWORD: secret
    ports: ["5432:5432"]
    options: >-
      --health-cmd "pg_isready -U app -d expenses"
      --health-interval 5s --health-timeout 3s --health-retries 5
```

In this example: GitHub starts a `postgres:16-alpine` container before the job's steps
run, exposing port 5432 on the runner (so the app under test connects to
`localhost:5432` exactly as it would to a real database). `options:` defines a **health
check** (`pg_isready`) that GitHub polls — the job's steps don't start until Postgres
reports healthy, so the test step never races a database that isn't ready yet.

- **Services are scoped to the job**, run alongside it for its whole duration, and are
  automatically removed when the job finishes — no manual cleanup.
- **Health checks matter.** Without `--health-cmd`, steps could start running against a
  container that's still initializing, causing flaky "connection refused" failures —
  exactly the kind of pipeline flakiness Phase 1 warns erodes trust.
- **This is the integration-test tier of the test pyramid (Phase 1, 1.7)** in concrete
  form: real Postgres, real SQL, real wiring — not a mocked repository layer.

### Why it's useful

Service containers let CI validate real integration behavior (actual SQL against actual
Postgres) without anyone needing a database running locally or maintaining a shared test
database — the database's entire lifecycle is disposable and scoped to a single CI run.

### Summary

- `services:` declares containers (a DB, a queue) that run alongside the job.
- Health-check options gate the job's steps until the service is actually ready.
- Services are automatically torn down when the job ends — no manual cleanup.
- This is how real integration tests (not mocks) run inside CI.

## 2.8 — Secrets, `GITHUB_TOKEN`, and Least-Privilege Permissions

Pipelines routinely need credentials (registry logins, cloud API keys) — GitHub Actions
provides a secure mechanism for storing and referencing them without ever putting them
in the YAML or the repo.

- **Secrets** (`${{ secrets.NAME }}`) are encrypted values stored at the repo, org, or
  **environment** level (2.9). They're never printed in logs (GitHub automatically
  masks a secret's value if it appears in output) and are only exposed to workflow runs,
  never visible in the UI once saved.
- **`GITHUB_TOKEN`** is a special, automatically-generated secret scoped to the current
  workflow run, used to authenticate the job against the GitHub API and (as in `cd.yml`)
  the GitHub Container Registry — no manual token setup required for same-repo
  operations.
- **`permissions:`** controls what `GITHUB_TOKEN` is allowed to do for this workflow,
  following least privilege — grant only what's needed instead of the broad default.

```yaml
# From cd.yml
permissions:
  contents: read
  packages: write   # only enough to push to GHCR — nothing else
```

In this example: this job's `GITHUB_TOKEN` can read repository contents and push
packages to GHCR, and **nothing else** — it cannot, say, modify repository settings or
delete branches, even though a default/unscoped token might be able to. If the token
were ever leaked from logs, the blast radius is limited to exactly these two
permissions.

```yaml
# From cd.yml: authenticating to GHCR using GITHUB_TOKEN as the password.
- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

### Why it's useful

Treating credentials as first-class, scoped, masked secrets — instead of hardcoding
them or pasting them into scripts — is the baseline security practice for any real
pipeline, and least-privilege `permissions:` limits how much damage a compromised or
misconfigured job/token can do.

### Summary

- `${{ secrets.NAME }}` references encrypted, masked secret values — never hardcode
  credentials.
- `GITHUB_TOKEN` is auto-provisioned per run for repo/registry auth.
- `permissions:` scopes what that token can do — default to least privilege.
- Secrets can be scoped at repo, org, or environment level.

## 2.9 — Environments and Protection Rules

A GitHub **Environment** (`environment: production`) is a named deployment target with
its own protection rules and secrets — the concrete mechanism behind Phase 1's
"production needs an approval gate."

```yaml
# From cd.yml
deploy:
  needs: build-and-push
  runs-on: ubuntu-latest
  environment: production      # <- gate: can require manual approval
  steps: [ ... ]
```

- **Required reviewers** — one or more people must approve before a job targeting this
  environment runs; the job pauses ("Waiting for review") until approved. This is
  literally what turns a workflow from Continuous Deployment into Continuous Delivery
  (Phase 1, 1.2) without changing a single line of pipeline logic.
- **Wait timer** — delays the job by a configured duration before it's allowed to
  proceed, even with approval (a "soak" delay).
- **Environment-scoped secrets** — secrets visible only to jobs targeting that
  environment (e.g. production database credentials only available to the `production`
  environment's jobs, not to every job in the repo).
- **Deployment branch/tag rules** — restrict which branches/tags are even allowed to
  deploy to this environment (e.g. only `main` or version tags).

### Why it's useful

Environments let you express "this deploy needs a human, these secrets, and these
branch restrictions" declaratively, right next to the job definition, rather than
bolting approval logic on with custom scripts — and they make Delivery-vs-Deployment a
one-line configuration choice.

### Summary

- `environment: <name>` targets a named Environment with its own rules.
- Required reviewers = the human approval gate (Delivery, not Deployment).
- Environment-scoped secrets restrict credential visibility to jobs that need them.
- Branch/tag rules restrict which refs may even attempt a deploy to that environment.

## 2.10 — Reusable Workflows and Composite Actions

As pipelines multiply across repos (or within one repo), duplicated YAML becomes a
maintenance burden — GitHub Actions has two mechanisms to DRY it up.

- **Reusable workflows** (`workflow_call` trigger) — an entire workflow file becomes
  callable from another workflow: `uses: org/repo/.github/workflows/build.yml@main`
  with `with:` inputs and `secrets:` passed through. Good for sharing a whole pipeline
  shape (e.g. "the standard Java build-test-package flow") across many repos.
- **Composite actions** — a reusable *sequence of steps* packaged as a single custom
  action (an `action.yml` with `runs: using: composite`), invoked with a single
  `uses:` like any other action. Good for sharing a smaller, repeated chunk of steps
  (e.g. "log in to our registry and compute image tags") within or across workflows.

### Why it's useful

Both mechanisms turn copy-pasted YAML blocks into a single source of truth that's
versioned and updated once — the same DRY motivation as extracting a shared function in
application code, applied to pipeline definitions.

### Summary

- Reusable workflows (`workflow_call`) share an entire pipeline across workflows/repos.
- Composite actions share a smaller step sequence, invoked like any other action.
- Both avoid duplicating and separately maintaining near-identical YAML.

## 2.11 — Expressions, Contexts, and Conditionals

GitHub Actions has an expression syntax (`${{ ... }}`) that reads from **contexts** —
structured data about the run, the repo, the triggering event, and more.

- **Common contexts:** `github` (repo, actor, ref, event payload — e.g.
  `github.repository`, `github.actor`, `github.ref_name` used in both examples),
  `secrets`, `env`, `matrix`, `needs` (upstream job outputs/results), `steps` (this
  job's prior step outputs).
- **`if:` conditionals** gate whether a step or job runs at all, based on any
  expression — including the run's outcome so far.

```yaml
# From ci.yml: upload the test report even when the test step failed.
- name: Upload test results
  if: always()      # <- runs regardless of prior step success/failure
  uses: actions/upload-artifact@v4
  with:
    name: test-results
    path: 02-spring-boot/expense-api/target/surefire-reports/
```

In this example: by default, a step is skipped once any earlier step in the job has
failed. `if: always()` overrides that so the test report still gets uploaded on a
failing run — exactly when you most need to see *why* it failed. Other common
conditionals include `if: success()` (the default behavior, made explicit),
`if: failure()`, and `if: github.ref == 'refs/heads/main'` to restrict a step to a
specific branch.

### Why it's useful

Expressions and conditionals are what make a workflow dynamic rather than a fixed
script — deriving image tags from git metadata, restricting a deploy step to `main`, or
guaranteeing a diagnostic step runs even after failure are all expression-driven
decisions rather than separate hardcoded workflows.

### Summary

- `${{ }}` expressions read structured data from contexts (`github`, `secrets`, `env`,
  `matrix`, `needs`, `steps`).
- `if:` conditionals gate steps/jobs — default is "run only if prior steps succeeded."
- `if: always()` is the standard way to guarantee a diagnostic/cleanup step still runs
  after a failure.

## 2.12 — Uploading and Sharing Artifacts

`actions/upload-artifact` / `actions/download-artifact` persist files produced by a job
so they're visible on the run summary or usable by another job — the way data crosses
the "jobs don't share filesystem state" boundary from 2.1.

```yaml
# From ci.yml
- name: Upload test results
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: test-results
    path: 02-spring-boot/expense-api/target/surefire-reports/
```

In this example: the Surefire XML test reports produced by `mvn -B verify` are zipped up
and attached to the workflow run, downloadable from the GitHub UI — useful for a human
to inspect exactly which test(s) failed and why, without re-running the pipeline
locally.

- **Distinct from caching (2.6):** artifacts are the *output* of a run meant for
  inspection or handoff to another job; caches are *inputs* meant to speed up a future
  run and are opportunistically reused, not guaranteed to exist.
- **Cross-job data:** an upstream job can `upload-artifact`, and a downstream job (even
  in a different workflow, via `workflow_run`) can `download-artifact` to consume it —
  useful for, e.g., a build job producing a binary that a separate deploy job needs.

### Why it's useful

Artifacts turn ephemeral runner output (logs, test reports, compiled binaries) into
something durable and inspectable after the run finishes, and they're the standard way
to move real files (not just small string outputs) between jobs.

### Summary

- `upload-artifact`/`download-artifact` persist and share files from a run.
- Artifacts are for output/handoff; caches (2.6) are for speeding up future runs.
- `if: always()` combined with artifact upload is the standard pattern for always
  capturing diagnostics.

## Walking Through the Two Example Workflows

- **`ci.yml`** (CI, Phase 1 §1.1): triggers on every push/PR to `main` → checks out code
  → sets up JDK 21 with Maven dependency caching → starts a health-gated Postgres
  service container → runs `mvn -B verify` (compile + the full test suite from track 02)
  → uploads the test report unconditionally (`if: always()`) so failures are inspectable.
  This is fast feedback on every change, exactly Phase 1's CI definition.
- **`cd.yml`** (Delivery, Phase 1 §1.2/§1.4): triggers **only** on a version tag push →
  logs in to GHCR with least-privilege `permissions:` and the built-in `GITHUB_TOKEN` →
  derives semantic-version and SHA tags from git metadata → builds and pushes the Docker
  image **exactly once**, with layer caching → a `deploy` job, gated on `needs:` (the
  image must exist) *and* on the protected `production` Environment (a human approval
  gate), deploys that one immutable tag. This is "build once, deploy many" and
  Continuous Delivery made concrete.

## Perspective

GitHub Actions' strength is **zero infrastructure + a huge action ecosystem + tight repo
integration**: triggers on any repo event, parallel jobs, matrix testing, dependency and
layer caching, real service containers for integration tests, and Environment-gated
deploys — all expressed in versioned YAML sitting right next to the code it builds. For
projects already hosted on GitHub it's usually the path of least resistance. The
trade-off against Jenkins (Phase 3) is hosted convenience and tight integration versus
self-hosted control and flexibility — both implement the same underlying pipeline
concepts from Phase 1, just with different syntax and a different ops model.
