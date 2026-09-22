<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · generics](../phase-4-generics/NOTES.md) | [Phase 6 · async tooling ➡](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Advanced Types: Interview Q&A

⭐ = asked constantly.

**Q: What is a discriminated union and why is it useful?** ⭐⭐
A union where every arm shares a common property — the discriminant — typed as a
distinct literal per arm (for example `kind: "circle"` vs `kind: "rect"`). Because
the discriminant is a literal, branching on it (a `switch` or a chain of `if`s)
**narrows** the value to exactly one arm's shape, so only that arm's specific fields
are accessible inside that branch. It's TypeScript's type-safe sum type and the
idiomatic way to model anything that's genuinely "one of several distinct shapes" —
API response variants, UI/request state, domain events — because it ties each field
to precisely the state(s) where that field is actually valid, instead of a bag of
optional fields that could combine in nonsensical ways.

**Q: How do you get exhaustiveness checking on a discriminated union, and what
happens if you forget to update it?** ⭐⭐
In the final `default`/catch-all branch, assign the (by then hopefully fully
narrowed) value to a variable explicitly typed `never`. If every arm has actually
been handled by the preceding cases, TypeScript has already narrowed the remaining
possibilities down to nothing by that point, so the assignment to `never` compiles
cleanly. If a new arm is later added to the union and the `switch` isn't updated to
handle it, the value at the `default` branch still includes that unhandled arm — no
longer `never` — and the assignment becomes a compile error. This converts "did
every place that switches on this union get updated" from a bug you discover at
runtime (or never) into a build failure the moment you add a new case anywhere the
check is missing.

```ts
default: {
  const _exhaustive: never = s;   // fails to compile if s isn't fully narrowed
  return _exhaustive;
}
```

**Q: What is a mapped type?** ⭐
A type that constructs a new object type by iterating over another type's keys:
`{ [K in keyof T]: SomeTransform<T[K]> }`. You can add or remove the `?` (optional)
and `readonly` modifiers per key with `-?`/`-readonly` (removing) or `?`/`readonly`
(adding), and you can rename keys during the iteration with `as` (key remapping).
The built-in `Partial`, `Required`, `Readonly`, and `Record` utility types are all
themselves defined as mapped types — there's no special compiler magic behind them
beyond what you can write yourself.

**Q: What is a conditional type, and what does `infer` do?** ⭐
`T extends U ? X : Y` is an `if`/`else` at the type level — it selects between two
type expressions based on whether `T` is assignable to `U`. `infer`, used inside
the `extends` clause, introduces a new type variable that captures a piece of
whatever matched — `T extends (infer E)[] ? E : never` matches `T` against "some
array type" and, if it matches, binds `E` to that array's element type. This
mechanism (conditional types plus `infer`) is what powers built-ins like
`ReturnType<F>` (infers the return position of a function type) and `Awaited<T>`
(infers and recursively unwraps the resolved value inside a `Promise`).

**Q: What does it mean that conditional types "distribute" over a union, and when
would you turn that off?** ⭐ *nuance*
When the type being checked in a conditional is a **bare (naked) type parameter**
and you instantiate it with a union, TypeScript applies the conditional to **each
member of the union separately** and re-unions the results, rather than treating the
union as one single type to check. `NonNullable<A | null>` — properly,
`T extends null | undefined ? never : T` applied to `A | null` — distributes into
`NonNullable<A> | NonNullable<null>`, which simplifies to `A | never`, which is just
`A` (since `never` drops out of a union). This default is usually exactly what you
want, but occasionally you want to compare the union *as a whole* against `U`
instead of distributing member-by-member — wrap both sides in a one-element tuple,
`[T] extends [U] ? X : Y`, which suppresses distribution because a tuple type is no
longer a "bare" type parameter as far as the distribution rule is concerned.

**Q: Name the utility types you use most and what they do.** ⭐⭐
`Partial<T>`/`Required<T>`/`Readonly<T>` toggle the optional/readonly modifier
across every property. `Pick<T, K>`/`Omit<T, K>` keep or remove a subset of keys —
commonly used to derive request/response DTOs from a domain model (`Omit<User,
"password">` for anything that leaves the server). `Record<K, V>` builds a fully
typed dictionary object type when the key set is known ahead of time.
`ReturnType<F>`/`Parameters<F>` introspect a function type's return value or
parameter list without retyping them by hand. `Awaited<T>` unwraps (recursively) a
`Promise<T>` to `T` — see Phase 6. `NonNullable<T>` strips `null`/`undefined`. All of
these exist so you can **derive** a type from one source of truth instead of
duplicating and hand-maintaining a parallel shape.

**Q: How would you type a public DTO that must never leak a password field?** ⭐
`type PublicUser = Omit<User, "password">`. Deriving it directly from `User` means
the DTO tracks the model automatically as `User` evolves — a new field added to
`User` will appear on `PublicUser` too *unless* it's also explicitly `Omit`-ted,
which is the safer failure direction for something sensitive like a password (you'd
rather a new sensitive field force you to update the `Omit` list than silently leak
by being forgotten in a hand-written duplicate interface).

**Q: What are template-literal types good for?**
Building string-literal unions out of a pattern: `` `${Entity}:created` `` expands
across every member of the `Entity` union, generating the full cross product as
concrete literal types — enabling typed event names, typed route paths, and typed
CSS values that the compiler actually checks against typos, instead of a bare
`string` type that accepts anything. Combined with mapped-type key remapping
(`as`) and the intrinsic string-manipulation types (`Capitalize`, `Uppercase`,
`Lowercase`, `Uncapitalize`), you can even *generate* member names — turning
`{ x: number }` into a type with a `getX(): number` method signature — making
previously "stringly-typed" conventions statically enforced.

**Q: When is type-level programming a bad idea?** *nuance*
When cleverness outweighs clarity, or when it materially slows down the compiler.
Deeply nested conditional/mapped types (especially recursive ones) become genuinely
unreadable to the next person — and sometimes to the author a month later — and can
noticeably slow `tsc`. A simpler, explicit, hand-written type is often the right
call even if it duplicates a little structure. Use discriminated unions and the
standard utility types freely — they're idiomatic and instantly recognizable — and
reserve hand-rolled conditional/mapped-type gymnastics for cases where they make a
genuinely public API meaningfully safer or DRYer, with a comment explaining intent
if the mechanics aren't obvious at a glance.

**Q: How do these features help correctness beyond catching typos?**
They let you **make illegal states unrepresentable**: model the domain so the type
system literally cannot express an invalid combination — a "loading" state that also
carries `data`, or a "success" that also carries an `error`. Phase 7 develops this
idea in depth, but the mechanism is exactly discriminated unions (5.1): if the shape
that represents the bug can't be constructed because the type doesn't allow it, the
bug is caught at compile time — or rather, it's not merely caught, it's prevented
from ever being written in the first place, which is a stronger guarantee than any
runtime check could offer after the fact.

**Q: What would `keyof T & string` (as seen in a `Getters<T>`-style mapped type)
actually do, and why not just `keyof T`?** *nuance*
`keyof T` for an object type can include `string | number | symbol` keys (numeric
and symbol-keyed properties are legal in JS objects and TS tracks them). Intersecting
with `string` — `keyof T & string` — narrows the key union down to only the
`string`-typed keys, which matters when you're about to feed those keys into a
template-literal type like `` `get${Capitalize<K>}` ``, because `Capitalize<K>`
requires `K` to actually be (or be assignable to) a string type; a bare `number` or
`symbol` key wouldn't be a valid argument to a string-manipulation intrinsic type.
