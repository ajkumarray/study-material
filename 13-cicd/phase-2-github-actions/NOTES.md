<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · concepts](../phase-1-concepts/NOTES.md) | [Phase 3 · jenkins ➡](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 2 — GitHub Actions: Notes

GitHub Actions is a **hosted CI/CD platform built into GitHub**: you commit YAML workflows
to `.github/workflows/`, and GitHub runs them on its runners in response to repo events. No
server to maintain. See `examples/github-actions/ci.yml` and `cd.yml`.

## 2.1 — The model: workflows → jobs → steps

- A **workflow** (one YAML file) is triggered by **events** (`on:`): `push`,
  `pull_request`, `tags`, `schedule` (cron), `workflow_dispatch` (manual).
- A workflow has **jobs**, which run **in parallel by default** (use `needs:` to sequence
  them — the CD example's `deploy` `needs: build-and-push`).
- Each job runs on a **runner** (`runs-on: ubuntu-latest`, a fresh VM) and contains
  ordered **steps**.
- A **step** is either a shell command (`run:`) or an **action** (`uses:`) — a reusable
  packaged unit (`actions/checkout@v4`, `actions/setup-java@v4`). The action marketplace is
  the ecosystem's superpower — most tasks already have an action.

## 2.2 — Runners, matrix, caching, services

- **Runners:** GitHub-hosted (Linux/Win/macOS VMs) or **self-hosted** (your own hardware,
  for special environments or cost). Ephemeral — clean state each run.
- **Matrix builds** run the same job across combinations (e.g. Java 17 + 21, or multiple
  OSes) in parallel — test broad compatibility cheaply:
  ```yaml
  strategy: { matrix: { java: [17, 21] } }
  ```
- **Caching** (`cache: maven` in setup-java, or `actions/cache`) persists dependencies
  between runs so builds don't re-download the world — the biggest speed lever.
- **Service containers** spin up dependencies (a real Postgres in the CI example) for
  integration tests, health-gated and torn down after — no mocking the database.

## 2.3 — Secrets, environments, permissions

- **Secrets** (`${{ secrets.X }}`) are encrypted repo/org/environment values (registry
  tokens, cloud creds) — never hardcode credentials. The built-in `GITHUB_TOKEN` authorizes
  actions against the repo.
- **Least-privilege permissions:** set `permissions:` to the minimum the job needs
  (`contents: read`, `packages: write`) — the CD example does. Don't grant broad write
  scopes by default.
- **Environments** (`environment: production`) add protection rules — **required reviewers**
  (manual approval), wait timers, and environment-scoped secrets — the gate before prod.
- **Reusable workflows** (`workflow_call`) and **composite actions** let you DRY shared
  pipeline logic across repos.

## The two example workflows

- **`ci.yml`** — on every push/PR: checkout → JDK + Maven cache → spin up Postgres →
  `mvn verify` (the tests you wrote) → upload the report. Fast feedback on every change.
- **`cd.yml`** — on a version tag: log in to GHCR → derive tags from git → **build & push
  the Docker image once** (with layer caching) → a **gated `deploy` job** using a protected
  `production` environment. This is "build once, deploy many" in practice.

## Perspective

GitHub Actions' strength is **zero infrastructure + a huge action ecosystem + tight repo
integration**: triggers on any repo event, parallel jobs, matrix testing, caching, service
containers, and environment-gated deploys — all in versioned YAML next to the code. For
projects already on GitHub it's usually the path of least resistance; the trade-off vs
Jenkins (Phase 3) is hosted-convenience vs self-hosted-control.
