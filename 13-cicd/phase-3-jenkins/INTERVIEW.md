<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · github actions](../phase-2-github-actions/NOTES.md) | [Phase 4 · deployment strategies ➡](../phase-4-deployment-strategies/NOTES.md)
<!-- /nav -->

# Phase 3 — Jenkins: Interview Q&A

⭐ = asked constantly.

**Q: What is Jenkins, and how does its architecture differ from GitHub Actions'
model?** ⭐
Jenkins is a self-hosted automation server for CI/CD — you install, run, and maintain it
yourself, unlike GitHub Actions where GitHub manages the runners for you. Architecturally
it's split into a **controller** (the central server: web UI, scheduling,
configuration/history storage) and **agents** (separate machines or containers that
actually execute build/test steps). Heavy work is never supposed to run directly on the
controller in production — it stays free to orchestrate while agents (which can be
persistent machines or ephemeral Docker containers spun up per run) do the actual work.
Pipelines are defined as code in a `Jenkinsfile` committed to the repo, predating the
hosted platforms by over a decade and still very common in enterprises and on-prem/
regulated environments.

*Follow-up: why keep the controller separate from build execution?* Isolating execution
onto agents keeps the controller stable and responsive under load, lets different agents
have different toolchains or hardware, and limits the blast radius of a single
misbehaving or resource-hungry build.

**Q: What is a `Jenkinsfile` / "pipeline as code," and why does it matter?** ⭐⭐
A `Jenkinsfile` in the repo root defines the entire pipeline — agent, stages, steps,
gates — as versioned, reviewable code, instead of being configured by clicking through
Jenkins' web UI (the older "Freestyle" job model, whose config lives in Jenkins' own
database, invisible to code review). It gets you everything versioning application code
gets you: `git log`/`git blame` history on the pipeline itself, review via normal PRs,
and branch-specific behavior for free since each branch's `Jenkinsfile` is checked out
independently. A "Multibranch Pipeline" job automatically discovers branches/PRs in a
repo and runs each one's own `Jenkinsfile` — Jenkins' equivalent of Actions triggering
natively per branch/PR.

**Q: Declarative vs scripted pipelines — what's the difference, and which should you
default to?** ⭐
Declarative pipelines use a structured `pipeline { agent {} stages {} post {} }` block —
readable, validated by Jenkins before it even runs, and with built-in lifecycle hooks
like `post`/`when` without hand-written control flow. Scripted pipelines are raw Groovy
wrapped in `node { }` — full programming-language power (loops, conditionals, try/catch)
but no structural guardrails, so errors surface at runtime rather than parse time and
it's easy to write something hard to follow. Default to declarative for the vast
majority of pipelines; when a specific piece of logic genuinely needs arbitrary
programmatic control, you can drop into a `script { }` block *inside* an otherwise
declarative pipeline rather than rewriting the whole file as scripted.

**Q: What is an `agent` in Jenkins, and why use a Docker agent specifically?** ⭐
`agent` declares *where* pipeline stages execute — it can be `any` available agent,
`none` (forcing each stage to declare its own), a label expression, or a container
spec. `agent { docker { image 'maven:3.9-eclipse-temurin-21' } }` runs every step
inside a fresh container built from that image, giving each build a clean,
reproducible toolchain instead of depending on whatever happens to be installed on a
shared, potentially-drifted agent machine. Mounting a host cache directory
(`args '-v $HOME/.m2:/root/.m2'`) lets dependency downloads persist across builds even
though the container itself is disposable — the Jenkins equivalent of GitHub Actions'
dependency caching.

**Q: How does Jenkins handle secrets, and what happens if you `echo` one by
accident?** ⭐⭐
Via the **credential store**: an admin adds a credential (username/password, secret
text, SSH key, certificate) once and gives it an ID; pipelines reference it only by
that ID via `credentials('id')`, so the actual secret value never appears in the
`Jenkinsfile` itself. Binding `credentials('ghcr-token')` to an `environment` variable
injects it at runtime — for a username/password credential, as `<VAR>_USR` and
`<VAR>_PSW` automatically. Jenkins scans console output and masks (`****`) any
occurrence of a bound credential's actual value, so even an accidental `echo $PASSWORD`
is redacted in the build log rather than leaking the secret. Credential scope
(global/folder/job) plus role-based access control governs who can even select a given
credential.

