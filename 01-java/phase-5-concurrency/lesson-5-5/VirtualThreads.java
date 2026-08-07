import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * Lesson 5.5 — Virtual threads (Java 21's headline feature)
 *
 * THE PROBLEM: platform threads wrap OS threads — ~1MB stack each,
 * expensive to create, a few thousand max. But server work is mostly
 * WAITING (DB, HTTP, disk). Blocking a platform thread wastes its
 * whole megabyte doing nothing — hence thread pools, async gymnastics
 * (5.4), reactive frameworks... all workarounds for expensive threads.
 *
 * THE FIX: virtual threads are JVM-managed, ~few-KB stacks, MILLIONS
 * possible. When one BLOCKS, the JVM UNMOUNTS it from its carrier
 * (platform) thread and mounts another. Blocking becomes CHEAP —
 * write simple blocking code, get async-level scalability.
 */
public class VirtualThreads {

    public static void main(String[] args) throws InterruptedException {

        // ============================================================
        // 1. Creating them
        // ============================================================
        System.out.println("=== creating ===");

        Thread vt = Thread.ofVirtual().name("my-virtual").start(() ->
                System.out.println("  " + Thread.currentThread()));
        vt.join();

        Thread pt = Thread.ofPlatform().name("my-platform").start(() ->
                System.out.println("  " + Thread.currentThread()));
        pt.join();
        // Virtual prints VirtualThread[#..]/runnable@ForkJoinPool-1-worker-N
        //                                            ^ its CARRIER — a real
        // OS thread from a small internal pool (~1 per CPU core).

        // ============================================================
        // 2. The party trick: 10,000 concurrent sleepers
        // ============================================================
        System.out.println("\n=== 10,000 concurrent blocking tasks ===");

        // 10k PLATFORM threads would be ~10GB of stacks — likely an
        // OutOfMemoryError. 10k virtual threads: trivial.
        Instant t0 = Instant.now();
        AtomicInteger completed = new AtomicInteger();

        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                exec.submit(() -> {
                    sleep(100);                    // each task BLOCKS 100ms
                    completed.incrementAndGet();
                });
            }
        }   // waits for all

        System.out.println("  " + completed.get() + " tasks, each blocking 100ms, total: "
                + Duration.between(t0, Instant.now()).toMillis() + " ms");
        // ~10,000 x 100ms of blocking finished in a fraction of a second —
        // because blocked virtual threads cost (almost) nothing.

        // ============================================================
        // 3. Where they DON'T help
        // ============================================================
        System.out.println("\n=== limits ===");
        System.out.println("""
                  I/O-bound (waiting)   -> virtual threads SHINE
                  CPU-bound (computing) -> no gain: cores are the limit, use a
                                           fixed pool of platform threads (5.3)
                  PINNING: a virtual thread that blocks INSIDE a synchronized
                  block (or native call) can't unmount — it pins its carrier.
                  Fix: ReentrantLock instead of synchronized on hot blocking
                  paths. (Java 24 largely removed the synchronized limitation,
                  but interviews still ask about pinning.)
                  DON'T POOL virtual threads — they're disposable by design:
                  one task = one fresh virtual thread. Pooling them reintroduces
                  the scarcity they were built to eliminate.""");

        // ============================================================
        // 4. The programming-model payoff
        // ============================================================
        System.out.println("\n=== style comparison ===");

        // 5.4's async pipeline exists because blocking was expensive.
        // With virtual threads, per-request code is just... sequential:
        try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
            var future = exec.submit(() -> {
                String user = fetchSlow("user-service", 100);     // block, who cares
                String orders = fetchSlow("order-service", 100);  // block again
                return user + " + " + orders;
            });
            System.out.println("  handled request: " + future.get());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        // Simple blocking style, thread-per-request, at reactive-framework
        // scale. This is why Spring Boot 3.2+ ships a one-liner
        // (spring.threads.virtual.enabled=true) to run every request on
        // a virtual thread — we WILL flip that switch in track 02.

        // ============================================================
        // 5. Structured concurrency (preview — the future direction)
        // ============================================================
        // StructuredTaskScope (still preview API) treats a group of
        // subtasks as ONE unit: fork children, join all, and if one
        // fails the rest are CANCELLED automatically — no orphan tasks.
        // Worth name-dropping in interviews as "where java.util.concurrent
        // is heading"; we'll adopt it when it goes final.

        System.out.println("\nphase 5 complete.");
    }

    static String fetchSlow(String service, long ms) {
        sleep(ms);
        return service + ":ok";
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
