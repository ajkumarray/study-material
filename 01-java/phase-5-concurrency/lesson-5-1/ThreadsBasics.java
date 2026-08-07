/*
 * Lesson 5.1 — Threads & the memory model basics
 *
 * A THREAD is an independent path of execution. Every Java program
 * starts with one (main); you can launch more. The JVM maps them to
 * OS threads (until virtual threads — lesson 5.5).
 *
 * Each thread has its OWN stack (locals, frames — Phase 1) but they
 * SHARE the heap (objects). Shared mutable state is where all
 * concurrency pain comes from.
 */
public class ThreadsBasics {

    public static void main(String[] args) throws InterruptedException {

        // ============================================================
        // 1. Creating and running threads
        // ============================================================
        System.out.println("=== creating threads ===");
        System.out.println("running in: " + Thread.currentThread().getName());

        // The task is a Runnable (a functional interface! () -> void):
        Runnable task = () -> System.out.println("  hello from " + Thread.currentThread().getName());

        Thread t1 = new Thread(task, "worker-1");
        Thread t2 = new Thread(task, "worker-2");

        t1.start();      // start() = ask the OS to run it CONCURRENTLY
        t2.start();      // NOT t1.run() — that would just call the method HERE
        t1.join();       // join() = wait for it to finish
        t2.join();

        // Interleaving is NOT deterministic: run this file twice and
        // worker-1/worker-2 may print in either order.

        // ============================================================
        // 2. THE RACE CONDITION — the founding bug of concurrency
        // ============================================================
        System.out.println("\n=== race condition ===");

        Counter counter = new Counter();
        Thread a = new Thread(() -> { for (int i = 0; i < 100_000; i++) counter.increment(); });
        Thread b = new Thread(() -> { for (int i = 0; i < 100_000; i++) counter.increment(); });
        a.start(); b.start();
        a.join();  b.join();

        System.out.println("expected 200000, got: " + counter.value());
        // Almost certainly LESS. Why: count++ is THREE operations —
        //   read count -> add 1 -> write back
        // Two threads can both read 41, both write 42: one increment LOST.
        // This is a "lost update"; the interleaving window is tiny but
        // 200k attempts hit it thousands of times. Fixes: lesson 5.2.

        // ============================================================
        // 3. VISIBILITY and volatile
        // ============================================================
        System.out.println("\n=== visibility / volatile ===");

        // Threads may cache shared variables (registers, CPU caches) —
        // a write by one thread is NOT guaranteed visible to others
        // unless something creates a HAPPENS-BEFORE edge:
        //   - volatile write -> subsequent volatile read
        //   - unlock -> subsequent lock of the same monitor
        //   - Thread.start() / join()
        //
        // `stop` below is volatile: the worker is GUARANTEED to see the
        // update promptly. Without volatile this loop may LITERALLY NEVER
        // END — the JIT can hoist the read out of the loop ("stop never
        // changes here, why re-read it?").
        Worker worker = new Worker();
        Thread w = new Thread(worker, "poller");
        w.start();
        Thread.sleep(100);
        worker.stop = true;                    // volatile write...
        w.join();                              // ...seen, loop exits, join returns
        System.out.println("worker stopped cleanly after " + worker.iterations + " iterations");
        // NOTE: volatile fixes VISIBILITY only — it does NOT make
        // count++ atomic (still read-modify-write). Different problems!

        // ============================================================
        // 4. Sleep, interrupt — cooperative cancellation
        // ============================================================
        System.out.println("\n=== interrupt ===");

        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(10_000);
                System.out.println("  slept full 10s (won't happen)");
            } catch (InterruptedException e) {
                // Blocking methods (sleep/join/wait) respond to interrupts
                // by throwing — this IS the cancellation mechanism:
                System.out.println("  interrupted mid-sleep — cleaning up and exiting");
            }
        }, "sleeper");
        sleeper.start();
        Thread.sleep(100);
        sleeper.interrupt();                   // polite request: "please stop"
        sleeper.join();
        // Interruption is COOPERATIVE — nothing forces a thread to die
        // (Thread.stop() was removed for good reason: it corrupted state).

        // ============================================================
        // 5. Daemon threads & lifecycle
        // ============================================================
        System.out.println("\n=== daemon + states ===");

        Thread housekeeper = new Thread(() -> {
            while (true) { }                   // infinite — but daemon, so JVM won't wait
        });
        housekeeper.setDaemon(true);           // must set BEFORE start()
        housekeeper.start();
        System.out.println("daemon running: " + housekeeper.isDaemon()
                + " (JVM exits when only daemons remain)");

        // Thread states (Thread.State): NEW -> RUNNABLE <-> (BLOCKED |
        // WAITING | TIMED_WAITING) -> TERMINATED
        Thread fresh = new Thread(() -> { });
        System.out.println("state NEW        : " + fresh.getState());
        fresh.start();
        fresh.join();
        System.out.println("state TERMINATED : " + fresh.getState());
        System.out.println("sleeper now      : " + sleeper.getState());
    }
}

class Counter {
    private int count = 0;

    void increment() {
        count++;               // NOT atomic: read, +1, write — the race window
    }

    int value() {
        return count;
    }
}

class Worker implements Runnable {
    volatile boolean stop = false;     // remove volatile -> loop may never see true
    long iterations = 0;

    @Override
    public void run() {
        while (!stop) {
            iterations++;
        }
    }
}
