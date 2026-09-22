<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · architecture](../phase-1-architecture/NOTES.md) | [Phase 3 · networking ➡](../phase-3-networking/NOTES.md)
<!-- /nav -->

# Phase 2 — Pods, Deployments & ReplicaSets: Interview Q&A

⭐ = asked constantly.

**Q: What is a Pod, precisely, and why does Kubernetes group containers into it at all?** ⭐⭐
A Pod is the smallest deployable unit in Kubernetes: one or more containers that are always
scheduled onto the same node together, sharing a network namespace (one IP; containers reach
each other on `localhost`) and, optionally, storage volumes. It exists because some containers
genuinely need to co-locate — most commonly an app plus a sidecar (a service-mesh proxy, a log
shipper) that must share the app's network identity or filesystem. The common case is still one
container per Pod; multi-container Pods are the exception for tightly-coupled helper
processes, not a way to group unrelated services.

*Follow-up: can two containers in the same Pod talk to each other, and how?* Yes, over
`localhost` on whatever port each one listens on — they share one network namespace, so from
the container's own point of view there's no network hop at all, just process-to-process
traffic on loopback.

**Q: Why don't you create Pods directly in production?** ⭐⭐
A bare Pod has no controller watching it. If the node it's on dies, if the container crashes
in a way the kubelet's restart policy doesn't cover, or if someone deletes it, nothing
recreates it — the Pod object is simply gone. A Deployment (or another controller) owns a
ReplicaSet that continuously reconciles "how many Pods matching this label exist" against a
desired count, so a dead Pod is replaced automatically, and template changes get a managed
rollout instead of a one-shot recreate. Bare Pods are useful only for one-off debugging or
truly disposable tasks where you don't want that management.

**Q: Deployment vs. ReplicaSet — what's the actual division of responsibility?** ⭐⭐
A ReplicaSet has one job: given a label selector and a desired count, keep exactly that many
matching Pods running — pure self-healing on count, nothing about versions. A Deployment
manages one or more ReplicaSets and adds everything version-related on top: rolling updates,
rollback, and revision history. You almost always write a Deployment; it creates and owns the
ReplicaSet for you and rewrites which ReplicaSet is "current" as you change the Pod template.
Editing a bare ReplicaSet's Pod template directly doesn't trigger a rollout at all — it has no
concept of one; that behavior lives one layer up, in the Deployment controller.

**Q: Explain exactly how a rolling update works, including `maxSurge` and `maxUnavailable`.**
⭐⭐
Changing `spec.template` on a Deployment (e.g. bumping the container image) makes the
Deployment controller create a brand-new ReplicaSet — its name includes a hash of the new
template — starting at 0 replicas, while the existing ReplicaSet stays at its current count.
The controller then alternately scales the new ReplicaSet up and the old one down, respecting
two caps on `spec.strategy.rollingUpdate`: `maxUnavailable` (how far below `replicas` the
*total* ready count is allowed to drop — how much capacity you're willing to sacrifice) and
`maxSurge` (how far above `replicas` the total is allowed to rise — how much you're willing to
over-provision temporarily). With `replicas: 3, maxUnavailable: 1, maxSurge: 1` (this repo's
values), the rollout never has fewer than 2 or more than 4 Pods total at any instant. Each new
Pod must pass its readiness probe before it counts as "available" and before the rollout is
allowed to terminate another old Pod — so a broken new version that never becomes ready
naturally stalls the rollout instead of taking down capacity.

```bash
kubectl set image deployment/expense-api api=ghcr.io/example/expense-api:1.1.0
kubectl rollout status deployment/expense-api
# Waiting for deployment "expense-api" rollout to finish: 1 out of 3 new replicas updated...
```

*Follow-up: what happens if the new version's readiness probe never passes?* The rollout
stalls at whatever partial state `maxUnavailable`/`maxSurge` allow — some old Pods still
serving, some new Pods stuck not-ready — and stays there (traffic keeps flowing to the still-
ready old Pods via the Service) until you either fix the image and re-apply, or run
`kubectl rollout undo` to abandon it.

**Q: How do you roll back a bad deploy, and why is it fast?** ⭐
`kubectl rollout undo deployment/expense-api` (optionally `--to-revision=N`). It's fast
because Kubernetes doesn't delete old ReplicaSets on a rollout — it scales them to 0 and keeps
them (up to `spec.revisionHistoryLimit`, default 10). Rolling back is just re-running the same
scale-up/scale-down dance in reverse against an already-existing ReplicaSet object whose Pod
template never needed to be rebuilt or re-fetched — `kubectl rollout history` lists the kept
revisions.

**Q: How do labels and selectors work, and why is this pattern used everywhere in
Kubernetes?** ⭐⭐
Objects carry `labels` (arbitrary key/value metadata, e.g. `app: expense-api`); controllers
and Services specify a `selector` (most commonly `matchLabels`, or the richer
`matchExpressions` for `In`/`NotIn`/`Exists` logic) that Kubernetes evaluates against every
object's labels to decide membership — "which Pods does this ReplicaSet own," "which Pods does
this Service route to." It's used pervasively (Deployment→ReplicaSet→Pod, Service→Pod, Phase
3's NetworkPolicy) because it loosely couples "who manages/routes to this thing" from "who
created this thing" — a Service doesn't care which Deployment made a Pod, only whether its
labels match, which is exactly what lets you, for example, point a Service at Pods from two
different Deployments during a blue/green switch.

**Q: What's the difference between `spec.selector` and `spec.template.metadata.labels` on a
Deployment, and why does the API server enforce a relationship between them?**
`spec.selector.matchLabels` is what the Deployment uses to find *which Pods it owns*;
`spec.template.metadata.labels` is what labels Pods *get* when this Deployment creates them.
The API server requires `spec.selector` to match a subset of `spec.template.metadata.labels`
— if they could diverge, a Deployment could create Pods it doesn't recognize as its own (an
immediate orphan) or "adopt" unrelated Pods it never created, which would corrupt replica
counting. This is also why `spec.selector` is immutable after creation — changing it could
silently re-scope which Pods a running Deployment manages.

