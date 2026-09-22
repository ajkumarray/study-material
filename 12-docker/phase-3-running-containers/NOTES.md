<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · dockerfiles](../phase-2-dockerfiles/NOTES.md) | [Phase 4 · compose ➡](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 3 — Running Containers: Notes

Building an image is half the job; running it well is the other half — ports,
persistence, config, and networking. Containers are **ephemeral and isolated by
default**, so every connection to the outside world (a client hitting your API, a
config value, durable data) has to be explicitly wired up. This phase covers the
`docker run` flags and concepts you need to run a container the way it'll actually
be run in production.

## 3.1 — `docker run` essentials

```bash
docker run -d --name api -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod \
  --restart unless-stopped --memory 512m --cpus 1 \
  expense-api:1.0
```

### Key Concepts

- **`-d` (detached)** runs the container in the background and prints its ID;
  without it, `docker run` attaches to the container's stdout/stderr in the
  foreground and blocks until it exits (`Ctrl+C` stops it).
- **`-p host:container`** *publishes* a port — maps host port `8080` to the
  container's port `8080`. Without `-p`, the container's ports exist only inside
  its own network namespace and are unreachable from the host or outside network,
  even if the app inside is listening.
- **`-e KEY=val`** (repeatable) or **`--env-file path`** injects configuration as
  environment variables — the standard ("12-factor") way to configure an image
  without rebuilding it. The same built image runs in dev/staging/prod with
  different `-e`/`--env-file` values.
- **`--name`** gives the container a stable, human-readable name to reference in
  later commands (`docker logs api`, `docker stop api`) instead of its
  auto-generated ID.
- **`--restart`** sets the restart policy — see 3.7.
- **`--memory` / `--cpus`** set cgroup-backed resource limits so one container
  can't starve the host or its neighbors — see 3.6.

### Worked example

```bash
docker run -d --name api -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod \
  --restart unless-stopped expense-api:1.0
# 9f3a1c7e2b4d...

curl -s http://localhost:8080/actuator/health
# {"status":"UP"}

docker logs -f api
# ... Started ExpenseApiApplication in 2.913 seconds ...
```

In this example, `-d` starts the container detached; `-p 8080:8080` makes the JVM
inside — which is listening on `8080` in its own network namespace — reachable at
`localhost:8080` on the host; `-e SPRING_PROFILES_ACTIVE=prod` tells the same
`expense-api:1.0` image (no rebuild) to boot with production configuration; and
`--restart unless-stopped` means if the process crashes, Docker restarts it
automatically. `curl` against the published port proves the mapping works end to
end, and `docker logs -f` follows the container's stdout live.

### Why it's useful

Every one of these flags externalizes something that would otherwise require
rebuilding the image: which port is reachable, what config the app boots with,
how many restarts it gets, how much CPU/memory it may use. This is exactly the
model orchestrators like Kubernetes formalize (Deployment env vars, Service port
mappings, resource requests/limits) — learning it here at the `docker run` level
makes those concepts immediately familiar later.

## 3.2 — Configuring containers without rebuilding

### Key Concepts

- **Environment variables are the primary config channel.** `-e KEY=val` for one
  value, `--env-file .env.prod` for many at once (a plain `KEY=value` per line
  file, not committed to source control if it holds secrets).
- **The 12-factor principle:** config that varies between environments (DB URLs,
  feature flags, log levels) belongs in the environment, not baked into the image
  or a config file shipped inside it. The exact same image artifact is promoted
  from staging to production; only its environment changes.
- **Secrets are a special case of config** that should come from a dedicated
  secrets manager or orchestrator secret store (Vault, AWS Secrets Manager,
  Kubernetes Secrets) mounted or injected at runtime — never hardcoded in a
  `-e` flag in a shell history or committed `.env` file, and never baked into the
  image at build time (Phase 5, 5.2).

```bash
# many values from a file, none of them hardcoded in the command itself:
docker run -d --name api -p 8080:8080 --env-file .env.prod expense-api:1.0
```

```
# .env.prod  (not committed to git)
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://db.prod.internal:5432/expenses
LOG_LEVEL=WARN
```

In this example, `--env-file` loads every `KEY=value` line as an environment
variable inside the container — the same `expense-api:1.0` image that ran with
`SPRING_PROFILES_ACTIVE=prod` set directly via `-e` above now runs against a
production database host, without a single line of the Dockerfile or image
changing.

### Why it's useful

This is what makes "build once, deploy everywhere" actually work in practice: CI
builds and pushes one image per release; every environment that runs it supplies
its own environment variables at `docker run`/orchestrator-deploy time. If config
were baked into the image, you'd need a separate image build per environment,
defeating the whole point of an immutable, tested artifact.

## 3.3 — Persistence: volumes, bind mounts, and tmpfs

### Definition

Containers are **ephemeral**: when a container is removed, its writable layer —
and anything written into it — is gone permanently (Phase 1, 1.2). To keep data
that must outlive a container, you mount storage from *outside* the container's
own filesystem.

### Key Concepts

- **Named volume** (`-v db-data:/var/lib/postgresql/data`) — storage that Docker
  creates and manages (typically under `/var/lib/docker/volumes/` on the host),
  identified by name rather than a host path. It **survives** container removal
  and recreation, and is the right choice for databases and any durable
  application state. Portable (doesn't depend on host directory layout) and
  straightforward to back up (`docker run --rm -v db-data:/data -v
  $(pwd):/backup alpine tar czf /backup/db-data.tgz /data`).
- **Bind mount** (`-v $(pwd)/src:/app/src`) — maps a specific **host path**
  directly into the container. Ideal for local development: edit a file on the
  host in your editor, see the change immediately inside the running container
  (e.g., with a framework's hot-reload). Less suitable for production, since it
  couples the container to the host machine's exact filesystem layout.
- **`tmpfs` mount** (`--tmpfs /app/tmp`) — an in-memory-only filesystem, never
  written to disk, wiped the instant the container stops. Useful for scratch
  space or short-lived secrets that must never persist to disk.
- **Rule of thumb: stateless containers, state in volumes.** Treat the container
  itself as disposable — kill it, recreate it from the same image, and expect
  identical behavior — while durable data lives in a volume that outlives any
  individual container instance.

```bash
# Named volume — data survives even if the container is removed and recreated:
docker run -d --name db -v db-data:/var/lib/postgresql/data postgres:16-alpine
docker rm -f db
docker run -d --name db -v db-data:/var/lib/postgresql/data postgres:16-alpine
# ^ same volume re-attached — all previously written rows are still there.

# Bind mount — for local dev, live-editing source on the host:
docker run -d --name web -v $(pwd)/src:/app/src -p 3000:3000 node-dev-server
```

In the first block, removing and recreating the `db` container does **not** lose
data, because the data was never in the container's writable layer to begin with
— it lived in the `db-data` named volume the whole time, which Docker keeps
independently of any specific container. In the second block, `$(pwd)/src` on the
host is mounted directly at `/app/src` inside the container, so saving a file in
your editor is instantly visible to the process running inside the container.

### Comparison table — volumes vs bind mounts vs tmpfs

| | Named volume | Bind mount | tmpfs |
|---|---|---|---|
| Managed by | Docker | You (a host path) | Docker (in-memory) |
| Survives container removal | Yes | Yes (it's a host directory) | No — gone on container stop |
| Portable across hosts | Yes | No (tied to host path/layout) | N/A |
| Typical use | Databases, durable app state | Local dev live-reload, sharing host config into a container | Scratch space, in-memory secrets |
| Backed by disk | Yes | Yes | No (RAM) |

### Why it's useful

Getting this choice right is the difference between a database container that
safely survives a routine `docker compose down && docker compose up` and one that
silently loses all its data. It's also directly reused by Compose (Phase 4, which
declares named volumes the same way) and by Kubernetes PersistentVolumes later.

## 3.4 — Networking

### Definition

Each container gets its own **network namespace** (Phase 1) with its own virtual
network interface, IP address, and routing table, isolated from the host and
other containers by default. Docker provides several **network drivers** to
control how containers reach each other and the outside world.

### Key Concepts

- **Default bridge network.** Without any network configuration, `docker run`
  attaches a container to Docker's default `bridge` network. Containers on it get
  an internal IP and *can* reach the outside internet, but — importantly — cannot
  resolve each other by name (no built-in DNS on the default bridge), only by IP,
  which changes on every restart. This is why the default bridge is rarely used
  directly for multi-container apps.
- **User-defined bridge network** — what `docker network create` (and what
  Compose creates automatically, Phase 4) gives you. Containers attached to it
  **resolve each other by container/service name via Docker's built-in DNS** —
  no hardcoded IPs, no manual `/etc/hosts` editing. This is how the compose stack
  in this repo works: the Spring API reaches Postgres at hostname `db`, not an IP.
- **`host` network** — the container shares the host's network namespace
  directly (no isolation, no port mapping needed — a port the app binds is
  immediately the host's port). Rare; used for niche performance/compatibility
  cases, and unavailable on Docker Desktop for Mac/Windows.
- **`none` network** — no networking at all; fully isolated, useful for batch
  jobs that shouldn't have network access.
- **Publishing vs exposing.** `EXPOSE` in a Dockerfile is documentation only.
  `-p host:container` at `docker run` (or `ports:` in Compose) is what actually
  maps a container port to a reachable host port. Only publish what genuinely
  needs external access — inter-container traffic on a user-defined network never
  needs a published port at all.

```bash
docker network create expense-net
docker run -d --name db --network expense-net -e POSTGRES_PASSWORD=secret postgres:16-alpine
docker run -d --name api --network expense-net -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/expenses expense-api:1.0
# "db" resolves via Docker's embedded DNS to the postgres container's internal IP —
# no IP address anywhere in this command.
```

In this example, both containers join the same user-defined network
`expense-net`. The API's connection string uses the *hostname* `db`, which
Docker's embedded DNS server resolves to whatever internal IP the `db` container
currently has — so if `db` is removed and recreated with a new internal IP, the
API's config never needs to change; the name still resolves correctly. Only the
API's port is published to the host (`-p 8080:8080`); Postgres's port is reachable
from `api` over the private network without ever being exposed to the host.

### `localhost` inside a container — the classic gotcha

**`localhost`/`127.0.0.1` inside a container refers to the container itself**, not
the host machine and not any other container. A process inside container A trying
to reach a service in container B via `localhost` will fail (or, worse, silently
connect to nothing/the wrong thing) — it needs the other container's **service or
container name** (on a shared user-defined network) instead. To reach a service
running on the *host* machine from inside a container, use the special DNS name
`host.docker.internal` (supported on Docker Desktop and recent Docker Engine
versions), not `localhost`.

### Why it's useful

Name-based service discovery on a user-defined bridge network is the exact
mechanism that makes Compose (Phase 4) and Kubernetes Services work — both are,
at their core, "create a private network where names resolve to the right
container/pod." Understanding it here at the raw `docker network` level makes
those higher-level tools far less magical.

## 3.5 — Everyday container-management commands

| Command | Does |
|---|---|
| `docker start`/`stop`/`restart <c>` | Start / gracefully stop (SIGTERM→SIGKILL) / restart an existing container |
| `docker logs -f <c>` | Follow a container's stdout/stderr live |
| `docker exec -it <c> sh` | Open an interactive shell (or run any command) inside a *running* container |
| `docker cp <c>:/path ./local` | Copy a file out of (or into) a container's filesystem |
| `docker top <c>` | List the processes running inside a container |
| `docker stats` | Live CPU/memory/network/IO usage across containers |
| `docker inspect <c>` | Full JSON: IP address, mounts, env, restart count, health status |
| `docker rm -f <c>` | Force-remove a running container (stop + remove in one step) |

## 3.6 — Resource limits

### Key Concepts

- **`--memory` (`-m`)** caps how much RAM a container may use, backed by cgroups.
  Exceeding it triggers the kernel's OOM killer *inside* the container's cgroup —
  the container is killed (exit code 137), not the whole host.
- **`--cpus`** caps how much CPU a container may use (e.g., `--cpus 1.5` = at most
  1.5 cores' worth of CPU time), preventing one busy container from starving
  others on a shared host.
- Without limits, a single container with a memory leak or a runaway loop can
  consume the entire host's resources and take down every other container
  running on it — limits are cheap insurance, not just a production nicety.

```bash
docker run -d --name worker --memory 256m --cpus 0.5 batch-job:1.0
docker stats worker --no-stream
# CONTAINER   CPU %   MEM USAGE / LIMIT     MEM %
# worker      12.30%  84.2MiB / 256MiB      32.89%
```

In this example, the container is capped at 256 MB RAM and half a CPU core.
`docker stats` shows it's currently using about a third of its memory limit — if
it grew past 256 MB, the kernel's OOM killer would terminate the process inside
the container (visible as exit code `137` in `docker ps -a`) rather than letting
it consume host memory unbounded.

## 3.7 — Restart policies

| Policy | Behavior |
|---|---|
| `no` (default) | Never automatically restart |
| `on-failure[:max-retries]` | Restart only if the container exits with a **non-zero** code, up to an optional retry cap |
| `always` | Always restart, regardless of exit code — even if manually stopped, it restarts on daemon restart |
| `unless-stopped` | Like `always`, but **won't** restart after an explicit `docker stop` — the common production default |

```bash
docker run -d --name api --restart unless-stopped expense-api:1.0
```

`unless-stopped` is generally preferred over `always` for long-running services:
it gives you self-healing after a crash or host reboot, but respects an
operator's explicit intent to stop the container (you don't want Docker
immediately restarting something you just deliberately took down for
maintenance).

## Perspective

Running containers well = **externalize everything that varies or must
persist**: config via environment variables, durable data via volumes,
connectivity via published ports and network names, and resilience via restart
policies and resource limits. Keep the container itself **stateless and
disposable** so you can kill it, recreate it from the same image, and scale it
freely — which is exactly the property Kubernetes (Phase 14) and Compose
(Phase 4) build on and formalize.

## Summary / Key Takeaways

- **`-p host:container`** publishes a port; without it, a container's ports are
  unreachable from outside, even if the app inside is listening.
- **Config via environment variables** (`-e`/`--env-file`) lets the same image
  run unchanged across environments — never bake environment-specific config or
  secrets into the image.
- **Named volumes** persist data across container removal/recreation (the right
  call for databases); **bind mounts** are for local dev live-reload; **tmpfs**
  is in-memory scratch space that vanishes on stop.
- **On a user-defined bridge network, containers resolve each other by name**
  via Docker's built-in DNS — `localhost` inside a container means the container
  itself, never the host or a sibling container.
- **`--memory`/`--cpus`** cap resource usage via cgroups so one container can't
  starve the host; **`--restart unless-stopped`** is the common production
  default for self-healing without fighting an operator's manual stop.
