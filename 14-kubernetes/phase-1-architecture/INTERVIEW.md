<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · workloads ➡](../phase-2-workloads/NOTES.md)
<!-- /nav -->

# Phase 1 — Architecture & Declarative Model: Interview Q&A

⭐ = asked constantly.

**Q: What is Kubernetes and what problem does it solve?** ⭐⭐
A container orchestrator that runs containers across a cluster of machines: it schedules
them, restarts failures (self-healing), load-balances, scales, and rolls out updates with
zero downtime — all from a declared desired state. It automates the operational work of
running many containers reliably at scale.

**Q: Describe the cluster architecture.** ⭐⭐
A control plane plus worker nodes. Control plane: API server (front door), etcd (state
store/source of truth), scheduler (places Pods), controller manager (reconciliation
controllers). Each worker node runs a kubelet (starts/monitors containers), a container
runtime, and kube-proxy (Service networking).

**Q: What is the declarative model / reconciliation loop?** ⭐⭐
You declare desired state in YAML (e.g. 3 replicas) and apply it; controllers continuously
compare desired vs actual state and act to close the gap. If a Pod dies, the controller
recreates it. You specify the *what*, and K8s figures out and maintains the *how* — it's
level-triggered, so it converges even after missed events or restarts.

**Q: What is etcd?** ⭐
The distributed key-value store that holds all cluster state — the source of truth. The API
server reads/writes it; controllers reconcile toward it. It must be backed up, since losing
etcd loses the cluster's declared state.

**Q: What does the scheduler do?**
It assigns newly created Pods to nodes based on resource requests, constraints
(affinity/anti-affinity, taints/tolerations), and available capacity. It decides *where* a
Pod runs; the kubelet on that node then actually runs it.

**Q: Imperative vs declarative kubectl?** ⭐
Imperative commands (`kubectl run/create/scale`) tell K8s to do something now. Declarative
`kubectl apply -f` submits desired-state manifests that K8s merges and reconciles toward.
Production uses declarative manifests in git (GitOps) for reproducibility and auditability.

**Q: What is self-healing and how does it work?** ⭐
K8s automatically replaces failed containers, reschedules Pods off dead nodes, and restarts
containers failing health checks — because controllers constantly reconcile actual state to
the declared replica count/health. You don't script recovery; the loop handles it.

**Q: What runs on a worker node?** *nuance*
The kubelet (agent that runs and monitors the node's assigned Pods and reports status), a
container runtime (containerd/CRI-O) that actually executes containers, and kube-proxy
(programs Service load-balancing/networking). The control plane decides; the node executes.
