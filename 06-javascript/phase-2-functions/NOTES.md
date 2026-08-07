<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects ➡](../phase-3-objects/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions, Scope & Closures: Notes

## 2.1 — First-class & higher-order functions
Functions are values: stored, passed, returned. A **higher-order function** takes/returns functions — array methods (`map`/`filter`/`reduce`) are the everyday case (Java Streams, built into the language). **Behavior parameterization**: pass a function as a strategy; return a function as a factory (`multiplier(10)`).

## 2.2 — Closures (most-probed JS concept)
A **closure** = a function + the variables from the scope where it was *defined*; the inner function keeps access to those variables even after the outer function returns. This is JS's mechanism for **private state** (the module/counter pattern — `count` reachable only through the returned methods). Each call to the outer function makes an independent closure. Powers `once`, memoization, event handlers, partial application. The `for (let ...)` loop capturing a fresh binding per iteration is a closure question in disguise (Phase 1.2).

## 2.3 — `this` (JS's biggest divergence from Java)
In Java `this` is always the instance. In JS **`this` is decided by how a function is CALLED**, not where it's defined. Four rules:
1. **Method call** `obj.fn()` → `this` = `obj` (left of the dot).
2. **Plain call** `fn()` → `this` = `undefined` (strict/modules) or global (sloppy). A method pulled off its object *loses* `this` (`const g = obj.greet; g()` breaks) — the "lost this" bug behind React class-component `.bind`.
3. **Explicit** `fn.call(obj)`/`fn.apply(obj, argsArray)`/`fn.bind(obj)` set `this` manually (`bind` returns a permanently-bound copy).
4. **Arrow functions have no own `this`** — they inherit it lexically from the enclosing scope at definition time. This is the fix for lost-this in callbacks.

Rule of thumb: **arrow for callbacks**; regular/method functions when you *want* a dynamic `this` (object methods, DOM handlers needing the element). `new fn()` is a fifth case (Phase 3): `this` = the new instance.

## 2.4 — Currying, partial application, composition
**Currying**: `f(a,b,c)` → `f(a)(b)(c)`, a chain of one-arg closures — enables specialization (`add10 = curriedAdd(10)`). **Composition**: build big transforms from small functions — `pipe` (left→right, `reduce`) and `compose` (right→left, `reduceRight`). **Memoization**: a closure over a `Map` cache wrapping a pure function. These functional patterns dominate modern JS/React (hooks, selectors, middleware).
