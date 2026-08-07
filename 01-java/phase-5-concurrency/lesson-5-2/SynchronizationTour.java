import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/*
 * Lesson 5.2 — synchronized, locks, atomic types
 *
 * Three fixes for 5.1's lost-update race, in increasing sophistication:
 *   1. synchronized  — built-in mutual exclusion (monitor locks)
 *   2. ReentrantLock — explicit lock object: tryLock, fairness, interruptible
 *   3. Atomic types  — lock-free hardware CAS for single variables
 *
 * Rule of thumb: single counter/reference -> Atomic;
 *                multi-step invariant -> synchronized;
 *                need timeout/tryLock/fairness -> ReentrantLock.
 */
public class SynchronizationTour {

    public static void main(String[] args) throws InterruptedException {

        // ============================================================
        // 1. synchronized — one thread in the critical section at a time
        // ============================================================
        System.out.println("=== synchronized ===");

        SyncCounter sync = new SyncCounter();
        race(() -> sync.increment());
        System.out.println("synchronized counter: " + sync.value() + "  (exact!)");
        // How it works: every object has a MONITOR. synchronized acquires
        // it on entry, releases on exit (even via exception). Others
        // BLOCK until it's free. Bonus: unlock->lock also creates the
        // happens-before edge, so visibility comes free with it.

        // ============================================================
        // 2. ReentrantLock — synchronized with superpowers
        // ============================================================
        System.out.println("\n=== ReentrantLock ===");

        LockCounter lock = new LockCounter();
        race(() -> lock.increment());
        System.out.println("lock counter        : " + lock.value());

        // The superpower synchronized lacks — tryLock (don't wait forever):
        ReentrantLock table = new ReentrantLock();
        table.lock();
        try {
            boolean got = table.tryLock();      // reentrant: same thread CAN re-acquire
            System.out.println("reentrant tryLock   : " + got + " (same thread re-enters fine)");
            if (got) table.unlock();
        } finally {
            table.unlock();                     // ALWAYS unlock in finally
        }

        // ============================================================
        // 3. Atomic types — no locks at all
        // ============================================================
        System.out.println("\n=== atomics ===");

        AtomicInteger atomic = new AtomicInteger();
        race(atomic::incrementAndGet);
        System.out.println("atomic counter      : " + atomic.get());
        // Under the hood: CAS (compare-and-swap), a CPU instruction —
        // "set to 43 ONLY IF still 42, else tell me and I'll retry".
        // Optimistic, no blocking, no thread parked. For a single hot
        // variable this beats locks by a lot.

        // CAS exposed directly:
        AtomicInteger seat = new AtomicInteger(7);
        System.out.println("CAS 7->8 : " + seat.compareAndSet(7, 8)
                + ", CAS 7->9 : " + seat.compareAndSet(7, 9) + "  (stale expectation fails)");

        // ============================================================
        // 4. DEADLOCK — the classic disaster (and the classic escape)
        // ============================================================
        System.out.println("\n=== deadlock avoidance ===");

        // Recipe for deadlock: thread A holds lock1, wants lock2;
        // thread B holds lock2, wants lock1. Both wait forever.
        // (We do NOT run that here — it would hang the lesson!)
        //
        // Escape #1 — GLOBAL LOCK ORDER: everyone acquires lock1 THEN
        // lock2, never the reverse. Cycles become impossible.
        //
        // Escape #2 — tryLock with backoff, demonstrated live:
        ReentrantLock l1 = new ReentrantLock();
        ReentrantLock l2 = new ReentrantLock();

        Thread pessimist = new Thread(() -> transferPolitely("A->B", l1, l2), "t-ab");
        Thread optimist  = new Thread(() -> transferPolitely("B->A", l2, l1), "t-ba");
        pessimist.start(); optimist.start();
        pessimist.join();  optimist.join();
        // Opposite lock orders — normally a deadlock recipe — but tryLock
        // backs off instead of waiting, so both complete.

        // ============================================================
        // 5. The classroom classic: wait/notify (know it, rarely write it)
        // ============================================================
        System.out.println("\n=== wait/notify ===");

        Mailbox box = new Mailbox();
        Thread consumer = new Thread(() -> {
            String got = box.take();
            System.out.println("  consumer received: " + got);
        }, "consumer");
        consumer.start();
        Thread.sleep(100);                       // let the consumer start waiting
        box.put("message-42");
        consumer.join();
        // wait() releases the monitor and parks; notify() wakes a waiter.
        // ALWAYS wait in a while-loop (spurious wakeups + recheck the
        // condition). Modern code uses BlockingQueue instead (5.3) —
        // but interviews still ask for the raw mechanics.
    }

    // Harness: two threads, 100k calls each — 5.1's exact race setup.
    static void race(Runnable op) throws InterruptedException {
        Thread a = new Thread(() -> { for (int i = 0; i < 100_000; i++) op.run(); });
        Thread b = new Thread(() -> { for (int i = 0; i < 100_000; i++) op.run(); });
        a.start(); b.start();
        a.join();  b.join();
    }

    static void transferPolitely(String label, ReentrantLock first, ReentrantLock second) {
        while (true) {
            if (first.tryLock()) {
                try {
                    if (second.tryLock()) {
                        try {
                            System.out.println("  " + label + " completed by "
                                    + Thread.currentThread().getName());
                            return;
                        } finally { second.unlock(); }
                    }
                } finally { first.unlock(); }
            }
            Thread.onSpinWait();                 // brief pause, then retry from scratch
        }
    }
}

class SyncCounter {
    private int count = 0;

    // Locks on `this` for instance methods (the class object for static).
    synchronized void increment() { count++; }
    synchronized int value()      { return count; }
    // Better hygiene in real code: a private final Object lock = new Object();
    // and synchronized(lock) {...} — outsiders can't lock on your monitor.
}

class LockCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int count = 0;

    void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();       // the discipline synchronized gives you free
        }
    }

    int value() {
        lock.lock();
        try { return count; } finally { lock.unlock(); }
    }
}

class Mailbox {
    private String message;

    synchronized String take() {
        while (message == null) {          // while, NEVER if (spurious wakeups)
            try {
                wait();                    // releases the monitor + parks
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        String m = message;
        message = null;
        return m;
    }

    synchronized void put(String m) {
        message = m;
        notify();                          // wake one waiter (notifyAll for many)
    }
}
