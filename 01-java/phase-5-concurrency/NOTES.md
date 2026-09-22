<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · modern java](../phase-4-modern-java/NOTES.md) | [Phase 6 · tooling ➡](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Concurrency: Notes

*Source: `lesson-5-1` (`ThreadsBasics.java`) through `lesson-5-5` (`VirtualThreads.java`).*

## 5.1 — Threads & the Memory Model

A **thread** is an independent path of execution through a program. Every Java program starts with exactly one (the `main` thread); you can launch more. Each thread has its **own stack** (its local variables and call frames — Phase 1), but all threads **share the heap** (every object). Every concurrency bug, at its root, is some flavor of "shared mutable heap state accessed without coordination."

### Key Concepts

- **Creating and starting a thread**: wrap a task (a `Runnable`, a functional interface — `() -> void`) in a `new Thread(task)`, then call `.start()`. `start()` asks the OS/JVM to actually run the task **concurrently**, on a new call stack. Calling `.run()` directly instead is an ordinary, synchronous method call executed on the *current* thread — it does not start anything new. This is a classic trick question precisely because both compile and both "work" in the sense of executing the code, but only one actually achieves concurrency.
- **`join()`** blocks the calling thread until the target thread finishes.
- **Interleaving is non-deterministic.** The order in which two started threads actually execute their instructions is decided by the OS scheduler and can differ from run to run — never write code (or tests) that assumes a particular interleaving unless something explicitly enforces it.
- **The heap is shared; the stack is not.** This single fact is the reason concurrency bugs exist at all — a variable in one thread's local stack frame can never race with another thread, but any object reachable from the heap can be read and written by multiple threads simultaneously.

### Worked Example: Creating Threads

```java
Runnable task = () -> System.out.println("hello from " + Thread.currentThread().getName());

Thread t1 = new Thread(task, "worker-1");
Thread t2 = new Thread(task, "worker-2");

t1.start();      // ask the OS to run it CONCURRENTLY
t2.start();      // NOT t1.run() — that would just call the method HERE, on the current thread
t1.join();        // wait for it to finish
t2.join();
```
```
hello from worker-1
hello from worker-2
```
(or `worker-2` first — either order is valid; interleaving isn't guaranteed.)

### The Race Condition — the Founding Bug of Concurrency

```java
class Counter {
    private int count = 0;
    void increment() { count++; }   // NOT atomic: read, +1, write — the race window
    int value() { return count; }
}

Counter counter = new Counter();
Thread a = new Thread(() -> { for (int i = 0; i < 100_000; i++) counter.increment(); });
Thread b = new Thread(() -> { for (int i = 0; i < 100_000; i++) counter.increment(); });
a.start(); b.start();
a.join();  b.join();

System.out.println("expected 200000, got: " + counter.value());
// expected 200000, got: 123138     (actual measured result — almost certainly less than 200000)
```

`count++` is not a single operation at the machine level — it's **three**: read the current value, add 1, write the result back. Two threads can both read `41` before either writes, both compute `42`, and both write `42` — one of the two increments is silently **lost**. The race window is nanoseconds wide, but hammering it 200,000 times hits it thousands of times. This is what makes races so dangerous: the program doesn't crash or print an error — it just silently produces a wrong answer, and the wrongness only shows up statistically, not on every single run.

### Visibility and the Java Memory Model

```java
class Worker implements Runnable {
    volatile boolean stop = false;     // remove volatile -> loop may never see true
    long iterations = 0;
    @Override public void run() {
        while (!stop) { iterations++; }
    }
}

Worker worker = new Worker();
Thread w = new Thread(worker, "poller");
w.start();
Thread.sleep(100);
worker.stop = true;                    // volatile write...
w.join();                              // ...seen, loop exits, join returns
System.out.println("worker stopped cleanly after " + worker.iterations + " iterations");
```

Beyond atomicity, there's a second, subtler problem: **visibility**. Without any synchronization, a write made by one thread to shared memory is not guaranteed to ever become visible to another thread — CPU core caches, registers, and JIT compiler optimizations (like hoisting a "loop-invariant" read out of a loop, since from the JIT's perspective, `stop` "never changes" inside that loop) can all cause a thread to keep reading stale data forever. Without `volatile` on `stop` above, the polling loop could — in principle, though it may work "by luck" in simple cases — run **forever**, never observing the write from the other thread.

Visibility is guaranteed only across specific **happens-before** edges defined by the Java Memory Model:
- a `volatile` write, followed by a later `volatile` read of the same field,
- releasing (unlocking) a monitor, followed by later acquiring (locking) that same monitor,
- `Thread.start()` (everything visible to the starting thread before `start()` is visible to the new thread),
- the end of a thread's run, followed by a `join()` on it returning.

**`volatile` provides visibility only — not atomicity.** A `volatile int count; count++;` still races exactly like the plain `int` version above; visibility and atomicity are two entirely different problems with two entirely different fixes (5.2).

### Cooperative Cancellation

```java
Thread sleeper = new Thread(() -> {
    try {
        Thread.sleep(10_000);
    } catch (InterruptedException e) {
        System.out.println("interrupted mid-sleep — cleaning up and exiting");
    }
}, "sleeper");
sleeper.start();
Thread.sleep(100);
sleeper.interrupt();                   // polite request: "please stop"
sleeper.join();
// interrupted mid-sleep — cleaning up and exiting
```

`interrupt()` sets a flag on the target thread; it does **not** forcibly stop anything. Blocking methods like `Thread.sleep`, `Object.wait`, and `Thread.join` respond to that flag by immediately throwing `InterruptedException` — this *is* the cancellation mechanism. When you catch `InterruptedException`, either clean up and actually exit the thread, or — if you can't exit right there — re-set the interrupt flag with `Thread.currentThread().interrupt()` so code further up the call chain still knows a cancellation was requested. **Never silently swallow it.** `Thread.stop()` (forcibly killing a thread) was deprecated and removed precisely because killing a thread in the middle of updating some shared invariant leaves that shared state permanently corrupted, with no safe way to detect or repair it.

### Thread Lifecycle and Daemon Threads

```
NEW → RUNNABLE ⇄ (BLOCKED — waiting for a monitor | WAITING — wait()/join() | TIMED_WAITING — sleep()/timed waits) → TERMINATED
```

```java
Thread fresh = new Thread(() -> { });
System.out.println(fresh.getState());   // NEW
fresh.start();
fresh.join();
System.out.println(fresh.getState());   // TERMINATED
```

`Thread.State` distinguishes `BLOCKED` (waiting to acquire a monitor lock another thread holds) from `WAITING`/`TIMED_WAITING` (voluntarily parked via `wait()`/`join()`/`sleep()`) — a distinction that matters when reading real thread dumps to diagnose contention vs. legitimate waiting.

```java
Thread housekeeper = new Thread(() -> { while (true) { } });
housekeeper.setDaemon(true);           // must be set BEFORE start()
housekeeper.start();
```

A **daemon thread** doesn't keep the JVM process alive by itself — once every remaining thread is a daemon, the JVM exits, even if a daemon thread is mid-execution. The JVM's own garbage collector runs on a daemon thread, for example. `setDaemon(true)` must be called before `start()`; it has no effect afterward.

### Why It's Useful

Race conditions and visibility bugs are among the hardest production bugs to reproduce, precisely because they depend on timing that varies run to run, machine to machine, and load to load — a bug that occurs once in ten thousand requests can sit undetected for months. Understanding the happens-before model isn't academic: it's the exact reasoning you need to decide whether a piece of shared state genuinely needs synchronization, or whether existing happens-before edges (like `start()`/`join()`) already make it safe without adding more.

### Summary / Key Takeaways

- Threads share the heap but have private stacks; shared mutable heap state is the root of every concurrency bug.
- `start()` runs concurrently; calling `run()` directly is just a normal method call — no concurrency happens.
- A race condition is a lost update from an unsynchronized read-modify-write sequence — the program keeps running but silently produces a wrong answer.
- `volatile` guarantees visibility (a happens-before edge on read/write) but does **not** make compound operations like `count++` atomic.
- Cancellation is cooperative via `interrupt()`; never swallow `InterruptedException` silently, and never rely on the removed `Thread.stop()`.

## 5.2 — `synchronized`, Locks, and Atomics

Three progressively more sophisticated fixes for 5.1's lost-update race, each measured to produce the exact expected count of 200,000.

### `synchronized`

```java
class SyncCounter {
    private int count = 0;
    synchronized void increment() { count++; }   // locks `this`
    synchronized int value()      { return count; }
}

SyncCounter sync = new SyncCounter();
// (200,000 concurrent increments across two threads)
System.out.println(sync.value());   // 200000 — exact!
```

Every Java object has an associated **monitor**. Entering a `synchronized` method or block acquires the monitor of the relevant object; leaving it — normally, or via an exception — releases the monitor automatically. Any other thread trying to enter a section synchronized on the same object **blocks** until it's free. An instance method locks `this`; a `static synchronized` method locks the `Class` object itself (one monitor shared by *all* instances). `synchronized` is **reentrant** — a thread already holding a monitor can re-enter another `synchronized` section guarded by the same monitor without deadlocking itself. Acquiring/releasing a monitor also establishes a happens-before edge, so `synchronized` gives you **visibility for free**, on top of mutual exclusion.

**Hygiene tip**: in real code, prefer locking a dedicated `private final Object lock = new Object();` and `synchronized (lock) { ... }` rather than locking `this` directly — locking `this` exposes your monitor to any external code holding a reference to your object, which could accidentally (or maliciously) synchronize on it too, causing unexpected blocking or contention you have no control over.

### `ReentrantLock` — `synchronized` With Superpowers

```java
class LockCounter {
    private final ReentrantLock lock = new ReentrantLock();
    private int count = 0;
    void increment() {
        lock.lock();
        try {
            count++;
        } finally {
            lock.unlock();       // the discipline synchronized gives you automatically
        }
    }
}
```

`ReentrantLock` is an explicit lock object with `lock()`/`unlock()` methods — **always call `unlock()` in a `finally` block**, since nothing does it for you automatically the way `synchronized`'s block structure does. In exchange for that extra discipline, it offers capabilities `synchronized` doesn't have:

- **`tryLock()`** (optionally with a timeout) — attempt to acquire without blocking forever; back off and do something else if it fails.
- **`lockInterruptibly()`** — a blocked lock-acquisition attempt can itself be interrupted.
- **Fairness option** — a fair lock grants access in roughly request order rather than allowing barging, at some throughput cost.
- **Multiple `Condition` objects** per lock — richer wait/notify-style coordination than a single object monitor allows.

```java
ReentrantLock table = new ReentrantLock();
table.lock();
try {
    boolean got = table.tryLock();      // reentrant: the SAME thread can re-acquire
    System.out.println(got);              // true
    if (got) table.unlock();
} finally {
    table.unlock();
}
```

### Atomic Types — Lock-Free Concurrency

```java
AtomicInteger atomic = new AtomicInteger();
// (200,000 concurrent atomic::incrementAndGet calls)
System.out.println(atomic.get());   // 200000

AtomicInteger seat = new AtomicInteger(7);
System.out.println(seat.compareAndSet(7, 8));   // true  — value still 7, swap succeeds
System.out.println(seat.compareAndSet(7, 9));   // false — value is now 8, stale expectation fails
```

`AtomicInteger`, `AtomicLong`, and `AtomicReference` achieve thread safety without ever blocking a thread, using **CAS (compare-and-swap)** — a single CPU instruction meaning "write the new value only if the current value still equals what I expected it to be; otherwise report failure and change nothing." Callers that need to update based on the current value loop: read, compute, attempt the CAS, and retry from the read step if it failed because another thread got there first. This is called **optimistic concurrency**: it assumes contention is low, avoids ever parking a thread, and for a single hot variable it typically outperforms lock-based approaches significantly. Useful methods: `incrementAndGet()`, `compareAndSet(expected, new)`, `updateAndGet(fn)`.

**Under very heavy write contention**, the CAS retry loop itself becomes the bottleneck (many threads all repeatedly failing and retrying against the same variable) — `LongAdder` addresses this by internally striping the count across multiple cells that different threads update independently, summing them only when the total is read, trading a slightly more expensive read for a much cheaper write under contention.

### Choosing Among the Three

| need | reach for |
|---|---|
| a single counter/reference, updated independently | `Atomic*` classes |
| a multi-step invariant (check-then-act, coordinated updates across multiple fields) | `synchronized` |
| a timeout, fairness, interruptible waiting, or multiple wait-conditions | `ReentrantLock` |

### Deadlock

A **deadlock** is a cycle of threads, each holding a lock the next thread in the cycle needs, so all of them wait forever. Classic two-thread recipe: thread A holds `lock1` and wants `lock2`; thread B holds `lock2` and wants `lock1`.

```java
static void transferPolitely(String label, ReentrantLock first, ReentrantLock second) {
    while (true) {
        if (first.tryLock()) {
            try {
                if (second.tryLock()) {
                    try {
                        System.out.println(label + " completed by " + Thread.currentThread().getName());
                        return;
                    } finally { second.unlock(); }
                }
            } finally { first.unlock(); }
        }
        Thread.onSpinWait();   // brief pause, then retry from scratch
    }
}

Thread pessimist = new Thread(() -> transferPolitely("A->B", l1, l2), "t-ab");
Thread optimist  = new Thread(() -> transferPolitely("B->A", l2, l1), "t-ba");
// both complete — opposite lock orders, normally a deadlock recipe, don't hang here
```

Two standard escapes: (1) **global lock ordering** — every thread in the system always acquires shared locks in the same, agreed-upon order, making a circular wait structurally impossible; this is the standard, preferred production fix. (2) **`tryLock` with backoff** — as shown above, a thread that can't get every lock it needs releases what it already holds and retries, instead of holding one lock while blocking indefinitely on another. Even with genuinely opposite acquisition orders between the two threads, this pattern completes without hanging. Deadlocks can be diagnosed in a running JVM with `jstack <pid>`, which literally prints "Found one Java-level deadlock" along with the exact thread/lock cycle when one exists.

### `wait()` / `notify()` — the Classroom Classic

```java
class Mailbox {
    private String message;
    synchronized String take() {
        while (message == null) {          // WHILE, never if — spurious wakeups
            try {
                wait();                    // releases the monitor and parks
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
        notify();                          // wake one waiter (notifyAll wakes all of them)
    }
}
```

`wait()` and `notify()`/`notifyAll()` are defined on every `Object` and must be called from **within a `synchronized` block/method holding that object's monitor** — calling them without holding the monitor throws `IllegalMonitorStateException`. `wait()` **releases the monitor** and parks the calling thread (unlike `Thread.sleep()`, which holds every lock it has while sleeping). `notify()` wakes one arbitrary waiting thread; `notifyAll()` wakes every waiting thread. **Always call `wait()` inside a `while` loop that rechecks the actual condition**, never a plain `if` — "spurious wakeups" (a thread waking up with no corresponding `notify`) are explicitly permitted by the JVM spec, so the condition must always be re-verified after waking, not assumed true. Modern code almost always reaches for higher-level tools instead — `BlockingQueue`, `CountDownLatch`, `Condition` (5.3) — but the raw `wait`/`notify` mechanics remain a standard interview topic, since those higher-level tools are themselves built on exactly this mechanism.

### Why It's Useful

Every production concurrency bug is ultimately either a missing lock (race/lost update), a missing happens-before edge (visibility), or a badly-ordered pair of locks (deadlock) — this lesson's three tools (`synchronized`, `ReentrantLock`, atomics) and the deadlock-avoidance pattern are the direct fixes for exactly those three failure modes, and knowing which one fits a given situation (single variable vs. multi-step invariant vs. needing a timeout) is a routine real-world design decision, not just interview trivia.

### Summary / Key Takeaways

- `synchronized` gives mutual exclusion plus visibility, is reentrant, and locks `this` (instance methods) or the `Class` object (static methods) — prefer a private dedicated lock object over `this` in real code.
- `ReentrantLock` adds `tryLock`, interruptible waiting, fairness, and multiple `Condition`s, at the cost of manual `unlock()` discipline in `finally`.
- Atomic types use lock-free CAS — fastest for a single hot variable; `LongAdder` wins under heavy write contention specifically.
- Prevent deadlock with a global lock ordering, or with `tryLock` + backoff when ordering can't be guaranteed.
- `wait()`/`notify()` must be called holding the monitor, and `wait()` must always be in a `while` loop rechecking the condition due to spurious wakeups.

## 5.3 — Executors & Thread Pools

Raw `new Thread()` per task doesn't scale: platform thread creation is expensive (roughly 1MB of stack reserved per thread, plus real OS syscalls), and letting the number of threads grow unbounded can exhaust process/OS resources entirely. A **thread pool** fixes this: a fixed number of long-lived worker threads continuously pull tasks off a shared queue — you submit *tasks*, and stop directly managing *threads* at all.

```java
ExecutorService pool = Executors.newFixedThreadPool(4);
pool.submit(task);       // task is queued -> some worker thread picks it up
```

### Key Concepts

- **`ExecutorService.submit(...)`** accepts either a `Runnable` (`void run()`, cannot throw a checked exception) or a `Callable<T>` (`T call() throws Exception` — returns a value, and can throw checked exceptions). Both return a `Future<T>` (a `Future<Void>` in the `Runnable` case, conceptually).
- **`Future.get()` blocks** until the task completes, returning its result — or, if the task threw, rethrowing that failure wrapped in `ExecutionException` (check `getCause()` for the real, original exception — 3.1's exception-chaining pattern again). Prefer the timeout overload `get(timeout, unit)` in real services; an unqualified `get()` can block forever if a task hangs, silently leaking a waiting thread.
- **`invokeAll(tasks)`** submits a whole batch and blocks until every one of them completes, returning a `List<Future<T>>` in the same order as the input tasks.
- **Since Java 19, `ExecutorService` implements `AutoCloseable`** — `try (var pool = Executors.newFixedThreadPool(3)) { ... }` automatically shuts the pool down and waits for outstanding work to finish when the block exits. The pre-19 idiom (`shutdown()` → `awaitTermination(timeout)` → `shutdownNow()` if that times out) still matters and is still commonly asked about.

### Worked Example: Fixed Pool

```java
try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
    for (int i = 1; i <= 6; i++) {
        int jobId = i;
        pool.submit(() -> {
            System.out.println("job-" + jobId + " on " + Thread.currentThread().getName());
            sleep(50);
        });
    }
}   // close(): stops accepting new tasks, waits for the 6 submitted tasks to finish
System.out.println("all jobs done (6 jobs, only 3 pool threads — queued + reused)");
```

Six tasks are submitted to a pool of only three worker threads — the pool queues the extra tasks and reuses each worker for multiple jobs rather than creating six separate threads.

### `Callable<T>` and `Future<T>`

```java
Callable<Integer> slowSquare = () -> { sleep(200); return 21 * 2; };

Future<Integer> future = pool.submit(slowSquare);
System.out.println(future.isDone());     // false (probably — depends on timing)
Integer answer = future.get();           // BLOCKS until the result exists
System.out.println(answer);                // 42

List<Callable<String>> checks = List.of(
        () -> { sleep(150); return "db: ok"; },
        () -> { sleep(100); return "cache: ok"; },
        () -> { sleep(120); return "queue: ok"; });
long t0 = System.currentTimeMillis();
List<Future<String>> results = pool.invokeAll(checks);
for (Future<String> f : results) System.out.println(f.get());
System.out.println((System.currentTimeMillis() - t0) + " ms");
// db: ok
// cache: ok
// queue: ok
// ~150 ms (not 370 — the three checks ran overlapped on the pool's threads)
```

### The Pool Zoo — Choosing an Implementation

| factory | shape | best for |
|---|---|---|
| `newFixedThreadPool(n)` | N workers, unbounded queue | CPU-bound work; `n` ≈ number of cores |
| `newCachedThreadPool()` | grows on demand, reuses idle threads | bursty, short-lived tasks — **danger: unbounded thread count under sustained load** |
| `newSingleThreadExecutor()` | one worker, strict order | serial execution where task order must be guaranteed |
| `newScheduledThreadPool(n)` | supports delayed/periodic scheduling | cron-like recurring or delayed tasks |
| `newVirtualThreadPerTaskExecutor()` | one fresh virtual thread per task | I/O-bound work at very large scale (5.5) |

**Production advice**: for anything beyond a quick script, construct `ThreadPoolExecutor` directly rather than one of the `Executors` convenience factories — you get explicit control over core/maximum pool size, and critically, a **bounded task queue with an explicit rejection policy**, so that overload under real load causes tasks to fail fast (or be rejected/handled deliberately) instead of the unbounded queue silently growing until the process runs out of memory. Sizing intuition: for CPU-bound work, roughly `cores` threads is optimal (more just adds context-switch overhead); for I/O-bound work, `cores × (1 + wait_time/compute_time)` is a common heuristic — or sidestep the arithmetic entirely with virtual threads (5.5).

### `BlockingQueue` — Producer/Consumer Without Manual `wait`/`notify`

```java
BlockingQueue<String> conveyor = new ArrayBlockingQueue<>(2);   // bounded!

Thread producer = new Thread(() -> {
    for (int i = 1; i <= 5; i++) {
        conveyor.put("order-" + i);            // BLOCKS once the queue is full
        System.out.println("produced order-" + i);
    }
}, "producer");

Thread consumer = new Thread(() -> {
    for (int i = 1; i <= 5; i++) {
        String order = conveyor.take();          // BLOCKS while the queue is empty
        sleep(80);                                // slow consumer...
        System.out.println("consumed " + order);
    }
}, "consumer");
```

`BlockingQueue` is 5.2's `Mailbox` example, industrialized: `put()` blocks the calling thread while the queue is full, `take()` blocks while it's empty — all the `wait`/`notify` machinery from 5.2 is implemented internally and hidden behind a simple, safe queue API. Because the queue above is **bounded** to size 2, the fast producer visibly **stalls** once it's two items ahead of the slower consumer — this is **backpressure**: instead of an unbounded buffer letting a fast producer run arbitrarily far ahead (risking unbounded memory growth), the bounded queue forces the producer to slow down to the consumer's actual pace. This exact bounded-queue idea, generalized and distributed, is the conceptual seed of real message brokers like Kafka and RabbitMQ.

Related coordination primitives worth knowing by name: `CountDownLatch` (block until N independent events have all occurred, then release all waiters — typically single-use), `Semaphore` (limit concurrent access to N permits — e.g., "no more than 10 concurrent database connections"), `CyclicBarrier` (a rendezvous point where N threads all wait for each other before any of them proceeds — and, unlike `CountDownLatch`, it's reusable).

### Concurrent Collections

`ConcurrentHashMap` uses lock striping and CAS internally, avoids ever holding one global lock for the whole map, and exposes genuinely **atomic compound operations** — `putIfAbsent`, `compute`, `merge` — that would otherwise require external synchronization around a plain `HashMap` to do safely; there is essentially never a good reason to manually synchronize access to a plain `HashMap` when `ConcurrentHashMap` exists. `CopyOnWriteArrayList` makes every mutation (`add`, `remove`, …) copy the entire backing array, which makes reads completely lock-free and very fast, at the cost of expensive writes — the right fit for read-heavy, rarely-mutated structures like a list of event listeners. Both use **fail-safe, weakly-consistent iterators**, in direct contrast to the Collections Framework's fail-fast iterators (Phase 3): iterating a `ConcurrentHashMap` while another thread mutates it will not throw `ConcurrentModificationException` — you'll just see some reasonably consistent, but not perfectly snapshotted, view of the data.

### Why It's Useful

Thread pool sizing, bounded queues, and rejection policies are exactly what stands between a service that degrades gracefully under load and one that falls over with an `OutOfMemoryError` during a traffic spike — this is genuinely production-critical, not academic. `ConcurrentHashMap` and `BlockingQueue` are two of the single most commonly reached-for concurrency tools in real Java backend code, well ahead of raw `synchronized` blocks in typical application code.

### Summary / Key Takeaways

- Thread pools amortize the real cost of thread creation and bound resource usage; you submit tasks, not threads.
- `Callable<T>` returns a value and can throw checked exceptions; `Future.get()` blocks and rethrows task failures wrapped in `ExecutionException` — prefer the timeout overload.
- `newCachedThreadPool` is unbounded and can OOM under sustained load; production code typically wants `ThreadPoolExecutor` directly, with a bounded queue and an explicit rejection policy.
- `BlockingQueue` hides `wait`/`notify` machinery and provides backpressure via bounded capacity — the conceptual root of message-broker design.
- `ConcurrentHashMap` and `CopyOnWriteArrayList` are fail-safe, not fail-fast — prefer them over manually synchronizing plain collections.

## 5.4 — `CompletableFuture`

A plain `Future` (5.3) has exactly one move once you have it: block on `.get()`. `CompletableFuture` adds **composable asynchronous pipelines**: "when this finishes, then do that; combine it with this other independent result; and here's what to do if it fails" — all expressed declaratively, without ever blocking between stages. Conceptually, it's Streams (4.2) applied to **time** instead of **data**, and the vocabulary maps over almost exactly.

### The Vocabulary Mapping

| sync / streams concept | `CompletableFuture` method | meaning |
|---|---|---|
| `map` | `thenApply(fn)` | transform the result once it's ready |
| `flatMap` | `thenCompose(fn -> CF)` | chain a *dependent* async call, flattening `CF<CF<T>>` |
| zip | `thenCombine(otherCf, biFn)` | join two *independent* futures once both complete |
| `forEach` | `thenAccept(consumer)` / `thenRun(runnable)` | consume the result / just run something next |
| `catch` | `exceptionally(fn)` | recover from failure with a fallback value |
| `finally` | `whenComplete((v, ex) -> ...)` | observe (value, exception) without changing either |
| both branches | `handle((v, ex) -> result)` | transform either the success or the failure into a new result |

### Worked Example: `supplyAsync` + `thenApply`

```java
CompletableFuture<String> greeting = CompletableFuture
        .supplyAsync(() -> {                       // runs asynchronously on the given pool
            sleep(150);
            return "ajay";
        }, pool)
        .thenApply(String::toUpperCase)            // transform once ready
        .thenApply(name -> "Hello, " + name + "!");

System.out.println("pipeline built, main thread is FREE (not blocked)");
System.out.println(greeting.join());   // join() only at the edge — Hello, AJAY!
```

Building the pipeline itself doesn't block the calling thread at all — the work happens asynchronously, and `.join()` (an unchecked-exception cousin of `.get()`) is called only once, at the very edge where the final result is actually needed.

### `thenCompose` — the Async `flatMap`

```java
static CompletableFuture<Profile> fetchProfile(String id, ExecutorService pool) {
    return CompletableFuture.supplyAsync(() -> { sleep(100); return new Profile(id); }, pool);
}
static CompletableFuture<Orders> fetchOrders(Profile p, ExecutorService pool) {
    return CompletableFuture.supplyAsync(() -> { sleep(100); return new Orders(7); }, pool);
}

Orders orders = fetchProfile("u1", pool)
        .thenCompose(p -> fetchOrders(p, pool))
        .join();
System.out.println(orders.count());   // 7
```

`fetchOrders` itself returns a `CompletableFuture<Orders>`. Using `thenApply` here would produce the awkward, doubly-wrapped `CompletableFuture<CompletableFuture<Orders>>` — exactly the same problem `Optional.map` has when its mapper returns another `Optional`. `thenCompose` flattens that extra layer, which is precisely what you need for **dependent** async calls (the second call genuinely needs the first call's result before it can even start).

### `thenCombine` — Joining Two Independent Futures

```java
long t0 = System.currentTimeMillis();

CompletableFuture<Double> price = CompletableFuture.supplyAsync(() -> { sleep(200); return 4999.0; }, pool);
CompletableFuture<Double> tax   = CompletableFuture.supplyAsync(() -> { sleep(200); return 0.18; }, pool);

Double total = price.thenCombine(tax, (p, t) -> p * (1 + t)).join();
long elapsed = System.currentTimeMillis() - t0;

System.out.println(total + " in " + elapsed + " ms");
// 5898.82 in ~200 ms  (two 200ms calls ran in PARALLEL, not 400ms sequentially)
```

Unlike `thenCompose`, `thenCombine` joins two futures that are **independent of each other** and started at the same time — both `price` and `tax` begin executing immediately and concurrently, and `thenCombine` fires its `BiFunction` only once *both* have completed. The measured total time (~200ms) proves the two calls genuinely ran in parallel rather than one after the other (which would have taken ~400ms).

### Fan-Out With `allOf` / `anyOf`

```java
List<CompletableFuture<String>> services = List.of(
        healthCheck("db", 120, pool), healthCheck("cache", 80, pool), healthCheck("queue", 100, pool));

CompletableFuture.allOf(services.toArray(CompletableFuture[]::new)).join();
services.forEach(f -> System.out.println(f.join()));
// db: ok (120ms)
// cache: ok (80ms)
// queue: ok (100ms)
```

`allOf(...)` returns a `CompletableFuture<Void>` that completes once *every* given future has completed — you then collect the actual results from the original future references, as shown. `anyOf(...)` completes as soon as the *first* of the given futures completes — useful for racing redundant replicas or applying a timeout against a slower fallback path.

### Error Handling

```java
String risky = CompletableFuture
        .supplyAsync(() -> { throw new IllegalStateException("payment gateway down"); }, pool)
        .exceptionally(ex -> {                     // an async catch block
            return "queued-for-retry";
        })
        .join();
System.out.println(risky);   // queued-for-retry

CompletableFuture.supplyAsync(() -> { sleep(5_000); return "slow"; }, pool)
        .orTimeout(200, TimeUnit.MILLISECONDS)
        .join();
// throws — the future is completed exceptionally with a TimeoutException after 200ms
```

An exception thrown inside a stage **skips every downstream `thenApply`/`thenAccept`/`thenCompose` stage** until it reaches a stage that actually handles failure — `exceptionally(fn)` (recover, producing a fallback value on the failure path only), `handle((value, ex) -> result)` (called on *either* outcome, letting you transform both success and failure into a unified result), or `whenComplete((value, ex) -> ...)` (observe both outcomes without changing the eventual result — the async equivalent of `finally`). This is exactly the same skip-to-handler semantics as a synchronous `throw` propagating up to a matching `catch`. `orTimeout(duration, unit)` and `completeOnTimeout(fallbackValue, duration, unit)` are the guard rails every real service pipeline needs against a dependency that simply never responds. Failures generally surface **wrapped** in `CompletionException` (or `ExecutionException` if observed via `.get()`) — inspect `.getCause()` for the actual original exception.

### Threading Fine Print

`CompletableFuture.supplyAsync(supplier)` **without** an explicit executor argument runs on `ForkJoinPool.commonPool()` — one pool, shared JVM-wide, sized to the number of CPU cores, and used internally by parallel streams too. **Never block that shared pool with I/O work** — a slow blocking call submitted to the common pool can starve every other unrelated piece of code (including parallel streams elsewhere in the same JVM) that also depends on that pool. Always pass an explicit dedicated executor for I/O-bound async work (as every example above does), or use virtual threads instead (5.5) and stop worrying about pool sizing entirely. Note also that non-`*Async` stage variants (`thenApply` vs. `thenApplyAsync`) may execute directly on whichever thread happens to *complete* the previous stage, rather than hopping to the pool — occasionally surprising if that completing thread turns out to be, say, an I/O callback thread you didn't expect to be running application logic on.

### Why It's Useful

`CompletableFuture` is the standard way to compose multiple independent or dependent asynchronous operations in Java without either blocking threads unnecessarily or hand-rolling callback-based code (with all its nesting and error-propagation pitfalls). The `thenCombine` timing example is a direct, measurable illustration of real fan-out parallelism: two 200ms calls finishing in ~200ms total, not 400ms, is exactly the kind of latency win this API exists to deliver in real services making multiple downstream calls.

### Summary / Key Takeaways

- `CompletableFuture` composes async pipelines declaratively; the vocabulary maps directly onto Streams: `thenApply` = map, `thenCompose` = flatMap, `thenCombine` = zip two independent results.
- `thenCompose` is for *dependent* calls (the second needs the first's result); `thenCombine` is for *independent* calls that can run in parallel.
- Exceptions skip downstream stages until a handler (`exceptionally`/`handle`/`whenComplete`) — exactly like synchronous throw-to-catch; always pair long-running pipelines with `orTimeout`/`completeOnTimeout`.
- `supplyAsync` without an explicit executor runs on the shared `ForkJoinPool.commonPool()` — never block it with I/O; pass a dedicated executor instead.
- Call `.join()`/`.get()` only at the edge of a pipeline, not between stages — that's what defeats the entire point of composing asynchronously.

## 5.5 — Virtual Threads (Java 21)

**The problem they solve**: platform threads are thin wrappers around real OS threads — roughly 1MB of stack reserved per thread, expensive to create, and practically capped at a few thousand concurrently before resource exhaustion becomes a real risk. But the overwhelming majority of typical server workloads spend most of their time **waiting** — on a database, an HTTP call, a disk read — not computing. Thread pools, `CompletableFuture` pipelines (5.4), and fully reactive frameworks are all, at their core, workarounds for the fact that blocking a platform thread wastes its entire expensive footprint doing nothing.

### The Mechanism

**Virtual threads** are JVM-managed, not OS-managed: each has a tiny, KB-scale stack that can grow as needed, so **millions** can coexist without trouble. Each virtual thread runs "mounted" on a **carrier** — an ordinary platform thread drawn from a small internal pool (roughly one carrier per CPU core, by default). When a virtual thread performs a blocking operation (sleeping, a blocking socket read, waiting on a lock), the JVM **unmounts** it from its carrier entirely, freeing that carrier to immediately go mount and run some *other* ready virtual thread. When the blocking operation completes, the original virtual thread gets remounted onto some available carrier and resumes. **Blocking effectively becomes nearly free** — this cheap-blocking property is the entire point of the feature.

```java
Thread vt = Thread.ofVirtual().name("my-virtual").start(() -> System.out.println(Thread.currentThread()));
vt.join();
// VirtualThread[#31,my-virtual]/runnable@ForkJoinPool-1-worker-1
//                                          ^ its carrier — a real OS thread from a small pool
```

### Worked Example: 10,000 Concurrent Blocking Tasks

```java
try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
    for (int i = 0; i < 10_000; i++) {
        exec.submit(() -> { sleep(100); completed.incrementAndGet(); });
    }
}   // waits for all submitted tasks to finish

System.out.println(completed.get() + " tasks, total: " + Duration.between(t0, Instant.now()).toMillis() + " ms");
// 10000 tasks, total: 136 ms
```

10,000 tasks, each individually blocking for 100ms — roughly 1,000 thread-seconds of total waiting — complete in about **136 milliseconds of wall-clock time**. The same experiment with 10,000 *platform* threads would require on the order of 10GB of reserved stack memory and would very plausibly throw `OutOfMemoryError` before even starting.

### Creating Virtual Threads

```java
Thread.ofVirtual().start(runnable);                       // one virtual thread, directly
Executors.newVirtualThreadPerTaskExecutor();               // an executor handing out a FRESH virtual thread per task
```

**Never pool virtual threads.** They're deliberately designed to be cheap and disposable — one task gets one fresh virtual thread, used once, then discarded. Pooling them (trying to reuse a fixed set of virtual threads the way you would platform threads) reintroduces exactly the scarcity problem they were built to eliminate, for no benefit. If you need to **limit concurrency** — say, no more than 10 simultaneous outbound calls to a rate-limited downstream service — use a `Semaphore` (5.3) to gate access, not a smaller thread pool.

### Limits and Gotchas

- **CPU-bound work gains nothing from virtual threads.** They don't make computation faster — they don't add CPU cores. For genuinely CPU-bound work (heavy computation, not waiting), the number of available cores is still the hard limit, and a fixed-size platform thread pool sized to the core count (5.3) remains the right tool.
- **Pinning**: if a virtual thread blocks while holding a `synchronized` lock (or inside certain native call frames), the JVM historically could **not** unmount it — the carrier stays stuck running that one blocked virtual thread, defeating the scalability win for that code path. The fix on hot blocking paths is to use `ReentrantLock` instead of `synchronized`, since `ReentrantLock`-based blocking doesn't pin. (Java 24 substantially fixed the `synchronized` case specifically — but the concept of pinning, and knowing this history, remains standard interview material.) Pinning can be diagnosed with the JVM flag `-Djdk.tracePinnedThreads`.
- **`ThreadLocal` still works** with virtual threads, but millions of virtual threads each holding their own `ThreadLocal` value is a real memory consideration — **scoped values** (a newer, more structured mechanism) are positioned as the long-term successor for this use case.

### The Programming-Model Payoff

```java
try (ExecutorService exec = Executors.newVirtualThreadPerTaskExecutor()) {
    var future = exec.submit(() -> {
        String user = fetchSlow("user-service", 100);     // block — who cares
        String orders = fetchSlow("order-service", 100);  // block again
        return user + " + " + orders;
    });
    System.out.println(future.get());
}
```

The entire reason `CompletableFuture`-style pipelines (5.4) exist in the first place is that blocking used to be expensive. With virtual threads, straightforward **sequential blocking code** — fetch the user, then fetch their orders, just block between the two calls like it's 2005 — achieves the same practical scalability as an elaborately composed async pipeline, without any of that composition ceremony. This is why Spring Boot 3.2+ offers a single configuration flag, `spring.threads.virtual.enabled=true`, to run every incoming web request on its own virtual thread.

**Structured concurrency** (`StructuredTaskScope`, still a preview API as of Java 21) is the natural companion direction: it treats a group of forked subtasks as a single unit — fork several children, join them all together, and if any one of them fails, the rest are automatically cancelled rather than left running as orphaned, unmanaged work. Worth knowing by name as "where `java.util.concurrent` is heading," even before it's finalized.

### Why It's Useful

Virtual threads are the single biggest concurrency-model shift in Java in years, because they let ordinary, easy-to-read, easy-to-debug blocking code scale to workloads that previously required either large, carefully-tuned thread pools or a full rewrite into asynchronous/reactive style. For any I/O-heavy service — which describes most typical web backends — this is a direct, practical simplification, not just a performance number.

### Summary / Key Takeaways

- Virtual threads are JVM-managed, KB-scale-stack threads that unmount from their carrier platform thread whenever they block, making blocking nearly free and allowing millions to run concurrently.
- They help I/O-bound (waiting) workloads dramatically; they do nothing for CPU-bound (computing) workloads, where core count is still the hard limit.
- Never pool virtual threads — they're disposable by design; use a `Semaphore` to limit concurrency instead.
- Pinning (blocking inside `synchronized`) can prevent unmounting on older JVMs — prefer `ReentrantLock` on hot blocking paths; diagnose with `-Djdk.tracePinnedThreads`.
- Virtual threads let sequential, blocking-style code achieve async-level scalability, which is why Spring Boot 3.2+ can enable them with a single configuration flag.
