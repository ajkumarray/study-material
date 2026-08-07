import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * System Design Phase 8 — Performance optimization
 * Run:  java PerformanceDemo.java
 *
 * The golden rule: MEASURE FIRST. Optimize the actual bottleneck, not a guess.
 * This program measures three of the highest-leverage backend wins:
 *   1. The N+1 query problem (1 + N round trips  ->  1 batched query)
 *   2. Caching a hot, expensive computation
 *   3. Pagination vs fetching everything
 * The numbers are simulated (a per-"query" delay stands in for network + DB),
 * but the SHAPE of the win is exactly what you see in production.
 */
public class PerformanceDemo {

    static final int QUERY_MS = 5;      // pretend each DB round trip costs ~5ms

    public static void main(String[] args) {
        System.out.println("=== 1. N+1 QUERY PROBLEM ===");
        nPlusOne();

        System.out.println("\n=== 2. CACHING a hot computation ===");
        caching();

        System.out.println("\n=== 3. PAGINATION vs fetch-everything ===");
        pagination();

        System.out.println("\n=== the optimization checklist (see NOTES) ===");
        System.out.println("  measure -> DB (indexes, N+1, pooling, replicas, pagination)");
        System.out.println("  -> app (cache, async, batch, compress) -> web (CDN, bundle, lazy).");
    }

    // A fake data layer: each call sleeps QUERY_MS to model a real round trip.
    static class Db {
        List<Integer> findOrderIds() { query(); return List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10); }
        String findCustomerForOrder(int orderId) { query(); return "cust-" + (orderId % 3); }
        Map<Integer, String> findCustomersForOrders(List<Integer> ids) {   // ONE batched query
            query();
            Map<Integer, String> m = new HashMap<>();
            for (int id : ids) m.put(id, "cust-" + (id % 3));
            return m;
        }
        void query() { sleep(QUERY_MS); }
    }

    // ============================================================
    // 1. N+1 — the #1 hidden performance killer (Java Phase 7.3, Spring 4).
    // Fetch a list (1 query), then fetch a related entity PER ROW (N queries).
    // Fix: fetch the related data in ONE query (a JOIN / IN-clause / batch).
    // ============================================================
    static void nPlusOne() {
        Db db = new Db();

        long t0 = System.nanoTime();
        List<Integer> orders = db.findOrderIds();                 // 1 query
        for (int id : orders) db.findCustomerForOrder(id);        // + N queries  <-- N+1
        long naive = ms(t0);

        t0 = System.nanoTime();
        List<Integer> orders2 = db.findOrderIds();                // 1 query
        db.findCustomersForOrders(orders2);                       // + 1 batched query
        long batched = ms(t0);

        System.out.printf("  N+1 (1+%d queries) : %d ms%n", orders.size(), naive);
        System.out.printf("  batched (2 queries): %d ms   (~%dx faster)%n",
                batched, Math.max(1, naive / Math.max(1, batched)));
    }

    // ============================================================
    // 2. CACHING — skip repeated expensive work. The demo computes an
    // "expensive" value once and serves later hits from memory.
    // ============================================================
    static void caching() {
        Map<Integer, Long> cache = new ConcurrentHashMap<>();

        long t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) expensive(7);                // recompute every time
        long uncached = ms(t0);

        t0 = System.nanoTime();
        for (int i = 0; i < 20; i++) cache.computeIfAbsent(7, PerformanceDemo::expensive);  // compute once
        long cached = ms(t0);

        System.out.printf("  uncached (20 calls): %d ms%n", uncached);
        System.out.printf("  cached   (20 calls): %d ms   (compute once, serve the rest)%n", cached);
    }
    static long expensive(int n) { sleep(10); return (long) n * n; }   // simulate heavy work

    // ============================================================
    // 3. PAGINATION — never SELECT * a huge table into memory. Fetch a page.
    // Cuts latency, memory, and payload size; the client asks for more as needed.
    // ============================================================
    static void pagination() {
        int total = 1_000_000, pageSize = 50;

        long t0 = System.nanoTime();
        int[] all = new int[total];                               // load EVERYTHING (memory + time)
        for (int i = 0; i < total; i++) all[i] = i;
        long fetchAll = ms(t0);

        t0 = System.nanoTime();
        int[] page = new int[pageSize];                           // load ONE page
        for (int i = 0; i < pageSize; i++) page[i] = i;
        long onePage = ms(t0);

        System.out.printf("  fetch all %d rows : %d ms, %d KB in memory%n",
                total, fetchAll, total * 4 / 1024);
        System.out.printf("  fetch 1 page (%d) : %d ms, %d KB   (LIMIT/OFFSET or keyset)%n",
                pageSize, onePage, pageSize * 4 / 1024);
    }

    static long ms(long startNanos) { return (System.nanoTime() - startNanos) / 1_000_000; }
    static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }
}
