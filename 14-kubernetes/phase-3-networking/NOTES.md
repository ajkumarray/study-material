<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · workloads](../phase-2-workloads/NOTES.md) | [Phase 4 · config storage ➡](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 3 — Services & Networking: Notes

Pods are ephemeral and get new IPs constantly (Phase 2), so you can't address them directly. A
**Service** is the stable front door to a set of Pods, and an **Ingress** exposes Services to
the outside world over HTTP. See `manifests/api-service.yaml`.

## 3.1 — The Kubernetes networking model

### Definition

Kubernetes assumes a **flat, NAT-free Pod network**: every Pod gets its own cluster-wide IP,
and every Pod can reach every other Pod's IP directly, on any node, without address
translation — regardless of which node either one is scheduled on.

### Key Concepts

- **This is a requirement K8s delegates, not implements.** The control plane and kubelet don't
  wire up this network themselves — a **CNI (Container Network Interface) plugin** (Calico,
  Cilium, Flannel, the AWS VPC CNI, …) does, and it's a mandatory cluster add-on, not optional.
  Different CNI plugins implement the same guarantee differently (overlay/VXLAN tunneling vs.
  native cloud VPC routing), which is why "what CNI does your cluster run" is a real
  operational question even though application manifests never mention it.
  interview-testable: "IP-per-Pod, no NAT between Pods" is the one guarantee every CNI must
  provide.
- **Every container in a Pod shares that one Pod IP** (Phase 2) — the Pod, not the container,
  is the addressable unit on this network.
- **Pod IPs are not stable.** A rescheduled or replaced Pod gets a new IP. This is precisely
  the problem Services solve — never hardcode a Pod IP into config or code.

### Why it's useful

This flat-network guarantee is what makes Service load balancing and cross-node communication
simple to reason about: an API Pod on `node-1` can reach a database Pod on `node-2` exactly as
if they were on the same host network, with no port-forwarding or NAT rules for you to manage
— that complexity is pushed down into the CNI layer, once, for the whole cluster.

## 3.2 — Services: why they exist, and the types

### Definition

A **Service** is a stable virtual IP and DNS name in front of a dynamic set of Pods, selected
by label, that load-balances traffic across whichever of those Pods are currently healthy.

### Key Concepts

- **Selector-based backend membership.** `spec.selector: { app: expense-api }` means "route to
  every Pod with this label," independent of which Deployment (or ReplicaSet revision)
  created them — during a rolling update (Phase 2), old and new Pods both match and both
  receive traffic for as long as both exist.
- **`EndpointSlices`** (the modern successor to the older, single `Endpoints` object) are what
  a Service actually load-balances across under the hood: a separate object, maintained by a
  controller that watches for Pods matching the selector and becoming Ready, listing their
  IPs and ports. A Pod that hasn't passed its readiness probe (Phase 5) is **not** in the
  EndpointSlice — this is the actual mechanism that keeps traffic off a not-yet-ready Pod.
- **Types:**
  - **`ClusterIP`** (default) — a stable, internal-only virtual IP, reachable only from inside
    the cluster. The right choice for service-to-service traffic (API → DB). The repo's API
    Service is `ClusterIP`.
  - **`NodePort`** — everything `ClusterIP` gives you, plus a static port (30000–32767 by
    default) opened on **every node's** IP, so `<any-node-ip>:<nodePort>` reaches the Service
    from outside the cluster. Basic and unglamorous, but it works without any cloud
    integration — mostly used for dev/test or as the plumbing a `LoadBalancer` Service is
    built on top of.
  - **`LoadBalancer`** — everything `NodePort` gives you, plus the cloud controller manager
    provisions a real external cloud load balancer (AWS ELB/NLB, GCP LB, …) with a public IP
    that forwards to the NodePort. The standard way to expose a Service directly to the
    internet in a cloud cluster — but one cloud LB (and its cost) per Service, which is part
    of why Ingress (3.3) exists for HTTP traffic.
  - **`ExternalName`** — no selector, no proxying at all; it's a **DNS CNAME** mapping the
    Service's cluster-DNS name to an external hostname (e.g. a managed database's endpoint
    outside the cluster). Lets in-cluster code address an external dependency by the same
    kind of Service name it uses for internal ones.
  - **Headless (`clusterIP: None`)** — no virtual IP and no load balancing at all; DNS returns
    the individual Pod IPs directly. Used with StatefulSets (Phase 4) where clients need to
    address a *specific* Pod, not "any Pod."

