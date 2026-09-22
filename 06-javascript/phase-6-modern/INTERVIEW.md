<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · async](../phase-5-async/NOTES.md) | [Phase 7 · runtime ➡](../phase-7-runtime/NOTES.md)
<!-- /nav -->

# Phase 6 — Modern JS & Modules: Interview Q&A

⭐ = asked constantly.

## ES Modules

**Q: ES Modules vs CommonJS — what's the practical difference?** ⭐

ES Modules use `import`/`export`, are **statically** analyzable (every import/export must be a top-level, literal statement, so tooling can see the whole dependency graph without running any code), load asynchronously, and are the standard in browsers and modern Node (`.mjs` files, or `.js` inside a package with `"type": "module"`). CommonJS uses `require()`/`module.exports`, is **dynamic** (`require()` can be called conditionally, inside an `if`, with a computed path), loads synchronously, and is Node's legacy module system — still common in older npm packages. The static-vs-dynamic distinction is the practically important one: it's exactly what enables (or blocks) tree-shaking.

**Q: Named exports vs. a default export — when would you use each?**

A module can have any number of **named** exports, each imported by its exact name (`import { square, cube } from './mathkit.mjs'`, renamable with `as`); it can have at most **one default** export, importable under any name the caller chooses (`import Calc from './mathkit.mjs'`). Default exports suit a module with one clear "main" thing (a single class or function); named exports suit a module offering several related, independently-useful utilities. Many style guides now prefer named exports across the board, since they're more refactor-friendly (renaming at the import site doesn't silently work around a stale name) and slightly better for tooling (auto-import, tree-shaking analysis).

**Q: What is tree-shaking, and why does it require ES Modules specifically?** ⭐

Tree-shaking is dead-code elimination performed by a bundler: because ESM `import`/`export` statements are static (fixed names, known at build time, no conditional imports), the bundler can precisely determine which exports from a module are actually used anywhere in the project and strip out everything else from the final bundle. CommonJS can't offer the same guarantee — `require()` can be called dynamically with a computed path, or `module.exports` can be mutated conditionally at runtime — so a bundler can't safely prove a given export is truly unused, and has to keep it in.

---

## Iterators & Generators

**Q: What is a generator, and what's a real use case for one?** ⭐

A generator (`function* name() {}`) is a function that can pause at a `yield` expression and resume later — calling it doesn't run the body immediately, it returns a generator object (which is also an iterator) that produces values one at a time as `.next()` is called. Real uses: **lazy/infinite sequences** (a `naturals()` generator that yields `1, 2, 3, ...` forever, computing only as many as are actually requested), **memory-efficient iteration over large or streamed data** (processing a huge file line by line without loading it all into memory), and **implementing custom iterables** with minimal boilerplate via `yield*`.

**Q: What makes an object "iterable" in JavaScript?**

Having a `[Symbol.iterator]()` method that returns an **iterator** — an object with a `next()` method that returns `{ value, done }` on each call, `done` becoming `true` once the sequence is exhausted. Arrays, strings, `Map`, `Set`, and generator objects all implement this protocol natively; plain object literals do not, which is exactly why `for...of` and spread throw `TypeError: obj is not iterable` on a plain `{}` but work fine on `[]`.

**Q: How would you make a plain object work with `for...of`?**

Implement `[Symbol.iterator]()` on it, either manually (returning an object with a hand-written `next()` that tracks position and returns `{ value, done }`) or, far more concisely, as a **generator method**: `*[Symbol.iterator]() { yield* this.items; }`. The generator-method version delegates directly to the underlying array's own iterator via `yield*`, which is almost always simpler than writing the `next()`/`done` bookkeeping by hand.

**Q: Why can generators represent an infinite sequence when a plain array can't?**

Because generators are **lazy** — nothing beyond the current `yield` actually executes until `.next()` is called again. A `while (true) { yield n++; }` generator body never "finishes running"; it just keeps pausing and resuming exactly as many times as requested. An array, by contrast, must be fully materialized in memory before you can use it, so there's no way to represent "infinite" data as a literal array — you'd have to decide on a finite size up front.

---

## Optional Chaining & Nullish Coalescing

**Q: What problem does optional chaining (`?.`) solve, and what forms does it come in?** ⭐

