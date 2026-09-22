<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · networking](../phase-3-networking/NOTES.md) | [Phase 5 · production ➡](../phase-5-production/NOTES.md)
<!-- /nav -->

# Phase 4 — Config, Secrets & Storage: Notes

How Kubernetes handles everything that *isn't* the container image: configuration, secrets,
and persistent data. Config and secrets keep the image environment-agnostic (12-factor);
storage keeps data alive across Pods that are, by design (Phase 2), disposable. See
`manifests/config.yaml` and `manifests/postgres-statefulset.yaml`.

## 4.1 — ConfigMaps

### Definition

A **ConfigMap** stores non-sensitive configuration as key/value pairs, decoupled from the
container image, so the identical image can run in every environment (dev/staging/prod) with
different config supplied at deploy time.

### Key Concepts

- **Injection has two shapes**: as **environment variables** (`envFrom` for the whole
  ConfigMap, or `valueFrom.configMapKeyRef` for a single key) or as **mounted files** (each key
  becomes a file in a mounted volume, its value the file's contents) — files are the right
  choice for config an app expects to read from disk (e.g. a `application.yml` or `nginx.conf`)
  rather than env vars.
- **A ConfigMap is just data — it has no behavior of its own.** Nothing about it is "active";
  it's referenced by Pods, and only takes effect through however a Pod's spec injects it.
- **Changing a ConfigMap does not restart Pods that already consumed it as env vars.** Env
  vars are set once at container start; updating the ConfigMap doesn't retroactively change a
  running container's environment. A new rollout (Phase 2) is needed to pick up the new
  values. **Mounted-file** ConfigMaps *do* update in place on the filesystem inside the running
  Pod (kubelet syncs periodically) — but the *application* still has to notice the file
  changed and reload it; Kubernetes doesn't do that for you (some setups add a sidecar
  "reloader" or `SIGHUP` trigger for this).
- **`immutable: true`** can be set on a ConfigMap (and Secret) to prevent any further updates —
  a small but real performance/safety win: the kubelet no longer needs to watch it for changes,
  and it protects against an accidental edit silently breaking every Pod using it.

### Worked example — from the repo's manifest

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: expense-config
data:
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/expenses
  SPRING_DATA_REDIS_HOST: redis
  SPRING_PROFILES_ACTIVE: prod
```

```yaml
# consumed in api-deployment.yaml:
containers:
  - name: api
    envFrom:
      - configMapRef:
          name: expense-config   # every key above becomes an env var in the container
```

```bash
kubectl create configmap expense-config --from-literal=SPRING_PROFILES_ACTIVE=staging --dry-run=client -o yaml
kubectl exec -it deploy/expense-api -- env | grep SPRING_PROFILES_ACTIVE
# SPRING_PROFILES_ACTIVE=prod    <- exactly the ConfigMap's value, no code change needed
```

In this example, `envFrom.configMapRef` injects **every** key in `expense-config` as an env
var in one line — no per-key wiring — which is exactly how the same `expense-api` image runs
with `SPRING_PROFILES_ACTIVE=staging` in one namespace and `prod` in another: only the
ConfigMap differs, the image is identical.

### Why it's useful

This is the mechanical implementation of the 12-factor "config in the environment, not in
code" principle (also seen in Docker Phases 3/5): the same built-once, tested-once image is
what actually ships to every environment, and only the ConfigMap (and Secret) change per
environment — eliminating an entire class of "worked in staging, broke in prod because the
image was rebuilt differently" bugs.

## 4.2 — Secrets

### Definition

A **Secret** is the same key/value injection mechanism as a ConfigMap, intended for sensitive
values (passwords, tokens, keys, certificates) — same `envFrom`/volume-mount consumption, but
with a `type` field and (in principle) tighter access controls.

### Key Concepts

- **⚠️ Not encrypted by default — only base64-encoded.** `kubectl get secret expense-secrets -o
  yaml` shows base64 text, trivially decodable (`base64 -d`) by anyone who can read the object
  — base64 is an *encoding*, not encryption, and provides zero confidentiality on its own.
  Anyone with API read access to Secrets (or direct etcd access, since etcd isn't encrypted by
  default either) can read every Secret's plaintext.
- **`stringData` vs. `data`.** You can write a Secret's values in `data` (you supply the
  base64 yourself) or `stringData` (you write plaintext and the API server base64-encodes it
  for you on write) — `stringData` exists purely for manifest-authoring convenience; the
  stored object always ends up with base64 in `data` either way.
- **Hardening a real deployment** layers several things, none of which is "on" by default:
  - **Encryption at rest for etcd** — so the Secret's bytes aren't plaintext-readable even by
    someone with raw etcd/disk access.
  - **RBAC** — restrict which users/ServiceAccounts can `get`/`list` Secret objects at all;
    "everyone in the namespace can read every Secret" is the naive default to avoid.
  - **An external secrets manager** (HashiCorp Vault, AWS/GCP/Azure secret stores) fetched via
    the **External Secrets Operator** or a **CSI secrets-store driver**, so the actual
    long-lived credential never lives in etcd at all — Kubernetes only holds a short-lived
    reference/synced copy.
- **Typed Secrets** — `type: Opaque` (the generic, arbitrary key/value default, used in this
  repo), `type: kubernetes.io/tls` (holds a cert + key, consumed by Ingress TLS termination —
  Phase 3), `type: kubernetes.io/dockerconfigjson` (registry credentials for pulling private
  images, referenced via `imagePullSecrets`). The `type` mostly documents intent and unlocks
  type-specific validation/consumption (e.g. Ingress TLS specifically expects
  `kubernetes.io/tls`).

### Worked example — from the repo's manifest

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: expense-secrets
type: Opaque
stringData:                 # plaintext here; the API server base64-encodes it on write
  SPRING_DATASOURCE_USERNAME: app
  SPRING_DATASOURCE_PASSWORD: secret
```

