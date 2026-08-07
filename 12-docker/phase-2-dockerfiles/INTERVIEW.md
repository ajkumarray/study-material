<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · containers images](../phase-1-containers-images/NOTES.md) | [Phase 3 · running containers ➡](../phase-3-running-containers/NOTES.md)
<!-- /nav -->

# Phase 2 — Writing Dockerfiles: Interview Q&A

⭐ = asked constantly.

**Q: What is a Dockerfile?** ⭐
A text recipe of instructions (`FROM`, `COPY`, `RUN`, `CMD`, …) that Docker executes to
build an image. Each instruction produces a cached layer. It makes image builds
reproducible and version-controllable.

**Q: `ENTRYPOINT` vs `CMD`?** ⭐⭐
`ENTRYPOINT` sets the fixed executable; `CMD` provides default arguments that can be
overridden at `docker run`. Together, `ENTRYPOINT ["java","-jar","app.jar"]` runs the app,
and `CMD` could supply default flags. Use the exec (JSON) form so the process is PID 1 and
gets signals.

**Q: How does Docker's build cache work and how do you exploit it?** ⭐⭐
Each instruction is a layer, cached and reused if unchanged; the first changed instruction
invalidates all layers after it. So order instructions least- to most-frequently-changing
and copy dependency manifests before source (e.g. `pom.xml` + fetch deps before `COPY
src`), so code edits don't re-run dependency installs.

**Q: What is a multi-stage build and why use it?** ⭐⭐
Multiple `FROM` stages in one Dockerfile: build with the full toolchain in one stage, then
`COPY --from=build` only the artifact into a slim runtime stage. The final image excludes
compilers/source/caches — much smaller and more secure. Standard for compiled apps (Java
jar, Next standalone).

**Q: Why copy `package.json`/`pom.xml` before the rest of the source?** ⭐
So dependency installation lands in its own cached layer that only invalidates when
dependencies change. Editing application code then reuses the cached deps layer instead of
re-downloading everything — dramatically faster rebuilds.

**Q: What is `.dockerignore` for?** ⭐
It excludes files from the build context sent to the daemon (`node_modules`, `.git`,
`.env`, build outputs). This speeds builds, shrinks images, and prevents secrets or junk
from being copied in via `COPY . .`.

**Q: Why run the process in exec form / as PID 1?** *nuance*
Exec form (`["java","-jar","app.jar"]`) makes your app PID 1, so it directly receives
SIGTERM on `docker stop` and can shut down gracefully. Shell form runs it under `/bin/sh`,
which may not forward signals, causing abrupt kills and skipped cleanup.

**Q: How do you reduce image size?**
Use a small base (alpine/slim/distroless), multi-stage builds (ship only the artifact),
`.dockerignore`, combine and clean up in the same `RUN` layer, and avoid installing build
tools in the runtime stage. Smaller images pull faster and have less attack surface.
