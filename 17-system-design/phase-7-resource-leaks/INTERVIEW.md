<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · reliability](../phase-6-reliability/NOTES.md) | [Phase 8 · performance ➡](../phase-8-performance/NOTES.md)
<!-- /nav -->

# Phase 7 — Resource Management & Leaks: Interview Q&A

⭐ = asked constantly.

**Q: An app "hangs" under load and it looks like the database is stuck. What's your
first hypothesis and how do you confirm it?** ⭐⭐
Connection-pool exhaustion — not the database actually being down or out of memory, but
the application having leaked connections until none remain to borrow. A connection pool
is small and bounded by design (HikariCP defaults to around 10); connections borrowed
and never returned — a missing `close()`, an exception thrown before `close()` runs
without try-with-resources, or a connection deliberately held open across a slow
external call — permanently shrink the pool until every new request blocks waiting for a
connection that will never come. It looks exactly like "the database is stuck" because
requests are hanging on database access, but the database itself is typically healthy
and idle underneath. Confirm it fast by checking pool metrics (active/idle/waiting
counts via Actuator) rather than the database server's own dashboards — a pool sitting
at `active == max` with requests queued waiting is the smoking gun. Then enable
HikariCP's `leakDetectionThreshold` to get a stack trace pointing directly at the
offending code, and audit for missing try-with-resources.

**Q: What causes a memory leak in a garbage-collected language like Java?** ⭐⭐
Not literally lost memory — the GC reclaims anything genuinely unreachable. A leak is
objects that remain unintentionally *reachable*: something still holds a reference to
them, so the collector correctly considers them still in use and never frees them, and
the set of such objects grows without bound over time. Classic sources: unbounded
caches/collections (a `Map` that's only ever `put` into, never evicted from), static
registries that accumulate and never remove entries, listeners/observers registered on a
long-lived subject and never unregistered, `ThreadLocal` values set on pooled threads
and never explicitly removed, and (an advanced case) ClassLoader leaks on application
redeploy, where a lingering reference from the old deployment prevents its entire
classloader — and everything it loaded — from being collected.

**Q: Why is "a cache without an eviction policy" specifically called a memory leak?** ⭐
Because it has the exact same failure signature: it only grows, with nothing ever
removed, until it eventually exhausts available memory and throws OutOfMemoryError —
functionally identical to any other unbounded, always-reachable collection, just wearing
the label "cache" instead of "bug." A real cache has an explicit bound and an eviction
policy — LRU, LFU, or TTL (Phase 2.6) — or, for an external store like Redis, a
`maxmemory` limit with a configured eviction policy. In the demo, a plain `HashMap`
given 100,000 puts ends with 100,000 live entries and would keep growing; the same
100,000 puts into a `LinkedHashMap` configured as an LRU cache (overriding
`removeEldestEntry`) stays capped at exactly 1,000 entries regardless of how many more
puts occur.

**Q: How do you prevent leaked connections, streams, and other closeable resources?** ⭐
try-with-resources (or, in languages/versions without it, an explicit try-finally)
guarantees `close()` runs even when an exception is thrown partway through — every
`Connection`, `Statement`, `ResultSet`, and I/O stream in Java implements
`AutoCloseable` for exactly this reason. In the demo, the leaky version borrows a
connection, uses it, and simply never calls `close()` — with a pool of 3 and 10
concurrent tasks, only the first 3 succeed and the other 7 time out waiting, with the
pool permanently stuck at 0 available afterward. Wrapping the exact same borrow in
`try (Connection c = pool.borrow(...)) { ... }` fixes it completely: all 10 tasks
succeed because the connection is returned to the pool the instant each block exits, so
the same 3 connections simply get reused in waves. Beyond the code fix, add leak
detection (a configurable threshold that logs a stack trace for any connection held too
long) and sane timeouts as production backstops.

**Q: Why must you call `ThreadLocal.remove()` when running inside a thread pool?** ⭐
Pool threads are reused across many requests indefinitely — a `ThreadLocal` value set
during one request's handling stays attached to the *thread*, not to that request, for
as long as the thread lives, which in a web server is essentially forever (until the
process restarts). If you never call `.remove()`, this is both a slow memory leak (the
value accumulates as reachable state on a long-lived thread) and, more subtly, a
correctness bug: the *next* request that happens to be handled by the same pooled thread
can see stale data left over from a completely different, earlier request unless the
value was explicitly cleared. Always `.remove()` in a `finally` block so cleanup happens
regardless of whether the request handling succeeded or threw.

**Q: How do you size a database connection pool?**
Keep it small — a common starting formula is roughly `(2 × CPU cores) + effective
spindle count`, typically landing in the small double digits rather than the hundreds
many developers instinctively reach for (Java Phase 7.2). More connections than the
database can genuinely serve concurrently just adds contention on the database side
without improving throughput — the bottleneck is the database's own capacity to execute
queries in parallel, not the number of open sockets. Pair pool sizing with connection
acquisition timeouts and query timeouts, so a slow query can't hold a connection (and
therefore a slot in an already-scarce pool) indefinitely.

