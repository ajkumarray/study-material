<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · jenkins](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 4 — Deployment Strategies & Best Practices: Notes

The final stage — getting the new version to users **without downtime or risk**. How you
roll out matters as much as what you built. These strategies pair with Kubernetes (14),
which implements several natively.

## 4.1 — Rollout strategies

- **Recreate** — stop the old version, start the new. Simple but causes **downtime**. Only
  for things that can tolerate a gap.
- **Rolling update** (the K8s default) — replace instances **incrementally** (a few at a
  time), keeping the service up throughout. Zero-downtime, gradual. Needs the new version to
  be **backward-compatible** with the old during the overlap.
- **Blue-green** — run two full environments (blue = current, green = new). Deploy to green,
  test it, then **switch all traffic** at the load balancer. Instant cutover and instant
  **rollback** (switch back), at the cost of double the infrastructure.
- **Canary** — release the new version to a **small % of traffic/users** first, watch
  metrics/errors, then progressively ramp to 100% (or roll back). The safest for risky
  changes — you catch problems with limited blast radius. Often automated with metric-based
  promotion.

## 4.2 — Safety practices

- **Rollback plan:** always be able to revert fast. With immutable artifacts (Phase 1),
  rollback = redeploy the previous image tag. Blue-green/canary make it near-instant.
- **Backward-compatible database migrations:** schema changes must work with **both** the
  old and new app versions during a rolling/canary overlap (expand-then-contract: add
  columns before using them, remove only after all instances stop needing them —
  Databases/Software-Design discipline). This is the hardest part of zero-downtime deploys.
- **Secrets management:** inject at deploy/runtime from a secrets manager (Vault, cloud
  secret stores, K8s Secrets), never in the image or repo (Docker Phase 5). Rotate them.
- **Artifact promotion & gates:** promote the *same* artifact dev → staging → prod;
  protect prod with **approval gates** and environment rules (the examples' gated jobs).
- **Feature flags** decouple *deploy* from *release*: ship code dark, turn it on for cohorts
  independently — a complement to canary.

## 4.3 — Pipeline hygiene

- **Fast, reliable tests.** Slow pipelines get bypassed; flaky tests get ignored (and erode
  trust). Parallelize, cache, and keep the critical path quick. Put cheap checks first
  (lint/unit) so failures are cheap.
- **Fail fast.** Order stages so the most likely/cheapest failures stop the pipeline early.
- **Security scanning in the pipeline:** dependency scanning (SCA), SAST, image/container
  scanning (Trivy), secret scanning — shift security **left** so issues are caught before
  prod, not after.
- **Reproducibility & caching:** pinned tool versions, cached deps, deterministic builds.
- **Observability of the pipeline itself:** clear logs, test reports, and notifications on
  failure so the team reacts quickly (ties to System Design Phase 10).

## Perspective

Building the artifact is table stakes; **releasing it safely** is the craft. Choose a
rollout strategy by risk tolerance — **rolling** for routine changes, **canary** for risky
ones, **blue-green** when you want instant cutover/rollback — and back it with fast tests,
immutable artifacts, backward-compatible migrations, managed secrets, and approval gates.
Done well, deploys become frequent non-events, and recovery is a redeploy away.
