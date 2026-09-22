<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · workloads ➡](../phase-2-workloads/NOTES.md)
<!-- /nav -->

# Phase 1 — Architecture & the Declarative Model: Notes

Kubernetes is a **container orchestrator**: given a pool of machines (a cluster) and a
description of what you want running, it schedules containers onto machines, keeps them
running, scales them, and updates them — continuously, without a human managing individual
servers. This phase builds the mental model the rest of the track sits on: what a cluster is
made of, and the single idea — *declare desired state, let controllers converge to it* —
that explains almost everything Kubernetes does.

## 1.1 — Why orchestration: the problem Kubernetes solves

### Definition

**Orchestration** is the automated placement, health management, scaling, and networking of
many containers across many machines, driven by a specification you write once rather than
operational steps you perform by hand.

### Key Concepts

- **Running one container is easy; running a system is not.** Docker (Track 12) solves "run
  this one container reliably on this one host." A real production system needs dozens of
  replicas spread across many hosts, automatic restart of crashed instances, load balancing
  across the replicas that are currently healthy, rolling out new versions without downtime,
  scaling out under load and back in when idle, distributing config/secrets per environment,
  and letting services find each other as they move. Scripting all of that by hand (SSH into
  boxes, run `docker run`, watch health, restart failures) does not scale past a handful of
  servers.
- **Kubernetes automates the operational loop**, not just container placement:
  - **Scheduling** — decide *which node* has room (CPU/memory/constraints) for a new Pod.
  - **Self-healing** — replace crashed containers and reschedule Pods off dead nodes,
    automatically, without a human paged at 3am.
  - **Scaling** — change replica counts manually or automatically based on load (HPA).
  - **Rollouts/rollbacks** — change versions gradually and reversibly, not "docker stop; docker
    run" all at once.
  - **Service discovery & load balancing** — give a stable name/IP to a set of Pods that are
    constantly being created and destroyed.
  - **Config & secret management** — inject per-environment values without baking them into
    the image.
  - **Storage orchestration** — attach durable disks to Pods and keep them attached across
    rescheduling.
- **What Kubernetes is *not*.** It is not a PaaS that builds your image (that's CI/CD, Track
  13) and it is not a hypervisor — it schedules and manages containers, assuming an image
  already exists in a registry. It also doesn't replace application-level concerns like
  business logic, database schema design, or (by default) service-to-service authentication —
  those are still your job, though K8s gives you primitives (Secrets, NetworkPolicies, service
  mesh) to build them on.

### Worked example — the problem, concretely

```bash
# Without an orchestrator: deploying 3 API replicas across 3 hosts by hand
ssh host1 "docker pull ghcr.io/example/expense-api:1.0.0 && docker run -d -p 8080:8080 ghcr.io/example/expense-api:1.0.0"
ssh host2 "docker pull ghcr.io/example/expense-api:1.0.0 && docker run -d -p 8080:8080 ghcr.io/example/expense-api:1.0.0"
ssh host3 "docker pull ghcr.io/example/expense-api:1.0.0 && docker run -d -p 8080:8080 ghcr.io/example/expense-api:1.0.0"
# Now: which host has capacity for a 4th replica? Which container just crashed?
# How do you roll out 1.1.0 without a gap? Nothing here answers that — you'd have to
# script health checks, a load balancer's backend list, and a rollout sequence yourself.

# With an orchestrator: same intent, declared once
kubectl apply -f manifests/api-deployment.yaml
# replicas: 3 is a property of the Deployment object, not a loop you wrote —
# Kubernetes decides which nodes, watches health, and keeps 3 running forever.
```

