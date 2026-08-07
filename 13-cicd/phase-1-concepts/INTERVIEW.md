<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · github actions ➡](../phase-2-github-actions/NOTES.md)
<!-- /nav -->

# Phase 1 — CI/CD Concepts: Interview Q&A

⭐ = asked constantly.

**Q: What is CI/CD?** ⭐⭐
Continuous Integration automatically builds and tests every change so integration issues
surface immediately. Continuous Delivery/Deployment automatically prepares (and, for
deployment, ships) every passing change to production. Together they make releasing
frequent, repeatable, and low-risk.

**Q: Continuous Delivery vs Continuous Deployment?** ⭐⭐
Both automate the pipeline through to a release-ready artifact. Continuous **Delivery**
keeps a human "click to deploy" gate before production; Continuous **Deployment** removes
that gate — every change passing the pipeline goes straight to prod. Deployment demands
stronger tests, monitoring, and rollback.

**Q: What are the typical stages of a pipeline?** ⭐
Build → test (unit/integration/e2e) → package (e.g. Docker image) → publish to a registry →
deploy → post-deploy verify. Each stage gates the next; ordering cheap/fast checks first
gives fast feedback and fails early.

**Q: What does "build once, deploy many" mean and why does it matter?** ⭐⭐
Produce the artifact a single time and promote that exact immutable artifact through
environments, changing only configuration. Rebuilding per environment risks shipping
something different from what you tested; one artifact guarantees prod runs what staging
validated and makes rollbacks deterministic.

**Q: Why automate deployment instead of doing it manually?** ⭐
Automation is repeatable, fast, and auditable, and it eliminates forgotten steps and human
error — the common causes of deploy-time outages. It also enables small, frequent releases,
which are far less risky than large infrequent ones.

**Q: What makes CI actually valuable — what's the prerequisite?** ⭐
A fast, reliable test suite. CI that runs weak or flaky tests just ships bugs faster and
erodes trust. The tests are the quality gate; without meaningful coverage, the automation
provides speed but not confidence.

**Q: How do environments and promotion work?**
Changes flow dev → staging → prod, validating progressively. Promotion moves the same
tested artifact forward (not a rebuild). Production is usually protected with approval gates
and access controls, and config/secrets differ per environment while the artifact stays the
same.

**Q: How does CI/CD enable easy rollback?** *nuance*
Because each release is an immutable, versioned artifact, rolling back is just redeploying
the previous known-good version — no reverse migration of code. (Data/schema changes still
need care — backward-compatible migrations — but the app artifact rollback is trivial.)
