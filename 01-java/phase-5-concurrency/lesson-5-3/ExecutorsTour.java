import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/*
 * Lesson 5.3 — Executors & thread pools
 *
 * Raw `new Thread()` per task doesn't scale: thread creation is
 * expensive (~1MB stack each, OS syscalls), and unbounded threads can
 * take down the process. THREAD POOLS fix this: N long-lived worker
 * threads pulling tasks from a queue.
 *
 *   ExecutorService pool = Executors.newFixedThreadPool(4);
 *   pool.submit(task);       // task queued -> a worker picks it up
 *
 * You stop managing THREADS and start submitting TASKS.
 */
public class ExecutorsTour {

    public static void main(String[] args) throws InterruptedException, ExecutionException {

        // ============================================================
        // 1. Fixed pool + Runnable
        // ============================================================
        System.out.println("=== fixed pool ===");

        // try-with-resources: ExecutorService is AutoCloseable since 19
        // (close() = shutdown + wait). 3.1 pays off yet again.
        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            for (int i = 1; i <= 6; i++) {
                int jobId = i;
                pool.submit(() -> {
                    System.out.println("  job-" + jobId + " on " + Thread.currentThread().getName());
                    sleep(50);
                });
            }
        }   // close(): no new tasks; waits for the 6 to finish
        System.out.println("all jobs done (6 jobs, only 3 pool threads — queued + reused)");

        // ============================================================
        // 2. Callable + Future — tasks that RETURN
        // ============================================================
        System.out.println("\n=== Callable / Future ===");

        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            // Runnable: () -> void, can't throw checked.
            // Callable<T>: () -> T, CAN throw checked. For results: Callable.
            Callable<Integer> slowSquare = () -> {
                sleep(200);
                return 21 * 2;
            };

            Future<Integer> future = pool.submit(slowSquare);
            System.out.println("submitted; done yet? " + future.isDone());
            Integer answer = future.get();          // BLOCKS until the result exists
            System.out.println("future.get() -> " + answer);
            // get(timeout) throws TimeoutException — always prefer it in
            // real services; get() forever is how threads leak.
            // If the task threw, get() throws ExecutionException wrapping
            // the real cause (3.1's chaining, again).

            // invokeAll: submit a batch, wait for ALL:
            List<Callable<String>> checks = List.of(
                    () -> { sleep(150); return "db: ok"; },
                    () -> { sleep(100); return "cache: ok"; },
                    () -> { sleep(120); return "queue: ok"; });
            long t0 = System.currentTimeMillis();
            List<Future<String>> results = pool.invokeAll(checks);
            for (Future<String> f : results) {
                System.out.println("  " + f.get());
            }
            System.out.println("3 checks on 2 threads: " + (System.currentTimeMillis() - t0)
                    + " ms (not 370 — they overlapped)");
        }

        // ============================================================
        // 3. The pool zoo — and what to actually use
        // ============================================================
        System.out.println("\n=== choosing a pool ===");
        System.out.println("""
                newFixedThreadPool(n)   -> N workers, unbounded queue. CPU-bound: n = cores.
                newCachedThreadPool()   -> grows on demand, reuses idle. Bursty small tasks;
                                           DANGER: unbounded threads under sustained load.
                newSingleThreadExecutor -> serial execution, task order guaranteed.
                newScheduledThreadPool  -> delayed / periodic tasks (cron-lite).
                newVirtualThreadPerTaskExecutor -> lesson 5.5: I/O-bound at scale.
                Production: prefer new ThreadPoolExecutor(...) directly — explicit
                bounded queue + rejection policy, so overload FAILS FAST instead of OOM.
                """);

        // ============================================================
        // 4. BlockingQueue — producer/consumer without wait/notify
        // ============================================================
        System.out.println("=== BlockingQueue (producer/consumer) ===");

        // 5.2's Mailbox, industrial edition. put() blocks when full,
        // take() blocks when empty — all the wait/notify machinery, hidden.
        BlockingQueue<String> conveyor = new ArrayBlockingQueue<>(2);   // bounded!

        Thread producer = new Thread(() -> {
            for (int i = 1; i <= 5; i++) {
                try {
                    conveyor.put("order-" + i);
                    System.out.println("  produced order-" + i);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }, "producer");

        Thread consumer = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    String order = conveyor.take();
                    sleep(80);                       // slow consumer...
                    System.out.println("            consumed " + order);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "consumer");

        producer.start(); consumer.start();
        producer.join();  consumer.join();
        // Watch the output: the producer STALLS after ~3 items — the
        // bounded queue applies BACKPRESSURE to the fast producer.
        // This bounded-queue idea is Kafka/RabbitMQ in miniature.

        // ============================================================
        // 5. Graceful shutdown — the pre-Java-19 idiom (still asked)
        // ============================================================
        System.out.println("\n=== classic shutdown idiom ===");

        ExecutorService old = Executors.newFixedThreadPool(2);
        old.submit(() -> sleep(100));
        old.shutdown();                              // stop accepting; finish queued
        if (!old.awaitTermination(2, TimeUnit.SECONDS)) {
            old.shutdownNow();                       // timeout -> interrupt stragglers
        }
        System.out.println("terminated: " + old.isTerminated());
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
