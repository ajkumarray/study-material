<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: What is TypeScript and how does it relate to JavaScript?** ⭐⭐
A statically-typed **superset** of JavaScript: all valid JS is valid TS. It adds a
compile-time type system that `tsc` checks and then **erases**, emitting plain JS.
It changes *nothing* at runtime — it's a developer-time safety and tooling layer.

**Q: Are types available at runtime?** ⭐⭐
No — types are **erased** during compilation. You can't `instanceof` an interface,
switch on a type, or reflect over types. Runtime decisions must use runtime values
(`typeof`, `in`, a discriminant field, or a validation library like Zod). This
differs from Java, where types are reified in bytecode.

**Q: Structural vs nominal typing — which does TS use?** ⭐⭐
**Structural** (duck typing): compatibility is by shape, so any object with the
required members fits a type without declaring `implements`. Java is **nominal** —
it needs a declared relationship. TS can emulate nominal typing with branded types
when you need identity (e.g. `UserId` vs `OrderId` both backed by `string`).

**Q: `any` vs `unknown` vs `never`?** ⭐⭐
`any` opts out of type checking (unsafe, contagious — avoid). `unknown` is the safe
top type: accepts any value but permits no operations until you **narrow** it — use
it for external data (`JSON.parse`, `catch`). `never` is the bottom type: no value
inhabits it; it's the return of throwing/non-returning functions and powers
exhaustiveness checks.

**Q: When would you use `unknown` over `any`?** ⭐
Whenever a value's type is genuinely unknown at compile time but you still want
safety — API responses, `JSON.parse`, caught errors. `unknown` forces you to prove
the shape (narrow) before use; `any` just lets bugs through silently.

**Q: `type` alias vs `interface`?** ⭐⭐
Both describe object shapes and are interchangeable for that. `interface` can be
re-opened/merged and reads as an extendable contract (classes `implement` it);
`type` also expresses unions, tuples, primitives, and mapped/conditional types but
can't be merged. Convention: `interface` for object/class shapes, `type` for unions
and computed types.

**Q: What is narrowing?** ⭐⭐
The compiler's control-flow analysis that shrinks a variable's type inside a check
(`typeof`, `in`, truthiness, `instanceof`, a discriminant, or a user-defined guard),
so you can safely use members specific to one arm of a union. It's how unions and
`unknown` become usable.

**Q: What is a user-defined type guard?** ⭐
A function whose return type is a **type predicate** `arg is T`. When it returns
true, the compiler narrows `arg` to `T` at the call site — used to encapsulate a
runtime shape check (`function isCat(a): a is Cat`).

**Q: What does `strict` mode give you?** ⭐
A bundle of strictness flags, most importantly `strictNullChecks` (null/undefined
are distinct types you must handle — kills the "undefined is not a function" class),
plus `noImplicitAny`, `strictFunctionTypes`, etc. Always develop with `strict: true`;
turning it on later on a large codebase is painful.

**Q: If types are erased, what stops bad data at runtime?** *nuance*
Nothing automatic — TS trusts your annotations. At trust boundaries (network, disk,
user input) you must **validate at runtime** (manual guards or a schema validator)
and type the result as the parsed shape. TS guarantees *internal* consistency, not
that external data matches your types.
