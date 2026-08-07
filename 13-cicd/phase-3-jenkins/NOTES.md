<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · github actions](../phase-2-github-actions/NOTES.md) | [Phase 4 · deployment strategies ➡](../phase-4-deployment-strategies/NOTES.md)
<!-- /nav -->

# Phase 3 — Jenkins: Notes

Jenkins is the long-standing, **self-hosted** automation server. You run it yourself, and
pipelines are defined as code in a **`Jenkinsfile`** committed to the repo. It predates the
hosted platforms and is still everywhere in enterprises. See `examples/Jenkinsfile`.

## 3.1 — Pipeline as code; declarative vs scripted

- **Pipeline as code:** the pipeline lives in a `Jenkinsfile` in the repo (versioned,
  reviewed) rather than being clicked together in the UI — the same shift GitHub Actions
  makes.
- **Declarative pipeline** (the example, recommended): a structured `pipeline { agent {}
  stages {} post {} }` block — readable, validated, with built-in `post` conditions. Best
  for the vast majority of cases.
- **Scripted pipeline:** full Groovy programming (`node { ... }`) — maximum flexibility for
  complex/dynamic logic, but more rope to hang yourself. Prefer declarative; drop to
  scripted only when you must.

## 3.2 — Anatomy (from the example)

- **`agent`** — *where* stages run. `agent { docker { image 'maven:...' } }` runs the build
  in a clean container so the toolchain is reproducible (vs a mutable shared agent). Can be
  a label-selected node, `any`, or `none` (per-stage agents).
- **`stages` / `stage` / `steps`** — the ordered pipeline; each `stage` (Build, Test, Build
  image, Deploy) groups `steps` (`sh` commands, plugin steps).
- **`environment` + `credentials('id')`** — inject secrets from Jenkins' **credential
  store** (registry tokens, kube creds) — never hardcoded; masked in logs.
- **`when`** — conditional stages (`when { branch 'main' }` — feature branches only test;
  main also builds/deploys).
- **`input`** — a **manual approval gate** (`input message: 'Deploy to production?'`) — human
  control before prod.
- **`post`** — always/success/failure hooks: publish `junit` test results, send Slack/email
  notifications.
- **Plugins** — Jenkins' vast (sometimes messy) plugin ecosystem integrates almost anything;
  it's the source of both its power and its maintenance burden.

## 3.3 — Jenkins vs GitHub Actions

| | **Jenkins** | **GitHub Actions** |
|---|---|---|
| Hosting | **self-hosted** (you run/patch/secure the server + agents) | **hosted** by GitHub (managed runners) |
| Config | `Jenkinsfile` (Groovy) | YAML workflows |
| Ecosystem | huge plugin library | action marketplace |
| Control | total (any environment, air-gapped, custom hardware) | convenient, less to manage |
| Cost | infra + maintenance | usage minutes (free tier) |
| Best for | large enterprises, regulated/on-prem, complex existing setups | GitHub-hosted projects, teams wanting zero-ops |

Both do the same job (build/test/deploy on triggers); the axis is **self-hosted control &
flexibility (Jenkins)** vs **hosted convenience & tight repo integration (Actions)**. Being
able to compare them is a common interview ask.

## Perspective

Jenkins remains dominant where teams need **full control** — on-prem, regulated
environments, bespoke hardware, or big established pipelines — at the cost of running and
maintaining the server, agents, and plugins. The concepts are identical to Actions (stages,
agents/runners, credentials, gates); only the syntax and the ops burden differ. Knowing
both means you can work in any shop and justify a tool choice.
