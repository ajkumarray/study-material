<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · github actions ➡](../phase-2-github-actions/NOTES.md)
<!-- /nav -->

# Phase 1 — CI/CD Concepts: Notes

CI/CD automates the journey from a git commit to running software, so releasing is
frequent, boring, and safe rather than rare and terrifying. It's the process backbone that
carries everything else in this repo to production.

## 1.1 — CI vs CD vs CD

- **Continuous Integration (CI):** every change is automatically **built and tested** on
  merge/PR, so integration problems surface within minutes, not at a big scary merge later.
  The heart of CI is a **fast, reliable test suite** that gates the change.
- **Continuous Delivery (CD):** every change that passes CI is automatically prepared for
  release (built into a deployable artifact, deployed to staging) and is **ready to ship at
  the click of a button** — the final push to prod is a human decision.
- **Continuous Deployment (CD):** goes further — every change that passes the pipeline is
  **automatically deployed to production**, no human gate. Requires high confidence
  (strong tests, monitoring, easy rollback).
- **Why automate:** faster feedback, fewer manual mistakes, smaller/safer releases,
  repeatability, and a documented, auditable path to prod. Manual deploys are where
  "works-on-my-machine" and forgotten-step outages live.

## 1.2 — The pipeline

A pipeline is an ordered set of **stages**, each gating the next (fail early, fail fast):

```
commit → [build] → [test] → [package/containerize] → [publish] → [deploy] → [verify]
              ↑ fail here and the rest never runs; the author gets feedback fast
```

- **Build** — compile / assemble.
- **Test** — unit → integration → (sometimes) e2e; the quality gate. Cheap/fast tests first.
- **Package** — produce the artifact (a jar, a **Docker image** — track 12).
- **Publish** — push the artifact to a registry/repository.
- **Deploy** — roll it out to an environment.
- **Verify** — smoke tests / health checks post-deploy.
- **Gates** between stages (tests must pass, a human approval for prod) enforce quality and
  control. **Fast feedback** is the design goal — order stages so failures show up as early
  and cheaply as possible.

## 1.3 — Build once, deploy many; environments

- **Build the artifact ONCE**, then promote the *same immutable artifact* through
  environments (dev → staging → prod). Never rebuild per environment — a rebuild could
  differ from what you tested. Configuration changes per environment (env vars/secrets),
  the **artifact does not**. (This is why Docker images + env-based config from track 12
  matter here.)
- **Environments** (dev/staging/prod) let you validate progressively; **promotion** moves a
  tested artifact forward. Production typically has an **approval gate** and protection
  rules.
- **The artifact is the contract:** `expense-api:1.4.2` that passed staging is the exact
  bytes that go to prod — reproducible, and trivially **rollback**-able by redeploying the
  previous tag.

## Perspective

CI/CD's value is **confidence and speed**: automated build+test on every change (CI) plus
an automated, repeatable path to release (CD) means you ship small changes often and
recover fast when something's wrong. The two non-negotiables are a **trustworthy test
suite** (or the automation just ships bugs faster) and a **single immutable artifact**
promoted through environments. Everything in Phases 2–4 is *how* to implement this with
specific tools and strategies.