### Worked example — from the repo's manifest

```yaml
apiVersion: v1
kind: Service
metadata:
  name: expense-api          # in-cluster DNS: expense-api.<namespace>.svc.cluster.local
spec:
  type: ClusterIP
  selector:
    app: expense-api         # routes to Pods with this label — decoupled from the Deployment
  ports:
    - port: 80                # the Service's own port
      targetPort: 8080        # the container port traffic is forwarded to
```

```bash
kubectl get svc expense-api
# NAME          TYPE        CLUSTER-IP     PORT(S)   AGE
# expense-api   ClusterIP   10.96.34.201   80/TCP    5m

kubectl get endpointslices -l kubernetes.io/service-name=expense-api
# NAME                  ADDRESSTYPE   PORTS   ENDPOINTS
# expense-api-abc12     IPv4          8080    10.244.1.5,10.244.2.7,10.244.2.9

# scale down and watch the EndpointSlice shrink:
kubectl scale deployment expense-api --replicas=1
kubectl get endpointslices -l kubernetes.io/service-name=expense-api
# ENDPOINTS: 10.244.1.5     <- only the surviving, Ready Pod remains
```

In this example, `port: 80` is what other Pods connect to (`http://expense-api/...`);
`targetPort: 8080` is where the actual container listens — the Service does the translation,
so the container's listen port and the Service's advertised port never have to match. The
`EndpointSlice` is the live, controller-maintained list of *which Pod IPs currently back this
Service* — scaling the Deployment changes that list automatically, with zero manual
reconfiguration, because the EndpointSlice controller is just another reconciliation loop
watching Pods with a matching, Ready label.

### Comparison table — Service types

| Type | Reachable from | Gets a cloud LB? | Typical use |
|---|---|---|---|
| `ClusterIP` | Inside the cluster only | No | Service-to-service traffic (API → DB, API → cache) |
| `NodePort` | Any node's IP, from outside | No | Dev/test; the plumbing under `LoadBalancer` |
| `LoadBalancer` | Public internet, via a real cloud LB | Yes | Directly exposing one Service externally (non-HTTP, or a single app) |
| `ExternalName` | Internally, as a DNS alias | No | Addressing an external dependency by an in-cluster-style name |
| Headless (`clusterIP: None`) | Inside the cluster, per-Pod DNS | No | StatefulSets — clients need a *specific* Pod, not load balancing |

## 3.3 — Service discovery via cluster DNS

### Definition

Kubernetes runs an internal DNS server (**CoreDNS**, by default) that automatically creates a
DNS record for every Service, so components find each other by **name**, never by hardcoded
IP.

### Key Concepts

- **Naming scheme:** `<service-name>.<namespace>.svc.cluster.local`. Within the same
  namespace, the short form `<service-name>` alone resolves correctly (DNS search-domain
  suffixes fill in the rest) — which is exactly why the repo's ConfigMap can set
  `SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/expenses` using the bare name
  `postgres` and have it resolve from any namespace-local Pod.
- **This is the same idea as Docker Compose's service-name networking (Track 12), at cluster
  scale** — name-based discovery instead of IP-based, so nothing in application config needs
  to know or care about actual Pod/Service IPs, which change constantly.
- **DNS resolves to the Service's ClusterIP** for a normal Service (one stable virtual IP, load
  balanced under the hood) or **directly to Pod IPs** for a headless Service — the client's
  resolver behavior differs based on that one field (`clusterIP: None` or not), not on
  anything the client itself configures.

### Worked example

```bash
# From inside any Pod in the same namespace as the expense-api Service:
kubectl exec -it deploy/expense-api -- nslookup postgres
# Server:    10.96.0.10
# Name:      postgres.default.svc.cluster.local
# Address:   10.96.201.44         <- the Service's stable ClusterIP

kubectl exec -it deploy/expense-api -- nslookup postgres.default.svc.cluster.local
# (same result — fully-qualified form always works, short form works within-namespace)
```

