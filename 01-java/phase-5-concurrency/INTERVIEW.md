<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · modern java](../phase-4-modern-java/NOTES.md) | [Phase 6 · tooling ➡](../phase-6-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Concurrency: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly. Concurrency is where senior interviews are won.

## 5.1 — Threads & Memory Model

**Q: `start()` vs `run()` — what's the actual difference?** ⭐

`start()` registers the thread with the scheduler, which then runs its task **concurrently**, on a genuinely new call stack — and it may only be called once per `Thread` instance (a second call throws `IllegalThreadStateException`). Calling `run()` directly, by contrast, is just an ordinary synchronous method invocation executed on the *current* thread — there is no concurrency at all, even though the code inside runs identically either way. This is an evergreen trick question specifically because both calls compile cleanly and "work," but only one actually achieves what the interviewer is asking about.

**Q: What is a race condition? Give the canonical example.** ⭐⭐

A race condition is a bug where program correctness depends on the specific, unpredictable interleaving of operations across threads. The canonical example is two threads both executing `count++` on a shared `int` field: `count++` is actually three separate machine-level steps — read, add one, write back — not one atomic operation. Both threads can read the same value before either writes back, causing one of the two intended increments to be silently lost. Measured directly in the lesson: 200,000 total increment attempts across two threads produced a final count of **123,138**, not 200,000. The key phrase to use in an answer: check-then-act and read-modify-write sequences are non-atomic unless something explicitly makes them so.

**Q: What is the visibility problem, and what does `volatile` actually guarantee?** ⭐⭐

Without any synchronization, a write made by one thread to a shared variable is not guaranteed to ever become visible to another thread reading that same variable — CPU core caches, registers, and JIT compiler optimizations (like hoisting a read out of a loop because the compiler believes the value "never changes" in that loop body) can all cause a reading thread to observe stale data indefinitely. The textbook symptom is a `while (!stop)` polling loop on a non-`volatile` boolean that, in principle, never terminates. `volatile` guarantees that a write is immediately visible to any subsequent read of that same field (it establishes a happens-before edge) and prevents certain instruction reordering around it. **Critically, `volatile` does not provide atomicity** — `volatile int count; count++;` still has exactly the same lost-update race as a non-volatile `int`, because incrementing is still a read-modify-write sequence, and visibility alone doesn't make that sequence atomic. Visibility and atomicity are genuinely two separate problems requiring two separate fixes.

*Follow-up: "Name the happens-before edges the Java Memory Model actually guarantees."* A `volatile` write happens-before a later `volatile` read of the same field; releasing (unlocking) a monitor happens-before a later acquisition (locking) of that same monitor; `Thread.start()` happens-before anything the started thread does; and the completion of a thread's run happens-before a `join()` on it returning.

**Q: How do you stop a running thread cleanly?** ⭐

Cooperatively — there is no forcible, safe way to kill a thread in modern Java. `interrupt()` sets an internal flag on the target thread; blocking methods (`Thread.sleep`, `Object.wait`, `Thread.join`) respond to that flag by immediately throwing `InterruptedException`, and non-blocking loops are expected to periodically check `Thread.isInterrupted()` themselves. Upon catching `InterruptedException`, the thread should either clean up and genuinely exit, or — if it can't exit immediately at that point — restore the interrupt flag with `Thread.currentThread().interrupt()` so code further up the call stack still knows cancellation was requested. Never silently swallow it. `Thread.stop()`, which forcibly terminated a thread, was deprecated and effectively removed precisely because killing a thread mid-update of some shared invariant leaves that shared state permanently, silently corrupted with no way to detect or repair it.

**Q: Describe the thread lifecycle states.**

`NEW` (created, not yet started) → `RUNNABLE` (eligible to run, actually running or waiting for CPU time) ⇄ `BLOCKED` (waiting to acquire a monitor another thread holds) | `WAITING` (parked indefinitely via `wait()`/`join()` with no timeout) | `TIMED_WAITING` (parked with a timeout, e.g. `sleep()` or a timed `wait()`) → `TERMINATED`. Being precise about the distinction between `BLOCKED` and `WAITING` specifically signals real thread-dump-reading experience in production debugging, since they mean genuinely different things when diagnosing contention versus legitimate coordination.

**Q: What is a daemon thread?**

A thread that doesn't by itself keep the JVM process alive — once every remaining live thread in the process is a daemon thread, the JVM exits immediately, even mid-execution of those daemon threads. The garbage collector itself typically runs on a daemon thread. `setDaemon(true)` must be called **before** `start()` — calling it afterward has no effect and typically throws `IllegalThreadStateException`.

## 5.2 — `synchronized`, Locks, Atomics

**Q: How does `synchronized` work under the hood?** ⭐⭐

Every Java object carries an associated monitor (intrinsic lock). Entering a `synchronized` method or block acquires the relevant monitor; leaving it — whether normally or via an exception propagating out — releases it automatically. Any other thread attempting to enter a section synchronized on the same monitor simply blocks until it becomes free. An instance method locks `this`; a `static synchronized` method locks the enclosing `Class` object (one shared monitor across every instance); a `synchronized(obj)` block locks whatever object `obj` refers to. It's **reentrant** — a thread already holding a given monitor can freely enter another `synchronized` section guarded by that same monitor without deadlocking against itself. Because acquiring/releasing a monitor also establishes a Java Memory Model happens-before edge, `synchronized` gives you visibility "for free" alongside mutual exclusion.

*Follow-up: "Why lock a private final Object instead of `this`?"* Because locking `this` exposes your object's monitor to any external code holding a reference to it — an unrelated piece of code could `synchronized(yourObject)` too, either accidentally causing unexpected contention or, in adversarial cases, deliberately interfering. A dedicated private lock object fully encapsulates the monitor, the same encapsulation thinking behind private fields in general (Phase 2).

**Q: `synchronized` vs. `ReentrantLock` — when do you reach for the latter?** ⭐

`ReentrantLock` adds capabilities `synchronized` structurally can't offer: `tryLock()` (optionally with a timeout) to attempt acquisition without blocking indefinitely; `lockInterruptibly()`, so a thread stuck waiting for the lock can itself still respond to interruption; an optional fairness policy; and support for multiple independent `Condition` objects per lock (richer than the single implicit condition a monitor's `wait`/`notify` gives you). The cost is discipline: `unlock()` must be called manually, always inside a `finally` block — forget it once under an exceptional path and you've created a permanent deadlock for anyone else waiting on that lock. Default to `synchronized` for its simplicity and automatic release; reach for `ReentrantLock` specifically when you need one of those superpowers. (One virtual-thread-era wrinkle worth mentioning: hot blocking code paths generally prefer `ReentrantLock` over `synchronized` to avoid carrier-thread pinning — 5.5.)