In this example, the imperative script has no memory: if `host2`'s container dies, nothing
restarts it, and nothing above `docker run` knows the desired count is 3. The declarative
`kubectl apply` submits a *specification* ("3 replicas of this image, with these probes and
resource limits") that a controller inside the cluster continuously enforces — the exact
mechanism in 1.5.

### Why it's useful

This is the "why Kubernetes / why not just Docker" interview question. The honest answer is
that Docker solves packaging and single-host running; Kubernetes solves the operational
problem of running that package reliably, at scale, across a fleet of machines, without
hand-written scripts for restart, rollout, and scaling. Teams adopt it not because containers
need it to run, but because the operational loop around many containers is too complex and
too failure-prone to hand-script once you're past a handful of instances.

## 1.2 — Cluster anatomy: the control plane

### Definition

A Kubernetes **cluster** is a set of machines split into two roles: a small number of
**control plane** nodes that make decisions (the brain) and a larger number of **worker
nodes** that run your containers (the muscle). The control plane holds no application
containers itself (in a properly configured cluster) — it exists purely to observe and
reconcile cluster state.

### Key Concepts

- **API server (`kube-apiserver`)** — the single front door to the cluster. Every
  interaction — `kubectl`, controllers, the scheduler, other control-plane components, even
  one Pod's request to talk to another via the K8s API — goes through it as a REST/HTTP(S)
  request, authenticated and authorized, then validated and persisted to etcd. Nothing talks
  to etcd directly except the API server; this makes the API server the one place access
  control, validation, and admission logic live.
- **etcd** — a distributed, consistent key-value store holding *all* cluster state: every
  object's spec and status. It is the literal source of truth — if etcd is lost with no
  backup, the cluster's declared state (and with it, the cluster) is gone, even if every node
  and every running container is untouched. Production clusters run etcd as a 3- or 5-member
  Raft cluster for fault tolerance and back it up regularly.
- **Scheduler (`kube-scheduler`)** — watches the API server for Pods with no node assigned
  and picks a node for each, based on requested resources (`resources.requests`), node
  capacity, affinity/anti-affinity rules, and taints/tolerations. It only *decides*; it does
  not create the Pod's containers — that's the kubelet's job (1.3) on whichever node was
  chosen.
- **Controller manager (`kube-controller-manager`)** — a single binary that runs many
  independent **controllers** as goroutines/loops: the Deployment controller, ReplicaSet
  controller, Node controller, Job controller, and dozens more. Each controller watches one
  slice of state via the API server and drives it toward its desired spec — this is the
  reconciliation loop from 1.5, and it's what makes every object in this track "self-driving."
- **Cloud controller manager** — (in cloud-hosted clusters) the piece that talks to the cloud
  provider's API to provision things like LoadBalancer Services as real cloud load balancers,
  or to detect node termination. It keeps cloud-specific code out of the core control plane.
- **High availability**: production control planes run *multiple* API server replicas behind
  a load balancer, and an odd-sized etcd cluster (3 or 5 members) so a minority of node
  failures doesn't halt the cluster (Raft needs a majority quorum to keep serving writes).

### Worked example — watching the control plane in action

```bash
kubectl get componentstatuses
# NAME                 STATUS    MESSAGE
# scheduler            Healthy   ok
# controller-manager   Healthy   ok
# etcd-0               Healthy   ok

kubectl apply -f manifests/api-deployment.yaml
# deployment.apps/expense-api created

# Behind that one command:
# 1. kubectl sends a POST to the API server with the Deployment YAML as JSON.
# 2. The API server authenticates/authorizes the request, validates the object
#    schema, and writes it to etcd.
# 3. The Deployment controller (in kube-controller-manager) is watching Deployments;
#    it notices the new object and creates a ReplicaSet for it.
# 4. The ReplicaSet controller notices the new ReplicaSet has 0 of 3 desired Pods
#    and creates 3 Pod objects (unscheduled — no node yet).
# 5. The scheduler notices 3 unscheduled Pods and assigns each a node.
# 6. Each node's kubelet notices a Pod was assigned to it and starts the containers.
```

In this example, one `kubectl apply` fans out through five independent components that never
talk to each other directly — each only watches the API server for the slice of state it
cares about and reacts. This "watch and react" pattern (rather than one component
orchestrating the others) is why the control plane scales and survives individual component
restarts: nothing holds a synchronous call chain open.

### Why it's useful

Understanding *which* control-plane component does *what* is the difference between correctly
diagnosing "my Pod is stuck Pending" (scheduler — no node has room, or a taint isn't
tolerated) versus "my Deployment never created Pods" (controller manager — check the
Deployment/ReplicaSet controller's view) versus "nothing works and `kubectl` hangs" (API
server or etcd — the front door or the source of truth is down). Interviewers use "describe
what happens when you run `kubectl apply`" specifically to check this mental map.

## 1.3 — Cluster anatomy: worker nodes

### Definition

A **worker node** is a machine (VM or bare metal) that actually runs your containers, under
instruction from the control plane. Each node runs a small, fixed set of agents.

### Key Concepts

- **kubelet** — the agent on every node; it registers the node with the API server, watches
  for Pods assigned to *this* node, and drives the container runtime to start/stop containers
  to match each Pod's spec. It also runs the Pod's liveness/readiness/startup probes (Phase
  5) and reports Pod/node status back to the API server — this is the node-local half of the
  reconciliation loop, mirroring what the controller manager does cluster-wide.
- **Container runtime (via the CRI)** — the software that actually creates and runs
  containers (namespaces, cgroups, image pulls) — containerd or CRI-O, communicating with the
  kubelet over the **Container Runtime Interface (CRI)**, a standard gRPC API. This is why
  Kubernetes can support any CRI-compliant runtime rather than being hardwired to one; Docker
  Engine itself was dropped as a directly-supported runtime years ago (dockershim removal) in
  favor of runtimes that speak CRI natively.
- **kube-proxy** — a per-node agent that implements Service networking (Phase 3): it programs
  the node's iptables or IPVS rules so traffic to a Service's virtual IP gets load-balanced
  to one of the Service's backing Pods, including Pods on *other* nodes.
- **CNI plugin** — not a K8s-built component but a required one: a Container Network
  Interface plugin (Calico, Cilium, Flannel, AWS VPC CNI, …) gives every Pod its own routable
  IP and wires up the flat, NAT-free Pod-to-Pod network the whole cluster assumes exists.
- **Node capacity & allocatable resources** — each node reports its total CPU/memory
  (`capacity`) and what's actually schedulable after reserving some for the OS and kubelet
  itself (`allocatable`); the scheduler places Pods against `allocatable`, comparing it to the
  sum of `resources.requests` already promised on that node.

### Worked example — a node's view of the world

```bash
kubectl get nodes -o wide
# NAME       STATUS   ROLES    AGE   VERSION   INTERNAL-IP    KERNEL-VERSION
# node-1     Ready    <none>   30d   v1.30.2   10.0.1.11      5.15.0-1051-aws
# node-2     Ready    <none>   30d   v1.30.2   10.0.1.12      5.15.0-1051-aws

kubectl describe node node-1
# Capacity:      cpu: 4      memory: 16Gi
# Allocatable:   cpu: 3800m  memory: 15Gi     # some reserved for kubelet/OS
# Non-terminated Pods:  (7 in total)
#   expense-api-7d9...   250m (6%)   512Mi (3%)   <- from resources.requests
#   postgres-0            500m (13%)  1Gi (7%)
# Allocated resources:
#   cpu     2250m (59%)
#   memory  4608Mi (30%)
```

In this example, `kubectl describe node` shows exactly what the scheduler uses to decide
whether a new Pod fits: `Allocatable` capacity versus the sum of every already-scheduled
Pod's `resources.requests` (not actual live usage — the scheduler reasons about *requests*,
not real-time metrics). A Pod requesting `2000m` CPU would not fit on `node-1` here even
though the node is only using a fraction of it live, because the *requested* capacity is
already 59% committed.