```groovy
environment {
  REGISTRY_CREDS = credentials('ghcr-token')   // -> REGISTRY_CREDS_USR / _PSW
}
```

**Q: How do you add a manual approval gate before a production deploy in Jenkins?** ⭐
The `input` step pauses the running pipeline and waits for a human to approve through
the Jenkins UI before continuing:
`input message: 'Deploy to production?', ok: 'Deploy'`. Combined with
`when { branch 'main' }` scoping the whole deploy stage to the main branch, only
main-branch builds ever reach that gated `input` step — feature branches stop after
Build/Test and never touch it. This is the direct Jenkins equivalent of a GitHub
Actions job targeting a protected Environment with required reviewers (Phase 2): same
underlying concept — a human gate before production, the mechanism that turns
Continuous Deployment into Continuous Delivery — different tool, different syntax.

*Follow-up: what publishes test results so a failed build is diagnosable?* The `post`
block's `always { junit '.../surefire-reports/*.xml' }` — it runs whichever way the
Test stage went, publishing JUnit XML results so you can see exactly which test failed,
the same purpose as Actions' `if: always()` artifact-upload pattern.

**Q: How do you make a stage run only on certain branches?** ⭐
`when { branch 'main' }` on a stage — in the example `Jenkinsfile`, `Build` and `Test`
run for every branch (fast feedback on every change), but `Build image` and
`Deploy to production` are scoped with `when { branch 'main' }` so feature branches
never build/push an image or reach the deploy gate. `when` supports other conditions
too (`expression { }` for arbitrary Groovy conditions, `changelog`, `changeset`),
functioning like GitHub Actions' branch/tag-scoped `on:` triggers, just applied
per-stage rather than per-workflow.

**Q: Jenkins vs GitHub Actions — how would you frame the trade-off, and where would
you map their syntax onto each other?** ⭐⭐
Both implement the exact same underlying CI/CD concepts — ordered gated stages, an
execution environment (agent/runner), secrets management, branch-scoped behavior, and a
manual approval gate — just with different syntax and, more importantly, a different
operational model. Jenkins is self-hosted: total control over the environment (on-prem,
air-gapped networks, custom hardware, deep legacy integrations via its huge plugin
library) in exchange for owning the controller/agent infrastructure and its patching.
GitHub Actions is hosted: managed runners, zero infrastructure to maintain, and a
curated action Marketplace, in exchange for less control and (for very unusual
environments) less flexibility. Mapping concepts directly: `agent`↔`runs-on`,
`stage`/`steps`↔`job`/`steps`, `credentials('id')`↔`${{ secrets.NAME }}`,
`input`↔protected Environment with required reviewers, a Jenkins plugin↔a GitHub
action.

*Follow-up: what's the concrete downside of Jenkins' plugin ecosystem?* It's Jenkins'
biggest strength (near-universal tool integration) and its most common cited weakness
in the same breath — plugins vary wildly in maintenance quality, plugin-to-plugin and
plugin-to-core version compatibility can break on upgrade, and each installed plugin is
additional attack surface someone has to keep patched. A hosted platform's curated
action ecosystem mostly externalizes that burden to the platform vendor instead.

**Q: Why would a large enterprise choose Jenkins over a hosted CI/CD platform today?**
*nuance*
Regulatory or compliance requirements that mandate on-prem or air-gapped
infrastructure, existing deep investment in Jenkins plugins/integrations that would be
costly to replace, specialized/legacy hardware that a hosted runner can't access, or
simply organizational scale where the ops cost of self-hosting is justified by the
control it buys (custom security policies, private network access, precise control over
the build environment). It's rarely "Jenkins is technically superior" — it's "we need
the control Jenkins gives us badly enough to own operating it."
