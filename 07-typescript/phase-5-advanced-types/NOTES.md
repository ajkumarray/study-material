<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · generics](../phase-4-generics/NOTES.md) | [Phase 6 · async tooling ➡](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Advanced & Type-Level Programming: Notes

This is TypeScript's signature superpower: the type system is itself a small,
**pure functional language** that computes types from types. Most of this phase has
**no runtime value** — the demo verifies it with compile-time `Expect<Equal<A,B>>`
assertions, so `tsc` *is* the test.

## 5.1 — Discriminated (tagged) unions

The single most important modelling tool in TS. Each arm of a union shares a common
**literal discriminant** (`kind: "circle"` / `"rect"`…). Because the discriminant is
a literal, switching on it **narrows** to exactly one arm, exposing only that arm's
fields. It's TS's type-safe **sum type** (Java's closest analogue is a sealed
interface hierarchy).

- **Exhaustiveness with `never`:** in the `default` branch, assign the value to a
  `const _x: never`. If every arm is handled, the value's type is `never` and it
  compiles; add a new arm and forget to handle it, and the type is no longer `never`
  → **compile error**. This turns "did I update every switch?" into a compiler job.

## 5.2 — Mapped & conditional types

- **Mapped types** build a new object type by iterating another's keys:
  `{ [K in keyof T]: ... }`. Modifiers `?`/`readonly` can be **added or removed**
  (`-?`, `-readonly`). This is literally how `Partial`, `Required`, `Readonly` are
  defined. **Key remapping** with `as` (5.4) can rename keys.
- **Conditional types** `T extends U ? X : Y` pick a branch by a type relationship —
  an `if` at the type level. **`infer E`** captures a piece of the matched type
  (e.g. the element type of an array, the return type of a function).
- **Distribution:** when the checked type is a *naked* type parameter and you pass a
  **union**, the conditional distributes over each member
  (`NonNull<A | null>` → `NonNull<A> | NonNull<null>`). This default is powerful and
  occasionally surprising; wrap in a tuple `[T] extends [U]` to switch it off.

## 5.3 — Utility types (the type-level standard library)

Built-ins (all definable from mapped/conditional types) you use constantly:

| Utility | Meaning |
|---|---|
| `Partial<T>` | all props optional (patch/update payloads) |
| `Required<T>` | all props required |
| `Readonly<T>` | all props readonly |
| `Pick<T, K>` | keep only keys `K` |
| `Omit<T, K>` | drop keys `K` (e.g. strip `password` for a DTO) |
| `Record<K, V>` | object with keys `K`, values `V` (typed dictionary) |
| `ReturnType<F>` / `Parameters<F>` | a function's return / params |
| `Awaited<T>` | unwrap a `Promise<T>` (Phase 6) |
| `NonNullable<T>` | remove `null`/`undefined` |

**Deriving DTOs** from one source type (`Omit<User, "password">`,
`Partial<Omit<User, "id">>`) keeps the API and the model in sync with zero drift —
change the model, the derived types update. This is the practical payoff.

## 5.4 — Template-literal types

String types can be composed like template strings: `` `${Entity}:created` ``
generates a union of concrete string literals. Enables typed event names, route
paths, CSS units, and — with mapped-type **key remapping** (`as \`get${Capitalize<K>}\``)
plus intrinsic string types (`Capitalize`, `Uppercase`, `Lowercase`) — generated
member names (getters, action types). It makes "stringly-typed" APIs statically safe.

## When to stop

Type-level programming is addictive and can become unreadable. Guidance: use
discriminated unions and the standard utility types liberally (they're clear and
idiomatic); reach for hand-written conditional/mapped gymnastics **sparingly**, and
only to make a public API safer or DRYer — not for cleverness. If a type needs a
comment to explain *how* it computes, consider whether a simpler explicit type would
serve readers better (KISS, from Software Design Phase 1).

## Perspective

Discriminated unions + exhaustive `never` are the everyday tool — reach for them to
model any "one of N shapes" (API responses, UI state, domain events). Mapped/
conditional/utility types are how you keep one source of truth and derive the rest.
Together they let you **make illegal states unrepresentable** (Phase 7): if the type
can't express the bad case, the bug can't compile.
