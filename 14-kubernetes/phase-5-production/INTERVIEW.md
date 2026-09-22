<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · config storage](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 5 — Health, Scaling & Production: Interview Q&A

⭐ = asked constantly.

**Q: Liveness vs. readiness probes — what's the actual difference in behavior on failure?**
⭐⭐
A liveness probe answers "is this container alive, or should it be restarted?" — on repeated
failure the kubelet kills and restarts the container (same Pod, fresh process), which recovers
a process that's technically running but wedged (deadlocked, stuck, unable to make progress).
A readiness probe answers "is this container ready for traffic right now?" — on failure the
kubelet removes the Pod from the Service's EndpointSlices, with **no restart at all**; it's
purely a traffic-routing decision, used during startup warm-up or whenever a hard dependency
(the database) is temporarily unreachable. You can distinguish the two in practice by watching
`kubectl get pods`: a readiness failure shows `READY: 0/1` with `STATUS: Running` and
`RESTARTS` unchanged; a liveness failure shows the `RESTARTS` counter climbing.

```bash
kubectl get pods -l app=expense-api
# expense-api-7d9f6c5b8f-abcde   0/1   Running   0    <- readiness failing, not liveness
```

*Follow-up: what happens if you configure the liveness probe to hit a path that depends on the
database being up?* You'll get a self-inflicted restart loop — the database being briefly
unreachable should only affect readiness, not liveness; restarting the container doesn't fix
"the database is down," it just adds unnecessary churn (and, worse, if every replica's
liveness fails simultaneously, you can lose the whole fleet to synchronized restarts instead of
gracefully riding out the outage not-ready).

**Q: What's a startup probe for, and why not just set a long `initialDelaySeconds` on the
liveness probe instead?** ⭐
A startup probe exists for containers with unpredictable or long boot times: while it's
configured and hasn't yet succeeded, liveness (and readiness) checks are suspended entirely, so
a slow boot is never mistaken for a hang and killed mid-startup. `initialDelaySeconds` on the
liveness probe alone is a blunter tool — it's a fixed wait that has to be set conservatively
long enough for the *worst-case* boot time, which then also means a genuinely hung container
during that whole window goes undetected. A startup probe checks *actively* for completion
(so a fast boot doesn't wait unnecessarily) and only then hands off to liveness/readiness
running on their normal, tighter cadence.

**Q: Resource requests vs. limits — what's the difference, and what happens when each is
exceeded?** ⭐⭐
Requests are what the scheduler reserves when deciding node placement — a guaranteed minimum
the container is promised, used purely for the scheduling decision, never enforced as a live
ceiling. Limits are hard caps enforced by cgroups at runtime. Exceeding the **CPU** limit
throttles the container (via the CFS quota) — it keeps running, just slower, no crash.
Exceeding the **memory** limit gets the container **OOM-killed** immediately, because memory
usage (unlike CPU time) can't be throttled the same way — `kubectl describe pod` shows
`OOMKilled` as the last termination reason, and it counts toward the restart count exactly
like a crash would.

**Q: What's a Pod's QoS class, and why does it matter?** ⭐⭐
It's derived automatically from how requests/limits are set and determines eviction order
under node memory pressure. **Guaranteed** — every container's requests equal its limits for
both CPU and memory; evicted last. **Burstable** — at least one request/limit is set but
they're not all equal (this repo's API Pod: `requests 250m/512Mi`, `limits 1/1Gi`); evicted
before Guaranteed. **BestEffort** — no requests or limits set at all; evicted first, since the
kubelet has no information to protect it. This is a direct, practical reason to always set
requests/limits rather than skip them "to keep the YAML simple" — an unset Pod is the first
thing killed when a node runs low on memory, regardless of how important that workload actually
is.

**Q: How does the HorizontalPodAutoscaler work, end to end?** ⭐⭐
The HPA controller periodically reads a metric — CPU/memory utilization by default, or a
custom/external metric — for the Pods matching `scaleTargetRef`, via the **metrics-server** (or
a custom metrics adapter); the HPA controller doesn't collect metrics itself, it's a consumer
of that separate metrics pipeline. It compares the observed average against the target (this
repo: keep average CPU utilization near 70%) and computes a new replica count, then patches
`spec.replicas` on the target Deployment — which is just an ordinary field change the
Deployment/ReplicaSet controllers then act on exactly as if a human had edited it. It applies a
stabilization window (near-immediate scale-up, a default ~5-minute window before scaling down)
specifically to avoid thrashing replica count on short-lived spikes.

```yaml
metrics:
  - type: Resource
    resource:
      name: cpu
      target: { type: Utilization, averageUtilization: 70 }
```

*Follow-up: what's the difference between HPA, VPA, and the Cluster Autoscaler?* HPA changes
**how many** Pods exist (horizontal); VPA changes **how big** each Pod's own
requests/limits are (vertical), based on observed usage history; the Cluster Autoscaler changes
**how many nodes** exist, adding capacity when Pods are stuck `Pending` for lack of room and
removing underutilized, safely-drainable nodes. They operate at three independent layers and
commonly run together — though VPA and HPA are normally not combined on the *same* metric
(e.g. both resizing and replicating on CPU) since they'd fight each other, and because
resizing a running Pod today generally requires evicting and recreating it.

**Q: Why does the HPA require metrics-server (or similar) to be installed, and what breaks if
it isn't?**
The HPA object only declares *intent* — "keep CPU near 70%" — it has no built-in way to observe
actual CPU usage. metrics-server aggregates resource usage from every node's kubelet
(cAdvisor) and exposes it via the `metrics.k8s.io` API, which the HPA controller reads.
Without it running, `kubectl get hpa` shows `TARGETS: <unknown>/70%` and the HPA never scales
anything — it silently does nothing rather than failing loudly, which is a real "why isn't
autoscaling working" debugging trap.

**Q: What are namespaces for, and what do they *not* give you automatically?** ⭐
Namespaces are logical partitions of one physical cluster — separating teams, environments, or
apps — and they scope object *names* (two Services both named `postgres` can coexist in
`staging` and `prod`) and are the unit RBAC and ResourceQuota attach to. What they do **not**
give you by default is network isolation: the flat Pod network (Phase 3) lets Pods in different
namespaces reach each other directly unless a NetworkPolicy explicitly restricts it — a common
misconception is that namespaces alone are a security boundary, when they're really an
organizational and quota boundary that needs NetworkPolicy layered on top for actual traffic
isolation.

**Q: What is RBAC, and how do Role/ClusterRole/RoleBinding/ClusterRoleBinding fit together?**
Role-Based Access Control defines *what verbs* (`get`, `list`, `create`, `delete`, `watch`, …)
are permitted *on which resources*, and binds that permission set to a subject (a user, group,
or ServiceAccount). `Role` is namespace-scoped (permissions within one namespace);
`ClusterRole` is the cluster-wide equivalent (or a reusable definition bindable per-namespace).
`RoleBinding` attaches a Role (or ClusterRole) to a subject within one namespace;
`ClusterRoleBinding` attaches a ClusterRole cluster-wide. The governing principle is least
privilege for both humans (a developer might get read-only access to `prod`) and workloads (a
Pod's own ServiceAccount should be scoped to only the API verbs/resources it actually calls,
not cluster-admin by default — a compromised container with an over-privileged ServiceAccount
is a real, common escalation path).

**Q: What's a PodDisruptionBudget, and how is it different from the rolling-update guardrails
(`maxSurge`/`maxUnavailable`)?** ⭐
A PodDisruptionBudget (PDB) limits how many Pods of a group can be **voluntarily** disrupted at
once — a node drain for maintenance, a cluster upgrade evicting Pods to reschedule them
elsewhere — as opposed to involuntary disruption (a crash, an OOM kill), which a PDB has no
control over at all. `minAvailable: 2` on a 3-replica Deployment means the eviction API used by
drains/upgrades will evict at most 1 Pod at a time and wait for a healthy replacement before
evicting the next. This is a *different* guardrail from `maxSurge`/`maxUnavailable`, which only
govern *your own* rolling updates of that Deployment — a PDB protects the workload during
cluster-level operations initiated by someone else (an operator draining a node, a managed
Kubernetes provider's automated node upgrade) that a Deployment's own rollout settings have no
say over.

**Q: What is Helm, and why use it instead of plain manifests?** ⭐
Helm is Kubernetes' package manager: it templates, versions, and parameterizes a set of
manifests into a reusable **chart**, so deploying the same application to a new environment
means overriding `values.yaml` (image tag, replica count, resource sizes) rather than
copy-pasting and hand-editing raw YAML per environment. Charts can be versioned and shared via
a chart repository, and `helm rollback` reverts an entire release (every object it manages) as
one atomic unit, matching Helm's tracked release history. Kustomize is the template-free
alternative, built into `kubectl` — a `base` of plain manifests plus per-environment
`overlays` that patch specific fields without templating syntax, appealing to teams who find
Helm's Go-template YAML harder to read and lint.

**Q: What is GitOps, and how does it extend Kubernetes' own reconciliation model?** ⭐
GitOps makes a git repository the single source of truth for desired cluster state, with a
controller (Argo CD, Flux) running in-cluster that continuously diffs live cluster state
against the repo and syncs any drift — the exact same "declare desired state, a controller
reconciles toward it, forever" pattern from Phase 1, just applied one layer up, to the
deployment process itself rather than to individual objects. The practical payoff: every
production change is a git commit (auditability), rollback is `git revert` rather than a
manual `kubectl` operation, and humans/CI don't need direct `kubectl apply` access to
production at all — CI's job stops at "build, test, push the image, update a manifest/values
file in the deploy repo," and the GitOps controller takes it from there.

**Q: What's the operational difference between running your own metrics/observability stack
and just relying on probes?** *nuance*
Probes only answer "is this one container alive/ready right now" — they're local, binary, and
reactive. Real production observability (Prometheus scraping metrics, Grafana dashboards,
centralized logs, distributed tracing) answers questions probes can't: trends over time,
cross-service latency, why a specific request was slow, whether an HPA's target metric is
actually well-chosen. Probes and observability aren't substitutes for each other — HPA
specifically *depends on* metrics-server (a minimal slice of the observability stack) existing
at all, so "just probes, no metrics pipeline" is not a viable production setup the moment
autoscaling or alerting enters the picture.

**Q: When would you reach for a service mesh (Istio/Linkerd) instead of, or in addition to,
NetworkPolicy and Ingress?** *nuance*
NetworkPolicy gives coarse, label-based allow/deny rules at L3/L4; Ingress gives L7 HTTP
routing at the cluster edge. A service mesh adds a sidecar proxy to every Pod and operates at
L7 *between every internal service*, not just at the edge — giving automatic mTLS for all
east-west traffic, fine-grained traffic shifting (percentage-based canary releases, request-
level routing rules beyond what a Deployment's rolling update alone can express), and rich
per-request observability (latency, error rate, retries) without any application code changes.
It's adopted when those finer-grained internal traffic controls are worth the real operational
complexity a mesh adds (extra sidecar resource overhead, another control plane to run and
understand) — not a default for every cluster.
