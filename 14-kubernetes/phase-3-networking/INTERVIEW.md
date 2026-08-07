<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · workloads](../phase-2-workloads/NOTES.md) | [Phase 4 · config storage ➡](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 3 — Services & Networking: Interview Q&A

⭐ = asked constantly.

**Q: What is a Service and why is it needed?** ⭐⭐
A Service gives a stable virtual IP and DNS name plus load balancing over a dynamic set of
Pods selected by label. Pods are ephemeral with changing IPs, so you address the Service, not
Pods directly — it stays constant as Pods scale, die, and reschedule.

**Q: ClusterIP vs NodePort vs LoadBalancer?** ⭐⭐
ClusterIP (default) is internal-only, for service-to-service traffic. NodePort exposes a
static port on every node's IP (basic external access, mostly dev). LoadBalancer provisions
a cloud load balancer with a public IP — the standard cloud way to expose a Service
externally.

**Q: How does service discovery work?** ⭐⭐
Cluster DNS (CoreDNS) gives every Service a name like `expense-api.<ns>.svc.cluster.local`
(short `expense-api` in-namespace). Components connect by name — e.g. the API uses host
`postgres`/`redis` — so nothing hardcodes IPs and the Service load-balances to current
matching Pods.

**Q: What is an Ingress?** ⭐⭐
A single external entry point that routes HTTP(S) traffic to internal Services by host and
path and can terminate TLS — one load balancer serving many apps, instead of a LoadBalancer
Service per app. It needs an ingress controller (nginx/Traefik) running to implement the
rules.

**Q: Ingress vs LoadBalancer Service?**
A LoadBalancer Service exposes one Service via one cloud LB (L4). Ingress provides L7 HTTP
routing (host/path, TLS) for many Services behind one entry point/LB — cheaper and more
flexible for web traffic. Use LoadBalancer for non-HTTP or a single service; Ingress for
HTTP apps.

**Q: How does a Service know which Pods to route to?** ⭐
By label selector — it targets all Pods whose labels match (`app: expense-api`), regardless
of which Deployment created them. As Pods scale or roll, the Service's backend set updates
automatically. This label-based decoupling is core to K8s.

**Q: What is a headless Service?** *nuance*
A Service with `clusterIP: None` — no load-balancing VIP; DNS returns the individual Pod
IPs. Used with StatefulSets so each Pod has a stable, addressable DNS name
(`postgres-0.postgres…`), which stateful clustering/replication needs.

**Q: How do you restrict traffic between Pods?**
With NetworkPolicies — they define which Pods/namespaces may communicate (ingress/egress
rules by label), acting as a Pod-level firewall. By default all Pods can reach all Pods, so
NetworkPolicies are how you segment and enforce least-privilege networking.
