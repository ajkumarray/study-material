<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · modern java](../phase-4-modern-java/NOTES.md) | [Phase 6 · tooling ➡](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Concurrency: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly. Concurrency is where senior interviews are won.

## 5.1 — Threads & memory model

**Q: `start()` vs `run()`?** ⭐
`start()` asks the scheduler to run the thread *concurrently* (and may only be called once — second call: `IllegalThreadStateException`). Calling `run()` directly is an ordinary method call on the *current* thread — no concurrency at all. Evergreen trick question.

**Q: What is a race condition? Give the canonical example.** ⭐⭐
Correctness depending on thread interleaving. Canonical: two threads doing `count++` (read→add→write, not atomic) — both read 41, both write 42, one update lost. Measured in the lesson: 200,000 attempted increments → 123,138. Key phrase: **check-then-act and read-modify-write sequences are non-atomic**.

**Q: What is the visibility problem? What does `volatile` actually do?** ⭐⭐
Without synchronization, one thread's write may never become visible to another (caches, registers, JIT hoisting) — an infinite `while (!stop)` loop is the classic symptom. `volatile` guarantees a write is visible to subsequent reads (a happens-before edge) and prevents reordering around it. **It does NOT provide atomicity**: `volatile int count; count++` still races. volatile = visibility; locks/atomics = visibility *and* atomicity.
*Follow-up: name happens-before edges — volatile write→read, unlock→lock, start(), join().*

**Q: How do you stop a thread?** ⭐
Cooperatively: `interrupt()` sets a flag; blocking methods throw `InterruptedException`; loops check `isInterrupted()`. On catch: exit cleanly or restore the flag (`Thread.currentThread().interrupt()`) — never swallow. `Thread.stop()` was removed: killing a thread mid-invariant corrupts shared state.

**Q: Thread states?**
NEW → RUNNABLE ⇄ BLOCKED (monitor wait) | WAITING (`wait`/`join`) | TIMED_WAITING (`sleep`) → TERMINATED. Precision on BLOCKED-vs-WAITING reads as real debugging experience (they're distinct in thread dumps).

**Q: What is a daemon thread?**
A thread that doesn't keep the JVM alive — JVM exits when only daemons remain (GC is one). `setDaemon(true)` before `start()`.

## 5.2 — synchronized, locks, atomics

**Q: How does `synchronized` work?** ⭐⭐
Every object has a monitor; synchronized acquires it on entry, releases on exit (exceptions included); contenders block. Instance method → locks `this`; static method → locks the `Class` object; block → locks the given object. Reentrant (same thread may re-enter). Also establishes happens-before → visibility included.
*Follow-up: "Why lock a private final Object instead of `this`?" — outsiders can synchronize on your object and deadlock/interfere; a private lock encapsulates the monitor (2.2 thinking).*

**Q: `synchronized` vs `ReentrantLock`?** ⭐
ReentrantLock adds: `tryLock()` (+timeout) — bail instead of waiting forever; `lockInterruptibly()`; optional fairness; multiple `Condition` objects. Costs: manual `unlock()` in `finally` — forget it once and you own a permanent deadlock. Default to synchronized for simplicity; upgrade for the specific superpowers. (Virtual-thread era wrinkle: hot blocking paths prefer ReentrantLock — pinning, 5.5.)

**Q: How does AtomicInteger work without locks?** ⭐
**CAS** — compare-and-swap, a hardware instruction: "set to new value only if it still equals my expected value; else fail." Callers retry in a loop. Optimistic (assumes low contention), no thread parking. Under *heavy* write contention the retry storm hurts — `LongAdder` (striped cells, summed on read) wins there. Senior differentiator.

**Q: What is deadlock? Prevention?** ⭐⭐
A cycle of threads each holding a lock the next needs — all wait forever. Four Coffman conditions (mutual exclusion, hold-and-wait, no preemption, circular wait) — break one: **global lock ordering** (kills circular wait — the standard answer), `tryLock` with backoff, or single coarse lock. Diagnosis: `jstack` literally prints "Found one Java-level deadlock".
*Follow-ups: livelock (threads actively yielding to each other, no progress — two people side-stepping in a corridor); starvation (a thread perpetually losing the scheduling/lock race).*

**Q: Rules of wait/notify?** ⭐
Must hold the monitor to call them (else `IllegalMonitorStateException`); `wait()` releases the monitor and parks (unlike `sleep`, which holds everything); `notify()` wakes one waiter, `notifyAll()` all; **always wait in a while loop** — spurious wakeups exist and the condition must be rechecked. Modern replacements: `BlockingQueue`, `CountDownLatch`, `Condition`.
*Classic follow-up: "sleep vs wait?" — sleep: static, keeps locks, time-based. wait: instance method, releases the monitor, needs notify (or timeout).*

## 5.3 — Executors & thread pools

**Q: Why thread pools?** ⭐
Thread creation is expensive (~1MB stack, syscalls) and unbounded threads are a self-DoS. A pool reuses N workers over a task queue: amortized creation, bounded resources, decouples task submission from execution policy.

**Q: `Runnable` vs `Callable`?**
`Runnable`: `void run()`, no checked throws. `Callable<T>`: `T call() throws Exception` — returns a value, surfaces exceptions through `Future.get()` (wrapped in `ExecutionException`).

**Q: What's dangerous about `Future.get()`?**
It blocks forever by default — use the timeout overload (`TimeoutException`) or you leak blocked threads when a task hangs. Also: it rethrows task failures as `ExecutionException` — inspect `getCause()`.

**Q: Fixed vs cached pool? How do you size one?** ⭐
Fixed: N workers, unbounded queue — predictable; CPU-bound sweet spot (n ≈ cores). Cached: grows per demand, reuses idle, **unbounded thread count** — fine for bursts, an OOM risk under sustained load. Sizing: CPU-bound → cores; I/O-bound → cores × (1 + wait/compute) — or skip the arithmetic with virtual threads. Production: raw `ThreadPoolExecutor` with a **bounded queue + rejection policy** — overload should fail fast, not OOM.

**Q: How do you shut down an ExecutorService properly?** ⭐
Classic idiom: `shutdown()` (stop intake, drain queue) → `awaitTermination(timeout)` → if false, `shutdownNow()` (interrupt workers, return undone tasks). Java 19+: it's AutoCloseable — try-with-resources does shutdown-and-wait.

**Q: What is a BlockingQueue? Why bounded?** ⭐
Producer/consumer conduit: `put` blocks when full, `take` when empty — wait/notify machinery hidden. Bounded queues create **backpressure**: a fast producer is stalled to the consumer's pace instead of growing an infinite buffer (demoed live). The concept behind every message broker.
*Name-drops that score: CountDownLatch (await N events), Semaphore (N permits), CyclicBarrier (rendezvous), ConcurrentHashMap's atomic compute/merge, CopyOnWriteArrayList for read-heavy listeners.*

## 5.4 — CompletableFuture

**Q: Future vs CompletableFuture?** ⭐
Future: submit, then *block* on get — no composition. CompletableFuture: declarative pipelines — `thenApply`/`thenCompose`/`thenCombine`/`allOf` — stages fire on completion, no blocking between them; plus explicit completion (`complete()`) and async error handling.

**Q: `thenApply` vs `thenCompose`?** ⭐⭐
`thenApply` = map (sync transform of the result). `thenCompose` = flatMap — the function itself returns a CompletableFuture; compose flattens `CF<CF<T>>` → `CF<T>`. Dependent async calls (profile → orders) are compose; pure transforms are apply. Answering "it's map vs flatMap, same as Optional/Streams" signals the pattern generalizes for you.

**Q: `thenCombine` vs `thenCompose`?**
Combine joins two **independent** futures with a BiFunction (parallel fan-out: price + tax → total, measured 200ms not 400). Compose chains **dependent** calls (needs the first result to start the second).

**Q: How do errors flow through a CompletableFuture pipeline?** ⭐
An exception skips every downstream `thenApply/thenAccept` until a handler — `exceptionally` (recover with fallback), `handle((v, ex) → r)` (transform both cases), `whenComplete` (observe, unchanged) — precisely throw-to-catch semantics, async. Guard rails: `orTimeout` / `completeOnTimeout`. Failures surface wrapped — check `getCause()`.

**Q: What thread runs `supplyAsync` if you don't pass an executor? Why does it matter?** ⭐ *senior probe*
`ForkJoinPool.commonPool()` — one shared, CPU-sized pool for the whole JVM. Blocking I/O on it starves everything else using it (parallel streams included). Pass an explicit executor for I/O, or use virtual threads. Also know: non-`*Async` stages may execute on the *completing* thread — surprise work on an I/O callback thread.

## 5.5 — Virtual threads

**Q: What are virtual threads and what problem do they solve?** ⭐⭐ *the modern-Java question*
JVM-managed threads with tiny growable stacks (KBs), scheduled onto a small pool of **carrier** (platform) threads. When one blocks, the JVM *unmounts* it — the carrier runs another. Millions are affordable, so **blocking becomes cheap**, dissolving the thread-scarcity problem that pools, async chains, and reactive frameworks exist to work around. Measured: 10,000 × 100ms-blocking tasks in 136 ms wall time.

**Q: When do virtual threads NOT help?** ⭐
CPU-bound work — cores remain the limit (keep fixed platform pools). And they're not "faster threads" — same execution speed; the win is *concurrency of blocking tasks* (throughput, not latency).

**Q: What is pinning?** ⭐ *the depth probe*
A virtual thread that blocks while inside a `synchronized` block (or native call) can't unmount — its carrier is stuck, eroding the scalability win. Mitigations: `ReentrantLock` on hot blocking paths; diagnose with `-Djdk.tracePinnedThreads`. (Java 24 largely fixed the synchronized case — knowing *that* is bonus credit.)

**Q: Should you pool virtual threads?**
No — they're disposable by design: one task, one fresh virtual thread (`newVirtualThreadPerTaskExecutor`). Pooling reintroduces scarcity. To limit concurrency (e.g., max 10 DB connections), use a `Semaphore`, not a pool.

**Q: How do virtual threads change how you write services?**
Thread-per-request returns: plain sequential blocking code (fetch user; fetch orders; return) scales like async without CompletableFuture ceremony. Spring Boot 3.2+: `spring.threads.virtual.enabled=true`. Structured concurrency (`StructuredTaskScope`, preview) is the emerging companion: forked subtasks joined as a unit, failure cancels siblings.
