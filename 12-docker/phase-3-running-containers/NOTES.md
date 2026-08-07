<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · dockerfiles](../phase-2-dockerfiles/NOTES.md) | [Phase 4 · compose ➡](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 3 — Running Containers: Notes

Building an image is half the job; running it well is the other half — ports, persistence,
config, and networking. Containers are **ephemeral and isolated by default**, so you
explicitly connect them to the outside world.

## 3.1 — `docker run` essentials

```bash
docker run -d --name api -p 8080:8080 -e SPRING_PROFILES_ACTIVE=prod \
  --restart unless-stopped expense-api:1.0
```

- **`-d`** detached (background); omit to attach to logs in the foreground.
- **`-p host:container`** publishes a port — maps host `8080` to the container's `8080`.
  Without `-p`, the container's ports are unreachable from the host.
- **`-e KEY=val`** / `--env-file` injects **config as environment variables** — the
  12-factor way to configure an image without rebuilding it (same image, different env per
  environment).
- **`--name`** a stable name; **`--restart`** policy (`no`, `on-failure`, `always`,
  `unless-stopped`) controls auto-restart.
- **`--memory` / `--cpus`** set resource limits (cgroups) so one container can't starve
  the host.

## 3.2 — Persistence (containers are ephemeral)

When a container is removed, its writable layer is gone. To keep data, mount storage:

- **Named volume** (`-v db-data:/var/lib/postgresql/data`) — Docker-managed storage that
  **survives** container removal/recreation. The right choice for databases (the compose
  file uses one for Postgres). Portable and backup-friendly.
- **Bind mount** (`-v $(pwd)/src:/app/src`) — maps a **host path** into the container.
  Great for **local development** (edit on host, see changes live), less so for prod
  (couples to host layout).
- **tmpfs** — in-memory only, wiped on stop (secrets/scratch).
- Rule: **stateless containers, state in volumes.** Treat containers as disposable; keep
  durable data outside them.

## 3.3 — Networking

- Each container gets its own network namespace. By default they join a **bridge**
  network and can reach the outside; you publish ports (`-p`) to be reachable *from* the
  host.
- On a **user-defined bridge network** (what compose creates), containers reach each other
  by **service/container name via built-in DNS** — the compose API talks to Postgres at
  host `db`, not an IP. This is how multi-container apps wire up (Phase 4).
- **`localhost` inside a container is the container itself**, not the host or other
  containers — a classic gotcha. Use the service name (compose) or `host.docker.internal`
  (to reach the host) instead.
- `EXPOSE`/publishing: `EXPOSE` documents a port; `-p` actually maps it. Only publish what
  needs external access; inter-container traffic stays on the private network.

## Perspective

Running containers well = **externalize everything that varies or must persist**: config
via env vars, durable data via volumes, and connectivity via published ports + network
names. Keep the container itself **stateless and disposable** so you can kill, recreate,
and scale it freely — which is exactly the property Kubernetes (14) relies on.
