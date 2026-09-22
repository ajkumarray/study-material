<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects ➡](../phase-3-objects/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions & Closures: Interview Q&A

⭐ = asked constantly.

## First-Class & Higher-Order Functions

**Q: What does it mean for JavaScript to have "first-class functions"?** ⭐

It means functions are treated exactly like any other value: they can be assigned to variables, passed as arguments to other functions, returned as the result of a function, and stored in arrays or objects. Nothing about a function requires special syntax to be "handled" the way it would in a language without first-class functions — `const f = someFunction; f();` works precisely because `someFunction` is just a value that happens to be callable. This property is the prerequisite for callbacks, `map`/`filter`/`reduce`, and closures.

**Q: What's a higher-order function? Give an example beyond `map`/`filter`/`reduce`.** ⭐

A function that takes one or more functions as arguments, returns a function, or both. Beyond the array methods, `setTimeout(fn, ms)` is higher-order (it takes a function), and a factory like `function createMultiplier(factor) { return value => value * factor; }` is higher-order because it returns a function. Higher-order functions let you separate "the general shape of an operation" (loop through this array, wait this long) from "the specific behavior" (what to do with each element, what to run when the timer fires).

**Q: What's the difference between a callback being called synchronously vs asynchronously? Does it matter?** ⭐

`array.forEach(cb)` calls its callback synchronously — every invocation completes before `forEach` returns. `setTimeout(cb, ms)` calls its callback asynchronously — `setTimeout` returns immediately, and `cb` runs later, after the timer fires and the call stack is clear. It matters a lot for control flow: code written after a synchronous-callback call can assume the callback already ran; code after an asynchronous-callback call cannot. This distinction is the seed of the event loop topic (Phase 7).

---

## Closures

**Q: What is a closure?** ⭐⭐ *the single most-asked JS question*

A closure is a function bundled together with references to the variables from the lexical scope in which it was defined — it retains access to those outer variables even after the outer function has finished executing and would otherwise have gone out of scope. Concretely: `function outer() { let x = 1; return function inner() { return x; }; }` — the `inner` function returned by `outer()` still has access to `x` no matter how much later it's called, because it closed over `x` when it was created.

**Q: Give a real, practical use for closures.** ⭐

Several: **data privacy** via the module pattern (a `makeCounter()` factory that returns methods sharing a private `count` variable nobody outside can reach); **function factories** (`multiplier(n)` returning a function pre-configured with `n`); **memoization/caching** (a returned function closing over a private `Map` cache); **`once` wrappers** that guarantee expensive setup runs exactly one time; and **React hooks** — `useState` is implemented using closures to persist state across re-renders without a class.

**Q: What does this log, and why?**
```js
function makeHandlers() {
  const handlers = [];
  for (let i = 0; i < 3; i++) {
    handlers.push(() => i);
  }
  return handlers;
}
console.log(makeHandlers().map(h => h()));
```
⭐⭐ *the classic — expect the follow-up "what if it were `var`?"*

It logs `[0, 1, 2]`. Because the loop uses `let`, JavaScript creates a fresh binding of `i` for each iteration, so each arrow function closes over its own independent `i`. If `var` were used instead, there would be exactly one `i` shared by the whole function scope, and every closure would read whatever `i` equals after the loop finishes — `[3, 3, 3]`. This is simultaneously a scoping question and a closures question, and interviewers use it to check that you understand *why* `let` fixes it (a new binding per iteration), not just *that* it does.

**Q: Does every function that references an outer variable create a closure, or only ones returned from another function?**

Every function that references a variable from an enclosing scope forms a closure over that scope, whether or not it's ever returned or passed elsewhere — closures aren't special syntax, they're a natural consequence of lexical scoping. The interesting/visible cases are the ones where the closure *outlives* the scope it was created in (returned from a function, stored in an array, passed to `setTimeout`), because that's when "the outer function already returned, but the variable is still accessible" becomes surprising if you haven't internalized how closures work.

**Q: Do two calls to the same outer function share their closures?**

No — each call to the outer function creates a brand-new, independent set of local variables, and any closures formed during that call capture *that* call's variables specifically. `const c1 = makeCounter(); const c2 = makeCounter();` produces two counters with completely separate private `count` variables; incrementing `c1` never affects `c2`.

---

## `this`

**Q: How is `this` determined in JavaScript?** ⭐⭐

By the call site — how the function is actually invoked — not by where the function is defined (with one exception: arrow functions, which fix `this` lexically at definition time and ignore the call site entirely). The rules, roughly in order of how often they come up: a **method call** (`obj.fn()`) sets `this` to the object before the dot; a **plain call** (`fn()`) sets `this` to `undefined` in strict mode/ES modules or the global object in old sloppy-mode scripts; a **constructor call** (`new Fn()`) sets `this` to the newly created instance; `call`/`apply`/`bind` set `this` explicitly to whatever you specify; and an **arrow function** has no `this` of its own — it inherits whatever `this` was in scope where it was written.

**Q: Why doesn't the code below work as expected, and how would you fix it?**
```js
const person = { name: "John", greet() { console.log(this.name); } };
const fn = person.greet;
fn(); // ???
```
⭐⭐

It throws (or logs `undefined`'s crash on `.name`) because pulling `greet` off `person` and calling it as a bare `fn()` triggers the plain-call rule, not the method-call rule — `this` is no longer `person`, it's `undefined` (strict mode). Three standard fixes: `fn = person.greet.bind(person)` to permanently lock `this`; call it as `person.greet()` in the first place, never detaching it; or define `greet` as an arrow class field (`greet = () => console.log(this.name)`) so it captures `this` lexically from the constructor and can never lose it, regardless of how it's later called.

**Q: `call` vs `apply` vs `bind`?** ⭐

All three explicitly set `this` for a function call, but they differ in *when* the call happens and *how* arguments are supplied. `call(thisArg, a, b)` invokes immediately, arguments listed individually. `apply(thisArg, [a, b])` invokes immediately too, but arguments are bundled into an array — useful when you already have an args array, e.g. `Math.max.apply(null, numbersArray)`. `bind(thisArg, a, ...)` does not invoke anything — it returns a brand-new function with `this` (and optionally some leading arguments) permanently fixed, to be called later, any number of times. Mnemonic: **A**pply takes an **A**rray.

**Q: Why don't arrow functions have their own `this`?** ⭐

By design — arrow functions were introduced specifically to solve the "lost `this` in nested callbacks" problem. Instead of establishing a new `this` binding based on how they're called (like every other function form does), an arrow function captures `this` from its enclosing lexical scope at the moment it's defined, and that binding never changes, no matter how the arrow function is later invoked — not even `call`/`apply`/`bind` can override it. This lets you write `this.members.map(m => this.name)` inside an object method and have the inner arrow correctly see the outer `this` (the object), instead of losing it the way a nested regular `function` would.

Follow-up: *what are arrow functions unsuitable for, as a consequence?* Object methods that need a dynamic `this` (since `this` is fixed at definition time, usually to the outer/global scope for a top-level arrow); constructor functions (arrows can't be called with `new` — they have no `[[Construct]]` internal method); and anywhere you need the real `arguments` object, since arrows don't have their own — they see the enclosing scope's `arguments`, if any.

**Q: What does `bind()` return, and does it mutate the original function?**

`bind()` returns a completely new function object with `this` (and any given leading arguments) permanently fixed — the original function is left entirely unchanged and can still be called normally elsewhere with a different `this`. Calling `.bind()` again on an already-bound function has no further effect on `this` — the first bind wins, because the bound function's internal `this` is already locked and the second `bind` call is just binding a wrapper around a wrapper.

---

## Currying & Composition

**Q: What is currying?**

Transforming a function that takes multiple arguments, `f(a, b, c)`, into a chain of functions that each take exactly one argument, called as `f(a)(b)(c)`. Each step returns a new function — a closure — that remembers the arguments supplied so far and waits for the next one, until enough arguments have accumulated to compute the final result. It's implemented purely with closures; there's no special language syntax for it.

**Q: How is currying different from general partial application?**

Currying always breaks a function down into unary (single-argument) steps. Partial application is the more general idea of fixing *some* number of arguments up front (could be one, could be several) and returning a function that accepts the rest in one go — `const addFromFive = (a, b) => a + b + 5` fixed via `bind(null, 5)` for the third argument of a three-arg function is partial application but not currying, because the returned function still takes multiple remaining arguments at once rather than one at a time.

**Q: What's the difference between `pipe` and `compose`, and how would you implement them?**

Both combine a list of single-argument functions into one function, but apply them in opposite order. `pipe(f, g, h)(x)` applies `f` first, then `g`, then `h` — left to right, equivalent to `h(g(f(x)))` — and is naturally implemented with `Array.prototype.reduce`. `compose(f, g, h)(x)` applies `h` first, then `g`, then `f` — right to left, equivalent to `f(g(h(x)))` — implemented with `reduceRight`. `pipe` tends to read more naturally ("do this, then this, then this"), which is why it's more common in modern codebases, while `compose` mirrors traditional mathematical function composition notation.

**Q: Implement a generic `curry` function that works for any arity, and explain how it decides when to actually call the original function.**

```js
function curry(fn) {
  return function curried(...args) {
    if (args.length >= fn.length) {
      return fn.apply(this, args);
    }
    return (...more) => curried.apply(this, args.concat(more));
  };
}
```

It relies on `fn.length` — the number of parameters `fn` was declared with — as the target argument count. Each call to the returned `curried` function accumulates arguments; once enough have been collected (`args.length >= fn.length`), it invokes the original function with everything gathered so far. Until then, it returns another function that keeps collecting. This is why the resulting curried function flexibly supports both `f(1)(2)(3)` and `f(1, 2)(3)` — it doesn't care how the arguments are grouped, only how many have arrived in total.
