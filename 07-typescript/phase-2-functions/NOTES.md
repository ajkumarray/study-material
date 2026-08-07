<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects classes ➡](../phase-3-objects-classes/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions & Everyday Types: Notes

Functions are where types earn their keep day to day: parameters and return types
are the contracts the compiler checks at every call site.

## 2.1 — Function types; params; `void` vs `undefined`

- **Annotate parameters; let the return infer.** Return types are inferred from the
  body; annotate them only on public APIs (to lock the contract) or recursive
  functions. Parameters have no initializer to infer from, so annotate them.
- **Optional `?`, default `=`, rest `...`.** Optional params are `T | undefined`
  and must follow required ones. A default supplies the value *and* infers the type,
  and makes the param optional for callers. Rest params are a typed array.
- **Call-signature aliases** (`type Op = (a: number, b: number) => number`) name a
  function shape — ideal for callbacks and higher-order functions. When you assign a
  function to such a type, its **parameter types are inferred from the alias**
  (contextual typing) — you don't re-annotate them.
- **`void` return ≠ `undefined`.** A `void`-typed callback *may* return a value; the
  caller ignores it. This deliberate rule is why `forEach(x => arr.push(x))` type-
  checks even though `push` returns a `number`. `void` means "I don't care what you
  return," not "you must return `undefined`."

## 2.2 — Unions & `as const`

- **Narrow unions before arm-specific use** (Phase 1). A `number | string` param
  can only use `.toFixed`/`.trim` after a `typeof` check.
- **`as const`** freezes a literal to its **narrowest, deeply-`readonly`** type.
  Without it, `{ role: "admin" }` widens `role` to `string`; with it, `role` is the
  literal `"admin"` and every property is `readonly`. On an array, `as const` yields
  a **readonly tuple of literals**.
- **Derive a union from data:** `const ROLES = [...] as const; type Role =
  typeof ROLES[number]`. One source of truth serves both the runtime array (iterate,
  `.includes`) and the compile-time union — no drift. This is the idiomatic
  "enum without an enum."

## 2.3 — Arrays, tuples, `readonly`, enums

- `T[]` == `Array<T>`. Nested arrays are `T[][]`.
- **Tuples** are fixed-length, position-typed arrays (`[string, number]`) — Java has
  no first-class tuple. Elements can be **named** for docs (`[start: number, end:
  number]`) and can include a rest element. `useState` returns a tuple.
- **`readonly T[]`** (or `ReadonlyArray<T>`) removes mutating methods at compile
  time. ⚠️ It's *compile-time only* — the emitted JS array is still mutable, so a
  cast or JS caller can mutate it. (In this track's demos, an `@ts-expect-error`
  `push` still runs under `tsx`; the point is the compiler rejects it.)
- **Enums are special:** almost everything in TS erases, but a (non-`const`) enum
  **emits a runtime object**. Numeric enums are 0-indexed and two-way (reverse
  mapping). **Modern guidance: prefer a string-literal union or an `as const`
  object** — zero runtime footprint, easier to narrow, and it plays nicely with
  `JSON`. Use `const enum` only with caution (inlined, has isolation gotchas).

## 2.4 — Overloads & `this`

- **Overload signatures**: list several typed call shapes above one broad
  *implementation* signature (which is not directly callable). Use when the return
  type genuinely depends on the argument types in a non-uniform way. Often a single
  **union signature** or a **generic** is clearer — reach for overloads last.
- **`this` typing**: you can type the `this` a function expects via a fake first
  parameter (`function f(this: HTMLElement, e: Event)`), erased at runtime. Arrow
  functions capture the lexical `this` (JS Phase 2) and cannot declare a `this` type.

## Perspective

Functions are the unit where TS's contracts live: precise parameter types, inferred
returns, and unions narrowed at the boundary. Two habits carry through the rest of
the track — **derive unions from `as const` data** instead of hand-maintaining
enums, and **prefer unions/generics over overloads**. Both keep a single source of
truth and lean on inference instead of repetition.
