<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · containers images](../phase-1-containers-images/NOTES.md) | [Phase 3 · running containers ➡](../phase-3-running-containers/NOTES.md)
<!-- /nav -->

# Phase 2 — Writing Dockerfiles: Notes

A **Dockerfile** is the recipe that builds an image — a sequence of instructions, each
producing (or modifying) a layer. Writing them well — ordering for cache, splitting
into multiple stages — is where most of the practical, interview-relevant Docker
skill lives, because it's the difference between a 900 MB image that takes minutes to
rebuild and a 150 MB image that rebuilds in seconds. This phase walks the real
multi-stage Dockerfiles in `examples/Dockerfile.springboot` and
`examples/Dockerfile.nextjs` instruction by instruction.

## 2.1 — The core instructions

| Instruction | Purpose |
|---|---|
| `FROM image:tag` | The base image to build on — starts every stage |
| `WORKDIR /app` | Set (and create if missing) the working directory for subsequent instructions |
| `COPY src dst` | Copy files from the **build context** into the image |
| `ADD src dst` | Like `COPY`, plus auto-extracts local tar archives and can fetch URLs — prefer `COPY` unless you need those extras |
| `RUN cmd` | Execute a command at **build time** (install packages, compile) → produces a new layer |
| `ENV KEY=val` | Set an environment variable, available at both **build and runtime** |
| `ARG KEY[=default]` | A **build-time-only** variable, passed via `--build-arg`, not present in the running container |
| `EXPOSE 8080` | Document the port the app listens on — purely informational, does **not** publish it |
| `USER app` | Switch the user subsequent instructions (and the container's process) run as |
| `LABEL key=val` | Attach metadata (maintainer, version, git SHA) to the image |
| `VOLUME /data` | Declare a mount point; Docker creates an anonymous volume there if none is bound at runtime |
| `ENTRYPOINT` | The fixed executable run when the container **starts** |
| `CMD` | Default arguments to `ENTRYPOINT` (or the whole command if no `ENTRYPOINT`) — overridable at `docker run` |

### `ENTRYPOINT` vs `CMD`

- **`ENTRYPOINT`** is the process that always runs — it's what makes the container
  "an nginx container" or "a java-app container." **`CMD`** supplies the default
  arguments to that process, which the caller *can* override by appending arguments
  to `docker run`.
- Common pattern: `ENTRYPOINT ["java", "-jar", "app.jar"]` with no `CMD` — the jar
  always runs, full stop. Another common pattern: `ENTRYPOINT
  ["docker-entrypoint.sh"]` + `CMD ["nginx", "-g", "daemon off;"]`, where `CMD`'s
  arguments get passed to the entrypoint script, and a caller can override just the
  arguments (`docker run myimg nginx -v`) without touching the fixed setup script.
- **Use exec form (JSON array), not shell form.** Exec form —
  `ENTRYPOINT ["java", "-jar", "app.jar"]` — runs the process directly as the
  container's **PID 1**, so it receives `SIGTERM` from `docker stop` and can shut
  down gracefully. Shell form — `ENTRYPOINT java -jar app.jar` — silently wraps the
  command in `/bin/sh -c "..."`, which becomes PID 1 instead; signals sent to the
  container may not reach your actual process, so it gets forcibly `SIGKILL`ed
  after the grace period instead of shutting down cleanly. This is a very common,
  very costly production bug.

```dockerfile
# Shell form — DON'T do this for a long-running service:
ENTRYPOINT java -jar app.jar
# /bin/sh becomes PID 1; `docker stop` sends SIGTERM to sh, which may not forward
# it to the java child process. Container hangs until the 10s grace period expires
# and gets SIGKILLed — no graceful shutdown, in-flight requests just get dropped.

# Exec form — do this instead:
ENTRYPOINT ["java", "-jar", "app.jar"]
# java itself is PID 1 and receives SIGTERM directly.
```

### `RUN` (build time) vs `CMD`/`ENTRYPOINT` (run time)

This is a very common early confusion: `RUN` executes a command **while the image is
being built** and bakes its result into a layer (e.g., installing a package, so the
package is present in every container started from that image). `CMD`/`ENTRYPOINT`
define what executes **when a container starts** from the finished image — they
don't run at all during `docker build`.

### Why it's useful

The instruction set is small on purpose — a Dockerfile should read like a short,
linear recipe. Getting `ENTRYPOINT`/`CMD` and exec-vs-shell form right is one of the
highest-leverage five-minute fixes you can make to a production Dockerfile: it's the
difference between a rolling deploy that drains connections cleanly and one that
drops in-flight requests on every restart.

## 2.2 — The build context, `docker build`, and `.dockerignore`

### Definition

The **build context** is the set of files sent from your machine to the Docker
daemon when you run `docker build` — it's what `COPY`/`ADD` instructions are allowed
to read from. `docker build -t myapp .` sends the entire `.` directory as the
context, which is why an oversized or secret-laden context is a real footgun.

### Key Concepts

- **`docker build -t name:tag -f path/to/Dockerfile <context-dir>`** — `-t` tags
  the resulting image; `-f` points at a Dockerfile that isn't named `Dockerfile` or
  isn't in the context root (the compose file in this repo does exactly this,
  pointing `-f` at `12-docker/examples/Dockerfile.springboot` while the context is
  `02-spring-boot/expense-api`).
