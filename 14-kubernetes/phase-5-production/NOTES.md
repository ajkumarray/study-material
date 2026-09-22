<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · config storage](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 5 — Health, Scaling & Production: Notes

What makes a Kubernetes deployment production-grade rather than merely "it runs": health
probes that tell the control plane the truth about a container's state, resource governance
that keeps the cluster stable and schedulable, autoscaling that matches capacity to load, and
the operational tooling (namespaces, RBAC, Helm/Kustomize, GitOps, observability) around all
of it. `manifests/api-deployment.yaml` and `manifests/api-hpa.yaml` apply most of this
directly.

## 5.1 — Health probes

### Definition

A **probe** is a periodic check the kubelet runs against a container to determine its real
state — "process running" is not the same as "actually working," and probes are how
Kubernetes tells the difference.

### Key Concepts

- **Liveness probe** — "is this container alive, or should it be restarted?" On repeated
  failure, the kubelet **kills and restarts the container** (same Pod, new container
  instance) — the recovery mechanism for a process that's technically still running but wedged
  (deadlocked, stuck in an infinite loop, unable to make progress). Get this wrong (too
  aggressive, or hitting a path that depends on a slow downstream call) and you create
  self-inflicted restart loops on a perfectly healthy app.
- **Readiness probe** — "is this container ready to receive traffic right now?" On failure,
  the kubelet **removes the Pod from the Service's EndpointSlices** (Phase 3) — no restart, no
  punishment, just "don't send this Pod requests yet." Used during startup warm-up (JVM
  classloading, connection-pool init) and any time a hard dependency (the database) is
  temporarily unreachable — the Pod can sit "not ready" indefinitely without being killed,
  automatically rejoining once the check passes again.
- **Startup probe** — exists for slow-starting containers: while a startup probe is
  configured and hasn't yet succeeded, liveness (and readiness) checks are **suspended**, so a
  container that legitimately takes 60 seconds to boot isn't mistaken for a hung one and killed
  mid-boot in a restart loop. Once the startup probe succeeds once, liveness/readiness probes
  take over as normal for the rest of the container's life.
- **Probe mechanisms** — `httpGet` (expects 200–399; this repo's probes hit Spring Boot
  Actuator's `/actuator/health/liveness` and `/actuator/health/readiness`), `tcpSocket` (just
  checks the port accepts a connection — coarser, useful for non-HTTP services), `exec` (runs
  a command inside the container; non-zero exit = failure).
- **Tuning fields** — `initialDelaySeconds` (grace period before the *first* check),
  `periodSeconds` (how often), `timeoutSeconds`, `failureThreshold` (consecutive failures
  before acting), `successThreshold`. A liveness probe with too-low `failureThreshold`/
  `periodSeconds` on a container with occasional GC pauses is a classic cause of unnecessary
  restarts.

### Worked example — from the repo's manifest

```yaml
containers:
  - name: api
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 30    # give the JVM 30s to boot before the first check
      periodSeconds: 10
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 10
      periodSeconds: 5           # checked more often — traffic-gating is cheaper to get wrong
```

```bash
# Simulate the database being briefly unreachable — Spring Boot's readiness indicator
# flips to DOWN, but the process itself keeps running fine:
kubectl get pods -l app=expense-api
# NAME                           READY   STATUS    RESTARTS
# expense-api-7d9f6c5b8f-abcde   0/1     Running   0     <- NOT restarted, just not "ready"

kubectl get endpointslices -l kubernetes.io/service-name=expense-api
# this Pod's IP is absent — the Service stopped sending it traffic automatically

# once the DB is reachable again, readiness passes and the Pod rejoins the Service
# with zero manual intervention and zero restarts.
```

In this example, `READY: 0/1` with `STATUS: Running` and `RESTARTS: 0` is the tell: this is a
readiness failure, not a liveness failure — the container is fine, just correctly excluded
from traffic until its dependency recovers. A liveness failure would instead show an
increasing `RESTARTS` count.

### Comparison table — the three probes

| | Liveness | Readiness | Startup |
|---|---|---|---|
| Question answered | Is it alive? | Is it ready for traffic? | Has it finished starting? |
| On failure | Restart the container | Remove Pod from Service endpoints (no restart) | Suspends liveness/readiness checks until it passes once |
| Use for | Deadlock/hang recovery | Warm-up, dependency outages | Slow-booting apps (avoid false-positive liveness kills during boot) |

### Why it's useful

Probes are what makes a rolling update (Phase 2) actually safe — a new Pod that hasn't passed
readiness never receives traffic, so a broken new version stalls the rollout instead of taking
down capacity — and what makes self-healing (Phase 1) correct rather than blunt: a hung
process gets killed and replaced (liveness), while a temporarily-overloaded or warming-up one
just gets quietly skipped until it's actually ready (readiness), with no restart cost.

## 5.2 — Resource requests, limits & QoS

### Definition

**Requests** are what the scheduler reserves for a container when deciding node placement;
**limits** are hard caps enforced by the kernel (cgroups) once the container is running. They
answer two different questions — "how much do you need guaranteed" vs. "how much are you
allowed to use, ever."

### Key Concepts

- **Requests drive scheduling.** The scheduler only places a Pod on a node whose *allocatable*
  capacity minus the *sum of requests already promised there* can fit the new Pod's requests —
  it never looks at real-time usage for this decision (Phase 1.3). Under-requesting risks a
  node being oversubscribed under real load; over-requesting wastes capacity the scheduler
  reserves but the container never actually uses.
- **Limits are hard caps, enforced differently per resource.** Exceed the **CPU** limit and
  the container is **throttled** (cgroups CFS quota — it keeps running, just slower, no
  crash). Exceed the **memory** limit and the container is **OOM-killed** immediately (memory
  can't be "throttled" the way CPU time can) — this shows up as `OOMKilled` in
  `kubectl describe pod`, and repeated OOM-kills eventually count against
  `RESTARTS` the same way a crashing process would.
- **QoS (Quality of Service) class** — derived automatically from how requests/limits are set,
  and used by the kubelet to decide **eviction order** under node memory pressure:
  - **Guaranteed** — every container's `requests` equal its `limits`, for both CPU and memory.
    Evicted last.
  - **Burstable** — at least one request/limit is set, but they're not all equal (this repo's
    manifest: `requests: 250m/512Mi`, `limits: 1/1Gi` — Burstable). Evicted before Guaranteed,
    after BestEffort.
  - **BestEffort** — no requests or limits set at all. Evicted first under pressure — the
    kubelet has zero information to protect it.
- **Why set both, not just limits (or neither).** No requests/limits at all means the
  scheduler can pack a node past what it can actually sustain (every Pod is BestEffort, first
  to be OOM-killed under pressure); limits with no requests are technically allowed but
  discouraged (Kubernetes then sets requests equal to limits automatically for CPU/memory in
  many setups, but it's clearer to state both explicitly, as this repo's manifest does).

### Worked example — from the repo's manifest

```yaml
resources:
  requests:
    cpu: "250m"      # 0.25 of a vCPU core — reserved for scheduling
    memory: "512Mi"
  limits:
    cpu: "1"          # hard cap: 1 full vCPU core — throttled above this
    memory: "1Gi"      # hard cap: OOM-killed above this
```

```bash
kubectl describe pod expense-api-7d9f6c5b8f-abcde | grep -A4 "Limits\|Requests\|QoS"
#   Limits:  cpu: 1, memory: 1Gi
#   Requests: cpu: 250m, memory: 512Mi
# QoS Class: Burstable

# a Pod that exceeded its memory limit:
kubectl get pod some-leaky-pod
# NAME             READY   STATUS      RESTARTS
# some-leaky-pod   0/1     OOMKilled   3
```

In this example, `250m` CPU is reserved for scheduling purposes, but the container is free to
burst up to a full core (`1`) if the node has spare capacity — that headroom between request
and limit is exactly what "Burstable" describes, and exactly why setting `requests` well below
`limits` is a normal, intentional pattern for bursty workloads rather than a mistake.

### Why it's useful

Getting requests/limits right is directly a cost and stability lever: under-provisioned
requests cause noisy-neighbor problems and surprise evictions in production; over-provisioned
requests waste cluster capacity (and cloud spend) that's reserved but idle. This is one of the
first places a platform/SRE interview goes to test whether you've operated a real cluster, not
just written YAML.

## 5.3 — Autoscaling

### Definition

**Autoscaling** automatically adjusts capacity to match load, at three independent layers:
Pod *count* (HPA), Pod *size* (VPA), and *node count* (Cluster Autoscaler).

### Key Concepts

- **HorizontalPodAutoscaler (HPA)** — watches a metric (CPU/memory utilization, or a custom/
  external metric via the metrics pipeline) and adjusts a Deployment's (or StatefulSet's)
  `spec.replicas` to keep it near a target. Requires the **metrics-server** (or a custom
  metrics adapter) running in the cluster — the HPA controller itself doesn't collect metrics,
  it reads them from that API. It scales **out/in** — more or fewer identical Pods — which
  works well for stateless, horizontally-scalable workloads.
