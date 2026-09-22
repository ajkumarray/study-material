<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · workloads ➡](../phase-2-workloads/NOTES.md)
<!-- /nav -->

# Phase 1 — Architecture & Declarative Model: Interview Q&A

⭐ = asked constantly.

**Q: What is Kubernetes and what problem does it solve?** ⭐⭐
Kubernetes is a container orchestrator: given a pool of machines and a declared desired
state, it schedules containers onto nodes with capacity, restarts or replaces failed ones
(self-healing), load-balances traffic across the Pods currently healthy, rolls out new
versions gradually with zero downtime, scales replica counts with demand, and distributes
config/secrets per environment. It solves the operational problem that appears the moment you
have more than a couple of containers spread across more than one machine — hand-scripting
placement, restart, rollout, and service discovery with `docker run` and SSH doesn't scale and
doesn't self-heal.

*Follow-up: is Kubernetes a replacement for Docker?* No — they solve different layers. Docker
(or another CRI-compliant runtime) builds images and runs individual containers on one host;
Kubernetes schedules and manages many containers across many hosts. A Kubernetes node still
needs a container runtime underneath it to actually execute containers.

**Q: Describe the cluster architecture in detail.** ⭐⭐
A cluster splits into a **control plane** and **worker nodes**. The control plane makes
decisions: the **API server** is the single front door — every read/write, from `kubectl` to
every controller, goes through it as an authenticated, authorized, validated REST call; only
it talks to **etcd**, the distributed key-value store holding all cluster state (the literal
source of truth); the **scheduler** watches for Pods with no assigned node and picks one based
on resource requests and constraints; the **controller manager** runs the many reconciliation
controllers (Deployment, ReplicaSet, Node, Job, …) that drive actual state toward each
object's declared spec. Worker nodes run the **kubelet** (starts/stops containers to match
Pods assigned to that node, runs health probes, reports status), a **container runtime**
(containerd/CRI-O, via the CRI) that actually executes containers, and **kube-proxy**
(programs iptables/IPVS rules for Service load balancing). A CNI plugin (not a built-in K8s
component) gives every Pod a routable IP on a flat, NAT-free network.

**Q: Walk through exactly what happens when you run `kubectl apply -f deployment.yaml`.** ⭐⭐
`kubectl` sends an HTTP request (effectively a server-side-apply PATCH) to the API server. The
API server authenticates the caller, authorizes the action (RBAC), runs the object through
admission control (validating/mutating webhooks), validates the schema, and persists it to
etcd. The Deployment controller, which is watching Deployments via the API server, notices the
new/changed object and creates or updates a ReplicaSet to match. The ReplicaSet controller
notices its Pod count doesn't match `spec.replicas` and creates Pod objects — initially
unscheduled. The scheduler notices unscheduled Pods and assigns each a node based on resource
requests and constraints. Each target node's kubelet notices a Pod was assigned to it and
instructs the container runtime to pull the image and start the container(s). No component in
this chain calls another directly — each only watches the API server for the slice of state
it cares about and reacts, which is why the control plane tolerates individual components
restarting.

**Q: What is etcd, and why does it matter that it's the *only* thing the API server persists
to?** ⭐
etcd is a distributed, Raft-consistent key-value store holding every object's spec and status
— all cluster state, full stop. Routing every write through the API server (rather than
letting other components touch etcd directly) means access control, validation, and admission
logic have exactly one enforcement point, and etcd's schema/format can change without every
controller needing to know about it. Production clusters run etcd as a 3- or 5-node cluster
(an odd number, for Raft quorum) and back it up regularly — losing etcd with no backup means
losing every object definition in the cluster, even if the nodes and running containers are
physically untouched.

**Q: What does the scheduler actually decide, and what does it *not* do?** ⭐
It decides *which node* a newly created, unassigned Pod should run on, based on the Pod's
`resources.requests`, the node's allocatable capacity, and constraints like node
affinity/anti-affinity and taints/tolerations. It writes that decision once (setting
`spec.nodeName` on the Pod) and is done — it does not start the container, does not monitor
the Pod afterward, and does not re-decide unless the Pod is deleted and recreated. Ongoing
health and lifecycle on that node is the kubelet's job, not the scheduler's.

