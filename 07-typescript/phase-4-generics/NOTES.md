<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects classes](../phase-3-objects-classes/NOTES.md) | [Phase 5 · advanced types ➡](../phase-5-advanced-types/NOTES.md)
<!-- /nav -->

# Phase 4 — Generics: Notes

Generics let a function, interface, or class work over *many* types while keeping
the relationships between its inputs and outputs precise. The core idea is familiar
from Java's `<T>` — but TypeScript generics are not erased to a raw `Object`-like
base, are inferred at the call site far more aggressively, and gain `keyof` and
indexed-access power that has no Java equivalent at all.

## 4.1 — Generic functions and inference

A type parameter (conventionally `T`, `U`, `K`, `V`, `E`) is a placeholder that gets
bound to a concrete type at the **call site**, and the compiler tracks it precisely
through the rest of the signature.

- `identity<T>(x: T): T` returns exactly the type it received — the caller's type
  flows straight through, with no widening to a base type.
- **Inference is the default.** TS infers `T` from the arguments passed, so
  `identity(3)` — not `identity<number>(3)` — is the normal way to call a generic
  function. You supply explicit type arguments only when there's nothing to infer
  from, or when inference would pick something wider than you want.
- Multiple type parameters let you relate an input's type to an output's type, which
  is exactly `Array.prototype.map`'s real signature shape.

```ts
function identity<T>(x: T): T {
  return x;
}
const three = identity(3);     // T inferred as number
const word = identity("hi");   // T inferred as string
console.assert(three === 3 && word === "hi", "generic identity infers T");

function firstOf<T>(xs: readonly T[]): T | undefined {
  return xs[0];
}
const f = firstOf([10, 20, 30]);   // number | undefined
console.assert(f === 10, "generic array element type preserved");

function mapArr<T, U>(xs: readonly T[], fn: (x: T) => U): U[] {
  return xs.map(fn);
}
const lengths = mapArr(["a", "bb", "ccc"], (s) => s.length);   // number[]
console.assert(lengths.join(",") === "1,2,3", "generic map T->U");
```

In this example: `identity(3)` never mentions `T` explicitly, yet `three` is typed
`number` — TS reads the argument's type and binds `T = number` for that call, then
substitutes it into the return type. `mapArr` has two type parameters: `T` (the
input element type, inferred from `xs`) and `U` (the output element type, inferred
from what `fn` returns). Passing `(s) => s.length` infers `U = number`, so `lengths`
comes out typed `number[]` — the *relationship* between input and output element
types is preserved, not just each independently guessed.

### Why it's useful

Generics let you write one implementation that works for every type while the
compiler still enforces the exact same guarantees it would for a hand-written,
type-specific version — no casts, no `Object`/`any` fallback, no risk of a caller
putting a `string` into a container built for `number`. This is the difference
between "type-safe reuse" and the alternative of duplicating the function per type
or reaching for `any` and losing safety altogether.

### Summary

- A type parameter is bound at the call site and tracked precisely by the compiler.
- Inference is the norm — explicit `<T>` is the exception, not the rule.
- Multiple type parameters relate an input's type to an output's type.
- No erasure to a base type: `Stack<string>.pop()` really is `string | undefined`,
  never `Object`.

## 4.2 — Constraints, `keyof`, and indexed access

- **Constraints** (`T extends SomeShape`) restrict what `T` can be, so the compiler
  will let you use `SomeShape`'s members inside the generic body. This is the direct
  analogue of Java's `<T extends Comparable<T>>`.
- **`keyof T`** produces the **union of `T`'s own property keys**, as string (or
  symbol/number) literal types — `keyof { a: 1; b: 2 }` is `"a" | "b"`. There is no
  Java equivalent; it's a genuinely type-level operation with no reflection-based
  analogue in a statically compiled language.
- **Indexed access `T[K]`** is the type of the property at key `K` on `T`. Combined
  with a `keyof`-constrained type parameter, `T[K]` gives you the **exact** value
  type for a *specific* key — not a widened union of all possible value types.

```ts
function longest<T extends { length: number }>(a: T, b: T): T {
  return a.length >= b.length ? a : b;
}
console.assert(longest("aa", "bbb") === "bbb", "constrained generic (string has length)");
console.assert(longest([1], [1, 2]).length === 2, "constrained generic (array has length)");
// @ts-expect-error — number has no `length`, so it fails the constraint
longest(1, 2);

function getProp<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}
const person = { name: "Ada", age: 36 };
const nm: string = getProp(person, "name");   // T[K] = string
const ag: number = getProp(person, "age");    // T[K] = number
console.assert(nm === "Ada" && ag === 36, "keyof + indexed access give exact value type");
// @ts-expect-error — "email" is not a key of person
getProp(person, "email");
```

