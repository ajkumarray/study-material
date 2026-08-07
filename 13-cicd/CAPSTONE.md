# Capstone — A Full Pipeline for the Expense Stack

The CI/CD track's synthesis: the complete path from a git push to a running production
deployment of the Spring `expense-api`, expressed as **real pipelines** (GitHub Actions +
Jenkins) in `examples/`.

> No CI runner is available here, so the pipelines are correct-by-construction, written to
> production standards, with the commands and comments to run them.

## The pipeline, end to end

```
 git push ──▶ CI (ci.yml)              ──▶ tag v1.x ──▶ CD (cd.yml)
             ├─ checkout                                ├─ build Docker image (12)
             ├─ JDK + Maven cache                       ├─ push to GHCR registry
             ├─ start Postgres service                  └─ deploy job (gated by
             ├─ mvn verify (the 9 tests, 02)                environment: production)
             └─ upload test report                          └─ kubectl set image (14)
```

Same flow is mirrored in `Jenkinsfile` (agent → Build → Test → Build image → gated Deploy).

## Every phase, applied

| Phase | Concept | Where |
|---|---|---|
| 1 | Build → test → package → deploy; build once, deploy many | the overall two-workflow split (CI on push, CD on tag) |
| 1 | Immutable artifact promoted | one image built in `cd.yml`, deployed by tag |
| 2 | Actions: triggers, caching, service containers, secrets, environments | `ci.yml` (Postgres service, Maven cache), `cd.yml` (GHCR login, gated `production`) |
| 3 | Jenkins pipeline-as-code, agents, credentials, approval gate | `Jenkinsfile` |
| 4 | Deploy strategy + gate + rollback | gated deploy job; `kubectl set image` = rolling update; rollback = redeploy prior tag |

## The whole repo, wired for delivery

This capstone is where the tracks converge:

- The **tests (02)** are the CI quality gate.
- The **Docker image (12)** is the artifact CD builds and pushes.
- **Kubernetes (14)** is the deploy target (`kubectl set image` → rolling update).
- **Deployment strategy (Phase 4)** and reliability/rollback are the **System Design (17)**
  concerns applied to release.

## Run it (in a real repo)

```bash
# CI: copy ci.yml to .github/workflows/ci.yml — runs on every push/PR automatically
# CD: copy cd.yml to .github/workflows/cd.yml — runs when you push a tag:
git tag v1.0.0 && git push origin v1.0.0
# Jenkins: point a Pipeline job at the repo's Jenkinsfile
```

## The one-sentence takeaway

CI/CD turns "it compiles on my machine" into "every commit is automatically built and
tested, and every release is a repeatable, gated, rollback-able deployment of one immutable
artifact" — the automated backbone that carries everything else in this repo to users.
