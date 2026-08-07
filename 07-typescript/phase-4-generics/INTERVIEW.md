<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects classes](../phase-3-objects-classes/NOTES.md) | [Phase 5 · advanced types ➡](../phase-5-advanced-types/NOTES.md)
<!-- /nav -->

# Phase 4 — Generics: Interview Q&A

⭐ = asked constantly.

**Q: What are generics and why use them?** ⭐⭐
Type parameters that let a function/class/type work over many types while preserving
the relationship between inputs and outputs. They give reuse *without* losing type
safety — a `Stack<string>` yields `string`s out, no casts. The alternative (`any`)
throws away safety.

**Q: How do TS generics differ from Java generics?** ⭐⭐
TS infers type arguments at the call site (rarely explicit), doesn't fall back to
`Object` (no unchecked casts), has no wildcards (`? extends`/`? super`) — variance is
structural — and adds `keyof`, indexed access `T[K]`, and mapped/conditional types
that turn generics into type-level programming. Both erase at runtime, but TS erases
*all* types uniformly rather than to a raw base type.

**Q: What is a generic constraint?** ⭐
`<T extends Shape>` restricts `T` so you can use `Shape`'s members inside the generic
(`T extends { length: number }` lets you read `.length`). It's the analogue of Java's
`<T extends Comparable>`.

**Q: What do `keyof` and `T[K]` do together?** ⭐⭐
`keyof T` is the union of `T`'s keys; `T[K]` is the value type at key `K`. A signature
`get<T, K extends keyof T>(o: T, k: K): T[K]` returns the *exact* type of the accessed
property (e.g. `number` for `"age"`), and rejects keys that don't exist — precision
Java generics can't express.

**Q: What are default type parameters?**
Type params with a fallback: `interface Result<T, E = Error>` lets callers write
`Result<number>` and get `E = Error`. Like default value parameters, but at the type
level; they must be trailing.

**Q: How would you model an operation that can fail, type-safely?** ⭐
A generic `Result<T, E>` discriminated union: `{ ok: true; value: T } | { ok: false;
error: E }`. Callers check `.ok`, which narrows to the correct arm so `value`/`error`
are only reachable where valid. It makes failure explicit in the type instead of via
thrown exceptions.

**Q: When should you pass explicit type arguments vs rely on inference?**
Rely on inference by default. Pass explicit `<T>` when there's no argument to infer
from (e.g. `new Stack<string>()`, an empty array's element type), or when inference
widens to something too general and you want to pin it.

**Q: Do TS generics have runtime cost or reflection?** *nuance*
No — like all types they're erased; there's no `List<T>.class` or reflective access
to `T` at runtime. If you need the type at runtime (e.g. to validate), you must pass a
runtime token (a schema, a discriminant, a constructor) explicitly — the type alone
isn't available.
