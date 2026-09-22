<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · dockerfiles](../phase-2-dockerfiles/NOTES.md) | [Phase 4 · compose ➡](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 3 — Running Containers: Interview Q&A

⭐ = asked constantly.

**Q: What does `-p 8080:80` do, exactly?** ⭐⭐
It **publishes** a port: the first number is the host port, the second is the
container port, so `-p 8080:80` maps the host's port `8080` to whatever is
listening on port `80` *inside* the container's own network namespace. Without
publishing a port, the container's ports are unreachable from the host or outside
network — even if a server inside is actively listening — because the container
has its own isolated network namespace by default. `EXPOSE` in a Dockerfile is
just documentation; it does not publish anything on its own.

*Follow-up: what's the difference between `-p 8080:80` and `-p 80` (or `-P`)?*
`-p 80` (or `docker run -P`) publishes the container's exposed port(s) to a
**random** free host port, useful when you don't care which host port you get
(e.g., running many instances side by side). `-p 8080:80` pins the exact host
port.

**Q: How do you configure a container without rebuilding its image?** ⭐⭐
Inject configuration as environment variables — `-e KEY=val` for individual
values, or `--env-file path` for many at once from a file. This is the 12-factor
config principle: the same built, tested image artifact runs unchanged in dev,
staging, and prod, and only the environment variables supplied at run time
differ. Secrets are a special case of this — they should still arrive as runtime
config, but sourced from a secrets manager or orchestrator secret store (Vault,
AWS Secrets Manager, Kubernetes Secrets), never hardcoded into a shell command,
committed `.env` file, or — worst of all — baked into the image at build time
(where they'd be permanently visible via `docker history`, Phase 5).

**Q: Volume vs bind mount — what's the difference, and when do you use each?** ⭐⭐
A **named volume** (`-v db-data:/var/lib/postgresql/data`) is storage Docker
creates and manages itself, identified by name rather than a specific host path;
it survives container removal and recreation and is portable across hosts — the
right choice for databases and any durable application state. A **bind mount**
(`-v $(pwd)/src:/app/src`) instead maps a *specific host directory* directly into
the container; it's ideal for local development, since editing a file on the host
is instantly visible inside the running container (e.g., for hot-reload
workflows), but it couples the container to that exact host path/layout, which
is a poor fit for production. Both outlive the container itself; the difference
is who manages the storage and how portable it is.

*Follow-up: what's `tmpfs`, and when would you use it?* An in-memory-only mount
that's wiped the instant the container stops — used for scratch space or
short-lived secrets that must never touch disk.

**Q: What happens to data when a container is removed?** ⭐⭐
Anything written into the container's writable layer is gone permanently, because
`docker rm` deletes that layer along with the container object. Persistent data
must live in a volume or bind mount, which exist independently of any single
container's lifecycle. The operating rule of thumb is "stateless containers,
state in volumes" — a container should be freely disposable (kill it, recreate it
from the same image) without losing anything that matters.

**Q: How do containers communicate with each other?** ⭐⭐
On a **user-defined bridge network** (which `docker network create` sets up
manually, or which Compose creates automatically per stack), each container
resolves the others by **container/service name via Docker's built-in embedded
DNS server** — no hardcoded IPs required, and no manual `/etc/hosts` editing. This
is distinct from Docker's *default* bridge network, which containers land on if
you don't specify a network and which does **not** provide name-based DNS
resolution between containers (only IP, which also isn't stable across
restarts) — a common reason "it works with Compose but not with plain `docker
run`" bugs happen. Publishing a port with `-p` is only needed for access *from
outside* the Docker network (e.g., from your laptop's browser); container-to-
container traffic on the same user-defined network never needs a published port.

**Q: Inside a container, what does `localhost` refer to?** *nuance* ⭐
The container itself — never the host machine, and never a sibling container.
This trips people up constantly: a process in container A trying to reach a
service in container B via `localhost:5432` will simply fail to connect (there's
nothing listening on port 5432 inside container A itself). The fix is to use the
other container's **name** on a shared user-defined network (e.g., `db:5432`). To
reach something running on the *host* machine from inside a container, use the
special DNS name `host.docker.internal` instead of `localhost`.

**Q: What are restart policies, and which do you use in production?** ⭐
`--restart` controls automatic restart behavior on exit: `no` (default — never
restart), `on-failure[:max-retries]` (restart only on a non-zero exit code, with
an optional cap), `always` (always restart, even after an explicit manual stop,
and again after the Docker daemon itself restarts), and `unless-stopped` (behaves
like `always`, but respects an operator's explicit `docker stop` and won't
auto-restart after that). `unless-stopped` is the typical production default for
long-running services — it gives you self-healing after a crash or host reboot
without fighting a deliberate manual stop for maintenance.

**Q: How do you limit a container's resource usage, and what happens if it's
exceeded?** ⭐
`--memory`/`-m` and `--cpus`, both backed by cgroups (Phase 1). Exceeding the
memory limit triggers the kernel's out-of-memory killer *scoped to that
container's cgroup* — the container is killed (visible as exit code `137` in
`docker ps -a`), not the whole host. Exceeding CPU just means the process is
throttled to its allotted share; it isn't killed. Setting both is what prevents a
single leaking or runaway container from starving every other workload on a
shared host — the same requests/limits model Kubernetes formalizes at the pod
level (Phase 14).

**Q: `docker exec` vs attaching to a container's logs — what's each for?**
`docker exec -it <c> sh` opens a brand-new process inside an already-running
container's namespaces — used interactively to poke around, inspect files, or
run a diagnostic command without disturbing the container's main process.
`docker logs -f <c>` instead streams the *existing* main process's stdout/stderr
— it doesn't run anything new. In practice you reach for `logs -f` to watch
what's already happening and `exec` when you need to actively investigate
(check a config file, test DNS resolution from inside the container, etc.).

**Q: How do you back up data stored in a named volume?**
Run a short-lived helper container that mounts both the volume and a host
directory, then archive it: `docker run --rm -v db-data:/data -v
$(pwd):/backup alpine tar czf /backup/db-data.tgz /data`. This works because
named volumes are just directories Docker manages on the host (typically under
`/var/lib/docker/volumes/`), so any container can mount the same volume to read
or write it, independent of whichever container originally created it.

**Q: What's the difference between `docker stop` and `docker rm -f`?**
`docker stop` sends `SIGTERM`, waits a grace period, then `SIGKILL`s if needed —
it leaves the (now-exited) container object and its writable layer intact for
later inspection (`docker logs`, `docker cp`). `docker rm -f` force-stops
*and* immediately deletes the container object and its writable layer in one
step — there's nothing left to inspect afterward. Use `stop` when you might want
to look at what happened; use `rm -f` when you're certain you're done with it.
