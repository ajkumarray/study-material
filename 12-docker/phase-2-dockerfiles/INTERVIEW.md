<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · containers images](../phase-1-containers-images/NOTES.md) | [Phase 3 · running containers ➡](../phase-3-running-containers/NOTES.md)
<!-- /nav -->

# Phase 2 — Writing Dockerfiles: Interview Q&A

⭐ = asked constantly.

**Q: What is a Dockerfile?** ⭐
A plain-text recipe of instructions (`FROM`, `WORKDIR`, `COPY`, `RUN`, `ENV`,
`ENTRYPOINT`/`CMD`, …) that the Docker daemon executes in order to build an image.
Almost every instruction produces one cached, content-addressed layer stacked on
the one before it. Because it's a text file, it's version-controllable and code
reviewable — the exact same argument for infrastructure-as-code applied to image
builds.

**Q: `ENTRYPOINT` vs `CMD` — what's the difference, and how do they combine?** ⭐⭐
`ENTRYPOINT` is the fixed executable that always runs when the container starts.
`CMD` supplies default *arguments* to that executable (or, if there's no
`ENTRYPOINT` at all, the entire default command) — and unlike `ENTRYPOINT`, `CMD`
can be overridden by arguments appended to `docker run`. A common combined pattern
is `ENTRYPOINT ["docker-entrypoint.sh"]` with `CMD ["nginx", "-g", "daemon
off;"]`: the entrypoint script always runs (e.g., to do setup), and its default
argument is `nginx -g daemon off;`, which a caller can swap out for something else
without touching the entrypoint. In this repo's Spring Boot Dockerfile, there's
just a single `ENTRYPOINT ["java", "-jar", "app.jar"]` and no `CMD` — the jar
always runs, full stop, no overridable arguments needed.

*Follow-up: why does exec form matter here specifically?* Because `ENTRYPOINT
["java", "-jar", "app.jar"]` (JSON array = exec form) runs `java` directly as PID
1 inside the container, so it receives `SIGTERM` from `docker stop` and can drain
in-flight requests and shut down cleanly. Written as shell form
(`ENTRYPOINT java -jar app.jar`), Docker silently wraps it as `/bin/sh -c "java
-jar app.jar"` — the shell becomes PID 1 instead, and signals sent to the
container may never reach the JVM, so it just gets force-killed after the grace
period with no graceful shutdown.

**Q: How does Docker's build cache work, and how do you exploit it?** ⭐⭐
Each instruction produces a layer that's cached and reused on the next build *if
that instruction and its inputs are byte-identical to last time*. The moment one
instruction's cache is invalidated, every instruction stacked after it in the file
rebuilds from scratch too, regardless of whether their own inputs actually
changed — because each layer's cache key is partly derived from the layer below
it. The exploit is instruction ordering: put what changes rarely (installing
dependencies from a manifest file) *before* what changes on every commit (the
application source). In `examples/Dockerfile.springboot`, `COPY pom.xml .` +
`RUN mvn dependency:go-offline` come before `COPY src ./src` — so as long as
`pom.xml` is untouched, the (slow, network-bound) dependency download layer is
reused on every rebuild, and only the fast recompile step re-runs.

*Follow-up: what happens if you write `COPY . .` before installing dependencies
instead?* Any source file change invalidates that single `COPY` layer's cache,
which invalidates the dependency-install step stacked after it too — every
rebuild re-downloads every dependency from the network, even for a one-line code
change. This is one of the most common real-world Dockerfile performance bugs.

**Q: What is a multi-stage build, and why use one?** ⭐⭐
A Dockerfile with more than one `FROM`, where each `FROM` starts an independently
named stage (`FROM maven:3.9-eclipse-temurin-21 AS build`), and a later stage can
selectively pull files out of an earlier one with `COPY --from=<stage>`. The
standard pattern: build with the full toolchain in one "fat" stage, then
`COPY --from=build` only the compiled artifact into a slim, separate final stage.
In this repo's Spring Boot Dockerfile, the `build` stage has the full JDK, Maven,
and every downloaded dependency jar; the `runtime` stage starts fresh from
`eclipse-temurin:21-jre-alpine` (a JRE only, no compiler) and copies in exactly
one file — `app.jar`. The image that actually gets pushed and deployed has no
Maven, no source, and no dependency cache: dramatically smaller (hundreds of MB
saved) and a much smaller attack surface, since there's no compiler or build
tooling sitting in a production image for an attacker to abuse.

