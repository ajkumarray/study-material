import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * System Design Phase 2 — Caching
 * Run:  java -ea CacheDemo.java
 *
 * Caching = keep a copy of expensive-to-produce data somewhere fast, so most
 * requests avoid the slow source. It's the highest-leverage performance lever
 * (Phase 8), but has two famously hard problems: INVALIDATION (keeping the copy
 * fresh) and STAMPEDE (many misses hitting the source at once).
 */
public class CacheDemo {

    // Stand-in for a slow source (DB / API). Counts how often it's actually hit.
    static class Database {
        final AtomicInteger hits = new AtomicInteger();
        String load(String key) { hits.incrementAndGet(); sleep(20); return "value-of-" + key; }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 1. CACHE-ASIDE (lazy loading) ===");
        cacheAside();

        System.out.println("\n=== 2. CACHE STAMPEDE (and the fix) ===");
        stampedeUnprotected();
        stampedeProtected();
    }

    // ============================================================
    // CACHE-ASIDE: the app checks the cache; on a MISS it loads from the DB and
    // populates the cache; on a HIT it skips the DB. The most common pattern.
    // ============================================================
    static void cacheAside() {
        Map<String, String> cache = new ConcurrentHashMap<>();
        Database db = new Database();

        for (int i = 0; i < 5; i++) get(cache, db, "user:1");   // 1 miss, then 4 hits

        System.out.println("  5 reads of user:1 -> DB hit " + db.hits.get() + " time (cache served the rest)");
        assert db.hits.get() == 1;
    }
    static String get(Map<String, String> cache, Database db, String key) {
        String v = cache.get(key);
        if (v == null) {                       // MISS -> load and populate
            v = db.load(key);
            cache.put(key, v);
        }
        return v;                              // HIT -> served from cache
    }

    // ============================================================
    // STAMPEDE: a popular key expires (or is cold); 50 concurrent requests all
    // MISS at once and all hammer the DB simultaneously — a thundering herd that
    // can overload the source right when it's most loaded.
    // ============================================================
    static void stampedeUnprotected() throws InterruptedException {
        Map<String, String> cache = new ConcurrentHashMap<>();
        Database db = new Database();
        runConcurrently(50, () -> get(cache, db, "hot:key"));   // all miss together
        System.out.println("  unprotected: 50 concurrent misses -> DB hit " + db.hits.get()
                + " times  <-- STAMPEDE");
    }

    static void stampedeProtected() throws InterruptedException {
        Map<String, String> cache = new ConcurrentHashMap<>();
        Database db = new Database();
        // Fix: single-flight — only ONE thread computes a missing key; the rest
        // wait for and reuse its result. computeIfAbsent locks per key.
        runConcurrently(50, () ->
                cache.computeIfAbsent("hot:key", db::load));    // atomic per-key load
        System.out.println("  protected  : 50 concurrent misses -> DB hit " + db.hits.get()
                + " time  (single-flight: one loads, 49 wait & reuse)");
        assert db.hits.get() == 1;
    }

    static void runConcurrently(int n, Runnable task) throws InterruptedException {
        Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) { ts[i] = new Thread(task); ts[i].start(); }
        for (Thread t : ts) t.join();
    }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
