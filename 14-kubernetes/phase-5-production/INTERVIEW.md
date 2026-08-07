<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · config storage](../phase-4-config-storage/NOTES.md)
<!-- /nav -->

# Phase 5 — Health, Scaling & Production: Interview Q&A

⭐ = asked constantly.

**Q: Liveness vs readiness probes?** ⭐⭐
Liveness checks if a container is alive; on failure K8s restarts it (recovers from
deadlocks). Readiness checks if it's ready for traffic; on failure K8s removes the Pod from
Service endpoints without restarting (used during warm-up or when a dependency is down). One
recovers hung containers; the other gates traffic.

**Q: What's a startup probe for?**
Slow-starting apps: it delays liveness checks until startup succeeds, so a long boot isn't
misread as a liveness failure and killed in a restart loop. Once it passes, liveness/readiness
take over.

**Q: Resource requests vs limits?** ⭐⭐
Requests are what the scheduler reserves to place a Pod (guaranteed minimum); limits are hard
caps enforced by cgroups. Exceeding the CPU limit throttles the container; exceeding the
memory limit gets it OOM-killed. They enable bin-packing, prevent noisy neighbors, and set
the Pod's QoS class.

**Q: How does autoscaling work in Kubernetes?** ⭐⭐
The HorizontalPodAutoscaler adjusts a Deployment's replica count based on metrics (e.g. keep
CPU ~70%), scaling Pods out under load and in when idle. VPA resizes Pods vertically, and the
Cluster Autoscaler adds/removes nodes when Pods can't be scheduled. HPA is the common one.

**Q: What are namespaces used for?** ⭐
Logical isolation within a cluster — separating teams, environments, or apps — with
per-namespace resource quotas and RBAC. Names are scoped to a namespace, so the same Service
name can exist in several. They organize and secure a shared cluster.

**Q: What is RBAC?**
Role-Based Access Control: Roles/ClusterRoles define permitted verbs on resources, bound to
users or ServiceAccounts via RoleBindings. It enforces least privilege for both humans and
workloads, controlling who/what can read/modify cluster objects.

**Q: What is Helm and why use it?** ⭐
Kubernetes' package manager: it templates, versions, and parameterizes manifests into a
reusable chart, so you deploy the same app across environments by changing values instead of
copy-pasting YAML. Kustomize is a template-free alternative using base + overlay patches.

**Q: What is GitOps?** *nuance*
An operating model where git is the single source of truth for desired cluster state, and a
controller (Argo CD/Flux) continuously reconciles the cluster to match the repo — auto-
reverting drift, giving auditability and easy rollback. It extends K8s' reconciliation loop
to the deployment process itself, and is where the CI/CD deploy stage naturally lives.