- **Metric types on `autoscaling/v2`** — `Resource` (CPU/memory utilization or absolute value
  — this repo's HPA), `Pods` (a custom metric averaged across Pods, e.g. requests-per-second
  per Pod), `Object` (a metric off a *different* K8s object, e.g. an Ingress's request rate),
  `External` (a metric from outside the cluster entirely, e.g. a cloud queue's depth) —
  covering everything from "scale on CPU" to "scale a consumer Deployment on a message
  queue's backlog length."
- **VerticalPodAutoscaler (VPA)** — instead of more Pods, resizes a Pod's own
  `resources.requests`/`limits` based on observed usage history. Notable operational wrinkle:
  changing a running Pod's resources currently requires **evicting and recreating** it (unless
  running in in-place-resize mode, still maturing as of recent K8s versions), so VPA and HPA
  are normally not combined on the same CPU/memory metric — they'd fight each other.
- **Cluster Autoscaler** — a different layer entirely: it adds or removes **nodes** based on
  whether Pods are unschedulable (`Pending` due to insufficient capacity) or nodes are
  underutilized and safely drainable. HPA/VPA change what's *asked for*; Cluster Autoscaler
  changes how much *hardware exists* to satisfy those asks — all three commonly run together
  in a production cloud cluster.

### Worked example — from the repo's manifest

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: expense-api
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: expense-api
  minReplicas: 3
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70   # scale out above ~70% avg CPU, in below it
```

```bash
kubectl get hpa expense-api
# NAME          REFERENCE                TARGETS   MINPODS   MAXPODS   REPLICAS
# expense-api   Deployment/expense-api   85%/70%   3         10        5

# under load: TARGETS shows current 85% > target 70%, so the HPA scaled from 3 to 5 replicas.
# once load drops and CPU stays under 70% for the stabilization window, it scales back down.
```

In this example, the HPA controller doesn't act on a single instantaneous CPU spike — it
averages across Pods and applies a **stabilization window** (default 5 minutes for
scale-down, near-immediate for scale-up) to avoid thrashing replica count up and down on
noisy, short-lived spikes. `minReplicas: 3` guarantees the floor even at zero load (matching
the Deployment's own base `replicas: 3`, which the HPA then takes over managing).

### Why it's useful

HPA is the concrete implementation of the "elastic scaling" idea from system design (Track
17) — capacity that grows with real demand instead of being permanently provisioned for peak,
directly trading operational simplicity (Cluster Autoscaler + HPA running unattended) for cost
efficiency versus a fixed, hand-tuned replica count.

## 5.4 — Namespaces, resource quotas & RBAC

### Definition

**Namespaces** partition a single cluster into logically isolated virtual clusters (by team,
environment, or app); **RBAC** controls who or what can do which operations on which
resources, scoped per-namespace or cluster-wide.

### Key Concepts

- **Namespaces scope names, not networking by default.** Two Services named `postgres` can
  coexist in `staging` and `prod` namespaces without conflict — DNS resolution (Phase 3) is
  namespace-relative, which is exactly what makes it work. But Pods across namespaces can
  still reach each other over the network by default (the flat Pod network doesn't respect
  namespace boundaries) unless a NetworkPolicy says otherwise.
- **ResourceQuota** — caps the *total* resources (CPU, memory, object counts like Pods or
  PVCs) a namespace may consume in aggregate — prevents one team/app from starving a shared
  cluster. **LimitRange** — sets default and min/max `requests`/`limits` for individual
  containers *within* a namespace, so Pods that don't specify their own resources still get
  sane defaults (and can't request something absurd).
- **RBAC** — `Role`/`ClusterRole` define a set of allowed verbs (`get`, `list`, `create`,
  `delete`, …) on resource types; `RoleBinding`/`ClusterRoleBinding` attach a Role to a
  subject (a user, group, or **ServiceAccount**). `Role`/`RoleBinding` are namespace-scoped;
  `ClusterRole`/`ClusterRoleBinding` are cluster-wide (or can be bound per-namespace for a
  reusable Role definition). The principle is **least privilege** for both humans and
  workloads — a Pod's own ServiceAccount should be bound to only the verbs/resources it
  actually needs to call the API server for, not cluster-admin by default.

### Worked example

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: staging
---
apiVersion: v1
kind: ResourceQuota
metadata:
  name: staging-quota
  namespace: staging
spec:
  hard:
    requests.cpu: "10"
    requests.memory: 20Gi
    pods: "50"
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  name: pod-reader
  namespace: staging
rules:
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list", "watch"]     # read-only — no create/delete/exec
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: ci-can-read-pods
  namespace: staging
subjects:
  - kind: ServiceAccount
    name: ci-pipeline
    namespace: staging
roleRef:
  kind: Role
  name: pod-reader
  apiGroup: rbac.authorization.k8s.io
```

```bash
kubectl apply -f manifests/api-deployment.yaml -n staging
kubectl apply -f manifests/api-deployment.yaml -n prod
# same manifest, two fully isolated namespaces — separate `expense-api` Deployment,
# Service (postgres.staging vs postgres.prod), and separate quota enforcement each.
```

In this example, the identical `expense-api` Deployment manifest is applied unmodified into
two namespaces; each gets its own object with its own name resolution, and the `staging`
namespace additionally can't consume more than the quota allows, protecting `prod` (or any
other tenant) on the same physical cluster from being starved by a staging load test.

