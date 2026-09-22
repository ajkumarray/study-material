<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — Fundamentals & the Type System: Notes

TypeScript is JavaScript plus a static type system that is checked at compile time
and **erased before runtime**. It is a strict *superset* of JavaScript: every valid
`.js` file is (almost) valid TypeScript, and the type annotations you add are purely
a developer-time safety and tooling layer. The compiler, `tsc`, does two jobs: it
**type-checks** your code against the rules you declared, and it **transpiles**
TS syntax down to plain JS that any JS engine can run. In this repo, `tsx` does the
transpile-and-run step quickly (by *stripping* types without fully checking them),
which is why `npm run typecheck` (`tsc --noEmit`) is run separately — it is the step
that actually proves the code is type-safe.

Coming from Java, the single biggest mental shift is that TypeScript's type system is
**structural, not nominal**, and that types vanish completely once compilation is
done. This phase covers that shift plus the vocabulary (`any`/`unknown`/`never`,
`type`/`interface`, unions, narrowing) that everything else in the track builds on.

## 1.1 — Type erasure: types exist only at compile time

TypeScript's type annotations, interfaces, and type aliases are a language layered
*on top of* JavaScript. When `tsc` compiles a `.ts` file, it strips every type
annotation and emits ordinary JavaScript — the runtime never sees a `Point` type, an
`interface`, or a generic parameter.

- **No runtime representation.** You cannot `instanceof` an `interface`, `switch` on
  a `type`, or reflect over a generic parameter, because none of that exists once
  compilation finishes. Java, by contrast, *reifies* types into the class file: a
  `List<String>` still has a runtime `Class` object you can inspect (modulo generic
  erasure quirks), and `instanceof MyInterface` works.
- **Runtime decisions need runtime values.** If your code needs to branch based on
  "what shape is this object," it must inspect an actual value (`typeof`, `in`, a
  literal discriminant field) — never a type.
- **Types cost nothing at runtime.** Because they disappear, there is zero
  performance or memory overhead for using an elaborate type system. This is very
  different from Java generics (bytecode-level erasure, but still verified at the
  JVM boundary) or from runtime-checked systems.

```ts
type Point = { x: number; y: number };

const p: Point = { x: 1, y: 2 };
console.log(typeof p);              // "object" — Point is invisible here
// console.log(p instanceof Point); // compile error: 'Point' only refers to a type
```

In this example: `typeof p` returns `"object"`, the same value it would return for
any plain object — there is no trace of the `Point` alias in the emitted JavaScript.
Attempting `p instanceof Point` is a compile error because `Point` is a type, not a
value, and `instanceof` needs a runtime constructor function to its right.

### Why it's useful

Erasure is what lets TypeScript be adopted incrementally on top of any existing JS
codebase or library with zero runtime cost — you get the safety net during
development and code review, and ship the exact same JavaScript you would have
written by hand. The trade-off, covered in Phase 7, is that **types cannot protect
you from bad data that enters your program from outside the type system** (network
responses, `JSON.parse`, user input) — you must validate that data at runtime.

### Summary

- Types are compile-time only; the emitted JS has no trace of them.
- You cannot `instanceof`, reflect on, or switch on a type at runtime.
- Runtime branching needs runtime values (`typeof`, `in`, a discriminant field).
- Zero runtime cost is the trade for "types don't protect you from untrusted data."

## 1.2 — Structural typing vs nominal typing

TypeScript uses **structural typing** (also called "duck typing"): a value is
compatible with a type if it has the required members, **regardless of how it was
declared**. Java uses **nominal typing**: a value is compatible with a type only if
it was declared to have that relationship (`implements Foo`, `extends Bar`).

- **Structural compatibility is about shape.** If an object has an `x: number` and a
  `y: number`, it satisfies `type Point = { x: number; y: number }` even if it was
  never told about `Point` and carries extra properties besides.
- **No `implements` is required** for a plain object to match an interface — only
  classes use `implements`, and even then it's a *check*, not the source of
  compatibility (Phase 3).
- **This is the #1 mental shift for a Java developer.** In Java, two classes with
  identical fields and methods but no shared interface are completely unrelated
  types. In TypeScript, they are interchangeable wherever that shape is expected.

