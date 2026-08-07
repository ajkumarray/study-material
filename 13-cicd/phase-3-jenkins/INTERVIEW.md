<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · github actions](../phase-2-github-actions/NOTES.md) | [Phase 4 · deployment strategies ➡](../phase-4-deployment-strategies/NOTES.md)
<!-- /nav -->

# Phase 3 — Jenkins: Interview Q&A

⭐ = asked constantly.

**Q: What is Jenkins?** ⭐
A self-hosted automation server for CI/CD. You run it yourself and define pipelines as code
in a `Jenkinsfile` committed to the repo. It's older than the hosted platforms and still
widely used, especially in enterprises and on-prem/regulated environments.

**Q: What is a `Jenkinsfile` / pipeline as code?** ⭐⭐
A file in the repo that defines the pipeline (stages, steps, agents) as versioned,
reviewable code instead of UI configuration. It makes pipelines reproducible and part of the
codebase, and enables branch-specific behavior.

**Q: Declarative vs scripted pipelines?** ⭐
Declarative uses a structured `pipeline { agent; stages; post }` syntax — readable,
validated, recommended for most cases. Scripted is full Groovy (`node { }`) for complex
dynamic logic with more flexibility and more risk. Prefer declarative; use scripted only
when necessary.

**Q: What is an agent in Jenkins?** ⭐
Where a stage/pipeline executes — a node or a container. Using a Docker agent
(`agent { docker { image 'maven:...' } }`) gives each build a clean, reproducible toolchain
rather than relying on a mutable shared machine.

**Q: How does Jenkins handle secrets?** ⭐
Via the credential store, referenced with `credentials('id')` and injected as environment
variables that Jenkins masks in logs. Credentials are never hardcoded in the `Jenkinsfile`;
scopes and folders control who can use them.

**Q: How do you add a manual approval before production?** ⭐
The `input` step pauses the pipeline for a human to approve
(`input message: 'Deploy to production?'`). Combined with `when { branch 'main' }`, only
main-branch builds reach the gated deploy stage.

**Q: Jenkins vs GitHub Actions — how do you choose?** ⭐⭐
Jenkins is self-hosted: total control (on-prem, custom hardware, regulated environments)
but you own the server, agents, and plugin maintenance. GitHub Actions is hosted with
managed runners and tight repo integration — near zero-ops, ideal for GitHub projects. Pick
control/flexibility vs convenience/low-maintenance.

**Q: What's the downside of Jenkins' plugin ecosystem?** *nuance*
It's powerful (integrations for almost anything) but a maintenance and security burden —
plugin version conflicts, CVEs, and upgrade friction. Part of choosing Jenkins is accepting
responsibility for keeping the server and its plugins patched and stable.
