<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · networking](../phase-3-networking/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Config, Secrets & Storage: Notes

How K8s handles the things that *aren't* the container image: configuration, secrets, and
persistent data. See `manifests/config.yaml` and `postgres-statefulset.yaml`.

## 4.1 — ConfigMaps & Secrets

- **ConfigMap** — non-sensitive config as key/value pairs, kept **out of the image** so the
  same image runs everywhere with different config (12-factor; Docker Phase 3/5). Inject as
  **env vars** (`envFrom`, as the Deployment does) or mounted files.
- **Secret** — same idea for **sensitive** values (passwords, tokens, keys). ⚠️ By default
  Secrets are only **base64-encoded, not encrypted** — anyone with API access/etcd can read
  them. Harden with: **encryption at rest** (etcd), **RBAC** to restrict access, and ideally
  an **external secrets manager** (Vault, cloud secret stores via the External Secrets
  Operator/CSI driver). The manifest's Secret feeds the DB credentials to both the API and
  the Postgres StatefulSet.
- Changing a ConfigMap/Secret doesn't auto-restart Pods (unless mounted as files with a
  reloader) — a rollout picks up new env values.

## 4.2 — Volumes, PersistentVolumes & PVCs

Containers are ephemeral (Docker Phase 3) — Pod storage is lost on restart. For durable
data:

- **Volume** — storage attached to a Pod (many types: emptyDir, configMap, secret, cloud
  disks). An `emptyDir` lives only as long as the Pod.
- **PersistentVolume (PV)** — a piece of **cluster storage** (a cloud disk, NFS, etc.),
  provisioned by an admin or dynamically via a **StorageClass**.
- **PersistentVolumeClaim (PVC)** — a Pod's **request** for storage ("I need 5Gi
  ReadWriteOnce"). K8s **binds** the PVC to a matching PV (or dynamically provisions one).
  The Pod mounts the PVC. This decoupling (claim vs actual disk) lets apps request storage
  without knowing the infrastructure.
- **Access modes:** `ReadWriteOnce` (one node), `ReadOnlyMany`, `ReadWriteMany` (shared) —
  matters for whether multiple Pods can mount the same volume.

## 4.3 — StatefulSets (stateful workloads)

Deployments assume Pods are **interchangeable** — wrong for databases, which need stable
identity and their own durable disk. A **StatefulSet** provides:

- **Stable, ordinal Pod names** — `postgres-0`, `postgres-1`, … (not random hashes), stable
  across reschedules.
- **Stable per-Pod storage** — `volumeClaimTemplates` create **one PVC per Pod**, and that
  Pod always re-binds to *its* volume (postgres-0 keeps its data disk).
- **Stable network identity** — via a **headless Service** (Phase 3), each Pod gets a
  predictable DNS name for clustering/replication.
- **Ordered** startup/scaling/termination — important for primaries/replicas.

The manifest runs Postgres as a StatefulSet with a 5Gi PVC per Pod and a headless Service —
the correct pattern for a database on K8s. (Often, though, teams run stateful data stores as
**managed services** outside the cluster — RDS, Cloud SQL — and keep K8s for stateless apps;
know both options.)

## Perspective

Keep **config and secrets out of the image** (ConfigMap/Secret, injected per environment)
and **durable data out of the Pod** (PVCs bound to PersistentVolumes). Stateless apps →
Deployments; stateful apps needing identity + their own disk → StatefulSets. And treat
Secrets as *not* secure by default — layer encryption, RBAC, and ideally an external secrets
manager on top. This separation is what lets the same image run anywhere while data
survives Pods coming and going.
