/*
 * Phase 2 — Functions, scope & closures (JS's heart)
 * Run:  node functions-closures-this.mjs
 *
 * Covers 2.1 first-class/higher-order, 2.2 closures, 2.3 `this`,
 * 2.4 currying/composition. This phase is where JS stops looking like
 * Java and starts thinking differently.
 */

// ===========================================================================
// 2.1 — First-class & higher-order functions
// ===========================================================================
console.log("=== 2.1 first-class / higher-order ===");

// A HIGHER-ORDER function takes and/or returns functions. Array methods are
// the everyday example (Java's Streams, but built into the language):
const nums = [1, 2, 3, 4, 5, 6];
const evensSquared = nums.filter((n) => n % 2 === 0).map((n) => n * n);
console.log("filter+map:", evensSquared);                 // [4, 16, 36]

// Passing a function as a "strategy" (behavior parameterization):
const apply = (fn, x) => fn(x);
console.log("apply:", apply((x) => x + 100, 5));          // 105

// Returning a function (a factory):
const multiplier = (factor) => (x) => x * factor;
const byTen = multiplier(10);
console.log("factory:", byTen(7));                        // 70

// ===========================================================================
// 2.2 — Closures (THE most-probed JS concept)
// ===========================================================================
console.log("\n=== 2.2 closures ===");

// A CLOSURE = a function bundled with the variables from the scope where it
// was DEFINED. The inner function "remembers" those variables even after the
// outer function has returned. This is JS's answer to private state.
function makeCounter() {
    let count = 0;                        // private — only the closures can see it
    return {
        increment: () => ++count,
        decrement: () => --count,
        value: () => count,
    };
}
const counter = makeCounter();
counter.increment();
counter.increment();
counter.decrement();
console.log("counter:", counter.value()); // 1
// `count` is truly private: no way to touch it except through the methods.
// This is the module pattern, the basis of encapsulation before #private.

// Each call to makeCounter creates a SEPARATE closure with its own count:
const c2 = makeCounter();
c2.increment();
console.log("independent:", counter.value(), c2.value());  // 1 1 (not shared)

// The famous interview use — fixing the loop with a closure (pre-let era):
function makeHandlers() {
    const handlers = [];
    for (let i = 0; i < 3; i++) {
        handlers.push(() => i);           // let -> fresh binding captured each time
    }
    return handlers;
}
console.log("loop closures:", makeHandlers().map((h) => h()));  // [0, 1, 2]

// Closures also power "once" and memoization:
function once(fn) {
    let called = false, result;
    return (...args) => {
        if (!called) { called = true; result = fn(...args); }
        return result;
    };
}
const init = once(() => { console.log("  (running expensive init)"); return 42; });
console.log("once:", init(), init(), init());   // init runs ONCE, returns 42 thrice

// ===========================================================================
// 2.3 — `this` and how it's decided (JS's biggest trap vs Java)
// ===========================================================================
console.log("\n=== 2.3 this ===");

// In Java `this` is always the current instance. In JS `this` is decided by
// HOW A FUNCTION IS CALLED, not where it's defined. Four rules:

// (1) METHOD call: `this` = the object left of the dot.
const person = {
    name: "Ajay",
    greet() { return `Hi, I'm ${this.name}`; },
};
console.log("method call:", person.greet());        // this = person

// (2) PLAIN call: `this` = undefined (strict/module) or global (sloppy).
const loose = person.greet;
try {
    loose();                                        // called with no object!
} catch (e) {
    console.log("detached method:", e.constructor.name);  // TypeError: name of undefined
}
// This "lost this" bug is why React class components needed .bind everywhere.

// (3) EXPLICIT: call / apply / bind set `this` manually.
const other = { name: "Ravi" };
console.log("call:", person.greet.call(other));     // borrow greet, this = other
const bound = person.greet.bind(other);             // permanently bind this
console.log("bind:", bound());                      // Hi, I'm Ravi

// (4) ARROW functions have NO own `this` — they inherit it from the
// enclosing scope at definition time. This is the fix for lost-this:
const team = {
    name: "backend",
    members: ["a", "b"],
    // arrow inside map inherits `this` from listAll -> the team object:
    listAll() { return this.members.map((m) => `${m}@${this.name}`); },
};
console.log("arrow this:", team.listAll());         // ['a@backend', 'b@backend']
// If that inner callback were a regular function, `this.name` would break.
// Rule of thumb: arrow functions for callbacks; regular/method functions
// when you WANT a dynamic `this` (object methods, event handlers needing
// the element).

// ===========================================================================
// 2.4 — Currying, partial application, composition
// ===========================================================================
console.log("\n=== 2.4 currying & composition ===");

// CURRYING: turn f(a, b, c) into f(a)(b)(c) — a chain of one-arg functions,
// each a closure over the earlier args. Enables specialization:
const curriedAdd = (a) => (b) => (c) => a + b + c;
console.log("curry:", curriedAdd(1)(2)(3));          // 6
const add10 = curriedAdd(10);                        // partially applied
console.log("partial:", add10(20)(30));              // 60

// COMPOSITION: build complex transforms from small functions.
const compose = (...fns) => (x) => fns.reduceRight((acc, fn) => fn(acc), x);
const pipe = (...fns) => (x) => fns.reduce((acc, fn) => fn(acc), x);

const trim = (s) => s.trim();
const upper = (s) => s.toUpperCase();
const exclaim = (s) => s + "!";

const shout = pipe(trim, upper, exclaim);            // left-to-right
console.log("pipe:", shout("  ship it  "));          // 'SHIP IT!'
console.log("compose:", compose(exclaim, upper, trim)("  hi  "));  // right-to-left 'HI!'

// MEMOIZATION via closure — cache results of a pure function:
function memoize(fn) {
    const cache = new Map();                          // captured private cache
    return (n) => {
        if (cache.has(n)) return cache.get(n);
        const result = fn(n);
        cache.set(n, result);
        return result;
    };
}
const slowSquare = (n) => n * n;
const fastSquare = memoize(slowSquare);
console.log("memoized:", fastSquare(9), fastSquare(9)); // 81 81 (second from cache)
