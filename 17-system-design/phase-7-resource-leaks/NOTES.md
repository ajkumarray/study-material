<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · reliability](../phase-6-reliability/NOTES.md) | [Phase 8 · performance ➡](../phase-8-performance/NOTES.md)
<!-- /nav -->

# Phase 7 — Resource Management & "Leaks": Notes

The unifying cause of every leak: **acquire a bounded resource, forget to release it.** The unifying fixes: **guarantee release** (try-with-resources / try-finally) and **bound everything** (pools, queues, caches). The demo reproduces the two most common in production.

## 7.1 — Connection-pool exhaustion (the real "database memory leak")
When someone says "the database is leaking," it's almost always the *application* leaking **connections**, not the DB leaking RAM. Connections are a small, bounded pool (HikariCP — Java Phase 7.2, default ~10). Code that borrows a connection and never returns it (missing `close()`, an exception before `close()`, a connection held across a slow external call) shrinks the pool until **every request blocks waiting for a connection that never comes** — the app "hangs," throughput → 0, and it looks like the DB died. (Demo: a pool of 3, leaked → 7 of 10 requests time out; pool at 0/3.)

**Fixes:**
- **try-with-resources / try-finally** so `close()` always runs (Java Phase 3.1). Every `Connection`, `Statement`, `ResultSet`, `InputStream` is `AutoCloseable`.
- **Leak detection:** HikariCP's `leakDetectionThreshold` logs a stack trace for connections held too long — the fastest way to find the offending code.
- **Size the pool** and set **connection/statement timeouts** so a slow query can't hold a connection indefinitely.
- Don't hold a DB connection across a network call to another service (release it first).

## 7.2 — Memory leaks (app and DB)
In a GC language a "leak" = **unintentionally reachable objects that grow without bound** (the GC can't collect what's still referenced). Common sources:
- **Unbounded caches/collections** — a `Map` you only ever `put` into. "A cache without an eviction policy is a memory leak." Fix: a **bounded cache** (LRU via `LinkedHashMap.removeEldestEntry`, Caffeine, or Redis with `maxmemory` + eviction). (Demo: unbounded → 100k entries; LRU capped at 1000.)
- **Static collections / registries** that accumulate and never remove.
- **Unremoved listeners/callbacks** (observer registrations) keeping objects alive.
- **`ThreadLocal` on pooled threads** — the thread lives forever (thread pool), so a value never `remove()`d stays reachable per thread. Always `remove()` in a finally, especially in web apps (request threads are reused).
- **ClassLoader leaks** on redeploy (advanced).

**DB-side memory:** the database itself can use lots of RAM legitimately (buffer/page cache, sort/work memory per query, prepared-statement/plan cache). Problems look like leaks but usually are: too-large `work_mem` × many connections, an ever-growing plan cache, or **table/index bloat** — in Postgres, dead tuples from updates/deletes that `VACUUM` reclaims (7.4).

## 7.3 — Other handle leaks & back-pressure
- **Thread leaks:** creating threads/executors without shutting them down; unbounded thread creation (use bounded pools — Java Phase 5.3). **File/socket handle leaks:** unclosed streams exhaust the OS file-descriptor limit ("too many open files").
- **Unbounded queues** are a memory leak under load: if producers outpace consumers, an unbounded queue grows until OOM. Use **bounded queues** so the system applies **back-pressure** (slows/rejects producers) instead of falling over (Java Phase 5.3's `ArrayBlockingQueue`). Fail fast > OOM.

## 7.4 — DB maintenance
- **VACUUM / bloat (Postgres):** MVCC leaves dead row versions after updates/deletes; autovacuum reclaims them. Disabled/lagging vacuum → table & index **bloat** → more I/O, slower queries, growing disk. Monitor dead-tuple ratio.
- **Long-running transactions** hold locks and prevent vacuum from cleaning rows still "visible" to them — a single forgotten open transaction can bloat the whole DB. Keep transactions short.
- **Lock contention / deadlocks** (Databases Phase 5): long locks serialize writers and cause timeouts. Diagnose with the DB's lock views; keep critical sections small.

**Diagnosing in production:** pool metrics (active/idle/waiting — actuator, Java Phase 6/Spring 8), heap dumps + a profiler (Eclipse MAT) for growing object sets, `jstack` for stuck threads waiting on the pool, `lsof` for FD leaks, DB dashboards for connections/bloat/long transactions.
