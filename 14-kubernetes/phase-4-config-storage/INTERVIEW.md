<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · networking](../phase-3-networking/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Config, Secrets & Storage: Interview Q&A

⭐ = asked constantly.

**Q: ConfigMap vs Secret?** ⭐⭐
Both externalize configuration from the image and inject it as env vars or mounted files.
ConfigMaps hold non-sensitive config; Secrets hold sensitive values. The difference is
intent and handling — Secrets get (some) access controls and can be encrypted, but by
default they're only base64-encoded.

**Q: Are Kubernetes Secrets actually secure?** ⭐⭐
Not by default — they're base64-encoded, not encrypted, and readable by anyone with API/etcd
access. Secure them with encryption at rest for etcd, RBAC to limit access, and preferably an
external secrets manager (Vault, cloud secret stores) via the External Secrets Operator or
CSI driver.

**Q: What are PersistentVolumes and PersistentVolumeClaims?** ⭐⭐
A PV is a piece of cluster storage (a cloud disk/NFS), provisioned statically or dynamically
via a StorageClass. A PVC is a Pod's request for storage (size + access mode); K8s binds it
to a matching PV. The Pod mounts the PVC, so apps request storage without knowing the
underlying infrastructure.

**Q: Why do containers need PVs at all?** ⭐
Because container/Pod storage is ephemeral — data written to the container filesystem is lost
when the Pod restarts or reschedules. Durable data (databases, uploads) must live on a
PersistentVolume that outlives the Pod.

**Q: Deployment vs StatefulSet — when do you use each?** ⭐⭐
Deployment for stateless, interchangeable Pods (web/API). StatefulSet for stateful workloads
needing stable identity and their own persistent storage — databases, message brokers,
anything with per-instance data or clustering. StatefulSets give ordinal names, per-Pod PVCs,
and ordered lifecycle.

**Q: What does a StatefulSet provide that a Deployment doesn't?**
Stable ordinal Pod names (`db-0`, `db-1`) that persist across reschedules, a dedicated PVC
per Pod (each Pod always re-binds its own volume), stable DNS via a headless Service, and
ordered startup/scaling/termination — all requirements for stateful/clustered systems.

**Q: How do you inject config without rebuilding the image?** ⭐
Mount ConfigMap/Secret values as environment variables (`envFrom`/`valueFrom`) or as files
into the container. The same image then runs in every environment with different
config/secrets supplied by K8s — the 12-factor config principle.

**Q: Should you run your database inside Kubernetes?** *nuance*
You can (StatefulSet + PVCs), and it works, but many teams run production databases as managed
services (RDS, Cloud SQL) outside the cluster to offload backups, HA, and upgrades, keeping
K8s for stateless apps. Running stateful data in-cluster is viable with operators but adds
operational responsibility — know the trade-off.
