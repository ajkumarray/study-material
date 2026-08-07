<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · config storage](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 5 — Health, Scaling & Production: Notes

What makes a K8s deployment production-grade: health probes, resource governance,
autoscaling, and the operational tooling around it. The `api-deployment.yaml` and
`api-hpa.yaml` manifests apply these.

## 5.1 — Health probes & resources

- **Probes** let K8s know a container's real state (not just "process running"):
  - **Liveness** — is it alive? If it fails, K8s **restarts** the container (recovers from
    deadlocks/wedged states). The manifest hits `/actuator/health/liveness`.
  - **Readiness** — is it ready for traffic? If it fails, K8s **removes the Pod from Service
    endpoints** (no restart) — used during warm-up or when a dependency is down, so traffic
    only goes to ready Pods. Hits `/actuator/health/readiness`.
  - **Startup** — for slow-starting apps; delays liveness until startup completes so a slow
    boot isn't mistaken for a failure.
- **Resource requests & limits:**
  - **Requests** — what the scheduler reserves (used to place Pods on nodes with capacity).
  - **Limits** — hard caps (cgroups); exceed CPU → throttled, exceed memory → **OOM-killed**.
  - Setting them prevents noisy-neighbor problems and enables bin-packing. They also define
    the Pod's **QoS class** (Guaranteed/Burstable/BestEffort), which affects eviction order.

## 5.2 — Autoscaling, namespaces, RBAC, rollout

- **HorizontalPodAutoscaler (HPA)** — automatically adjusts a Deployment's **replica count**
  based on metrics (CPU/memory or custom). The manifest keeps CPU ~70%, scaling 3→10 Pods.
  This is *horizontal* scaling (more Pods); **VPA** does *vertical* (bigger Pods), and the
  **Cluster Autoscaler** adds/removes *nodes* when Pods can't be scheduled.
- **Namespaces** — virtual clusters for isolation/organization (team/env boundaries), with
  per-namespace **resource quotas** and access control.
- **RBAC** — role-based access control: who/what (users, ServiceAccounts) can do which verbs
  on which resources. Least privilege for humans and workloads.
- **Rollout & rollback** — the Deployment's rolling update + `kubectl rollout undo` (Phase
  2) implement zero-downtime deploys and fast reverts natively (CI/CD Phase 4 strategies).

## 5.3 — Production ecosystem (know the names)

- **Helm** — the K8s package manager: template + version + parameterize manifests as a
  reusable **chart** (avoids copy-pasting YAML per environment). **Kustomize** — template-free
  overlays (base + per-env patches), built into `kubectl`.
- **GitOps** (Argo CD, Flux) — git is the source of truth for desired state; a controller
  continuously **syncs** the cluster to the repo. This extends K8s' reconciliation model to
  deployments (declarative, auditable, auto-reverting drift) — the natural home for the CI/CD
  (13) deploy stage.
- **Observability** — metrics (Prometheus), dashboards (Grafana), logs, tracing (System
  Design Phase 10). Probes + HPA depend on metrics.
- **Cost & right-sizing** — requests/limits and autoscaling tuning are where cloud spend is
  won or lost.
- **Service mesh** (Istio/Linkerd — mention) — mTLS, traffic shifting (canary), and
  observability at the network layer.

## Perspective

Production K8s = **health-aware, resource-governed, autoscaled, and declaratively managed**.
Probes give the control loop truth (restart the dead, route only to the ready); requests/
limits keep the cluster stable and schedulable; HPA matches capacity to load; and Helm/
Kustomize + GitOps make the whole desired state a versioned, auto-reconciled artifact. With
these, the reconciliation engine from Phase 1 runs your app reliably at scale — the payoff of
the entire infra half of this repo.
