<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · github actions](../phase-2-github-actions/NOTES.md) | [Phase 4 · deployment strategies ➡](../phase-4-deployment-strategies/NOTES.md)
<!-- /nav -->

# Phase 3 — Jenkins: Notes

Jenkins is the long-standing, **self-hosted** automation server for CI/CD. Unlike
GitHub Actions, nobody hosts it for you — you install, run, and maintain the Jenkins
server (and its build agents) yourself, and pipelines are defined as code in a
**`Jenkinsfile`** committed to the repo. It predates the hosted platforms by over a
decade and is still extremely common in enterprises, regulated environments, and
on-prem setups. See `examples/Jenkinsfile`, which implements the same
build → test → image → gated-deploy flow as Phase 2's GitHub Actions examples, so you
can directly compare the two tools' syntax for identical concepts.

## 3.1 — Jenkins Architecture: Controller and Agents

Before the pipeline syntax, it helps to know what's actually running Jenkins.

- **Controller (formerly "master")** — the central Jenkins server: hosts the web UI,
  schedules builds, stores configuration and job/pipeline history. In production you
  never run build steps directly on the controller — it should stay free to
  orchestrate.
- **Agents (formerly "slaves")** — separate machines (or containers) that the
  controller dispatches actual build/test work to. An agent can be a persistent VM, an
  ephemeral cloud instance, or — very commonly today — a **Docker container** spun up
  just for one pipeline run (`agent { docker { image '...' } }`, as in the example).
- **Why separate them:** isolating execution onto agents keeps the controller stable and
  responsive even under heavy build load, lets different agents have different
  toolchains/hardware (a Docker agent for Java builds, a GPU agent for ML jobs), and
  limits the blast radius of a misbehaving build.
- **Labels** — agents are tagged with labels (`agent { label 'linux && docker' }`) so a
  pipeline can request "any agent matching this label" rather than naming a specific
  machine, giving the controller flexibility in scheduling.

### Why it's useful

Understanding controller/agent separation explains why "self-hosted" means real
operational responsibility: someone has to provision, patch, and scale both the
controller and its fleet of agents — the direct cost side of the "total control"
trade-off against a hosted platform like GitHub Actions.

### Summary

- Controller schedules and orchestrates; agents actually run build/test steps.
- Never run heavy work directly on the controller in production.
- Agents can be persistent machines or ephemeral containers (a common modern pattern).
- Labels let a pipeline request a *kind* of agent instead of a specific machine.

## 3.2 — Pipeline as Code: the `Jenkinsfile`

**Pipeline as code** means the pipeline definition — stages, steps, agents, everything —
lives in a file in the repository (`Jenkinsfile`, by convention at the repo root)
instead of being clicked together through Jenkins' web UI. This is the same shift
GitHub Actions makes by storing workflows as YAML in `.github/workflows/`.

- **Versioned and reviewed like any other code.** A pipeline change goes through the
  same PR review as an application change, and you can see exactly how the pipeline
  evolved via `git log`/`git blame` on the `Jenkinsfile`.
- **Branch-specific behavior for free.** Because the `Jenkinsfile` is checked out per
  branch, different branches can define different pipeline behavior just by having a
  different `Jenkinsfile` — no separate out-of-band configuration needed.
- **A "Multibranch Pipeline" job** in Jenkins automatically discovers branches (and PRs)
  in a repo and runs each one's own `Jenkinsfile` — the Jenkins equivalent of GitHub
  Actions triggering per-branch/per-PR automatically.