### Why it's useful

"What's the difference between the scheduler and the kubelet" and "what does kube-proxy do"
are near-universal K8s interview questions, because they test whether you understand the
control-plane/node split: the scheduler makes a *placement decision* once, centrally; the
kubelet *executes and continuously enforces* that decision locally, on its own node, forever
(restarting containers, running probes) without needing to ask the control plane's permission
for routine work.

## 1.4 — The API server, the object model, and `kubectl`

### Definition

Every Kubernetes object — a Deployment, a Service, a ConfigMap — is a resource exposed by the
API server's REST API, identified by `apiVersion` + `kind` + `metadata.name` (and
`metadata.namespace` for namespaced resources). `kubectl` is a thin HTTP client over that API;
it has no special powers a raw `curl`/client-library call couldn't also make.

### Key Concepts

- **Every manifest has the same four top-level keys**: `apiVersion` (which version of which
  API group defines this kind — e.g. `apps/v1` for Deployments, `v1` for the legacy "core"
  group's Pods/Services/ConfigMaps/Secrets, `networking.k8s.io/v1` for Ingress),
  `kind` (the object type), `metadata` (name, namespace, labels, annotations — identity and
  bookkeeping, not behavior), and `spec` (the desired state you're declaring — the only part
  a controller reads to decide what to do).
- **`status` is not something you write.** Every object also has a `status` subresource that
  controllers write back to (e.g. a Deployment's `status.availableReplicas`, a Pod's
  `status.phase`). You declare `spec`; the cluster reports `status`. `kubectl get -o yaml`
  shows both.
- **API groups and versioning.** Kubernetes' API is organized into groups (`apps`, `batch`,
  `networking.k8s.io`, `autoscaling`, the unnamed core/legacy group as just `v1`), each
  independently versioned (`v1`, `v1beta1`, `v2`). This is why a Deployment is `apps/v1` but
  an HPA in this track's manifest is `autoscaling/v2` — different maturity, different group.
