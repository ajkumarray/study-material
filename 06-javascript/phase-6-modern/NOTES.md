<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · async](../phase-5-async/NOTES.md) | [Phase 7 · runtime ➡](../phase-7-runtime/NOTES.md)
<!-- /nav -->

# Phase 6 — Modern JS & Modules: Notes

## 1. ES Modules (`import`/`export`)

An ES **module** is a JavaScript file with its own private scope — nothing inside it is global by default, and the only things visible outside are whatever it explicitly `export`s. This is JavaScript's standard, built-in module system (as opposed to Node's older CommonJS `require`/`module.exports`), and it's what every `.mjs` file (or a `.js` file in a package with `"type": "module"`) uses.

### Key Concepts

- **Named exports**: `export const x = ...` / `export function f() {}` — any number per module, imported by their exact name inside `{ }` (and renamable with `as`).
- **Default export**: `export default ...` — at most one per module, representing "the main thing" this file provides, and importable under **any** chosen name (no `{ }`).
- **Namespace import**: `import * as name from './module.mjs'` — pulls in every export as properties of one object.
- **Module-private by default**: anything not explicitly exported is completely invisible outside the file — genuine encapsulation, unlike old-style scripts where every top-level `var` leaked onto the global `window`/`global` object.
- **Static structure**: `import`/`export` statements must appear at the top level (not inside `if`/functions) and use literal module specifiers, which lets tooling analyze the whole dependency graph *before* running anything.

### Worked Example: named exports, a default export, and a private helper

```js
// mathkit.mjs
export const PI = 3.14159;
export function square(x) { return x * x; }
export const cube = (x) => x ** 3;

function secretFactor() { return 42; }        // NOT exported — invisible outside this file
export function withSecret(x) { return x * secretFactor(); }

export default class Calculator {
  add(a, b) { return a + b; }
  mul(a, b) { return a * b; }
}
```

```js
// app.mjs
import Calculator, { PI, square, cube, withSecret } from "./mathkit.mjs";

console.log(PI, square(5), cube(3));      // 3.14159 25 27
console.log(withSecret(2));               // 84 — secretFactor() itself is never exposed
console.log(new Calculator().add(2, 3));  // 5
```

`Calculator` is the **default** export, so it can be imported under any local name (here, `Calculator` — but `import Whatever from "./mathkit.mjs"` would work identically). `PI`, `square`, `cube`, and `withSecret` are **named** exports, so they must be imported using their exact original names inside `{ }`. `secretFactor` was never exported at all — there is no way to reach it from `app.mjs`, even though `withSecret` uses it internally; the module boundary genuinely hides it.

### Worked Example: namespace import

```js
import * as math from "./mathkit.mjs";
console.log(math.PI, math.square(4), new math.default().add(1, 1));
```

A namespace import bundles every named export (plus the default, under the special key `default`) into a single object — useful when you want to reference a module's exports as a group rather than destructuring each one individually.

### Comparison Table: ES Modules vs CommonJS

| | ES Modules (ESM) | CommonJS (CJS) |
|---|---|---|
| Syntax | `import` / `export` | `require(...)` / `module.exports` |
| Loading | Asynchronous, resolved before execution | Synchronous, resolved at the `require()` call |
| Structure | Static — analyzable without running the code | Dynamic — `require()` can be called conditionally, anywhere |
| Enables tree-shaking | Yes | No (a bundler can't safely prove what's unused) |
| Where it's used | Browsers, modern Node (`.mjs` or `"type": "module"`) | Legacy Node, older npm packages |
| `this` at top level | `undefined` | The module's `exports` object |

### Why It's Useful

Module-per-file scoping is what allows large codebases to avoid naming collisions and hidden global mutation entirely — every dependency between files is explicit, written right at the top as an `import`. ESM's static structure is specifically what enables **tree-shaking**: because a bundler can see, without running any code, exactly which named exports are actually imported anywhere in the project, it can safely strip out everything else from the final bundle, producing smaller production builds.

### Summary

- Each ES module has its own private scope; only explicit exports are visible outside it.
- Named exports (any number, exact-name import) vs. a single default export (any-name import).
- ESM is static and analyzable — this is what makes tree-shaking possible; CommonJS's dynamic `require()` is not tree-shakeable.
- Non-exported bindings are truly private to the file, not just conventionally private.

---

## 2. Iterators & Generators

