<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · workloads](../phase-2-workloads/NOTES.md) | [Phase 4 · config storage ➡](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 3 — Services & Networking: Interview Q&A

⭐ = asked constantly.

**Q: What is a Service and why is it needed?** ⭐⭐
A Service is a stable virtual IP and DNS name, plus load balancing, in front of a dynamic set
of Pods selected by label. Pods are ephemeral — they get new IPs whenever they're rescheduled,
scaled, or replaced after a crash — so nothing can safely hardcode a Pod's address. A Service's
name and virtual IP never change even as its backing Pods come and go, so you address the
Service, and Kubernetes handles routing that to whichever Pods currently match the selector
and are passing their readiness probe.

*Follow-up: how does the Service actually know which Pods are currently valid targets?* Via
`EndpointSlices` — a controller watches for Pods matching the Service's selector and Ready
(readiness probe passing), and maintains a live list of their IPs/ports. A Pod that hasn't
passed readiness is excluded from the EndpointSlice, so it never receives Service traffic even
though it matches the label selector.

**Q: What's the Kubernetes networking model at the Pod level, before Services even enter the
picture?** ⭐
Every Pod gets its own cluster-wide IP, and every Pod can reach every other Pod's IP directly
— across nodes, with no NAT — a flat network. Kubernetes doesn't implement this itself; it's a
hard requirement delegated to a CNI (Container Network Interface) plugin (Calico, Cilium,
Flannel, a cloud VPC CNI), which is a mandatory cluster add-on. That guarantee — IP-per-Pod, no
NAT between Pods — is what every higher networking layer (Services, NetworkPolicy) is built
on top of.

**Q: ClusterIP vs. NodePort vs. LoadBalancer vs. ExternalName — what's the difference and when
do you use each?** ⭐⭐
`ClusterIP` (the default) is a virtual IP reachable only from inside the cluster — the right
choice for service-to-service traffic like an API reaching its database. `NodePort` opens a
static port (30000–32767 by default) on every node's IP in addition to the ClusterIP, giving
basic external reachability without any cloud integration — mostly dev/test, or the plumbing
underneath a `LoadBalancer`. `LoadBalancer` additionally has the cloud controller manager
provision a real external cloud load balancer with a public IP pointed at that NodePort — the
standard way to expose one Service directly to the internet in a cloud cluster, at the cost of
one cloud LB (and its bill) per Service. `ExternalName` has no selector and does no proxying at
all — it's a DNS CNAME mapping an in-cluster Service name to an external hostname, letting code
address something outside the cluster (a managed database) using the same kind of name it uses
for internal Services.

**Q: How does service discovery actually work?** ⭐⭐
The cluster runs CoreDNS, which automatically creates a DNS record for every Service:
`<service-name>.<namespace>.svc.cluster.local`, with the short form `<service-name>` resolving
correctly for callers in the same namespace via DNS search-domain suffixes. Components connect
by name, never by IP — e.g. this repo's API connects to Postgres using the bare host `postgres`
(from its ConfigMap's `SPRING_DATASOURCE_URL`), and CoreDNS resolves that to the Postgres
Service's ClusterIP, which then load-balances to whichever Postgres Pod(s) are currently Ready.
Because resolution is namespace-relative, the identical manifest works unmodified in every
namespace that has its own `postgres` Service — the same trick Docker Compose's service-name
networking plays at single-host scale.

**Q: What is an Ingress, and why not just use a `LoadBalancer` Service for everything?** ⭐⭐
Ingress is a set of L7 (HTTP-aware) routing rules — by host and path — that map external
HTTP(S) requests to internal Services, plus TLS termination, all behind one entry point. A
`LoadBalancer` Service works at L4 and gives you exactly one IP → one Service; if you have ten
HTTP apps, that's ten cloud load balancers, ten public IPs, and no shared host/path routing or
centralized TLS. An Ingress, backed by one ingress controller (which itself typically sits
behind a single `LoadBalancer` Service), lets you route `api.example.com` and
`admin.example.com`, or `/api` and `/`, to different Services through that one entry point —
cheaper and more appropriate for HTTP traffic specifically. Non-HTTP traffic (raw TCP/UDP)
still needs a `LoadBalancer`/`NodePort` Service, since Ingress is HTTP-aware by design.

```yaml
spec:
  rules:
    - host: expenses.example.com
      http:
        paths:
          - path: /api
            pathType: Prefix
            backend:
              service: { name: expense-api, port: { number: 80 } }
```

*Follow-up: does the Ingress object do anything by itself?* No — it's purely declarative
routing rules. An **ingress controller** (nginx, Traefik, a cloud-managed gateway) must be
running in the cluster, watching Ingress objects, and programming its own proxy to match; the
object with no controller installed is inert.

**Q: How does a Service know which Pods to route to, and what happens when the Deployment
behind it scales or rolls out a new version?** ⭐
By label selector (`spec.selector`), matched against Pod labels — independent of which
Deployment or ReplicaSet revision created a given Pod. During a rolling update (Phase 2), both
the old and new ReplicaSet's Pods carry the same `app` label, so both are valid backends and
both receive traffic for the overlap window; as the rollout progresses and old Pods terminate,
they drop out of the Service's EndpointSlice automatically. Scaling the Deployment up or down
just changes how many Pod IPs are in that EndpointSlice — no manual reconfiguration of the
Service itself is ever needed.

**Q: What is a headless Service, and why does a StatefulSet need one?** ⭐
A Service with `clusterIP: None` — it has no virtual IP and does no load balancing; DNS queries
against it return the individual backing Pod IPs directly instead of one shared VIP. A
StatefulSet (Phase 4) pairs with a headless Service so each of its Pods gets a stable,
individually addressable DNS name (`postgres-0.postgres.default.svc.cluster.local`), which
matters for anything that needs to talk to *a specific instance* — a primary vs. a replica in
a database cluster, or peer discovery in a clustered system — rather than "any healthy
instance," which is all a normal Service can offer.

**Q: How do you restrict which Pods can talk to which?** ⭐
With NetworkPolicy objects — label-selected ingress/egress rules that act as a Pod-level
firewall. By default, with no NetworkPolicy applied, every Pod can reach every other Pod on the
flat cluster network; the moment a NetworkPolicy selects a Pod for a direction (ingress or
egress), that direction becomes default-deny for that Pod except what the policy explicitly
allows. A common pattern is a namespace-wide deny-all policy plus targeted policies permitting
only the specific traffic each service actually needs (e.g. only the API's Pods may reach
Postgres on 5432).

*Follow-up: does NetworkPolicy always work?* Only if the cluster's CNI plugin implements
enforcement (Calico and Cilium do; some minimal CNI plugins don't) — applying a NetworkPolicy
on a cluster whose CNI ignores it is a silent no-op, which is a real production gotcha worth
verifying, not assuming.

**Q: Ingress vs. Gateway API — what's the relationship?** *nuance*
The Gateway API is the newer, evolving successor to Ingress, splitting routing configuration
into more expressive, role-oriented objects (`Gateway`, `HTTPRoute`, and others) instead of
Ingress's single object plus controller-specific annotations for anything beyond basic
host/path matching (e.g. `nginx.ingress.kubernetes.io/rewrite-target`, which is nginx-specific
and not portable to another ingress controller). Ingress remains the more commonly deployed
object today; knowing Gateway API exists — and *why* it exists (to make advanced routing
behavior portable across implementations rather than locked behind vendor annotations) — is
enough for most interviews unless the role specifically works with it.

**Q: Why do Service `port` and `targetPort` differ, and does that matter?**
`port` is what other Pods/clients connect to on the Service's virtual IP; `targetPort` is the
actual port the container listens on. They're allowed to differ (this repo's Service uses
`port: 80` → `targetPort: 8080`) so callers can use a conventional port (80) while the
container keeps whatever port its runtime defaults to — the Service transparently translates
between them, and changing the container's listen port only requires updating `targetPort`,
never every caller's configuration.