- **`kubectl` is a REST client, nothing more.** `kubectl get pods` issues a `GET` against
  `/api/v1/namespaces/<ns>/pods`; `kubectl apply -f x.yaml` issues a `PATCH` (server-side
  apply) or `POST`. Anything `kubectl` does, a Java/Go/Python client using the same
  credentials could do identically — useful to know when explaining how operators/controllers
  (which are just programs using client libraries) work.
- **Admission control.** Between "authenticated + authorized" and "persisted to etcd," the
  API server runs the object through admission webhooks/controllers — validating (reject bad
  objects) and mutating (e.g., inject a sidecar, set a default) — the extension point behind
  tools like Istio's sidecar injection or policy engines (OPA/Gatekeeper, Kyverno).

### Worked example — the same object, two ways

```bash
# kubectl:
kubectl get deployment expense-api -o yaml

# equivalent raw API call kubectl is making under the hood (illustrative):
curl -s --cacert ca.crt --header "Authorization: Bearer $TOKEN" \
  https://<api-server>:6443/apis/apps/v1/namespaces/default/deployments/expense-api
```

```yaml
# manifests/api-deployment.yaml — the four top-level keys
apiVersion: apps/v1        # API group "apps", version v1
kind: Deployment           # the object type this group/version defines
metadata:
  name: expense-api        # identity — how you and other objects reference it
  labels:
    app: expense-api        # metadata used for selection, not behavior
spec:                        # the ONLY part a controller reads to decide what to do
  replicas: 3
  # ...
```

In this example, `kubectl get -o yaml` and the raw `curl` call reach the identical endpoint —
proving `kubectl` has no privileged path into the cluster. And the Deployment manifest shows
the universal shape every object in this repo's `manifests/` directory follows: identity
(`metadata`) is separate from desired behavior (`spec`), which is what lets tools generically
diff, patch, and reconcile any object type the same way.

### Why it's useful

Recognizing `apiVersion`/`kind`/`metadata`/`spec` as a fixed pattern — rather than memorizing
each object's YAML independently — is what lets you read an unfamiliar CRD (Custom Resource
Definition) from a third-party tool (Argo CD, cert-manager, a service mesh) and immediately
know where the "what do I want" part lives. It's also the basis of `kubectl explain
deployment.spec.strategy`, which walks the same schema the API server validates against.

## 1.5 — Declarative desired state & the reconciliation loop

This is the *key idea* of Kubernetes, and what makes it fundamentally different from scripted
deploys or even simple restart-on-crash supervisors.

### Definition

**Reconciliation** is the pattern where a controller runs an infinite loop: observe the
current actual state, compare it to the declared desired state, and take the minimal action
to move actual state closer to desired — repeating forever, not just once after a change.

### Key Concepts

- **You declare *what*, not *how*.** A manifest says "I want 3 replicas of this Pod template"
  (`spec.replicas: 3`). It does not say "if a Pod dies, create a new one" — that reactive
  behavior is never written by you; it falls out for free because the controller's loop keeps
  re-checking desired vs. actual forever.
- **Level-triggered, not edge-triggered.** The controller doesn't react to the *event* "a Pod
  died" (edge-triggered — miss the event, miss the recovery). It repeatedly asks "how many
  matching Pods exist *right now*, and does that equal `spec.replicas`?" (level-triggered).
  This makes the system self-correcting even if the controller itself crashed and restarted,
  or an update was missed — on the next loop iteration it just re-derives the right action
  from current state, with no event history to replay or lose.
- **Every major object has its own controller running this same loop**: the ReplicaSet
  controller reconciles Pod count, the Deployment controller reconciles ReplicaSets (Phase
  2), the Endpoints/EndpointSlice controller reconciles which Pod IPs back a Service (Phase
  3), the HPA controller reconciles replica count against metrics (Phase 5). Once you
  understand one loop, you understand the shape of all of them.
- **Consequences that fall out of this design:**
  - **Self-healing is automatic** — a crashed Pod, a drained node, a manually `kubectl delete
    pod`'d Pod are all just "actual state now differs from desired," and the next loop tick
    fixes it, with no special-cased recovery code anywhere.
  - **Idempotency** — re-applying the same manifest twice is a no-op if actual state already
    matches; `kubectl apply` is safe to run repeatedly (unlike re-running an imperative
    "create" script, which would error on a duplicate).
  - **GitOps becomes possible** (Phase 5) — because the whole system is "make actual state
    match this declaration," a git repo *can be* the declaration, with a controller (Argo
    CD/Flux) doing nothing more than applying reconciliation at the level of "does the
    cluster match this git commit."
- **Declarative `kubectl apply` vs. imperative commands.** `kubectl apply -f manifest.yaml`
  computes a three-way merge (last-applied state, current live state, new desired state) and
  patches only the diff — safe to run repeatedly, safe for multiple people/pipelines to apply
  overlapping manifests. Imperative commands (`kubectl create`, `kubectl run`, `kubectl scale
  --replicas=5`) mutate live state directly and don't update any manifest — fine for a quick
  demo or debugging, but they drift the cluster away from whatever is in git, which is why
  production practice is "manifests in git, always `apply`, never ad-hoc imperative edits."

### Worked example — watching reconciliation converge

```bash
kubectl apply -f manifests/api-deployment.yaml
kubectl get pods -l app=expense-api
# NAME                           READY   STATUS    RESTARTS
# expense-api-7d9f6c5b8f-abcde   1/1     Running   0
# expense-api-7d9f6c5b8f-fghij   1/1     Running   0
# expense-api-7d9f6c5b8f-klmno   1/1     Running   0

