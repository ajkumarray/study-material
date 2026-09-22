<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · reliability](../phase-6-reliability/NOTES.md) | [Phase 8 · performance ➡](../phase-8-performance/NOTES.md)
<!-- /nav -->

# Phase 7 — Resource Management & "Leaks": Notes

The unifying cause behind almost every "leak" bug: **acquire a bounded resource, forget
to release it.** The unifying fixes: **guarantee release** (try-with-resources /
try-finally, so `close()` always runs no matter what) and **bound everything** (pools,
queues, caches all need an explicit limit). `ResourceLeaksDemo.java` reproduces the two
most common production leaks — connection-pool exhaustion and an unbounded cache — with
real concurrent code, then fixes both.

## 7.1 — Connection-pool exhaustion ("the DB memory leak" that isn't)

### Key Concepts

- **The real cause of "the database is leaking memory"** — when someone reports this,
  it's almost always the *application* leaking **connections**, not the database leaking
  RAM. A connection pool (HikariCP — Java Phase 7.2) is small and bounded by design,
  commonly around 10 connections by default.
- **How a connection gets leaked** — code that borrows a connection from the pool and
  never returns it: a missing `close()` call, an exception thrown *before* `close()` runs
  (skipping it entirely without try-with-resources), or a connection deliberately held
  open across a slow, unrelated network call to another service instead of being
  released first.
- **The failure mode** — each leaked connection permanently shrinks the pool's available
  count. Once every connection has been leaked, **every subsequent request blocks
  waiting for a connection that will never be returned**. The application appears to
  "hang," throughput drops to zero, and — because the symptom is "requests are stuck
  waiting on the database" — it's easy to mistakenly suspect the database itself has
  died or is out of memory, when the database is actually fine and idle; the application
  simply can't reach it anymore.
- **Fixes**: **try-with-resources / try-finally** so `close()` runs unconditionally,
  even on an exception (Java Phase 3.1) — every `Connection`, `Statement`, `ResultSet`,
  and I/O stream is `AutoCloseable` for exactly this reason. **Leak detection** —
  HikariCP's `leakDetectionThreshold` logs a stack trace for any connection held longer
  than the configured threshold, which is by far the fastest way to find the offending
  code in production. **Timeouts** — connection acquisition timeouts and query timeouts
  so a slow query or a stuck borrow can't hold (or wait for) a connection indefinitely.
  **Never hold a connection across a network call to another service** — release it
  first, make the call, borrow again if needed.

### Worked example — `ResourceLeaksDemo`'s `Pool`/`Connection` and both variants

```java
// A "pooled connection": borrowing takes a permit; close() returns it.
// This models exactly what HikariCP does.
static class Pool {
    private final Semaphore permits = new Semaphore(POOL_SIZE);   // POOL_SIZE = 3
    Connection borrow(long timeoutMs) throws InterruptedException {
        if (!permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) return null;
        return new Connection(permits);
    }
}
static class Connection implements AutoCloseable {
    private final Semaphore permits;
    private boolean closed = false;
    void query() { sleep(50); }
    public void close() { if (!closed) { closed = true; permits.release(); } }
}
```

```java
// 1a. LEAKY: borrow, use, and NEVER close.
static void leakyPool() throws InterruptedException {
    Pool pool = new Pool();
    runConcurrently(TASKS, () -> {     // TASKS = 10, POOL_SIZE = 3
        Connection c = pool.borrow(200);
        if (c == null) { exhausted.incrementAndGet(); return; }
        c.query();
        // BUG: no c.close() -> the connection is LEAKED forever.
        ok.incrementAndGet();
    });
    // leaky : 3 succeeded, 7 TIMED OUT waiting for a connection
    //         pool now has 0/3 free  <-- EXHAUSTED (all leaked)
}

// 1b. FIXED: try-with-resources GUARANTEES close(), even on exception.
static void fixedPool() throws InterruptedException {
    Pool pool = new Pool();
    runConcurrently(TASKS, () -> {
        try (Connection c = pool.borrow(2000)) {    // <- released automatically
            if (c == null) { exhausted.incrementAndGet(); return; }
            c.query();
            ok.incrementAndGet();
        }
    });
    // fixed : 10 succeeded, 0 timed out
    //         pool back to 3/3 free  (try-with-resources returns them)
}
```

