<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · reliability](../phase-6-reliability/NOTES.md) | [Phase 8 · performance ➡](../phase-8-performance/NOTES.md)
<!-- /nav -->

# Phase 7 — Resource Management & Leaks: Interview Q&A

⭐ = asked constantly.

**Q: An app "hangs" under load and the DB looks stuck. What's your first hypothesis?** ⭐⭐
Connection-pool exhaustion: connections borrowed and not returned (missing `close()`, exception before close, or held across a slow call) drain the pool; new requests block waiting for a connection. Check pool metrics (active/idle/waiting), enable leak detection, and ensure try-with-resources everywhere. This is the real thing behind "the database has a memory leak."

**Q: What causes a memory leak in a garbage-collected language?** ⭐⭐
Objects that remain *reachable* but are never used again, growing without bound — the GC can't reclaim referenced objects. Classic sources: unbounded caches/collections, static registries, unremoved listeners, `ThreadLocal`s on pooled threads, and ClassLoader leaks on redeploy.

**Q: Why is "a cache without eviction" a memory leak?** ⭐
It only grows — every miss adds an entry, nothing is removed — until OutOfMemoryError. A real cache has a bound and an eviction policy (LRU/LFU/TTL) or a max-memory limit (Redis `maxmemory`). Size it to the working set.

**Q: How do you prevent leaked connections/streams?** ⭐
try-with-resources (or try-finally) guarantees `close()` runs even on exceptions — every `Connection`/`Statement`/`ResultSet`/stream is `AutoCloseable`. Add pool leak detection and sane timeouts as backstops.

**Q: Why must you `ThreadLocal.remove()` in a thread pool?** ⭐
Pool threads are reused indefinitely, so a `ThreadLocal` value set on one request stays reachable (and visible to the next request on that thread) forever. Remove it in a finally block. Otherwise: a slow leak plus subtle cross-request bugs.

**Q: How do you size a connection pool?**
Small — often ~(2 × cores + effective spindles), a small double-digit number (Java Phase 7.2). More connections than the DB can concurrently serve just adds contention. Set connection and query timeouts so a slow query can't hold a connection forever.

**Q: What's back-pressure and why bound your queues?** ⭐
An unbounded queue is a memory leak under sustained load — if producers outpace consumers it grows until OOM. A bounded queue applies back-pressure: it blocks or rejects producers, keeping the system alive (fail fast beats fall over). Same idea for bounded thread pools and rate limits.

**Q: What is database bloat, and what's VACUUM?** ⭐
In MVCC databases (Postgres), updates/deletes leave dead row versions; VACUUM reclaims them. If autovacuum lags (or a long-running transaction blocks it), tables and indexes bloat — more I/O, slower queries, growing disk. Keep transactions short and monitor dead-tuple ratios.

**Q: How would you find a memory leak in production?**
Watch heap trends (steadily rising old-gen after GC), capture heap dumps and diff dominators in Eclipse MAT (which object set grows?), review recent changes to caches/statics/ThreadLocals. For handle leaks: `lsof` (file descriptors), `jstack` (threads stuck on the pool).

**Q: "Too many open files" — what does it mean and how do you fix it?**
File-descriptor exhaustion: sockets/files/streams opened and not closed hit the OS ulimit. Fix the leak (close in finally / try-with-resources), and raise the ulimit only as a stopgap. Connections and threads count against limits too.