**Q: What is back-pressure, and why should queues always be bounded?** ⭐
An unbounded queue is a memory leak under sustained load: if producers add work faster
than consumers can drain it, the queue simply keeps growing, consuming memory until
OutOfMemoryError — with no warning until it actually crashes. A bounded queue (Java
Phase 5.3's `ArrayBlockingQueue`) applies back-pressure instead: once full, it either
blocks the producer (slowing it down to match what consumers can actually handle) or
rejects new items outright with a clear, immediate signal — either way, the system fails
fast and visibly rather than degrading invisibly until it runs out of memory. The same
principle applies to bounded thread pools and rate limiting (Phase 5.5) — an unbounded
"just accept everything" posture always eventually fails, just less predictably and less
gracefully than a deliberately bounded one.

**Q: What is database bloat, and what does VACUUM actually do?** ⭐
In an MVCC database like Postgres, `UPDATE` and `DELETE` don't immediately remove the
old row version — they leave a "dead tuple" behind that other in-flight transactions
might still need to see for consistent reads. `VACUUM` is the background process
(usually running automatically as `autovacuum`) that reclaims those dead tuples once
nothing needs them anymore. If autovacuum falls behind under heavy write load, or is
blocked — most commonly by a long-running or forgotten open transaction, which forces
Postgres to retain dead tuples for as long as that transaction might still reference them
— tables and their indexes bloat: they grow larger on disk than their logical row count
would suggest, which increases I/O per query and slows everything touching that table,
even though nothing about the application logic changed. The fix is almost always
keeping transactions short, and specifically never holding one open across a slow
external network call.

**Q: How would you go about finding a memory leak in a production Java service?**
Start by watching heap trends over time — a steadily rising old-generation size *after*
full GCs (not just before, which is normal sawtooth behavior) is the signature of a real
leak rather than ordinary garbage churn. Capture a heap dump, and ideally a second one
taken some time later, and use a profiler like Eclipse MAT to diff which object counts
grew and to inspect the dominator tree — which reference chain is keeping the most
memory reachable almost always points straight at the offending static field, uncleared
listener registry, or unbounded cache. Cross-reference against recent code changes to
caches, statics, or ThreadLocal usage. For handle leaks specifically (not heap memory):
`lsof` to check open file-descriptor counts against the process's `ulimit`, and `jstack`
to check for threads stuck blocked on a resource pool or a lock.

**Q: What does "too many open files" mean, and how do you actually fix it — not just
work around it?**
It means the process has hit the OS's file-descriptor limit (`ulimit`) — every open
socket, file, and stream (including database connections and, on some systems, even
certain in-memory constructs) counts against this limit. It's almost always caused by a
leak: something opening streams/sockets/connections and not closing them, the same root
cause as the connection-pool case, just manifesting at the OS level instead of the
application's own pool. Fix the actual leak first (close in a `finally` block or
try-with-resources); raising the `ulimit` should be treated as a stopgap or a
legitimately-higher ceiling for genuinely high, correctly-bounded concurrency — not a
substitute for fixing code that's leaking handles, since a real leak will eventually
exhaust any ceiling you set.
