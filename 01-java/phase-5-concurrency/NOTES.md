<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · modern java](../phase-4-modern-java/NOTES.md) | [Phase 6 · tooling ➡](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Concurrency: Notes

## 5.1 — Threads & the memory model

**Model:** each thread has its own *stack*; all threads share the *heap*. Every concurrency bug is some flavor of "shared mutable heap state + unsynchronized access."

**Mechanics:** `new Thread(runnable).start()` — `start()` runs concurrently; calling `run()` directly just invokes a method on the current thread (classic trap). `join()` waits for completion. Scheduling/interleaving is **non-deterministic** — never rely on observed ordering.

**The race condition (measured: 200,000 increments → 123,138):** `count++` is read → add → write; two threads can read the same value and one increment is *lost*. The window is nanoseconds wide; hit 200k times, it fails thousands of times. Races don't announce themselves — the program is silently wrong.

**Visibility & the Java Memory Model:** without synchronization, a write by one thread may never be seen by another (CPU caches, registers, JIT hoisting — a non-`volatile` `while (!stop)` loop can literally run forever). Visibility is guaranteed only across **happens-before** edges: volatile write → later volatile read; monitor unlock → later lock; `Thread.start()` → run; run end → `join()` return. **`volatile` = visibility only** — it does *not* make `count++` atomic (different problems: visibility vs atomicity).

**Cancellation is cooperative:** `interrupt()` sets a flag; blocking calls (`sleep`/`wait`/`join`) respond by throwing `InterruptedException`. On catching it: clean up and exit, or re-set the flag (`Thread.currentThread().interrupt()`) — never swallow silently. `Thread.stop()` was removed because killing threads mid-invariant corrupts state.

**Lifecycle:** NEW → RUNNABLE ⇄ (BLOCKED — waiting for a monitor | WAITING — `wait`/`join` | TIMED_WAITING — `sleep`/timed waits) → TERMINATED. **Daemon threads** don't keep the JVM alive (`setDaemon(true)` before `start()`).

## 5.2 — synchronized, locks, atomics

Three fixes for the race, all measured exact (200,000):

**1. `synchronized`** — every object has a *monitor*; a synchronized method/block acquires it on entry, releases on exit (exceptions included), others block. Instance methods lock `this`, static methods lock the `Class` object. Also creates the happens-before edge → visibility free. *Reentrant*: a thread can re-acquire its own monitor. Hygiene: lock a `private final Object lock` rather than `this` (outsiders can't grab your monitor). Keep critical sections *small*.

**2. `ReentrantLock`** — explicit `lock()`/`unlock()` (always unlock in `finally`). Superpowers over synchronized: `tryLock()` (+timeout) — opt out instead of waiting forever; `lockInterruptibly()`; fairness option; multiple `Condition`s. Cost: discipline (forgetting unlock = permanent deadlock).

**3. Atomics (`AtomicInteger`, `AtomicLong`, `AtomicReference`)** — lock-free via **CAS** (compare-and-swap, a CPU instruction): "write only if the value still equals what I expected; else report failure" — callers loop and retry. Optimistic, no parking, fastest for single hot variables. `incrementAndGet`, `compareAndSet`, `updateAndGet(fn)`. `LongAdder` beats AtomicLong under heavy contention (striped cells).

**Choosing:** single variable → atomic; multi-step invariant (check-then-act, multiple fields) → synchronized; need timeout/fairness/interruptibility → ReentrantLock.

**Deadlock:** cycle of threads each holding a lock the next wants. Prevention: **global lock ordering** (everyone acquires in the same order — cycles impossible), or `tryLock` + backoff (demoed live with opposite-order acquisition that would otherwise hang). Detection: `jstack` prints "Found one Java-level deadlock".

**wait/notify (classroom classic):** `wait()` releases the monitor and parks; `notify()`/`notifyAll()` wakes waiter(s); both require holding the monitor (`IllegalMonitorStateException` otherwise). **Always `wait()` in a `while` loop** — spurious wakeups + recheck the condition. Modern code reaches for `BlockingQueue`/`CountDownLatch` instead, but the mechanics remain interview canon.

## 5.3 — Executors & thread pools

**Why pools:** platform threads are expensive (~1MB stack, OS syscalls); per-task creation doesn't scale and unbounded threads can OOM the process. A pool = N long-lived workers pulling from a task queue — you submit *tasks*, not threads.

**API:** `ExecutorService.submit(Runnable | Callable<T>)` → `Future<T>`. `Callable<T>` returns a value and may throw checked (Runnable can't). `future.get()` blocks (prefer the timeout overload; task exceptions surface as `ExecutionException` wrapping the cause). `invokeAll` = batch + wait all. Since Java 19 ExecutorService is `AutoCloseable` — `try (var pool = ...)` awaits completion; the pre-19 idiom (`shutdown()` → `awaitTermination(timeout)` → `shutdownNow()`) is still interview material.

**The pool zoo:** `newFixedThreadPool(n)` (CPU-bound: n ≈ cores); `newCachedThreadPool` (bursty, **unbounded — dangerous under sustained load**); `newSingleThreadExecutor` (serial, ordered); `newScheduledThreadPool` (delays/periodic); `newVirtualThreadPerTaskExecutor` (5.5). Production advice: construct `ThreadPoolExecutor` directly — core/max size, **bounded queue**, rejection policy — so overload fails fast instead of OOMing. Sizing intuition: CPU-bound → cores; I/O-bound → cores × (1 + wait/compute) — or virtual threads.

**BlockingQueue** (`ArrayBlockingQueue` bounded, `LinkedBlockingQueue`): `put` blocks when full, `take` blocks when empty — producer/consumer with all wait/notify machinery hidden. The demo showed **backpressure**: a bounded queue stalls the fast producer to the consumer's pace. This is the seed of Kafka/RabbitMQ thinking. Related coordination tools worth knowing by name: `CountDownLatch` (wait for N events), `Semaphore` (N permits), `CyclicBarrier` (rendezvous point).

**Concurrent collections:** `ConcurrentHashMap` (lock-striped/CAS, atomic compound ops `putIfAbsent`/`compute`/`merge` — never lock a plain HashMap when this exists), `CopyOnWriteArrayList` (reads free, writes copy — read-heavy listener lists), fail-*safe* weakly-consistent iterators vs 3.2's fail-fast.

## 5.4 — CompletableFuture

**Purpose:** `Future` can only block on `get()`; CompletableFuture composes async *pipelines* — stages that run when their inputs complete. It's streams (4.2) over *time* instead of data, and the vocabulary maps exactly:

| sync/streams | CompletableFuture | meaning |
|---|---|---|
| `map` | `thenApply(fn)` | transform result |
| `flatMap` | `thenCompose(fn→CF)` | chain dependent async call (flattens CF<CF<T>>) |
| zip | `thenCombine(cf, biFn)` | join two *independent* futures |
| `forEach` | `thenAccept`/`thenRun` | consume / just-do-next |
| `catch` | `exceptionally(fn)` | recover with fallback |
| `finally` | `whenComplete` | peek at (value, ex), unchanged |
| both branches | `handle((v, ex) → r)` | transform either outcome |

**Measured payoff:** two independent 200ms calls with `thenCombine` → total 200ms, not 400. Fan-out to N: `allOf(cfs...)` (returns `CF<Void>` — collect from the originals), `anyOf` (first wins).

**Error flow:** an exception *skips* downstream `thenApply` stages until a handler — exactly like throw-to-catch. `orTimeout(t, unit)` / `completeOnTimeout(fallback, t, unit)` are the guard rails services need. Exceptions surface wrapped (`CompletionException`/`ExecutionException`) — check `getCause()`.

**Threading fine print:** `supplyAsync` without an executor runs on `ForkJoinPool.commonPool()` — shared JVM-wide, sized for CPU work; **don't block it with I/O** (starvation). Pass an explicit pool (as demoed) or use virtual threads. `*Async` stage variants hop threads; plain variants may run on the completing thread. `join()` vs `get()`: same block, unchecked vs checked exception — join only at the pipeline's edge.

## 5.5 — Virtual threads (Java 21)

**The problem they solve:** platform threads = OS threads: ~1MB stacks, expensive, thousands max — while server workloads are mostly *waiting* on I/O. Pools, async pipelines, and reactive frameworks are all workarounds for thread scarcity.

**The mechanism:** virtual threads are JVM-managed continuations with KB-scale, growable stacks — *millions* are fine. Each runs mounted on a **carrier** (platform thread from a small ForkJoin pool, ~1/core). When a virtual thread blocks (sleep, socket, lock), the JVM **unmounts** it and the carrier picks up another virtual thread; on unblock it remounts. Blocking becomes nearly free — *cheap blocking is the entire feature*.

**Measured:** 10,000 tasks × 100ms blocking each (≈1,000 thread-seconds of waiting) completed in **136 ms wall time**. The same with platform threads would need 10GB of stacks.

**Creating:** `Thread.ofVirtual().start(r)`, `Executors.newVirtualThreadPerTaskExecutor()` (one fresh virtual thread per task). **Never pool virtual threads** — they're disposable; pooling reintroduces the scarcity they abolish. Rate-limit with a `Semaphore` instead.

**Limits & gotchas:**
- **CPU-bound work gains nothing** — cores are still the limit; keep fixed platform pools for computation.
- **Pinning:** blocking inside a `synchronized` block (or native frame) prevents unmounting — the carrier is stuck. Hot blocking paths → `ReentrantLock`. (Java 24 largely fixed the synchronized case; the concept remains interview material.) Diagnose with `-Djdk.tracePinnedThreads`.
- ThreadLocals work but millions of threads × ThreadLocal = memory; scoped values are the successor.

**The payoff in style:** dependent blocking calls written sequentially (fetch user, then orders — just block) get async-level scalability without CompletableFuture gymnastics. Spring Boot 3.2+: `spring.threads.virtual.enabled=true` puts every request on a virtual thread — we'll use it in track 02. **Structured concurrency** (`StructuredTaskScope`, preview): treat forked subtasks as one unit — join all, failure cancels siblings; name-drop it as "where this is heading."
