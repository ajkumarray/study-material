<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · containers images](../phase-1-containers-images/NOTES.md) | [Phase 3 · running containers ➡](../phase-3-running-containers/NOTES.md)
<!-- /nav -->

# Phase 2 — Writing Dockerfiles: Notes

A **Dockerfile** is the recipe that builds an image — a sequence of instructions, each
producing a layer. Writing them well (ordering for cache, multi-stage for size) is where
most of the practical skill lives. See `examples/Dockerfile.springboot`.

## 2.1 — The core instructions

| Instruction | Purpose |
|---|---|
| `FROM image:tag` | the base image to build on (start every stage) |
| `WORKDIR /app` | set (and create) the working directory |
| `COPY src dst` | copy files from the build context into the image |
| `RUN cmd` | execute a command at **build** time (install, compile) → a new layer |
| `ENV KEY=val` | set an environment variable (build + runtime) |
| `ARG KEY` | a **build-time** variable (not present at runtime) |
| `EXPOSE 8080` | document the listening port (informational) |
| `ENTRYPOINT` / `CMD` | what runs when the container **starts** |

- **`ENTRYPOINT` vs `CMD`:** `ENTRYPOINT` is the fixed executable; `CMD` is the default
  arguments (overridable at `docker run`). Common pattern: `ENTRYPOINT ["java","-jar",
  "app.jar"]`. Use **exec form** (JSON array) so the process is **PID 1** and receives
  signals (SIGTERM) for graceful shutdown — shell form wraps it in `/bin/sh` and breaks
  signal delivery.
- **`RUN` at build vs `CMD` at run:** `RUN` bakes results into a layer during `build`;
  `CMD`/`ENTRYPOINT` execute when the container **runs**. Don't confuse them.

## 2.2 — Layer caching & instruction order

Docker caches each layer and reuses it on rebuild **if that instruction and its inputs are
unchanged** — and once a layer's cache is invalidated, **every layer after it rebuilds**.
Therefore: **order from least- to most-frequently-changing.**

- **Copy dependency manifests before source.** The Spring example copies `pom.xml` and runs
  `dependency:go-offline` *before* copying `src`. Dependencies (which rarely change) are
  cached in their own layer, so editing source doesn't re-download them. Same idea for
  Node: `COPY package*.json` + `npm ci` before `COPY . .`.
- Combine related `RUN`s with `&&` and clean up in the *same* layer
  (`apt-get update && apt-get install -y x && rm -rf /var/lib/apt/lists/*`) — a separate
  cleanup layer doesn't shrink the earlier one (layers are additive).
- Use a **`.dockerignore`** so `COPY . .` doesn't drag `node_modules`, `.git`, or secrets
  into the context (faster builds, smaller images, no leaks).

## 2.3 — Multi-stage builds

The most important production technique. Use **multiple `FROM` stages**: build in a
fat image with all the toolchain, then copy **only the artifact** into a slim runtime
image.

- The Spring example: stage 1 = `maven:...-jdk-21` compiles the jar; stage 2 =
  `eclipse-temurin:21-jre-alpine` receives just `app.jar` via `COPY --from=build`. The
  final image has **no Maven, no source, no build cache** — a fraction of the size and a
  much smaller attack surface.
- Node example: a `deps` stage installs modules, a `build` stage compiles, a `runtime`
  stage carries only the standalone output.
- Rule: **the final stage should contain only what's needed to run**, nothing to build.

## Perspective

A good Dockerfile is *ordered for cache* and *multi-stage for size/security*. Those two
habits — copy deps before source, and build in one stage but ship a slim runtime stage —
account for most of the difference between a 900 MB image that rebuilds slowly and a
150 MB image that rebuilds in seconds. Everything in Phase 5 (non-root, healthcheck,
pinned base) layers onto this foundation.
