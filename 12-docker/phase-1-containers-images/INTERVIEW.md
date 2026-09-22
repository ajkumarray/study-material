<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · dockerfiles ➡](../phase-2-dockerfiles/NOTES.md)
<!-- /nav -->

# Phase 1 — Containers & Images: Interview Q&A

⭐ = asked constantly.

**Q: What is a container and how is it different from a VM?** ⭐⭐
A container is an isolated *process* on the host, using two Linux kernel features:
**namespaces**, which restrict what the process can *see* (its own PID tree, network
interfaces, mount table, hostname), and **cgroups**, which restrict what it can *use*
(CPU, memory, I/O, via resource accounting/limits). Crucially, it shares the host's
kernel — there's no second OS booting. A VM instead virtualizes hardware through a
hypervisor and boots a full, separate guest OS/kernel per VM.

The practical consequence is size and speed: containers are megabytes and start in
milliseconds because there's no OS to boot; VMs are gigabytes and take seconds to
minutes because they are booting a real kernel. The trade-off is isolation strength —
a kernel exploit is a theoretical path to escape every container on a host, whereas a
VM's boundary is enforced by the hypervisor at the hardware level, which is much
harder to cross. In practice most production containers run *inside* a VM anyway
(an EC2 instance, a GKE node), combining both layers.

*Follow-up: do containers replace VMs entirely?* No — they solve different problems.
Containers win on density, speed, and reproducible app packaging; VMs are still used
where you need a hard multi-tenant or security boundary, or need to run an entirely
different OS. They compose rather than compete.

**Q: What are namespaces and cgroups, specifically?** ⭐⭐
Namespaces partition a kernel resource so each namespace instance appears to be the
whole thing: `pid` (own process tree, container's main process is PID 1 inside it),
`net` (own interfaces/routes/ports), `mnt` (own filesystem mounts), `uts` (own
hostname), `ipc` (own shared memory/semaphores), `user` (own UID/GID mapping).
cgroups (control groups) are the separate mechanism that *limits and accounts for*
resource usage — memory ceilings, CPU shares/quotas, block I/O throttling — for a
group of processes. Namespaces answer "what can this process see"; cgroups answer
"how much can it use." `docker run --memory 512m --cpus 1` is a thin CLI wrapper
around setting these cgroup limits.

**Q: Image vs container — what's the relationship?** ⭐⭐
An image is a read-only, layered template: a filesystem snapshot plus metadata
(entrypoint, default env, working directory, exposed ports). A container is a
*running (or stopped) instance* of an image, with one additional thin **writable**
layer stacked on top via copy-on-write. One image can spawn any number of
independent containers — the same relationship as a class and its objects. Because
the writable layer is copy-on-write, a container can freely modify files that exist
in the read-only layers below without ever touching the underlying image, which is
what lets many containers safely share one image on disk.

**Q: What are image layers, and why does caching them matter so much?** ⭐⭐
Each Dockerfile instruction (mostly `RUN`, `COPY`, `ADD`) produces one read-only,
content-addressed layer; an image is a stack of them. Layers are cached locally and
**shared across images** that share a base — two images `FROM node:22-alpine` store
and transfer that base's layers only once. On a rebuild, Docker reuses a cached
layer if that instruction and its inputs are byte-identical to a previous build; the
moment one layer's content changes, **every layer stacked after it must rebuild**,
because each layer's identity is derived partly from the layer beneath it. This
single fact is why Dockerfile instruction *order* is a real performance lever
(Phase 2) — put what rarely changes (installing dependencies) before what changes
constantly (application source).

*Follow-up: what does `docker history <image>` show?* The layers of an image,
newest first, each with the instruction that created it and the size it added —
useful for spotting an unexpectedly large layer or confirming a secret didn't get
baked in (Phase 5).

**Q: What is a registry, and how do tags differ from digests?** ⭐
A registry stores and serves images by `[host/]repo:tag` — Docker Hub is the public
default; teams also run private ones (GHCR, ECR, GCR, Harbor). `docker push`/`pull`
move images; `docker login` authenticates against a private registry. A **tag** is a
*mutable* pointer — `myapp:1.2.0` can be re-pushed tomorrow to point at entirely
different bytes. A **digest** (`myapp@sha256:...`) is an immutable content hash that
always resolves to the exact same image. Tags are for human-readable versioning;
digests are for guaranteeing you got exactly what you think you got.

**Q: Why not use the `latest` tag in production?** ⭐⭐
`latest` is just an ordinary mutable tag, not "automatically the newest version" —
it's whatever was last pushed without an explicit tag. Deploying off it means two
deploys of "the same" image can silently be different bytes, and "roll back to
what was running an hour ago" has no well-defined answer once `latest` has moved.
Pin an explicit semantic version (`myapp:1.4.2`) or, for maximum determinism, a
digest — so a deployment is reproducible and a rollback means something concrete.

**Q: Why do containers solve "works on my machine"?** ⭐
The image bundles the app together with its exact runtime, OS libraries, and
config as one artifact. The same image is built once (typically in CI) and then
run unchanged in dev, staging, and prod — so the class of bugs caused by
environment drift (different library versions, different OS patch level, a config
file someone forgot to copy) simply can't occur, because there's only one
environment: the one baked into the image.

**Q: What happens to data written inside a container?** ⭐⭐
It's written into the container's writable layer via copy-on-write, and that
writable layer is deleted the moment the container is removed (`docker rm`) — the
underlying image is never mutated. So any state that must outlive the container's
life (a database's files, uploaded content) has to live outside the container
filesystem, in a named volume or bind mount (Phase 3). The rule of thumb: treat
containers as disposable/stateless, and put state in volumes.

**Q: What are the container lifecycle states?**
Created (object exists, nothing running yet) → Running → Paused (frozen via a
cgroup freezer, `docker pause`/`unpause`) → Exited (process finished or was
stopped; the container object, its exit code, and its writable layer still exist
until `docker rm`) → Dead (cleanup failed, needs `docker rm -f`). The fact that
`Exited` preserves both the exit code and the filesystem is what makes `docker
logs` and `docker cp` useful for debugging a crashed container after the fact, and
is what restart policies (Phase 3) key off of.

**Q: What's the difference between `docker stop` and `docker kill`?** *nuance*
`docker stop` sends `SIGTERM` to the container's PID 1, waits a grace period
(default 10s, tunable with `-t`), and only then sends `SIGKILL` if it hasn't
exited — giving the process a chance to shut down gracefully (drain connections,
flush buffers). `docker kill` sends `SIGKILL` (or another signal you specify)
immediately, with no grace period. `docker stop` is what you want in almost every
case; `docker kill` is for when a container is hung and won't respond to `SIGTERM`.

**Q: `docker exec` vs `docker run` — what's the difference?**
`docker run` creates a *new* container from an image and starts a *new* process as
its PID 1. `docker exec` starts an *additional* process inside an *already
running* container, sharing its namespaces — used to poke around, run a one-off
shell, or debug a live container without disturbing its main process. `docker exec
-it web sh` is the everyday way to "SSH into" a container.

**Q: Do containers replace VMs?** *nuance*
Not entirely, as covered above — they solve different problems (density/speed vs
hard isolation) and typically combine in production rather than substitute for one
another.
