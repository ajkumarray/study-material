<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects classes ➡](../phase-3-objects-classes/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions & Everyday Types: Interview Q&A

⭐ = asked constantly.

**Q: Optional param vs default param vs `T | undefined`?** ⭐
An optional param (`x?: T`) may be omitted and is typed `T | undefined` inside. A
default (`x = v`) also lets callers omit it but substitutes `v` (so it's never
`undefined` in the body) and infers its type from the default. `x: T | undefined`
requires the caller to pass *something* (possibly `undefined`) — it isn't optional.

**Q: Why does `void` matter for callbacks?** ⭐
A `void` return type means the caller ignores the return value, so a callback typed
`(x) => void` may still return a value. That's why `forEach(x => arr.push(x))`
compiles even though `push` returns a number. `void` = "return ignored," not "must
return undefined."

**Q: What does `as const` do?** ⭐⭐
It infers the narrowest, deeply-`readonly` type: string/number literals stay literal
(not widened to `string`/`number`), and objects/arrays become `readonly`. Its killer
use is deriving a union type from a runtime array/object
(`type Role = typeof ROLES[number]`) so one value is the single source of truth.

**Q: Tuple vs array?** ⭐
An array (`T[]`) is variable-length, one element type. A tuple (`[string, number]`)
is fixed-length with a type per position. Tuples model heterogeneous fixed records
(like `useState`'s `[value, setter]`), can name positions, and support a rest element.

**Q: Is `readonly` enforced at runtime?** ⭐
No — it's compile-time only. `readonly T[]` removes mutating methods from the type,
but the emitted JS array is a normal mutable array; a cast or a plain-JS caller can
still mutate it. Use `Object.freeze` if you need a runtime guarantee.

**Q: Should you use `enum`? Why or why not?** ⭐⭐
Often no. Unlike the rest of TS, a (non-const) `enum` **emits runtime code** and has
quirks (numeric reverse-mapping, structural surprises). Prefer a **string-literal
union** or an `as const` object with a derived union: zero runtime cost, easy to
narrow, JSON-friendly. Enums are fine when you want a named runtime namespace, but
they're not the default choice anymore.

**Q: When do you need function overloads?**
When a function's return type depends on argument types in a way a single signature
can't express uniformly. You write multiple overload signatures over one broad
implementation signature (not itself callable). Prefer a union parameter or a
generic first — overloads are verbose and easy to get subtly wrong.

**Q: How does contextual typing help with callbacks?**
When a function is assigned to a known function type (a param, a typed variable), TS
infers the callback's parameter types from context, so you write `(a, b) => a * b`
without annotating `a`/`b`. It's inference flowing from the expected type into the
expression.

**Q: How do you type `this` in a function?** *nuance*
Declare a fake first parameter `this: SomeType`; it's erased at runtime and only
constrains how the function may be called. Arrow functions can't declare `this` —
they capture the lexical `this`, which is usually what you want to avoid the JS
`this`-binding pitfalls from track 06.
