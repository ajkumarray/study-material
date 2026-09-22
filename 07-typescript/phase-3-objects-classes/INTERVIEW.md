<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · generics ➡](../phase-4-generics/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects, Interfaces & Classes: Interview Q&A

⭐ = asked constantly.

**Q: How does interface implementation differ from Java?** ⭐⭐
TypeScript is **structural**: `implements` on a class is only a *check* that the
class has the required members — it doesn't create the relationship, and it isn't
even required for compatibility. Any object, class instance or otherwise, satisfies
an interface as long as it has the right shape, whether or not it ever declared
`implements`. Java is **nominal**: a class must explicitly declare `implements
Foo`, and that declaration is what creates the type relationship — two classes with
identical methods but no shared `implements` are unrelated types in Java, while in
TS they're interchangeable wherever that shape is expected. Practically, this means
in TS you can pass a plain object literal or a mock straight into a function
expecting an interface, with zero boilerplate "fake implementing class," which is
common in unit tests.

```ts
interface Shape { area(): number }
function totalArea(shapes: Shape[]) { return shapes.reduce((s, x) => s + x.area(), 0); }
totalArea([{ area: () => 4 }]); // fine — never declared "implements Shape"
```

**Q: What is an excess-property check, and when does it and doesn't it apply?** ⭐
It's a stricter check TypeScript applies specifically when you pass a **fresh
object literal** directly to a typed slot (a variable declaration, a function
argument): any property on the literal that doesn't exist on the target type is
flagged as an error, even though ordinary structural typing would have allowed the
extra property to just be ignored. It exists to catch typos — `{ colour: "red" }`
against a type expecting `color` — that structural compatibility alone would
silently accept as "just an extra field." It only fires on literals: assigning the
same object to a variable first (`const x = {...}; printName(x);`) opts back into
plain structural compatibility, and the check disappears, because now you're passing
an already-typed value rather than a bare literal with no prior type of its own.

**Q: Are `private` class members truly private at runtime?** ⭐⭐
No — `public`/`protected`/`private` are TypeScript keywords, checked only by the
compiler, and are completely erased from the emitted JavaScript. A field marked
`private` is still a perfectly ordinary property on the emitted object and remains
reachable via bracket notation (`obj["secret"]`) or from any plain-JS code that
isn't going through the type checker at all. For **genuine** runtime privacy, use
JavaScript's native `#private` fields — these really are inaccessible from outside
the class, enforced by the JS engine itself, not just flagged by `tsc`. Use `private`
when you want API hygiene (discourage misuse, keep autocomplete clean) and `#private`
when privacy is an actual invariant that must hold no matter what code touches the
object.

**Q: What are parameter properties?**
A constructor-parameter shorthand: prefixing a constructor parameter with an access
modifier (`private`/`protected`/`public`) and/or `readonly` automatically both
declares that field on the class *and* assigns it from the argument, in one line —
`constructor(private readonly id: string)` replaces a separate field declaration
plus a `this.id = id;` statement in the constructor body. It's pure TypeScript
sugar with no equivalent syntax in plain JavaScript classes; the emitted JS still
has the ordinary field-assignment logic, just generated for you.

**Q: `interface` vs `type` for a class/object shape — pick one and defend it.** ⭐⭐
For a plain object shape they're interchangeable — either works, and a class can
`implements` either kind as long as it describes an object shape. `interface` is
generally preferred for object/class contracts because it supports **declaration
merging** (multiple `interface` declarations with the same name automatically
combine) and reads as an extendable public contract, which matters when you're
designing something meant to be implemented or extended, or when you need to augment
a type you don't own (a library's or the global scope's types). `type` is required
the moment you need something `interface` can't express — unions, tuples,
mapped/conditional types — so it remains the default for anything computed. A common
convention: `interface` for objects/classes, `type` for everything else.

**Q: What is declaration merging and when is it useful?** ⭐
When two (or more) `interface` declarations share the same name in the same scope,
TypeScript automatically **merges** them into a single interface requiring the union
of all their members — it is not a redeclaration error, unlike almost every other
kind of duplicate identifier in the language. `type` aliases cannot do this; a
second `type` with the same name is always an error. The main real use is
**augmenting a type you don't own**: adding a custom property to the global
`Window`, to an Express `Request`, or to a third-party library's options interface,
by declaring the same interface name again in your own project rather than editing
(or forking) the library's source.

```ts
interface Box { width: number }
interface Box { height: number }   // merges — Box now needs both
```

**Q: `extends` vs intersection (`&`)?**
Interface `extends` is interface inheritance and can extend multiple base
interfaces at once (`interface C extends A, B {}`); it reads as "this interface
*is-a* combination of those." Intersection `A & B` is the `type`-level operator that
combines any two (or more) types — not just interfaces — into a single type
requiring the members of all of them; it's the composition counterpart to a union's
"one of." In practice they often produce equivalent results for plain object shapes,
but `extends` is interface-only syntax while `&` works with any `type`, which makes
it the tool of choice for composing an interface with a plain `type` alias (as in
`Entity & WithAudit`) or for building a type from other computed types.

**Q: When would you avoid classes in TypeScript entirely?** *nuance*
Much idiomatic TypeScript — especially in React/Redux-style codebases — models data
as plain typed objects (`interface`/`type`) plus standalone functions, closer to
JavaScript's natural grain: no `this`-binding pitfalls, objects are trivially
spreadable/serializable/cloneable/diffable, and testing needs no fake class, just a
plain literal (courtesy of structural typing). Reach for classes specifically when
you want **encapsulated mutable state with real invariants**, inheritance-based
polymorphism (Template Method, Strategy — see the software-design track), or
framework-managed lifecycles/dependency injection (Angular services, NestJS
providers), where the class's identity and lifecycle genuinely matter, not just its
data.

**Q: How does `noUncheckedIndexedAccess` change index and array access?** ⭐
With it on, an indexed read — `obj[key]` on an index-signature object, or `arr[i]`
on an array — is typed `T | undefined` instead of just `T`, because the compiler
can't statically know a given key or index actually exists (the key might not be
present, the index might be out of bounds). This forces you to explicitly handle
the "missing" case — an `if`/`??`/optional chain, or a non-null assertion (`!`) when
you're certain — everywhere you do a dynamic lookup. Without it, TS optimistically
assumes every indexed access succeeds, which is a real gap: reading `scores["nobody"]`
on a `{ [k: string]: number }` type would otherwise silently be typed `number`, even
though at runtime it's `undefined`. It's worth the extra `undefined` handling for the
bugs it prevents, and this track enables it.

**Q: If a class `implements` an interface but never assigns a required field, when
does TypeScript catch it?** *nuance*
At the class declaration itself, not at instantiation — `class Circle implements
Shape` is checked immediately against `Shape`'s members; if `Circle` is missing an
`area()` method (or has one with an incompatible signature), the error is reported
right there on the `class` line, before anyone ever calls `new Circle()`. This is
different from a plain object literal assigned to an interface type, where the
check happens at that specific assignment — but for a class, `implements` triggers a
one-time structural check against the whole class body.