```ts
type Point = { x: number; y: number };

// `named` was declared with no reference to `Point` at all.
const named = { x: 1, y: 2, label: "origin" };
const p: Point = named; // OK — has x and y; the extra `label` doesn't disqualify it

console.assert(p.x === 1 && p.y === 2, "structural typing: shape is enough");
```

In this example: `named` is inferred as `{ x: number; y: number; label: string }`.
Assigning it to `p: Point` succeeds because it has *at least* the members `Point`
requires — `x` and `y` — even though it also has `label`. A Java compiler would
reject this outright unless `named`'s class explicitly implemented a `Point`
interface.

| | Structural typing (TypeScript) | Nominal typing (Java) |
|---|---|---|
| Compatibility rule | Has the required members ("shape") | Declares the relationship (`implements`/`extends`) |
| Unrelated types with same shape | Interchangeable | Incompatible, even if identical |
| Extra members on the value | Allowed (see excess-property checks, Phase 3, for one exception) | N/A — nominal types don't compare shape |
| Enforced by | The compiler comparing member lists | The compiler checking declared inheritance |
| Simulating the other | "Branded" types fake nominal identity (Phase 7) | Reflection/duck-typing libraries fake structural checks |

### Why it's useful

Structural typing makes TypeScript extremely ergonomic for working with plain data,
mocking objects in tests (you don't need a fake class that "implements" an
interface — a literal with the right shape just works), and gradually typing
existing JavaScript. Its cost is that two conceptually different things that happen
to share a shape (a `UserId` and an `OrderId`, both `string`) are freely
interchangeable unless you deliberately opt back into nominal-style safety with a
**branded type** (Phase 7).

### Summary

- Compatibility is by shape ("duck typing"), not by declared relationship.
- A value never has to declare which types it satisfies.
- Extra properties on the value are generally fine (with one carve-out for fresh
  object literals — Phase 3's excess-property check).
- When you need true identity distinctions between same-shaped types, use a branded
  type (Phase 7), not a `type` alias alone.

## 1.3 — Inference vs annotation

TypeScript infers types wherever it reasonably can, so annotations are needed far
less often than in a language like Java where every variable declaration states its
type.

- **Inference from initializers.** `let n = 42;` gives `n` the type `number` with no
  annotation — TS reads the right-hand side.
- **Inference from return expressions.** A function's return type is inferred from
  what it actually returns, unless you annotate it explicitly.
- **Where to annotate:** function **parameters** (there's no initializer to infer
  from) and **public API boundaries** (exported functions, class members) to lock
  the contract and catch accidental signature drift. Let inference handle locals —
  over-annotating obvious values is noise that a reviewer has to read for no benefit.

```ts
let n = 42;              // inferred: number
// @ts-expect-error — a string is not assignable to the inferred `number`
n = "nope";

function double(x: number) {  // parameter annotated; return type inferred as `number`
  return x * 2;
}
```

In this example: because `n` was initialized with a number literal, TypeScript locks
its type to `number` from that point on — assigning a string later is a compile
error. `double`'s parameter `x` must be annotated (there's nothing to infer it from),
but its return type doesn't need annotating; TS works out `number` from `x * 2`.

### Why it's useful

Relying on inference keeps code terse and still fully type-safe — you get the same
guarantees with less to type and less to keep in sync when a type changes.
Annotating public boundaries anyway (even where inference would work) documents the
contract for callers and prevents an internal refactor from silently changing an
exported function's type.

### Summary

- TS infers types from initializers and return expressions.
- Annotate parameters (mandatory, nothing to infer from) and public APIs
  (recommended, locks the contract).
- Don't annotate obvious locals — let inference do the work.

## 1.4 — `any`, `unknown`, and `never`: the escape hatches and the extremes

Three special types sit at the edges of TypeScript's type system, plus `void`
(covered in Phase 2 alongside functions). Understanding the difference between them
is one of the most commonly tested interview topics.

- **`any`** — opts **out** of type checking entirely for that value. Every operation
  on an `any` is allowed, and any value assigned *from* an `any` is silently treated
  as whatever type it's assigned to. It is **contagious**: once a value is `any`,
  everything derived from it becomes `any` too, silently switching off the checker
  along the way. Treat it as a smell — `strict` mode and linters flag it for a
  reason.
