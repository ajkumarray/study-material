<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · concepts](../phase-1-concepts/NOTES.md) | [Phase 3 · jenkins ➡](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 2 — GitHub Actions: Interview Q&A

⭐ = asked constantly.

**Q: What are workflows, jobs, and steps?** ⭐⭐
A workflow is a YAML file triggered by repo events; it contains jobs, which run in parallel
by default (sequence with `needs:`); each job runs on a runner and has ordered steps. A step
is a shell command (`run:`) or a reusable action (`uses:`).

**Q: What triggers a workflow?** ⭐
The `on:` key: `push`, `pull_request`, `tags`, `schedule` (cron), `workflow_dispatch`
(manual button), and more. You scope by branch/tag/path so, e.g., CI runs on PRs and CD runs
only on version tags.

**Q: How do you speed up builds in Actions?** ⭐⭐
Cache dependencies (`cache: maven`/`actions/cache`) so they aren't re-downloaded each run,
use matrix builds to parallelize, split/parallelize jobs, and reuse Docker layer caching
(`cache-from/to: gha`). Caching is usually the biggest win.

**Q: What is a matrix build?**
A strategy that runs the same job across combinations of parameters (e.g. Java 17 and 21,
multiple OSes) in parallel, so you test compatibility broadly without duplicating the
workflow. Defined under `strategy.matrix`.

**Q: How do you run integration tests that need a database?** ⭐
Use a service container: declare the DB image (e.g. `postgres`) under `services`, health-gate
it, and point the app at it. GitHub starts a real database for the job and tears it down
after — no mocking the DB.

**Q: How do you handle secrets?** ⭐⭐
Store them as encrypted repo/org/environment secrets and reference `${{ secrets.NAME }}`;
never hardcode credentials. Use the built-in `GITHUB_TOKEN` for repo/registry auth, and set
`permissions:` to least privilege so a job can't do more than it needs.

**Q: How do you gate a production deploy?** ⭐
Use a GitHub **Environment** (`environment: production`) with protection rules — required
reviewers (manual approval), wait timers, and environment-scoped secrets. The deploy job
pauses for approval before running.

**Q: GitHub-hosted vs self-hosted runners?** *nuance*
Hosted runners are managed ephemeral VMs — zero maintenance, good default. Self-hosted
runners are your own machines — needed for special hardware, private-network access, GPUs,
or cost control at scale, but you own patching/security and isolation. Many orgs use a mix.
