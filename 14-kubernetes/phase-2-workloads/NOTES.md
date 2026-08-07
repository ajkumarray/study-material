<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · architecture](../phase-1-architecture/NOTES.md) | [Phase 3 · networking ➡](../phase-3-networking/NOTES.md)
<!-- /nav -->

# Phase 2 — Pods, Deployments & ReplicaSets: Notes

The workload objects that actually run your containers. You almost never create a raw Pod;
you declare a **Deployment**, and the machinery below it keeps the right Pods running. See
`manifests/api-deployment.yaml`.

## 2.1 — Pods

- A **Pod** is the **smallest deployable unit** — one or more tightly-coupled containers
  that share a network namespace (same IP/localhost) and storage. Usually **one container
  per Pod** (your app); a second "sidecar" container (log shipper, proxy) is the exception.
- Pods are **ephemeral and disposable**: they get a new IP each time, can be killed/
  rescheduled anytime, and are never "healed" in place — a dead Pod is *replaced* by a new
  one, not resurrected. So **you don't manage Pods directly**; a controller does.
- This ephemerality is *why* Services (Phase 3) exist — you need a stable address in front
  of Pods that come and go.

## 2.2 — Deployments & ReplicaSets

- A **ReplicaSet** ensures a specified number of identical Pod replicas are running — if one
  dies, it creates a replacement (self-healing). You rarely touch it directly.
- A **Deployment** manages ReplicaSets and adds **rollouts**: it's the object you actually
  write for stateless apps. You set `replicas`, a **Pod template** (the container spec), and
  a **selector**. Changing the template triggers a controlled rollout (2.3).
- **Labels & selectors** are the glue of K8s: Pods carry **labels** (`app: expense-api`),
  and controllers/Services use **selectors** (`matchLabels`) to find the Pods they manage or
  route to. This label-based loose coupling (not hardcoded references) is fundamental — a
  Service finds Pods by label, independent of which Deployment made them.

## 2.3 — Rolling updates & rollbacks

- Update the image in the Deployment (or `kubectl set image`) and K8s performs a **rolling
  update**: it spins up new-version Pods and terminates old ones **gradually**, governed by
  `maxUnavailable`/`maxSurge` (the manifest allows 1 of each), so the service stays up
  throughout. New Pods must pass **readiness** (Phase 5) before receiving traffic.
- **Rollback:** K8s keeps rollout history; `kubectl rollout undo deployment/expense-api`
  reverts to the previous ReplicaSet — near-instant, because the old ReplicaSet's spec is
  retained. This is the deployment-strategy discipline (CI/CD Phase 4) implemented natively.
- Watch a rollout with `kubectl rollout status`; pause/resume for canary-ish control.

## Other workload controllers (know the names)

- **StatefulSet** — stable identity + storage for stateful apps (Phase 4, Postgres).
- **DaemonSet** — one Pod **per node** (log/metrics agents, CNI).
- **Job / CronJob** — run-to-completion tasks / scheduled tasks (batch, migrations).

## Perspective

Think in **desired replica count of a Pod template**, not individual containers. A
Deployment says "keep 3 of this image healthy and roll updates safely"; ReplicaSets enforce
the count, and labels/selectors wire everything together loosely. Because Pods are
disposable, you never nurse one back to health — you let the controller replace it. That
disposability is the whole reason K8s can self-heal, scale, and roll out without downtime.
