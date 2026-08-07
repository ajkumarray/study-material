<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · workloads](../phase-2-workloads/NOTES.md) | [Phase 4 · config storage ➡](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 3 — Services & Networking: Notes

Pods are ephemeral and get new IPs constantly, so you can't address them directly. A
**Service** is the stable front door to a set of Pods, and an **Ingress** exposes Services
to the outside world. See `manifests/api-service.yaml`.

## 3.1 — Why Services; the types

- A **Service** gives a **stable virtual IP + DNS name** for a dynamic set of Pods and
  **load-balances** across them. Pods behind it can be created/destroyed/rescheduled; the
  Service address never changes. It selects Pods by **label** (`selector: app: expense-api`),
  decoupled from any Deployment.
- **Types:**
  - **ClusterIP** (default) — an internal-only virtual IP; reachable **within** the cluster.
    For service-to-service traffic (API → DB). The manifest's API Service is ClusterIP.
  - **NodePort** — opens a static port on **every node's IP**; basic external access, mostly
    for dev/testing.
  - **LoadBalancer** — provisions an external cloud load balancer (AWS ELB, GCP LB) with a
    public IP; the standard way to expose a Service in the cloud.
  - **(Headless, `clusterIP: None`)** — no load-balancing VIP; returns Pod IPs directly, for
    StatefulSets needing per-Pod DNS (Phase 4).

## 3.2 — Service discovery via DNS

- The cluster runs an internal **DNS** (CoreDNS). Every Service gets a name:
  `expense-api.<namespace>.svc.cluster.local` (short: `expense-api` within the namespace).
- So the API reaches Postgres by connecting to host **`postgres`** and Redis to **`redis`**
  — exactly the ConfigMap values in the manifests. **Name-based discovery, not IPs** — the
  same idea as Docker Compose networking (12), at cluster scale.
- **Labels/selectors** remain the glue: a Service routes to whatever Pods currently match its
  selector, so scaling or rolling the Deployment automatically updates the backend set.

## 3.3 — Ingress

- A **Service** of type LoadBalancer per app gets expensive and gives you no HTTP routing. An
  **Ingress** is a single entry point that routes **external HTTP(S)** to internal Services
  by **host and path**, and terminates **TLS** — one load balancer for many apps.
- The manifest routes `expenses.example.com/api` → the `expense-api` Service. You could add
  `/` → a frontend Service on the same host.
- Ingress is just the *rules*; you need an **ingress controller** (nginx, Traefik, cloud
  gateway) actually running in the cluster to implement them. (The newer **Gateway API** is
  the evolving successor with richer routing.)

## Mental model of K8s networking

- Every Pod gets its own IP; all Pods can reach each other directly (flat network, via the
  CNI plugin) — no NAT between Pods.
- **Services** provide stable, load-balanced addressing over ephemeral Pods (internal:
  ClusterIP).
- **Ingress/LoadBalancer** bridge from outside the cluster to Services.
- **DNS** turns Service names into addresses so nothing hardcodes IPs.
- **NetworkPolicies** (mention) restrict which Pods can talk to which — the firewall layer.

## Perspective

Because Pods come and go, **you never talk to Pods — you talk to Services**, which provide a
stable name + load balancing over the current Pods (found by label), with DNS for discovery
and Ingress for external HTTP routing. Get this layer right and the app's components find and
balance across each other automatically as they scale and reschedule — the networking
backbone of a resilient system.
