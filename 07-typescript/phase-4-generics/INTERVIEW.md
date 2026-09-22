<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects classes](../phase-3-objects-classes/NOTES.md) | [Phase 5 · advanced types ➡](../phase-5-advanced-types/NOTES.md)
<!-- /nav -->

# Phase 4 — Generics: Interview Q&A

⭐ = asked constantly.

**Q: What are generics and why use them?** ⭐⭐
Type parameters that let a function, interface, or class work over many concrete
types while the compiler still preserves and checks the *relationship* between
inputs and outputs. They give you reuse without giving up type safety: a
`Stack<string>` yields `string`s back out with no casts required, whereas the
`any`-based alternative would compile the same way whether you pushed a `string` or
accidentally pushed a `number`, silently losing the very guarantee generics exist to
provide.

**Q: How do TypeScript generics differ from Java generics?** ⭐⭐
Several concrete differences: TS infers type arguments at the call site far more
aggressively, so explicit `<T>` is the exception rather than the norm; TS doesn't
erase to a raw base type the way Java erases to (commonly) `Object` at the bytecode
level, so there's no unchecked cast machinery involved — `Stack<string>.pop()` really
is statically `string | undefined` all the way through; TS has no wildcard syntax
(`? extends`/`? super`) because variance is checked structurally by comparing actual
member types; and TS adds `keyof`, indexed access (`T[K]`), and (Phase 5)
mapped/conditional types, which together make generics a genuinely functional
type-level programming system with no Java parallel. Both languages erase generic
type information at runtime in the sense that you can't reflect on `T` itself, but
the *mechanism and precision* of how each language tracks `T` before erasure differ
substantially.

**Q: What is a generic constraint?** ⭐
`<T extends Shape>` restricts what concrete types `T` may be bound to, which in turn
lets you use `Shape`'s members inside the generic body — without a constraint, `T`
could be *anything*, so the compiler can't let you call `.length` or any other
member on a bare, unconstrained `T`. `function longest<T extends { length: number
}>(a: T, b: T): T` accepts strings and arrays (both have `.length`) but rejects
`number`, caught at the call site. It's the direct analogue of Java's `<T extends
Comparable<T>>`.

**Q: What do `keyof` and indexed access `T[K]` do, and why are they usually shown
together?** ⭐⭐
`keyof T` produces the union of `T`'s own property keys as literal types —
`keyof { a: number; b: string }` is `"a" | "b"`. `T[K]` (indexed access) is the type
of the value at property `K` on `T`. Shown together in a generic signature —
`function getProp<T, K extends keyof T>(obj: T, key: K): T[K]` — they let a single
function return the **exact** type of whatever specific property was requested:
calling `getProp(person, "age")` resolves to `number` (the real type of
`person.age`), not some widened union of every possible property's type, and passing
a key that doesn't exist on `T` (`"email"` when `person` has no such field) is a
compile error rather than a runtime `undefined`. This is precision Java's generics
and reflection-based property access simply can't express statically.

**Q: What are default type parameters?**
Type parameters with a fallback value, written like a default value parameter but at
the type level: `interface Result<T, E = Error>` lets a caller write `Result<number>`
and have `E` default to `Error` rather than being forced to spell out
`Result<number, Error>` every time. Default type parameters must come after any
type parameters without defaults (they must be trailing), exactly mirroring the rule
for default value parameters.

**Q: How would you model an operation that can fail, type-safely, without throwing?**
⭐
A generic `Result<T, E>` discriminated union: `{ ok: true; value: T } | { ok: false;
error: E }`. Callers must check `.ok` before the compiler will let them read `.value`
(the error arm doesn't have `.value`, and vice versa) — this turns "did the caller
handle the failure case" into a compile-time-enforced requirement rather than a
convention that's easy to forget, the way an uncaught/unhandled `throw` is easy to
forget. It's Phase 5's discriminated union pattern applied generically, and it's
covered further as a deliberate exceptions-vs-Result trade-off in Phase 7.

```ts
type Result<T, E = string> = { ok: true; value: T } | { ok: false; error: E };
```

*Follow-up: what does `function ok<T>(value: T): Result<T, never>` buy you by using
`never` for the error type parameter?* It documents, precisely, "this branch can
never actually be the error arm" — and because `never` is assignable to anything
(it's the bottom type), a `Result<T, never>` still satisfies a caller expecting the
wider `Result<T, string>` (or whatever concrete error type is in play), so `ok(n)`
can be returned directly from a function declared to return `Result<number>`.

**Q: When should you pass explicit type arguments instead of relying on
inference?**
Rely on inference by default — it's the idiomatic TS style and keeps call sites
terse. Pass an explicit `<T>` when there's genuinely nothing for the compiler to
infer from, most commonly constructing an empty container (`new Stack<string>()`,
where there's no argument carrying the element type), or when inference would widen
to something broader than you actually want and you need to pin it down explicitly.

**Q: Do TypeScript generics have any runtime cost, or can you reflect on `T` at
runtime?** ⭐ *nuance*
No runtime cost, and no reflection — like all TS types, generic type parameters are
completely erased at compile time; there's no `List<T>.class`-style object and no
way to ask "what is `T` right now" inside a generic function's body at runtime. If
your code genuinely needs the type at runtime — to validate a value's shape, to
construct an instance, to pick behavior per type — you must pass a **runtime
token** explicitly alongside the type parameter (a schema object, a class
constructor reference, a discriminant string), because the type argument alone never
survives to runtime. This mirrors Java's generic erasure in spirit (no
`T.class` either) even though the two languages erase generics through very
different compilation pipelines.

**Q: How does variance work in TypeScript generics — do you ever write `?
extends`/`? super`-style wildcards?** *nuance*
No wildcard syntax exists in TypeScript. Variance is checked **structurally**: when
the compiler needs to know if `Container<Dog>` is assignable where
`Container<Animal>` is expected, it looks at how `T` is actually used inside
`Container`'s member types (return positions behave covariantly, parameter
positions behave contravariantly under `strictFunctionTypes`) rather than requiring
you to annotate the relationship up front the way Java's `? extends`/`? super`
wildcards do at the call site. Explicit `in`/`out` variance annotations do exist on
type parameters for narrow cases (mostly library authors pinning down variance for
better error messages or performance), but application code essentially never needs
them.