# Simulate a failure — delete one Pod directly (imperative, bypassing the manifest):
kubectl delete pod expense-api-7d9f6c5b8f-abcde
# pod "expense-api-7d9f6c5b8f-abcde" deleted

kubectl get pods -l app=expense-api
# NAME                           READY   STATUS    RESTARTS
# expense-api-7d9f6c5b8f-fghij   1/1     Running   0
# expense-api-7d9f6c5b8f-klmno   1/1     Running   0
# expense-api-7d9f6c5b8f-pqrst   0/1     ContainerCreating   0   <- new Pod, unprompted
```

In this example, nobody ran `kubectl create pod` a fourth time. Deleting a Pod only changed
*actual* state (2 running instead of 3); the Deployment's `spec.replicas: 3` never changed, so
on its next reconciliation tick the ReplicaSet controller saw the gap and created a
replacement Pod — the same loop that would also fire if a node crashed, a container OOM-killed,
or the process itself exited. This is self-healing with zero recovery code written by you.

### Comparison table — imperative vs. declarative

| | Imperative (`kubectl create/run/scale`) | Declarative (`kubectl apply -f`) |
|---|---|---|
| What you specify | A command to run now | The end-state you want |
| Idempotent (safe to re-run)? | No — often errors on duplicates | Yes — no-op if already matching |
| Source of truth | Whatever's live in the cluster | The manifest (ideally in git) |
| Drift from git | Common — nothing recorded the change | None if all changes go through `apply` |
| Good for | Quick debugging, demos, one-offs | Production, CI/CD, GitOps |
| Rollback story | Manual — you must remember prior state | `kubectl rollout undo` / revert the git commit |

### Why it's useful

This is the mental model every other object in the track is an instance of. Once "declare
desired state, a controller reconciles toward it, forever, in a level-triggered loop" clicks,
a Deployment, a Service's endpoint list, an HPA's replica count, and a GitOps sync are all
recognized as the *same pattern* applied to different resources — which is exactly what makes
the rest of this track fast to learn instead of a pile of unrelated features.

## Summary / Key Takeaways

- Kubernetes automates the **operational loop** around many containers — scheduling,
  self-healing, scaling, rollout, service discovery, config, and storage — that doesn't scale
  as hand-scripted `docker run` commands across a fleet.
- The **control plane** (API server, etcd, scheduler, controller manager) decides; **worker
  nodes** (kubelet, container runtime via CRI, kube-proxy, a CNI plugin) execute. The API
  server is the only thing that talks to etcd; everything else talks to the API server.
- Every object is `apiVersion` + `kind` + `metadata` + `spec` — a REST resource `kubectl` is
  just an HTTP client for. You write `spec` (desired state); the cluster reports back
  `status`.
- The core mechanism is the **reconciliation loop**: level-triggered, continuous comparison
  of desired vs. actual state, which is why self-healing, idempotent `kubectl apply`, and
  GitOps all fall out of the same design rather than being separate features.
- **`kubectl apply` (declarative) is the production practice**; imperative commands
  (`create`/`run`/`scale`) mutate live state without updating any record of intent and drift
  the cluster away from git.
