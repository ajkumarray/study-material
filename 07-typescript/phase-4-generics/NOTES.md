<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects classes](../phase-3-objects-classes/NOTES.md) | [Phase 5 · advanced types ➡](../phase-5-advanced-types/NOTES.md)
<!-- /nav -->

# Phase 4 — Generics: Notes

Generics let a function/type/class work over *many* types while keeping the
relationships between inputs and outputs precise. You know the idea from Java —
but TS generics are **not erased to `Object`**, support **inference**, and gain
`keyof`/indexed-access power Java can't express.

## 4.1 — Generic functions & inference

- A type parameter `<T>` is a placeholder bound at the **call site**. `identity<T>(x:
  T): T` returns exactly what it received — the caller's type flows through.
- **Inference is the norm.** TS infers `T` from the arguments, so you write
  `identity(3)`, not `identity<number>(3)`. Supply explicit type args only when
  inference can't (no value to infer from) or gives too wide a type.
- Multiple parameters relate inputs and outputs: `map<T, U>(xs: T[], fn: (x: T) =>
  U): U[]` — the transform's return type `U` becomes the array's element type. This
  is exactly `Array.prototype.map`'s real signature.

## 4.2 — Constraints, `keyof`, indexed access

- **Constraints** `<T extends Constraint>` require `T` to have certain members so you
  can use them in the body (`T extends { length: number }` lets you read `.length`).
  Java's `<T extends Comparable>` is the analogue.
- **`keyof T`** is the union of `T`'s property keys as literal types (`keyof {a;b}` =
  `"a" | "b"`). No Java equivalent.
- **Indexed access `T[K]`** is the type of property `K` on `T`. Combined:
  `get<T, K extends keyof T>(o: T, k: K): T[K]` returns the *exact* value type for
  the specific key — `getProp(person, "age")` is typed `number`, not a widened union.
  This precision (a key tied to its own value type) is a signature TS superpower.

## 4.3 — Generic interfaces, classes & default params

- **Generic interfaces/classes** parameterise a whole contract: `interface Result<T,
  E>`, `class Stack<T>`, `Map<K, V>`. The type parameter threads through every member.
- **Default type parameters** `<T, E = Error>` let callers omit trailing type args
  (like default value params, but at the type level). `Result<number>` uses `E =
  Error`.
- **Variance** is inferred structurally (no `? extends`/`? super` wildcards like
  Java). You generally don't annotate variance; TS checks assignability structurally.
  (`in`/`out` variance annotations exist but are rarely needed.)

## 4.4 — Practical patterns

- **`Result<T, E>`** — model success/failure as data instead of throwing, as a
  discriminated union `{ ok: true; value: T } | { ok: false; error: E }`. Checking
  `.ok` narrows to the right arm, so `value`/`error` are only accessible where they
  exist. This is the type-safe error pattern (expanded in Phase 7), analogous to
  Rust's `Result` or a functional `Either`.
- **Generic repository** `Repository<T extends HasId>` — one implementation, many
  entity types, all statically checked. Mirrors the Java/Spring repository (Software
  Design DIP) but the element type is a real generic, not `Object`.

## Generics vs Java (the deltas to state in interviews)

- **No runtime erasure to a base type.** TS erases the *annotations* (like all types)
  but the compiler tracks `T` precisely during checking — `Stack<string>.pop()` is
  `string | undefined`, and there's no `Object` fallback or unchecked casts.
- **Inference-first**, so far less explicit `<T>` at call sites.
- **`keyof` + `T[K]` + mapped/conditional types** (Phase 5) make generics a small
  functional language over types — well beyond Java generics.
- **No wildcards** (`? extends`/`? super`); assignability is structural.

## Perspective

Generics are how you write reusable, type-preserving code: containers, higher-order
functions, repositories, and result wrappers. The habit to build is **let inference
do the work** and reach for **constraints + `keyof`/`T[K]`** to keep the exact
relationship between inputs and outputs — that precision is what makes the type-level
tools in Phase 5 possible.
