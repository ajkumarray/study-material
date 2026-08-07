# Capstone — Containerize the Expense Stack

The Docker track's synthesis: take the repo's **Spring Boot API** and run it — plus its
**Postgres** database and **Redis** cache — as one containerized stack, applying every
best practice from Phases 1–5. All artifacts are in `examples/`.

> No Docker daemon runs in this environment, so the artifacts are correct-by-construction
> and shown with the exact commands. They're written to production standards.

## The artifacts

- **`Dockerfile.springboot`** — multi-stage (Maven build → JRE runtime), dependency-layer
  caching, **non-root** user, **healthcheck** on `/actuator/health`, exec-form entrypoint.
- **`Dockerfile.nextjs`** — multi-stage Node build using Next `standalone` output, non-root.
- **`docker-compose.yml`** — API + Postgres + Redis on a private network, **health-gated**
  startup, persistent DB volume.
- **`.dockerignore`** — lean build context, no secrets.

## Run it (with Docker installed)

```bash
cd 12-docker/examples

# Build & run just the API image
docker build -f Dockerfile.springboot -t expense-api:1.0 ../../02-spring-boot/expense-api
docker run -p 8080:8080 expense-api:1.0

# Or bring up the whole stack (API + Postgres + Redis) with one command
docker compose up            # add -d to detach
docker compose ps            # see health status
docker compose down -v       # stop and remove (incl. volumes)
```

## Every phase, applied

| Phase | Concept | Where |
|---|---|---|
| 1 | Images vs containers, registries | official `postgres`/`redis` images pulled; app image built |
| 2 | Multi-stage + layer caching | `Dockerfile.springboot` (pom before src; build → jre stage) |
| 3 | Ports, volumes, networking | `-p 8080:8080`; `db-data` volume; service-name DNS (`db`, `redis`) |
| 4 | Compose + health-gated deps | `docker-compose.yml` — `depends_on: service_healthy` |
| 5 | Production hardening | non-root, healthcheck, exec entrypoint, `.dockerignore`, pinned tags |

## The full-stack picture (repo-wide)

```
        ┌─────────────┐   service DNS   ┌──────────────┐
        │  Next.js /  │ ───────────────▶│  Spring API  │
        │  React app  │   (or BFF)      │  (container) │
        └─────────────┘                 └──────┬───────┘
                                    ┌───────────┴───────────┐
                              ┌─────▼─────┐           ┌──────▼──────┐
                              │ Postgres  │           │    Redis    │
                              │(container)│           │ (container) │
                              └───────────┘           └─────────────┘
                            (named volume:            (cache / sessions —
                             durable data)             Redis track 15)
```

Every box is something built earlier in this repo, now packaged as an image and wired by
Compose.

## What's next

- **CI/CD (13):** a pipeline builds these images, runs the tests, and pushes to a registry
  on every commit.
- **Kubernetes (14):** the same images become Deployments/Services/StatefulSets, scaled and
  self-healed across a cluster. This compose file is the conceptual first draft of those
  manifests.
