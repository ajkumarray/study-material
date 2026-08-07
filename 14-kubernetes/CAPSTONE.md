# Capstone — Deploy the Expense Stack on Kubernetes

The Kubernetes track's synthesis: the repo's expense stack running on a cluster — the Spring
API as a self-healing, autoscaled, load-balanced Deployment, backed by a Postgres
StatefulSet, all declared in `manifests/`.

> No cluster is available here, so the manifests are correct-by-construction, written to
> production standards, with the commands to apply them.

## The manifests, as a system

```
                      Ingress (expenses.example.com/api)
                                  │
                          Service: expense-api  (ClusterIP, load-balances)
                                  │  selector app=expense-api
              ┌───────────────────┼───────────────────┐
          Pod: api-xxx        Pod: api-yyy        Pod: api-zzz     ← Deployment (3 replicas,
          (probes, limits)                                            rolling update, HPA 3→10)
                                  │  env from ConfigMap + Secret
                          Service: postgres  (headless)
                                  │
                          Pod: postgres-0   ← StatefulSet + 5Gi PVC (durable data)
```

## Apply it (with a cluster + kubectl)

```bash
cd 14-kubernetes
kubectl apply -f manifests/            # config + secret + statefulset + deployment + svc + ingress + hpa
kubectl get pods,svc,hpa               # watch it converge
kubectl rollout status deployment/expense-api
kubectl set image deployment/expense-api api=ghcr.io/example/expense-api:1.1.0   # rolling update
kubectl rollout undo deployment/expense-api                                       # instant rollback
```

## Every phase, applied

| Phase | Concept | Manifest |
|---|---|---|
| 1 | Declarative desired state, reconciliation | every file — `kubectl apply` and controllers converge |
| 2 | Deployment/ReplicaSet, replicas, rolling update, labels | `api-deployment.yaml` (3 replicas, `RollingUpdate`) |
| 3 | Service (ClusterIP) + Ingress + DNS discovery | `api-service.yaml`; API reaches DB via host `postgres` |
| 4 | ConfigMap + Secret + StatefulSet + PVC | `config.yaml`, `postgres-statefulset.yaml` |
| 5 | Probes, resources, HPA, rollout/rollback | `api-deployment.yaml` (probes/limits), `api-hpa.yaml` |

## The whole infra story, end to end

This capstone closes the infra half of the repo:

1. **Docker (12)** built the image.
2. **CI/CD (13)** tested it, pushed it to a registry, and the deploy stage `kubectl apply`s
   these manifests.
3. **Kubernetes (14)** runs it: 3 replicas behind a Service, exposed by Ingress, autoscaled
   by the HPA, self-healed by the Deployment controller, with Postgres persisted via a PVC.
4. **System Design (17)** is the theory under all of it — replicas & load balancing,
   autoscaling, health/reliability, and zero-downtime rollout.

## The one-sentence takeaway

You declare the desired state — replicas, config, storage, health, scaling rules — and
Kubernetes' controllers relentlessly make it real and keep it real: the same containerized
app you built now runs resiliently at scale, updates without downtime, and rolls back in one
command.
