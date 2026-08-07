/*
 * Phase 5 — Asynchronous JavaScript (THE big one)
 * Run:  node async-javascript.mjs
 *
 * Covers 5.1 the event loop, 5.2 callbacks, 5.3 promises, 5.4 async/await.
 *
 * THE FUNDAMENTAL FACT: JavaScript is SINGLE-THREADED. One call stack, one
 * thing at a time. Yet it handles thousands of concurrent I/O operations.
 * How? The EVENT LOOP + non-blocking I/O. This is the opposite of Java's
 * "many threads" model (track 01 Phase 5) — and the reason virtual threads
 * were such a big deal is they gave Java JS-like scalability.
 */

// ===========================================================================
// 5.1 — The event loop, call stack, task & microtask queues
// ===========================================================================
console.log("=== 5.1 event loop ===");

console.log("1: sync start");                       // runs now (call stack)

setTimeout(() => console.log("4: macrotask (setTimeout 0)"), 0);   // MACROtask queue

Promise.resolve().then(() => console.log("3: microtask (promise)"));  // MICROtask queue

console.log("2: sync end");                         // runs now

// Output order: 1, 2, 3, 4 — NOT source order. Why:
//   - Synchronous code runs first, to completion (1, 2).
//   - Then the MICROtask queue drains fully (3) — promises live here.
//   - Then ONE MACROtask runs (4) — setTimeout/setInterval/I/O live here.
//   - Microtasks ALWAYS beat macrotasks. This ordering is a top interview Q.

// We continue the lesson after the event loop settles, so the async demos
// below print in a readable order (using an async IIFE):
await new Promise((r) => setTimeout(r, 10));

// ===========================================================================
// 5.2 — Callbacks & "callback hell"
// ===========================================================================
console.log("\n=== 5.2 callbacks ===");

// The original async pattern: pass a function to run "when done". Node's
// convention is error-first: callback(error, result).
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
await new Promise((r) => setTimeout(r, 20));

// The PROBLEM: sequential async steps nest into a "pyramid of doom":
//   fetchUser(1, (e, u) => {
//     fetchOrders(u, (e, o) => {
//       fetchDetails(o, (e, d) => { ... })   // ever-deeper nesting, tangled errors
//     })
//   })
// Promises were invented to flatten exactly this.

// ===========================================================================
// 5.3 — Promises
// ===========================================================================
console.log("\n=== 5.3 promises ===");

// A PROMISE is an object representing a value that will exist LATER. It has
// three states: pending -> fulfilled (resolve) or rejected (reject).
// (This is Java's CompletableFuture — same concept, same then/catch shape.)
function fetchUserP(id) {
    return new Promise((resolve, reject) => {
        setTimeout(() => {
            id > 0 ? resolve({ id, name: "User" + id }) : reject(new Error("bad id"));
        }, 10);
    });
}

// then/catch/finally — and CHAINING flattens the pyramid (each then returns
// a new promise; return a value or another promise to continue the chain):
await fetchUserP(1)
    .then((user) => {
        console.log("promise result:", user.name);
        return user.id * 100;                       // pass to next then
    })
    .then((computed) => console.log("chained:", computed))
    .catch((err) => console.log("caught:", err.message))   // one catch for the whole chain
    .finally(() => console.log("finally: always runs"));

// COMBINATORS — orchestrate multiple promises (Java's allOf/anyOf):
const p = (val, ms) => new Promise((r) => setTimeout(() => r(val), ms));

// all: wait for ALL, fail if ANY fails (parallel — total time = slowest):
const t0 = Date.now();
const all = await Promise.all([p("a", 30), p("b", 30), p("c", 30)]);
console.log("Promise.all:", all, `in ~${Date.now() - t0}ms (parallel, not 90)`);

// race: first to settle wins (timeouts, fastest replica):
console.log("Promise.race:", await Promise.race([p("slow", 50), p("fast", 10)]));

// allSettled: wait for all, never short-circuit — get every outcome:
const settled = await Promise.allSettled([p("ok", 10), Promise.reject(new Error("boom"))]);
console.log("allSettled:", settled.map((s) => s.status));   // ['fulfilled','rejected']

// ===========================================================================
// 5.4 — async / await (the modern syntax)
// ===========================================================================
console.log("\n=== 5.4 async / await ===");

// async/await is SUGAR over promises: write async code that READS like
// synchronous code. `await` pauses the function (not the thread!) until the
// promise settles. This is Java's virtual-threads promise — simple sequential
// code, non-blocking underneath — delivered via syntax instead of runtime.
async function loadProfile(id) {
    try {
        const user = await fetchUserP(id);          // "pause" until resolved
        const orders = await p(["order-1", "order-2"], 10);  // then this
        return { user, orders };                    // wrapped in a promise automatically
    } catch (err) {
        return { error: err.message };              // try/catch works with await!
    }
}
console.log("await success:", await loadProfile(1));
console.log("await failure:", await loadProfile(-1));

// PARALLEL with async/await — DON'T await in sequence if independent.
// Sequential (slow): await a; await b;   ->  time(a) + time(b)
// Parallel (fast):   Promise.all([a, b]) ->  max(time(a), time(b))
const t1 = Date.now();
const [x, y] = await Promise.all([p("x", 30), p("y", 30)]);
console.log("parallel await:", x, y, `~${Date.now() - t1}ms (not 60)`);

// COMMON BUG: forgetting await gives you the Promise object, not the value:
const forgot = fetchUserP(1);                        // a Promise, not a user
console.log("forgot await:", forgot instanceof Promise ? "[Promise]" : forgot);

console.log("\nphase 5 done.");
