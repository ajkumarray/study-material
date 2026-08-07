<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 14 — Kubernetes

**Run containers at scale, declaratively, with self-healing.** Docker (12) packages one
container; Kubernetes (K8s) runs *thousands* across a cluster of machines — scheduling them,
restarting failures, load-balancing traffic, rolling out new versions, and scaling to
demand. You declare the desired state; K8s continuously makes reality match.

Taught for someone who has containerized the stack (Docker 12) and built a CI/CD pipeline
(13). Kubernetes is the deploy target — where the images actually run in production.

> **Format note:** no cluster/`kubectl` is available here, so this track is **theory +
> real, correct-by-construction manifests** in `manifests/`, with the commands to apply
> them — the same approach as the Docker/CI-CD/Databases tracks. The YAML is written to
> real-world standards (probes, resources, HPA, StatefulSet).

## The manifests: `manifests/`

| File | Objects |
|---|---|
| `api-deployment.yaml` | **Deployment** (3 replicas, rolling update, liveness/readiness probes, resource requests/limits) |
| `api-service.yaml` | **Service** (ClusterIP) + **Ingress** (external HTTP routing) |
| `config.yaml` | **ConfigMap** (non-secret config) + **Secret** (credentials) |
| `api-hpa.yaml` | **HorizontalPodAutoscaler** (CPU-based autoscaling 3→10) |
| `postgres-statefulset.yaml` | **StatefulSet** + headless Service + per-Pod **PVC** (durable DB storage) |

```bash
# (with a cluster + kubectl)
kubectl apply -f manifests/
kubectl get pods,svc,hpa
kubectl rollout status deployment/expense-api
```

## Curriculum

### Phase 1 — Architecture & the declarative model ✅
- [x] 1.1 Why orchestration; what K8s does (scheduling, self-healing, scaling, rollout)
- [x] 1.2 Cluster anatomy: control plane (API server, scheduler, etcd, controllers) + nodes (kubelet)
- [x] 1.3 Declarative desired state & the reconciliation loop; `kubectl apply`
- NOTES · INTERVIEW

### Phase 2 — Pods, Deployments & ReplicaSets ✅
- [x] 2.1 Pods (the smallest unit) and why you rarely create them directly
- [x] 2.2 Deployments & ReplicaSets: replicas, labels/selectors, self-healing
- [x] 2.3 Rolling updates & rollbacks (`kubectl rollout`)
- NOTES · INTERVIEW

### Phase 3 — Services & networking ✅
- [x] 3.1 Why Services exist (Pods are ephemeral); ClusterIP / NodePort / LoadBalancer
- [x] 3.2 Service discovery via cluster DNS; labels & selectors as the glue
- [x] 3.3 Ingress: external HTTP routing, host/path rules, TLS
- NOTES · INTERVIEW

### Phase 4 — Config, secrets & storage ✅
- [x] 4.1 ConfigMaps & Secrets; injecting as env/volumes (config out of the image)
- [x] 4.2 Volumes, PersistentVolumes & PersistentVolumeClaims
- [x] 4.3 StatefulSets for stateful workloads (stable identity + per-Pod storage)
- NOTES · INTERVIEW

### Phase 5 — Health, scaling & production ✅
- [x] 5.1 Liveness/readiness/startup probes; resource requests & limits
- [x] 5.2 Autoscaling (HPA); namespaces; RBAC; rollout strategy & rollback
- [x] 5.3 Production concerns: Helm/Kustomize, GitOps, observability, cost
- NOTES · INTERVIEW

### Capstone ✅
- [x] Deploy the whole expense stack on K8s: API Deployment + Service + Ingress + HPA,
  Config/Secret, and Postgres StatefulSet. See `CAPSTONE.md`.

## How this connects

- **← Docker (12):** K8s schedules the images you built; a container is the unit it runs.
- **← CI/CD (13):** the pipeline's deploy stage `kubectl apply`s / rolls these manifests.
- **↔ System Design (17):** replicas, load balancing, autoscaling, health, and rollout are
  the scalability/reliability concepts, made concrete.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · architecture** — [Notes](phase-1-architecture/NOTES.md) · [Interview](phase-1-architecture/INTERVIEW.md)
- **Phase 2 · workloads** — [Notes](phase-2-workloads/NOTES.md) · [Interview](phase-2-workloads/INTERVIEW.md)
- **Phase 3 · networking** — [Notes](phase-3-networking/NOTES.md) · [Interview](phase-3-networking/INTERVIEW.md)
- **Phase 4 · config storage** — [Notes](phase-4-config-storage/NOTES.md) · [Interview](phase-4-config-storage/INTERVIEW.md)
- **Phase 5 · production** — [Notes](phase-5-production/NOTES.md) · [Interview](phase-5-production/INTERVIEW.md)
<!-- /phases-nav -->
