<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · compose](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 5 — Production & Best Practices: Notes

Getting an image *production-ready* means it's small, secure, observable, and
well-behaved at runtime — every earlier phase's techniques applied deliberately
and together. The `examples/Dockerfile.springboot` and `examples/Dockerfile.nextjs`
already apply all of these; this phase makes the reasoning behind each one
explicit, so it reads as a checklist you can hold any real Dockerfile up against.

## 5.1 — Image size

### Why it matters

Smaller images pull faster — directly shortening deploy time and, critically,
autoscaling reaction time (a new instance can't serve traffic until its image has
finished pulling). They also cost less registry storage/transfer, and — just as
important as speed — have a **smaller attack surface**: fewer installed packages
means fewer potential CVEs sitting in a running production container.

### Key Concepts

- **Small base image.** Prefer `alpine` (a minimal Linux distribution, ~5 MB
  base), a vendor `-slim` variant, or **distroless** (Google's images that
  contain *only* the language runtime — no shell, no package manager, no
  coreutils at all) over a full OS base. `examples/Dockerfile.springboot` uses
  `eclipse-temurin:21-jre-alpine` for the runtime stage — a JRE, not a full JDK,
  on an Alpine base — specifically because the runtime stage never needs a
  compiler.
- **Multi-stage builds** (Phase 2, 2.4) — ship only the compiled artifact, never
  the build toolchain, in the final stage.
- **`.dockerignore`** (Phase 2, 2.2) — keep the build context lean so nothing
  extraneous is ever copied in.
- **Fewer, cleaner layers.** Combine related `RUN` commands with `&&` and clean
  up caches in the *same* layer — a later `RUN rm -rf ...` cannot shrink an
  earlier layer's size on disk, since layers are additive (Phase 2, 2.3).

```dockerfile
# One layer: installs curl, uses it, then removes the apt cache — none of the
# apt metadata/cache persists in any layer of the final image.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
```

In this example, all three commands run in a single `RUN`, so they produce a
*single* layer whose final on-disk diff already excludes the apt cache — the
cache was written and then deleted before that one layer was ever finalized. Had
the cleanup been a separate, later `RUN rm -rf /var/lib/apt/lists/*` instruction,
the apt cache would still be sitting, permanently, in the earlier `apt-get
install` layer — visible in `docker history` and counted in the image's total
size, even though `ls` inside the final container wouldn't show it.

### Why it's useful

On this repo's own Spring Boot Dockerfile, multi-stage + a JRE-alpine base
routinely turns what would be a 700–900 MB single-stage JDK+Maven image into one
closer to 200 MB — a difference that's very directly felt in CI build cache
transfer time and in how fast a new pod can start serving traffic during an
autoscaling event.

## 5.2 — Security

### Run as a non-root user

Containers run as `root` **by default** unless a Dockerfile says otherwise. If an
attacker manages to exploit the application inside the container and escape its
process sandbox, running as root there means the escape lands as root — a far
worse outcome than landing as an unprivileged user with no meaningful
permissions.

```dockerfile
RUN addgroup -S app && adduser -S app -G app
USER app
```

This is exactly what both example Dockerfiles do: create a dedicated,
unprivileged system user/group (`-S` = system account, no login shell, no home
directory needed), then switch to it with `USER app` before the final `COPY`/
`ENTRYPOINT`. Every instruction after `USER app` — and the container's actual
running process — executes as that unprivileged user, not root. Pair this, where
the base image and application allow it, with a **read-only root filesystem**
(`docker run --read-only`, or the Kubernetes pod-security equivalent) so even a
compromised process can't modify the container's own filesystem.

### Minimal base images and scanning

- **Fewer installed packages = fewer CVEs.** Alpine and distroless bases
  deliberately omit a shell, package manager, and most coreutils — there's
  simply less software present for a scanner to flag or an attacker to abuse
  post-compromise (no `bash`/`curl`/`wget` available inside the container to
  pivot with, in the distroless case).
- **Scan images in CI**, not just at write-time. Tools like **Trivy**, `docker
  scout`, or **Snyk** scan an image's OS packages and language dependencies
  against known-CVE databases and can fail a build/block a push above a chosen
  severity threshold. A base image that was clean when you last rebuilt can
  accrue newly disclosed CVEs over time even with no Dockerfile changes — so
  scanning belongs in the regular CI pipeline (Phase 13), not just a one-time
  check.

### Never bake secrets into an image

This is one of the most consequential production mistakes to get wrong.
**Anything `COPY`'d into an image, or set via `ENV`/`ARG`, is permanently
recorded in that layer** — and remains recoverable via `docker history` **even
if a later layer deletes or overwrites it**, because layers are diffs stacked on
top of each other, not destructive edits.

```dockerfile
# DON'T:
ENV DB_PASSWORD=supersecret123
RUN rm -f /app/temp-credentials.json    # doesn't help — the file existed in an
                                          # EARLIER layer and is still extractable
```

```bash
# Anyone with the image can recover it:
docker history --no-trunc myimage | grep DB_PASSWORD
# <missing>   2 weeks ago   ENV DB_PASSWORD=supersecret123
```

Secrets must be injected **at runtime**, never baked in at build time: `-e`/
`--env-file` sourced from a secrets manager at deploy time (Phase 3), mounted
secret files (Docker Swarm/Kubernetes secrets), or BuildKit's dedicated
`--secret` build flag for the narrow case of a secret needed only *during* the
build (like a private registry credential to fetch a dependency) that must never
land in any layer at all.

### Pin base image tags (or digests)

Floating tags like `latest` (or even a loosely-pinned `21-jre-alpine`, which can
still move to a new patch build) mean a rebuild months later can silently pull in
a different base image than the one you tested against. Pinning a specific
version — and, for maximum reproducibility, a digest — keeps builds deterministic
and puts upgrades under your explicit control (bump the pin, rebuild, re-scan,
re-test) rather than happening invisibly on the next rebuild.

## 5.3 — Runtime behavior

### Healthchecks

```dockerfile
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
```

A `HEALTHCHECK` reports whether the application is actually **ready to serve
traffic**, not merely "the process exists." This example hits the Spring Boot
Actuator health endpoint rather than, say, just checking the Java process is
running — a JVM can be alive and completely wedged (deadlocked, out of DB
connections) while still technically "running." Orchestrators (Compose's
`condition: service_healthy`, Phase 4; Kubernetes readiness probes, Phase 14)
read this signal to decide whether to route traffic to an instance at all, and
whether to restart one that's gone unhealthy.

### Graceful shutdown

Be **PID 1 via exec-form `ENTRYPOINT`** (Phase 2, 2.1) so the application
directly receives `SIGTERM` when `docker stop` runs or a Kubernetes pod is
evicted, giving it a chance to stop accepting new requests, drain in-flight ones,
close database connections, and exit cleanly — before the grace period elapses
and the platform sends an unforgiving `SIGKILL`. This connects directly to System
Design's reliability material: a rolling deploy that doesn't drop requests
depends entirely on every instance shutting down gracefully like this.

### Logs to stdout/stderr

Containers should **never write log files to their own filesystem** — emit
everything to stdout/stderr and let the container platform collect and ship it
(to CloudWatch, an ELK stack, Loki, whatever the platform uses). This follows the
12-factor app's logging principle and keeps the container itself fully stateless
— nothing on the container's own disk needs to survive its removal, including
its own log history (which lived outside it in the platform's log store all
along).

### Resource limits and one process per container

Set `--memory`/`--cpus` (Phase 3, 3.6) so a single container can't starve its
neighbors, and run **a single primary process per container** — the database,
the app, and a reverse proxy are separate containers wired together by Compose
or Kubernetes, not three processes crammed into one container with a supervisor
script. This keeps scaling, restarting, health-checking, and log collection all
simple and independent per concern: you can scale the web tier to five replicas
without touching the database at all, and a crash in one concern doesn't corrupt
the state of another sharing its container.

## Production checklist (verified against `examples/`)

| Check | Where it lives in the examples |
|---|---|
| Multi-stage build, slim runtime base | `eclipse-temurin:21-jre-alpine` / `node:22-alpine` runtime stage |
| Non-root `USER` | `addgroup`/`adduser` + `USER app` (Spring), `USER nextjs` (Next.js) |
| `HEALTHCHECK` against a real endpoint | `wget ... /actuator/health` |
| Exec-form `ENTRYPOINT`/`CMD` (PID 1, signal-aware) | `ENTRYPOINT ["java", "-jar", "app.jar"]` / `CMD ["node", "server.js"]` |
| `.dockerignore` excludes secrets/junk | `.env`, `.env.*`, `.git`, `node_modules`, `target` all excluded |
| Pinned base image tags, config via env | `21-jre-alpine`, `node:22-alpine`; no `latest`; DB creds passed via compose `environment:` |
| Logs to stdout | Neither Dockerfile redirects output to a file — default stdout/stderr |

## 5.4 — Tagging strategy and the CI/CD handoff

A production image tag should be traceable back to the exact source that
produced it, not just a version number a human remembers to bump. A common
scheme combines a semantic version *and* the git commit SHA:

```bash
docker build -t myorg/expense-api:1.4.2 -t myorg/expense-api:a1b2c3d -f Dockerfile.springboot .
docker push myorg/expense-api:1.4.2
docker push myorg/expense-api:a1b2c3d
```

Tagging the same built image twice (once with a human-readable version, once
with the immutable commit SHA) means you can always answer "which commit is
running in prod right now" by reading the deployed tag, and a deploy manifest
can pin the SHA tag for maximum determinism while release notes reference the
semver tag for humans. This is exactly the artifact a CI/CD pipeline (Phase 13)
builds and pushes on every merge to main, and exactly what Kubernetes (Phase 14)
pulls by tag or digest when it schedules the workload.

## Perspective

Production Docker is a small, repeatable discipline, not a pile of unrelated
tricks: **slim + multi-stage** (size), **non-root + minimal base + no baked
secrets + scanned** (security), and **healthcheck + PID-1 signal handling +
stdout logs + resource limits + one process per container** (runtime behavior).
The example Dockerfiles in this track embody the entire checklist end to end —
apply it consistently and your images are ready for CI/CD (Phase 13) to build,
scan, and push, and for Kubernetes (Phase 14) to run reliably at scale.

## Summary / Key Takeaways

- **Multi-stage + a slim/alpine/distroless runtime base** is the biggest single
  lever on both image size and attack surface.
- **Always run as a non-root `USER`** — containers default to root, and a
  compromise as root is a far worse outcome than one as an unprivileged user.
- **Never bake a secret into an image** — `ENV`/`COPY`'d values are permanently
  visible via `docker history` even if a later layer "removes" them; inject
  secrets at runtime only.
- **`HEALTHCHECK`/readiness probes** must test real readiness (an actual
  endpoint), not just "the process exists," so orchestrators route traffic
  correctly and restart genuinely broken instances.
- **Exec-form `ENTRYPOINT`** makes your app PID 1, so it receives `SIGTERM` and
  can shut down gracefully — essential for zero-dropped-request rolling deploys.
- **Pin base image versions**, scan images in CI (Trivy/`docker scout`/Snyk),
  log to stdout, and run **one primary process per container** — the same small
  checklist covers almost every real production Dockerfile review.
