<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · architecture](../phase-1-architecture/NOTES.md) | [Phase 3 · networking ➡](../phase-3-networking/NOTES.md)
<!-- /nav -->

# Phase 2 — Pods, Deployments & ReplicaSets: Interview Q&A

⭐ = asked constantly.

**Q: What is a Pod?** ⭐⭐
The smallest deployable unit in K8s — one or more containers sharing a network namespace
(same IP/localhost) and storage, scheduled together. Usually one app container per Pod, with
optional sidecars. Pods are ephemeral and replaced (not healed) when they die.

**Q: Why don't you create Pods directly?** ⭐⭐
Bare Pods aren't self-healed or rolled out — if the node dies or the Pod crashes, nothing
recreates it. You use a controller (Deployment) that maintains a desired replica count and
manages updates, so Pods are automatically replaced and versioned.

**Q: Deployment vs ReplicaSet?** ⭐⭐
A ReplicaSet keeps N identical Pods running (self-healing the count). A Deployment manages
ReplicaSets and adds rolling updates and rollbacks. You write Deployments; they create/adjust
ReplicaSets under the hood as you change the Pod template.

**Q: How do labels and selectors work?** ⭐⭐
Objects carry labels (`app: expense-api`); controllers and Services use selectors
(`matchLabels`) to find the Pods they manage or route to. This label-based matching loosely
couples components — a Service targets Pods by label regardless of which Deployment created
them.

**Q: How does a rolling update work?** ⭐
K8s creates new-version Pods and terminates old ones gradually, bounded by
`maxUnavailable`/`maxSurge`, keeping the app available. New Pods must pass readiness before
receiving traffic. Changing the Deployment's Pod template (e.g. image) triggers it.

**Q: How do you roll back a bad deploy?** ⭐
`kubectl rollout undo deployment/<name>` reverts to the previous ReplicaSet, which K8s
retained — fast because the old spec still exists. `kubectl rollout status`/`history` show
progress and revisions.

**Q: What's the difference between a Deployment, StatefulSet, DaemonSet, and Job?**
Deployment: stateless, interchangeable replicas. StatefulSet: stable identity + per-Pod
storage (databases). DaemonSet: one Pod per node (agents). Job/CronJob: run-to-completion or
scheduled tasks. You pick the controller matching the workload's needs.

**Q: Why are Pods designed to be disposable?** *nuance*
Disposability enables self-healing, scaling, and zero-downtime rollouts — K8s can kill,
move, and recreate Pods freely to converge on desired state. It forces you to keep apps
stateless (state in volumes/DBs) and to rely on Services for stable addressing, which is
what makes the whole orchestration model work.
