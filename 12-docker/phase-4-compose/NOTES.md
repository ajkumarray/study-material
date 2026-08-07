<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · running containers](../phase-3-running-containers/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Docker Compose: Notes

Real apps are **multiple containers** — API + database + cache. **Docker Compose** defines
the whole stack in one YAML file and runs it with a single command, on a shared network.
See `examples/docker-compose.yml` (the full expense stack).

## 4.1 — The compose file

- A `services:` map, one entry per container. Each service uses either a prebuilt
  **`image:`** (Postgres, Redis) or a **`build:`** context (our Spring API from its
  Dockerfile). Add `ports`, `environment`, `volumes`, `depends_on`.
- **One command:** `docker compose up` builds/pulls everything, creates a **private
  network**, and starts all services; `docker compose down` tears it all down (`-v` also
  removes volumes). No manual `docker run` per container, no hand-wiring networks.
- Compose auto-creates a network where **service names are DNS hostnames** — the API's
  `SPRING_DATASOURCE_URL` points at host `db`, and Redis at host `redis`. This is the
  networking from Phase 3, declared instead of scripted.

## 4.2 — Dependencies & startup order

- **`depends_on`** controls start **order**, but by default only waits for a container to
  *start*, not to be *ready* (Postgres accepts connections seconds after the process
  launches). A dependent app can crash on boot racing an unready DB.
- The fix (in the example): **healthchecks** + `depends_on: { condition:
  service_healthy }`. Postgres has a `pg_isready` healthcheck and Redis a `redis-cli
  ping`; the API waits until both report **healthy** before starting. This is the correct
  way to sequence a stack.
- Apps should *also* be resilient to a dependency being briefly unavailable (retries /
  connection-pool waits — System Design Phase 6) — orchestration ordering is best-effort,
  not a guarantee.

## 4.3 — The full stack & variations

- The example composes **API + Postgres + Redis** with a persistent volume for the DB —
  the entire backend of this repo bootable with `docker compose up`. It's the developer-
  experience win: a new teammate runs one command and has the whole system.
- **Profiles** (`--profile`) toggle optional services (e.g. a `tools` profile with pgAdmin
  only when needed). **Override files** (`docker-compose.override.yml`,
  `-f base.yml -f prod.yml`) layer environment-specific config (dev bind-mounts vs prod
  images) without duplicating the base.
- Compose is for **single-host** orchestration (dev, CI, small deployments). For
  multi-host, scaling, self-healing production, you graduate to **Kubernetes (14)** — but
  the compose file is often the conceptual first draft of the K8s manifests.

## Perspective

Compose turns "start five containers in the right order, on a network, with the right
env" into a declarative file and one command. It's the everyday tool for local
development and testing of multi-service systems (and small prod). Master it as the
step between running single containers (Phase 3) and orchestrating a fleet with Kubernetes
(14) — same concepts (services, networks, config, health), different scale.