With a pool of 3 and 10 concurrent tasks, the leaky version lets exactly 3 tasks succeed
(one per available permit) before the pool is fully drained — every one of the other 7
tasks blocks on `tryAcquire` for its full 200ms timeout and then gives up, reported as
"timed out." The pool's `available()` stays at 0 forever afterward, because nothing
ever calls `close()`/`permits.release()` — this is a **permanent** exhaustion, not a
transient slowdown; the pool doesn't recover on its own. The fixed version wraps the
borrow in a try-with-resources block: even though each task still only holds the
connection for the duration of `c.query()`, the moment that block exits (normally, or
via an exception), the JVM calls `c.close()` automatically, returning the permit to the
pool immediately. All 10 tasks succeed — the same 3-connection pool simply serves them
in waves of up to 3 at a time instead of running out after the first 3.

### Why it's useful

This is one of the highest-value bugs to be able to recognize by symptom alone: "the
app is hanging, and it looks like it's stuck talking to the database, but the database's
own metrics show it's healthy and mostly idle" is close to a textbook description of
connection-pool exhaustion, and knowing to immediately check the pool's active/idle/
waiting counts (rather than the database server itself) can turn a multi-hour incident
into a five-minute diagnosis.

## 7.2 — Memory leaks in a garbage-collected language

### Key Concepts

- **What "leak" means when there's a garbage collector** — in Java (or any GC'd
  language), a memory leak isn't literally forgotten, unreachable memory (the GC would
  reclaim that). It's **unintentionally *reachable*** objects — something still holds a
  reference to them, so the GC correctly considers them "still in use" and never
  collects them — that grow without bound over time.
- **Unbounded caches/collections** — the single most common source: a `Map` (or any
  collection) that the code only ever `put`s into, with nothing ever removing entries.
  "A cache without an eviction policy is a memory leak with better branding" (Phase
  2.6). Fix: a **bounded cache** with an explicit eviction policy — an LRU
  `LinkedHashMap` with `removeEldestEntry` overridden, a library like Caffeine, or an
  external cache like Redis configured with `maxmemory` and an eviction policy.
- **Static collections / registries** — a `static` field holding a collection that
  accumulates entries over the application's lifetime (e.g., a registry of "active
  requests" that adds on start but never removes on completion) — because it's static,
  it's reachable for the entire life of the JVM, so anything added to it is never
  collected.
- **Unremoved listeners/callbacks** — registering an object as an observer/listener on
  some long-lived subject, and never unregistering it, keeps that object (and everything
  it references) reachable through the subject's listener list for as long as the
  subject lives — even if nothing else in the application still cares about the
  listener.
- **`ThreadLocal` on pooled threads** — a `ThreadLocal` value set during one request
  stays attached to the *thread*, not the request. In a web application, request-
  handling threads come from a bounded, reused pool (Java Phase 5.3) — the thread lives
  far longer than any single request, so a value set via `ThreadLocal.set()` and never
  explicitly removed with `ThreadLocal.remove()` stays reachable (attached to that pool
  thread) indefinitely, both leaking memory slowly and, more subtly, potentially leaking
  *data* from one request into the next request that happens to reuse the same thread.
  Always `.remove()` in a `finally` block.
- **ClassLoader leaks on redeploy** — an advanced case specific to application servers
  that support hot redeployment: if any object created by the old deployment's
  classloader is still referenced after redeploy (a lingering thread, a static reference
  held by a shared library), the entire old classloader — and every class and static
  field it loaded — can't be garbage collected, effectively leaking the whole previous
  deployment's memory footprint.

### Worked example — `ResourceLeaksDemo`'s unbounded vs. bounded cache

```java
// 2a. MEMORY LEAK: a collection that only ever GROWS.
static void unboundedCacheLeak() {
    Map<Integer, byte[]> cache = new java.util.HashMap<>();
    for (int i = 0; i < 100_000; i++) cache.put(i, new byte[128]);   // never evicts
    // unbounded cache -> 100000 entries and still growing  <-- LEAK
}

// 2b. FIXED: a BOUNDED cache (LRU) evicts when full.
static void boundedCache() {
    int capacity = 1000;
    Map<Integer, byte[]> lru = new LinkedHashMap<>(16, 0.75f, true) {   // accessOrder=true
        protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> e) {
            return size() > capacity;                 // evict oldest past capacity
        }
    };
    for (int i = 0; i < 100_000; i++) lru.put(i, new byte[128]);
    // bounded LRU cache -> capped at 1000 entries after 100k puts  (memory safe)
    assert lru.size() == capacity;
}
```

`unboundedCacheLeak` puts 100,000 entries (12.8MB of raw byte arrays, plus per-entry
`HashMap` overhead) into a plain `HashMap` with no bound at all — the map's size ends
at exactly 100,000, every single entry reachable and un-collectible, growing linearly
with however many puts happen. In a real long-running service, this would just keep
growing until `OutOfMemoryError`. `boundedCache` wraps the same 100,000 puts in a
`LinkedHashMap` constructed with `accessOrder=true` and an overridden
`removeEldestEntry`, which Java calls after every `put` — returning `true` here tells
the map to evict its least-recently-used entry whenever `size() > capacity`. The result
after the same 100,000 puts: the map's size is capped at exactly 1,000, no matter how
many more puts occur — this is the exact mechanism behind an LRU cache, implemented with
nothing beyond the standard library.