**Q: kubelet vs. kube-proxy vs. the container runtime — what does each one do?** ⭐⭐
The kubelet is the per-node agent that watches for Pods assigned to its node and drives the
container runtime to start/stop containers to match, runs liveness/readiness/startup probes,
and reports Pod/node status back to the API server — it's the node-local half of
reconciliation. The container runtime (containerd/CRI-O, speaking the CRI to the kubelet) is
what actually creates namespaces/cgroups and runs the container process — the kubelet doesn't
run containers itself, it delegates. kube-proxy is unrelated to running containers; it
programs the node's iptables/IPVS rules so traffic sent to a Service's virtual IP gets
load-balanced to one of that Service's backing Pod IPs, including Pods on other nodes.

**Q: What is the declarative model and the reconciliation loop, precisely?** ⭐⭐
You declare desired state in a manifest's `spec` (e.g. "3 replicas of this Pod template") and
`kubectl apply` it; you never write the recovery logic. A controller runs an infinite loop:
observe current actual state, compare it to declared desired state, and take the minimal
action to close any gap — then repeat, forever. Crucially this is **level-triggered**, not
**edge-triggered**: the controller doesn't react to the *event* "a Pod died," it repeatedly
asks "does the current Pod count equal `spec.replicas`?" That makes the system self-correcting
even if the controller itself restarted or an update event was dropped — the next loop tick
just re-derives the right action from current state, with nothing to replay.

```bash
kubectl delete pod expense-api-7d9f6c5b8f-abcde   # actual state now: 2 of 3
# nobody re-runs "create pod" — the ReplicaSet controller's next reconcile tick
# sees 2 != spec.replicas: 3 and creates the third Pod, unprompted.
```

*Follow-up: why does level-triggered matter over edge-triggered?* Edge-triggered systems miss
recovery if they miss the event (a webhook that didn't fire, a controller that was down for
the one relevant tick). Level-triggered systems re-derive the correct action from current
state every tick, so a missed or delayed update self-corrects on the next pass — no event log
to replay, no special-cased "catch up" logic needed.

**Q: What's the difference between imperative and declarative `kubectl` commands, and why does
production favor declarative?** ⭐⭐
Imperative commands (`kubectl create`, `kubectl run`, `kubectl scale --replicas=5`) mutate
live cluster state directly and don't update any manifest — convenient for a one-off demo or
debugging, but running them drifts the cluster away from whatever's checked into git, and
they're generally not idempotent (re-running `create` on an existing name errors).
`kubectl apply -f manifest.yaml` submits a full desired-state document; the API server computes
a three-way merge against the last-applied state and current live state and patches only the
diff — safe to run repeatedly, safe for multiple pipelines to apply overlapping manifests
without stepping on each other's unrelated fields. Production practice is manifests in git,
`kubectl apply` (or a GitOps controller doing the same thing automatically) as the only path
to changing the cluster, so the git history is always an accurate record of "why" the cluster
looks the way it does.

**Q: What are the four things every Kubernetes manifest has, and what does each mean?**
`apiVersion` (which API group + version defines this object type — `apps/v1` for a
Deployment, `v1` for the legacy/core group's Pods, Services, ConfigMaps, and Secrets,
`autoscaling/v2` for an HPA), `kind` (the object type itself), `metadata` (identity —
`name`, `namespace`, `labels`, `annotations` — bookkeeping, not behavior), and `spec` (the
desired state — the *only* part any controller reads to decide what to do). Every object also
gets a `status` subresource that controllers write back to (e.g. a Deployment's
`status.availableReplicas`) — you never write `status` yourself; it's the cluster reporting
what's actually true.

**Q: Is `kubectl` privileged in any way a direct API call isn't?**
No. `kubectl` is a plain REST/HTTP(S) client using the same credentials (kubeconfig,
service account token) any other client library would use — `kubectl get pods` is a `GET`
against `/api/v1/namespaces/<ns>/pods`, and `kubectl apply` is (with server-side apply) a
`PATCH`. Anything `kubectl` does, a controller or operator written in Go/Python/Java against
the same API and credentials can do identically — this is in fact how every custom controller
and operator (Argo CD, cert-manager, a Helm-based operator) works.

**Q: What is admission control, and where does it run?** *nuance*
Between "the API server authenticated and authorized the request" and "the object is
persisted to etcd," the request passes through admission plugins: validating webhooks/
controllers that can reject a non-conformant object, and mutating webhooks/controllers that
can modify it in flight (inject a sidecar container, set a default resource limit, add a
label). This is the extension point behind things like Istio's automatic sidecar injection and
policy engines like OPA/Gatekeeper or Kyverno enforcing "every Deployment must set resource
limits" as a cluster-wide rule, without every team having to remember to do it themselves.