In this example: `longest`'s constraint `T extends { length: number }` accepts any
type with a `.length` (strings, arrays, and any custom object that has one), and
rejects `number`, which has no `.length` at all — this is caught at the call site,
before the function even runs. `getProp<T, K extends keyof T>` ties the second type
parameter `K` to the *keys* of the first, `T` — so `getProp(person, "name")`
resolves `K` to the literal `"name"`, and `T[K]` (indexed access) evaluates to
`string`, the exact type of `person.name`. Calling `getProp(person, "email")` fails
because `"email"` isn't in `keyof typeof person` (`"name" | "age"`) — a typo in the
key argument is a compile error, not a runtime `undefined`.

### Why it's useful

Constraints let you write a generic that's still allowed to *use* the shape it needs
internally, instead of being limited to operations valid for literally any type.
`keyof`/indexed access together let a function like `getProp` be both fully generic
*and* return the precisely correct type per call — this precision (tying a key
parameter to its exact corresponding value type) is a distinctive TypeScript
capability well beyond what Java's generics or reflection-based property access
can express statically.

### Summary

- `T extends Shape` constrains a type parameter so you can use `Shape`'s members.
- `keyof T` is the union of `T`'s keys as literal types.
- `T[K]` (indexed access) is the type of the value at key `K`.
- `K extends keyof T` plus `T[K]` as a return type gives you the exact value type
  for whichever specific key was passed — precision with no Java analogue.

## 4.3 — Generic interfaces, classes, and default type parameters

- **Generic interfaces and classes** parameterize an entire contract:
  `interface Result<T, E>`, `class Stack<T>`, the built-in `Map<K, V>`. The type
  parameter threads through every member that needs it.
- **Default type parameters** (`<T, E = Error>`) let callers omit trailing type
  arguments, exactly like a default value parameter but operating at the type level.
- **Variance** in TypeScript is checked **structurally**, with no explicit
  wildcard syntax like Java's `? extends`/`? super` — you generally never annotate
  variance yourself; the compiler figures out whether a `Container<Dog>` is
  assignable to a `Container<Animal>`-shaped slot from the actual member types
  involved. (`in`/`out` variance annotations exist for edge cases but are rarely
  needed in application code.)

```ts
interface ApiResult<T, E = Error> {
  ok: boolean;
  data?: T;
  error?: E;
}
const good: ApiResult<number> = { ok: true, data: 42 };   // E defaults to Error
console.assert(good.data === 42, "generic interface with default type param");

class Stack<T> {
  private items: T[] = [];
  push(x: T): void {
    this.items.push(x);
  }
  pop(): T | undefined {
    return this.items.pop();
  }
  get size(): number {
    return this.items.length;
  }
}
const s = new Stack<string>();
s.push("a");
s.push("b");
console.assert(s.pop() === "b" && s.size === 1, "generic class Stack<string>");
```

In this example: `ApiResult<number>` supplies only the first type argument; `E`
falls back to its default, `Error`, so `error?: E` is `Error | undefined` without
you writing `ApiResult<number, Error>` explicitly. `Stack<T>` is a fully generic
class — `new Stack<string>()` fixes `T = string` for that instance, so `s.pop()` is
typed `string | undefined` and `s.push(42)` would be a compile error, with the
compiler enforcing the container's element type exactly as a Java `Stack<String>`
would, but without ever falling back to an unchecked `Object`-based implementation
underneath.

### Why it's useful

Parameterizing an entire interface or class (rather than just one function) is how
you build reusable containers, wrappers, and repositories that stay fully typed per
usage — a `Stack<string>` and a `Stack<Book>` share one implementation with zero
duplicated code and zero loss of type safety in either direction. Default type
parameters reduce boilerplate at call sites for the common case (most `Result<T>`
usages want the ordinary `Error` type) while still allowing an override
(`Result<T, MyCustomError>`) when needed.

### Summary

- Generic interfaces/classes parameterize an entire contract, not just one function.
- Default type parameters (`<T, E = Error>`) must be trailing and let callers omit
  the common case.
- Variance is checked structurally; there's no Java-style wildcard syntax to write.

## 4.4 — Practical generic patterns: `Result<T, E>` and a generic repository

### `Result<T, E>` — modelling failure without exceptions

`Result` is a generic **discriminated union** (a preview of Phase 5) that
represents "success with a value" or "failure with an error," fully typed, without
using `throw`/`catch` at all — the same idea as Rust's `Result` or a functional
`Either`.

