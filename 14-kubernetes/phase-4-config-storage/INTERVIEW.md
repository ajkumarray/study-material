<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · networking](../phase-3-networking/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Config, Secrets & Storage: Interview Q&A

⭐ = asked constantly.

**Q: ConfigMap vs. Secret — what's actually different between them?** ⭐⭐
Mechanically they're nearly identical: both externalize key/value data from the container
image and get injected into Pods either as environment variables (`envFrom` for the whole
object, `valueFrom` for one key) or as mounted files, so the same image can run unmodified
across environments. The difference is intent and (in principle) handling: ConfigMaps are for
non-sensitive config; Secrets are for sensitive values and carry a `type` field (`Opaque`,
`kubernetes.io/tls`, `kubernetes.io/dockerconfigjson`) plus somewhat different default
tooling behavior. But by default, a Secret is *not* actually more protected at rest than a
ConfigMap — see the next question.

**Q: Are Kubernetes Secrets actually secure? What would you add before trusting them with
real credentials?** ⭐⭐
Not by default. A Secret's values are only **base64-encoded**, not encrypted — `kubectl get
secret x -o yaml` shows base64 that anyone with read access to the object (or direct etcd
access, since etcd isn't encrypted by default either) can trivially decode with `base64 -d`.
Production hardening layers three things on top, none of which is automatic: **encryption at
rest** for etcd (so the bytes aren't plaintext-readable even with raw disk/etcd access),
**RBAC** to restrict which users/ServiceAccounts can `get`/`list` Secret objects at all
(default-open read access within a namespace is the naive trap), and ideally an **external
secrets manager** (Vault, a cloud secret store) integrated via the External Secrets Operator or
a CSI secrets-store driver, so the actual long-lived credential is never persisted in etcd at
all — only a short-lived synced reference is.

```bash
kubectl get secret expense-secrets -o jsonpath='{.data.SPRING_DATASOURCE_PASSWORD}'
echo c2VjcmV0 | base64 -d   # secret   <- trivially reversible; base64 is an encoding, not encryption
```

*Follow-up: what's the difference between `data` and `stringData` on a Secret manifest?*
`data` requires you to supply already-base64-encoded values; `stringData` lets you write
plaintext in the manifest and the API server base64-encodes it on write — purely an
authoring convenience. The stored object always ends up with base64-encoded `data` either way,
so this doesn't change the "not encrypted" answer above.

**Q: What are PersistentVolumes and PersistentVolumeClaims, and why does Kubernetes split
storage into two objects instead of one?** ⭐⭐
A `PersistentVolume` (PV) is a piece of actual cluster storage — a cloud disk, an NFS export —
existing as its own object, provisioned statically by an admin or, much more commonly,
dynamically by a `StorageClass`'s provisioner on demand. A `PersistentVolumeClaim` (PVC) is a
Pod's *request* for storage — "I need 5Gi, ReadWriteOnce" — which Kubernetes binds to a
matching (or newly provisioned) PV; the Pod mounts the PVC, never the PV directly. The split
exists so application manifests describe *what* storage they need without knowing or caring
*how* the cluster provides it (which cloud, which disk type, which provisioner) — the same
separation of concerns as ConfigMap/Secret decoupling config from the image.

**Q: Why do containers need PersistentVolumes at all — what's wrong with just writing to the
container's own filesystem?** ⭐
Container and Pod storage is ephemeral by design: anything written to a container's own
filesystem is lost the moment the Pod restarts, is rescheduled, or is replaced (Phase 2's
"Pods are disposable" is exactly this). A database's data files, user-uploaded content, or any
state that must survive past a single Pod's lifetime has to live on storage that exists
*outside* and *independent of* the Pod — a PersistentVolume bound via a PVC — so a new Pod
instance (even with a brand-new identity, for a Deployment) can re-attach to the same durable
data on restart.

**Q: Deployment vs. StatefulSet — when do you use each, and what specifically does a
StatefulSet provide that a Deployment doesn't?** ⭐⭐
Use a Deployment for stateless, fully interchangeable Pods — a web/API tier where any replica
can serve any request and none of them has state that matters after it's gone. Use a
StatefulSet for workloads needing stable per-instance identity and their own persistent
storage — databases and anything else that clusters or replicates and needs to know "which
instance am I." Concretely, a StatefulSet gives: **stable, ordinal Pod names**
(`postgres-0`, `postgres-1`, …) that persist across reschedules instead of random hash
suffixes; **one PersistentVolumeClaim per Pod**, created from `volumeClaimTemplates`, where a
given ordinal always re-binds to *its own* PVC (never shuffled between replicas); **stable
per-Pod DNS** via a required headless Service (`clusterIP: None`), so you can address "the
specific Nth instance," not just "any healthy instance"; and **ordered** startup/scaling/
termination by default (0 before 1 before 2), which matters when a primary must be up before
replicas start.

```bash
kubectl delete pod postgres-0
kubectl get pods -l app=postgres
# postgres-0   1/1   Running   0   <- same name, same PVC re-attached — not a fresh random Pod
```

*Follow-up: what would go wrong if you ran Postgres as a Deployment instead?* A Deployment's
ReplicaSet creates Pods with random suffixes and no per-Pod storage guarantee by default — a
replaced Pod is a brand-new, unrelated identity with no automatic reconnection to any specific
prior disk, and there's no way to address "the primary specifically" versus "any Pod" through
a normal load-balancing Service. Running a clustered stateful system this way breaks its
assumptions about instance identity.

**Q: How do you inject config without rebuilding the image?** ⭐
Mount ConfigMap/Secret values as environment variables via `envFrom`/`valueFrom`, or as files
via a volume mount, into the container spec. The container image itself never contains
environment-specific values; the same image is deployed to every environment, and only the
ConfigMap/Secret objects differ per namespace/cluster — the 12-factor config principle made
concrete at the orchestration layer.

**Q: If you update a ConfigMap that's already injected into a running Pod as env vars, does
the Pod pick up the change?** *nuance*
No — env vars are set once when the container starts; updating the ConfigMap object doesn't
retroactively change a running container's already-initialized environment. You need to
trigger a new rollout (e.g. `kubectl rollout restart deployment/expense-api`) for new Pods to
start with the updated values. Mounted-**file** ConfigMaps behave differently: the kubelet
periodically syncs the mounted file's contents to match the ConfigMap, so the file on disk
inside a *running* Pod does update — but the application still has to notice the file changed
and reload it itself; Kubernetes doesn't restart or signal the process for you (some setups
add a sidecar "reloader" or trigger `SIGHUP` specifically to bridge that gap).

**Q: What are Kubernetes volume access modes, and why does it matter which one a workload
uses?**
`ReadWriteOnce` (RWO) — mountable read-write, but by only **one node** at a time; this is what
most cloud block storage (EBS, a GCE persistent disk) supports, and what a single-replica
Postgres StatefulSet uses. `ReadOnlyMany` (ROX) — many nodes, read-only. `ReadWriteMany`
(RWX) — many nodes, read-write concurrently, which needs a storage backend built for it (NFS,
a cloud file store) since most block storage physically can't be attached read-write to
multiple nodes at once. Picking the wrong mode for the backend (e.g. assuming RWX on plain EBS)
is a real, common misconfiguration that surfaces as a PVC stuck `Pending` or a second Pod
failing to mount.

**Q: What is a StorageClass, and what does "dynamic provisioning" mean?**
A StorageClass is a template describing *how* to create storage on demand — which provisioner
to invoke (e.g. `ebs.csi.aws.com`) and what parameters to pass it (disk type, IOPS tier). When
a PVC references a StorageClass (explicitly, or implicitly via the cluster's default), the
provisioner creates an actual backing disk and a matching PersistentVolume object automatically
— "dynamic provisioning" — instead of an admin having to pre-create PV objects one at a time
("static provisioning"). This is what lets the identical StatefulSet manifest run unmodified on
different clouds; only which StorageClass exists in that cluster differs.

**Q: Should you run your database inside Kubernetes as a StatefulSet, or use a managed
service?** *nuance*
Both are legitimate, and it's a real trade-off worth stating both sides of. Running it
in-cluster (StatefulSet + PVCs, often via a purpose-built Operator) keeps everything on one
platform, one deployment model, and avoids crossing a network boundary to a separate managed
service — but it makes your team responsible for backups, failover, and version upgrades that
a managed service (RDS, Cloud SQL, Aurora) would otherwise handle. Many teams split the
difference: StatefulSets in-cluster for lower-stakes or self-hosted-by-necessity stateful
systems, managed services for the primary production database where operational risk and
recovery time matter most. Knowing to raise this trade-off, rather than treating "StatefulSet"
as the automatic answer, is the actual signal being tested.

**Q: What's `emptyDir`, and how is it different from a PersistentVolumeClaim?**
`emptyDir` is a plain `Volume` type — a directory created empty when the Pod starts and deleted
permanently when the Pod is removed. It lives exactly as long as the Pod (surviving container
restarts *within* that Pod, since the Pod itself doesn't change), which makes it useful for
scratch space or sharing files between containers in one Pod — but it is **not** durable
storage; a rescheduled or replaced Pod gets a brand-new, empty `emptyDir`. A PVC-backed volume
is the opposite: it's bound to a PersistentVolume that exists independently of any Pod's
lifecycle and (for a StatefulSet) reliably re-attaches to the same ordinal Pod after that Pod
is deleted and recreated.
