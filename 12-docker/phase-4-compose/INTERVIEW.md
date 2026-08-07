<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · running containers](../phase-3-running-containers/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Docker Compose: Interview Q&A

⭐ = asked constantly.

**Q: What problem does Docker Compose solve?** ⭐⭐
It defines a multi-container application (services, networks, volumes, config) in one YAML
file and runs it with a single command. Instead of many `docker run` commands and manual
network wiring, `docker compose up` brings the whole stack up reproducibly.

**Q: How do services in a compose file talk to each other?** ⭐⭐
Compose creates a private network where each service name is a DNS hostname, so the API
connects to Postgres at host `db` and Redis at `redis` — no IPs. Only ports you explicitly
publish are reachable from outside the network.

**Q: Does `depends_on` guarantee a dependency is ready?** ⭐⭐
No — by default it only controls start order and waits for the container to *start*, not to
be *ready* to serve. A database process starts before it accepts connections. Combine
`depends_on` with `condition: service_healthy` and a healthcheck to wait for actual
readiness.

**Q: How do you make a service wait until its database is actually ready?** ⭐
Define a healthcheck on the DB (e.g. `pg_isready`) and set the dependent service's
`depends_on` to `condition: service_healthy`. Also make the app resilient with connection
retries, since orchestration ordering is best-effort, not a hard guarantee.

**Q: `image:` vs `build:` in a service?**
`image:` pulls a prebuilt image from a registry (Postgres, Redis). `build:` builds the
image from a local Dockerfile/context (your own app). A stack typically mixes both — build
your services, pull third-party ones.

**Q: How do you persist database data in Compose?** ⭐
Mount a named volume at the DB's data directory (`db-data:/var/lib/postgresql/data`), and
declare it under top-level `volumes:`. The volume outlives container recreation, so data
survives `up`/`down` cycles (unless you run `down -v`).

**Q: How do you handle environment-specific config?**
Use override files (`-f base.yml -f prod.yml` or `docker-compose.override.yml`) to layer
env-specific settings over a base, and profiles to toggle optional services. Secrets come
from env files/secrets, not committed into the compose file.

**Q: When do you outgrow Compose for Kubernetes?** *nuance*
Compose is single-host — great for dev, CI, and small deployments. When you need multi-node
scheduling, horizontal scaling, self-healing, rolling updates, and service discovery across
machines, you move to Kubernetes (14). The compose file often maps conceptually to the
first K8s Deployments/Services.