**Q: How does `AtomicInteger` achieve thread safety without locks?** ⭐

Via **CAS (compare-and-swap)**, a single CPU-level instruction meaning: "write this new value only if the variable's current value still equals what I expect it to be; otherwise, do nothing and report failure." Code using CAS directly loops: read the current value, compute the desired new value, attempt the CAS, and retry from the read step if the CAS failed because some other thread updated the value in between. This is **optimistic** concurrency — it assumes contention is generally low, and importantly it never blocks or parks a thread, which is why for a single frequently-updated variable it typically significantly outperforms lock-based approaches. Under genuinely *heavy* write contention, though, the constant CAS-failure-and-retry cycle itself becomes a bottleneck — `LongAdder`, which internally stripes the counter across multiple independently-updatable cells and only sums them on read, wins in that specific scenario. Knowing to reach for `LongAdder` under heavy contention, rather than assuming `AtomicLong` always wins, is a genuine senior-level differentiator.

**Q: What is deadlock, and how do you prevent it?** ⭐⭐

A cycle of two or more threads, each holding a lock that some other thread in the cycle needs next, so every thread in the cycle waits forever with no progress possible. The classical formal description is the four Coffman conditions (mutual exclusion, hold-and-wait, no preemption, circular wait) — breaking any single one prevents deadlock, and in practice the standard, most reliable fix is breaking **circular wait** via a **global lock ordering**: every thread in the system agrees to always acquire a given set of shared locks in the same fixed order, which makes a cycle structurally impossible to form. The alternative demonstrated directly in the lesson is `tryLock()` with backoff — a thread that can't acquire every lock it needs releases whatever it's already holding and retries from scratch, rather than holding one lock while blocking indefinitely on another; this correctly avoids a hang even when two threads deliberately acquire the same two locks in opposite order. In a running JVM, `jstack <pid>` reliably diagnoses an actual deadlock, printing "Found one Java-level deadlock" along with the specific thread/lock cycle.

*Follow-up: "How do livelock and starvation differ from deadlock?"* Livelock is threads actively, repeatedly responding to each other (e.g. both continually backing off and retrying in a way that never actually resolves) without making real progress — like two people in a corridor each repeatedly stepping aside in the same direction as the other. Starvation is a thread perpetually losing out on scheduling or lock acquisition to other threads, so it makes no progress even though the system as a whole isn't stuck.

