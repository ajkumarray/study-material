<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · concepts](../phase-1-concepts/NOTES.md) | [Phase 3 · jenkins ➡](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 2 — GitHub Actions: Interview Q&A

⭐ = asked constantly.

**Q: Explain the workflow → job → step → action hierarchy in GitHub Actions.** ⭐⭐
A workflow is a single YAML file under `.github/workflows/`, triggered by one or more
events declared under `on:`. A workflow contains one or more jobs, which run **in
parallel by default**, each on its own fresh runner VM — you sequence them explicitly
with `needs:` when one depends on another. Each job contains an ordered list of steps
that run sequentially on the same runner and therefore share filesystem state within
that job (but jobs don't share state with each other by default). A step is either a
shell command (`run:`) or an invocation of a reusable **action** (`uses:`), like
`actions/checkout@v4` — never both on one step. The public Marketplace of pre-built
actions for common tasks (checkout, language setup, Docker builds, cloud deploys) is
the platform's biggest practical advantage over writing raw shell for everything.

*Follow-up: if two jobs in the same workflow both need the compiled output of an
earlier job, how do you get it to them?* Either `needs:` plus job `outputs:` for small
string values, or `actions/upload-artifact` in the upstream job paired with
`actions/download-artifact` in the downstream job for actual files — jobs don't share a
filesystem, so nothing crosses that boundary implicitly.

**Q: What triggers are available, and how would you make CI run on every PR but CD run
only on a release?** ⭐⭐
`on:` supports `push` and `pull_request` (each scoped with `branches`/`tags`/`paths`),
`schedule` (cron), `workflow_dispatch` (a manual button, optionally with typed inputs),
and `workflow_call` (makes the workflow itself reusable from another workflow). To split
CI from CD by intent: CI triggers on `push`/`pull_request` to `main` so every change
gets verified, while CD triggers only on `push: tags: ["v*"]` — it never fires on an
ordinary commit, only when someone deliberately cuts a version tag. That's exactly how
this repo's `ci.yml` and `cd.yml` are split, and it directly implements "build once,
deploy many": the artifact only gets built and shipped at an explicit release moment,
not on every commit.

**Q: What's the difference between GitHub-hosted and self-hosted runners, and when
would you choose self-hosted?** ⭐
GitHub-hosted runners (`runs-on: ubuntu-latest`, etc.) are managed, ephemeral VMs GitHub
provisions fresh for every job and tears down afterward — zero maintenance, standard
tooling preinstalled, billed via included/paid minutes. Self-hosted runners are machines
you register and maintain yourself. You'd choose self-hosted for requirements hosted
runners can't meet: special hardware (GPUs), access to a private network/VPC that public
runners can't reach, or cost control at very high build volume — at the cost of owning
patching, security, and capacity for those machines yourself.

*Follow-up: why are hosted runners described as "ephemeral," and what does that imply
for pipeline design?* They start from a clean image every run and are destroyed after —
nothing persists between runs by default. It implies you must explicitly cache
dependencies (`actions/cache` or a `setup-*` action's built-in `cache:`) or upload
artifacts for anything you want to reuse or inspect later; you can't rely on state left
behind by a previous run.

**Q: How do matrix builds work, and what does `fail-fast` control?** ⭐
`strategy.matrix` runs the same job once per combination of declared variables — e.g.
`matrix: { java: [17, 21], os: [ubuntu-latest, windows-latest] }` produces four parallel
jobs, one per combination, with `${{ matrix.java }}`/`${{ matrix.os }}` substituted into
that job's steps. `fail-fast` (default `true`) cancels every other in-progress matrix
job the instant one combination fails, giving fast feedback but hiding whether other
combinations also failed; setting `fail-fast: false` lets every combination run to
completion so you get the full compatibility picture. `include`/`exclude` let you add
one-off combinations or remove specific pairings instead of running the full
cross-product.

**Q: How do you speed up a slow GitHub Actions pipeline?** ⭐⭐
Caching is usually the biggest win: `setup-*` actions like `actions/setup-java` have a
built-in `cache:` flag (`cache: maven` caches `~/.m2` keyed by a hash of `pom.xml`) so
dependencies aren't re-downloaded on every run, and `actions/cache` covers anything not
built in. Docker builds get their own layer cache (`cache-from`/`cache-to: type=gha`) so
unchanged layers are reused instead of rebuilt. Beyond caching: parallelize independent
work across jobs (which run concurrently by default), use a matrix instead of
sequential repeated runs, and keep the critical path — the stage a PR is actually
waiting on — as lean as possible by moving slow/non-blocking work (e.g. a nightly
security scan) off the PR-triggered path entirely.

**Q: How do you run integration tests that need a real database in CI, and why does the
health check matter?** ⭐
Declare a service container under `services:` — e.g. `postgres: image: postgres:16-alpine`
with `ports: ["5432:5432"]` — and GitHub starts that container alongside the job,
exposing it on `localhost` for the job's steps, then tears it down automatically when
the job ends. Without a health check (`options: --health-cmd "pg_isready ..."`), the
job's steps could start running before Postgres has actually finished initializing,
producing flaky "connection refused" failures that have nothing to do with the code
being tested — exactly the kind of pipeline flakiness that erodes trust in results. The
health check makes GitHub poll the container and hold the job's steps until it reports
healthy.

**Q: How does GitHub Actions handle secrets, and what does `GITHUB_TOKEN` do?** ⭐⭐
Secrets (`${{ secrets.NAME }}`) are encrypted values stored at the repo, org, or
Environment level; GitHub automatically masks their value if it ever appears in log
output, and they're never visible again in the UI once saved — never hardcode
credentials in the YAML. `GITHUB_TOKEN` is a special secret GitHub auto-generates and
scopes to the current workflow run, used to authenticate against the GitHub API and
GitHub-owned services like the Container Registry (GHCR) without any manual setup.
Alongside it, `permissions:` should scope exactly what that token can do —
`contents: read, packages: write` and nothing more if that's all the job needs — so a
leaked or misused token has a small blast radius.

```yaml
permissions:
  contents: read
  packages: write
```

*Follow-up: are environment-level secrets different from repo secrets, and why would
you use them?* Yes — a secret scoped to a specific Environment (e.g. `production`) is
only visible to jobs that target that Environment, unlike a repo-level secret visible
to every workflow. This is how, e.g., production database credentials stay inaccessible
to a PR-triggered CI job that has no business seeing them.

**Q: How do you gate a production deploy behind manual approval, and what mechanism
actually implements that?** ⭐
Use a GitHub **Environment** (`environment: production` on the job) configured with
**required reviewers**. The job pauses ("Waiting for review") the moment it reaches that
step, and only proceeds once an authorized reviewer approves it in the GitHub UI.
Environments can also add a wait timer (a forced soak delay even after approval),
environment-scoped secrets, and branch/tag restrictions on what's even allowed to
deploy to them. Mechanically, this is the entire difference between Continuous Delivery
and Continuous Deployment (Phase 1) — the pipeline YAML doesn't need to change, only
whether the target Environment has that reviewer requirement configured.

**Q: What are reusable workflows and composite actions, and when would you use each?**
Reusable workflows (`workflow_call` trigger) let an entire workflow file be invoked from
another workflow via `uses: org/repo/.github/workflows/build.yml@main`, with inputs and
secrets passed through — good for sharing a whole pipeline shape (e.g. "our standard
Java build-test-package flow") across many repos. Composite actions package a reusable
*sequence of steps* as a single custom action, invoked like any built-in action — good
for a smaller, repeated chunk (e.g. "log in to our registry and compute image tags")
used within or across workflows. Both exist to avoid copy-pasting and separately
maintaining near-identical YAML in multiple places.

**Q: What does `if: always()` do, and why would you put it on an artifact-upload
step?** ⭐
By default, a step is skipped once any earlier step in the same job has failed —
`always()` overrides that so the step runs unconditionally regardless of prior outcome.
Putting it on `actions/upload-artifact` (as `ci.yml` does for the Surefire test report)
means the report is still uploaded and inspectable even when the test step itself
failed — exactly the moment you most need to see which test failed and why, instead of
losing that diagnostic because the job aborted early.

```yaml
- name: Upload test results
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: test-results
    path: 02-spring-boot/expense-api/target/surefire-reports/
```

**Q: What's the difference between an artifact (`upload-artifact`) and a cache
(`actions/cache`)?** *nuance*
An artifact is the durable **output** of a run — a test report, a built binary — meant
for a human to inspect afterward or for another job to consume; it's guaranteed to be
there if the upload step ran. A cache is an **input optimization** — dependencies or
build outputs opportunistically saved to speed up a *future* run; it's a best-effort
hit/miss (a cache can be evicted or simply not match) and you should never depend on a
cache existing for correctness, only for speed.

**Q: GitHub-hosted convenience vs Jenkins self-hosted control — how would you frame the
trade-off in an interview?** *nuance*
GitHub Actions gives zero infrastructure to maintain, tight integration with repo
events (PR checks, branch protection), a large Marketplace of pre-built actions, and is
usually the path of least resistance for projects already hosted on GitHub. Jenkins
gives total control over the execution environment — on-prem or air-gapped networks,
custom hardware, arbitrary plugins — at the cost of running, patching, and securing the
server and its agents yourself. Both implement the identical underlying pipeline
concepts from Phase 1 (stages, gates, credentials, environments); the axis is hosted
convenience versus self-hosted flexibility, covered in depth in Phase 3.