**Q: What are init containers, and how are they different from a regular container that just
runs first?**
Init containers are declared separately (`spec.initContainers`), run sequentially to
completion *before any* of the Pod's main containers start, and the Pod's `STATUS` reflects
this (`Init:0/1`, etc.) while they're running. If an init container fails, the kubelet retries
the Pod from the beginning of the init sequence according to the Pod's restart policy — main
containers never start until every init container has exited successfully. They're used for
setup a main container shouldn't own itself: waiting for a dependency to become reachable,
running a one-time migration, or populating a shared volume before the app starts reading from
it. The key distinguishing behavior versus "just run it first inside the main container's
entrypoint script" is that init containers get their own image, resource limits, and failure/
retry semantics, fully separate from the app container's.

**Q: What's the difference between a Deployment, a StatefulSet, a DaemonSet, and a
Job/CronJob — when do you reach for each?** ⭐⭐
Deployment: stateless workloads where Pods are fully interchangeable and identity doesn't
matter (a web/API tier). StatefulSet: workloads needing stable, ordinal identity and their own
persistent storage per instance — databases, anything that clusters or replicates and needs to
know "which instance am I" (Phase 4). DaemonSet: exactly one Pod per (matching) node,
automatically as nodes join or leave — node-level agents like log/metrics collectors or CNI
plugins, not something you'd ever run as a fixed replica count. Job: run a Pod to completion
for a finite task and track success/failure, rather than keeping it running forever. CronJob:
the same run-to-completion Job semantics, created on a cron schedule.

**Q: Why are Pods designed to be disposable, and what does that force onto application
design?** *nuance*
Disposability is what makes self-healing, scaling, and zero-downtime rollouts possible at all
— the control plane can freely kill, move, and recreate Pods to converge on desired state
because no Pod's identity or in-memory state is assumed to be precious. That forces
applications toward statelessness: anything that must survive a Pod's death (session data,
uploaded files, a database's data) has to live outside the Pod's own filesystem — in a
Service-fronted external store, a cache, or (Phase 4) a PersistentVolume bound via a PVC — and
components must find each other through Services (stable addresses) rather than by caching a
particular Pod's IP, since that IP is guaranteed to change the moment the Pod is replaced.

**Q: What does `kubectl rollout status` actually block on, and what would make it hang
forever?**
It polls the Deployment's `status` until `status.updatedReplicas`, `status.availableReplicas`,
and `status.replicas` all match `spec.replicas` (i.e., the rollout fully completed with every
new Pod both updated and passing readiness). It hangs if new Pods never become ready — a bad
image, a crash loop, a readiness probe hitting the wrong path/port, or a dependency (e.g. the
database) being unreachable — because the rollout, by design, refuses to finish replacing old
capacity with new capacity that hasn't proven itself ready.