**Q: What are the rules for `wait()`/`notify()`?** ⭐

Both must be called from inside a `synchronized` block/method holding the relevant monitor — calling either without holding the monitor throws `IllegalMonitorStateException`. `wait()` **releases the monitor** and parks the calling thread until notified (in sharp contrast to `Thread.sleep()`, which holds every lock the thread has while sleeping). `notify()` wakes exactly one arbitrary waiting thread; `notifyAll()` wakes every waiting thread. **Always call `wait()` inside a `while` loop rechecking the actual condition**, never a plain `if` — the JVM spec explicitly permits "spurious wakeups" (a thread waking with no corresponding `notify` at all), so the condition must always be re-verified after waking rather than assumed true just because `wait()` returned. Modern production code typically reaches for `BlockingQueue`, `CountDownLatch`, or an explicit `Condition` instead of hand-rolling this — but interviews still routinely test the raw mechanics, since those higher-level tools are themselves built directly on top of it.

*Classic follow-up: "sleep() vs wait() — what's the difference?"* `sleep()` is a `static Thread` method: it's time-based, and it holds onto every lock the thread currently has the entire time it sleeps. `wait()` is an instance method on `Object`: it releases the monitor it's called under, and requires either a `notify`/`notifyAll` or a timeout to resume.

## 5.3 — Executors & Thread Pools

**Q: Why use a thread pool instead of just spawning `new Thread()` per task?** ⭐

Because platform thread creation is genuinely expensive — roughly 1MB of stack reserved per thread, plus real OS-level syscalls to create it — and letting the number of live threads grow unbounded as load increases is effectively a self-inflicted denial-of-service risk against your own process. A thread pool amortizes creation cost across many tasks by reusing N long-lived worker threads pulling from a shared task queue, bounding total resource usage, and cleanly decoupling the *act of submitting work* from the *policy governing how that work actually executes*.

**Q: `Runnable` vs. `Callable<T>` — what's the difference?**

`Runnable` has a single `void run()` method that cannot throw a checked exception. `Callable<T>` has `T call() throws Exception` — it returns an actual value, and any exception it throws surfaces to the caller through `Future.get()`, wrapped in `ExecutionException`.

**Q: What's genuinely dangerous about calling `Future.get()`?**

By default it **blocks indefinitely** — if the underlying task hangs or never completes, `get()` never returns, and the calling thread is effectively leaked, permanently blocked. Production code should almost always use the timeout overload, `get(timeout, unit)`, which throws `TimeoutException` instead of blocking forever. Separately, `get()` rethrows any exception the task itself threw, wrapped in `ExecutionException` — always inspect `getCause()` to get at the actual underlying failure rather than treating `ExecutionException` itself as the real error.

**Q: Fixed pool vs. cached pool — and how do you size either?** ⭐

`newFixedThreadPool(n)` maintains exactly N worker threads with an unbounded task queue behind them — predictable resource usage, and the natural sweet spot for CPU-bound work where `n` is chosen around the number of available cores. `newCachedThreadPool()` grows its thread count on demand and reuses idle threads, which handles bursty short-lived work well, but its thread count is **effectively unbounded** — under sustained heavy load it can spawn enough threads to exhaust memory or OS thread limits entirely, a genuine production risk. Sizing heuristic: CPU-bound work wants roughly `cores` threads; I/O-bound work benefits from more, roughly `cores × (1 + wait_time/compute_time)` — or you can skip that arithmetic altogether by switching to virtual threads (5.5). For serious production use, prefer constructing `ThreadPoolExecutor` directly, with a deliberately **bounded queue and an explicit rejection policy** — overload should fail fast and visibly, not silently grow an unbounded queue until the process runs out of memory.

**Q: How do you shut down an `ExecutorService` correctly?** ⭐

The classic, pre-Java-19 idiom: call `shutdown()` (stops accepting new tasks, but lets already-queued/running tasks finish), then `awaitTermination(timeout, unit)` to wait for that to actually happen; if that times out and returns `false`, call `shutdownNow()`, which attempts to interrupt still-running workers and returns the list of tasks that never started. Since Java 19, `ExecutorService` implements `AutoCloseable`, so `try (var pool = Executors.newFixedThreadPool(3)) { ... }` performs an equivalent shutdown-and-await automatically when the block exits — but the classic manual idiom is still commonly asked about and still needed for more nuanced shutdown scenarios (e.g., you want to explicitly act on the tasks `shutdownNow()` returns).

**Q: What is a `BlockingQueue`, and why does bounding its capacity matter?** ⭐

