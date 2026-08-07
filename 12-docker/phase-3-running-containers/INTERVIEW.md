<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · dockerfiles](../phase-2-dockerfiles/NOTES.md) | [Phase 4 · compose ➡](../phase-4-compose/NOTES.md)
<!-- /nav -->

# Phase 3 — Running Containers: Interview Q&A

⭐ = asked constantly.

**Q: What does `-p 8080:80` do?** ⭐⭐
Publishes a port: maps host port 8080 to the container's port 80, so traffic to the host's
8080 reaches the container. Without publishing, the container's ports aren't reachable from
the host or network.

**Q: How do you configure a container without rebuilding it?** ⭐⭐
Inject configuration as environment variables (`-e KEY=val` / `--env-file`). The same
image runs in every environment with different env values — the 12-factor config
principle. Secrets come from a secrets manager/orchestrator, not baked into the image.

**Q: Volume vs bind mount?** ⭐⭐
A named volume is Docker-managed storage that persists across container removal — used for
databases and durable data. A bind mount maps a host directory into the container — great
for local dev (live code edits), but couples to the host's filesystem. Both outlive the
container; volumes are preferred for production data.

**Q: What happens to data when a container is removed?** ⭐
Anything in the container's writable layer is lost. Persistent data must live in a volume
or bind mount. The guiding rule is "stateless containers, state in volumes."

**Q: How do containers communicate with each other?** ⭐⭐
On a user-defined bridge network (which Compose creates), containers resolve each other by
service/container name via Docker's built-in DNS — no hardcoded IPs. Publishing ports is
only needed for access from outside the network.

**Q: Inside a container, what is `localhost`?** *nuance*
The container itself — not the host and not other containers. To reach another container
use its service name; to reach the host use `host.docker.internal` (or the host's network
address). Assuming `localhost` is the host is a very common bug.

**Q: What are restart policies?**
`--restart` controls auto-restart: `no` (default), `on-failure` (restart on non-zero
exit), `always`, and `unless-stopped` (restart unless you manually stopped it). They keep
long-running services up across crashes/reboots.

**Q: How do you limit a container's resources?**
`--memory` and `--cpus` (backed by cgroups) cap memory and CPU so a container can't starve
the host or neighbors. In orchestration this becomes requests/limits (Kubernetes 14).
Setting them prevents a single container from taking down the node.
