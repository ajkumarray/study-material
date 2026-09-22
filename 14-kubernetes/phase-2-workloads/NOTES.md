<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · architecture](../phase-1-architecture/NOTES.md) | [Phase 3 · networking ➡](../phase-3-networking/NOTES.md)
<!-- /nav -->

# Phase 2 — Pods, Deployments & ReplicaSets: Notes

The workload objects that actually run your containers. You almost never create a raw Pod
directly; you declare a **Deployment**, and the machinery underneath it — ReplicaSets, and
ultimately Pods — keeps the right containers running, healthy, and up to date. See
`manifests/api-deployment.yaml`.

## 2.1 — Pods

### Definition

A **Pod** is the smallest deployable unit in Kubernetes — one or more containers that are
always scheduled together, on the same node, sharing a network namespace (same IP,
`localhost` between them) and optionally storage volumes.

### Key Concepts

- **Usually one container per Pod.** The Pod abstraction exists for containers that *must*
  co-locate — a second "sidecar" container (a log shipper, a service-mesh proxy, a config
  reloader) is the exception, not the rule. Two unrelated apps never belong in one Pod; if
  they scale independently or fail independently, they belong in separate Pods (and typically
  separate Deployments).
- **Shared network namespace.** All containers in a Pod share one IP address and port space —
  container A can reach container B on `localhost:<port>`, and from outside the Pod, every
  container's ports are on the same IP. This is *why* sidecars work: an Envoy proxy sidecar
  can transparently intercept traffic to `localhost` without any extra network hop.
- **Shared storage (optional).** Volumes (Phase 4) are defined at the Pod level and mounted
  into whichever containers need them — the same volume can be mounted by two containers in
  one Pod (e.g. an app writing logs to a shared `emptyDir`, and a sidecar shipping them).
- **Init containers** — containers that run to completion, in order, *before* the Pod's main
  containers start. Used for setup work a main container shouldn't do itself: waiting for a
  dependency to be reachable, running a one-time migration, populating a volume with config
  fetched at startup. If an init container fails, the Pod is retried from the first init
  container — the main containers never start until all init containers succeed.
- **Ephemeral and disposable — never "healed" in place.** A Pod that crashes or is evicted is
  not restarted in the sense of resuming the same object; a *new* Pod object (new name, often
  a new IP) is created by the ReplicaSet controller to replace it. (Container *restarts within
  a still-scheduled Pod*, e.g. after a liveness probe failure, do keep the same Pod — see
  Phase 5. It's the Pod being rescheduled elsewhere, or deleted, that produces a brand-new Pod
  identity.) Because identity and IP aren't stable, **you never manage Pods directly** — a
  controller does, and this ephemerality is exactly why Services (Phase 3) exist: something
  has to provide a stable address in front of Pods that come and go.

### Worked example — a Pod spec, standalone

```yaml
# For illustration only — in this repo you'd never write a bare Pod like this;
# it's what a Deployment's `template:` ultimately produces.
apiVersion: v1
kind: Pod
metadata:
  name: expense-api-debug
  labels:
    app: expense-api
spec:
  initContainers:
    - name: wait-for-db
      image: busybox:1.36
      command: ["sh", "-c", "until nc -z postgres 5432; do sleep 1; done"]
  containers:
    - name: api
      image: ghcr.io/example/expense-api:1.0.0
      ports:
        - containerPort: 8080
```

```bash
kubectl apply -f pod-debug.yaml
kubectl get pod expense-api-debug
# NAME                 READY   STATUS     RESTARTS
# expense-api-debug    0/1     Init:0/1   0        <- init container still running

# ...once postgres is reachable:
kubectl get pod expense-api-debug
# NAME                 READY   STATUS    RESTARTS
# expense-api-debug    1/1     Running   0
```

In this example, `wait-for-db` blocks in a loop until Postgres accepts connections on port
5432, and the `api` container does not start until that init container exits successfully —
the Pod's `STATUS` column visibly tracks `Init:0/1` before flipping to `Running`. This pattern
avoids baking retry/wait logic into the application itself for a dependency that's only slow
at cold-cluster-start time.

### Why it's useful

Understanding that a Pod is the *unit of scheduling and networking*, not the unit of scaling
or management, is the prerequisite for everything else in this phase: a Deployment doesn't
scale "containers," it scales *Pods* (whole co-located groups), and a Service doesn't
load-balance to "containers," it load-balances to Pod IPs.

## 2.2 — ReplicaSets

### Definition

A **ReplicaSet** is a controller that ensures a specified number of identical Pod replicas
(matching a label selector) are running at all times — creating new ones if the count drops
below desired, and (less commonly) deleting extras if it's above.

### Key Concepts

- **You rarely write a ReplicaSet by hand.** In practice a Deployment (2.3) creates and owns
  one for you. Knowing it exists matters because `kubectl get replicasets` is exactly where
  you see the mechanics of a rollout (multiple ReplicaSets, one per revision, scaled up/down
  against each other).
