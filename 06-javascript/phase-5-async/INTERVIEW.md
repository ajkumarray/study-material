<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · arrays](../phase-4-arrays/NOTES.md) | [Phase 6 · modern ➡](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 5 — Asynchronous JavaScript: Interview Q&A

⭐ = asked constantly. This phase dominates frontend interviews.

**Q: Is JavaScript single-threaded? How does it do async then?** ⭐⭐
Yes — one call stack, one thing at a time. Concurrency comes from the **event loop**: long operations (timers, network, I/O) are handed off to the runtime/OS; when they finish, their callbacks are queued and the event loop runs them when the stack is empty. So JS never blocks on I/O despite being single-threaded.

**Q: Explain the event loop, macrotasks, and microtasks.** ⭐⭐
The loop runs sync code to completion, then drains the **entire microtask queue** (promise callbacks, `await` continuations), then runs **one macrotask** (`setTimeout`, I/O), then drains microtasks again, and repeats. Microtasks always run before the next macrotask.

**Q: What does this print — `console.log(1); setTimeout(()=>console.log(2),0); Promise.resolve().then(()=>console.log(3)); console.log(4);`?** ⭐⭐
`1, 4, 3, 2`. Sync first (1, 4), then the microtask (3, promise), then the macrotask (2, timeout) — even with `0ms`, timeouts are macrotasks and lose to microtasks.

**Q: What are the states of a Promise?**
Pending → fulfilled (resolved with a value) or rejected (with a reason). A promise settles once and is then immutable.

**Q: `Promise.all` vs `allSettled` vs `race` vs `any`?** ⭐
`all` — all fulfill (parallel) or reject on the first failure. `allSettled` — waits for all, returns every outcome (status + value/reason), never short-circuits. `race` — settles as soon as the first promise settles (fulfill *or* reject). `any` — first *fulfilled* (rejects only if all reject).

**Q: How do you run async operations in parallel vs sequence?** ⭐
Sequential: `const a = await f(); const b = await g();` (b waits for a — sum of times). Parallel: start both, then await together — `const [a, b] = await Promise.all([f(), g()])` (max of times). Awaiting independent operations in sequence is a common performance bug.

**Q: Is async/await better than promises?**
It's sugar over promises — same mechanics, more readable sequential-looking code, and `try/catch` for errors. Use `.then` chains for simple transforms or fire-and-forget; async/await for multi-step flows. They interoperate freely.

**Q: What happens to an unhandled promise rejection?**
In modern Node it triggers `unhandledRejection` and crashes the process by default; in browsers it logs an error. Always attach `.catch` or wrap `await` in `try/catch`.

**Q: Does `await` block the thread?**
No — it suspends only the *current async function*, freeing the single thread to run other work (event loop keeps going). That's the whole point: non-blocking waiting.

**Q: What's the difference between `setTimeout(fn, 0)` and `queueMicrotask(fn)`?**
`setTimeout(fn, 0)` schedules a macrotask (runs after all microtasks and after a minimum clamp). `queueMicrotask(fn)` schedules a microtask (runs before the next macrotask). Promises use the microtask queue.
