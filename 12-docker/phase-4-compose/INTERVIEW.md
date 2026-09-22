<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · running containers](../phase-3-running-containers/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Docker Compose: Interview Q&A

⭐ = asked constantly.

**Q: What problem does Docker Compose solve?** ⭐⭐
It defines a whole multi-container application — every service, the network(s)
tying them together, volumes, and per-service config — declaratively in one YAML
file, and runs the entire thing with a single command. Without it, standing up
even a modest stack means a `docker network create` plus one carefully
hand-typed `docker run` per container, each with matching `--network`,
`-e`, `-p`, and `-v` flags that are easy to get subtly wrong or let drift out of
sync between teammates. `docker compose up` reads the file once and creates the
network, builds/pulls every image, and starts every container with the right
config, in the right order; `docker compose down` reverses all of it just as
cleanly.

**Q: How do services in a compose file talk to each other?** ⭐⭐
Compose automatically creates a private, user-defined bridge network for the
stack and attaches every service to it; each service's YAML key becomes its DNS
hostname on that network. So an API service's connection string can read
`jdbc:postgresql://db:5432/expenses` — `db` resolves via Docker's embedded DNS to
whatever internal IP that service's container currently has, with no IP address
anywhere in the config. Only ports you explicitly list under `ports:` are
published to the host machine; service-to-service traffic on the shared network
never needs to be published at all.

**Q: Does `depends_on` guarantee a dependency is actually ready?** ⭐⭐
No — by default `depends_on` only controls **start order**: Compose creates the
listed dependency's container before the dependent service's, but "container
started" and "ready to accept traffic" are different things. A Postgres
container's process can be running for several seconds before it's actually
accepting connections on 5432, so a dependent app that boots the instant its
container starts can race the database and crash with a connection-refused error
— often intermittently, since the race's outcome depends on machine speed. The
fix is combining `depends_on` with a `healthcheck` on the dependency and
`condition: service_healthy` on the dependent:

```yaml
db:
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U app -d expenses"]
    interval: 5s
    retries: 5
api:
  depends_on:
    db:
      condition: service_healthy
```

Now Compose withholds creating the `api` container until `db`'s healthcheck
reports healthy, not merely until the container process has started.

*Follow-up: is that enough on its own, in production?* Not fully — it only
covers the startup race. A dependency can still become briefly unreachable later
at runtime (a network blip, a rolling restart), so the application itself should
still implement connection retries with backoff rather than relying purely on
startup-time orchestration ordering as its only defense.

**Q: `image:` vs `build:` in a compose service — when do you use each?**
`image:` pulls a prebuilt image from a registry — used for third-party
components you don't maintain, like Postgres or Redis. `build:` (a `context` plus
an optional `dockerfile` path) builds the image locally from your own
Dockerfile — used for services whose source you own. A realistic stack mixes
both in the same file: this repo's compose file pulls `postgres:16-alpine` and
`redis:7-alpine` by `image:`, while the `api` service is built from
`02-spring-boot/expense-api` using the Dockerfile in `12-docker/examples/`.

**Q: How do you persist database data across `docker compose up`/`down` cycles?** ⭐
Mount a **named volume** at the database's data directory
(`db-data:/var/lib/postgresql/data`) and declare `db-data` under the top-level
`volumes:` key so Compose manages it. The volume is independent of any specific
container instance, so `docker compose down` followed by `docker compose up`
again reuses the same data — you only lose it if you explicitly run `docker
compose down -v`, which also removes named volumes.

**Q: How do you handle environment-specific configuration in Compose?**
A few complementary mechanisms: a `.env` file next to the compose file for
`${VARIABLE}` substitution inside the YAML; `env_file:` per service to load many
environment variables from a file instead of a long inline `environment:` block;
and **override files** (`docker-compose.override.yml`, auto-merged, or explicit
`-f base.yml -f prod.yml` layering) to add or change settings for a specific
environment — e.g., a `dev` override adding bind-mount volumes for live-reload,
a `prod` override adding resource limits and `restart: always` — without
duplicating the shared base definition. Secrets should come from an env file or
a real secrets store, never committed directly into the compose YAML.

**Q: What does `docker compose ps` showing `(healthy)` mean, and where does that
come from?**
It reflects the result of that service's `healthcheck:` block — a command run
on a schedule inside the container, whose exit code (`0` = healthy) Compose
tracks. Only services with a `healthcheck:` defined show a health status at all;
services without one just show `Up`. This status is exactly what
`condition: service_healthy` reads to decide whether a dependent service is
allowed to start.

**Q: When do you outgrow Compose and move to Kubernetes?** *nuance* ⭐
Compose orchestrates containers on a **single Docker host** — perfect for local
development, CI, and small single-machine production deployments. It has no
concept of scheduling across multiple machines, automatic horizontal scaling in
response to load, rolling updates with automated health-gated rollback, or
self-healing beyond that one host's restart policies. Once you need multi-node
scheduling, autoscaling, or coordinated rolling deploys across a fleet, that's
the move to Kubernetes (Phase 14) — and conceptually the compose file usually
maps directly onto the first Deployments and Services you'd write there:
`services:` → Deployment + Service, `healthcheck`/`depends_on` → readiness/
liveness probes, top-level `volumes:` → PersistentVolumeClaims.

**Q: What's the difference between `docker compose down` and `docker compose
down -v`?**
`docker compose down` stops and removes the stack's containers and the network
Compose created for it, but **leaves named volumes intact** — a subsequent `up`
reattaches to the same data. `docker compose down -v` additionally removes those
named volumes, permanently deleting whatever was stored in them (e.g., all
database rows). This is a common and costly footgun if run against a stack whose
database volume wasn't backed up first.

**Q: How would you run only a subset of services, or an optional one like an
admin UI?**
Use **profiles**: tag optional services with `profiles: [tools]` in the compose
file, and they're skipped by a plain `docker compose up` — only
`docker compose --profile tools up` starts them too. This keeps a stack's
default footprint small (just what's needed daily) while still letting the
compose file document how to bring up optional tooling like a DB admin UI when
someone actually needs it.
