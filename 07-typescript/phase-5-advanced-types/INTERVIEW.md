<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · generics](../phase-4-generics/NOTES.md) | [Phase 6 · async tooling ➡](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Advanced Types: Interview Q&A

⭐ = asked constantly.

**Q: What is a discriminated union and why is it useful?** ⭐⭐
A union whose arms share a common literal field (the discriminant, e.g. `kind`).
Switching/branching on that field narrows to a single arm, so you can only access
that arm's properties — a type-safe sum type. It's the idiomatic way to model API
responses, UI state, and domain events.

**Q: How do you get exhaustiveness checking?** ⭐⭐
In the `default`/final branch, assign the value to a `const _x: never`. If all arms
are handled the value is `never` and it compiles; if someone adds an arm and doesn't
handle it, the value isn't `never` and you get a compile error — the compiler flags
every unhandled case.

**Q: What is a mapped type?** ⭐
A type that constructs a new object type by iterating another's keys:
`{ [K in keyof T]: ... }`. You can add/remove `?` and `readonly` modifiers (`-?`,
`-readonly`) and remap keys with `as`. `Partial`, `Required`, `Readonly`, `Record`
are all mapped types.

**Q: What is a conditional type and what does `infer` do?** ⭐
`T extends U ? X : Y` selects a type based on a relationship — a type-level `if`.
`infer` introduces a type variable captured from the matched pattern, e.g.
`T extends (infer E)[] ? E : never` extracts an array's element type. It powers
`ReturnType`, `Awaited`, etc.

**Q: What does it mean that conditional types "distribute"?** *nuance*
When the checked type is a bare type parameter and you pass a union, the conditional
applies to each union member separately and re-unions the results. Usually desirable
(`NonNullable<A | null>` → `A`), but to compare the union as a whole, wrap both sides
in a tuple: `[T] extends [U] ? ...`.

**Q: Name the utility types you use most and what they do.** ⭐⭐
`Partial`/`Required`/`Readonly` (toggle modifiers), `Pick`/`Omit` (subset/remove
keys), `Record<K,V>` (typed dictionary), `ReturnType`/`Parameters` (introspect
functions), `Awaited` (unwrap Promises), `NonNullable`. They let you **derive** types
from a single source instead of duplicating shapes.

**Q: How would you type a public DTO that hides a password?** ⭐
`type PublicUser = Omit<User, "password">`. Deriving it from `User` means the DTO
tracks the model automatically — add a field to `User` and it appears (or you `Omit`
it), so the API and model never drift.

**Q: What are template-literal types good for?**
Building string-literal unions from patterns: typed event names
(`` `${Entity}:created` ``), route paths, CSS values. With mapped-type key remapping
and intrinsics like `Capitalize`, you can even generate member names (getters, action
types), making previously "stringly-typed" APIs statically checked.

**Q: When is type-level programming a bad idea?** *nuance*
When cleverness beats clarity. Deeply nested conditional/mapped types become
unreadable and slow the compiler, and a simpler explicit type often serves readers
better. Use unions and standard utilities freely; hand-rolled type gymnastics only to
make a public API meaningfully safer or DRYer — and comment the intent.

**Q: How do these features help correctness beyond catching typos?**
They let you **make illegal states unrepresentable**: model the domain so the type
can't even express an invalid combination (a loading state with data, a success with
an error). If the bad case can't be typed, it can't compile — errors caught at design
time, not runtime.
