<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · workloads ➡](../phase-2-workloads/NOTES.md)
<!-- /nav -->

# Phase 1 — Architecture & the Declarative Model: Notes

Kubernetes is a **container orchestrator**: given a pool of machines and a description of
what you want running, it schedules containers onto machines, keeps them running, scales
them, and updates them — continuously, without you managing individual servers.

## 1.1 — Why orchestration

Running one container (Docker) is easy. Running a real system is not: you need many
replicas across many machines, automatic restart of crashed containers, load balancing,
rolling updates with zero downtime, scaling with demand, secret/config distribution, and
service discovery. Doing that by hand doesn't scale. **Kubernetes automates all of it.** It
gives you:

- **Scheduling** — place containers on nodes with capacity.
- **Self-healing** — restart failed containers, replace dead nodes' Pods, reschedule.
- **Scaling** — manual or automatic (HPA) replica counts.
- **Rollouts/rollbacks** — gradual, reversible version changes.
- **Service discovery & load balancing** — stable names/IPs for moving Pods.
- **Config & secret management** — inject per-environment.

## 1.2 — Cluster anatomy

A cluster = a **control plane** + **worker nodes**.

**Control plane** (the brain — decides desired state):
- **API server** — the front door; everything (`kubectl`, controllers) talks to it. The
  single source of truth's gateway.
- **etcd** — the distributed key-value store holding all cluster state (the source of
  truth). Backup this.
- **Scheduler** — assigns new Pods to nodes based on resources/constraints.
- **Controller manager** — runs the **controllers** (see 1.3) that drive reconciliation.

**Worker nodes** (the muscle — run the containers):
- **kubelet** — the agent on each node; starts/stops containers to match assigned Pods and
  reports health back.
- **Container runtime** — actually runs containers (containerd/CRI-O).
- **kube-proxy** — implements Service networking/load-balancing on the node.

## 1.3 — Declarative desired state & reconciliation

This is the *key idea*, and what makes K8s different from scripting deploys:

- You **declare desired state** in YAML manifests ("I want 3 replicas of this image") and
  `kubectl apply` it. You describe the *what*, not the *how*.
- **Controllers run reconciliation loops:** each continuously compares **desired state**
  (from the API server/etcd) to **actual state** and takes action to close the gap. A Pod
  died and now there are 2? The controller starts a 3rd. A node vanished? Its Pods are
  rescheduled elsewhere. Nobody scripts this — the loop just drives reality toward the
  declaration.
- **Consequences:** self-healing is automatic; changes are made by editing the declared
  state (GitOps-friendly — Phase 5); the system is **level-triggered** (converges to the
  target) not **edge-triggered** (react to one event), so it's robust to missed events and
  restarts.
- `kubectl apply -f` is declarative (merge to desired state); imperative commands
  (`kubectl run/create`) exist but manifests-in-git is the production practice.

## Perspective

Kubernetes is a **reconciliation engine over a cluster of machines**: a control plane holds
your declared desired state, and controllers relentlessly make the actual state match. Once
you internalize "declare what you want; controllers converge to it," every object (Pods,
Deployments, Services, HPA) is just another piece of desired state with a controller
watching it. That's the whole mental model the rest of the track builds on.
