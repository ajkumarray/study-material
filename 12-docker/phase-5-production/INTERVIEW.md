<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · compose](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 5 — Production & Best Practices: Interview Q&A

⭐ = asked constantly.

**Q: How do you reduce Docker image size?** ⭐⭐
In priority order: multi-stage builds so the shipped image excludes the build
toolchain entirely (a Java build can go from 700–900 MB with a full JDK+Maven
down to ~200 MB with just a JRE and the jar); a small base image — `alpine`, a
vendor `-slim` variant, or distroless (no shell, no package manager at all) —
instead of a full OS image; a `.dockerignore` so the build context (and any
`COPY . .`) never drags in junk; and combining related `RUN` commands with
cleanup in the *same* layer, since layers are additive and a later `rm` in a
separate layer doesn't shrink an earlier one's size on disk. Smaller images pull
faster — which matters directly for deploy time and autoscaling reaction time —
and present less surface for a vulnerability scanner to flag.

**Q: Why run a container as a non-root user?** ⭐⭐
Containers run as `root` by default unless a Dockerfile explicitly switches
users. If the application inside is compromised and an attacker manages to
escape the process sandbox, running as root there means the escape lands as
*root on the host's shared kernel* — a far more dangerous outcome than landing
as an unprivileged account with essentially no permissions. The standard fix is
creating a dedicated system user and switching to it before the app runs:

```dockerfile
RUN addgroup -S app && adduser -S app -G app
USER app
```

Both example Dockerfiles in this repo do exactly this (`USER app` for the Spring
Boot image, `USER nextjs` for the Next.js image) — every instruction after that
line, and the container's actual running process, executes as the unprivileged
account. Pairing it with a read-only root filesystem (`docker run --read-only`)
further limits what a compromised process could do even without root.

**Q: Can you put secrets in a Dockerfile?** ⭐⭐
No — and this is one of the most consequential mistakes to get wrong in
practice. Anything set via `ENV`, passed as `ARG`, or `COPY`'d into the image is
permanently recorded in that instruction's layer, and remains recoverable via
`docker history --no-trunc` **even if a later instruction deletes or overwrites
it** — because image layers are additive diffs stacked on top of each other, not
destructive in-place edits; the earlier layer containing the secret still
exists underneath. Secrets must be injected at **runtime** instead: `-e`/
`--env-file` sourced from a real secrets manager at deploy time, mounted secret
files from the orchestrator (Kubernetes Secrets, Docker Swarm secrets), or
BuildKit's dedicated `--secret` flag for the narrow case where a secret is
needed only transiently *during* the build (like a token to fetch a private
dependency) and must never land in any layer at all.

*Follow-up: if someone already accidentally committed a secret into an image
layer, does deleting the file and rebuilding fix it?* No — the old layer with
the secret is still cached and distributable to anyone who already pulled that
image (or any tag/digest still pointing at it). The actual fix is rotating the
leaked secret and rebuilding from scratch with the secret never entering a layer
in the first place; simply "removing" it in a later `RUN` does not retroactively
scrub it from the image's history.

**Q: Why add a `HEALTHCHECK` / readiness probe?** ⭐⭐
So the platform knows an instance is actually **ready to serve traffic**, not
merely that its process exists. A JVM (or any app) can be alive and completely
wedged — deadlocked, out of DB connections, stuck in a retry loop — while still
technically "running" from the OS's point of view. This repo's example hits the
Spring Boot Actuator health endpoint (`wget -qO- http://localhost:8080/actuator/health
|| exit 1`) rather than checking for a live process, so a genuinely broken
instance is correctly reported unhealthy. Orchestrators read that signal
directly: Compose's `condition: service_healthy` withholds starting dependents
until it passes (Phase 4), and Kubernetes readiness probes remove an unhealthy
pod from a Service's load-balancing rotation without restarting it, while
liveness probes trigger an actual restart.

**Q: How do you get graceful shutdown in a container?** ⭐⭐
Run the application as PID 1 via **exec-form** `ENTRYPOINT`
(`["java", "-jar", "app.jar"]`, not the bare shell-form equivalent) so it
receives `SIGTERM` directly when `docker stop` runs or a Kubernetes pod is
evicted, and have the application handle that signal — stop accepting new
requests, finish in-flight ones, close DB/cache connections — before the grace
period expires and the platform escalates to an unforgiving `SIGKILL`.
Shell-form entrypoints often don't forward the signal to the actual application
process, so it never gets the chance to shut down cleanly, and in-flight
requests get dropped mid-response during every restart or rolling deploy.

**Q: Where should container logs go?** ⭐
To stdout/stderr — never written to a log file inside the container's own
filesystem. The container platform (Docker's logging driver, Kubernetes'
kubelet) collects those streams and ships them to a centralized logging system
(CloudWatch, ELK, Loki, …). This follows the 12-factor app's logging principle
and keeps the container itself fully stateless — nothing that matters needs to
survive on the container's own disk, including its own log history.

**Q: Why pin base image tags instead of using `latest`?** ⭐
Reproducibility and controlled upgrades. A floating tag — `latest`, or even a
loosely pinned tag like `21-jre-alpine` that can still receive new patch builds
— can point to different bytes on a rebuild months later than it did when you
last tested, silently changing behavior with no corresponding code change to
review. Pinning an exact version (or, for maximum determinism, a digest) makes a
build deterministic and puts version bumps under your explicit control — you
choose when to bump the pin, rebuild, re-scan for new CVEs, and re-test, rather
than it happening invisibly on the next unrelated rebuild.

**Q: What's the "one process per container" principle?** *nuance* ⭐
Each container should run a single primary concern — the app, the database, and
a reverse proxy are separate containers wired together by Compose or Kubernetes,
not multiple processes crammed into one container under a supervisor script.
This keeps scaling, restarting, health-checking, and log collection independent
per concern: you can scale the web tier to five replicas without touching the
database, a crash in one process doesn't corrupt another's in-memory state
inside the same container, and a healthcheck against one container unambiguously
reflects one thing's health rather than an ambiguous mix. Multi-process
containers actively fight this model — the platform can't restart or scale "just
the broken part" of a container running several unrelated processes.

**Q: How do you scan an image for known vulnerabilities, and when should that
run?**
Tools like **Trivy**, `docker scout`, or **Snyk** scan an image's OS packages
and language-level dependencies against CVE databases, and can be configured to
fail a build or block a push above a chosen severity threshold. This should run
routinely in CI (Phase 13) — on every build, not just once when the Dockerfile
was first written — because a base image that was clean at the time can
accumulate newly disclosed CVEs over time even with zero Dockerfile changes;
periodic rescans of already-published images catch that drift.

**Q: How should you tag images for a real deployment pipeline?**
Tag the same built image with both a human-readable semantic version and the
immutable git commit SHA that produced it (`myorg/api:1.4.2` and
`myorg/api:a1b2c3d`), and push both tags. That way "which commit is running in
production right now" is always answerable by reading the deployed tag, deploy
manifests can pin the SHA tag for maximum determinism, and release notes/humans
can still reference the friendlier semver tag. This is the artifact a CI/CD
pipeline (Phase 13) produces on every merge and exactly what an orchestrator
(Phase 14) later pulls by tag or digest.
