<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · dockerfiles ➡](../phase-2-dockerfiles/NOTES.md)
<!-- /nav -->

# Phase 1 — Containers & Images: Notes

Docker packages an application with its entire runtime environment into a portable
**image**, and runs it as an isolated **container**. The payoff: the thing you test is
byte-for-byte the thing that runs in production — "works on my machine" disappears
because the machine's differences never entered the picture in the first place. This
phase builds the mental model everything else in the track sits on: what a container
actually *is*, how images are constructed and shared, and the small set of commands
you'll type dozens of times a day.

## 1.1 — Containers vs VMs: what isolation actually costs

A **container** is not a lightweight virtual machine — it's a regular process on the
host operating system that the kernel has been told to *restrict what it can see and
use*. Understanding the two kernel features behind that restriction is the single most
interview-tested Docker fact.

### Key Concepts

- **Namespaces — control what a process can *see*.** Linux namespaces partition
  kernel resources so a process inside one sees only its own slice: `pid` (its own
  process tree, where it's PID 1), `net` (its own network interfaces/IP/ports),
  `mnt` (its own filesystem mounts), `uts` (its own hostname), `ipc` (its own
  shared-memory/semaphores), and `user` (its own UID/GID mapping). A process in a
  container can't see or signal processes outside its PID namespace, even though
  they're all running on the same host kernel.
- **cgroups (control groups) — control what a process can *use*.** They cap and
  account for CPU, memory, disk I/O, and network bandwidth per group of processes.
  This is what backs `docker run --memory` and `--cpus`: without it, one runaway
  container could starve every other process on the host.
- **Union/overlay filesystem — how the image's layers become one filesystem.**
  Docker uses a union filesystem driver (typically **OverlayFS** on Linux) to stack
  read-only image layers under a thin writable layer, presenting them to the process
  as a single, ordinary-looking root filesystem. More in 1.3.
- **Shared kernel.** All containers on a host — and the host itself — run on the
  *same* kernel. This is the core trade-off: it's why containers start in
  milliseconds and cost megabytes instead of gigabytes, and also why container
  isolation is weaker than VM isolation (a kernel-level exploit can, in principle,
  escape every container on that host).
- **A VM virtualizes hardware.** A hypervisor (e.g., KVM, Hyper-V) presents virtual
  hardware to each VM, and each VM boots its **own full guest OS and kernel** on top
  of that virtual hardware. That's real, hardware-level isolation, but it means
  minutes to boot, gigabytes of disk and memory overhead per VM, and duplicated OS
  patching/maintenance.

### Worked example — same host, four "environments"

```bash
# Run three containers, each isolated, all sharing this one host's kernel:
docker run -d --name web  nginx:1.27-alpine
docker run -d --name api  eclipse-temurin:21-jre-alpine sleep infinity
docker run -d --name cache redis:7-alpine

docker ps --format "table {{.Names}}\t{{.Image}}\t{{.Status}}"
# NAMES   IMAGE                          STATUS
# cache   redis:7-alpine                 Up 2 seconds
# api     eclipse-temurin:21-jre-alpine  Up 2 seconds
# web     nginx:1.27-alpine              Up 3 seconds

# Each container's PID 1 is its own process — inside "web", nginx IS pid 1:
docker exec web ps aux
# PID   USER     COMMAND
#     1 root     nginx: master process
#     ...
```

In this example, three containers with completely different runtimes (a proxy, a
JVM, an in-memory store) run side by side on one Linux kernel in the time it takes
to pull their images. There is no guest OS booted per container — `docker exec web
ps aux` shows `nginx` as PID 1 *inside its own PID namespace*, even though on the
host it's just another process with a different, higher PID. Compare that to
starting three VMs, which would mean three kernels, three sets of OS-level
processes, and several minutes of combined boot time.

### Comparison table — containers vs VMs

| | Container | Virtual Machine |
|---|---|---|
| Isolation mechanism | Namespaces + cgroups (same kernel) | Hypervisor + virtual hardware (own kernel) |
| Kernel | Shared with host | Own guest kernel per VM |
| Startup time | Milliseconds | Seconds to minutes |
| Typical size | MBs–low hundreds of MBs | GBs |
| Density per host | Dozens–hundreds | Low tens |
| Isolation strength | Weaker (kernel exploit can cross containers) | Stronger (hardware-level boundary) |
| Best for | App packaging, CI, microservices, horizontal scale | Hard multi-tenant/security boundaries, running a different OS entirely |

### Why it's useful

This is the trade-off every "why Docker" and "containers vs VMs" interview question
is really asking about: containers buy speed, density, and reproducibility by
accepting a weaker isolation boundary than a VM. In practice the two aren't
mutually exclusive — most cloud containers *run inside* a VM (an EC2 instance, a GKE
node), giving you VM-level isolation at the infrastructure boundary and
container-level density and speed for the app workloads on top of it.

## 1.2 — Images, containers, and layers

### Definition

An **image** is a read-only, versioned template — a filesystem plus metadata
(entrypoint, default env, exposed ports, working directory). A **container** is a
running (or stopped) *instance* of an image, with a thin **writable layer** added on
top. The relationship is the same as a **class and its objects**: one image, many
containers, each with its own writable layer and process state, all sharing the same
read-only image underneath.

### Key Concepts

- **Layers are the unit of everything.** An image is a stack of read-only layers,
  one produced by (most of) each instruction in the Dockerfile that built it
  (Phase 2). Each layer only stores the *diff* from the layer below it.
- **Layers are content-addressed and shared.** Docker identifies a layer by a hash
  of its content. If two images share a base (say, both `FROM eclipse-temurin:21-jre-alpine`),
  Docker stores and transfers that shared layer **once** on disk and over the
  network — pulling a second image with the same base is nearly instant for that
  part.
- **The writable layer is copy-on-write (CoW).** When a running container modifies
  a file that exists in a read-only layer below it, the union filesystem copies
  that file up into the container's writable layer first, then modifies the copy.
  The underlying image is never mutated — which is exactly what makes one image
  safely spawn many independent containers.
- **The writable layer is ephemeral.** It lives as long as the container does.
  `docker rm` deletes it — and anything written there — permanently. This is why
  Phase 3 (volumes) exists: state that must outlive a container cannot live in this
  layer.
- **`docker commit` exists but is an anti-pattern.** It freezes a container's
  current writable layer into a new image layer. It's useful for quick debugging
  snapshots, but production images should always be built reproducibly from a
  Dockerfile, not hand-assembled from a live container's drift.

### Worked example — inspecting layers

```bash
docker pull eclipse-temurin:21-jre-alpine
docker history eclipse-temurin:21-jre-alpine
# IMAGE          CREATED       CREATED BY                                      SIZE
# <missing>      3 weeks ago   ENTRYPOINT ["java"]                             0B
# <missing>      3 weeks ago   RUN set -eux; ...install java...                 98MB
# <missing>      3 weeks ago   ENV JAVA_VERSION=21.0.4                          0B
# <missing>      4 weeks ago   /bin/sh -c #(nop) ADD alpine-minirootfs...       7.34MB
# ...

docker inspect eclipse-temurin:21-jre-alpine --format '{{.RootFS.Layers}}'
# [sha256:a1b2c3... sha256:d4e5f6... sha256:...]   <- one hash per layer, content-addressed
```

In this example, `docker history` reads the image's layers newest-to-oldest and
shows the instruction and size each one contributed. `docker inspect
--format '{{.RootFS.Layers}}'` prints the actual content hashes Docker uses to
identify and dedupe those layers on disk. Two images that both start `FROM
eclipse-temurin:21-jre-alpine` will list the *same* leading hashes — proof that
Docker is storing and reusing that shared base only once.

### Comparison table — image vs container

| | Image | Container |
|---|---|---|
| Mutability | Read-only | Read-only layers + one writable layer |
| Lifespan | Persists until explicitly removed (`docker rmi`) | As short-lived as you want; `docker rm` deletes its writable layer |
| Identity | A name:tag or content digest | A running/stopped process with its own ID, network namespace, filesystem view |
| Cardinality | One image | Zero, one, or many containers from it |
| Analogy | Class / blueprint | Object / instance |

### Why it's useful

Layer sharing and caching are the reason a fleet of ten different app images built
`FROM node:22-alpine` doesn't cost ten times the disk and network — the shared
layers are stored once. It's also the mechanical reason Dockerfile *instruction
order* matters so much for build speed (Phase 2): once one layer's content changes,
every layer stacked on top of it must be rebuilt, because each layer's identity
depends on the layer below it plus its own diff.

## 1.3 — Registries, tags, and digests

### Definition

A **registry** is a server that stores and serves images, addressed by
`[registry-host/]repository:tag`. Docker Hub is the default public registry; most
teams also run or use a private one (GHCR, ECR, GCR, Artifactory, Harbor) for
proprietary images.

### Key Concepts

- **`docker pull` / `docker push`** move an image between your local Docker daemon
  and a registry. `docker pull nginx:1.27` with no registry host defaults to Docker
  Hub; `docker pull ghcr.io/myorg/myapp:2.3.1` targets a specific registry.
- **A tag is a mutable pointer**, not a fixed identity — `myapp:1.2.0` can be
  re-pushed to point at a completely different image tomorrow. `latest` is just a
  tag like any other (it's not automatically "the newest version"; it's whatever
  was last pushed without an explicit tag).
- **A digest is immutable.** `myapp@sha256:abc123...` pins to one exact content
  hash forever — pulling it always gets the identical bytes. Production deploy
  manifests increasingly pin digests, not tags, for this reason.
- **Naming convention:** `registry-host/namespace/repository:tag`. Omitting the
  registry host defaults to Docker Hub; omitting the namespace on Docker Hub
  defaults to the `library/` official-images namespace (`nginx` = `library/nginx`).
- **Authentication:** `docker login <registry>` before pushing to (or pulling from)
  a private registry. CI pipelines typically log in with a scoped token/service
  account, not a personal password.

### Worked example

```bash
docker pull nginx:1.27-alpine
# 1.27-alpine: Pulling from library/nginx
# a1b2c3d4e5f6: Pull complete   <- each line is one LAYER being fetched
# ...
# Digest: sha256:9c9e1f...
# Status: Downloaded newer image for nginx:1.27-alpine

docker tag nginx:1.27-alpine ghcr.io/myorg/nginx-mirror:1.27-alpine
docker push ghcr.io/myorg/nginx-mirror:1.27-alpine
# Only layers ghcr.io doesn't already have are actually uploaded —
# shared base layers show "Layer already exists".
```

In this example, `docker pull` reports progress layer by layer, which is the same
layer mechanism from 1.2 made visible over the network — layers already present
locally (from another image sharing that base) are skipped. `docker tag` doesn't
copy any data; it just adds a second name pointing at the same image ID. `docker
push` then only transfers the layers the target registry doesn't already have.

### Why it's useful

Pinning real version tags (or digests) instead of `latest` is what makes a
deployment **reproducible** and a **rollback** meaningful — if `myapp:latest` moved
three times since your last deploy, "roll back to what was running before" has no
well-defined answer. `myapp:1.4.2` (or its digest) always means the exact same
bytes, so CI/CD (Phase 13) can build once, tag with something traceable (a semver
tag and/or the git SHA), push it, and every environment that pulls that tag gets
byte-identical behavior.

## 1.4 — The core CLI workflow

| Command | Does |
|---|---|
| `docker pull nginx:1.27` | Fetch an image from a registry |
| `docker run -d -p 8080:80 --name web nginx:1.27` | Create + start a container (detached, port mapped, named) |
| `docker ps` / `docker ps -a` | List running / all (incl. stopped) containers |
| `docker logs web` / `docker logs -f web` | View a container's stdout/stderr, or follow it live |
| `docker exec -it web sh` | Run a command (e.g., open a shell) inside a *running* container |
| `docker stop web` | Send `SIGTERM`, wait a grace period, then `SIGKILL` if still running |
| `docker rm web` | Remove a stopped container (and its writable layer) |
| `docker images` | List locally stored images |
| `docker rmi nginx:1.27` | Remove a local image (fails if a container still uses it) |
| `docker inspect web` | Full JSON metadata: IP, mounts, env, config |
| `docker stats` | Live CPU/memory/network usage per container |
| `docker build -t myapp .` | Build an image from a Dockerfile (Phase 2) |

### Worked example — full lifecycle in one session

```bash
docker run -d --name web -p 8080:80 nginx:1.27-alpine
# 7f9c2a1e4b8d...                       <- prints the new container ID

docker ps
# CONTAINER ID   IMAGE                COMMAND                  STATUS         PORTS                  NAMES
# 7f9c2a1e4b8d   nginx:1.27-alpine    "/docker-entrypoint.…"   Up 4 seconds   0.0.0.0:8080->80/tcp   web

curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080
# 200

docker exec -it web sh -c "cat /etc/nginx/nginx.conf | head -1"
# user  nginx;

docker stop web        # sends SIGTERM, waits up to 10s, then SIGKILL
docker rm web           # removes the stopped container's writable layer
docker ps -a | grep web # <- no output: it's gone
```

In this example, `docker run -d` starts nginx detached and hands back its ID;
`docker ps` confirms it's up and shows the port mapping; `curl` proves the
published port actually reaches the container's web server; `docker exec` opens a
one-off shell command *inside the already-running* container's namespace to
inspect its config without SSH; `docker stop` gracefully terminates it and `docker
rm` deletes the container object entirely.

### Why it's useful

These dozen commands are what you type constantly during local development and
debugging — pull an image, run it, watch its logs, poke inside it with `exec`,
tear it down. Everything later in the track (custom Dockerfiles, Compose,
production hardening) still bottoms out in this same vocabulary; Compose, for
instance, is mostly a way to avoid typing `docker run` by hand for five services at
once.

## 1.5 — Container lifecycle states

A container moves through a small state machine, visible via `docker ps -a`'s
`STATUS` column:

- **Created** — `docker create` made the container object, but it hasn't started
  running yet (no process inside it).
- **Running** — the main process is executing (`docker run` / `docker start`).
- **Paused** — `docker pause` freezes all processes in the container via a cgroup
  freezer, without stopping them; `docker unpause` resumes.
- **Exited** — the main process finished or was stopped; the container object and
  its writable layer still exist (data recoverable, e.g. `docker cp` or `docker
  logs`) until `docker rm`.
- **Dead** — the daemon failed to fully clean it up; effectively a broken state
  needing `docker rm -f`.

```bash
docker run -d --name job alpine sh -c "sleep 2 && exit 3"
sleep 3
docker ps -a --filter name=job --format "{{.Status}}"
# Exited (3) 1 second ago     <- exit code 3 is preserved for inspection

docker inspect job --format '{{.State.ExitCode}}'
# 3
```

In this example, the container runs a script that exits with code `3`. Even after
the process has finished, `docker ps -a` still lists the container in `Exited`
state with that exit code preserved — this is exactly how CI systems and
orchestrators determine whether a one-off container (a test run, a migration job)
succeeded or failed, without needing to parse logs.

### Why it's useful

Understanding "exited ≠ gone" is what makes `docker logs` and `docker cp` useful
for postmortem debugging of a crashed container — the evidence survives until you
explicitly `docker rm` it. It's also the mechanism behind restart policies (Phase
3): the daemon watches for the `Running → Exited` transition and decides whether to
restart based on the exit code and the configured policy.

## Summary / Key Takeaways

- Containers are isolated **processes** (namespaces = visibility, cgroups =
  resource limits) sharing the host kernel — not lightweight VMs. That's why
  they're fast (ms startup, MBs) but isolate less strongly than a hypervisor-backed
  VM.
- An **image** is a read-only, layered template; a **container** is a running
  instance with a thin, ephemeral, copy-on-write writable layer on top — the
  class-vs-object relationship.
- Layers are **content-addressed and shared/cached** across images and pulls —
  the entire reason instruction order in a Dockerfile matters (Phase 2).
- **Tags are mutable, digests are immutable** — pin real version tags (or digests)
  in production; never deploy off `latest`.
- Data written inside a container lives in its writable layer and is **lost when
  the container is removed** — persistent state needs a volume (Phase 3), not the
  container filesystem.