### Why it's useful

The distinction "a leak is unintended *reachability*, not literally lost memory" is what
makes Java memory leaks debuggable at all: because leaked objects are still reachable,
a heap dump can show you *the reference chain* keeping them alive (Eclipse MAT's
"dominator tree" view), which almost always points straight at the offending static
field, uncleared listener list, or unbounded cache — you're never debugging truly
invisible, unreferenced memory.

## 7.3 — Database-side "memory" that looks like a leak but usually isn't

### Key Concepts

- **The database legitimately uses a lot of RAM on purpose** — buffer/page cache (hot
  data pages kept in memory to avoid disk reads), per-query sort/work memory, and a
  prepared-statement/query-plan cache are all normal, intentional memory usage, not a
  leak.
- **What actually looks like a leak but is misconfiguration** — `work_mem` (Postgres)
  set too high multiplied across many concurrent connections can genuinely exhaust a
  database server's RAM even though no single query is "leaking" anything; an
  ever-growing prepared-statement cache (rare, but possible with certain drivers/
  configurations generating unique statement text per call) can also look like unbounded
  growth.
- **Table/index bloat** — genuinely a database-side accumulation problem, but distinct
  from an application memory leak: in an MVCC database like Postgres, `UPDATE`/`DELETE`
  don't immediately remove the old row version — they leave a "dead tuple" that a
  background process (`VACUUM`) must later reclaim. If `VACUUM` falls behind (or is
  blocked, 7.4), dead tuples accumulate, inflating table and index size on disk, which
  increases I/O and slows every query touching that table even though the *logical* row
  count hasn't grown.

### Why it's useful

Distinguishing "the database is using a lot of memory because that's its job" from "the
database has a genuine leak-shaped problem (bloat from stalled vacuum)" prevents wasted
effort chasing a nonexistent leak when the real fix is tuning `work_mem` per connection,
sizing the connection pool sensibly (fewer connections × reasonable `work_mem` each,
rather than many connections × generous `work_mem` each), or addressing whatever is
blocking autovacuum (7.4).

## 7.4 — Other handle leaks and back-pressure

### Key Concepts

- **Thread leaks** — creating threads or executors without ever shutting them down, or
  creating unbounded numbers of threads on demand instead of drawing from a bounded pool
  (Java Phase 5.3). Each thread costs real OS resources (a stack allocation, kernel
  scheduling overhead); enough leaked threads degrades performance well before it causes
  an outright crash.
- **File/socket handle leaks** — any unclosed `InputStream`/`OutputStream`/socket holds
  an OS-level file descriptor. Every process has a finite file-descriptor limit
  (`ulimit`); enough leaked handles eventually produces the classic "too many open
  files" error, at which point *nothing* — not new files, not new sockets, not new
  database connections — can be opened by that process until some are freed or the
  process restarts.