- **`unknown`** — the **safe top type**. Any value is assignable *to* `unknown` (it
  can hold anything), but you can do **nothing** with an `unknown` value — not call
  a method, not read a property, not use an operator on it — until you **narrow** it
  to a more specific type. This is TypeScript's answer to Java's `Object`, but
  stricter: Java lets you call `.toString()` or `.equals()` on an `Object` with no
  cast; TS lets you do nothing with `unknown` until you prove what it is.
- **`never`** — the **bottom type**: a type with *no* possible values. It's the
  return type of a function that never returns normally (it always throws, or loops
  forever), and it's the type of an empty union. It's also the key ingredient for
  **exhaustiveness checking** in a `switch` over a discriminated union (Phase 5).

```ts
// any — silences the checker (the danger).
const dirty: any = "text";
const bad: number = dirty;   // no error — `any` accepted anywhere, no runtime check
console.log(typeof bad);     // "string" at runtime, but TS believes it's `number`!

// unknown — the safe top type.
const maybe: unknown = JSON.parse('{"x":1}');
// maybe.x;                  // compile error — no property access before narrowing
let width = 0;
if (typeof maybe === "object" && maybe !== null && "x" in maybe) {
  width = (maybe as { x: number }).x;   // safe: we proved the shape first
}
console.assert(width === 1, "unknown must be narrowed before use");

// never — a function that never returns.
function fail(msg: string): never {
  throw new Error(msg);
}
```

In this example: `dirty` is declared `any`, so assigning it to `bad: number` compiles
without complaint even though the runtime value is a string — this is exactly the
class of bug `any` reintroduces. `maybe` is `unknown`, so reading `maybe.x` directly
is rejected at compile time; only after the `typeof`/`"x" in maybe` checks (which
narrow `maybe`) is it safe to read `.x`, and even then a cast is needed because the
narrowing here isn't precise enough for the compiler to infer the exact shape on its
own. `fail` is typed `never` because every code path throws — it never produces a
value to return to the caller.