In this example, the application never needed a `--set DB_HOST=10.96.201.44` anywhere — the
ConfigMap says `postgres`, CoreDNS resolves it, and if the Postgres Service (or the Pods
behind it) ever change, the name keeps working without touching the API's config at all.

### Why it's useful

Name-based service discovery is what makes manifests portable across environments (dev,
staging, prod) without templating a single IP address — the same `SPRING_DATASOURCE_URL:
jdbc:postgresql://postgres:5432/expenses` works in every namespace that has its own `postgres`
Service, because DNS resolution is namespace-relative.

## 3.4 — Ingress: external HTTP routing

### Definition

An **Ingress** is a set of routing rules — by host and path — that map external HTTP(S)
requests to internal Services, implemented by an **ingress controller** running in the cluster.
It gives you one entry point (and one load balancer) for many Services, instead of one
`LoadBalancer` Service (and one cloud LB, and its cost) per app.

### Key Concepts

- **Ingress is just a spec; something else executes it.** The `Ingress` object itself does
  nothing on its own — you need an **ingress controller** (nginx, Traefik, HAProxy, a cloud
  provider's managed gateway) deployed in the cluster, watching Ingress objects and
  programming its own proxy/load balancer to match. Different controllers support different
  annotations for controller-specific behavior (e.g. `nginx.ingress.kubernetes.io/
  rewrite-target`), which is why Ingress YAML is sometimes described as "portable in shape,
  not always portable in behavior" across controllers.
- **Host and path routing** — one Ingress can route `expenses.example.com/api` to one Service
  and `expenses.example.com/` to another, or split by hostname entirely
  (`api.example.com`, `admin.example.com`) — L7 (HTTP-aware) routing that a plain `Service`
  can't do.
- **TLS termination** — `spec.tls` references a Secret holding a certificate/key; the ingress
  controller terminates HTTPS at the edge and typically forwards plain HTTP internally,
  centralizing certificate management instead of every app handling its own TLS.
- **The Gateway API** is the newer, evolving successor to Ingress — a more expressive,
  role-oriented API (separate `Gateway`, `HTTPRoute`, etc. objects) designed to fix Ingress's
  reliance on controller-specific annotations for anything beyond basic host/path routing.
  Worth knowing the name exists even where Ingress remains the day-to-day object in a repo
  like this one.

### Worked example — from the repo's manifest

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: expense-ingress
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /   # controller-specific: strip the matched prefix
spec:
  rules:
    - host: expenses.example.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service:
                name: expense-api
                port:
                  number: 80
```

```bash
# with an ingress controller (e.g. ingress-nginx) installed and DNS pointed at it:
curl https://expenses.example.com/api/expenses
# -> ingress controller matches host+path -> forwards to Service expense-api:80
#    -> Service load-balances to one of the expense-api Pods on :8080

# adding a second app on the same host, same Ingress:
#   - path: /
#     pathType: Prefix
#     backend:
#       service: { name: expense-frontend, port: { number: 80 } }
```

In this example, one Ingress object, behind one ingress controller (and typically one cloud
load balancer pointed at that controller), fans a single public hostname out to two entirely
separate Services by path — the `expense-api` Service never needs its own public IP or LB at
all; only the ingress controller does.

### Comparison table — Ingress vs. `LoadBalancer` Service

| | `LoadBalancer` Service | Ingress |
|---|---|---|
| OSI layer | L4 (TCP/UDP) | L7 (HTTP/HTTPS-aware) |
| Routing | One Service, one IP | Host/path rules across many Services |
| TLS termination | Not built in (or per-LB config) | Centralized, via `spec.tls` |
| Cloud LB cost | One per Service | One for the whole ingress controller, shared |
| Non-HTTP traffic (raw TCP/UDP, gRPC without HTTP/1.1 fallback edge cases) | Works natively | Needs an HTTP-aware path, or a separate mechanism |
| Needs an extra component running? | No | Yes — an ingress controller |

## 3.5 — Securing Pod-to-Pod traffic: NetworkPolicy

### Definition

By default, every Pod in a Kubernetes cluster can send traffic to every other Pod — there is
no implicit isolation. A **NetworkPolicy** is a label-selected firewall rule restricting which
Pods may send (`egress`) or receive (`ingress`) traffic to/from which other Pods, namespaces,
or IP blocks.

### Key Concepts

- **Opt-in, additive, and default-allow until you say otherwise.** With no NetworkPolicy
  applied to a Pod, all traffic is allowed. The moment *any* NetworkPolicy selects a Pod for a
  given direction (ingress or egress), that direction becomes **default-deny** for that Pod
  except what the policy's rules explicitly allow — so a common pattern is one policy per
  namespace that denies all ingress by default, plus targeted policies that allow specific,
  needed traffic.
- **Requires CNI support.** Like Pod networking itself, NetworkPolicy enforcement isn't done
  by the API server — it's implemented by the CNI plugin (Calico, Cilium support it; the most
  basic CNI plugins may not). Applying a NetworkPolicy on a cluster whose CNI doesn't enforce
  it is a silent no-op — a real operational gotcha worth knowing.
- **Selects by label, not by IP**, matching the rest of Kubernetes' networking model — rules
  say "allow from Pods labeled `app: expense-api`," not "allow from `10.244.1.5`."

### Worked example — restricting Postgres to only the API

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: postgres-allow-api-only
spec:
  podSelector:
    matchLabels:
      app: postgres              # this policy applies to Postgres Pods
  policyTypes: ["Ingress"]
  ingress:
    - from:
        - podSelector:
            matchLabels:
              app: expense-api    # only Pods labeled app=expense-api may connect
      ports:
        - protocol: TCP
          port: 5432
```

In this example, once this NetworkPolicy exists, Postgres Pods only accept inbound traffic on
5432 from Pods labeled `app: expense-api` — a debug Pod, a compromised unrelated workload, or
any other tenant sharing the cluster can no longer reach Postgres directly, even though they're
all on the same flat Pod network described in 3.1.

### Why it's useful

Understanding "flat network by default, opt-in isolation via NetworkPolicy" is the basis of
practical multi-tenant cluster security and of the principle of least privilege at the
network layer — the same idea Track 15/16 (Redis/Kafka) and Track 17 (system design) assume
when they talk about restricting blast radius between services sharing infrastructure.

## Mental model of K8s networking, end to end

- Every Pod gets its own IP; all Pods can reach each other directly (flat, NAT-free network
  via the CNI) — but by default, nothing *restricts* that reachability.
- **Services** provide stable, load-balanced addressing over the current set of Ready Pods
  (found by label) — internal by default (`ClusterIP`).
- **Ingress** (backed by an ingress controller) bridges external HTTP(S) traffic into
  Services, with host/path routing and TLS termination; `LoadBalancer`/`NodePort` Services
  bridge external L4 traffic directly, one Service at a time.
- **CoreDNS** turns Service (and, for headless Services, Pod) names into addresses, so nothing
  in application config hardcodes an IP.
- **NetworkPolicies** restrict which Pods may talk to which — the opt-in firewall layer on top
  of an otherwise fully-open Pod network.

## Summary / Key Takeaways

- **Never talk to Pods — talk to Services.** Pod IPs are not stable; a Service's virtual IP +
  DNS name is, and it load-balances across whichever Pods are currently Ready (tracked via
  EndpointSlices, which drop not-yet-ready Pods automatically).
- **`ClusterIP`** = internal only (service-to-service); **`NodePort`** = a port on every node;
  **`LoadBalancer`** = a real cloud LB; **`ExternalName`** = a DNS alias to something outside
  the cluster; **headless (`clusterIP: None`)** = no load balancing, per-Pod DNS (used by
  StatefulSets).
- **Cluster DNS (CoreDNS)** gives every Service a name (`<svc>.<ns>.svc.cluster.local`, short
  form within-namespace) — the mechanism behind name-based config like `postgres:5432` instead
  of a hardcoded IP.
- **Ingress** is L7 HTTP(S) routing (host/path, TLS) across many Services behind one entry
  point/LB, implemented by a separately-installed ingress controller — cheaper and more
  flexible than one `LoadBalancer` Service per app, for HTTP traffic specifically.
- The Pod network is **open by default**; **NetworkPolicy** is the opt-in, label-based
  firewall that restricts Pod-to-Pod traffic, and it only works if the cluster's CNI plugin
  supports enforcing it.