- **Unbounded queues are a memory leak under load** — if producers add to a queue faster
  than consumers can drain it, and the queue has no capacity limit, it simply grows
  without bound, consuming memory until `OutOfMemoryError`. A **bounded queue** (Java
  Phase 5.3's `ArrayBlockingQueue`) instead applies **back-pressure**: once full, it
  either blocks the producer (slowing it down to match the consumer's real pace) or
  rejects new items outright — both preferable to silently consuming unbounded memory.
  "Fail fast and visibly" beats "run out of memory eventually and invisibly."

### Why it's useful

Every one of these is the same underlying lesson as the connection pool (7.1) applied to
a different resource type: bound it, and guarantee release. A system with no bounded
queues, no bounded thread pools, and no bounded caches doesn't actually have "no
limits" — it has one enormous, uncontrolled limit called "however much RAM the machine
has," discovered the hard way in production via an OOM crash instead of chosen
deliberately in advance.

## 7.5 — Database maintenance: VACUUM, bloat, and long transactions

### Key Concepts

- **VACUUM (Postgres)** — the background process that reclaims dead tuples left behind
  by `UPDATE`/`DELETE` under MVCC. `autovacuum` runs this automatically based on
  configurable thresholds, but it can fall behind under heavy write load or be
  explicitly disabled/misconfigured.
- **Bloat** — the accumulated result of dead tuples not being reclaimed: tables and
  their indexes grow larger on disk than their logical (live-row) size would suggest,
  which means more pages to read per query (more I/O) and slower performance overall,
  even though nothing about the *application* changed. Monitor via the dead-tuple ratio
  in Postgres's statistics views.
- **Long-running transactions block vacuum** — MVCC requires keeping old row versions
  around as long as any open transaction might still need to see them (for
  repeatable-read consistency within that transaction). A single forgotten, long-open
  transaction — even one that isn't actively doing anything — can prevent `VACUUM` from
  reclaiming *any* dead tuples across the whole database for as long as it stays open,
  silently bloating everything in the meantime. Keep transactions as short as possible,
  and specifically avoid holding one open across a slow external call.
- **Lock contention and deadlocks** (Databases Phase 5) — long-held locks from long
  transactions also serialize other writers and can cause query timeouts; two
  transactions acquiring the same locks in a different order can deadlock, at which
  point the database detects it and kills one of them. Diagnose via the database's lock/
  blocking-query views; the fix is almost always "make the transaction shorter and more
  predictable," not a locking-strategy change.

### Why it's useful

"Keep transactions short" is one of the highest-leverage, simplest rules in this whole
phase — a single accidentally-long transaction (commonly: opened, then the code makes a
slow HTTP call to another service *while the transaction is still open*, Phase 6.1's
"don't hold a connection across a network call" applied here too) can simultaneously
cause connection-pool pressure (7.1), block vacuum (bloating the whole database), and
hold locks that stall unrelated writers — one bad pattern, three different symptoms.

## 7.6 — Diagnosing resource issues in production

### Key Concepts

- **Pool metrics** — active/idle/waiting connection counts, exposed via Spring Boot
  Actuator (Java Phase 6, Spring Boot Phase 8). A pool sitting at `active == max` with
  requests queued waiting is the direct symptom of exhaustion (7.1).
- **Heap dumps + a profiler** — for a suspected memory leak, capture a heap dump and use
  a tool like Eclipse MAT to find the "dominator tree" — which object or reference chain
  is keeping the most memory reachable. Comparing two heap dumps taken minutes apart and
  diffing which object counts grew is often the fastest way to identify the leaking
  collection.
- **`jstack`** — a thread dump showing every thread's current stack trace; threads stuck
  waiting on a connection pool, a lock, or a slow I/O call are immediately visible as a
  large cluster of threads blocked at the same line of code.
- **`lsof`** — lists open file descriptors for a process; a rapidly growing count (or one
  approaching the `ulimit`) points directly at a file/socket handle leak.
- **Database dashboards** — connection counts, dead-tuple ratio/bloat, and long-running/
  idle-in-transaction query lists are the database-side equivalents of the above.

### Why it's useful

Knowing which specific tool answers which specific question — "is it the pool?" →
Actuator metrics; "is it a growing Java collection?" → heap dump + MAT; "are threads
stuck?" → `jstack`; "is it OS-level file handles?" → `lsof`; "is it the database?" →
its own connection/bloat dashboards — turns "the app is slow/hanging, go investigate"
from an open-ended search into a fast, targeted process of elimination.

## Summary / Key Takeaways

- The unifying cause of resource "leaks" is always **acquire, forget to release**; the
  unifying fix is **guarantee release** (try-with-resources) and **bound every pool,
  queue, and cache**.
- **Connection-pool exhaustion** is the real story behind "the database is leaking
  memory" — leaked (never-`close()`d) connections shrink a small, bounded pool until
  every request blocks waiting for one that never comes (demo: a pool of 3, leaked → 7
  of 10 requests time out and the pool stays at 0/3 forever; fixed with
  try-with-resources → all 10 succeed, pool returns to 3/3).
- A **memory leak in a GC'd language** means unintentionally *reachable*, not literally
  lost, objects — unbounded caches/collections (demo: unbounded → 100,000 entries and
  growing; LRU-bounded → capped at 1,000), static registries, unremoved listeners, and
  un-`remove()`d **`ThreadLocal`**s on pooled threads are the classic sources.
- The database itself legitimately uses significant RAM (buffer cache, per-query work
  memory) — that's usually not a leak; **table/index bloat** from a lagging `VACUUM`,
  often caused by a **long-running transaction**, is the genuine database-side
  accumulation problem to watch for.
- **Unbounded queues, thread pools, and file/socket handles** all fail the same way
  under sustained load — bound them so the system applies **back-pressure** (fails fast
  and visibly) instead of degrading invisibly until it runs out of memory or file
  descriptors.
- Diagnose with the right tool per symptom: **pool metrics** (Actuator) for exhaustion,
  **heap dumps + a profiler** for growing collections, **`jstack`** for stuck threads,
  **`lsof`** for handle leaks, and **database dashboards** for bloat/long transactions.
