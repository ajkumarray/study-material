<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · running containers](../phase-3-running-containers/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Docker Compose: Notes

Real apps are **multiple containers** — an API, a database, a cache — that need to
be built/pulled, networked together, started in a sane order, and configured
consistently. **Docker Compose** describes that whole stack declaratively in one
YAML file and stands it up (or tears it down) with a single command. This phase
walks the real stack in `examples/docker-compose.yml`: the Spring Boot API, its
Postgres database, and a Redis cache.

## 4.1 — Anatomy of a compose file

```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: expenses
      POSTGRES_USER: app
      POSTGRES_PASSWORD: secret
    ports:
      - "5432:5432"
    volumes:
      - db-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d expenses"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  api:
    build:
      context: ../../02-spring-boot/expense-api
      dockerfile: ../../12-docker/examples/Dockerfile.springboot
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/expenses
      SPRING_DATASOURCE_USERNAME: app
      SPRING_DATASOURCE_PASSWORD: secret
      SPRING_DATA_REDIS_HOST: redis
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    restart: unless-stopped

volumes:
  db-data:
```

### Key Concepts

- **`services:`** is a map, one entry per container. Every top-level key
  (`db`, `redis`, `api`) becomes both the container's Compose-managed name *and*
  its DNS hostname on the stack's private network (4.2).
- **`image:` vs `build:`.** `db` and `redis` use `image:` — pull a prebuilt image
  straight from a registry, no local build step. `api` uses `build:` — a
  `context` (the directory sent as the build context, here reaching up to
  `02-spring-boot/expense-api`) and an explicit `dockerfile` path (because this
  repo's Dockerfile lives in `12-docker/examples/`, not alongside the app source).
  A real stack typically mixes both: build your own services, pull third-party
  ones (databases, caches, proxies).
- **`environment:`** sets environment variables inside that service's container —
  the same mechanism as `docker run -e` (Phase 3), just declared in YAML instead
  of typed on a command line.
- **`ports:`** is a list of `"host:container"` mappings — the same semantics as
  `docker run -p`.
- **`volumes:`** (service-level) mounts storage into that service; **`volumes:`**
  (top-level, at the bottom of the file) *declares* named volumes Compose manages
  for the whole stack — `db-data` here, mounted at Postgres's data directory so
  it persists across container recreation.
- **`depends_on:`** and **`healthcheck:`** control startup ordering — covered in
  depth in 4.2, since getting this wrong is the single most common Compose bug.
- **`restart:`** is the same restart-policy semantics from Phase 3, declared in
  YAML.

### One command instead of five manual ones

```bash
docker compose up            # build/pull everything, create the network, start all services
docker compose up -d         # same, but detached
docker compose ps            # list this stack's services and their status/health
docker compose logs -f api   # follow just the "api" service's logs
docker compose down          # stop and remove containers + the network (volumes kept)
docker compose down -v       # also remove named volumes (⚠ deletes DB data)
```

Without Compose, standing up this exact stack by hand would mean: `docker network
create`, three separate `docker run` commands with matching `--network` and
`-e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/...` flags typed by hand, and
manually sequencing which container starts first. `docker compose up` reads the
YAML once and does all of that — network creation, image build/pull, container
creation with the right env/ports/volumes, and startup in dependency order — in
one command, and `docker compose down` reverses all of it just as cleanly.

### Why it's useful

This is the core developer-experience win: a new teammate clones the repo, runs
`docker compose up`, and has the API, database, and cache running and wired
together correctly — no README steps to follow, no chance of a typo'd `--network`
flag. It's also how CI environments and small production deployments stand up a
full stack reproducibly from source control.

## 4.2 — Startup order: `depends_on` and healthchecks

### The problem

`depends_on: [db, redis]` alone only controls **start order** — Compose starts
`db` and `redis`'s containers *before* `api`'s. But "container started" is not
the same as "ready to accept connections": Postgres's process can be running for
several seconds before it's actually accepting connections on port 5432. If
`api` boots the instant its container starts (which is what bare `depends_on`
guarantees), it can race the database and crash on startup with a connection
refused error — intermittently, which makes it an especially nasty bug to debug.

### The fix — `healthcheck` + `condition: service_healthy`

```yaml
db:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U app -d expenses"]
    interval: 5s
    timeout: 3s
    retries: 5

api:
  depends_on:
    db:
      condition: service_healthy   # not just "started" — actually HEALTHY
    redis:
      condition: service_healthy
```

- A **healthcheck** runs a command *inside* the service's container on a
  schedule (`interval`) and marks the service `healthy`/`unhealthy` based on its
  exit code (`0` = healthy), with `retries` consecutive failures required before
  it's declared unhealthy and `timeout` bounding how long one check attempt may
  take. `pg_isready` is Postgres's own readiness-check utility; `redis-cli ping`
  is Redis's.
- `depends_on: { db: { condition: service_healthy } }` upgrades the dependency
  from "wait for the container process to start" to "wait for the healthcheck to
  report healthy" — Compose won't create the `api` container until `db` and
  `redis` both report healthy.
- **Even this is best-effort, not a hard guarantee** for everything that could go
  wrong after startup (a database that becomes briefly unreachable due to a
  network blip, a restart mid-runtime). Applications should *still* be resilient
  to a dependency being briefly unavailable — connection-pool retries with
  backoff — rather than relying on orchestration ordering alone to guarantee
  every single request-time connection succeeds (System Design, reliability
  patterns).

```bash
docker compose up -d
docker compose ps
# NAME              STATUS
# expense-db-1      Up 12 seconds (healthy)
# expense-redis-1   Up 12 seconds (healthy)
# expense-api-1     Up 6 seconds
# ^ api only started AFTER db/redis reported "healthy", not merely "Up"
```

In this example, `docker compose ps` shows `(healthy)` next to `db` and `redis` —
that annotation only appears because they have `healthcheck:` blocks. `api`
started six seconds after the others because Compose held it back until both
health conditions were satisfied, not merely until their containers existed.

### Why it's useful

This is the difference between a stack that starts reliably every single time and
one that flakily crash-loops on a fresh machine or in CI (where the DB is always
"colder" than on a developer's already-warm laptop). It's a very commonly probed
interview topic precisely because "just use `depends_on`" is the naive, incomplete
answer everyone gives first.

## 4.3 — Networking in Compose

Compose automatically creates a **private, user-defined bridge network** for the
stack (Phase 3, 3.4) and attaches every service to it. Each service's YAML key
becomes its **DNS hostname** on that network — this is the direct application of
what Phase 3 covered manually with `docker network create`.

```yaml
environment:
  # "db" and "redis" resolve via Docker's embedded DNS — no IP anywhere.
  SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/expenses
  SPRING_DATA_REDIS_HOST: redis
```

The Spring API's connection strings reference `db` and `redis` by name, not by
IP — because Compose put all three services on the same private network where
those names resolve automatically. Only `ports:` entries are published to the
host; `db`'s `5432:5432` and `redis`'s `6379:6379` in the example file are
published mainly so a developer's local database client can connect directly for
debugging — the `api` container itself reaches them over the private network
without needing those publishes at all.

### Why it's useful

You never hand-manage IPs or a custom `/etc/hosts` for a multi-container stack —
Compose gives every service a stable, predictable hostname for free, which is
exactly the abstraction Kubernetes Services provide at cluster scale later.

## 4.4 — Config variations: `.env`, profiles, and override files

### Key Concepts

- **A `.env` file** in the same directory as the compose file is automatically
  loaded and can supply values for `${VARIABLE}` substitution inside the YAML
  itself (e.g., `image: myapp:${TAG}`), keeping environment-specific values out
  of the committed compose file.
- **`env_file:`** (service-level) loads a file's contents as that service's
  environment variables — the Compose equivalent of `docker run --env-file`, and
  a cleaner alternative to a long inline `environment:` block for many values.
- **Profiles** (`docker compose --profile tools up`) let a compose file define
  services that are only started when their profile is explicitly requested —
  e.g., an optional `pgAdmin` service tagged `profiles: [tools]` that most
  developers don't need running by default.
- **Override files** layer environment-specific config on top of a shared base
  without duplicating it: `docker-compose.override.yml` is merged automatically
  with `docker-compose.yml` when you just run `docker compose up`; explicit
  layering with `docker compose -f docker-compose.yml -f docker-compose.prod.yml
  up` merges a base file with a named override (later files' keys win/merge).
  A common pattern: the base file defines the shared shape of each service, a
  `dev` override adds bind mounts for live-reload, and a `prod` override swaps
  in resource limits and a `restart: always` policy.

```yaml
# docker-compose.prod.yml (an override merged on top of the base file)
services:
  api:
    restart: always
    deploy:
      resources:
        limits:
          memory: 512M
```

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

In this example, the base `docker-compose.yml` (4.1) stays environment-agnostic;
`docker-compose.prod.yml` only adds the production-specific `restart` policy and
memory limit, and Compose merges the two files' `api` service definitions at
startup — no duplication of the shared `build`/`environment`/`ports` config
between the base and override files.

### Why it's useful

This is how one compose file (or a small family of them) serves local dev, CI,
and small production deployments without hand-maintaining three divergent copies
of the same stack definition — you change only what actually differs.

## 4.5 — When Compose is (and isn't) enough

Compose orchestrates containers on a **single Docker host**. That's exactly right
for local development, CI test environments, and small single-machine
production deployments. It does not provide: scheduling across multiple
machines, automatic horizontal scaling in response to load, rolling updates with
health-gated rollback, or self-healing beyond a single host's restart policies.
When those become requirements, the natural next step is **Kubernetes**
(Phase 14) — and the compose file is often the conceptual first draft of the
K8s manifests: `services:` → Deployments + Services, `depends_on`/`healthcheck` →
readiness/liveness probes, top-level `volumes:` → PersistentVolumeClaims.

## Perspective

Compose turns "start three containers in the right order, on a shared network,
with the right environment and healthchecks" into one declarative YAML file and
one command. It's the everyday tool for local development and testing of
multi-service systems (and for small production deployments). Master it as the
deliberate step between running single containers by hand (Phase 3) and
orchestrating a fleet of them across a cluster with Kubernetes (Phase 14) — the
concepts (services, networks, config, health) carry over directly; only the
scale changes.

## Summary / Key Takeaways

- Compose defines a whole multi-container stack — services, networks, volumes,
  config — in one YAML file; `docker compose up`/`down` bring it fully up/down.
- **`image:`** pulls a prebuilt image; **`build:`** builds from a local
  Dockerfile/context. Real stacks mix both.
- **`depends_on` alone only waits for a container to *start*, not to be
  *ready*.** Pair it with a `healthcheck` and `condition: service_healthy` to
  actually wait for readiness — and still build retry resilience into the app
  itself.
- Compose auto-creates a private network where **each service name is a DNS
  hostname** — no IPs, no manual network wiring.
- **Named volumes** (declared top-level) persist data across `up`/`down` cycles;
  `down -v` is the one command that also deletes them.
- Compose is **single-host**; when you need multi-node scheduling, autoscaling,
  and self-healing across machines, that's the move to Kubernetes (Phase 14).
