<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · arrays](../phase-4-arrays/NOTES.md) | [Phase 6 · modern ➡](../phase-6-modern/NOTES.md)
<!-- /nav -->

# Phase 5 — Asynchronous JavaScript: Notes

**The fundamental fact:** JavaScript is **single-threaded** — one call stack, one thing happening at a time — yet it routinely handles thousands of concurrent I/O operations (network requests, timers, file reads). It does this through the **event loop** plus non-blocking I/O, not through threads. This is the polar opposite of Java's traditional many-threads model, and it's why so much of this phase is dedicated to understanding *how* single-threaded code can still feel concurrent.

## 1. The Event Loop, Call Stack & Task Queues

The **event loop** is the mechanism that lets JavaScript's single thread juggle synchronous code, timers, and I/O callbacks without ever blocking. Understanding its pieces and the order it processes them in explains almost every "what does this log, and why" async interview question.

### Key Concepts

- **Call stack**: where synchronous code actually executes, one frame at a time — this is the only place JS code is *running* at any given moment.
- **Macrotask queue**: holds callbacks from `setTimeout`, `setInterval`, I/O completion, and UI events — one macrotask is processed per loop iteration.
- **Microtask queue**: holds promise callbacks (`.then`/`.catch`/`.finally`), `await` continuations, and `queueMicrotask` callbacks.
- **The loop's order**: run all synchronous code to completion → drain the **entire** microtask queue → run **one** macrotask → drain microtasks again → repeat.
- **Microtasks always beat macrotasks**: even a `setTimeout(fn, 0)` waits for every pending microtask to finish first.

### Worked Example: the ordering that trips everyone up

```js
console.log("1: sync start");

setTimeout(() => console.log("4: macrotask (setTimeout 0)"), 0);

Promise.resolve().then(() => console.log("3: microtask (promise)"));

console.log("2: sync end");

// Output: 1, 2, 3, 4
```

In this example: synchronous code always runs first and fully — `"1"` and `"2"` print immediately, in source order, before anything asynchronous gets a chance. Once the call stack is empty, the event loop drains the **entire microtask queue** before touching the macrotask queue — so the promise callback (`"3"`) runs next, even though `setTimeout` was *scheduled* earlier in the source. Only after every microtask is done does the loop pull **one** macrotask off its queue — the `setTimeout` callback (`"4"`) — and run it. This exact scenario, or a variant of it, is one of the single most common JS interview questions.

### Comparison Table: macrotask vs microtask

| | Macrotask | Microtask |
|---|---|---|
| Examples | `setTimeout`, `setInterval`, I/O callbacks, UI events | Promise `.then`/`.catch`/`.finally`, `await` continuations, `queueMicrotask` |
| How many run per loop tick | Exactly one | The entire queue, drained fully |
| Priority vs the other | Lower — always waits for microtasks | Higher — always runs before the next macrotask |
| `setTimeout(fn, 0)` vs `queueMicrotask(fn)` | The `setTimeout` version runs *after* all pending microtasks, plus a minimum clamp | Runs before any macrotask, right after the current synchronous code finishes |

### Why It's Useful

Understanding the event loop is what lets you predict output ordering in any mixed sync/async code, correctly reason about *why* a `Promise.resolve().then(...)` always "wins" against a `setTimeout(..., 0)`, and debug real production bugs where UI updates or logging appear in an unexpected order. It's foundational to everything else in this phase — promises and `async`/`await` are both, ultimately, ways of scheduling work onto the microtask queue.

### Summary

- JS is single-threaded; the event loop is what makes async code possible without blocking.
- Order: all sync code → drain the whole microtask queue → run one macrotask → repeat.
- Microtasks (promises, `await`) always run before the next macrotask (`setTimeout`, I/O).
- `1, 4, 3, 2`-style ordering questions are testing exactly this loop, every time.

---

## 2. Callbacks (and Why They Don't Scale)