```ts
type Result<T, E = string> = { ok: true; value: T } | { ok: false; error: E };

function ok<T>(value: T): Result<T, never> {
  return { ok: true, value };
}
function err<E>(error: E): Result<never, E> {
  return { ok: false, error };
}

function parseIntSafe(s: string): Result<number> {
  const n = Number(s);
  return Number.isInteger(n) ? ok(n) : err(`not an integer: "${s}"`);
}
const r1 = parseIntSafe("42");
const r2 = parseIntSafe("x");
// The discriminant `ok` narrows which arm's fields are available:
console.assert(r1.ok && r1.value === 42, "Result success arm");
console.assert(!r2.ok && r2.error.startsWith("not an integer"), "Result failure arm");
```

In this example: `Result<T, E>` is generic over both the success value's type and
the error's type, defaulting `E` to `string`. `ok`/`err` are small generic helper
constructors — `ok`'s return type `Result<T, never>` says "this can never be the
error arm," which is exactly why TypeScript lets you build a `Result<number>` (with
`E` defaulted to `string`) out of an `ok(n)` call whose own error type is `never`
(a `never` fits into any union arm). Checking `r1.ok`/`r2.ok` is what narrows —
reading `r1.value` is only legal once `r1.ok` has been checked truthy, and reading
`r2.error` only once `r2.ok` is checked falsy; this is Phase 1's discriminant-driven
narrowing, applied to a generic type.

### A generic in-memory repository

```ts
interface HasId {
  id: string;
}
class Repository<T extends HasId> {
  private store = new Map<string, T>();
  save(entity: T): void {
    this.store.set(entity.id, entity);
  }
  findById(id: string): T | undefined {
    return this.store.get(id);
  }
  all(): T[] {
    return [...this.store.values()];
  }
}
interface Book extends HasId {
  title: string;
}
const repo = new Repository<Book>();
repo.save({ id: "b1", title: "TS" });
repo.save({ id: "b2", title: "JS" });
console.assert(repo.findById("b1")?.title === "TS", "generic repository find");
console.assert(repo.all().length === 2, "generic repository all");
```

In this example: `Repository<T extends HasId>` is constrained so its implementation
can rely on every stored entity having an `id: string` (used as the `Map` key),
while still being generic over what *else* the entity looks like. `Repository<Book>`
fixes `T = Book`, so `findById` returns `Book | undefined` with `.title` available,
and passing an object without an `id` to `save` would fail to satisfy `T extends
HasId` and be rejected at compile time. This mirrors a typical Spring Data-style
repository interface, except here the element type is a real generic checked by the
compiler end-to-end, not `Object` with casts.

### Why it's useful

`Result<T, E>` makes failure an explicit, typed part of a function's return value
instead of an invisible possibility hidden behind `throw` — callers are compelled by
the type system to check `.ok` before touching `.value`, which eliminates an entire
class of "forgot to handle the error path" bug (expanded on in Phase 7). A generic
repository is the standard shape for "one storage implementation, many entity
types," giving you compiler-checked CRUD operations per entity type with zero
duplicated code.

### Summary

- `Result<T, E>` is a generic discriminated union for typed, explicit
  success/failure — an alternative to exceptions for expected, recoverable failures.
- A constrained generic repository (`Repository<T extends HasId>`) reuses one
  implementation across many entity types while keeping full type safety per entity.

## Generics vs Java — the deltas to state in interviews

- **No erasure to a base type.** TS erases type *annotations* like everything else,
  but the compiler tracks `T` precisely throughout type-checking — `Stack<string>
  .pop()` is `string | undefined`, never `Object`, and there's no unchecked cast
  anywhere in the picture. Java's generics are erased to their bound (often
  `Object`) at the bytecode level, which is why `List<String>.get(0)` involves an
  implicit cast the JVM inserts for you.
- **Inference-first.** TS call sites almost never write explicit type arguments;
  Java requires them far more often (though `var`/diamond inference has narrowed
  this gap somewhat in modern Java).
- **`keyof` + `T[K]` + mapped/conditional types (Phase 5)** turn TS generics into a
  small, genuinely functional type-level language — capabilities with no Java
  parallel at all.
- **No wildcards.** There's no `? extends T`/`? super T` syntax; assignability
  between generic instantiations is determined structurally by the compiler.

## Perspective

Generics are how you write reusable, type-preserving code: containers, higher-order
functions, repositories, and result wrappers. The habit to build is **let inference
do the work** at call sites, and reach for **constraints plus `keyof`/`T[K]`** to
keep the exact relationship between inputs and outputs precise — that precision is
exactly what makes the type-level toolkit in Phase 5 (mapped types, conditional
types, utility types) possible.
