<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · dockerfiles ➡](../phase-2-dockerfiles/NOTES.md)
<!-- /nav -->

# Phase 1 — Containers & Images: Notes

Docker packages an application with its entire runtime environment into a portable
**image**, and runs it as an isolated **container**. The payoff: the thing you test is
byte-for-byte the thing that runs in production — "works on my machine" disappears.

## 1.1 — Containers vs VMs; why Docker

- A **container** is an isolated process (or group) on the host, using Linux kernel
  features — **namespaces** (isolate what a process can *see*: PIDs, network, mounts) and
  **cgroups** (limit what it can *use*: CPU, memory). It shares the host kernel.
- A **VM** virtualizes hardware and runs a **full guest OS** per VM (via a hypervisor).
  Heavy: GBs, minutes to boot.
- **Containers are lightweight** because they skip the guest OS: MBs, start in
  milliseconds, high density on one host. Trade-off: weaker isolation than a VM (shared
  kernel), so VMs still win where hard multi-tenant isolation is required.
- **Why it matters:** consistent environments (dev = CI = prod), fast startup,
  reproducibility, and clean dependency isolation (two apps needing different Java versions
  coexist).

## 1.2 — Images vs containers; layers; registries

- An **image** is a read-only template: the filesystem + metadata (entrypoint, env,
  ports). A **container** is a running (or stopped) *instance* of an image with a thin
  writable layer on top. One image → many containers (like a **class → objects**).
- **Layered filesystem:** an image is a stack of read-only layers, one per Dockerfile
  instruction. Layers are **cached and shared** — two images on the same base share those
  layers on disk and over the network. This is why ordering instructions well (Phase 2)
  makes builds fast and images small.
- **Registry:** where images are stored/shared — Docker Hub (public), or private (GHCR,
  ECR, GCR, Harbor). `docker pull`/`push` move images; a **tag** (`myapp:1.2.0`) names a
  version. **Pin real version tags**, not `latest`, for reproducibility.

## 1.3 — The core workflow (commands)

| Command | Does |
|---|---|
| `docker pull nginx:1.27` | fetch an image from a registry |
| `docker run -d -p 8080:80 nginx` | create + start a container (detached, port mapped) |
| `docker ps` / `docker ps -a` | list running / all containers |
| `docker logs <c>` | view a container's stdout/stderr |
| `docker exec -it <c> sh` | run a command (open a shell) inside a running container |
| `docker stop <c>` / `docker rm <c>` | stop / remove a container |
| `docker images` / `docker rmi <img>` | list / remove images |
| `docker build -t myapp .` | build an image from a Dockerfile (Phase 2) |

- Containers are **ephemeral**: when removed, their writable layer (and any data in it)
  is gone. Persistent data needs **volumes** (Phase 3).

## Perspective

The mental model: an **image** is an immutable, layered, versioned artifact of "your app +
its world"; a **container** is a cheap, isolated, disposable running instance of it. That
immutability + portability is the whole value — you build once, and the same image runs on
your laptop, in CI, and in production. Everything else (Dockerfiles, compose, Kubernetes)
is about producing good images and running containers well.
