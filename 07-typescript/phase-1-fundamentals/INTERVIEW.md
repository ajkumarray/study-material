<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals: Interview Q&A

⭐ = asked constantly.

**Q: What is TypeScript and how does it relate to JavaScript?** ⭐⭐
TypeScript is a statically-typed **superset** of JavaScript: every valid JS program
is (nearly) valid TS, and TS adds a compile-time type system checked by `tsc`. The
compiler does two separable jobs — type-checking and transpiling to plain JS — and
the type information is **erased** in the emitted output. At runtime, TypeScript
code *is* JavaScript; nothing about execution changes because you used TS. This is
why TS can be adopted incrementally on an existing JS codebase with zero runtime
cost, and why tools like `tsx`/`esbuild` can run `.ts` files quickly by just
stripping the annotations without doing a full type check.

*Follow-up: if types are erased, what actually catches bugs?* The type checker
catches them **at compile time**, before the code ever runs — a mismatched
argument, a typo'd property name, a missing `null` check are all compiler errors,
not runtime exceptions. The safety is a development-time guarantee, not a runtime
one.

**Q: Are types available at runtime? What does that rule out?** ⭐⭐
No. Types are erased during compilation — the emitted JavaScript contains no
interfaces, type aliases, or generic parameters. Concretely this means you cannot
`instanceof` an interface, `switch` on a `type`, or use reflection to inspect a
generic parameter's actual type argument. Any decision your program needs to make
at runtime has to be based on a runtime value: `typeof`, `in`, a literal
discriminant field on an object, or an explicit runtime validation step. This is a
sharp contrast with Java, where types are reified into the class file and
`instanceof`, reflection, and generic bounds checks all work against real runtime
type information (modulo Java's own generic erasure nuances).

```ts
interface Shape { area(): number }
function f(x: unknown) {
  // if (x instanceof Shape) {}  // compile error — Shape is a type, not a value
}
```

**Q: Structural vs nominal typing — which does TypeScript use, and why does it
matter?** ⭐⭐
TypeScript is **structural** (duck typing): an object is compatible with a type if
it has the required members, regardless of whether it was ever declared to relate
to that type. Java is **nominal**: compatibility requires a declared relationship
(`implements`, `extends`). Practically, this means in TS two completely unrelated
object literals with the same shape are freely interchangeable — a test can pass a
plain literal wherever an interface is expected, with no fake "implementing" class
needed. The downside is that two conceptually distinct types that happen to share a
representation (e.g. `UserId` and `OrderId`, both `string`) are also freely
interchangeable, which is a real source of bugs; TS can emulate nominal typing for
exactly this case with a **branded type** (Phase 7) when identity matters.

**Q: `any` vs `unknown` vs `never` — define each and say when you'd use it.** ⭐⭐
`any` disables type checking for a value entirely and is contagious — everything
derived from an `any` becomes `any` too — so it silently readmits every bug the
type system exists to prevent; avoid it. `unknown` is the safe top type: anything
is assignable *to* it, but no operation is permitted *on* it until you narrow its
type with a runtime check — this is the correct type for values whose shape you
don't yet know at compile time (`JSON.parse` results, `fetch` responses, a `catch`
variable). `never` is the bottom type, meaning no value can ever have that type — it
is the return type of a function that always throws or loops forever, and it is the
type of an empty union; it also powers exhaustiveness checks in a discriminated
union `switch` (Phase 5), because the compiler can tell you a branch is unreachable
by checking whether the remaining type is `never`.

*Follow-up: why is `unknown` described as "stricter than Java's `Object`"?*
Because in Java you can call `Object` methods (`toString()`, `equals()`,
`hashCode()`) on any `Object` reference with no cast. In TS you cannot call *any*
method or read *any* property on an `unknown` value — not even something generic —
until you narrow it to a more specific type. `unknown` offers strictly fewer
operations than Java's `Object` does.

**Q: When would you reach for `unknown` instead of `any`?** ⭐
Whenever a value's shape is genuinely not known at compile time but you still want
type safety: API responses, `JSON.parse` output, values caught in a `catch` clause
(which TS types as `unknown` under `strict`). `unknown` forces you to prove the
shape (via `typeof`, `in`, `instanceof`, or a type guard) before you can use the
value, whereas `any` lets you use it immediately and incorrectly with no compiler
complaint — the bug just surfaces later, at runtime, exactly where TS was supposed
to prevent it.

