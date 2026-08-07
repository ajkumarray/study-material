import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * System Design Phase 7 — Resource management & "leaks"
 * Run:  java -ea ResourceLeaksDemo.java
 *
 * When people say "the database has a memory leak," it's almost never the DB
 * leaking RAM — it's the APP leaking a scarce resource: CONNECTIONS. Every
 * borrowed-and-never-returned connection shrinks the pool until requests hang
 * waiting for one that never comes. This program REPRODUCES pool exhaustion and
 * an unbounded-cache memory leak, then fixes both.
 *
 * The root cause is always the same: acquire a bounded resource, forget to
 * release it. The fix is always the same: guarantee release (try-with-resources
 * / try-finally) and BOUND every pool, queue, and cache.
 */
public class ResourceLeaksDemo {

    static final int POOL_SIZE = 3;      // a tiny DB connection pool
    static final int TASKS = 10;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 1. CONNECTION-POOL EXHAUSTION (\"the DB memory leak\") ===");
        leakyPool();
        fixedPool();

        System.out.println("\n=== 2. UNBOUNDED-CACHE MEMORY LEAK ===");
        unboundedCacheLeak();
        boundedCache();

        System.out.println("\n=== 3. the other usual suspects (see NOTES) ===");
        System.out.println("  ThreadLocals on pooled threads, unremoved listeners, growing");
        System.out.println("  collections, unclosed streams/files, unbounded queues.");
    }

    // A "pooled connection": borrowing takes a permit; close() returns it.
    // This models exactly what HikariCP does (Java Phase 7.2).
    static class Pool {
        private final Semaphore permits = new Semaphore(POOL_SIZE);
        int available() { return permits.availablePermits(); }

        // Returns a connection, or null if none free within the timeout (exhaustion).
        Connection borrow(long timeoutMs) throws InterruptedException {
            if (!permits.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) return null;
            return new Connection(permits);
        }
    }
    // AutoCloseable -> works in try-with-resources; close() releases the permit.
    static class Connection implements AutoCloseable {
        private final Semaphore permits;
        private boolean closed = false;
        Connection(Semaphore permits) { this.permits = permits; }
        void query() { sleep(50); }              // simulate DB work
        public void close() { if (!closed) { closed = true; permits.release(); } }
    }

    // ============================================================
    // 1a. LEAKY: borrow a connection, use it, and NEVER close it. After
    // POOL_SIZE borrows the pool is empty and every further request times out.
    // ============================================================
    static void leakyPool() throws InterruptedException {
        Pool pool = new Pool();
        AtomicInteger ok = new AtomicInteger(), exhausted = new AtomicInteger();
        runConcurrently(TASKS, () -> {
            try {
                Connection c = pool.borrow(200);     // borrow...
                if (c == null) { exhausted.incrementAndGet(); return; }
                c.query();
                // BUG: no c.close() -> the connection is LEAKED forever.
                ok.incrementAndGet();
            } catch (InterruptedException ignored) { }
        });
        System.out.printf("  leaky : %d succeeded, %d TIMED OUT waiting for a connection%n",
                ok.get(), exhausted.get());
        System.out.printf("          pool now has %d/%d free  <-- EXHAUSTED (all leaked)%n",
                pool.available(), POOL_SIZE);
    }

    // ============================================================
    // 1b. FIXED: try-with-resources GUARANTEES close() (release) even on
    // exception. A pool of 3 now serves all 10 tasks by reuse.
    // ============================================================
    static void fixedPool() throws InterruptedException {
        Pool pool = new Pool();
        AtomicInteger ok = new AtomicInteger(), exhausted = new AtomicInteger();
        runConcurrently(TASKS, () -> {
            try (Connection c = pool.borrow(2000)) {    // <- released automatically
                if (c == null) { exhausted.incrementAndGet(); return; }
                c.query();
                ok.incrementAndGet();
            } catch (InterruptedException ignored) { }
        });
        System.out.printf("  fixed : %d succeeded, %d timed out%n", ok.get(), exhausted.get());
        System.out.printf("          pool back to %d/%d free  (try-with-resources returns them)%n",
                pool.available(), POOL_SIZE);
        assert ok.get() == TASKS && pool.available() == POOL_SIZE;
    }

    // ============================================================
    // 2a. MEMORY LEAK: a cache (or any collection) that only ever GROWS. Under
    // load it consumes memory until OutOfMemoryError. "Cache" without a bound
    // is just a memory leak with good PR.
    // ============================================================
    static void unboundedCacheLeak() {
        Map<Integer, byte[]> cache = new java.util.HashMap<>();
        for (int i = 0; i < 100_000; i++) cache.put(i, new byte[128]);   // never evicts
        System.out.printf("  unbounded cache -> %d entries and still growing  <-- LEAK%n",
                cache.size());
    }

    // ============================================================
    // 2b. FIXED: a BOUNDED cache (LRU) evicts the least-recently-used entry
    // when full, so memory is capped no matter how much you put.
    // ============================================================
    static void boundedCache() {
        int capacity = 1000;
        Map<Integer, byte[]> lru = new LinkedHashMap<>(16, 0.75f, true) {   // accessOrder=true
            protected boolean removeEldestEntry(Map.Entry<Integer, byte[]> e) {
                return size() > capacity;                 // evict oldest past capacity
            }
        };
        for (int i = 0; i < 100_000; i++) lru.put(i, new byte[128]);
        System.out.printf("  bounded LRU cache -> capped at %d entries after 100k puts  (memory safe)%n",
                lru.size());
        assert lru.size() == capacity;
    }

    // ---- harness ----
    static void runConcurrently(int n, Runnable task) throws InterruptedException {
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) { threads[i] = new Thread(task); threads[i].start(); }
        for (Thread t : threads) t.join();
    }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