- **`.dockerignore`** excludes paths from the context entirely, the same syntax
  idea as `.gitignore`. This repo's `examples/.dockerignore` excludes
  `node_modules`, `.next`, `dist`, `target`, `.git`, `.env`/`.env.*`, `*.log`, and
  the Dockerfiles/compose files themselves.
- **Why it matters three ways:** (1) speed — a smaller context uploads to the
  daemon faster; (2) correctness — a stray `COPY . .` can't accidentally pull in
  `node_modules` built for the host's OS/arch instead of the container's; (3)
  security — `.env` files, `.git` history, and cloud credentials sitting in the
  project directory never get a chance to land inside an image layer.

```
# examples/.dockerignore
node_modules
.next
dist
target
.git
.env
.env.*
*.log
Dockerfile*
docker-compose*.yml
README.md
```

In this file: `node_modules`/`dist`/`target`/`.next` are build outputs that should
be produced *inside* the image (by `npm ci`/`mvn package`), not copied in stale
from the host. `.git` is large and irrelevant to runtime. `.env`/`.env.*` is the
single most important line — without it, a local secrets file sitting next to the
Dockerfile could get copied into an image layer via an overly broad `COPY . .` and
become extractable by anyone who pulls the image (see Phase 5, 5.2).

### Why it's useful

A lean, correct `.dockerignore` is a five-line file that prevents two very common
production incidents: leaked secrets baked into a public image, and "works on my
machine" bugs from host-built artifacts (like a `node_modules` compiled for macOS)
silently overwriting what the container would have built for itself.

## 2.3 — Layer caching & instruction order

Docker caches each layer and reuses it on rebuild **if that instruction and its
inputs are byte-identical to a previous build** — and the moment one instruction's
cache is invalidated, **every instruction after it rebuilds from scratch**, even if
their own inputs didn't change (because a layer's identity depends on the layer
beneath it). The entire practical skill of writing a fast Dockerfile is: **order
instructions from least-frequently-changing to most-frequently-changing.**

### Worked example — from `examples/Dockerfile.springboot`

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy ONLY the POM first and pre-fetch dependencies.
COPY pom.xml .
RUN mvn -q dependency:go-offline
# ^ This layer's cache key is the CONTENTS of pom.xml. As long as pom.xml doesn't
#   change, this layer — and the ~100+ MB of downloaded dependency jars it produced
#   — is reused on every rebuild, even if you change source code 50 times in a row.

