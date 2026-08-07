<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · generics ➡](../phase-4-generics/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects, Interfaces & Classes: Interview Q&A

⭐ = asked constantly.

**Q: How does interface implementation differ from Java?** ⭐⭐
TS is **structural**: `implements` only *checks* a class has the members; a class
(or any object) satisfies an interface by shape alone, with or without declaring it.
Java is **nominal** — the class must declare `implements` and that declaration
creates the relationship. In TS, `implements` is a convenience check, not the source
of compatibility.

**Q: What is an excess-property check?** ⭐
When you pass a **fresh object literal** to a typed slot, TS flags properties not in
the target type — catching typos that structural typing would otherwise allow.
Assigning the literal to a variable first bypasses it (you opt into structural
compatibility). It only applies to fresh literals.

**Q: Are `private` members truly private at runtime?** ⭐⭐
No — `public`/`protected`/`private` are compile-time only and erased; a `private`
field is still accessible via bracket notation in the emitted JS. For **runtime**
privacy use JS `#private` fields, which are genuinely inaccessible outside the class.

**Q: What are parameter properties?**
A shorthand where a constructor parameter marked with an access modifier (or
`readonly`) is automatically declared and assigned as a field:
`constructor(private readonly id: string)` replaces a field declaration plus
`this.id = id`. TS-only sugar.

**Q: `interface` vs `type` — pick one and defend it.** ⭐⭐
For plain object shapes they're interchangeable. `interface` supports declaration
merging and reads as an extendable contract, so it's preferred for public object/
class APIs and library augmentation. `type` is required for unions, tuples,
primitives, and mapped/conditional types. Common convention: `interface` for objects,
`type` for everything computed.

**Q: What is declaration merging and when is it useful?**
Two `interface`s with the same name merge into one combined interface. It's mainly
used to **augment existing types** — adding a field to `Window`, Express's `Request`,
or a library's options — without editing their source. `type` aliases can't merge.

**Q: `extends` vs intersection (`&`)?**
`extends` is interface (or class) inheritance. Intersection `A & B` is the `type`-
level combination of two shapes into one that has all members of both. Use `extends`
for interface hierarchies; `&` to compose `type`s or add cross-cutting fields.

**Q: When would you avoid classes in TS?** *nuance*
Much idiomatic TS models data as plain typed objects (`interface`/`type`) plus
functions, matching the JS grain and React/Redux style — no `this`, easy to spread/
serialize, trivially testable. Reach for classes when you want encapsulated mutable
state with invariants, inheritance, or DI-managed lifecycles (Angular, Nest).

**Q: How does `noUncheckedIndexedAccess` change index/array access?** ⭐
It makes indexed reads (`obj[key]`, `arr[i]`) return `T | undefined`, forcing you to
handle the missing/out-of-bounds case. It closes a real hole where TS otherwise
assumes every key/index is present. Worth enabling despite the extra `undefined`
handling.