| | `any` | `unknown` | `never` |
|---|---|---|---|
| Accepts any value | Yes (unchecked) | Yes (safe) | No value can exist |
| Usable without narrowing | Yes — everything allowed | No — must narrow first | N/A — unreachable |
| Assignable to other types | Yes, to anything, unchecked | No, only after narrowing | Yes, to anything (it's the bottom type) |
| Typical use | Avoid; legacy/untyped interop only | External/untrusted data (`JSON.parse`, `catch`, `fetch`) | Functions that always throw; exhaustiveness checks; empty unions |
| Safety | Disables the type system | Preserves safety, forces proof | Represents "impossible," compiler enforces it |

### Why it's useful

`unknown` is the correct default for anything arriving from outside your program's
control — API responses, `JSON.parse` results, `catch` clause errors — because it
forces you to prove the shape before using it, closing off an entire class of
"assumed the wrong shape" runtime crash. `never` is what makes the compiler able to
tell you "you forgot to handle a new case" in a `switch`, turning a whole category of
missed-update bug into a compile error (Phase 5). `any` should be reserved for the
rare case where you are deliberately opting out (e.g. interop with an untyped
third-party module you can't yet type) — never as a default.

### Summary

- `any` disables checking — contagious and dangerous; avoid by default.
- `unknown` is the safe top type — accepts anything, permits nothing until narrowed.
- `never` is the bottom type — no values, the type of "impossible," used for
  non-returning functions and exhaustiveness checks.
- Mental model: `unknown` accepts everything and offers nothing until you narrow it;
  `never` accepts nothing and is assignable to everything; `any` breaks the rules in
  both directions, which is exactly why it's dangerous.

## 1.5 — `type` aliases vs `interface`

Both `type` and `interface` can describe the shape of an object, and for that use
case they are largely interchangeable. Each has capabilities the other doesn't.

- **`interface`** describes object/class shapes. It supports **declaration
  merging** (two `interface` declarations with the same name combine into one —
  Phase 3) and `extends` (including extending multiple interfaces). Classes
  `implement` interfaces. It reads well as a public, extendable contract.
- **`type`** can describe *anything*: object shapes (like `interface`), but also
  unions, tuples, primitives, function types, and the mapped/conditional types
  covered in Phase 5. A `type` **cannot** be re-opened once declared.
- **Rule of thumb:** use `interface` for object and class shapes you expect might be
  extended or implemented; use `type` for unions, tuples, and anything computed from
  other types.

```ts
interface User {
  readonly id: number;   // readonly = assign once
  name: string;
  email?: string;        // optional
}

const u: User = { id: 1, name: "Ada" };
// @ts-expect-error — `id` is readonly
u.id = 2;
console.assert(u.email === undefined, "optional prop is absent when omitted");

type Id = number | string;     // a union — only `type` can express this directly
type Pair = [Id, Id];          // a tuple — also `type`-only
```

In this example: `User` is an `interface` describing an object contract — the kind
of thing a class might implement or a function might accept as a parameter. `Id` is
a union of two primitive types, which has no `interface` equivalent; only `type` can
alias a union. `readonly` on `id` means the property can be set once (at creation)
and never reassigned — the compiler blocks `u.id = 2` even though nothing stops it
at runtime; `?` on `email` marks the property as optional, so it may be omitted and
is `string | undefined` inside the object.

| | `interface` | `type` |
|---|---|---|
| Object/class shapes | Yes | Yes |
| Unions (`A \| B`) | No | Yes |
| Tuples | No | Yes |
| Primitives, function types | No (function *call signatures* only, as an object shape) | Yes |
| Mapped / conditional types | No | Yes |
| Declaration merging (re-opening) | Yes | No |
| `extends` multiple | Yes | Via `&` intersection |
| Classes `implements` it | Yes | Yes (if it describes an object shape) |

### Why it's useful

Picking `interface` for anything a class might implement or a consumer might extend
gives you declaration merging for free — invaluable when augmenting a third-party
library's types (Phase 3) — while reaching for `type` whenever you need a union,
tuple, or computed type keeps the type-level toolkit (Phase 5) available without a
second thought about which keyword to reach for.

### Summary

- For a plain object shape, `interface` and `type` are interchangeable.
- Only `type` does unions, tuples, and mapped/conditional types.
- Only `interface` supports declaration merging and multiple `extends`.
- Default: `interface` for object/class contracts, `type` for everything else.

## 1.6 — Literal types and union types

TypeScript can narrow a value's type down to an **exact literal** rather than just
its general primitive type, and can express **"one of several types"** with a
union.

- **Literal types** pin a value to a specific literal — `"north"`, `42`, `true` —
  instead of the wider `string`/`number`/`boolean`. Combined with unions, a set of
  string literals replaces many uses of a Java `enum`.
- **Union types (`A | B`)** mean "a value that is one of these types." There is no
  direct Java equivalent — the closest analogue is a sealed interface hierarchy,
  but a TS union can mix primitives, object shapes, or both freely.
- A union parameter or variable **must be narrowed** (1.7) before you can use members
  specific to only one of its arms — the compiler only allows operations valid on
  *every* member of the union until you've proven which one you actually have.

```ts
type Direction = "north" | "south" | "east" | "west";  // union of string literals
const heading: Direction = "north";
// @ts-expect-error — "up" is not one of the allowed literals
const wrong: Direction = "up";

type Id = number | string;
function stringifyId(id: Id): string {
  // Must narrow before using a type-specific member:
  return typeof id === "number" ? id.toFixed(0) : id.toUpperCase();
}
console.assert(stringifyId(7) === "7", "union narrowed to number");
console.assert(stringifyId("ab") === "AB", "union narrowed to string");
```

In this example: `Direction` accepts exactly four string values — anything else,
even another valid `string`, is a compile error. `stringifyId` takes `number |
string`; inside the function, `id.toFixed` (a `number`-only method) is only legal
after the `typeof id === "number"` check proves that arm, and `id.toUpperCase`
(a `string`-only method) is only legal in the `else` branch, where the compiler has
narrowed `id` to `string` by elimination.

### Why it's useful

Literal-and-union types let you model a closed, finite set of allowed values (a
status, a direction, an HTTP method) with full compiler enforcement — a typo like
`"nrth"` is caught at compile time instead of causing a silent runtime bug — while
staying zero-cost, since (unlike an `enum`) a union of literals is erased entirely.
Unions in general are how TypeScript expresses "this could legitimately be more than
one shape," which is the foundation for discriminated unions (Phase 5), the single
most important modelling tool in applied TypeScript.

### Summary

- A literal type pins a value to one exact literal, not just its base type.
- A union (`A | B`) means "one of these"; it has no direct Java equivalent.
- You must narrow a union before using a member specific to one arm.
- A union of string literals is the modern, zero-cost replacement for many enum uses.

## 1.7 — Narrowing and type guards

TypeScript performs **control-flow analysis**: inside a conditional branch, it
tracks which members of a union are still possible and **narrows** the variable's
type accordingly for the rest of that branch. This is how unions and `unknown`
become usable in practice.

- **`typeof`** narrows for primitives: `"string"`, `"number"`, `"boolean"`,
  `"object"`, `"function"`, `"undefined"`, `"symbol"`, `"bigint"`.
- **`in`** narrows object unions by checking whether a property exists on the value.
- **Truthiness / `=== null` / `=== undefined`** narrows away `null`/`undefined` —
  essential under `strictNullChecks` (part of `strict`), where `null` is *not*
  automatically assignable to `string`, unlike plain JS where `null` silently flows
  anywhere.
- **`instanceof`** narrows by checking a real runtime prototype chain — works for
  class instances (`Error`, custom classes), not for plain object shapes.
- **User-defined type guards** — a function whose return type is a **type
  predicate** (`x is T`) teaches the compiler to narrow at the call site whenever
  that function returns `true`.
- **Discriminated unions** (Phase 5) — a shared literal field (`kind`, `status`,
  `type`) is the cleanest and most scalable narrowing mechanism once a union grows
  beyond two or three arms.

```ts
function measure(x: string | string[] | null): number {
  if (x === null) return 0;                 // narrowed away null
  if (typeof x === "string") return x.length;  // x: string here
  return x.length;                          // x: string[] here (only option left)
}
console.assert(measure(null) === 0, "null guard");
console.assert(measure("hi") === 2, "string branch");
console.assert(measure(["a", "b", "c"]) === 3, "array branch");

type Cat = { kind: "cat"; meow: () => string };
type Dog = { kind: "dog"; bark: () => string };
function isCat(a: Cat | Dog): a is Cat {     // type predicate
  return a.kind === "cat";
}
const pet: Cat | Dog = { kind: "cat", meow: () => "meow" };
console.assert(isCat(pet) && pet.meow() === "meow", "user-defined type guard narrows");
```

In this example: `measure` takes a three-member union. The first `if` eliminates
`null` by early return; after that, `x` is `string | string[]`. The second `if`
checks `typeof x === "string"`, narrowing `x` to `string` inside that branch; by
elimination, the final `return` statement sees `x` as `string[]` with no further
check needed, because those are the only two possibilities left. `isCat` is a
user-defined type guard: its return type `a is Cat` tells the compiler that wherever
`isCat(pet)` returns `true`, `pet` should be treated as `Cat` — which is exactly what
lets `pet.meow()` compile inside the `&&`.

| Narrowing mechanism | Works on | Example |
|---|---|---|
| `typeof` | Primitives | `typeof x === "string"` |
| `in` | Object unions (property presence) | `"bark" in a` |
| Truthiness / `=== null` | `null`/`undefined` removal | `if (x)` / `if (x !== null)` |
| `instanceof` | Class instances | `e instanceof Error` |
| User-defined guard | Any union, custom logic | `function isCat(a): a is Cat` |
| Discriminant field | Tagged/discriminated unions | `switch (s.kind)` (Phase 5) |

### Why it's useful

Narrowing is what makes `unknown` and unions *usable* rather than merely safe — you
write ordinary conditional logic, and the compiler rewards you with precise types in
each branch instead of requiring manual casts. Under `strictNullChecks`, this is
also what eliminates the "cannot read property of undefined" class of runtime crash
at compile time: the compiler won't let you dereference a possibly-`null` value
until you've proven, in code it can follow, that it isn't `null` in that branch.

### Summary

- Narrowing = the compiler shrinking a union's possibilities inside a conditional.
- Mechanisms: `typeof`, `in`, truthiness/null checks, `instanceof`, user-defined
  guards (`x is T`), and discriminant fields (Phase 5).
- `strictNullChecks` makes `null`/`undefined` real, separate types you must narrow
  away before use — this is the mechanism behind that safety.
- A user-defined type guard packages a runtime shape check into a reusable predicate
  the compiler understands.