```bash
kubectl get secret expense-secrets -o jsonpath='{.data.SPRING_DATASOURCE_PASSWORD}'
# c2VjcmV0                              <- base64, NOT encrypted
echo c2VjcmV0 | base64 -d
# secret                                 <- trivially reversible by anyone with read access
```

In this example, the same `envFrom` pattern from 4.1 (`secretRef` instead of `configMapRef`)
injects the credentials into the API container, and — critically for Phase 4.3 — the
Postgres StatefulSet consumes the *same* Secret's password via `valueFrom.secretKeyRef`, so
the API and the database agree on one credential without either hardcoding it.

### Comparison table — ConfigMap vs. Secret

| | ConfigMap | Secret |
|---|---|---|
| Intended for | Non-sensitive config | Sensitive values (passwords, tokens, certs) |
| Storage encoding | Plaintext | base64 (not encryption) |
| Encrypted at rest by default? | No | No — must enable etcd encryption |
| Consumption | `envFrom`/`valueFrom`, or volume mount | Identical mechanism |
| `type` field | N/A | `Opaque`, `kubernetes.io/tls`, `kubernetes.io/dockerconfigjson`, … |
| Real security | None needed | Needs RBAC + encryption at rest + ideally an external secrets manager |

### Why it's useful

"Are Kubernetes Secrets secure" is one of the most common K8s interview traps — the honest,
correct answer ("no, not by default, here's what you layer on") signals real production
experience versus having only read the object's name.

## 4.3 — Volumes, PersistentVolumes & PersistentVolumeClaims

### Definition

Container/Pod storage is ephemeral by design (Docker Phase 3, Phase 2 of this track) — data
written to a container's own filesystem is lost on restart or rescheduling. Kubernetes' volume
system is the layered abstraction that provides storage lasting **exactly as long as the Pod**
(a plain `Volume`) up through storage that **outlives any individual Pod** (a
`PersistentVolume`/`PersistentVolumeClaim`).

### Key Concepts