It's a thread-safe producer/consumer conduit: `put()` blocks the calling thread when the queue is full, `take()` blocks when it's empty — all the underlying `wait`/`notify` coordination from 5.2 is implemented internally and completely hidden behind a simple queue API. **Bounding** the queue's capacity (e.g. `new ArrayBlockingQueue<>(2)`) creates genuine **backpressure**: a producer that's running faster than its consumer gets stalled to the consumer's actual pace once the bounded buffer fills up, rather than an unbounded queue letting the producer race arbitrarily far ahead and grow memory usage without limit. This exact idea, generalized to distributed systems, is the conceptual root of how message brokers like Kafka and RabbitMQ behave under load.

*Name-drops worth having ready*: `CountDownLatch` (block until N independent events have all happened), `Semaphore` (limit concurrent access to N permits, e.g. capping concurrent outbound calls), `CyclicBarrier` (a reusable rendezvous point where N threads all wait for each other), `ConcurrentHashMap`'s atomic `compute`/`merge` operations, and `CopyOnWriteArrayList` for read-heavy, rarely-mutated structures like listener lists.

## 5.4 — CompletableFuture

**Q: `Future` vs. `CompletableFuture` — what's the actual upgrade?** ⭐

A plain `Future` gives you exactly one operation once you have it: block on `get()`. There's no way to attach further work, combine it with another future, or react to its eventual failure without blocking. `CompletableFuture` adds fully declarative pipelines — `thenApply`, `thenCompose`, `thenCombine`, `allOf`, error-handling stages like `exceptionally`/`handle` — where each stage fires automatically once its inputs are ready, with no blocking required between stages. It also supports explicit manual completion (`complete(value)`) and structured, composable async error handling that a plain `Future` has no facility for at all.

**Q: `thenApply` vs. `thenCompose` — what's the difference, and how does it relate to concepts from earlier phases?** ⭐⭐

