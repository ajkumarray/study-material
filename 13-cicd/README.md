<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 13 — CI/CD

**Automate the path from commit to production.** Every push builds, tests, and packages
the app; every release ships it — repeatably, with no manual steps to forget. CI/CD is the
conveyor belt that carries the code you wrote (and the Docker images from track 12) into
users' hands.

Taught for someone who has the pieces to ship: the **Spring API (02)** with its **9 tests**,
a **Dockerfile (12)**, and a **Kubernetes (14)** target. CI/CD wires them into a pipeline.

> **Format note:** no CI runner/Jenkins server is available here, so this track is
> **theory + real, correct-by-construction pipelines** (GitHub Actions workflows, a
> `Jenkinsfile`) with commentary — the same approach as the Docker and Databases tracks.
> The files are written to real-world standards.

## The artifacts: `examples/`

| File | What it shows |
|---|---|
| `github-actions/ci.yml` | **CI**: on push/PR → JDK setup + Maven cache, a Postgres **service container**, `mvn verify`, upload test report |
| `github-actions/cd.yml` | **CD**: on tag → build & push a Docker image to GHCR, then a gated **deploy** job |
| `Jenkinsfile` | the same flow as a **Jenkins declarative pipeline** (agent, stages, credentials, manual approval gate) |

## Curriculum

### Phase 1 — CI/CD concepts ✅
- [x] 1.1 CI vs Continuous Delivery vs Continuous Deployment; why automate
- [x] 1.2 The pipeline: stages (build → test → package → deploy), gates, fast feedback
- [x] 1.3 Build once, deploy many; the immutable artifact; environments (dev/stage/prod)
- NOTES · INTERVIEW

### Phase 2 — GitHub Actions ✅
- [x] 2.1 Workflows, jobs, steps, actions; triggers (`on: push`/`pull_request`/`tags`)
- [x] 2.2 Runners, matrix builds, caching, service containers
- [x] 2.3 Secrets, environments, permissions (least privilege); reusable workflows
- NOTES · INTERVIEW

### Phase 3 — Jenkins ✅
- [x] 3.1 Pipeline-as-code: the `Jenkinsfile`; declarative vs scripted
- [x] 3.2 Agents, stages, steps, `post`; credentials; plugins
- [x] 3.3 Jenkins vs GitHub Actions (self-hosted control vs hosted convenience)
- NOTES · INTERVIEW

### Phase 4 — Deployment strategies & best practices ✅
- [x] 4.1 Rolling, blue-green, canary deployments; rollbacks
- [x] 4.2 Secrets management, artifact promotion, approval gates, environment protection
- [x] 4.3 Pipeline hygiene: fast/reliable tests, fail fast, security scanning, caching
- NOTES · INTERVIEW

### Capstone ✅
- [x] A full pipeline for the expense stack: build → test (with a real DB) → containerize →
  push → deploy to Kubernetes, gated. See `CAPSTONE.md`.

## How this connects

- **← Spring Boot (02):** the tests the CI stage runs are the ones you wrote.
- **← Docker (12):** the CD stage builds and pushes those exact images.
- **→ Kubernetes (14):** the deploy stage rolls the new image onto the cluster.
- **↔ System Design (17):** deployment strategies (canary/blue-green), rollbacks, and
  reliability are design concerns too.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · concepts** — [Notes](phase-1-concepts/NOTES.md) · [Interview](phase-1-concepts/INTERVIEW.md)
- **Phase 2 · github actions** — [Notes](phase-2-github-actions/NOTES.md) · [Interview](phase-2-github-actions/INTERVIEW.md)
- **Phase 3 · jenkins** — [Notes](phase-3-jenkins/NOTES.md) · [Interview](phase-3-jenkins/INTERVIEW.md)
- **Phase 4 · deployment strategies** — [Notes](phase-4-deployment-strategies/NOTES.md) · [Interview](phase-4-deployment-strategies/INTERVIEW.md)
<!-- /phases-nav -->