- **Contrast with UI-configured "Freestyle" jobs** (Jenkins' older job type): those
  store their configuration in Jenkins' own database, invisible to code review and easy
  to drift silently — pipeline-as-code exists specifically to avoid that.

### Why it's useful

Treating the pipeline as versioned code, not clickable configuration, gives you exactly
what versioning application code gives you: history, review, rollback, and the ability
to reason about "what did the pipeline do at commit X" — critical when debugging why an
old release built or deployed the way it did.

### Summary

- The `Jenkinsfile` in the repo defines the entire pipeline — reviewed and versioned
  like code.
- Multibranch Pipeline jobs auto-discover and run each branch's own `Jenkinsfile`.
- This replaces older UI-configured ("Freestyle") jobs whose config isn't in the repo.

## 3.3 — Declarative vs Scripted Pipelines

Jenkins supports two pipeline syntaxes, both written in Groovy under the hood.

- **Declarative pipeline** (the style used throughout `examples/Jenkinsfile`, and the
  recommended default) — a structured, opinionated block:
  `pipeline { agent {} stages {} post {} }`. It's more readable, gets built-in
  validation (Jenkins can tell you a `Jenkinsfile` is structurally invalid before it
  even runs), and has first-class support for conditions like `post` and `when` without
  hand-written Groovy control flow.
- **Scripted pipeline** — a general-purpose Groovy script wrapped in `node { ... }`,
  giving full programming-language power (loops, conditionals, functions, try/catch)
  with none of declarative's structural guardrails. More flexible for genuinely complex
  or highly dynamic logic, but also more rope to hang yourself with — errors surface at
  runtime rather than at parse time, and it's easy to write something unreadable.
- **Practical guidance:** default to declarative. Reach for scripted only for the
  specific parts that genuinely need arbitrary programmatic logic (declarative supports
  dropping into a `script { }` block *inside* an otherwise-declarative pipeline for
  exactly this — you don't have to choose one or the other for the whole file).

```groovy
// Declarative shell (see 3.4 for the fully worked example)
pipeline {
  agent any
  stages {
    stage('Build') {
      steps { sh 'mvn -B package' }
    }
  }
}
```

```groovy
// Scripted equivalent — same idea, imperative Groovy
node {
  stage('Build') {
    sh 'mvn -B package'
  }
}
```

### Why it's useful

Knowing both exists — and that declarative is the modern default — lets you read
Jenkinsfiles from either era and explain, in an interview, exactly why a team would
choose the more constrained declarative form for almost everything.

### Summary

- Declarative: structured `pipeline { agent; stages; post }`, validated, recommended
  default.
- Scripted: raw Groovy in `node { }`, maximum flexibility, more risk, less readable.
- A `script { }` block lets declarative pipelines drop into scripted logic locally when
  needed.

## 3.4 — Anatomy of a Declarative Pipeline

Walking through `examples/Jenkinsfile` block by block.

```groovy
pipeline {
  agent {
    docker { image 'maven:3.9-eclipse-temurin-21' ; args '-v $HOME/.m2:/root/.m2' }
  }

  options {
    timeout(time: 30, unit: 'MINUTES')
    disableConcurrentBuilds()
  }

  environment {
    REGISTRY = 'ghcr.io'
    IMAGE    = "ghcr.io/example/expense-api"
    REGISTRY_CREDS = credentials('ghcr-token')
  }

  stages {
    stage('Build') {
      steps {
        dir('02-spring-boot/expense-api') {
          sh 'mvn -B -DskipTests clean package'
        }
      }
    }

    stage('Test') {
      steps {
        dir('02-spring-boot/expense-api') { sh 'mvn -B test' }
      }
      post {
        always { junit '02-spring-boot/expense-api/target/surefire-reports/*.xml' }
      }
    }

    stage('Build image') {
      when { branch 'main' }
      steps {
        sh """
          echo \$REGISTRY_CREDS_PSW | docker login \$REGISTRY -u \$REGISTRY_CREDS_USR --password-stdin
          docker build -f 12-docker/examples/Dockerfile.springboot \
            -t \$IMAGE:${env.BUILD_NUMBER} ./02-spring-boot/expense-api
          docker push \$IMAGE:${env.BUILD_NUMBER}
        """
      }
    }

    stage('Deploy to production') {
      when { branch 'main' }
      steps {
        input message: 'Deploy to production?', ok: 'Deploy'
        sh "kubectl set image deployment/expense-api api=\$IMAGE:${env.BUILD_NUMBER}"
      }
    }
  }

  post {
    failure { echo 'Pipeline failed — notify the team (Slack/email).' }
    success { echo 'Pipeline succeeded.' }
  }
}
```

- **`agent`** — *where* the pipeline (or a specific stage) runs.
  `agent { docker { image 'maven:3.9-eclipse-temurin-21' } }` runs every step inside a
  fresh container from that image, giving a clean, reproducible toolchain instead of
  depending on whatever happens to be installed on a shared, mutable agent machine.
  `args '-v $HOME/.m2:/root/.m2'` mounts the host's Maven cache into the container so
  dependencies persist across builds (Jenkins' equivalent of Phase 2's dependency
  caching). `agent` can also be `any` (any available agent), `none` (require each stage
  to declare its own agent), or a label expression.
- **`options`** — pipeline-level settings. `timeout(time: 30, unit: 'MINUTES')` kills a
  hung build instead of it tying up an agent forever; `disableConcurrentBuilds()`
  ensures only one build of this branch runs at a time (avoids two builds racing to
  deploy).
- **`environment` + `credentials('id')`** — `REGISTRY`/`IMAGE` are plain environment
  variables; `REGISTRY_CREDS = credentials('ghcr-token')` pulls a credential from
  Jenkins' **credential store** by ID and injects it as environment variables
  (`REGISTRY_CREDS_USR`/`REGISTRY_CREDS_PSW` for a username/password credential) — never
  hardcoded, and Jenkins automatically **masks** these values in the build log.
- **`stages` / `stage` / `steps`** — the ordered pipeline. Each named `stage` (Build,
  Test, Build image, Deploy to production) groups `steps`: shell commands (`sh`) or
  plugin-provided steps (`junit`, `input`). This is the same "ordered, gating stages"
  model as Phase 1's generic pipeline shape and Phase 2's jobs/steps.
- **`dir('...')`** — scopes the working directory for the steps inside it, since this
  is a monorepo and the Spring Boot app lives in a subdirectory.
- **`when { branch 'main' }`** — a conditional stage: `Build image` and
  `Deploy to production` only run on the `main` branch. Feature-branch builds still get
  `Build` and `Test` (fast feedback on every change), but never build/push an image or
  touch production — mirroring Phase 2's tag/branch-scoped triggers.
- **`input`** — a **manual approval gate**: the pipeline literally pauses and waits for
  a human to click "Deploy" in the Jenkins UI before the `sh "kubectl set image ..."`
  step runs. This is Jenkins' direct equivalent of a GitHub Environment's required
  reviewers (Phase 2, §2.9) — same concept (Continuous Delivery's human gate), different
  mechanism.
- **`post`** — lifecycle hooks that run after stages complete, regardless of (or
  conditional on) outcome: `always { junit '...' }` publishes JUnit XML test results
  whichever way the Test stage went (so a failing build still shows *which* tests
  failed — the Jenkins equivalent of Phase 2's `if: always()` artifact upload);
  top-level `post { failure {} success {} }` sends notifications based on the whole
  pipeline's final result.

### Why it's useful

Every one of these blocks maps directly onto a Phase 1/Phase 2 concept you already
know — `agent` is a runner, `stages`/`steps` are the pipeline's gated stages,
`credentials()` is secrets management, `when` is a trigger/scope filter, `input` is an
approval gate, `post` is artifact/notification handling. Learning Jenkins after GitHub
Actions is mostly learning new syntax for concepts you've already internalized.

### Summary

- `agent` picks where steps run — a Docker agent gives a clean, reproducible toolchain.
- `options` set pipeline-wide behavior like timeouts and concurrency limits.
- `credentials('id')` injects secrets from Jenkins' credential store, masked in logs.
- `when { branch '...' }` scopes a stage to specific branches, like Actions' triggers.
- `input` is a manual approval gate — Jenkins' equivalent of a protected GitHub
  Environment.
- `post` handles always/success/failure hooks — test result publishing, notifications.

## 3.5 — Credentials Management

Jenkins' **credential store** centralizes secrets (usernames/passwords, secret text,
SSH keys, certificates) so they're never hardcoded in a `Jenkinsfile`.

- **Stored once, referenced by ID.** An admin (or a scoped, permissioned user) adds a
  credential through the UI or API and gives it an ID (`ghcr-token` in the example);
  pipelines reference it by that ID only — the actual secret value never appears in
  pipeline source.
- **`credentials('id')` binding.** Assigning `credentials('id')` to an environment
  variable in a declarative `environment {}` block automatically injects the secret —
  for a username/password credential, as `<VAR>_USR` and `<VAR>_PSW`; for a secret-text
  credential, as the variable directly.
- **Automatic masking.** Jenkins scans build console output and replaces any occurrence
  of a bound credential's value with `****`, reducing the chance of an accidental leak
  through `echo`/debug output.
- **Scoping** — credentials can be scoped globally, to a specific folder, or to a
  specific job, and Jenkins' role-based access control governs who can even select a
  given credential in a pipeline.

### Why it's useful

Centralized, ID-referenced, auto-masked credentials mean a `Jenkinsfile` (which,
remember, is reviewed and stored like any other code) never needs to contain an actual
secret value — the same principle GitHub Actions' `secrets.*` context implements, just
with Jenkins' own storage and injection mechanism.

### Summary

- Credentials are stored once in Jenkins' credential store and referenced by ID.
- `credentials('id')` injects them as environment variables at pipeline runtime.
- Jenkins automatically masks bound credential values in console output.
- Scoping/RBAC controls which jobs and users can use a given credential.

## 3.6 — The Plugin Ecosystem

Jenkins' core is deliberately minimal; almost everything beyond running shell commands
comes from **plugins** — Docker integration, `junit`/test-report publishing, Slack/email
notifications, Kubernetes agent provisioning, and hundreds more.

- **Huge breadth.** If a tool exists, there's usually a Jenkins plugin for it — this is
  part of why Jenkins remains entrenched in large, heterogeneous enterprises with many
  different systems to integrate.
- **The cost side.** Plugins are maintained at wildly varying quality, version
  compatibility between plugins (and between plugins and the Jenkins core) can break,
  and each plugin is additional attack surface that needs patching — a real, ongoing
  operational burden that a hosted platform's built-in action ecosystem (Phase 2) mostly
  externalizes to the platform vendor.
- **Where plugins show up in the example:** the `junit` step, Docker agent support, and
  credential-binding are all plugin-provided functionality, even though they read like
  built-in pipeline syntax.

### Why it's useful

The plugin ecosystem is simultaneously Jenkins' greatest strength (near-universal
integration) and its most-cited operational weakness (maintenance/security burden) — a
pairing worth being able to articulate precisely in an interview, since it's the most
common "what's the catch with Jenkins" follow-up.

### Summary

- Plugins provide almost all non-core Jenkins functionality — huge integration breadth.
- Plugin/core version compatibility and patching is an ongoing maintenance burden.
- This trade-off (breadth vs. maintenance) is central to the Jenkins-vs-Actions
  comparison (3.7).

## 3.7 — Jenkins vs GitHub Actions

| | **Jenkins** | **GitHub Actions** |
|---|---|---|
| Hosting | **Self-hosted** — you run/patch/secure the controller + agents | **Hosted** by GitHub — managed runners |
| Config | `Jenkinsfile` (Groovy, declarative or scripted) | YAML workflows |
| Extensibility | Huge plugin library, variable quality | Curated action Marketplace |
| Control | Total — any environment, air-gapped, custom hardware | Convenient, less to manage, less control |
| Cost model | Your own infra + ops time | Usage minutes (with a free tier) |
| Secrets | Credential store, `credentials('id')`, auto-masked | `secrets.*` context, auto-masked, `GITHUB_TOKEN` |
| Approval gate | `input` step | Protected Environment + required reviewers |
| Multi-branch/PR discovery | Multibranch Pipeline job | Native — triggers directly off repo events |
| Best for | Large enterprises, regulated/on-prem, complex/legacy integrations | GitHub-hosted projects, teams wanting near-zero ops |

Both tools implement the *same* underlying concepts from Phase 1 (ordered gated stages,
runners/agents, secrets management, approval gates, branch-scoped behavior) — the axis
that actually differs is **self-hosted control & flexibility (Jenkins)** vs **hosted
convenience & tight repo integration (Actions)**. Being able to map concept-for-concept
between the two (as the table above does) is a very common interview ask, since most
engineers will encounter both across a career.

### Why it's useful

Choosing between them in practice usually isn't about which is "better" in the
abstract — it's about whether the organization needs Jenkins' total control (on-prem,
regulated industries, exotic hardware, deep legacy tool integration) badly enough to
accept owning its operation, or whether GitHub Actions' hosted convenience is a better
fit for a team already living on GitHub.

### Summary

- Same core concepts, different hosting model: Jenkins self-hosted, Actions hosted.
- Jenkins trades ops burden for total control and plugin breadth.
- Actions trades some control for zero infrastructure and tight GitHub integration.
- Map syntax 1:1 mentally: `agent`↔`runs-on`, `stage/steps`↔`job/steps`,
  `credentials()`↔`secrets.*`, `input`↔protected Environment, plugin↔action.

## Perspective

Jenkins remains dominant wherever teams need **full control** — on-prem infrastructure,
regulated/air-gapped environments, bespoke hardware, or large established pipelines
built up over years — at the direct cost of running and maintaining the controller,
agents, and plugin ecosystem yourselves. The underlying concepts are identical to what
Phase 2 covered for GitHub Actions: ordered gated stages, agents/runners, credential
management, conditional scoping, and manual approval gates. Only the syntax and the
operational ownership differ. Being fluent in both means you can work productively in
any shop and give a grounded answer when asked to justify a tool choice — which, per
Phase 4, is only half the story; getting the *artifact* built is table stakes, and how
you actually roll it out to users is the other half.