`thenApply` is the async equivalent of `map` — a synchronous transformation applied to the eventual result. `thenCompose` is the async equivalent of `flatMap` — used specifically when the function you're chaining itself returns a `CompletableFuture`; `thenCompose` flattens the resulting `CompletableFuture<CompletableFuture<T>>` down to a plain `CompletableFuture<T>`. The practical rule: a **dependent** async call — one that genuinely needs the previous stage's result before it can even start (fetch a user's profile, *then* fetch their orders using that profile) — is `thenCompose`; a pure, purely synchronous transformation of an already-available result is `thenApply`. Explicitly stating "this is the same map-vs-flatMap distinction as `Optional` and `Stream`" is a strong signal in an interview that the pattern has genuinely generalized for you, rather than being three separately memorized API quirks.

**Q: `thenCombine` vs. `thenCompose` — when do you use each?**

`thenCombine` joins two futures that are **independent** of each other — both were started separately and run concurrently, and `thenCombine`'s `BiFunction` fires only once *both* have completed (a genuine parallel fan-out: fetching a price and a tax rate simultaneously, then combining them once both return — measured in the lesson at ~200ms total for two 200ms calls, not 400ms). `thenCompose` chains futures that are **dependent** — the second call genuinely can't even begin until the first one's result is available.

**Q: How do errors actually flow through a `CompletableFuture` pipeline?** ⭐

An exception thrown inside any stage **skips every subsequent `thenApply`/`thenAccept`/`thenCompose` stage downstream**, propagating forward until it reaches a stage that's explicitly designed to handle failure: `exceptionally(fn)` recovers with a fallback value, but only runs on the failure path; `handle((value, ex) -> result)` runs on *either* outcome, letting you unify success and failure into one consistent result; `whenComplete((value, ex) -> ...)` observes both outcomes without altering the eventual result at all — the async equivalent of a `finally` block. This is precisely the same skip-forward-to-a-handler semantics as a synchronous exception propagating from a `throw` up to a matching `catch`. `orTimeout(duration, unit)` and `completeOnTimeout(fallback, duration, unit)` are the standard guard rails any real service pipeline needs against a dependency that never responds at all. Failures generally surface wrapped — `CompletionException` if observed inside another stage, `ExecutionException` if observed via a blocking `.get()` — so always check `.getCause()` for the real underlying exception.

**Q: If you don't pass an executor to `supplyAsync`, what thread pool actually runs it — and why does that matter?** ⭐ *senior probe*

It runs on `ForkJoinPool.commonPool()` — a single pool shared JVM-wide, sized by default to the number of available CPU cores, and also used internally by parallel streams. Running genuinely blocking I/O work on that shared pool can **starve** every other unrelated piece of code depending on it — including totally unrelated parallel stream operations elsewhere in the same JVM process — since the pool has a fixed, small number of worker threads and blocking ties one up indefinitely. The fix is always passing an explicit, dedicated executor for I/O-bound async work, or switching to virtual threads (5.5) and largely sidestepping the pool-sizing question entirely. It's also worth knowing that the non-`*Async` stage variants (`thenApply` vs. `thenApplyAsync`) may execute directly on whichever thread happens to be the one that completes the previous stage, rather than always hopping onto the pool — occasionally surprising when that completing thread turns out to be something like an I/O driver's own callback thread.

## 5.5 — Virtual Threads

**Q: What are virtual threads, and what specific problem do they solve?** ⭐⭐ *the modern-Java concurrency question*

Virtual threads are JVM-managed threads with tiny, growable KB-scale stacks (versus roughly 1MB for a platform/OS thread), scheduled dynamically onto a small internal pool of **carrier** platform threads. When a virtual thread performs a blocking operation, the JVM **unmounts** it from its carrier, immediately freeing that carrier to go run a different, ready virtual thread; when the blocking operation completes, the original virtual thread gets remounted onto some available carrier to resume. Because millions of virtual threads are practically affordable — unlike platform threads, which are capped in the low thousands before resource exhaustion becomes a real concern — **blocking becomes cheap**, which dissolves the core thread-scarcity problem that thread pools, async pipelines (`CompletableFuture`), and fully reactive frameworks all exist, at bottom, to work around. Measured directly: 10,000 tasks each blocking for 100ms completed in about 136ms of wall-clock time.

**Q: When do virtual threads *not* help?** ⭐

For **CPU-bound** work — actual computation, not waiting — virtual threads provide no benefit at all, since the number of available CPU cores remains the hard physical limit regardless of how many virtual threads exist; a fixed-size platform thread pool sized to the core count is still the right tool there. It's also worth stating clearly that virtual threads aren't "faster threads" in any sense — the execution speed of any individual piece of code is unchanged; the entire win is in *concurrency of blocking work* (how many tasks can be in flight simultaneously), which is a throughput improvement, not a latency improvement for any single task.

**Q: What is pinning?** ⭐ *the depth probe*

Pinning is when a virtual thread that's blocking cannot actually be unmounted from its carrier — historically, this happened specifically when the blocking occurred while the virtual thread was inside a `synchronized` block (or certain native call frames). The carrier thread stays stuck running that one blocked virtual thread instead of being freed to run others, which erodes the scalability benefit for that specific code path. The mitigation on hot blocking paths is to use `ReentrantLock` instead of `synchronized`, since lock-based blocking (unlike monitor-based blocking) doesn't cause pinning. It can be diagnosed at runtime with the JVM flag `-Djdk.tracePinnedThreads`. Worth noting for extra credit: Java 24 largely eliminated the `synchronized` case of pinning specifically — knowing that this history exists, and roughly when it changed, tends to read as genuine up-to-date familiarity.

**Q: Should you pool virtual threads the way you'd pool platform threads?**

No — they're explicitly designed to be cheap and disposable: one task gets one freshly created virtual thread (`Executors.newVirtualThreadPerTaskExecutor()`), used once, then discarded. Attempting to pool and reuse virtual threads reintroduces exactly the scarcity constraint the whole feature was built to eliminate, with no upside. If the actual goal is limiting *concurrency* — e.g. capping outbound calls to a rate-limited downstream service at 10 concurrent requests — the correct tool is a `Semaphore` gating access, not a fixed-size thread pool.

**Q: How do virtual threads change the way you write service code?**

They bring back plain, sequential, thread-per-request blocking code as a genuinely scalable style: `fetchUser(id); fetchOrders(id);` written as two straightforward, sequential blocking calls now scales roughly like an asynchronously composed pipeline would, without any of the `CompletableFuture` chaining ceremony needed to achieve that scalability under platform threads. This is precisely why Spring Boot 3.2+ offers a single configuration flag — `spring.threads.virtual.enabled=true` — to run every incoming web request on its own dedicated virtual thread. Looking forward, **structured concurrency** (`StructuredTaskScope`, still a preview API as of Java 21) is the natural companion mechanism: it treats a set of forked subtasks as a single logical unit of work — fork several children, join them together, and automatically cancel the remaining siblings if any one of them fails, avoiding orphaned unmanaged tasks. It's worth name-dropping in an interview as "where `java.util.concurrent` is heading," even ahead of it being finalized.
