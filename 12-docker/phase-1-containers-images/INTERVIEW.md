<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · dockerfiles ➡](../phase-2-dockerfiles/NOTES.md)
<!-- /nav -->

# Phase 1 — Containers & Images: Interview Q&A

⭐ = asked constantly.

**Q: What is a container and how is it different from a VM?** ⭐⭐
A container is an isolated process on the host using kernel namespaces (isolation) and
cgroups (resource limits); it shares the host kernel. A VM virtualizes hardware and runs a
full guest OS via a hypervisor. Containers are far lighter (MBs, millisecond starts) but
share the kernel, so they isolate less strongly than VMs.

**Q: Image vs container?** ⭐⭐
An image is a read-only, layered template (filesystem + metadata). A container is a
running instance of an image with a thin writable layer on top. One image can spawn many
containers — like a class and its objects.

**Q: What are image layers and why do they matter?** ⭐⭐
Each Dockerfile instruction creates a read-only layer; an image is a stack of them. Layers
are cached and shared across images and rebuilds, so good instruction ordering makes
builds fast and images small (unchanged layers are reused, not rebuilt or re-downloaded).

**Q: What is a registry?** ⭐
A store for images — Docker Hub (public) or private (GHCR, ECR, GCR). `docker push`/`pull`
move images; tags name versions (`app:1.2.0`). CI pushes built images to a registry that
production/K8s pulls from.

**Q: Why not use the `latest` tag in production?** ⭐
`latest` is mutable — it can point to different images over time, breaking
reproducibility and making rollbacks ambiguous. Pin an explicit version tag (or a digest)
so a deployment is deterministic and repeatable.

**Q: Why do containers solve "works on my machine"?** ⭐
The image bundles the app with its exact runtime, libraries, and config, so the same
artifact runs identically in dev, CI, and prod. Environment differences that used to cause
drift are frozen into the image.

**Q: What happens to data written inside a container?** ⭐
It goes in the container's writable layer and is **lost when the container is removed**
(containers are ephemeral). Persistent data must live in a volume or bind mount (Phase 3),
not in the container's filesystem.

**Q: Do containers replace VMs?** *nuance*
Not entirely. Containers are better for density, speed, and reproducible app packaging;
VMs give stronger isolation (separate kernels) for hard multi-tenant or security
boundaries. In practice they combine — containers often run *on* VMs in the cloud.
