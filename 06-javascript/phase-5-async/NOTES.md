<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · arrays](../phase-4-arrays/NOTES.md) | [Phase 6 · modern ➡](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 5 — Asynchronous JavaScript: Notes

**The fundamental fact:** JavaScript is **single-threaded** — one call stack, one thing at a time — yet handles thousands of concurrent I/O ops via the **event loop** + non-blocking I/O. The opposite of Java's many-threads model (track 01 Phase 5); Java's virtual threads were exciting precisely because they gave Java this kind of cheap concurrency.

## 5.1 — The event loop
Runtime pieces: the **call stack** (synchronous execution), the **macrotask queue** (`setTimeout`, `setInterval`, I/O, UI events), and the **microtask queue** (promise callbacks, `queueMicrotask`, `await` continuations). The loop: run all sync code to completion → **drain the entire microtask queue** → run **one** macrotask → drain microtasks again → repeat. **Microtasks always beat macrotasks.** That's why `1(sync), 2(sync), 3(promise), 4(setTimeout 0)` print in that order even though the timeout was scheduled first — the top ordering interview question.

## 5.2 — Callbacks
The original async pattern: pass a function to run "when done." Node convention is **error-first**: `callback(err, result)`. Problem: sequential async steps nest into the **pyramid of doom** (callback hell) — deep nesting, tangled error handling, no composability. Promises exist to fix this.

## 5.3 — Promises
An object for a value that will exist later; states **pending → fulfilled (resolve) / rejected (reject)**, settling once. JS's `CompletableFuture` (track 01 Phase 5.4) — same concept. `.then` (transform/chain — each returns a new promise, flattening the pyramid), `.catch` (one handler for the whole chain), `.finally` (always runs). **Combinators:** `Promise.all` (all succeed, else reject; runs in **parallel** — total time = slowest, proven ~30ms not 90), `Promise.race` (first to settle — timeouts/fastest replica), `Promise.allSettled` (all outcomes, never short-circuits), `Promise.any` (first fulfilled).

## 5.4 — async / await
Syntactic sugar over promises: async code that **reads sequentially**. An `async` function always returns a promise; `await` pauses the function (not the thread!) until a promise settles; `try/catch` handles rejections. This is Java's virtual-threads payoff delivered via syntax — simple sequential-looking code, non-blocking underneath.

**Key skills:** run independent awaits in **parallel** with `Promise.all` (`await a; await b` is needlessly sequential — sum of times vs max of times, proven ~30ms not 60). Common bugs: forgetting `await` (you get the Promise, not the value); unhandled promise rejections (crash modern Node — always `.catch` or `try/catch`); `await` inside a loop when the iterations are independent (serializes them).