*Follow-up: can a stage reference an earlier stage without naming it?* Yes, by
index (`COPY --from=0 ...`), but naming stages with `AS <name>` is strongly
preferred — it's self-documenting and stays correct if you reorder or insert
stages later.

**Q: Why copy `package.json`/`pom.xml` before the rest of the source?** ⭐⭐
So the dependency-install step lands in its own cache layer keyed only on the
manifest/lockfile — it only re-runs when dependencies actually change, not on
every source edit. This is the concrete instance of the general caching rule
above, and it's the single highest-value Dockerfile change most people are
missing: without it, a one-line code fix triggers a full `npm ci`/`mvn
dependency:go-offline` from the network on every rebuild.

**Q: What is `.dockerignore` for?** ⭐
It excludes paths from the **build context** — everything `docker build` sends to
the daemon and everything `COPY`/`ADD` can reach — using `.gitignore`-style
patterns. This repo's `examples/.dockerignore` excludes `node_modules`, `.next`,
`dist`, `target`, `.git`, `.env`/`.env.*`, `*.log`, and the Dockerfiles/compose
files themselves. It matters for three separate reasons: build speed (smaller
context uploads faster), correctness (a stray `COPY . .` can't drag in a
host-built `node_modules` that doesn't match the container's OS/arch), and
security (a `.env` file with real secrets sitting in the project directory can
never accidentally end up copied into an image layer).

**Q: Why run the process in exec form / as PID 1?** ⭐⭐
Exec form (`["java", "-jar", "app.jar"]`) makes your application PID 1 inside the
container, so it directly receives `SIGTERM` when `docker stop` runs (or a
Kubernetes pod is evicted), letting it drain in-flight requests and release
resources before exiting cleanly. Shell form (`java -jar app.jar` without
brackets) is silently wrapped by Docker in `/bin/sh -c "..."`, which becomes PID
1 instead; many shells don't forward signals to their child processes by
default, so the app never sees the `SIGTERM`, the grace period elapses, and
Docker sends `SIGKILL` — an abrupt kill with no cleanup, potentially dropping
in-flight requests or leaving a transaction half-committed.

**Q: How do you reduce Docker image size?** ⭐⭐
In order of impact: (1) multi-stage builds so the shipped image excludes
compilers/toolchains/source; (2) a small base image — `alpine`, a `-slim`
variant, or distroless (no shell or package manager at all) — the example uses
`eclipse-temurin:21-jre-alpine` (JRE, not JDK) for exactly this reason; (3)
`.dockerignore` to keep junk out of the context in the first place; (4) combining
related `RUN` commands and cleaning up caches in the *same* layer, since layers
are additive and a later `rm` doesn't shrink an earlier layer's size on disk.
Smaller images pull faster (which matters directly for autoscaling and rolling
deploys) and present a smaller surface for vulnerability scanners to flag.

**Q: `ARG` vs `ENV` — what's the difference?**
`ARG` defines a build-time-only variable, supplied via `docker build --build-arg
KEY=value`; it's gone once the image is built and isn't visible to the running
container. `ENV` defines a variable visible both during the build (to later `RUN`
instructions) and at container runtime (to the process that eventually runs). You
can bridge them (`ARG VERSION` then `ENV APP_VERSION=$VERSION`) to bake a
build-time value into something the running app can read. Neither should ever
hold a secret — both are permanently recorded in the image's layer history and
readable via `docker history`, even an `ARG` that's never assigned to an `ENV`.

**Q: `COPY` vs `ADD`?**
Both copy files from the build context into the image. `ADD` additionally
auto-extracts local `.tar`/`.tar.gz` archives into the destination and can fetch
a remote URL directly. The general guidance is to prefer `COPY` for everything
except those two specific cases, because `COPY`'s behavior is simpler and more
predictable — `ADD`'s implicit extraction/fetching has surprised people (e.g.,
silently re-extracting an archive on every build because its cache invalidation
behaves differently than a plain file copy).

**Q: What's the difference between `RUN` and `CMD`/`ENTRYPOINT` conceptually?**
`RUN` executes a command *while the image is being built* and its result (files
created, packages installed) becomes a permanent layer in the image — it never
runs again once the image exists. `CMD`/`ENTRYPOINT` define what runs *every time
a container is started* from the finished image; they don't execute at all during
`docker build`. Confusing the two is a common early mistake — e.g. trying to
`RUN java -jar app.jar` to "start the app" inside a Dockerfile, which would just
run the app once during the build and then discard the result.