A **generator function** (`function* name() {}`) can **pause** its execution at a `yield` expression and **resume** later, producing a sequence of values lazily — one at a time, on demand — rather than computing and returning them all at once. Generators are also the easiest way to build a custom **iterable** object.

### Key Concepts

- **`function*` syntax**: marks a function as a generator; calling it does **not** run the body — it returns a generator object (an iterator) immediately.
- **`yield value`**: pauses execution and hands `value` back to whoever called `.next()`; execution resumes from exactly that point the next time `.next()` is called.
- **Lazy evaluation**: values are only computed as they're requested — this makes infinite sequences possible, which a plain array fundamentally cannot represent.
- **The iteration protocol**: any object with a `[Symbol.iterator]()` method that returns `{ next() }` (where `next()` returns `{ value, done }`) is iterable — usable with `for...of`, spread, and destructuring.
- **Generators are inherently iterable**: a generator object satisfies the iteration protocol automatically, so it works directly with `for...of` and spread.

### Worked Example: a basic generator

```js
function* range(start, end, step = 1) {
  for (let i = start; i < end; i += step) {
    yield i;
  }
}
console.log([...range(0, 10, 2)]);   // [0, 2, 4, 6, 8]
for (const n of range(1, 4)) console.log(n);   // 1 2 3
```

Calling `range(0, 10, 2)` doesn't execute the loop right away — it produces a generator object. Spreading it (`[...range(...)]`) or looping over it with `for...of` repeatedly calls `.next()` internally, each call resuming the paused function right after its last `yield` until the loop inside `range` finishes and the generator reports `done: true`.

### Worked Example: lazy, infinite sequences

```js
function* naturals() {
  let n = 1;
  while (true) yield n++;    // an infinite loop — but nothing runs until asked
}
const gen = naturals();
console.log(gen.next().value);   // 1
console.log(gen.next().value);   // 2
console.log(gen.next().value);   // 3
```