# Now copy source and build. Editing source invalidates only FROM HERE DOWN.
COPY src ./src
RUN mvn -q clean package -DskipTests
```

Contrast with the naive, slow version:

```dockerfile
# DON'T do this — copies everything (including pom.xml) in one instruction:
COPY . .
RUN mvn -q dependency:go-offline && mvn -q clean package -DskipTests
# Now ANY source file change invalidates this single COPY layer, which
# invalidates the dependency-download step too — every rebuild re-downloads
# every dependency from the internet, even for a one-line code change.
```

In the first version, `pom.xml` rarely changes (only when you add/upgrade a
dependency), so the dependency-download layer stays cached across the vast
majority of rebuilds — only editing `src/` triggers `COPY src ./src` and the
`mvn package` after it to re-run. In the second version, the single `COPY . .`
instruction's cache key includes every file in the context, so touching a single
`.java` file invalidates dependency resolution too, turning every rebuild into a
full from-scratch build. The exact same pattern applies to Node
(`COPY package*.json ./` + `npm ci` *before* `COPY . .`) and any other ecosystem
with a separate manifest and lockfile.

### More cache-ordering rules

- **Combine related `RUN`s with `&&`, and clean up in the *same* layer.** Layers
  are additive — a `rm -rf` in a *later* layer doesn't shrink an *earlier* layer's
  size on disk, it just hides the files. `apt-get update && apt-get install -y
  curl && rm -rf /var/lib/apt/lists/*` in one `RUN` keeps the apt cache out of the
  image entirely; splitting it into three separate `RUN`s bakes the full apt cache
  into a permanent layer even though a later layer deletes it.
- **Put instructions that change often (like `COPY src`) as late as possible.**
  Anything after them re-executes on every single code change; anything before
  them survives unchanged code edits.
- **`ARG`/`ENV` used inside a `RUN` become part of that layer's cache key too** — a
  build-time argument that changes on every build (like a timestamp) placed before
  expensive steps will defeat caching for everything after it. Keep volatile
  `ARG`s as late in the file as their usage allows.

### Why it's useful

On a real Spring Boot or Node project, correct cache ordering turns a 3–5 minute
"re-download the world" rebuild into a 5–10 second "just recompile my changed
source" rebuild during local development and in CI. It's the single highest-impact
Dockerfile optimization, and it costs nothing — it's purely about *instruction
order*, not extra tooling.

## 2.4 — Multi-stage builds

### Definition

A **multi-stage build** uses more than one `FROM` in a single Dockerfile, where each
`FROM` starts a new, independent **stage**. Later stages can selectively copy
specific files out of earlier stages with `COPY --from=<stage>`. The pattern: build
with the full toolchain (compiler, package manager, build cache) in one stage, then
ship **only the compiled artifact** in a slim, separate final stage.

### Worked example — `examples/Dockerfile.springboot`

```dockerfile
# ---- Stage 1: build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests
# This stage's final image would be ~600-800MB: full JDK + Maven + all downloaded
# dependency jars + source. None of that is needed to RUN the app.

# ---- Stage 2: runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app/target/*.jar app.jar
# ^ pulls ONLY the built jar out of the "build" stage by name. Maven, the JDK
#   compiler, source files, and the dependency cache never enter this stage at all.
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

In this Dockerfile, `AS build` and `AS runtime` name the two stages. `FROM
maven:3.9-eclipse-temurin-21` is a "fat" image containing a JDK, Maven, and (after
the `RUN`s) all of the project's dependency jars and compiled classes — everything
needed to *produce* `app.jar`, but nothing of it is needed to *run* `app.jar`. The
second `FROM` starts completely fresh from a minimal `21-jre-alpine` base (a JRE,
not a JDK — no compiler); `COPY --from=build /app/target/*.jar app.jar` reaches
back into the finished first stage and copies out exactly one file. The final image
that actually gets pushed to a registry and deployed has **no Maven, no source
code, and no dependency cache** — only a JRE and the jar.

### Worked example — `examples/Dockerfile.nextjs`

```dockerfile
# ---- deps: install node_modules (cached on lockfile changes) ----
FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json* ./
RUN npm ci

# ---- build: compile the production bundle ----
FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN npm run build   # next.config's output: "standalone" emits a minimal server

# ---- runtime: only the standalone server + static assets ----
FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup -S nodejs && adduser -S nextjs -G nodejs
USER nextjs
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public
EXPOSE 3000
CMD ["node", "server.js"]
```

This example uses **three** stages rather than two: `deps` isolates
`npm ci` into its own cache layer (keyed only on `package.json`/`package-lock.json`,
so editing app code never re-triggers a full dependency reinstall — same cache
principle as 2.3), `build` reuses those installed modules to compile, and `runtime`
copies out only Next.js's `standalone` output (a self-contained server bundle with
just the modules it actually needs) plus static assets. Note that `deps`'
`node_modules` is copied into `build` via `COPY --from=deps`, but `build`'s own
`node_modules`/source never make it into `runtime` at all — only three explicit
`COPY --from=build` lines do.

### Comparison table — single-stage vs multi-stage

| | Single-stage | Multi-stage |
|---|---|---|
| Final image contains | Toolchain + source + artifact | Only the artifact + minimal runtime |
| Typical size (Java example) | ~700–900 MB | ~180–250 MB |
| Attack surface | Compiler, shell utilities, package manager all present | Minimal — no compiler/build tools |
| Build cache reuse | Same principles apply within the one stage | Each stage caches independently |
| Complexity | One `FROM` | Multiple `FROM`s, named with `AS` |

### Why it's useful

Multi-stage builds are the standard, expected technique for any compiled or
bundled language (Java, Go, Next.js, TypeScript, C++) in a production Dockerfile.
They solve size *and* security simultaneously: the shipped image has nothing an
attacker could use to build/modify code, and it's a fraction of the size to pull,
scan, and store — which matters directly for autoscaling latency and registry
storage cost.

## 2.5 — `ARG` vs `ENV`

### Key Concepts

- **`ARG`** defines a variable available **only during the build**, set via
  `docker build --build-arg KEY=value`. It is *not* present in the running
  container's environment unless you explicitly assign it to an `ENV`.
- **`ENV`** defines a variable available **during the build and at container
  runtime** — visible to `RUN` instructions after it's declared, and to the
  process the container eventually runs.
- **Bridging the two:** `ARG VERSION=1.0` then `ENV APP_VERSION=$VERSION` bakes a
  build-time value into a runtime-visible variable.
- **Never put secrets in `ARG` or `ENV`.** Both are recorded permanently in the
  image's layer history and are readable with `docker history` — even a
  build-time-only `ARG` shows up there (see Phase 5, 5.2 for the full security
  treatment).

```dockerfile
ARG APP_VERSION=dev          # build-time only
ENV APP_VERSION=${APP_VERSION}  # now also visible to the running app, e.g. for a /version endpoint
```

```bash
docker build --build-arg APP_VERSION=2.3.1 -t myapp:2.3.1 .
docker run myapp:2.3.1 printenv APP_VERSION
# 2.3.1
```

In this example, `--build-arg` supplies the value at build time; because it's
re-assigned into an `ENV` in the Dockerfile, it's still readable inside the running
container via `printenv`. If the Dockerfile had used `ARG` alone (no matching
`ENV`), `printenv APP_VERSION` inside the container would print nothing — `ARG`
values evaporate once the image is built.

## Perspective

A good Dockerfile is *ordered for cache* and *multi-stage for size/security*. Those
two habits — copy dependency manifests before source, and build in one stage but
ship a slim runtime stage — account for most of the difference between a 900 MB
image that rebuilds slowly and a 150 MB image that rebuilds in seconds. `.dockerignore`
keeps the build context (and therefore the image) free of junk and secrets, and
exec-form `ENTRYPOINT` makes the running process a well-behaved PID 1. Everything
in Phase 5 (non-root user, healthcheck, pinned base, no baked secrets) layers
directly onto this foundation.

## Summary / Key Takeaways

- **`ENTRYPOINT`** = the fixed process; **`CMD`** = its default, overridable
  arguments. Always use **exec form** (`["cmd", "arg"]`) so your process is PID 1
  and receives `SIGTERM` for graceful shutdown.
- **`RUN`** executes at *build* time and bakes a layer; **`CMD`/`ENTRYPOINT`**
  execute at *container start* time — don't confuse the two.
- **Cache order = least- to most-frequently-changing.** Copy dependency manifests
  (`pom.xml`, `package*.json`) and install deps *before* copying source, so editing
  code doesn't re-trigger a full dependency reinstall.
- **Multi-stage builds** ship only the compiled artifact in a minimal runtime
  stage — the standard technique for any compiled/bundled app, and the biggest
  single lever on image size.
- **`.dockerignore`** keeps the build context (and therefore the image) free of
  `node_modules`, `.git`, and — most importantly — secrets like `.env`.
- **`ARG` is build-time only; `ENV` is build- and run-time.** Neither should ever
  hold a secret — both are permanently visible via `docker history`.
