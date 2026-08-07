<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · compose](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 5 — Production & Best Practices: Interview Q&A

⭐ = asked constantly.

**Q: How do you reduce Docker image size?** ⭐⭐
Small base image (alpine/slim/distroless), multi-stage builds that ship only the artifact,
`.dockerignore`, and combining/cleaning layers in the same `RUN`. Smaller images pull
faster (important for autoscaling) and have less attack surface.

**Q: Why run a container as a non-root user?** ⭐⭐
Containers run as root by default, so a container escape or app compromise can become host
root. Creating an unprivileged user and `USER app` limits the blast radius. Pair it with a
read-only root filesystem and dropped capabilities where possible.

**Q: Can you put secrets in a Dockerfile?** ⭐⭐
No. Anything copied or set via `ENV` is baked into an image layer and visible through
`docker history` — even if a later layer deletes it. Inject secrets at runtime (env from a
secrets manager, mounted files, orchestrator secrets), never at build time.

**Q: Why add a HEALTHCHECK / readiness probe?** ⭐
So the platform knows an instance is actually ready to serve (e.g. `/actuator/health`),
not merely that the process started. Orchestrators route traffic only to healthy instances
and restart unhealthy ones, preventing requests hitting a not-ready or wedged container.

**Q: How do you get graceful shutdown in a container?** ⭐
Run the app as PID 1 via exec-form `ENTRYPOINT` so it receives `SIGTERM` on stop/eviction,
and handle that signal to drain connections and release resources before the grace period
expires and `SIGKILL` hits. Shell-form entrypoints often swallow the signal.

**Q: Where should container logs go?** ⭐
To stdout/stderr, not files inside the container. The platform collects the streams and
ships them to a logging system. This follows 12-factor and keeps containers stateless
(ties to observability, System Design Phase 10).

**Q: Why pin base image tags?**
Reproducibility and controlled upgrades: `latest` (or a floating tag) can change under you,
breaking builds or silently pulling in changes. Pin a version or digest so a build is
deterministic and you decide when to bump it (and re-scan).

**Q: What's the "one process per container" principle?** *nuance*
Each container should run a single primary concern (app, DB, proxy are separate
containers), wired together by Compose/Kubernetes. It simplifies scaling, restarts,
health, and logging — you scale the web tier without touching the DB, and a crash affects
one concern. Multi-process containers fight the orchestration model.