`naturals()` describes an infinite sequence, yet the code never hangs — nothing beyond the current `.next()` call actually executes. Each call resumes the `while (true)` loop just long enough to compute and `yield` the next number, then pauses again. A plain array could never represent this (you'd have to decide up front how many elements to generate); a generator can represent it naturally because it only computes what's actually requested.

### Worked Example: implementing a custom iterable

```js
const playlist = {
  songs: ["a", "b", "c"],
  [Symbol.iterator]() {
    let i = 0;
    return {
      next: () => i < this.songs.length
        ? { value: this.songs[i++], done: false }
        : { value: undefined, done: true },
    };
  },
};
console.log([...playlist]);   // ['a', 'b', 'c']
```

Implementing `[Symbol.iterator]()` by hand — returning an object with a `next()` method that follows the `{ value, done }` shape — is exactly what makes `for...of` and spread work on `playlist`, even though it's a plain object, not an array. This is the mechanism generators automate for you; writing it manually here shows what a generator is really doing under the hood.

### Worked Example: the same custom iterable, using a generator instead

```js
const playlist2 = {
  songs: ["a", "b", "c"],
  *[Symbol.iterator]() {         // a generator method — much less boilerplate
    yield* this.songs;
  },
};
console.log([...playlist2]);   // ['a', 'b', 'c']
```

Using a **generator method** for `[Symbol.iterator]` collapses the manual `next()`/`done` bookkeeping down to a single `yield*` (which delegates to another iterable) — this is why generators are the standard, low-effort way to make any custom object iterable.

### Why It's Useful

Generators are the natural fit for lazy/infinite sequences, streaming large datasets without loading everything into memory at once, and building custom iterables without hand-writing the iterator protocol. They were also historically used (before `async`/`await` existed) to write async flow-control libraries that made asynchronous code look synchronous — the same "pause and resume" mechanism `async`/`await` now provides natively.

### Summary

- `function*`/`yield` pause and resume execution, producing values lazily instead of all at once.
- Generators enable infinite/lazy sequences that a plain array cannot express.
- An object is iterable if it implements `[Symbol.iterator]()` returning a `{ next() }` iterator — generators satisfy this automatically.
- `for...of`, spread, and destructuring all rely on the iteration protocol, and only work on iterables.

---

## 3. Optional Chaining (`?.`) & Nullish Coalescing (`??`)

**Optional chaining** (`?.`) lets you safely read a property, call a method, or index into a value that might be `null` or `undefined`, without throwing — it short-circuits to `undefined` the moment it hits a nullish link, instead of crashing. **Nullish coalescing** (`??`) supplies a default value, but only when the left side is specifically `null` or `undefined` — not for other falsy values like `0` or `""`.

### Key Concepts

- **`a?.b`**: reads property `b` on `a`, but if `a` is `null`/`undefined`, the whole expression short-circuits to `undefined` instead of throwing `TypeError: Cannot read properties of undefined`.
- **`a?.()`**: calls `a` as a function only if `a` is not `null`/`undefined` — useful for optional callbacks/methods.
- **`a?.[i]`**: indexes into `a` (array or computed key) only if `a` isn't nullish.
- **Short-circuiting propagates through a chain**: `a?.b?.c?.d` stops at the *first* nullish link and evaluates the whole expression to `undefined`, without attempting to read anything past it.
- **`??` vs `||`**: `??` only falls back for `null`/`undefined`; `||` falls back for *any* falsy value, which is a bug when `0`, `""`, or `false` are legitimate values (Phase 1).

### Worked Example: replacing a manual guard ladder

```js
const user = { name: "Ajay", address: { city: "Pune" } };
const guest = { name: "Guest" };   // no address property at all

console.log(user?.address?.city);      // 'Pune'
console.log(guest?.address?.city);     // undefined — no crash, even though guest.address is undefined
```

Before optional chaining, this required a manual guard: `guest && guest.address && guest.address.city`. `?.` expresses the same intent — "keep going only if everything so far exists" — far more concisely, and it's easy to extend to arbitrarily deep chains without the guard growing linearly.

### Worked Example: optional method calls and indexing

```js
console.log(user.greet?.());       // undefined — `greet` doesn't exist, call is skipped safely
console.log(user?.tags?.[0]);      // undefined — `tags` doesn't exist, indexing is skipped safely
```

`user.greet?.()` first checks whether `user.greet` exists; if it doesn't, the whole expression evaluates to `undefined` and the function call is never attempted (avoiding `TypeError: user.greet is not a function`). The same idea applies to `?.[...]` for safe, conditional array/property indexing.

### Worked Example: `??` vs `||`

```js
const guest2 = { name: "Guest" };
console.log(guest2?.address?.city ?? "unknown");   // 'unknown' — city really is nullish

const settings = { volume: 0 };
console.log(settings.volume ?? 100);   // 0   — 0 is NOT nullish, so it's kept
console.log(settings.volume || 100);   // 100 — BUG: || treats 0 as "missing"
```

`??` correctly distinguishes "there's a real value here, and it happens to be falsy" (`volume: 0`) from "there's genuinely nothing here" (`null`/`undefined`) — `||` cannot make that distinction, since it treats every falsy value the same way.

### Worked Example: the combined idiom

```js
const port = user?.config?.port ?? 8080;
console.log(port);   // 8080 — user.config doesn't exist, so ?. yields undefined, then ?? kicks in
```

`?.` and `??` are frequently used together: `?.` safely navigates a possibly-missing chain of properties, and `??` supplies the final fallback only if the result of that navigation was genuinely nullish — this exact pattern (`obj?.a?.b ?? default`) is extremely common when reading optional configuration or API response fields.

### Why It's Useful

Both operators directly replace verbose, error-prone guard code that was extremely common before ES2020 — deeply nested API responses, optional configuration objects, and DOM-adjacent code (where an element or property might legitimately not exist) all benefit. `??` in particular fixes a genuine, easy-to-miss bug class where `||`-based defaults silently override valid falsy values like `0` or `""`.

### Summary

- `?.` short-circuits to `undefined` on a nullish link instead of throwing — works for property access (`?.`), calls (`?.()`), and indexing (`?.[]`).
- The short-circuit propagates through the rest of the chain the moment it triggers.
- `??` falls back only on `null`/`undefined`; `||` falls back on any falsy value — prefer `??` whenever `0`/`""`/`false` are valid data.
- `obj?.a?.b ?? default` is the standard combined idiom for safely reading optional, possibly-missing data.

---

## 4. Error Handling: `try`/`catch`, Custom Errors & `cause`

JavaScript's `try`/`catch`/`finally` looks structurally like Java's, but with a key difference: you can `throw` **any** value, not just `Error` instances — which is exactly why the convention of always throwing (and catching) real `Error` objects matters so much.

### Key Concepts

- **`try`/`catch`/`finally`**: `try` runs code that might fail; `catch` handles a thrown error; `finally` always runs, whether or not an error occurred.
- **`throw` accepts any value**: strings, numbers, plain objects — but doing so loses stack traces and `instanceof` narrowing, so best practice is to always throw `Error` (or a subclass) instances.
- **Custom error classes**: `class ValidationError extends Error` — extend the built-in `Error`, call `super(message)`, set `this.name`, and attach any structured data relevant to that error type.
- **Narrowing by type**: `if (e instanceof ValidationError)` inside a `catch` block, mirroring a Java multi-catch/exception-hierarchy pattern — and rethrowing (`throw e`) anything you don't specifically expect.
- **`Error` `cause` option**: `new Error(message, { cause: originalError })` chains a lower-level error onto a higher-level one, inspectable via `err.cause` — directly analogous to Java's exception cause chaining.
- **Async errors**: a rejected promise `await`ed inside a `try` block is caught by the surrounding `catch`, exactly like a synchronous throw (Phase 5).

### Worked Example: a custom error class with structured data

```js
class ValidationError extends Error {
  constructor(message, field) {
    super(message);
    this.name = "ValidationError";   // improves stack traces / logging
    this.field = field;              // attach structured, error-specific data
  }
}

function validateAge(age) {
  if (typeof age !== "number") throw new ValidationError("must be a number", "age");
  if (age < 0) throw new ValidationError("must be non-negative", "age");
  return age;
}
```

`ValidationError` behaves exactly like a built-in error (it has `.message`, `.stack`, and passes `instanceof Error`), while also carrying an extra `.field` property specific to validation failures — this is the standard pattern for domain-specific error types.

### Worked Example: narrowing and rethrowing

```js
for (const input of [25, -5, "oops"]) {
  try {
    console.log("valid:", validateAge(input));
  } catch (e) {
    if (e instanceof ValidationError) {
      console.log(`ValidationError on '${e.field}': ${e.message}`);
    } else {
      throw e;   // not a ValidationError — don't swallow it, let it propagate
    }
  }
}
// valid: 25
// ValidationError on 'age': must be non-negative
// ValidationError on 'age': must be a number
```

Checking `instanceof ValidationError` inside `catch` mirrors a Java `catch (SpecificException e)` block — it lets you handle only the errors you actually know how to deal with, and deliberately rethrow (`throw e`) anything unexpected rather than silently swallowing it, which would hide real bugs.

### Worked Example: `Error` `cause` for chaining context

```js
try {
  try {
    throw new Error("low-level DB error");
  } catch (dbErr) {
    throw new Error("failed to load user", { cause: dbErr });
  }
} catch (e) {
  console.log(e.message, "<-", e.cause.message);
  // failed to load user <- low-level DB error
}
```

Wrapping a low-level error in a higher-level, more meaningful one while preserving the original via `{ cause: dbErr }` gives callers both the "what layer failed" context (`"failed to load user"`) and the original root cause (`e.cause`), without losing information the way re-throwing a brand-new unrelated error would.

### Worked Example: catching an async rejection

```js
await Promise.reject(new Error("handled"))
  .catch(e => console.log("async error caught:", e.message));
// async error caught: handled
```

A rejected promise `await`ed inside a `try` block (or handled with `.catch()` directly, as here) is caught exactly like a synchronous `throw` — this is why `async`/`await` lets you reuse ordinary `try`/`catch` for asynchronous error handling (full depth in Phase 5).

### Why It's Useful

Always throwing real `Error` instances (never bare strings/objects) preserves stack traces for debugging and enables reliable `instanceof` narrowing in `catch` blocks. Custom error classes with structured fields (`field`, `statusCode`, etc.) let calling code branch on *what kind* of failure occurred, rather than string-matching an error message. `cause` chaining keeps root-cause information intact as an error is caught and re-thrown up through layers of a system — exactly the debugging information you want in a stack trace or log.

### Summary

- `try`/`catch`/`finally` works like Java's, but JS lets you throw *any* value — always throw `Error` instances anyway, for stack traces and `instanceof` checks.
- Custom error classes (`extends Error`) let you attach structured, error-specific data and narrow by type in `catch`.
- `new Error(msg, { cause: originalErr })` chains a lower-level cause onto a higher-level error, inspectable via `.cause`.
- A rejected, `await`ed promise is caught by an ordinary surrounding `try`/`catch`, same as a synchronous throw.