### Why it's useful

Namespaces + quotas + RBAC are how one physical cluster safely hosts multiple teams/
environments — the multi-tenancy story that lets an org run one cluster instead of one cluster
per team, without any one namespace's bug or bad actor starving or reading everyone else's
resources.

## 5.5 — Rollout strategy, rollback & disruption budgets

### Key Concepts (extending Phase 2)

- **Rolling update + rollback** (Phase 2) are the native, zero-extra-tooling way K8s implements
  the deployment-strategy discipline from CI/CD (Track 13): `kubectl rollout status`,
  `kubectl rollout undo`, `maxSurge`/`maxUnavailable` — already covered in depth there.
- **PodDisruptionBudget (PDB)** — a separate but related production concern: it limits how
  many Pods of a group can be **voluntarily** disrupted at once (a node drain for maintenance,
  a cluster upgrade evicting Pods) — as opposed to *involuntary* disruption (a crash, an OOM
  kill), which a PDB has no say over. `minAvailable: 2` on a 3-replica Deployment means a node
  drain will evict at most 1 of those Pods at a time, waiting for a replacement to become
  Ready before evicting the next — preventing a maintenance operation from accidentally taking
  an app below its safe minimum capacity.

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: expense-api-pdb
spec:
  minAvailable: 2          # never voluntarily evict Pods below this, during drains/upgrades
  selector:
    matchLabels:
      app: expense-api
