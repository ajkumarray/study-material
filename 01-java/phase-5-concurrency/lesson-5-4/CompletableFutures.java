import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/*
 * Lesson 5.4 — CompletableFuture
 *
 * Future (5.3) has one move: block on get(). CompletableFuture adds
 * PIPELINES: "when this finishes, then do that, and combine it with
 * this other one, and here's the error plan" — async workflows
 * composed declaratively, no manual blocking between stages.
 *
 * It's the streams idea (4.2) applied to time instead of data —
 * and the map/flatMap concepts transfer EXACTLY (thenApply/thenCompose).
 */
public class CompletableFutures {

    record Profile(String user) { }
    record Orders(int count) { }

    public static void main(String[] args) {

        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {

            // ========================================================
            // 1. supplyAsync + thenApply — the async map
            // ========================================================
            System.out.println("=== supplyAsync / thenApply ===");

            CompletableFuture<String> greeting = CompletableFuture
                    .supplyAsync(() -> {                       // runs on the pool
                        log("fetching name...");
                        sleep(150);
                        return "ajay";
                    }, pool)
                    .thenApply(String::toUpperCase)            // transform WHEN ready
                    .thenApply(name -> "Hello, " + name + "!");

            log("pipeline built, main thread is FREE (not blocked)");
            System.out.println("result: " + greeting.join()); // join only at the edge

            // ========================================================
            // 2. thenCompose — the async flatMap
            // ========================================================
            System.out.println("\n=== thenCompose ===");

            // fetchProfile returns CompletableFuture<Profile>;
            // thenApply would nest: CF<CF<Orders>>. thenCompose flattens —
            // EXACTLY Optional.flatMap / Stream.flatMap, but for async.
            Orders orders = fetchProfile("u1", pool)
                    .thenCompose(p -> fetchOrders(p, pool))
                    .join();
            System.out.println("orders: " + orders.count());

            // ========================================================
            // 3. thenCombine — join two INDEPENDENT futures
            // ========================================================
            System.out.println("\n=== thenCombine (parallel fan-out) ===");

            long t0 = System.currentTimeMillis();

            CompletableFuture<Double> price = CompletableFuture.supplyAsync(() -> {
                sleep(200);                                    // price service: 200ms
                return 4999.0;
            }, pool);
            CompletableFuture<Double> tax = CompletableFuture.supplyAsync(() -> {
                sleep(200);                                    // tax service: 200ms
                return 0.18;
            }, pool);

            Double total = price.thenCombine(tax, (p, t) -> p * (1 + t)).join();
            long elapsed = System.currentTimeMillis() - t0;

            System.out.println("total: " + total + " in " + elapsed
                    + " ms  (two 200ms calls -> ~200, not 400: they ran in PARALLEL)");

            // ========================================================
            // 4. allOf / anyOf — fan-out to many
            // ========================================================
            System.out.println("\n=== allOf ===");

            List<CompletableFuture<String>> services = List.of(
                    healthCheck("db", 120, pool),
                    healthCheck("cache", 80, pool),
                    healthCheck("queue", 100, pool));

            // allOf returns CF<Void> — collect results via the originals:
            CompletableFuture.allOf(services.toArray(CompletableFuture[]::new)).join();
            services.forEach(f -> System.out.println("  " + f.join()));

            // anyOf: first to complete wins (racing replicas, timeouts).

            // ========================================================
            // 5. ERRORS — exceptionally / handle
            // ========================================================
            System.out.println("\n=== error handling ===");

            String risky = CompletableFuture
                    .supplyAsync(() -> {
                        if (true) throw new IllegalStateException("payment gateway down");
                        return "paid";
                    }, pool)
                    .exceptionally(ex -> {                     // async catch block
                        log("recovering: " + ex.getCause().getMessage());
                        return "queued-for-retry";
                    })
                    .join();
            System.out.println("outcome: " + risky);
            // An exception SKIPS all downstream thenApply stages until a
            // handler (exceptionally / handle / whenComplete) — exactly
            // like a throw skipping to catch. handle((val, ex) -> ...)
            // sees BOTH cases; whenComplete peeks without changing them.

            // orTimeout: the guard rail real services always want:
            try {
                CompletableFuture.supplyAsync(() -> { sleep(5_000); return "slow"; }, pool)
                        .orTimeout(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .join();
            } catch (Exception e) {
                System.out.println("orTimeout fired: " + e.getCause().getClass().getSimpleName());
            }
        }

        // Threading fine print: supplyAsync without an executor uses
        // ForkJoinPool.commonPool() — shared JVM-wide; fine for CPU work,
        // WRONG for blocking I/O (starves it). Pass your own pool (as we
        // did), or use virtual threads (next lesson) and stop caring.
    }

    static CompletableFuture<Profile> fetchProfile(String id, ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(100);
            log("profile loaded");
            return new Profile(id);
        }, pool);
    }

    static CompletableFuture<Orders> fetchOrders(Profile p, ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(100);
            log("orders loaded for " + p.user());
            return new Orders(7);
        }, pool);
    }

    static CompletableFuture<String> healthCheck(String name, long ms, ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> {
            sleep(ms);
            return name + ": ok (" + ms + "ms)";
        }, pool);
    }

    static void log(String msg) {
        System.out.println("  [" + Thread.currentThread().getName() + "] " + msg);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