- **`Volume`** — storage attached to a Pod, mounted into one or more containers, for the
  *Pod's* lifetime (not the container's — a container restart within the same Pod keeps the
  volume; a Pod being replaced does not, unless the volume type is itself durable). Common
  types: `emptyDir` (a scratch directory, created empty when the Pod starts, deleted when the
  Pod is removed — used for scratch space or sharing files between containers in one Pod),
  `configMap`/`secret` (project a ConfigMap/Secret's keys as files — 4.1/4.2), and various
  cloud-disk or network-filesystem types wired to real durable storage.
- **`PersistentVolume` (PV)** — a piece of **cluster storage** (a cloud disk, an NFS export,
  …), existing as its own API object independent of any Pod. Provisioned either **statically**
  (an admin creates PV objects ahead of time) or, far more commonly, **dynamically** via a
  **StorageClass** (a template telling Kubernetes *how* to provision storage on demand — which
  cloud disk type, which provisioner).
- **`PersistentVolumeClaim` (PVC)** — a Pod's **request** for storage: "I need 5Gi,
  `ReadWriteOnce`." Kubernetes **binds** the PVC to a matching (or dynamically provisioned) PV;
  the Pod then references the PVC by name in its volume mounts, never the PV directly. This
  claim/volume split is deliberate: an app author writes a PVC without knowing or caring
  whether the underlying disk is an AWS EBS volume, a GCE persistent disk, or on-prem NFS — the
  cluster's StorageClass and provisioner own that detail.
- **Access modes** — `ReadWriteOnce` (RWO: mountable read-write by Pods on **one node** at a
  time — most cloud block storage, and what the repo's Postgres PVC uses), `ReadOnlyMany`
  (RWX-read: many nodes, read-only), `ReadWriteMany` (RWX: many nodes, read-write — needs a
  storage backend that supports concurrent multi-node writers, like NFS or a cloud file store;
  most block storage does not).
- **Reclaim policy** — what happens to the underlying storage when its PVC is deleted:
  `Delete` (the PV and its backing disk are deleted too — the default for most dynamically
  provisioned classes) or `Retain` (the disk survives for manual recovery/inspection) — a real
  production decision for anything holding data you can't regenerate.

### Worked example — dynamic provisioning, conceptually

```yaml
# A StorageClass (usually cluster-provided, e.g. by the cloud provider) — the
# "how to make storage" template a PVC implicitly or explicitly references:
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
reclaimPolicy: Delete
```

```bash
kubectl get pvc
# NAME          STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS
# postgres-data-postgres-0   Bound   pvc-3f1a9c...                  5Gi        RWO            fast-ssd

kubectl get pv pvc-3f1a9c...
# NAME             CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS   CLAIM
# pvc-3f1a9c...    5Gi        RWO            Delete           Bound    default/postgres-data-postgres-0
```

In this example, no admin pre-created a 5Gi disk — the StatefulSet's `volumeClaimTemplates`
(4.4) created the PVC, the PVC referenced (explicitly or via the cluster default)
`StorageClass: fast-ssd`, and that StorageClass's provisioner (the AWS EBS CSI driver here)
created an actual EBS volume and a matching `PersistentVolume` object on demand — the "dynamic"
in dynamic provisioning.

### Why it's useful

This decoupling — app declares a *claim* (size + access mode), infrastructure supplies the
*volume* — is what lets the identical Postgres StatefulSet manifest run unmodified on any
cloud (each with its own StorageClass/provisioner) or on-prem, and is the same separation of
concerns as ConfigMap/Secret: application manifests describe *what* they need, never *how*
the cluster provides it.

## 4.4 — StatefulSets: stateful workloads

### Definition

A **Deployment** assumes Pods are fully interchangeable — fine for a stateless API, wrong for
a database, which needs a stable identity and its own durable disk that follows *that specific
instance* across restarts. A **StatefulSet** is the controller built for exactly that.

### Key Concepts

- **Stable, ordinal Pod names.** A StatefulSet named `postgres` with `replicas: 3` creates
  Pods named `postgres-0`, `postgres-1`, `postgres-2` — not random hash suffixes like a
  Deployment's Pods — and those names are **stable across reschedules**: if `postgres-1`'s
  node dies, its replacement is still named `postgres-1`, not a brand-new identity.
- **One PVC per Pod, via `volumeClaimTemplates`.** Instead of one shared volume for all
  replicas, `spec.volumeClaimTemplates` is a *template* the StatefulSet controller uses to
  create a **dedicated PVC per Pod** (`data-postgres-0`, `data-postgres-1`, …), and — crucially
  — a given ordinal **always re-binds to its own PVC**, even after being deleted and
  recreated. `postgres-0`'s data disk is always `postgres-0`'s data disk, never shuffled with
  another replica's.
- **Stable network identity via a headless Service** (Phase 3, `clusterIP: None`). Each Pod
  gets its own predictable DNS name — `postgres-0.postgres.default.svc.cluster.local` — which
  clustering/replication logic (e.g. "connect specifically to the primary,
  `postgres-0`") depends on; a normal load-balancing Service can't express "the specific Nth
  instance."
- **Ordered lifecycle.** By default, Pods are created, scaled, and terminated **in order**
  (`postgres-0` before `postgres-1` before `postgres-2` on scale-up; reverse order on
  scale-down), and each Pod must be Running and Ready before the next one is created —
  important for primary-first startup or replicas that need to find an already-running
  primary. (`podManagementPolicy: Parallel` opts out of this ordering when it isn't needed.)

### Worked example — from the repo's manifest

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
spec:
  serviceName: postgres     # must name a headless Service — gives stable per-Pod DNS
  replicas: 1
  selector:
    matchLabels: { app: postgres }
  template:
    metadata:
      labels: { app: postgres }
    spec:
      containers:
        - name: postgres
          image: postgres:16-alpine
          volumeMounts:
            - name: data
              mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:        # one PVC PER Pod, not one shared PVC
    - metadata: { name: data }
      spec:
        accessModes: ["ReadWriteOnce"]
        resources: { requests: { storage: 5Gi } }
---
apiVersion: v1
kind: Service
metadata: { name: postgres }
spec:
  clusterIP: None             # headless — DNS returns Pod IPs, not a load-balanced VIP
  selector: { app: postgres }
  ports: [{ port: 5432, targetPort: 5432 }]
```

```bash
kubectl apply -f manifests/postgres-statefulset.yaml
kubectl get pods -l app=postgres
# NAME         READY   STATUS    RESTARTS
# postgres-0   1/1     Running   0

kubectl delete pod postgres-0
kubectl get pods -l app=postgres
# NAME         READY   STATUS    RESTARTS
# postgres-0   1/1     Running   0            <- same name, same PVC re-attached, new process

kubectl get pvc
# NAME              STATUS   VOLUME       CAPACITY   ACCESS MODES
# data-postgres-0   Bound    pvc-3f1a9c   5Gi        RWO         <- survived the Pod's deletion
```

In this example, deleting `postgres-0` doesn't leave the cluster with a `postgres-1` (a
Deployment's ReplicaSet would just create *a* new Pod with a fresh random name); the
StatefulSet controller specifically recreates the ordinal `postgres-0` and re-attaches it to
the *same* `data-postgres-0` PVC — the database restarts against its existing data, not an
empty disk.

### Comparison table — Deployment vs. StatefulSet

| | Deployment | StatefulSet |
|---|---|---|
| Pod naming | Random suffix (`api-7d9f6c5b8f-abcde`) | Stable ordinal (`postgres-0`, `postgres-1`) |
| Pod identity across reschedule | New identity each time | Same ordinal identity preserved |
| Storage | Shared or none — Pods interchangeable | One PVC **per Pod**, always re-bound to the same Pod |
| Network identity | Only the Service's shared VIP | Per-Pod stable DNS, via a headless Service |
| Startup/scaling order | Unordered, parallel | Ordered by default (0, 1, 2, …) |
| Use for | Stateless apps (APIs, web tiers) | Databases, clustered/replicated stateful systems |

### Worked note — should you even run the database in-cluster?

Running Postgres as a StatefulSet, as this repo does, is a valid and common pattern (and the
one worth knowing cold for interviews), but it's also common for teams to run production
databases as **managed services outside the cluster** (RDS, Cloud SQL, Aurora) and reserve
Kubernetes for stateless application tiers — offloading backups, failover, and patching to the
managed service. Both are legitimate; StatefulSets (often via a purpose-built **Operator** —
e.g. the Postgres Operator, Zookeeper/Kafka operators) close the gap when running stateful
systems in-cluster is the right call for the team.

### Why it's useful

Recognizing *when a Deployment is the wrong tool* — anything needing "this specific instance,
with its own disk, addressable by its own name" — is a routine production/design-interview
signal; StatefulSet is the answer whenever the workload can't tolerate its Pods being fully
interchangeable.

## Summary / Key Takeaways

- **ConfigMap** externalizes non-sensitive config; **Secret** does the same for sensitive
  values but is only **base64-encoded by default, not encrypted** — real security needs etcd
  encryption at rest, RBAC, and ideally an external secrets manager.
- Both inject as **env vars** (`envFrom`/`valueFrom`, set once at container start — a rollout
  is needed to pick up changes) or as **mounted files** (which do update live on disk, though
  the app must notice and reload).
- **PersistentVolumeClaim** is an app's request for storage ("5Gi, RWO"); **PersistentVolume**
  is the actual cluster storage it binds to, usually created on demand by a **StorageClass**'s
  provisioner — the claim/volume split hides *which disk* from the application manifest
  entirely.
- **StatefulSet** gives stable ordinal Pod names, **one PVC per Pod that always re-binds to
  the same Pod**, per-Pod DNS via a headless Service, and ordered lifecycle — everything a
  Deployment's interchangeable-Pod model can't provide, which is exactly what databases and
  other clustered stateful systems need.
- Keep **config/secrets out of the image** and **durable data out of the Pod filesystem** —
  that separation is what lets the same image run anywhere while data survives Pods coming
  and going.
