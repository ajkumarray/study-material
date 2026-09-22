<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · arrays](../phase-4-arrays/NOTES.md) | [Phase 6 · modern ➡](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 5 — Asynchronous JavaScript: Interview Q&A

⭐ = asked constantly. This phase dominates frontend interviews.

## The Event Loop

**Q: Is JavaScript single-threaded? How does it handle asynchronous work if so?** ⭐⭐

Yes — there is exactly one call stack, so only one piece of JS code is ever actually executing at a given instant. Concurrency comes from the **event loop**, not from threads: long-running operations (timers, network requests, file I/O) are handed off to the runtime/OS to handle outside the single JS thread, and when they complete, their callbacks are placed into a queue. The event loop continuously checks whether the call stack is empty, and when it is, pulls the next queued callback onto the stack to run. This is why JS never blocks on I/O — the "waiting" happens entirely outside the single thread.

**Q: Explain the event loop's ordering: call stack, microtasks, macrotasks.** ⭐⭐

The loop's cycle is: run all synchronous code on the call stack to completion, then **drain the entire microtask queue** (every promise `.then`/`.catch`/`.finally` callback and `await` continuation currently queued, including any new ones added while draining), then run exactly **one** macrotask (a `setTimeout`/`setInterval`/I/O callback), then drain the microtask queue again, and repeat. The key rule interviewers are checking for: **microtasks always fully drain before the next macrotask runs**, no matter which was scheduled first.

**Q: What does this log, and why?**
```js
console.log(1);
setTimeout(() => console.log(2), 0);
Promise.resolve().then(() => console.log(3));
console.log(4);
```
⭐⭐

`1, 4, 3, 2`. Synchronous statements always run first, in source order — `1` and `4` print immediately, before any queued callback gets a turn. Once the stack is empty, the event loop drains the microtask queue completely before considering any macrotask — so the promise's `.then()` callback (`3`) runs next. Only after that does the loop pick up the single pending macrotask, the `setTimeout` callback (`2`) — even with a `0`ms delay, it's still a macrotask and always loses to any pending microtask.

**Q: What's the difference between `setTimeout(fn, 0)` and `queueMicrotask(fn)`?**

`setTimeout(fn, 0)` schedules `fn` as a **macrotask** — it runs only after the current synchronous code finishes *and* the entire microtask queue has been drained, plus browsers/Node apply a minimum delay clamp even for `0`. `queueMicrotask(fn)` schedules `fn` as a **microtask** — it runs before the next macrotask, right alongside (and in the same batch as) promise callbacks. Promises always use the microtask queue internally, which is exactly why `Promise.resolve().then(...)` consistently beats `setTimeout(..., 0)` in ordering.

---

## Promises

**Q: What are the states of a Promise, and how do they transition?**

**Pending** (the initial state), which transitions exactly once to either **fulfilled** (the operation succeeded, and the promise now holds a value) or **rejected** (the operation failed, and the promise now holds a reason/error). Once a promise settles — fulfills or rejects — it can never change state again; it's immutable from that point on, which is what makes `.then()` callbacks safe to attach even after a promise has already settled (they simply fire with the already-known outcome).

**Q: What does returning a promise (vs. a plain value) from inside a `.then()` callback do?** ⭐

Returning a plain value passes that value straight to the next `.then()` in the chain, wrapped automatically in a resolved promise if needed. Returning **another promise** causes the chain to wait for that promise to settle before continuing — the next `.then()` receives its resolved value (or the chain jumps to `.catch()` if it rejects). This is the exact mechanism that lets `.then()` chains express sequential async steps flatly, instead of nesting a new callback inside each previous one.

**Q: How does a `.catch()` at the end of a promise chain handle an error from any earlier step?** ⭐

The moment any `.then()` in the chain throws, or the promise it returns rejects, the engine skips every remaining `.then()` and jumps straight to the nearest `.catch()` downstream — regardless of how many `.then()`s are in between. This means a single `.catch()` at the end of a long chain correctly handles a failure from *any* step, not just the last one, which is a major readability win over per-step error checking in nested callbacks.

---

## The Promise API (Combinators)

**Q: `Promise.all` vs `Promise.allSettled` vs `Promise.race` vs `Promise.any`?** ⭐⭐

`Promise.all` runs every promise concurrently and fulfills with an array of all their values **only if every one fulfills** — if even one rejects, `Promise.all` immediately rejects with that reason, without waiting for the rest ("fail fast"). `Promise.allSettled` also runs everything concurrently but never short-circuits — it always resolves (never rejects) with an array describing every promise's outcome as `{status: 'fulfilled', value}` or `{status: 'rejected', reason}`. `Promise.race` settles — fulfills or rejects, whichever happens — as soon as the very **first** promise in the array settles, ignoring the rest. `Promise.any` resolves with the first promise that **fulfills**, ignoring any rejections along the way, and only rejects (with an `AggregateError`) if *every* promise in the array rejects.

**Q: When would you choose `Promise.all` over `Promise.allSettled`?**

`Promise.all` when every operation is required and any single failure should abort the whole thing (e.g. loading several pieces of data that are all mandatory to render a page — no point showing a partial page if one is missing). `Promise.allSettled` when partial success is acceptable and you need to know the outcome of each individual operation regardless of others failing (e.g. sending notifications to multiple users — one failed send shouldn't stop the others, and you want to know which ones failed).

**Q: Give a practical use case for `Promise.race`.**

Implementing a timeout: race the real operation's promise against a promise that rejects after N milliseconds via `setTimeout`. Whichever settles first "wins" — if the real operation is slow, the timeout promise rejects first and the caller can treat it as a timeout error; if the operation finishes in time, its result wins the race instead. It's also used for "fastest of several redundant sources" (e.g. querying multiple mirror servers and using whichever responds first).

**Q: How do `Promise.all` and `Promise.any` differ in what makes them reject?**

`Promise.all` rejects as soon as **any single** promise rejects — it's "all must succeed." `Promise.any` only rejects if **every** promise rejects — it's "at least one must succeed." They're near-opposites: `all` optimizes for "give me everything, fail on the first problem"; `any` optimizes for "give me anything that works, ignore the failures."

---

## `async` / `await`

**Q: How do you run several independent async operations in parallel instead of sequentially?** ⭐

Don't `await` each one individually in sequence (`const a = await f(); const b = await g();` — this sums their durations, since `g()` doesn't even start until `f()` finishes). Instead, start all the promises first, then await them together: `const [a, b] = await Promise.all([f(), g()])`. Because both `f()` and `g()` begin running concurrently the moment they're called, the total wait time becomes the duration of the *slowest* one, not the sum of both — often a significant real-world performance difference, and a common code-review/interview "spot the bug" scenario.

**Q: Is `async`/`await` "better" than raw `.then()` chains? When would you still use `.then()`?**

`async`/`await` is syntactic sugar over promises — same underlying mechanics, not a different concurrency model. It tends to read more naturally for multi-step sequential logic, and it lets you reuse ordinary `try`/`catch` for error handling instead of `.catch()`. `.then()` chains still make sense for simple, single-transform cases, for "fire-and-forget" calls where you deliberately don't want to block surrounding code with `await`, or inside utility functions specifically designed to be chained. The two interoperate freely — an `async` function can `await` a `.then()`-based promise, and vice versa.

**Q: Does `await` block the JavaScript thread while waiting?** ⭐

No — `await` only suspends the **current `async` function**, not the entire thread. Execution returns to the event loop, which is free to run other queued code (other functions, event handlers, timers) while the awaited promise is still pending. This is the entire point of the design: code inside an `async` function *reads* as if it's blocking sequentially, but the runtime is never actually blocked waiting on it.

**Q: What happens to an unhandled promise rejection?** ⭐

If a promise rejects and nothing in its chain ever attaches a `.catch()` (and it's never `await`ed inside a `try`/`catch`), the rejection goes unhandled. In modern Node.js, this fires the `unhandledRejection` process event and, by default, **crashes the process** — a real production incident waiting to happen if async error handling is skipped anywhere. In browsers, it typically logs an "Uncaught (in promise)" error to the console instead of crashing the page. The fix is always to attach `.catch()` to any promise chain that isn't awaited, or wrap `await` calls in `try`/`catch`.

**Q: What's a common mistake with `await` inside a loop, and how do you fix it?** ⭐

Writing `for (const id of ids) { const user = await fetchUser(id); ... }` forces each iteration to wait for the previous one's fetch to complete before starting the next — serializing operations that might be entirely independent of each other. If the fetches don't depend on one another's results, the fix is to kick them all off first and await together: `const users = await Promise.all(ids.map(id => fetchUser(id)))`. This turns a total time proportional to `ids.length × (one fetch)` into roughly `one fetch`'s duration, since they all run concurrently.

**Q: What does forgetting `await` actually produce, and why is that bug easy to miss?**

You get the `Promise` object itself, still (usually) pending, instead of its eventually-resolved value — `const user = fetchUserP(1);` leaves `user` as `Promise { <pending> }`, not `{ id: 1, name: 'User1' }`. It's an easy bug to miss because JavaScript doesn't error at that line — `user.name` simply evaluates to `undefined` (reading a property that doesn't exist on a Promise object), and the failure often doesn't surface until much later, somewhere downstream that assumed `user` was the real object.