- **Selector-based membership.** A ReplicaSet's `spec.selector.matchLabels` defines which Pods
  count toward its replica total — any Pod (even one not created by this ReplicaSet) with a
  matching label and no other owner can be "adopted." This is the same label-matching
  mechanism every controller in Kubernetes uses (2.3's Deployment→ReplicaSet relationship,
  Phase 3's Service→Pod relationship).
- **Self-healing is this controller's entire job.** It has one loop: count current matching
  Pods, compare to `spec.replicas`, create or delete the difference. There's no rollout logic
  here at all — that's the Deployment's layer on top.

### Worked example — what a Deployment creates underneath

```bash
kubectl apply -f manifests/api-deployment.yaml
kubectl get replicasets -l app=expense-api
# NAME                       DESIRED   CURRENT   READY   AGE
# expense-api-7d9f6c5b8f     3         3         3       2m
#   ^^^^^^^^^^^^^^^^^^^^ the hash suffix encodes the Pod template's content —
#   change the template (e.g. bump the image) and a NEW ReplicaSet with a
#   different hash appears; see 2.3.

kubectl describe replicaset expense-api-7d9f6c5b8f
# Selector: app=expense-api
# Controlled By: Deployment/expense-api    <- ownerReference back to the Deployment
```

In this example, the ReplicaSet's name is derived from a hash of its Pod template — that hash
is the mechanism a Deployment uses to detect whether a rollout is needed (template changed →
new hash → new ReplicaSet) versus a no-op re-apply (template unchanged → same hash → same
ReplicaSet, just reconciled). `Controlled By: Deployment/expense-api` is an `ownerReference`,
which is also what makes `kubectl delete deployment expense-api` cascade-delete its
ReplicaSets and their Pods by default.

### Why it's useful

Debugging a stuck rollout means reading `kubectl get replicasets` and seeing which revision is
scaled to what — this is the layer where "3 old Pods, 1 new Pod, stuck" becomes visible when a
new version's Pods aren't passing readiness (Phase 5) and the rollout can't progress past
`maxUnavailable`.

## 2.3 — Deployments, rolling updates & rollbacks

### Definition

A **Deployment** is the object you actually write for stateless workloads: it manages
ReplicaSets on your behalf and adds versioned, gradual **rollouts** and easy **rollbacks** —
the layer of behavior a bare ReplicaSet doesn't have.

### Key Concepts

- **`spec.replicas`, `spec.selector`, `spec.template`.** You declare the desired replica
  count, the selector that must match the Pod template's labels (`spec.selector.matchLabels`
  must be a subset of `spec.template.metadata.labels` — the API server rejects a Deployment
  where they don't align), and the Pod template itself (container images, ports, env,
  resources, probes — everything from 2.1).
