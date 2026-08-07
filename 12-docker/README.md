<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 12 — Docker

**Package an app with everything it needs to run, and run it identically anywhere.**
Docker ends "works on my machine": the image is the app + its runtime + its libraries,
frozen into a portable artifact.

Taught for someone who has built the apps in this repo — the **Spring Boot API (02)**,
the **Next.js app (09)**, backed by **Postgres (04)** and **Redis (15)**. Docker is how
those pieces become deployable and how the **CI/CD (13)** and **Kubernetes (14)** tracks
ship them.

> **Format note:** no Docker daemon is available in this environment, so this track is
> **theory + real, correct-by-construction artifacts** (Dockerfiles, `docker-compose.yml`,
> `.dockerignore`) with the exact commands to run them — the same approach the Databases
> track used for engines it didn't run live. The files are written to real-world standards
> (multi-stage, non-root, healthchecks, layer caching).

## The artifacts: `examples/`

| File | What it shows |
|---|---|
| `Dockerfile.springboot` | **multi-stage** Java build (Maven → JRE), layer-cached deps, non-root, healthcheck |
| `Dockerfile.nextjs` | multi-stage Node build with Next `standalone` output, non-root |
| `docker-compose.yml` | the **full stack**: Spring API + Postgres + Redis, private network, health-gated startup |
| `.dockerignore` | keep the build context small and secrets out |

```bash
# (with Docker installed)
docker build -f examples/Dockerfile.springboot -t expense-api ../02-spring-boot/expense-api
docker run -p 8080:8080 expense-api
docker compose -f examples/docker-compose.yml up   # whole stack
```

## Curriculum

### Phase 1 — Containers & images ✅
- [x] 1.1 What a container is; VMs vs containers; why Docker (the "works on my machine" fix)
- [x] 1.2 Images vs containers; the layered filesystem; registries (Docker Hub)
- [x] 1.3 The core workflow: `pull` / `run` / `ps` / `logs` / `exec` / `stop` / `rm`
- NOTES · INTERVIEW

### Phase 2 — Writing Dockerfiles ✅
- [x] 2.1 Instructions: `FROM`, `WORKDIR`, `COPY`, `RUN`, `ENV`, `EXPOSE`, `CMD`/`ENTRYPOINT`
- [x] 2.2 Layer caching & instruction order (copy deps before source)
- [x] 2.3 Multi-stage builds — small, secure final images
- NOTES · INTERVIEW

### Phase 3 — Running containers ✅
- [x] 3.1 Port publishing `-p`, env `-e`, detached `-d`, naming, restart policies
- [x] 3.2 Persistence: volumes vs bind mounts vs tmpfs (containers are ephemeral)
- [x] 3.3 Networking: bridge networks, container DNS, exposing services
- NOTES · INTERVIEW

### Phase 4 — Docker Compose (multi-service) ✅
- [x] 4.1 The compose file: services, images vs `build`, ports, env
- [x] 4.2 Dependencies & startup order: `depends_on` + healthchecks
- [x] 4.3 The full stack as one command; profiles & overrides
- NOTES · INTERVIEW

### Phase 5 — Production & best practices ✅
- [x] 5.1 Image size: small base images, multi-stage, `.dockerignore`, fewer layers
- [x] 5.2 Security: non-root user, minimal base, pinned tags, no secrets in images/layers
- [x] 5.3 Runtime: healthchecks, graceful shutdown (PID 1/signals), logging, resource limits
- NOTES · INTERVIEW

### Capstone ✅
- [x] Containerize the Spring capstone and compose the whole expense stack (API + Postgres
  + Redis) — the bridge to CI/CD (13) and Kubernetes (14). See `CAPSTONE.md`.

## How this connects

- **← Spring Boot (02) / Next.js (09):** the apps we built are what gets containerized.
- **← Databases (04) / Redis (15):** run as official images in the compose stack.
- **→ CI/CD (13):** the pipeline builds and pushes these images.
- **→ Kubernetes (14):** K8s runs these images at scale; a container is the unit K8s
  schedules.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · containers images** — [Notes](phase-1-containers-images/NOTES.md) · [Interview](phase-1-containers-images/INTERVIEW.md)
- **Phase 2 · dockerfiles** — [Notes](phase-2-dockerfiles/NOTES.md) · [Interview](phase-2-dockerfiles/INTERVIEW.md)
- **Phase 3 · running containers** — [Notes](phase-3-running-containers/NOTES.md) · [Interview](phase-3-running-containers/INTERVIEW.md)
- **Phase 4 · compose** — [Notes](phase-4-compose/NOTES.md) · [Interview](phase-4-compose/INTERVIEW.md)
- **Phase 5 · production** — [Notes](phase-5-production/NOTES.md) · [Interview](phase-5-production/INTERVIEW.md)
<!-- /phases-nav -->