It replaces manual "is this link in the chain missing" guard code (`a && a.b && a.b.c`) with a concise operator that automatically short-circuits to `undefined` the moment it hits a `null`/`undefined` value, instead of throwing. Three forms: `a?.b` for safe property access, `a?.()` for calling something that might not be a function (or might not exist at all), and `a?.[expr]` for safe computed/array indexing. The short-circuit propagates through the rest of a chained expression — `a?.b.c.d` stops immediately if `a` is nullish, never attempting to read `.c` or `.d` at all.

**Q: `??` vs `||` — what's the actual difference, and when does it matter?** ⭐

`||` falls back to its right operand for **any** falsy left operand — `0`, `""`, `false`, `NaN`, `null`, `undefined` all trigger it. `??` falls back **only** when the left operand is specifically `null` or `undefined`, leaving other falsy-but-valid values untouched. The difference matters exactly when `0`, `""`, or `false` are legitimate values you don't want silently overridden: `settings.volume || 100` incorrectly replaces an explicit `volume: 0` with `100`; `settings.volume ?? 100` correctly keeps the `0`.

**Q: What does `a?.b?.c ?? "default"` actually do, step by step?**

Evaluate `a?.b`: if `a` is `null`/`undefined`, the whole optional-chain expression short-circuits to `undefined` right there. Otherwise, continue to `?.c` on the result of `a.b`, with the same short-circuit logic. Whatever value comes out of that chain — a real value, or `undefined` from a short-circuit, or `undefined`/`null` because the actual property genuinely held one of those — is then checked by `??`: if it's `null`/`undefined`, the expression evaluates to `"default"`; otherwise, the actual (possibly falsy-but-valid, like `0`) value is kept. This combined pattern is the standard idiom for safely reading a deeply-nested, possibly-missing configuration value with a sensible fallback.

---

## Error Handling

**Q: How do you create a custom error type in JavaScript, and why bother instead of just throwing a string?** ⭐

Extend the built-in `Error` class, call `super(message)` inside the constructor to set up the standard `.message`/`.stack` properties, set `this.name` to something descriptive (improves how it prints and logs), and attach any additional structured fields relevant to that error (`this.field`, `this.statusCode`, etc.). Throwing an `Error` subclass instead of a bare string preserves a real **stack trace** (critical for debugging) and enables `instanceof` narrowing in `catch` blocks (`if (e instanceof ValidationError)`), neither of which is possible if you `throw "some string"`.

**Q: How would you handle different error types differently in a single `catch` block?**

Narrow by type with `instanceof` checks, and rethrow anything you don't specifically know how to handle:
```js
try {
  doSomething();
} catch (e) {
  if (e instanceof ValidationError) {
    console.log(`Invalid ${e.field}: ${e.message}`);
  } else if (e instanceof NetworkError) {
    retry();
  } else {
    throw e;   // don't swallow errors you don't understand
  }
}
```
This mirrors a Java multi-catch/exception-hierarchy pattern, and the final `throw e` (rethrow) is important — silently swallowing unexpected error types hides real bugs instead of surfacing them.

**Q: What does the `Error` constructor's `cause` option do, and why use it instead of just throwing a new unrelated error?**

`new Error("higher-level message", { cause: originalError })` attaches `originalError` to the new error's `.cause` property, so both the higher-level context ("failed to load user") and the original root cause ("low-level DB error") are preserved and inspectable together, typically shown chained in the stack trace/log output. Throwing a brand-new error without `cause` when catching and re-throwing loses the original error entirely — whoever eventually logs or debugs the failure only sees the outer message, with no way to trace it back to what actually went wrong underneath. `cause` is JS's equivalent of Java's exception-cause chaining.

**Q: How does error handling work across an `await`ed promise rejection?**

A rejected promise that's `await`ed inside a `try` block is caught by the surrounding `catch`, exactly as if a synchronous `throw` had occurred at that line — `async`/`await` deliberately unifies synchronous and asynchronous error handling under the same `try`/`catch` syntax (full depth in Phase 5). Outside of `await`/`try`-`catch`, a rejected promise needs an explicit `.catch()`, or it becomes an unhandled rejection — which crashes a modern Node process by default.