A **callback** is a function passed into another function to be invoked once some operation completes. It's JavaScript's original mechanism for async programming — everything before promises existed was built this way, and it's still common in older Node APIs and browser event handling.

### Key Concepts

- **Error-first convention**: Node's idiomatic callback signature is `callback(err, result)` — the caller always checks `err` first before trusting `result`.
- **Inversion of control**: once you pass a callback to a function you don't control, you're trusting that function to call it correctly (once, with the right arguments, handling errors properly) — there's no language-level guarantee.
- **Callback hell (pyramid of doom)**: chaining several dependent async steps as nested callbacks drifts the code rightward with every step, tangling error handling and control flow (full depth in Phase 2's callback hell section).

### Worked Example: an error-first callback

```js
function fetchUser(id, callback) {
  setTimeout(() => {
    if (id <= 0) return callback(new Error("bad id"));
    callback(null, { id, name: "User" + id });
  }, 10);
}

fetchUser(1, (err, user) => {
  if (err) return console.log("error:", err.message);
  console.log("callback result:", user.name);
});
// callback result: User1
```

`fetchUser` simulates an async operation with `setTimeout`, then calls back with either an error (first argument) or a result (second argument, only meaningful when `err` is `null`). This error-first shape is the Node.js standard, and checking `err` first is the idiomatic guard against silently using a bad `result`.

### Worked Example: the pyramid this leads to

```js
fetchUser(1, (err1, user) => {
  fetchOrders(user, (err2, orders) => {
    fetchOrderDetails(orders, (err3, details) => {
      console.log(details);   // three levels deep, and it only gets worse
    });
  });
});
```

Every dependent async step adds another level of nesting and another `err` to check — this is exactly the problem promises (next section) and `async`/`await` were designed to solve. Phase 2 covers the callback-hell problem and its fixes in full depth.

### Why It's Useful

Even though promises and `async`/`await` are preferred for new code, callbacks are still the underlying mechanism for DOM event listeners (`el.addEventListener('click', cb)`), `setTimeout`/`setInterval`, and some Node core APIs (`fs.readFile(path, cb)`). Recognizing the error-first convention and the pyramid-of-doom smell is essential for reading and maintaining any codebase with async history.

### Summary

- A callback is a function invoked by someone else once an operation completes.
- Node's error-first convention (`callback(err, result)`) is the standard shape.
- Deeply nested dependent callbacks produce "callback hell" — the direct motivation for promises.

---

## 3. Promises: Creation, States & Handling

A **Promise** is a special JavaScript object representing a value that will exist eventually — the eventual result of an asynchronous operation, whether it succeeds or fails. Promises exist specifically to make async code composable and readable, avoiding the nested-callback pyramid.

### Key Concepts

- **Three states**: **pending** (initial, neither resolved nor rejected), **fulfilled** (completed successfully, has a value), **rejected** (failed, has a reason/error).
- **Settles exactly once**: a promise transitions from pending to fulfilled or rejected exactly one time, and then never changes again — it's immutable after settling.
- **Constructor shape**: `new Promise((resolve, reject) => { ... })` — you call `resolve(value)` on success or `reject(error)` on failure inside the executor function.
- **`.then(onFulfilled, onRejected)`**: registers callbacks for the fulfilled/rejected outcomes; returns a **new** promise, enabling chaining.
- **`.catch(onRejected)`**: shorthand for `.then(undefined, onRejected)` — handles rejection from anywhere earlier in the chain.
- **`.finally(fn)`**: runs regardless of whether the promise fulfilled or rejected — for cleanup that must always happen.

### Worked Example: creating and handling a promise

```js
const myPromise = new Promise((resolve, reject) => {
  let success = true;
  if (success) {
    resolve("The operation was successful!");
  } else {
    reject("The operation failed.");
  }
});

myPromise
  .then((message) => console.log(message))     // "The operation was successful!"
  .catch((error) => console.error(error));      // only runs if rejected
```

The executor function passed to `new Promise(...)` runs **immediately and synchronously**, but whether it calls `resolve` or `reject` determines the promise's eventual state — here, `success` is `true`, so `resolve(...)` is called, and the `.then()` handler receives that value. If `reject(...)` had been called instead, `.then()`'s callback would be skipped entirely and `.catch()`'s would run.

### Worked Example: a realistic async promise

```js
function fetchUserP(id) {
  return new Promise((resolve, reject) => {
    setTimeout(() => {
      id > 0 ? resolve({ id, name: "User" + id }) : reject(new Error("bad id"));
    }, 10);
  });
}

fetchUserP(1)
  .then((user) => console.log("promise result:", user.name))   // User1
  .catch((err) => console.log("caught:", err.message))
  .finally(() => console.log("finally: always runs"));
```

Here `resolve`/`reject` are called asynchronously, inside `setTimeout`, which is the realistic shape — wrapping any async operation (a timer, a network call, a file read) in a `Promise` so its eventual outcome can be handled uniformly with `.then`/`.catch`/`.finally`, regardless of how the underlying operation actually works.

### Worked Example: chaining `.then()` for sequential steps

```js
myPromise
  .then((result) => {
    console.log(result);
    return anotherPromise();     // returning a promise here chains it into the sequence
  })
  .then((anotherResult) => {
    console.log(anotherResult);  // waits for anotherPromise to settle first
  })
  .catch((error) => console.error(error));
```

Returning a **promise** from inside a `.then()` callback automatically chains it — the next `.then()` doesn't run until that returned promise settles, and it receives *its* resolved value. Returning a plain value instead just passes that value straight to the next `.then()`. This is the mechanism that flattens callback-hell-style nesting into a linear chain.

### Why It's Useful

Promises give async code a uniform, composable shape: no matter what the underlying operation is (timer, network call, file read), the caller always interacts with it the same way — `.then()` for success, `.catch()` for failure, `.finally()` for cleanup. This uniformity is what makes chaining, `Promise.all`-style combinators, and `async`/`await` all possible.

### Summary

- A promise represents a future value; it's pending, then settles once to fulfilled or rejected.
- `.then()` handles success (and can chain further promises); `.catch()` handles failure; `.finally()` always runs.
- The executor function passed to `new Promise(...)` runs synchronously; `resolve`/`reject` determine the eventual state.
- Promises exist specifically to avoid nested-callback "pyramid of doom" code (Phase 2).

---

## 4. Promise Chaining

**Promise chaining** links a sequence of asynchronous steps together using `.then()`, where each step's result flows into the next — producing a flat, readable sequence instead of nested callbacks, with a single `.catch()` able to handle an error from *any* step.

### Key Concepts

- **Each `.then()` returns a new promise**: this is what makes chaining possible — you're never calling `.then()` twice on the same promise, you're calling it once on each link in the chain.
- **Returning a value vs. returning a promise**: a plain returned value is wrapped and passed to the next `.then()` immediately; a returned promise is *awaited* first, and its resolved value is what the next `.then()` receives.
- **Errors propagate down the chain**: if any `.then()` throws or its promise rejects, execution jumps straight to the nearest `.catch()`, skipping every `.then()` in between.

### Worked Example: chaining multiple async steps

```js
function asyncOperation1() {
  return new Promise((resolve) => setTimeout(() => { console.log("Op 1 complete"); resolve(10); }, 1000));
}
function asyncOperation2(value) {
  return new Promise((resolve) => setTimeout(() => { console.log("Op 2 complete"); resolve(value * 2); }, 1000));
}
function asyncOperation3(value) {
  return new Promise((resolve) => setTimeout(() => { console.log("Op 3 complete"); resolve(value + 5); }, 1000));
}

asyncOperation1()
  .then(result1 => { console.log("Result 1:", result1); return asyncOperation2(result1); })
  .then(result2 => { console.log("Result 2:", result2); return asyncOperation3(result2); })
  .then(result3 => console.log("Final result:", result3))
  .catch(error => console.error("Error:", error));
// Op 1 complete → Result 1: 10
// Op 2 complete → Result 2: 20
// Op 3 complete → Final result: 25
```

Each step only starts once the previous one has resolved, and each `.then()` returns the *next* operation's promise, which the chain automatically waits on before continuing — the result is a flat, top-to-bottom sequence rather than the nested pyramid from Phase 2's callback-hell example.

### Worked Example: error propagation skips ahead to `.catch()`

```js
doSomething()
  .then(result => { throw new Error("Something went wrong in Task 1"); })
  .then(newResult => { console.log(newResult); })   // SKIPPED entirely
  .catch(error => console.error(error.message));     // catches it here
```

Throwing inside a `.then()` (or the underlying promise rejecting) causes the chain to jump immediately to the nearest `.catch()`, bypassing every `.then()` in between — this is exactly how a single `.catch()` at the end of a long chain can safely cover errors from *any* step in the chain, without needing per-step error handling.

### Why It's Useful

Chaining is what turns a sequence of dependent async operations into linear, easy-to-follow code with centralized error handling — a direct, practical fix for callback hell. It's also the mental model `async`/`await` builds on: every `await` in an `async` function is, under the hood, equivalent to a `.then()` in a chain.

### Summary

- `.then()` always returns a new promise, enabling one link to chain into the next.
- Returning a promise from `.then()` makes the chain wait for it; returning a plain value passes it through.
- A thrown error or rejected promise anywhere in the chain jumps straight to the nearest `.catch()`.
- Chaining is the direct fix for callback hell — sequential steps, one readable line each.

---

## 5. `async` / `await`

`async`/`await` is syntax built on top of promises that lets asynchronous code **read like synchronous code**, while still behaving fully asynchronously underneath. It's the modern, preferred way to write multi-step async logic in JavaScript.

### Key Concepts

- **`async function`**: always returns a promise. If the function body explicitly returns a value, that value is automatically wrapped in a resolved promise; if it throws, the returned promise is rejected.
- **`await`**: pauses execution of the `async` function (not the whole program/thread) until the awaited promise settles, then either yields the resolved value or throws the rejection reason.
- **Only valid inside `async` functions**: (with the exception of top-level `await` in ES modules).
- **Error handling via `try`/`catch`**: since `await` throws on rejection, a normal `try`/`catch` block around `await` calls handles errors — no `.catch()` chain needed.

### Worked Example: basic `async`/`await`

```js
async function functionName() {
  // ...
}

async function fetchData() {
  try {
    let response = await fetch("https://api.example.com/data");
    let data = await response.json();
    console.log(data);
  } catch (error) {
    console.log("Error:", error);
  }
}
fetchData();
```

`fetchData` is declared `async`, so it always returns a promise. The first `await` pauses until `fetch(...)`'s promise resolves, storing the result in `response`; the second `await` pauses again until `.json()`'s promise resolves. If anything in between throws or rejects — a network failure, an invalid response — the `catch` block handles it, exactly as it would for synchronous exceptions.

### Worked Example: converting a promise chain to async/await

```js
// Promise chain:
function fetchDataChain() {
  fetch("https://api.example.com/data")
    .then(response => response.json())
    .then(data => console.log(data))
    .catch(error => console.log("Error:", error));
}

// Equivalent async/await:
async function fetchDataAsync() {
  try {
    let response = await fetch("https://api.example.com/data");
    let data = await response.json();
    console.log(data);
  } catch (error) {
    console.log("Error:", error);
  }
}
```

Both versions do exactly the same thing — `async`/`await` is sugar, not a different mechanism. The `await`-based version simply reads top-to-bottom like synchronous code, which most developers find easier to follow than a `.then()` chain, especially once there are several sequential dependent steps.

### Worked Example: multi-step async logic with `try`/`catch`

```js
async function loadProfile(id) {
  try {
    const user = await fetchUserP(id);
    const orders = await fetchOrders(user);
    return { user, orders };            // automatically wrapped in a resolved promise
  } catch (err) {
    return { error: err.message };      // try/catch works seamlessly with await
  }
}
console.log(await loadProfile(1));
```

Because `loadProfile` is `async`, its `return` value — whether the success object or the error object from the `catch` block — is automatically wrapped in a promise; calling code needs `await` (or `.then()`) to unwrap it.

### Why It's Useful

`async`/`await` is what makes multi-step async logic (load a user, then their orders, then compute something from both) readable without a wall of `.then()` chains, and it lets you reuse ordinary `try`/`catch` for error handling instead of `.catch()` at the end of a chain. It's the default style for new async code in modern JS, with `.then()` chains typically reserved for simple, single-step transforms or fire-and-forget calls.

### Summary

- `async` functions always return a promise; `await` pauses (only the function, not the thread) until a promise settles.
- `await` is only valid inside `async` functions (plus ES module top-level `await`).
- Error handling uses ordinary `try`/`catch` around `await` calls.
- `async`/`await` is syntactic sugar over promises — same mechanics, more readable sequential-looking code.

---

## 6. The Promise API: Static Combinators

Beyond `.then()`/`.catch()`/`.finally()`, the `Promise` object provides **static methods** for orchestrating multiple promises at once — running them in parallel and combining their outcomes in different ways depending on what you need.

### Key Concepts

- **`Promise.resolve(value)` / `Promise.reject(error)`**: create an already-settled promise directly, useful for normalizing a value into a promise or for testing.
- **`Promise.all(promises)`**: waits for **all** to fulfill; if **any** rejects, the whole thing rejects immediately with that reason (short-circuits). Runs all promises concurrently — total time is roughly the *slowest* one, not the sum.
- **`Promise.race(promises)`**: settles (fulfills or rejects) as soon as the **first** promise settles, whichever it is.
- **`Promise.allSettled(promises)`**: waits for **all** to settle, no matter the outcome, and returns an array describing every result — never short-circuits.
- **`Promise.any(promises)`**: resolves with the first **fulfilled** promise, ignoring rejections along the way; only rejects if *every* promise rejects.

### Worked Example: `Promise.all` — parallel execution, fail-fast

```js
const promise1 = Promise.resolve(3);
const promise2 = new Promise((resolve) => setTimeout(resolve, 1000, "foo"));

Promise.all([promise1, promise2])
  .then(values => console.log(values))   // [3, "foo"] — waits for the slowest
  .catch(error => console.error(error)); // if ANY promise rejects, this runs instead
```

```js
const p = (val, ms) => new Promise(r => setTimeout(() => r(val), ms));
const t0 = Date.now();
const all = await Promise.all([p("a", 30), p("b", 30), p("c", 30)]);
console.log(all, `in ~${Date.now() - t0}ms`);   // ~30ms, not 90ms — they ran concurrently
```

`Promise.all` starts every promise essentially at the same time and waits for the slowest one to finish — three 30ms operations complete in ~30ms total, not 90ms, because they overlap. But the moment any single promise rejects, `Promise.all`'s returned promise rejects immediately with that reason, without waiting for the others to finish.

### Worked Example: `Promise.race` — first to settle wins

```js
const promise1 = new Promise((resolve) => setTimeout(resolve, 500, "one"));
const promise2 = new Promise((resolve) => setTimeout(resolve, 100, "two"));

Promise.race([promise1, promise2]).then(value => console.log(value));
// "two" — it resolves faster, so it wins the race
```

`Promise.race` doesn't care about fulfillment vs. rejection specifically — it just settles as soon as *any* of the input promises does, in whichever state that first one settled in. A classic use: racing a real request against a timeout promise to implement a request timeout.

### Worked Example: `Promise.allSettled` — every outcome, no short-circuit

```js
const promise1 = Promise.resolve("Success");
const promise2 = Promise.reject("Failure");

Promise.allSettled([promise1, promise2]).then(results => console.log(results));
/*
[
  { status: "fulfilled", value: "Success" },
  { status: "rejected", reason: "Failure" }
]
*/
```

Unlike `Promise.all`, a rejection here doesn't short-circuit anything — every promise runs to completion, and the result array tells you the status (`"fulfilled"` or `"rejected"`) and either the `value` or `reason` for each one, in the same order as the input array. Ideal when you need to know the outcome of *every* operation, even the failed ones.

### Worked Example: `Promise.any` — first success, ignore failures

```js
const promise1 = Promise.reject("Fail 1");
const promise2 = new Promise((resolve) => setTimeout(resolve, 100, "Success"));
const promise3 = Promise.reject("Fail 2");

Promise.any([promise1, promise2, promise3])
  .then(value => console.log(value))     // "Success" — the first one that actually fulfilled
  .catch(error => console.error(error)); // only runs if EVERY promise rejects
```

`Promise.any` is the mirror image of `Promise.all`: instead of needing everything to succeed, it just needs *one* to succeed, and rejections from the others are simply ignored (unless literally all of them fail, in which case it rejects with an `AggregateError`).

### Comparison Table: the four combinators

| Method | Resolves when | Rejects when | Typical use |
|---|---|---|---|
| `Promise.all` | All fulfill | Any one rejects (immediately) | Load several independent, all-required resources in parallel |
| `Promise.race` | The first one settles (either way) | (same — first settle, whatever it is) | Implementing a timeout, or "fastest of several replicas" |
| `Promise.allSettled` | Always — once all have settled | Never | Need every outcome, success or failure, without stopping early |
| `Promise.any` | The first one fulfills | Only if all reject | "Try several sources, use whichever responds successfully first" |

### Why It's Useful

These combinators are the direct equivalent of Java's `CompletableFuture.allOf`/`anyOf` — they let you express "run these concurrently and wait appropriately" without manually tracking counters or writing your own orchestration logic. `Promise.all` for "I need everything, and any failure is fatal"; `allSettled` for "I need everything, but partial failure is fine"; `race` for timeouts; `any` for "first success wins, from redundant sources."

### Summary

- `Promise.all` — parallel, all-or-nothing, fails fast on the first rejection.
- `Promise.race` — settles with whichever promise finishes first, success or failure.
- `Promise.allSettled` — waits for everything, reports every outcome, never short-circuits.
- `Promise.any` — first fulfillment wins; only rejects if everything rejects.
- All four run their input promises concurrently — total time is governed by the slowest (or fastest) relevant one, not the sum.

---

## 7. Running Async Operations in Parallel vs. Sequence

A very common real-world (and interview) mistake is `await`ing independent async operations one after another when they could run concurrently — needlessly turning what should take "the longest operation's time" into "the sum of every operation's time."

### Key Concepts

- **Sequential `await`**: each `await` blocks the rest of the function until that specific promise settles, before even *starting* the next one — total time is the **sum** of each operation's duration.
- **Parallel via `Promise.all`**: start every promise first (they begin running concurrently), then `await Promise.all([...])` once — total time is the **max** of the operations' durations.
- **Only safe when independent**: operations that depend on each other's results genuinely must run sequentially — parallelizing only helps when the operations don't need each other's output.

### Worked Example: sequential (slow) vs. parallel (fast)

```js
const p = (val, ms) => new Promise(r => setTimeout(() => r(val), ms));

// Sequential — needlessly slow if x and y don't depend on each other:
const t0 = Date.now();
const x1 = await p("x", 30);
const y1 = await p("y", 30);
console.log(`sequential: ~${Date.now() - t0}ms`);   // ~60ms — sum of both

// Parallel — both start immediately, we just wait for the slower one:
const t1 = Date.now();
const [x2, y2] = await Promise.all([p("x", 30), p("y", 30)]);
console.log(`parallel: ~${Date.now() - t1}ms`);      // ~30ms — max of both
```

In the sequential version, `p("y", 30)` doesn't even *start* until `p("x", 30)` has fully resolved — the two 30ms delays stack up to ~60ms total. In the parallel version, both promises are created (and start their timers) before either is awaited, so they run concurrently and the total wait is governed by whichever one takes longest, ~30ms.

### Why It's Useful

This is one of the most common real-world async performance bugs — loading a page's independent data sources one `await` at a time instead of kicking them all off together — and fixing it is usually a one-line change (`Promise.all`) with a proportional real-world latency improvement. It's also a frequent interview question: "how would you speed up this code" with a snippet full of unnecessary sequential `await`s.

### Summary

- Sequential `await`s of independent operations sum their durations — often an accidental performance bug.
- `Promise.all([...])` starts every promise concurrently and waits for the slowest — the correct default for independent operations.
- Only keep operations sequential when a later one genuinely needs an earlier one's result.

---

## 8. Common Async Bugs

A handful of mistakes account for the overwhelming majority of real-world async bugs — knowing them by name (and by symptom) makes them fast to spot in code review or in your own debugging.

### Key Concepts

- **Forgetting `await`**: you get the `Promise` object itself, not its resolved value.
- **Unhandled promise rejections**: a rejected promise with no `.catch()`/`try`-`catch` anywhere in its chain — modern Node.js treats this as a fatal error and crashes the process by default; browsers log it as an unhandled error.
- **`await` inside a loop over independent operations**: serializes work that could have run in parallel (a specific case of the sequential-vs-parallel issue above).
- **Mixing up `.then()` return semantics**: forgetting that returning a promise from `.then()` chains it, while forgetting to `return` at all breaks the chain (the next `.then()` gets `undefined` instead of waiting).

### Worked Example: forgetting `await`

```js
function fetchUserP(id) {
  return new Promise(resolve => setTimeout(() => resolve({ id, name: "User" + id }), 10));
}

const forgot = fetchUserP(1);          // missing `await`!
console.log(forgot);                   // Promise { <pending> } — NOT the user object
```

Without `await` (or `.then()`), `fetchUserP(1)` returns immediately with the **promise itself**, still pending — trying to use `forgot.name` at this point gives `undefined`, not an error, which makes this bug especially easy to miss until something downstream behaves strangely.

### Worked Example: unhandled rejection

```js
async function risky() {
  throw new Error("boom");
}
risky();   // no .catch(), no surrounding try/catch — an unhandled rejection
```

Because `risky()` is called without `await` and without a `.catch()`, the rejection it produces has nowhere to go — in modern Node this triggers the `unhandledRejection` event and, by default, crashes the process. The fix is always to either `await` it inside a `try`/`catch`, or attach `.catch(...)` directly.

### Worked Example: serializing independent work inside a loop

```js
const ids = [1, 2, 3];
for (const id of ids) {
  const user = await fetchUserP(id);   // waits for each one before starting the next
  console.log(user.name);
}
// Total time ≈ 3 × (one fetch's duration) — needlessly sequential

const users = await Promise.all(ids.map(id => fetchUserP(id)));
// Total time ≈ 1 × (one fetch's duration) — all three run concurrently
```

`await` inside a `for` loop forces each iteration to fully finish before the next one starts — fine when each step genuinely depends on the previous one's result, but a real performance bug when the iterations are independent (as here, fetching unrelated users by id). Mapping to an array of promises and `Promise.all`-ing them runs everything concurrently instead.

### Why It's Useful

These are exactly the bugs that turn up in real production incidents (a crashed Node process from an unhandled rejection) and in every "what's wrong with this code" async interview question. Recognizing the *shape* of each bug — a bare async call with no `await`/`.catch`, a loop with `await` inside it, a variable that's suspiciously a `Promise` object when it shouldn't be — makes them fast to catch on sight.

### Summary

- Forgetting `await` leaves you holding a `Promise`, not its value — check for `Promise { <pending> }` when debugging.
- Always attach `.catch()` or wrap `await` in `try`/`catch` — an unhandled rejection can crash a Node process.
- `await` inside a loop serializes independent operations — use `Promise.all` with `.map()` instead when order/dependency doesn't matter.
- A `.then()` callback must `return` a value or promise to keep the chain going correctly.