```

### Why it's useful

Without a PDB, a well-intentioned cluster upgrade or node drain can legally evict all 3 of a
Deployment's Pods "at once" if the eviction logic has no other constraint telling it not to —
a PDB is the guardrail that keeps routine cluster maintenance from becoming an unplanned
outage, distinct from (and complementary to) the rolling-update guardrails that only apply to
*application* deploys.

## 5.6 — The production ecosystem (know the names and what problem each solves)

- **Helm** — Kubernetes' package manager: templates, versions, and parameterizes a set of
  manifests into a reusable **chart**, so deploying the same app to a new environment means
  changing `values.yaml`, not copy-pasting and hand-editing YAML. Charts can be versioned,
  shared (a chart repository), and rolled back as a unit (`helm rollback`).
- **Kustomize** — a template-free alternative, built into `kubectl` (`kubectl apply -k`): a
  `base` set of plain manifests plus per-environment `overlays` that patch specific fields
  (image tag, replica count, a ConfigMap value) without templating syntax — appeals to teams
  who find Helm's Go-template YAML awkward to read/write/lint.
- **GitOps (Argo CD, Flux)** — extends the reconciliation idea from Phase 1 to the *deployment
  process itself*: a git repository is the single source of truth for desired cluster state,
  and a controller running in-cluster continuously diffs the live cluster against the repo and
  syncs (or alerts on) drift — giving auditability (every change is a git commit), easy
  rollback (`git revert`), and no direct `kubectl apply` access needed by humans in production
  at all. This is the natural home for the CI/CD (Track 13) pipeline's deploy stage: CI builds
  and pushes an image and updates a manifest/values file in a "deploy repo"; Argo CD/Flux
  notices the commit and reconciles the cluster to match.
- **Observability** — metrics (Prometheus scraping `/metrics` endpoints, including
  metrics-server for HPA), dashboards (Grafana), logs (a collector DaemonSet shipping to a
  log store), and tracing (System Design Track 17, Phase 10). Probes and HPA both *depend* on
  a working metrics pipeline existing — they're not optional infrastructure once you're
  relying on autoscaling or alerting.
- **Cost & right-sizing** — requests/limits (5.2) and HPA/Cluster Autoscaler tuning (5.3) are
  where cloud spend is actually won or lost in a K8s cluster; over-requested, under-utilized
  Pods are the single most common source of silent cloud waste.
- **Service mesh** (Istio, Linkerd) — adds a sidecar proxy to every Pod for mTLS between
  services, fine-grained traffic shifting (canary/blue-green at the request level, beyond what
  a rolling update alone gives you), and rich network-layer observability — a step beyond what
  NetworkPolicy (Phase 3) and Ingress alone provide, at the cost of real operational
  complexity; adopted when those finer controls are worth that cost.

## Perspective

Production Kubernetes is **health-aware, resource-governed, autoscaled, access-controlled, and
declaratively managed end to end**. Probes give the control loop ground truth about each
container (restart the truly dead, route only to the truly ready); requests/limits keep the
cluster schedulable and stable under contention; HPA (with Cluster Autoscaler beneath it)
matches capacity to real load instead of a guessed fixed size; namespaces/quotas/RBAC let one
cluster safely host many teams; and Helm/Kustomize plus GitOps turn the entire desired state —
not just individual manifests — into a versioned, auditable, auto-reconciled artifact. With all
of this in place, the reconciliation engine from Phase 1 is running your application reliably,
safely, and cost-consciously at scale — the payoff of the whole infra half of this repo.

## Summary / Key Takeaways

- **Liveness** restarts a hung container; **readiness** gates traffic without restarting;
  **startup** protects slow-booting containers from being killed mid-boot — three different
  questions, three different actions on failure.
- **Requests** drive scheduling (reserved minimum); **limits** are hard caps (CPU throttles,
  memory OOM-kills) — together they determine a Pod's **QoS class**
  (Guaranteed/Burstable/BestEffort), which governs eviction order under node pressure.
- **HPA** scales replica count on a metric (needs metrics-server); **VPA** resizes a Pod's own
  resources; **Cluster Autoscaler** changes node count — three independent, often
  complementary layers of autoscaling.
- **Namespaces + ResourceQuota + RBAC** are how one physical cluster safely multi-tenants
  across teams/environments; **PodDisruptionBudget** protects app availability specifically
  during voluntary disruptions like node drains and cluster upgrades.
- **Helm/Kustomize** package and parameterize manifests per environment; **GitOps** (Argo
  CD/Flux) extends Kubernetes' own reconciliation model to the deployment process itself, with
  git as the source of truth — the natural endpoint of the CI/CD pipeline's deploy stage.
