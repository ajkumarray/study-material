<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · compose](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 5 — Production & Best Practices: Notes

Getting an image *production-ready*: small, secure, observable, and well-behaved at
runtime. The `examples/` Dockerfiles apply all of these.

## 5.1 — Image size

Smaller images pull faster (deploys, autoscaling), cost less storage, and have a smaller
attack surface.

- **Small base image:** `alpine`, `-slim`, or **distroless** (no shell/package manager at
  all). The Spring runtime uses `21-jre-alpine`, not the full JDK.
- **Multi-stage builds** (Phase 2): ship only the artifact, not the toolchain.
- **`.dockerignore`:** keep the context lean so nothing extra is copied in.
- **Fewer, cleaner layers:** combine `RUN`s and clean caches in the same layer (a later
  `rm` can't shrink an earlier layer).

## 5.2 — Security

- **Run as non-root.** By default containers run as root; a container escape then means
  host root. Create and switch to an unprivileged user (`USER app`) — both example
  Dockerfiles do. Combine with a read-only root filesystem where possible.
- **Minimal base:** fewer packages = fewer CVEs. Distroless/alpine shrink the attack
  surface; **scan images** (Trivy, `docker scout`, Snyk) for known vulnerabilities in CI.
- **No secrets in images or layers.** Anything `COPY`d or `ENV`'d is baked into a layer and
  visible via `docker history` — **even if deleted in a later layer**. Inject secrets at
  **runtime** (env from a secrets manager, mounted files, orchestrator secrets), never at
  build time.
- **Pin base image tags** (or digests) so builds are reproducible and you control upgrades.

## 5.3 — Runtime behavior

- **Healthchecks** (`HEALTHCHECK` / K8s probes): report real readiness (hit
  `/actuator/health`), not just "process running," so the orchestrator routes traffic only
  to healthy instances and restarts sick ones.
- **Graceful shutdown:** be **PID 1 via exec form** so the app receives `SIGTERM` on
  `docker stop`/pod eviction and can drain connections and close resources (System Design
  Phase 7) before the grace period ends and it's `SIGKILL`ed.
- **Logs to stdout/stderr.** Containers shouldn't write log files; emit to standard streams
  and let the platform collect/ship them (12-factor logs → System Design Phase 10
  observability).
- **Resource limits & one concern per container.** Set memory/CPU limits; run a single
  primary process per container (databases, app, and proxy are separate containers, wired
  by compose/K8s) — simpler scaling, restart, and reasoning.

## Production checklist (from the examples)

- ✅ Multi-stage build, slim runtime base (`jre-alpine`)
- ✅ Non-root `USER`
- ✅ `HEALTHCHECK` against a real endpoint
- ✅ Exec-form `ENTRYPOINT` (PID 1, signal-aware)
- ✅ `.dockerignore` excluding secrets/junk
- ✅ Pinned base image tags, config via env (no baked secrets)
- ✅ Logs to stdout

## Perspective

Production Docker is a small, repeatable discipline: **slim + multi-stage** (size),
**non-root + minimal + no baked secrets + scanned** (security), and **healthcheck + PID-1
signals + stdout logs + limits** (runtime). The example Dockerfiles embody the checklist —
apply it and your images are ready for the CI/CD (13) pipeline to build and push, and for
Kubernetes (14) to run reliably at scale.
