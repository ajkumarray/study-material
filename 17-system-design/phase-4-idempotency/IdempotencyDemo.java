import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/*
 * System Design Phase 4 — Concurrency, consistency & idempotency
 * Run:  java IdempotencyDemo.java
 *
 * The problem: when many requests hit your system at once (users double-click,
 * clients retry on timeout, load balancers replay), naive "check-then-act" code
 * creates DUPLICATES and loses UPDATES. This program REPRODUCES both bugs, then
 * fixes them — and each fix maps to a real database mechanism.
 *
 * The mapping (in-memory model  ->  real database):
 *   ConcurrentHashMap.putIfAbsent   ->  a UNIQUE constraint / idempotency key
 *   AtomicReference.compareAndSet   ->  OPTIMISTIC locking (@Version column)
 *   synchronized / a lock           ->  PESSIMISTIC locking (SELECT ... FOR UPDATE)
 * You met all three primitives in Java Phase 5.2 — here's WHY they matter.
 */
public class IdempotencyDemo {

    static final int THREADS = 100;   // 100 concurrent requests hammering the same resource

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== 1. DUPLICATE CREATE (the race) ===");
        duplicateCreateRace();
        duplicateCreateFixed();

        System.out.println("\n=== 2. LOST / DUPLICATE UPDATE (the race) ===");
        lostUpdateRace();
        lostUpdateOptimistic();
        lostUpdatePessimistic();

        System.out.println("\n=== 3. IDEMPOTENT OPERATION (safe retries) ===");
        idempotentRetries();
    }

    // ============================================================
    // 1. DUPLICATE CREATE — 100 requests try to register the SAME email.
    // "check if exists, then insert" is TWO steps; threads interleave between
    // them, so many pass the check and all insert. Classic double-submit bug.
    // ============================================================
    static void duplicateCreateRace() throws InterruptedException {
        Map<String, String> users = new ConcurrentHashMap<>();
        AtomicInteger createsExecuted = new AtomicInteger();

        runConcurrently(() -> {
            if (!users.containsKey("ajay@dev.io")) {        // step 1: check  <-- race window
                sleepABit();                                // (work that widens the window)
                users.put("ajay@dev.io", "row");            // step 2: act
                createsExecuted.incrementAndGet();
            }
        });
        System.out.println("  naive check-then-insert -> created " + createsExecuted.get()
                + " rows for ONE email  (should be 1!)  <-- DUPLICATES");
    }

    static void duplicateCreateFixed() throws InterruptedException {
        Map<String, String> users = new ConcurrentHashMap<>();
        AtomicInteger createsExecuted = new AtomicInteger();

        runConcurrently(() -> {
            // putIfAbsent is ATOMIC: check-and-insert in one indivisible step.
            // Real DB equivalent: a UNIQUE constraint — the 2nd insert FAILS.
            if (users.putIfAbsent("ajay@dev.io", "row") == null) {
                createsExecuted.incrementAndGet();          // only the winner runs this
            }
        });
        System.out.println("  atomic putIfAbsent      -> created " + createsExecuted.get()
                + " row  (UNIQUE constraint / idempotency key does this in the DB)");
        assert createsExecuted.get() == 1;
    }

    // ============================================================
    // 2. LOST UPDATE — 100 requests each add 1 to a shared balance via
    // read-modify-write. Without protection, updates overwrite each other.
    // ============================================================
    static void lostUpdateRace() throws InterruptedException {
        int[] balance = {0};                                // plain field, no protection
        runConcurrently(() -> {
            int read = balance[0];                          // read
            sleepABit();
            balance[0] = read + 1;                          // modify + write (stale read!)
        });
        System.out.println("  no locking       -> balance = " + balance[0]
                + " (expected " + THREADS + ")  <-- LOST UPDATES");
    }

    static void lostUpdateOptimistic() throws InterruptedException {
        // OPTIMISTIC: assume no conflict; on write, verify nothing changed since
        // read (compareAndSet). If it did, RETRY. Great for low contention.
        // Real DB: a @Version column; UPDATE ... WHERE id=? AND version=?;
        // 0 rows updated => someone else won => reload & retry.
        AtomicReference<Integer> balance = new AtomicReference<>(0);
        AtomicInteger retries = new AtomicInteger();
        runConcurrently(() -> {
            int read;
            do {
                read = balance.get();                        // read value + (implicit) version
                if (!balance.compareAndSet(read, read + 1)) retries.incrementAndGet();
                else break;                                  // won the CAS -> committed
            } while (true);
        });
        System.out.println("  optimistic (CAS) -> balance = " + balance.get()
                + " with " + retries.get() + " retries  (@Version + retry)");
        assert balance.get() == THREADS;
    }

    static void lostUpdatePessimistic() throws InterruptedException {
        // PESSIMISTIC: assume conflict; take an exclusive lock so only one thread
        // is in the critical section at a time. Real DB: SELECT ... FOR UPDATE
        // locks the row; others WAIT. Simpler, but serializes -> less throughput.
        int[] balance = {0};
        Object rowLock = new Object();
        runConcurrently(() -> {
            synchronized (rowLock) {                         // == the row lock
                int read = balance[0];
                sleepABit();
                balance[0] = read + 1;
            }
        });
        System.out.println("  pessimistic lock -> balance = " + balance[0]
                + "  (SELECT ... FOR UPDATE serializes the writers)");
        assert balance[0] == THREADS;
    }

    // ============================================================
    // 3. IDEMPOTENCY — the same operation applied twice has the SAME effect as
    // once. Essential because networks retry: a client that times out re-sends,
    // but the first request may have already succeeded.
    // ============================================================
    static void idempotentRetries() throws InterruptedException {
        Set<String> processed = ConcurrentHashMap.newKeySet();   // remembers handled request ids
        AtomicInteger balance = new AtomicInteger(0);

        // Simulate the SAME payment (idempotency key "pay-42") delivered 100x
        // (retries + duplicates). It must apply EXACTLY ONCE.
        runConcurrently(() -> {
            String idempotencyKey = "pay-42";
            if (processed.add(idempotencyKey)) {             // add returns false if already present
                balance.addAndGet(100);                      // the real side effect, guarded
            }
            // else: a duplicate/retry -> no-op, return the original result.
        });
        System.out.println("  100 deliveries of payment 'pay-42' -> balance = " + balance.get()
                + "  (applied exactly once via the idempotency key)");
        assert balance.get() == 100;

        System.out.println("\nAll idempotency assertions passed.");
    }

    // ---- concurrency harness: fire THREADS tasks at once, wait for all ----
    static void runConcurrently(Runnable task) throws InterruptedException {
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        for (int i = 0; i < THREADS; i++) {
            new Thread(() -> {
                try { start.await(); task.run(); }
                catch (InterruptedException ignored) { }
                finally { done.countDown(); }
            }).start();
        }
        start.countDown();          // release all threads at once -> maximize contention
        done.await();
    }

    static void sleepABit() {
        try { Thread.sleep(1); } catch (InterruptedException ignored) { }   // widen the race window
    }
}