**Q: `type` alias vs `interface` — pick one and defend it.** ⭐⭐
For a plain object shape, they're interchangeable, so it comes down to convention
and capability. `interface` supports **declaration merging** (two declarations with
the same name combine — Phase 3) and reads as an extendable public contract that a
class can `implement`; that makes it the better default for object/class shapes and
for augmenting third-party library types. `type` is required for anything
`interface` can't express: unions, tuples, primitives, function types on their own,
and the mapped/conditional types from Phase 5. Common convention: `interface` for
object/class shapes, `type` for unions and computed types.

*Follow-up: can `type` be re-opened like `interface` can?* No — once a `type` alias
is declared, it's final; attempting to declare it again is a duplicate-identifier
error. Only `interface` supports merging multiple declarations of the same name.

**Q: What is narrowing, and name the mechanisms.** ⭐⭐
Narrowing is the compiler's control-flow analysis: inside a conditional branch, it
shrinks a variable's type to the subset still possible in that branch, so you can
safely use members specific to that subset. Mechanisms: `typeof` (primitives), `in`
(property-presence check on object unions), truthiness/`=== null` checks (removes
`null`/`undefined`), `instanceof` (class instances via the real prototype chain), a
shared literal discriminant field (discriminated unions, Phase 5 — the cleanest
mechanism for 3+ arm unions), and user-defined type guards (a function returning a
type predicate `x is T`). Narrowing is what makes both `unknown` and unions usable:
without it you'd need explicit casts everywhere a union or `unknown` value was used.

**Q: What is a user-defined type guard, and what does the `x is T` return type
actually do?** ⭐
It's a function whose declared return type is a **type predicate** — `arg is T` —
instead of `boolean`. At runtime it still just returns `true`/`false` like any
boolean function; the `is T` part is purely a signal to the compiler. When such a
function is called in a condition and returns `true`, the compiler narrows the
argument's static type to `T` for the rest of that branch at the call site. It's how
you encapsulate a reusable runtime shape check (e.g. validating an `unknown` value
really has the fields of `UserDto`) and get the compiler to trust it going forward.

```ts
function isCat(a: Cat | Dog): a is Cat {
  return a.kind === "cat";
}
```

**Q: What does `strict` mode actually turn on, and should you always use it?** ⭐
`strict: true` is a bundle of individually-toggleable strictness flags, most
importantly `strictNullChecks` (making `null`/`undefined` distinct types you must
handle explicitly, instead of values that silently flow into any type), plus
`noImplicitAny` (parameters/variables that would otherwise infer to `any` are
errors), `strictFunctionTypes`, `useUnknownInCatchVariables` (Phase 6), and more.
Yes — always develop with `strict: true` from day one. Retrofitting it onto a large,
loosely-typed codebase later is painful because it surfaces every place `null`
wasn't handled or a parameter was implicitly `any`, all at once.

**Q: If types are erased, what actually stops bad data at runtime?** *nuance*
Nothing automatic — the type system only checks that your code is internally
consistent with the types you declared; it trusts your annotations completely.
Nothing forces a `fetch` response, `JSON.parse` output, or environment variable to
actually match the type you annotated it as. At every trust boundary (network,
disk, user input, third-party library without types), you are responsible for
**validating at runtime** — a type guard, or better, a schema-validation library —
and only then treating the value as the parsed, verified type (Phase 7 covers this
pattern in depth). TypeScript guarantees internal consistency, not that external
reality matches your claims.

**Q: What would `console.log(typeof someTypedValue)` print, and why does that
surprise people coming from other statically-typed languages?** *nuance*
It prints the JavaScript runtime type of the value (`"object"`, `"string"`,
`"number"`, etc.) — never anything about the TypeScript static type, because that
information doesn't exist at runtime. A value typed as an `interface Point` still
reports `typeof p === "object"`, exactly the same as it would for any other plain
object. This routinely surprises people from Java, where `.getClass()` or
`instanceof` reflects the declared/actual type hierarchy — TS has no equivalent
because the whole point of erasure is that the type layer disappears.
