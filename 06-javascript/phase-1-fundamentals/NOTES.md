<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — JavaScript Fundamentals: Notes

*Taught against your Java knowledge — the contrast IS the lesson.*

## 1.1 — Values & types; dynamic typing

**The core mental flip:** in Java a *variable* has a type (compile-time, fixed). In JS a *value* has a type; a variable is just a name that can hold any value and can hold a different type next line. Types are checked at **runtime** — there's no `javac`, the engine (V8 in Node) runs source directly. A "type error" is a runtime surprise, not a build failure. That pain is precisely why TypeScript (track 05) exists.

**The type system:** 7 primitives + object.
- `number` — the *only* numeric type: one 64-bit IEEE 754 double. No `int`/`long`/`float`/`double` split. Consequences: `0.1 + 0.2 === 0.30000000000000004` (Java 1.2's lesson, now for *every* number), and integer precision dies past 2^53 (`2**53 + 1 === 2**53` is `true`).
- `bigint` — arbitrary-precision integers, literal suffix `n` (`10n`). Separate type; can't mix with `number` in arithmetic.
- `string` — no `char` type; `'x'` and `"x"` are identical; immutable (like Java).
- `boolean`.
- `undefined` — "no value assigned yet" (the engine's default for uninitialized).
- `null` — "deliberately no value" (you set it). **`typeof null === "object"`** is a 25-year-old language bug kept for backward compatibility — a favorite interview question.
- `symbol` — unique, unforgeable keys for meta-behavior.
- **object** — everything else: arrays, functions, dates, plain `{}`. `typeof` returns `"function"` for functions (they're callable objects) and `"object"` for arrays — so **`typeof` can't detect arrays; use `Array.isArray()`**.

**undefined vs null:** both are "empty," but distinct. `undefined` = uninitialized/missing (function with no `return`, missing object property, unpassed argument). `null` = intentional emptiness. They're `==` to each other but not `===` (lesson 1.3).

**Primitive vs object semantics:** primitives are immutable and compared **by value** (`2 === 2`); objects are mutable and compared **by reference** (`[1] === [1]` is `false`; assigning `b = a` shares one object — Java's reference model exactly).

**Auto-boxing, invisibly:** primitives have no methods, yet `"hi".toUpperCase()` works — JS wraps the primitive in a temporary `String`/`Number` object, calls the method, discards the wrapper. Like Java autoboxing but automatic and momentary. Corollary: never write `new String(...)`/`new Number(...)` (creates a genuine object, breaks `typeof` and `===`).

**Checking types:** `typeof x` for primitives (mind the `null` and array caveats); `Array.isArray(x)` for arrays; `x instanceof Class` for object kinds; `x === undefined`/`x === null` for the empties.

## 1.2 — Variables: var / let / const, scope, hoisting, TDZ

**Three keywords (Java has one):** `const` (can't reassign — the default, ~95% of the time), `let` (reassignable), `var` (legacy, avoid). Modern rule: **const by default, let when it must change, var never.**

- **`const` freezes the binding, not the value.** `const o = {}; o.x = 1` is fine — you mutate the object, not rebind the name. `Object.freeze(o)` freezes the value.
- **Scope:** `let`/`const` are **block-scoped** (like Java); `var` is **function-scoped** — it ignores `{ }` and leaks out. This is the main reason `var` is dangerous.
- **The loop-closure classic:** `for (var i...)` shares one binding across all iterations, so closures capture the *final* value → `[3,3,3]`. `for (let j...)` creates a fresh binding per iteration → `[0,1,2]`. This bug is *why* `let` was added; it's a top interview question.

**Hoisting:** declarations are conceptually processed before execution, but differently:
- `var` — hoisted *and* initialized to `undefined`. Reading it before its line gives `undefined` (a silent bug), not an error.
- `let`/`const` — hoisted but **not initialized**; they sit in the **Temporal Dead Zone (TDZ)** from the top of the block to the declaration line. Access there *throws* `ReferenceError`. The TDZ is a feature: it turns "used before declared" into a loud error.
- **Function declarations** are fully hoisted — callable before their line. **Function expressions** (`const f = () => ...`) are not — the binding is in the TDZ.

**Modules:** in a Node `.mjs` file each module has its own scope; top-level `this` is `undefined`, no global leakage (old browser scripts leaked top-level `var` onto `window` — ES modules fixed it).

## 1.3 — Operators & coercion

**Coercion** = JS silently converting types to make an operation work (Java would refuse at compile time). Learnable rules, heavy interview material.

**`==` vs `===`:** `===` is strict (same type AND value, no coercion) — **always use it**. `==` coerces first (`1 == '1'` true, `0 == false` true, `'' == false` true). The one defensible `==`: `x == null` (idiom for "null or undefined"). `null == undefined` is true; `null === undefined` is false.

**Truthiness:** any value works as a boolean. Exactly **8 falsy values** — `false, 0, -0, 0n, "", null, undefined, NaN` — everything else is truthy, *including* `"0"`, `"false"`, `[]`, `{}`, `" "`. Note `[]` is truthy but `[] == false` is true — truthiness and `==` use different coercion paths, another reason to avoid `==`.

**`&&`/`||` return operands, not booleans** (short-circuit, but the value survives): `'a' && 'b'` → `'b'`; `'' || 'x'` → `'x'`. Powers the `config.port || 3000` default idiom — but `||` wrongly overrides valid falsy values (`0`, `''`). Fix: **`??` (nullish coalescing)** triggers only on `null`/`undefined`, so `0 ?? 99` → `0`.

**`+` is overloaded:** number add, but **string concat wins if either side is a string** and coerces the other (`'1' + 2` → `'12'`; `1 + 2 + '3'` → `'33'` left-to-right). Other math ops (`-`, `*`, `/`) have no string meaning, so they coerce *to number* (`'5' - 2` → `3`, `'abc' - 1` → `NaN`) — an asymmetry that trips everyone.

**`NaN`:** the only value not equal to itself (`NaN === NaN` is false, per IEEE 754); `typeof NaN` is `'number'`. Detect with **`Number.isNaN(x)`**, never `x === NaN`. Appears from invalid math (`0/0`, `Number('abc')`).

**Explicit conversion beats implicit:** `Number(x)` (but `Number('')` is `0`!), `parseInt(s, 10)` (stops at non-digits), `String(x)`, `Boolean(x)`. Convert on purpose; don't let coercion surprise you.

## 1.4 — Control flow & functions

**Control flow** mirrors Java, with JS-specific bits: `for...of` iterates **values** of an iterable; `for...in` iterates **keys** (property names) — for objects, *not* arrays (on arrays it yields string indices — a bug; use `for...of`). `switch` matches with `===` and still falls through without `break`; `switch (true)` with boolean cases is a clean way to express ranges.

**Four ways to define a function:** declaration (`function f(){}` — hoisted), expression (`const f = function(){}` — not hoisted), **arrow** (`const f = () => x` — concise, implicit return, no own `this` — Phase 2.3), and method shorthand (`obj.m(){}`). Return an object literal from an arrow with `() => ({...})`.

**Functions are first-class values** — stored in variables/arrays, passed, returned. Foundation of everything functional (Phase 2): `[add, sub].map(f => f(6,2))`; a function returning a function (`times = n => x => x*n`) is a closure.

**Parameters:** default params (`greeting = "Hello"` — replaces Java overloading for optionals), **rest** (`...nums` gathers args into a *real array* — Java varargs but genuinely an Array), **spread** (`f(...arr)` explodes an array into args; also clones/merges arrays and objects). JS **does not check argument count**: missing → `undefined`, extra → ignored. **No method overloading** (dynamic typing makes it meaningless) — one name, one function; branch on args inside, or use default/rest params.

**Template literals:** backticks with `${expr}` interpolation and real multiline strings — the default way to build strings (no `+` concat, no `StringBuilder`).
