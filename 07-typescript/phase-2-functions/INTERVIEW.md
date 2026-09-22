<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects classes ➡](../phase-3-objects-classes/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions & Everyday Types: Interview Q&A

⭐ = asked constantly.

**Q: Optional param vs default param vs `x: T | undefined`?** ⭐
An optional parameter (`x?: T`) may be omitted entirely by the caller and is typed
`T | undefined` *inside* the function body — you still have to handle the
`undefined` case yourself. A default parameter (`x = v`) also lets callers omit the
argument, but substitutes `v` when they do, so inside the body the parameter is
always exactly `T` — never `undefined` — and its type can be inferred from the
default's type if you don't annotate it. `x: T | undefined` (no `?`) is different
from both: the caller **must** pass an argument, even if that argument is
`undefined` — it isn't optional, it's a required parameter whose type happens to
include `undefined`.

**Q: Why does `void` matter for callback types?** ⭐
Because `void` as a return type means "the caller ignores whatever this returns,"
not "this must return `undefined`." A parameter typed `(x: T) => void` will accept a
callback that actually returns a value — the value is just not usable at the call
site. That's the entire reason `arr.forEach(x => arr2.push(x))` type-checks even
though `Array.prototype.push` returns a `number`: the `forEach` callback type is
`void`-returning, so a function that happens to return a number still satisfies it.
If `void` meant "must return `undefined`," a huge fraction of idiomatic
higher-order-function code in the standard library and ecosystem would fail to
compile.

**Q: What does `as const` do, and what's its most valuable use case?** ⭐⭐
It tells TS to infer the **narrowest possible type** for an expression instead of
the normal widened one: string/number/boolean literals stay exactly as written
(rather than widening to `string`/`number`/`boolean`), and objects/arrays become
deeply `readonly`. Its most valuable use is **deriving a union type from a runtime
array or object** — `const ROLES = [...] as const; type Role = typeof ROLES[number]`
— so one value serves as both the iterable runtime source of truth (for validation,
populating a UI) and the compile-time union, with zero risk of the two drifting
apart. This is effectively the idiomatic replacement for a lot of what people used
to reach for `enum` to do.

```ts
const ROLES = ["admin", "editor", "viewer"] as const;
type Role = (typeof ROLES)[number]; // "admin" | "editor" | "viewer"
```

*Follow-up: what would `type Role` be without `as const`?* Without it, `ROLES`
would be typed `string[]`, and `(typeof ROLES)[number]` would just be `string` — you
lose the literal-union precision entirely, because a plain array literal's element
type widens to the general primitive type.

**Q: Tuple vs array — what's the actual difference?** ⭐
An array (`T[]`) is variable-length with one element type applying to every
position. A tuple (`[string, number]`) is fixed-length, with each position having
its own independently declared type. Tuples model heterogeneous, fixed-shape
records — React's `useState` returning `[value, setter]` is the canonical example —
something a plain array type can't express (an array type has no notion of "the
first element is always a string and the second is always a number"). Tuple
elements can also be given names purely for documentation (`[start: number, end:
number]`), which are erased and have no runtime effect.

**Q: Is `readonly` on an array or object enforced at runtime?** ⭐
No — it's compile-time only. `readonly T[]` removes mutating methods (`push`,
`splice`, etc.) and index assignment from the *type*, so the compiler rejects code
that tries to mutate it, but the actual emitted JavaScript array is a normal,
fully-mutable array object. A type assertion back to a mutable type, or a plain-JS
caller with no type checking involved at all, can still mutate it. If you need an
actual runtime guarantee against mutation, use `Object.freeze()`, which throws (in
strict mode) on a mutation attempt — `readonly` and `Object.freeze` solve different
problems and are often used together.

**Q: Should you use `enum`? Why or why not?** ⭐⭐
Often, no. Unlike essentially every other TypeScript construct, a (non-`const`)
`enum` **emits real runtime code** — for numeric enums, a two-way lookup object
(`Status.Active === 0` and `Status[0] === "Active"` both work), which is extra
generated output most people don't expect from "just a type-ish declaration." Enums
also have some structural surprises (a numeric enum's members aren't quite plain
literal types the way string literals are). The modern default is a **string-literal
union** or an **`as const` object with a derived union type**: zero extra runtime
footprint beyond a plain object if you use one, trivial to narrow with `typeof`/
`switch`, and it serializes to JSON exactly as written (no ambiguity about whether a
stored `0` means `Active`). `enum` is still a reasonable choice when you specifically
want a named runtime namespace of related constants — it's not that it's "wrong," but
it's no longer the automatic first choice.

**Q: When do you need function overloads instead of a union parameter?**
When a function's *return type* depends on which specific argument shape was passed
in a way a single union signature can't express uniformly — the canonical example
is the DOM's `document.createElement`, which returns a different concrete element
type per tag-name string literal. You declare several overload signatures (the
public, callable shapes) above one broader implementation signature (which actually
runs but isn't itself directly callable). For most application code, prefer a union
parameter or a generic first — overloads are verbose, easy to get subtly wrong (an
implementation signature that doesn't actually satisfy every overload it claims to),
and usually a union or generic communicates the same contract more simply.

**Q: How does contextual typing help with callbacks?**
When you write a function expression somewhere TypeScript already knows the
expected function type — assigning to a variable with a declared function type, or
passing an argument to a parameter with a known function-type signature — TS infers
the callback's parameter types *from that context* rather than requiring you to
annotate them again. That's why `const mul: BinaryOp = (a, b) => a * b` doesn't need
`a: number, b: number` spelled out: the surrounding `BinaryOp` type already commits
to those types, and inference flows inward from the expected type into the
expression.

**Q: How do you type `this` in a function, and why can't arrow functions do it?**
⭐ *nuance*
Declare a fake first parameter literally named `this: SomeType` in a regular
`function` declaration or expression — it's erased at compile time, never a real
argument at the call site, and exists purely to constrain what receiver the function
may legally be called with (`this: HTMLButtonElement` for a DOM event handler, for
instance). Arrow functions can't do this because they have no `this` binding of
their own at all — they always capture the lexical `this` from their enclosing
scope, which is exactly the mechanism developers reach for to *avoid* the classic
JS `this`-rebinding footguns (detaching a method and passing it as a callback,
losing its receiver) covered in the JavaScript track.

**Q: What's the practical difference between `readonly T[]` and a tuple for
modeling fixed data, and when would you reach for each?** *nuance*
`readonly T[]` is still variable-length and uniformly typed — it just additionally
forbids mutation. A tuple is fixed-length with per-position types, which is a
stronger, different kind of constraint: it's not about mutability, it's about shape.
You'd use `readonly T[]` for "a list I don't want mutated, of unknown/variable
length" (e.g. a config list passed down through props) and a tuple for "exactly N
values, each with its own meaning and type" (a coordinate pair, a `[value, error]`
result pair, `useState`'s return). The two compose — `readonly [string, number]` is
a readonly tuple.
