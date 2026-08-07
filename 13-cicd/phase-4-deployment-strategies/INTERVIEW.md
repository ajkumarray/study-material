<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · jenkins](../phase-3-jenkins/NOTES.md)
<!-- /nav -->

# Phase 4 — Deployment Strategies & Best Practices: Interview Q&A

⭐ = asked constantly.

**Q: Explain rolling, blue-green, and canary deployments.** ⭐⭐
Rolling replaces instances incrementally (zero downtime, gradual). Blue-green runs two full
environments and switches traffic from old (blue) to new (green) at once (instant cutover
and rollback, double infra). Canary sends a small % of traffic to the new version first,
watches metrics, then ramps up (safest for risky changes, limited blast radius).

**Q: How do you achieve zero-downtime deployments?** ⭐⭐
Use rolling/blue-green/canary so old and new run simultaneously, health-gate new instances
before routing traffic, and ensure changes (especially DB migrations) are backward-
compatible during the overlap. Load balancers/orchestrators drain old instances gracefully.

**Q: Why must database migrations be backward-compatible?** ⭐
During a rolling or canary rollout, old and new app versions run at the same time against
the same schema. A breaking migration would crash one of them. Use expand-then-contract: add
new columns/tables first, deploy code that uses them, and only remove old ones after every
instance has moved on.

**Q: How do you roll back a bad release?** ⭐
Redeploy the previous immutable artifact (image tag) — trivial because you build once and
version artifacts. Blue-green makes it an instant traffic switch; canary just stops the
ramp. Coordinate with data: forward-only or backward-compatible migrations so rollback is
safe.

**Q: How should secrets be handled in a pipeline?** ⭐⭐
Store them in a secrets manager (Vault, cloud secret store) or the CI platform's encrypted
secrets, inject at deploy/runtime, and never commit them or bake them into images. Scope to
least privilege, mask in logs, and rotate regularly.

**Q: What is the difference between deploy and release (feature flags)?** *nuance*
Deploying ships code to production; releasing exposes it to users. Feature flags decouple
them — you can deploy code "dark" and turn a feature on for specific cohorts later,
independent of the deploy. This enables safer, gradual rollouts and instant kill-switches.

**Q: How do you keep a pipeline fast and trustworthy?** ⭐
Fast, reliable, parallelized tests with dependency caching; cheap checks first (lint/unit)
and fail-fast ordering; deterministic, pinned builds; and eliminating flaky tests. A slow or
flaky pipeline gets bypassed or ignored, defeating its purpose.

**Q: How does security fit into CI/CD?**
Shift left: run dependency scanning (SCA), static analysis (SAST), container/image scanning,
and secret scanning inside the pipeline so vulnerabilities are caught before production. Add
signing/provenance for supply-chain integrity. Security becomes an automated gate, not a
post-hoc audit.
