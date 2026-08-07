<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals & the Type System: Notes

TypeScript = **JavaScript + a static type layer that is checked at compile time
and erased before runtime.** It is a *superset*: every valid JS file is valid TS.
The compiler (`tsc`) does two jobs — **type-check** and **transpile to JS**. Tools
like `tsx` do the transpile-and-run in one step (they *strip* types without a full
check, which is why we also run `tsc --noEmit` to actually verify types).

## 1.1 — Types are erased; structural vs nominal

- **Erasure.** Types exist only during compilation. The emitted JS has *no* type
  annotations, no interfaces, no `type` aliases. Consequence: you **cannot**
  `instanceof` an interface, switch on a type, or reflect over types at runtime —
  unlike Java, where types are reified in the class file. Runtime checks must use
  runtime values (`typeof`, `in`, a discriminant field, a validation library).
- **Structural (duck) typing.** Compatibility is by **shape**, not by name. If an
  object has the required members, it *is* the type — no `implements` needed. Java
  is **nominal**: a type match requires a declared relationship. This is the #1
  mental shift for Java devs. (TS can *simulate* nominal typing with "branded"
  types — Phase 7.)

## 1.2 — Inference, and `any` / `unknown` / `never`

- **Inference.** TS infers types from initializers and return expressions, so you
  annotate far less than in Java. Annotate **function parameters and public API
  boundaries**; let inference handle locals. Over-annotating is noise.
- **`any`** — opts *out* of type checking entirely. It is contagious (spreads
  through expressions) and silently re-admits every JS bug TS exists to prevent.
  Treat `any` as a smell; `strict`/lint rules flag it.
- **`unknown`** — the **safe top type**. Anything is assignable *to* `unknown`, but
  you can do **nothing** with it until you **narrow** it (`typeof`, `in`, a guard).
  It's stricter than Java's `Object` (on which you can still call `Object` methods).
  Use `unknown` for values from outside the type system (`JSON.parse`, `fetch`,
  `catch (e)`), then narrow.
- **`never`** — the **bottom type**: no value inhabits it. It's the return type of
  functions that never return (throw / infinite loop) and the key to **exhaustive**
  `switch` checks (Phase 5). An empty union is `never`.

Top/bottom mental model: `unknown` accepts everything and offers nothing; `never`
accepts nothing and is assignable to everything; `any` disables the rules in both
directions (which is why it's dangerous).

## 1.3 — `type` vs `interface`; unions & literals

- **Literal types** pin a value to an exact literal (`"north"`, `42`, `true`), not
  just its base type. Combined with unions they replace many Java enums.
- **Union types** (`A | B`) mean "one of these" — no direct Java analogue (closest
  is a sealed interface hierarchy). You must **narrow** a union before using members
  specific to one arm.
- **`type` alias vs `interface`:**
  - `interface` — object/class shapes; can be **re-opened** and merged (declaration
    merging); `extends`; reads as a public contract. Classes `implement` it.
  - `type` — everything: unions, tuples, primitives, function types, and
    mapped/conditional types (Phase 5). Cannot be re-opened.
  - **Rule of thumb:** `interface` for object shapes you might extend/implement;
    `type` for unions and computed types. They're interchangeable for plain objects.
- `readonly` (assign-once, like Java `final` on a field) and `?` (optional
  property) refine object members.

## 1.4 — Narrowing & type guards

The compiler performs **control-flow analysis**: inside a conditional it *narrows*
a variable's type to the still-possible subset. Mechanisms:

- **`typeof`** — for primitives (`"string"`, `"number"`, `"boolean"`, `"object"`…).
- **`in`** — "does this property exist" narrows object unions.
- **Truthiness / `=== null`** — narrows away `null`/`undefined` (crucial under
  `strictNullChecks`, where `null` is *not* assignable to `string`).
- **`instanceof`** — for class instances (a real runtime value).
- **Discriminated unions** — a shared literal `kind` field (Phase 5) — the cleanest.
- **User-defined type guards** — a function returning **`x is T`** teaches the
  compiler to narrow at the call site.

Under `strictNullChecks` (part of `strict`), `null` and `undefined` are separate
types you must handle explicitly — this is what kills the JS "undefined is not a
function" class of bug at compile time.

## Perspective

TypeScript's value is a **compile-time contract** over JavaScript's dynamic runtime:
catch shape/`null`/coercion mistakes before they ship, and get editor autocomplete
and safe refactors. Coming from Java, internalize three deltas — **structural**
typing, **runtime erasure**, and the `any`/`unknown`/`never` trio — and the rest of
the language builds cleanly on top (functions → generics → the type-level toolkit).
