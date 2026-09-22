<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects classes ➡](../phase-3-objects-classes/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions & Everyday Types: Notes

Functions are where types earn their keep on a daily basis: parameters and return
types are the contract the compiler checks at every call site, so a function's
signature is effectively documentation that can't go stale. This phase also covers
the "everyday" data types — arrays, tuples, and the `readonly`/`as const` modifiers
— that functions constantly traffic in, plus enums and overloads.

## 2.1 — Function types, parameters, and contextual typing

A function type describes both its parameters and its return value. TypeScript
usually infers the return type from the body, so most of the annotation burden falls
on the parameters.

- **Annotate parameters; let the return type infer.** Parameters have no
  initializer for TS to infer from, so they need explicit types. Return types are
  inferred from what the function body actually returns; annotate the return
  explicitly only on public APIs (to lock the contract so an internal change can't
  silently widen it) or recursive functions (where TS sometimes can't infer without
  help).
- **A function-type alias** (a "call signature") names a function shape, ideal for
  callback parameters and higher-order functions: `type BinaryOp = (a: number, b:
  number) => number`.
- **Contextual typing.** When a function expression is assigned somewhere its
  expected type is already known (a variable of a function type, a callback
  parameter), TS infers the parameter types *from that context* — you write `(a, b)
  => a * b` without annotating `a`/`b` at all, because the surrounding type already
  says they're `number`.

```ts
const add = (a: number, b: number): number => a + b;
console.assert(add(2, 3) === 5, "typed function");

type BinaryOp = (a: number, b: number) => number;
const mul: BinaryOp = (a, b) => a * b;   // a, b inferred as number from the alias
console.assert(mul(3, 4) === 12, "function-type alias infers param types (contextual typing)");
```

In this example: `add` is fully annotated by hand. `mul` is assigned to a variable
declared `: BinaryOp`, so TypeScript already knows the expected shape and infers
`a` and `b` as `number` inside the arrow function without you writing it again —
that's contextual typing doing the work for you.

### Optional, default, and rest parameters

- **Optional (`?`)** parameters may be omitted by the caller and are typed `T |
  undefined` *inside* the function body. Optional parameters must come after all
  required parameters.
- **Default (`= value`)** parameters let the caller omit the argument, in which case
  the default is substituted — so inside the body the parameter is *never*
  `undefined` (it's exactly `T`, not `T | undefined`), and its type is inferred from
  the default value if not annotated explicitly.
- **Rest (`...xs: T[]`)** parameters collect any number of trailing arguments into a
  typed array.

```ts
function greet(name: string, title?: string): string {
  return title ? `${title} ${name}` : name;
}
console.assert(greet("Ada") === "Ada", "optional omitted");
console.assert(greet("Ada", "Dr.") === "Dr. Ada", "optional provided");

function pow(base: number, exp = 2): number {   // exp: number, inferred from default
  return base ** exp;
}
console.assert(pow(5) === 25, "default param");

function sum(...xs: number[]): number {
  return xs.reduce((a, b) => a + b, 0);
}
console.assert(sum(1, 2, 3, 4) === 10, "rest params");
```

In this example: `greet`'s `title` is optional, so `greet("Ada")` compiles and
`title` is `undefined` inside the function; the ternary handles that case
explicitly. `pow`'s `exp` has a default of `2`, so `pow(5)` compiles with `exp`
substituted to `2` — note that unlike `title?`, `exp` inside the body is always a
`number`, never `undefined`. `sum` accepts any number of arguments after none,
collected into `xs: number[]`.

| | Optional `x?: T` | Default `x = v` | `x: T \| undefined` |
|---|---|---|---|
| Caller may omit it | Yes | Yes | No — must pass something |
| Type inside the body | `T \| undefined` | `T` (never undefined) | `T \| undefined` |
| Needs explicit type annotation | Usually yes | Optional — inferred from `v` | Yes |
| Must be trailing | Yes (after required params) | No, but conventionally last | No |

### `void` return vs `undefined`

`void` as a return type means "the caller should ignore whatever this returns" — it
is subtly different from saying the function returns `undefined`. A callback
parameter typed to return `void` may actually return a value; the caller simply
isn't allowed to use it.

```ts
const logs: string[] = [];
function each<T>(xs: T[], fn: (x: T) => void): void {
  for (const x of xs) fn(x);
}
each([1, 2, 3], (x) => logs.push(String(x)));   // push returns number; void ignores it
console.assert(logs.length === 3, "void return ignores the callback's result");
```

In this example: `each`'s callback parameter is typed `(x: T) => void`, but the
callback passed, `(x) => logs.push(String(x))`, actually returns a `number` (what
`Array.prototype.push` returns). This still type-checks, because `void` means "I
won't use the return value," not "you must return `undefined`." This deliberate
rule is exactly why idioms like `arr.forEach(x => arr2.push(x))` compile throughout
the standard library.

### Why it's useful

Precise parameter types catch the majority of "called a function with the wrong
argument" bugs before the code ever runs, and contextual typing means callback-heavy
code (array methods, event handlers, promise chains) stays terse without sacrificing
safety. The `void`-vs-`undefined` distinction specifically exists so that
higher-order functions can accept callbacks written for other purposes (like `push`,
which has a useful return value elsewhere) without forcing every callback to
explicitly discard its result.

### Summary

- Annotate parameters (required); let return types infer except on public APIs.
- `x?: T` is `T | undefined` inside the body and may be omitted by the caller.
- `x = v` is always `T` inside the body (never undefined) and may be omitted.
- `void` means "return value ignored," not "must return undefined" — this is what
  lets callbacks that happen to return something still satisfy a `void` callback type.

## 2.2 — `as const` and deriving unions from data

`as const` tells TypeScript to infer the **narrowest possible, deeply `readonly`**
type for an expression, instead of the normal "widened" type it would otherwise
infer.

- Without `as const`, an object literal's string properties widen to `string`
  (because you're presumably going to reassign them later); `as const` freezes them
  to their exact literal type and marks every property `readonly`.
- On an array, `as const` produces a **readonly tuple of literal types** rather than
  a mutable `T[]`.
- The killer application: **deriving a union type from a runtime array**, so one
  value is the single source of truth for both the iterable runtime data and the
  compile-time union — no risk of the two drifting apart.

```ts
const cfgWide = { role: "admin" };          // role: string (widened)
const cfgConst = { role: "admin" } as const; // role: "admin" (readonly, literal)
cfgWide.role = "user";                      // allowed — widened to string
console.assert(cfgConst.role === "admin", "as const pins the literal");
// @ts-expect-error — as const made every property readonly
cfgConst.role = "user";

const ROLES = ["admin", "editor", "viewer"] as const;
type Role = (typeof ROLES)[number];   // "admin" | "editor" | "viewer"
const r: Role = "editor";
// @ts-expect-error — "root" isn't in the derived union
const badRole: Role = "root";
console.assert(ROLES.includes(r), "union derived from a const array");
```

In this example: `cfgWide.role` is `string`, so reassigning it to any string is
fine; `cfgConst.role` is the literal type `"admin"` and `readonly`, so reassignment
is a compile error. `ROLES` is a `readonly ["admin", "editor", "viewer"]` tuple
thanks to `as const`; `(typeof ROLES)[number]` indexes that tuple type by `number`
(meaning "any valid index"), which yields the union of its element types —
`"admin" | "editor" | "viewer"`. Now `ROLES` is simultaneously the runtime array you
iterate/`.includes()` against *and* the source of the `Role` type; change `ROLES`
and `Role` updates automatically.

### Why it's useful

`as const` + `typeof ROLES[number]` is the idiomatic modern replacement for many
`enum` uses (2.3): it gives you a real, iterable runtime array (useful for populating
a `<select>`, validating input with `.includes()`, etc.) and a precise compile-time
union derived from the *same* value, so there is exactly one place to update when the
set of allowed values changes.

### Summary

- `as const` infers the narrowest literal type and makes the value deeply readonly.
- On an array it produces a readonly tuple of literals, not a mutable array.
- `typeof arr[number]` turns a `const`-asserted array into a union type.
- This is the standard way to derive a compile-time union from one runtime source of
  truth, avoiding duplication between a list of values and a type.

## 2.3 — Arrays, tuples, `readonly`, and enums

### Arrays and tuples

- `T[]` (equivalent to `Array<T>`) is a variable-length array where every element
  has the same type. Nested arrays are `T[][]`.
- A **tuple** `[string, number]` is a *fixed-length*, **position-typed** array —
  each index has its own declared type. Java has no first-class tuple type; the
  closest equivalents are an ad hoc small class or `Map.Entry`.
- Tuple elements can be **named** for documentation (`[start: number, end: number]`)
  — purely a readability aid, erased like everything else — and can include a rest
  element for variable-length tails.

```ts
const nums: number[] = [1, 2, 3];
const grid: number[][] = [[1], [2, 3]];

let pair: [string, number] = ["age", 30];
pair = ["height", 180];
console.assert(pair[0] === "height", "tuple positions are typed");
// @ts-expect-error — wrong element type at position 1
pair = ["x", "y"];

type Span = [start: number, end: number];   // named tuple, e.g. useState-style
const span: Span = [0, 10];
console.assert(span[1] - span[0] === 10, "named tuple");
```

In this example: `pair` must always be a two-element array whose first element is a
`string` and second is a `number` — `["x", "y"]` fails because `"y"` isn't a
`number`. `Span` names its two positions `start` and `end` purely for documentation;
at runtime `span` is still just a plain two-element array. Tuples like this model
React's `useState` return value (`[value, setter]`) precisely.

### `readonly` arrays

`readonly T[]` (or `ReadonlyArray<T>`) removes every mutating method (`push`, `pop`,
`splice`, index assignment, etc.) from the type — but this is enforced **at compile
time only**.

```ts
const frozen: readonly number[] = [1, 2, 3];
console.assert(frozen.length === 3, "readonly array blocks mutation at compile time");
// @ts-expect-error — push doesn't exist on a readonly array's type
frozen.push(4);
```

The emitted JavaScript array is still a fully mutable array object — `readonly` is
purely a TypeScript-level restriction on what the *type* allows you to call. A cast
back to a mutable array type, or a plain-JS caller with no type checking at all,
can still mutate the underlying array. If you need a genuine runtime guarantee, use
`Object.freeze` (which does throw, in strict mode, on a mutation attempt).

### Enums vs the union/const-object alternative

`enum` is unusual among TypeScript features: almost everything else erases
completely, but a (non-`const`) `enum` **emits actual runtime code** — a two-way
lookup object for numeric enums.

```ts
enum Status {
  Active,
  Suspended,
}
console.assert(Status.Active === 0, "numeric enum is 0-indexed");

// Preferred modern alternative — a const object + derived union, zero runtime emit
// beyond the plain object literal itself:
const Level = { Low: "low", High: "high" } as const;
type Level = (typeof Level)[keyof typeof Level];   // "low" | "high"
const lvl: Level = "high";
console.assert(lvl === "high", "union-as-enum alternative");
```

In this example: `Status` is a numeric enum; `Status.Active` is `0` and
`Status.Suspended` is `1`, and TypeScript also generates a reverse mapping
(`Status[0] === "Active"`) — real, non-trivial runtime output for what looks like a
simple declaration. The `Level` alternative declares a plain object with `as const`
(so its values stay literal, not widened to `string`), then derives a `Level` type
by indexing that object's value type with `keyof typeof Level` (the union of its
keys) — yielding `"low" | "high"`. Note `Level` is declared twice: once as a `const`
(the runtime value) and once as a `type` (the compile-time type) — this is legal
because value and type names live in separate namespaces.

| | Numeric/string `enum` | `as const` object + derived union |
|---|---|---|
| Runtime cost | Emits a real object (and reverse map for numeric) | Just the plain object literal — no extra emit |
| Structural narrowing | Awkward — enum members aren't plain literal types in the same way | Narrows exactly like any string-literal union |
| JSON-friendly | Numeric enums serialize as numbers, easy to misread | Values are plain literals — obvious in JSON |
| Extra runtime feature | Reverse mapping (numeric enums only) | None — it's just an object |
| Modern guidance | Use sparingly, when you want a namespaced runtime value | Preferred default for "one of a fixed set" |

### Why it's useful

Tuples let you model fixed-shape heterogeneous data (a coordinate pair, a
`[value, setter]` result) with full positional type safety that a plain array can't
express. `readonly` documents and enforces (at compile time) an intent not to
mutate, cheaply preventing an entire class of aliasing bug. Preferring string-literal
unions over enums avoids the extra runtime object enums generate, narrows more
naturally with `typeof`/`switch`, and serializes to JSON exactly as written.

### Summary

- `T[]` is variable-length and uniform; a tuple is fixed-length and per-position typed.
- `readonly T[]` blocks mutating methods at compile time only — not a runtime guarantee.
- Enums are the one TS feature that emits real runtime code; modern guidance leans
  toward string-literal unions or `as const` objects instead.
- `as const` + `typeof obj[keyof typeof obj]` is the standard "enum without an enum."

## 2.4 — Function overloads and `this` typing

### Overload signatures

Overloads let a single function name present several distinct, non-uniform typed
call shapes. You declare multiple **overload signatures**, then one broader
**implementation signature** that covers all of them — the implementation signature
itself is not directly callable from outside.

```ts
function len(x: string): number;
function len(x: unknown[]): number;
function len(x: string | unknown[]): number {
  return x.length;
}
console.assert(len("hello") === 5, "overload: string");
console.assert(len([1, 2, 3]) === 3, "overload: array");
```

In this example: callers only ever see the two overload signatures — `len(x:
string)` and `len(x: unknown[])` — each returning `number`. The third `function
len(x: string | unknown[])` line is the implementation; it's what actually runs, but
it is invisible to call-site type checking, which resolves against the two
overloads above it. In practice, a single union-parameter signature is often just as
clear as (and simpler than) overloads — reach for overloads only when the *return
type itself* genuinely depends on which argument shape was passed in a way a union
signature can't express (e.g. `document.createElement("div")` returning
`HTMLDivElement` versus `createElement("a")` returning `HTMLAnchorElement` in the
DOM lib's own overloads).

### Typing `this`

You can constrain what `this` a (non-arrow) function expects by declaring a fake
**first parameter** literally named `this`. It's erased at compile time and never
appears as an actual argument at the call site — it only constrains how the function
may legally be called.

```ts
function handleClick(this: HTMLButtonElement, e: MouseEvent) {
  this.disabled = true;   // this: HTMLButtonElement — checked, not just assumed
}
```

Arrow functions **cannot** declare a `this` parameter — they don't have their own
`this` at all; they capture the lexical `this` from their enclosing scope, which is
usually exactly what you want to sidestep the classic JS `this`-rebinding pitfalls
(covered in the 06-javascript track).

### Why it's useful

Overloads are the right tool on the rare occasion a function's return type truly
depends on the specific shape of what was passed (library authors use this
constantly, e.g. the DOM and Node type definitions); for application code, a union
parameter or a generic almost always reads more simply. Typing `this` catches a
whole class of "called this method with the wrong receiver" bug — common with
event handlers extracted as standalone functions — entirely at compile time.

### Summary

- Overloads: several typed call shapes over one broader, non-callable implementation.
- Prefer a union parameter or a generic first; reach for overloads when the return
  type genuinely varies non-uniformly with the input shape.
- `this: T` as a fake first parameter constrains the caller's receiver; it's erased
  and never a real argument.
- Arrow functions can't type `this` — they always capture the lexical `this`.

## Perspective

Functions are the unit where TypeScript's contracts live day-to-day: precise
parameter types, inferred returns, and unions narrowed at the boundary. Two habits
carry forward through the rest of the track — **derive unions from `as const` data**
instead of hand-maintaining an enum or a duplicated list of allowed values, and
**prefer a union or a generic over an overload** unless the return type genuinely
depends on the input shape. Both keep a single source of truth and lean on inference
instead of repetition.