- **`spec.strategy`** controls how an update from one Pod template to another is rolled out:
  - **`RollingUpdate`** (the default) — replace Pods gradually. `maxUnavailable` caps how
    many Pods below `replicas` are allowed during the rollout (how much capacity you're
    willing to lose); `maxSurge` caps how many *extra* Pods above `replicas` are allowed
    (how much you're willing to over-provision temporarily). This repo's manifest sets both
    to `1`, so with 3 replicas the rollout never has fewer than 2 or more than 4 Pods at once.
  - **`Recreate`** — kill all old Pods, then create all new ones. Simple but causes downtime;
    used when the old and new versions genuinely cannot run side by side (e.g. an
    incompatible shared resource/schema).
- **A template change creates a new ReplicaSet; it doesn't just edit Pods in place.** Change
  the image (or any field in `spec.template`) and the Deployment controller creates a *new*
  ReplicaSet (new template hash) at `replicas: 0`, then scales it up while scaling the old
  ReplicaSet down, respecting `maxSurge`/`maxUnavailable` at every step — that's what "rolling
  update" mechanically *is*. New Pods must pass their **readiness probe** (Phase 5) before
  they count toward the new ReplicaSet's "available" total and before traffic reaches them via
  the Service.
- **Rollback is just re-pointing at a previous ReplicaSet.** Kubernetes retains a bounded
  history of old ReplicaSets (governed by `spec.revisionHistoryLimit`, default 10) at
  `replicas: 0` rather than deleting them — `kubectl rollout undo` scales the previous
  revision's ReplicaSet back up and the current one down, which is fast precisely because the
  old Pod template spec was never discarded.
- **Watching and controlling a rollout:**
  - `kubectl rollout status deployment/expense-api` — blocks and reports progress until the
    rollout completes or fails.
  - `kubectl rollout history deployment/expense-api` — lists revisions.
  - `kubectl rollout undo deployment/expense-api [--to-revision=N]` — roll back.
  - `kubectl rollout pause` / `resume` — halt a rollout mid-way (e.g. after 1 new Pod is up,
    to manually verify it) — a manual, coarse approximation of canary deployment.

### Worked example — a rolling update, step by step

```bash
kubectl set image deployment/expense-api api=ghcr.io/example/expense-api:1.1.0
# deployment.apps/expense-api image updated

kubectl rollout status deployment/expense-api
# Waiting for deployment "expense-api" rollout to finish: 1 out of 3 new replicas updated...
# Waiting for deployment "expense-api" rollout to finish: 2 out of 3 new replicas updated...
# deployment "expense-api" successfully rolled out

kubectl get replicasets -l app=expense-api
# NAME                       DESIRED   CURRENT   READY   AGE
# expense-api-7d9f6c5b8f     0         0         0       10m   <- old revision, kept for rollback
# expense-api-5c8b9d6f4a     3         3         3       1m    <- new revision, fully up

# A bad deploy — undo it:
kubectl rollout undo deployment/expense-api
# deployment.apps/expense-api rolled back
kubectl rollout history deployment/expense-api
# REVISION  CHANGE-CAUSE
# 1         <none>
# 2         kubectl set image deployment/expense-api api=...:1.1.0
# 3         kubectl rollout undo ...   <- new revision pointing back at rev 1's template
```

In this example, `kubectl set image` only changed `spec.template.spec.containers[0].image` —
that one field change is what triggers the whole rolling-update machinery. The old
ReplicaSet is scaled to `0` rather than deleted, so `rollout undo` is a scale-up/scale-down
operation on two already-existing ReplicaSets, not a rebuild — which is why rollback is
seconds, not a new deploy pipeline run.

### Comparison table — RollingUpdate vs. Recreate

| | `RollingUpdate` | `Recreate` |
|---|---|---|
| Downtime | None (with correct probes + `maxUnavailable`) | Yes — brief gap between old Pods gone and new Pods ready |
| Peak resource usage | Up to `replicas + maxSurge` Pods running at once | Never more than `replicas` (old and new never coexist) |
| Old/new coexistence | Yes, briefly, during the rollout | Never |
| Use when | The default — stateless apps that can run two versions side by side | Versions are mutually incompatible (e.g. a non-backward-compatible schema/lock a shared resource can't have both versions touch at once) |

### Other workload controllers (know the names and when to reach for each)

- **StatefulSet** — like a Deployment but for Pods needing **stable identity and per-Pod
  persistent storage** (ordinal names, own PVC, ordered startup) — covered in depth in Phase
  4 with the Postgres example.
- **DaemonSet** — runs exactly **one Pod per node** (or per matching subset of nodes), scaling
  automatically as nodes join/leave the cluster. Used for node-level agents: log/metrics
  collectors, CNI plugins, storage daemons — anything that must exist on every machine rather
  than at a chosen replica count.
- **Job** — runs a Pod (or several) **to completion** for a finite task (a batch job, a
  migration), and tracks success/failure rather than keeping something perpetually running;
  `spec.completions`/`spec.parallelism` control how many successful runs are needed and how
  many run concurrently.
- **CronJob** — creates a **Job on a cron schedule** (`spec.schedule: "0 2 * * *"`) — scheduled
  batch work (nightly reports, cleanup tasks) with the same run-to-completion semantics as a
  Job underneath.

```yaml
# Illustrative — a nightly cleanup task, showing the Job/CronJob shape:
apiVersion: batch/v1
kind: CronJob
metadata:
  name: expense-report-cleanup
spec:
  schedule: "0 2 * * *"          # 2am daily, standard cron syntax
  jobTemplate:
    spec:
      template:
        spec:
          restartPolicy: OnFailure   # Jobs never use "Always" — the Pod must be allowed to finish
          containers:
            - name: cleanup
              image: ghcr.io/example/expense-api:1.0.0
              command: ["java", "-jar", "app.jar", "--cleanup-old-reports"]
```

### Why it's useful

Picking the right controller is a routine production decision, not a trivia question: a
Deployment for the stateless API, a StatefulSet for Postgres, a CronJob for the nightly
report-cleanup task, and — if you ran a log shipper on every node instead of Fluent Bit as a
managed add-on — a DaemonSet. Getting this wrong (e.g. a Deployment for a database) is exactly
the class of mistake Phase 4 exists to prevent.

## Summary / Key Takeaways

- A **Pod** is the smallest schedulable unit — one or more co-located, network/storage-sharing
  containers — and is ephemeral by design: a dead Pod is *replaced*, never resurrected, which
  is why you never manage Pods directly.
- A **ReplicaSet** enforces "N matching Pods exist" via label selection; a **Deployment**
  manages ReplicaSets and adds rollout/rollback on top — you write Deployments, not
  ReplicaSets, for stateless apps.
- A rolling update changing `spec.template` creates a **new ReplicaSet**, scaled up as the old
  one scales down under `maxSurge`/`maxUnavailable`; the old ReplicaSet is kept at `0`
  replicas (not deleted) so `kubectl rollout undo` is a fast scale operation, not a rebuild.
- **Labels and selectors** are the loose-coupling glue used everywhere: Deployment→ReplicaSet,
  ReplicaSet→Pod, and (Phase 3) Service→Pod all work by label matching, independent of which
  object created which.
- Pick the controller for the workload's shape: **Deployment** (stateless, interchangeable),
  **StatefulSet** (stable identity + storage), **DaemonSet** (one per node), **Job/CronJob**
  (run-to-completion, scheduled or not).
