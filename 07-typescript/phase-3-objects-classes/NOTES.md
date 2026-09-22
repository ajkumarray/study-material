<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · generics ➡](../phase-4-generics/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects, Interfaces & Classes: Notes

Object shapes are the bread and butter of application types, and classes layer OOP
behavior on top of them. This is also where **structural typing** (Phase 1) has its
most surprising consequences for developers coming from a nominally-typed language
like Java — a class here satisfies an interface by shape, not by declaration.

## 3.1 — Interface members: `readonly`, optional, index signatures

- **`readonly`** members are assign-once at compile time — the closest analogue is a
  Java `final` field, except it's checked by the compiler only, never enforced at
  runtime.
- **`?` optional** members may be omitted from an object of that type and are typed
  `T | undefined` when read.
- **Index signatures** (`{ [key: string]: number }`) type an object used as a
  map/dictionary when the exact set of keys isn't known statically — any `string`
  key maps to a `number` value.

```ts
interface Product {
  readonly sku: string;
  name: string;
  price: number;
  discount?: number;
}
const book: Product = { sku: "BK-1", name: "TS in Depth", price: 30 };
// @ts-expect-error — sku is readonly
book.sku = "BK-2";
console.assert(book.discount === undefined, "optional prop absent");

interface StringScores {
  [name: string]: number;
}
const scores: StringScores = { ada: 90, alan: 85 };
scores.grace = 99;                       // any string key -> number
console.assert(scores.grace === 99, "index signature allows arbitrary keys");

// Under noUncheckedIndexedAccess (enabled in this track), a read may be missing:
const maybeScore = scores.unknownPerson; // type: number | undefined
console.assert(maybeScore === undefined, "index read may be undefined (safe)");
```

In this example: `book.sku` cannot be reassigned after creation — `readonly` blocks
it at compile time, though nothing stops a plain-JS caller from mutating it at
runtime, since (like `readonly` arrays in Phase 2) this is a compile-time-only
guarantee. `book.discount` may be omitted, and reading it when absent gives
`undefined`. `scores` accepts *any* string key, each mapping to a `number` — this is
how you type "an object used as a dictionary." With `noUncheckedIndexedAccess`
turned on (as this repo's `tsconfig.json` does), reading `scores.unknownPerson` is
typed `number | undefined` rather than just `number`, because the compiler can't
know that key actually exists — forcing you to handle the "missing key" case
explicitly, closing a real runtime hole that plain index signatures otherwise leave
open.

### Why it's useful

`readonly` documents and enforces "assign once" intent cheaply, catching accidental
reassignment during refactors. Index signatures let you type genuinely dynamic
objects (parsed JSON with unknown keys, a lookup table built at runtime) without
resorting to `any`. `noUncheckedIndexedAccess` specifically targets a very common
class of bug — assuming a map lookup or array index always succeeds — and turns it
into a compile-time reminder to handle the miss.

### Summary

- `readonly` = assign-once, compile-time only (like Java `final`, but unenforced at
  runtime).
- `?` = optional, `T | undefined` when read, may be omitted at construction.
- Index signatures type map-like objects with unknown key sets.
- `noUncheckedIndexedAccess` makes indexed reads `T | undefined`, forcing you to
  handle a missing key/out-of-bounds index — prefer `Record<K, V>` or `Map` (Phase
  5) when the key set is actually known ahead of time.

## 3.2 — Structural compatibility and excess-property checks

Assignability between object types is **structural**: an object with *more*
properties than a target type requires is still assignable to it — the extra
properties are simply ignored by that assignment. There is one deliberate exception:
**fresh object literals** are checked more strictly.

```ts
interface HasName {
  name: string;
}
function printName(x: HasName): string {
  return x.name;
}

const richer = { name: "Ada", age: 36, role: "engineer" };
console.assert(printName(richer) === "Ada", "extra props OK via a variable (structural)");

// @ts-expect-error — `nickname` is an excess property on a FRESH literal
printName({ name: "Ada", nickname: "A" });
```

In this example: `richer` has extra properties beyond `HasName`'s single `name`
field, but passing the *variable* `richer` to `printName` is fine — structural
typing only requires `HasName`'s members to be present, and `richer` has them. The
second call passes a **fresh object literal** directly as the argument, and that
literal has a `nickname` property that doesn't exist on `HasName` — TypeScript
specifically flags this as an excess-property error, even though pure structural
typing would allow it (the literal *does* have a `name: string`). This targeted
exception exists purely to catch typos (`{ colour: ... }` when the type wants
`color`) that plain structural compatibility would otherwise silently let through.
Assigning the literal to a variable first — `const x = { name: "Ada", nickname: "A"
}; printName(x);` — removes the excess-property check, because now you're passing an
already-typed variable, and ordinary structural compatibility applies again.

| | Passing a variable | Passing a fresh object literal |
|---|---|---|
| Extra properties allowed | Yes (pure structural typing) | No (excess-property check applies) |
| Purpose of the difference | Duck typing stays ergonomic | Catches typos on values with no prior type to check against |
| How to bypass on a literal | Assign to a variable first, use a type assertion, or add an index signature to the target | — |

### Why it's useful

Structural compatibility is what makes duck typing painless in practice — you don't
need adapter classes or explicit `implements` declarations to pass compatible data
around. The excess-property check on fresh literals is a narrowly-targeted safety
net specifically for the case structural typing is weakest at: a one-off object
literal with no prior type of its own, where a misspelled property name would
otherwise be silently accepted as "just an extra property."

### Summary

- Assignability is structural: extra properties on the source are fine in general.
- A **fresh object literal** passed directly is checked more strictly — unknown
  properties are flagged (the excess-property check).
- Assigning the literal to a variable first opts back into plain structural
  compatibility, silencing the check.

## 3.3 — Classes: access modifiers, `implements`, `abstract`, parameter properties

TypeScript classes are JavaScript classes (06-javascript track) with a
compile-time typing layer on top.

- **Access modifiers** `public` (default) / `protected` / `private` are
  **compile-time only** — they are erased, and a `private` field is still reachable
  via bracket-notation access (`obj["field"]`) in the emitted JavaScript, or from
  any code that ignores the type checker. For genuine runtime privacy, use
  JavaScript's native **`#private`** fields, which really are inaccessible from
  outside the class at runtime, not just flagged by the type checker.
- **Parameter properties** — `constructor(private readonly r: number)` — declare
  **and** assign a field in one line, removing the usual `this.r = r;`
  boilerplate. This is TS-only sugar with no JS equivalent syntax.
- **`implements`** checks a class against an interface **structurally** — it
  verifies the class has the required members; it does not add any members or
  behavior. A class may `implements` several interfaces at once.
- **`abstract` classes** cannot be instantiated directly and may declare `abstract`
  members that concrete subclasses must implement — the standard vehicle for the
  Template Method pattern (see the 05-software-design track).
- Under **`noImplicitOverride`** (enabled in this track), a method that overrides a
  base-class method must be marked **`override`**, which catches both accidental
  overrides and a base method's signature drifting out from under a subclass that
  meant to override it.
- **`get`/`set`** accessors present computed properties with property-access
  syntax; **`static`** members live on the class itself rather than on instances.

```ts
interface Shape {
  area(): number;
}

abstract class BaseShape implements Shape {
  abstract area(): number;
  describe(): string {
    return `area=${this.area().toFixed(2)}`;   // template method calling the abstract hook
  }
}

class Circle extends BaseShape {
  constructor(private readonly r: number) {    // parameter property: declares + assigns
    super();
  }
  override area(): number {
    return Math.PI * this.r ** 2;
  }
}

class Rect extends BaseShape {
  constructor(private readonly w: number, private readonly h: number) {
    super();
  }
  override area(): number {
    return this.w * this.h;
  }
}

const shapes: Shape[] = [new Circle(1), new Rect(2, 3)];
const totalArea = shapes.reduce((sum, s) => sum + s.area(), 0);
console.assert(Math.abs(totalArea - (Math.PI + 6)) < 1e-9, "polymorphic area via interface");
console.assert(new Rect(2, 3).describe() === "area=6.00", "abstract template method");
```

In this example: `BaseShape` `implements Shape` structurally — it just needs an
`area(): number` method somewhere in its hierarchy, which `Circle`/`Rect` supply.
`BaseShape.describe()` is a **template method**: it calls `this.area()`, whose
concrete behavior is supplied by whichever subclass is instantiated — this is
runtime polymorphism working exactly as it does in Java, since classes (unlike
plain object types) *are* real runtime constructs with prototype chains. `Circle`'s
constructor uses a **parameter property** (`private readonly r: number`), which both
declares the `r` field and assigns it from the constructor argument — no separate
`this.r = r;` line needed. Both subclasses mark `area()` with **`override`**, which
`noImplicitOverride` requires precisely because they're overriding an `abstract`
member from the base class.

```ts
class Counter {
  #count = 0;         // hard-private at runtime — not just a compile-time label
  increment(): void {
    this.#count++;
  }
  get value(): number {
    return this.#count;
  }
}
const c = new Counter();
c.increment();
c.increment();
console.assert(c.value === 2, "#private field via getter");
```

In this example: `#count` is genuinely inaccessible from outside `Counter` — not
only does `c.#count` fail to type-check, but there is no bracket-notation or
`Object.keys` trick that can read it at runtime either, because `#private` is a real
JavaScript language feature with runtime enforcement, unlike TS's `private` keyword.
`value` is a `get` accessor, letting callers read `c.value` with plain property
syntax while the actual storage stays encapsulated behind the getter.

| | `private`/`protected` (TS keywords) | `#private` (native JS) |
|---|---|---|
| Enforced by | Compiler only (erased) | The JS runtime itself |
| Reachable via `obj["field"]` at runtime | Yes | No |
| Reachable from plain untyped JS code | Yes | No |
| Use when | API hygiene is the goal (discourage, don't need a hard boundary) | Privacy is a real invariant that must hold even outside the type system |

### Why it's useful

`abstract` classes plus `override` give you compiler-checked template-method
polymorphism, catching both "forgot to implement this" and "this override no longer
matches the base signature" at compile time instead of at a confusing runtime call
site. `#private` fields matter whenever a class's internal state must never be
touched from outside regardless of what TypeScript-unaware code does with the
object — TS's own `private` is only a hint to well-behaved TS callers.

### Summary

- `public`/`protected`/`private` are compile-time only and erased; `#private` is
  real, runtime-enforced privacy.
- Parameter properties (`constructor(private readonly x: T)`) declare and assign a
  field in one step — TS-only sugar.
- `implements` is a structural check, not a source of behavior.
- `abstract` classes can't be instantiated and can require subclasses to implement
  specific members — the vehicle for Template Method.
- `noImplicitOverride` requires `override` on overriding methods, catching typos and
  signature drift between base and subclass.

## 3.4 — Composing shapes: `extends`, intersections, declaration merging

- **`extends`** on an interface is interface inheritance — an interface can extend
  **several** others at once, unlike a TS/JS class, which can only `extends` one
  base class (though it can `implements` many interfaces).
- **Intersection (`A & B`)** is the `type`-level way to combine two or more shapes
  into one that has *all* members of both — the composition counterpart to a
  union's "one of." Useful for mixing in cross-cutting fields.
- **Declaration merging** — two `interface` declarations with the **same name**
  automatically **merge** into a single combined interface. Only `interface`
  supports this; a `type` alias cannot be re-declared. Its main real-world use is
  **augmenting types you don't own** — adding a custom property to `Window`, or to
  an Express `Request`, without editing that library's source.

```ts
interface Timestamped {
  createdAt: number;
}
interface Entity extends Timestamped {
  id: string;
}
const e: Entity = { id: "x", createdAt: 1 };
console.assert(e.id === "x" && e.createdAt === 1, "interface extends");

type WithAudit = { updatedBy: string };
type AuditedEntity = Entity & WithAudit;
const ae: AuditedEntity = { id: "y", createdAt: 2, updatedBy: "ada" };
console.assert(ae.updatedBy === "ada", "intersection type combines shapes");

interface Box {
  width: number;
}
interface Box {
  height: number;      // merges with the Box declared above
}
const bx: Box = { width: 2, height: 3 };
console.assert(bx.width * bx.height === 6, "interface declaration merging");
```

In this example: `Entity` inherits `createdAt` from `Timestamped` via `extends`, so
`e` must supply both fields. `AuditedEntity` intersects `Entity` with `WithAudit`
using `&`, producing a type requiring all of `id`, `createdAt`, *and* `updatedBy` —
the same net effect as a second `extends`, but expressed at the `type` level, and
usable even when one side isn't itself an interface. The two `Box` declarations are
not a redeclaration error — because they're both `interface`, TypeScript **merges**
them into a single `Box` type requiring both `width` and `height`; this is the
mechanism library authors rely on when they ship an interface that consuming code
can extend by declaring the same interface name again in their own project (a
common pattern for augmenting `Express.Request`, `Window`, or a Redux store's root
state).

### Why it's useful

`extends` and `&` both let you build precise types by composing smaller, named
pieces instead of duplicating fields across several interfaces — `Entity &
WithAudit` reads as "an Entity, plus audit fields" rather than a new type with no
stated relationship to either. Declaration merging is specifically how you safely
extend a third-party or global type you don't control — it's the sanctioned
alternative to monkey-patching or casting to `any` when a library's types don't
cover a field your code actually relies on.

### Summary

- Interface `extends` supports multiple base interfaces; class `extends` supports
  exactly one base class (plus any number of `implements`).
- `A & B` (intersection) is the `type`-level composition tool — "has everything from
  both."
- Declaration merging combines same-named `interface` declarations; `type` cannot be
  re-opened this way.
- Merging's main real use: augmenting a library's or the global scope's types
  without touching their source.

## Perspective

Model data with `interface`/`type` and behavior with classes — but note that
idiomatic TypeScript often skips classes entirely for plain typed objects plus
functions, which is closer to JavaScript's natural grain and what libraries like
React and Redux favor. Two things to keep straight coming from Java: **structural
`implements`** (a class matches an interface by shape, and so can completely
unrelated plain objects — nothing requires the declared relationship Java demands),
and **erased access modifiers** (reach for `#private` whenever you need privacy that
holds even against code the type checker doesn't see). Excess-property checks are
your typo safety net specifically on fresh object literals.
