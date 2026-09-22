<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · generics](../phase-4-generics/NOTES.md) | [Phase 6 · async tooling ➡](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 5 — Advanced & Type-Level Programming: Notes

This phase is TypeScript's signature superpower: the type system is itself a small,
**pure functional language** that computes new types from existing ones. Most of
what's covered here has **no runtime value at all** — the demo file proves these
claims with compile-time `Expect<Equal<A, B>>` assertions (a tiny type-level test
kit defined at the top of `advancedTypes.ts`), so `tsc --noEmit` genuinely *is* the
test suite for the type-only parts of this phase; there's nothing to `console.assert`
against at runtime.

## 5.1 — Discriminated (tagged) unions and exhaustiveness checking

A **discriminated union** is a union where every arm shares one common property —
the **discriminant** — typed as a distinct literal per arm. This is the single most
important modelling tool in applied TypeScript: TS's type-safe answer to what other
languages call a "sum type" or "tagged union" (Java's closest analogue is a sealed
interface hierarchy with `instanceof` pattern matching).

```ts
type Shape =
  | { kind: "circle"; radius: number }
  | { kind: "rect"; width: number; height: number }
  | { kind: "square"; side: number };

function area(s: Shape): number {
  switch (s.kind) {
    case "circle":
      return Math.PI * s.radius ** 2;   // narrowed to the circle arm
    case "rect":
      return s.width * s.height;        // narrowed to the rect arm
    case "square":
      return s.side ** 2;               // narrowed to the square arm
    default: {
      // Exhaustiveness: if a new arm is added and this switch isn't updated,
      // `s` is no longer `never` here and the line below fails to compile.
      const _exhaustive: never = s;
      return _exhaustive;
    }
  }
}
console.assert(Math.abs(area({ kind: "square", side: 3 }) - 9) < 1e-9, "discriminated union area");
console.assert(area({ kind: "rect", width: 2, height: 5 }) === 10, "rect arm");
```

In this example: because `kind` is a **literal** field (`"circle"` / `"rect"` /
`"square"`, not just `string`), `switch (s.kind)` narrows `s` inside each `case` to
exactly that arm's shape — inside `case "circle"`, only `s.radius` exists; inside
`case "rect"`, only `s.width`/`s.height` exist. The `default` branch assigns `s` to a
variable explicitly typed `never`. If every case has genuinely been handled, TS has
already narrowed `s`'s remaining possibilities down to nothing by the time control
reaches `default`, so the assignment to `never` compiles. If someone later adds a
fourth shape (say `"triangle"`) and forgets to add a matching `case`, `s` inside
`default` would then still include the unhandled `"triangle"` arm — no longer
`never` — and the assignment becomes a compile error, flagging the missed update
before it ever ships.

### Why it's useful

Discriminated unions tie each field to exactly the state(s) where it's actually
valid, which — combined with exhaustiveness checking — turns "did I update every
switch/if-chain when I added a new case" from a runtime bug waiting to happen into
a compile error the moment you forget. This is the idiomatic way to model API
response shapes, UI/request state, and domain events (expanded on in Phase 7's
"make illegal states unrepresentable").

### Summary

- Each arm of a discriminated union shares one literal field (the discriminant).
- Narrowing on that field (via `switch`/`if`) exposes only that arm's members.
- Assigning the switched value to a `const x: never` in the final branch gives
  compiler-enforced exhaustiveness — add a case, forget to handle it, get an error.
- This is TS's type-safe sum type; Java's nearest analogue is a sealed hierarchy.

## 5.2 — Mapped types and conditional types

### Mapped types

A mapped type builds a **new object type** by iterating over another type's keys —
this is literally how built-in utility types like `Partial<T>` are defined under the
hood.

```ts
type MyPartial<T> = { [K in keyof T]?: T[K] };      // add "?" to every key
type MyRequired<T> = { [K in keyof T]-?: T[K] };     // strip "?" from every key

interface Config {
  host: string;
  port?: number;
}
// Compile-time-only assertions (see the type-level test kit at the top of the file):
type _p1 = Expect<Equal<MyPartial<Config>, { host?: string; port?: number }>>;
type _p2 = Expect<Equal<MyRequired<Config>, { host: string; port: number }>>;
```

In this example: `MyPartial<T>` iterates `keyof T` (every key of `T`) and re-emits
each with a `?` appended, producing a type where every original property becomes
optional. `MyRequired<T>` does the opposite with `-?`, which **removes** the
optional modifier from every key (even ones that didn't have it) rather than adding
it — `-` before a modifier means "strip this modifier," and `-readonly` similarly
strips `readonly`. `_p1`/`_p2` are compile-time-only checks: if `MyPartial<Config>`
ever computed something other than the expected shape, these lines would fail to
compile — `tsc --noEmit` catching a type-level regression the same way a unit test
catches a runtime one.

### Conditional types and `infer`

A conditional type, `T extends U ? X : Y`, is an `if`/`else` at the type level: it
picks between two branches based on whether `T` is assignable to `U`. The **`infer`**
keyword, used inside the `extends` clause, introduces a new type variable that
captures a piece of the matched type.

```ts
type ElementType<T> = T extends (infer E)[] ? E : T;
type _c1 = Expect<Equal<ElementType<string[]>, string>>;
type _c2 = Expect<Equal<ElementType<number>, number>>;   // not an array -> itself

// Conditional types distribute over unions — a subtle, powerful default.
type NonNull<T> = T extends null | undefined ? never : T;
type _c3 = Expect<Equal<NonNull<string | null | number>, string | number>>;
```

In this example: `ElementType<string[]>` matches the `(infer E)[]` pattern with `E =
string`, so it resolves to `string`; `ElementType<number>` doesn't match an array
pattern at all, so it falls through to the `: T` branch and resolves to `number`
itself. `NonNull<T>` is more subtle: when `T` is the union `string | null | number`
and `T` in the conditional is a **bare (naked) type parameter**, TypeScript
**distributes** the conditional across each member of the union individually —
effectively computing `NonNull<string> | NonNull<null> | NonNull<number>`, which is
`string | never | number`, and since `never` vanishes from a union (it contributes
no possible values), the final result is `string | number`. This distributive
behavior is the default and is usually exactly what you want; to compare the union
as a single whole instead of distributing, wrap both sides in a tuple —
`[T] extends [U] ? X : Y` — which switches distribution off.

### Why it's useful

Mapped types are how you transform an existing type's shape systematically instead
of hand-writing a parallel type that has to be kept in sync by hand forever — change
the source type, and everything derived from it via a mapped type updates
automatically. Conditional types with `infer` let you extract information buried
inside another type (an array's element type, a function's return type, a Promise's
resolved type) — this is exactly the mechanism behind `ReturnType<F>` and
`Awaited<T>` (5.3, Phase 6).

### Summary

- Mapped types (`{ [K in keyof T]: ... }`) build a new object type from another's
  keys; `-?`/`-readonly` strip modifiers, `?`/`readonly` add them.
- Conditional types (`T extends U ? X : Y`) are a type-level `if`; `infer` captures
  a piece of the matched type as a new type variable.
- Conditional types **distribute** over a union when the checked type is a bare type
  parameter — wrap in `[T] extends [U]` to compare the union as a whole instead.

## 5.3 — Utility types: the type-level standard library

TypeScript ships a set of built-in generic utility types — all of them definable in
terms of the mapped/conditional types from 5.2 — that you'll use constantly for
deriving one type from another.

```ts
interface User {
  id: string;
  name: string;
  email: string;
  password: string;
}

type PublicUser = Omit<User, "password">;             // no password leaves the boundary
type Credentials = Pick<User, "email" | "password">;
type _u1 = Expect<Equal<keyof PublicUser, "id" | "name" | "email">>;
type _u2 = Expect<Equal<keyof Credentials, "email" | "password">>;

type UserPatch = Partial<Omit<User, "id">>;
const patch: UserPatch = { name: "New Name" };        // any subset, id excluded
console.assert(patch.name === "New Name", "Partial patch type");

type RolePermissions = Record<"admin" | "viewer", string[]>;
const perms: RolePermissions = { admin: ["read", "write"], viewer: ["read"] };
console.assert(perms.admin.length === 2, "Record maps each key to V");

function makeUser(name: string): { id: string; name: string } {
  return { id: "x", name };
}
type MadeUser = ReturnType<typeof makeUser>;           // { id: string; name: string }
type _u3 = Expect<Equal<MadeUser, { id: string; name: string }>>;
```

In this example: `Omit<User, "password">` derives `PublicUser` by removing exactly
the `password` key, so a response DTO can never accidentally leak the password field
— and if `User` gains a new sensitive field later, it still has to be explicitly
`Omit`-ted, rather than silently appearing in `PublicUser` by default. `Pick<User,
"email" | "password">` does the inverse — keep only the listed keys — useful for a
login payload type. `Partial<Omit<User, "id">>` composes two utilities: first drop
`id` (you never patch a resource's own id), then make everything else optional,
producing exactly the shape a PATCH request body needs. `Record<"admin" | "viewer",
string[]>` builds an object type with exactly those two keys, each mapping to a
`string[]` — a fully-typed dictionary when the key set is known ahead of time (unlike
the open-ended index signatures from Phase 3). `ReturnType<typeof makeUser>`
introspects a function's return type without you retyping it by hand.

| Utility | Meaning |
|---|---|
| `Partial<T>` | Every property optional — patch/update payloads |
| `Required<T>` | Every property required |
| `Readonly<T>` | Every property `readonly` |
| `Pick<T, K>` | Keep only keys `K` |
| `Omit<T, K>` | Drop keys `K` (e.g. strip `password` for a DTO) |
| `Record<K, V>` | Object type with keys `K`, each mapped to value type `V` |
| `ReturnType<F>` | A function type's return type |
| `Parameters<F>` | A function type's parameter types, as a tuple |
| `Awaited<T>` | Unwraps a `Promise<T>` (recursively) — Phase 6 |
| `NonNullable<T>` | Removes `null`/`undefined` from `T` |
| `Exclude<T, U>` / `Extract<T, U>` | Remove/keep union members assignable to `U` |

### Why it's useful

Deriving a DTO from a single source-of-truth type — `Omit<User, "password">`,
`Partial<Omit<User, "id">>` — keeps the API surface and the domain model in sync
automatically: add a field to `User`, and every type derived from it via a utility
type reflects the change (or, for `Omit`, must be explicitly excluded, which is the
*safe* direction to fail in for something like `password`). This is dramatically
less error-prone than hand-writing a parallel `PublicUser` interface that has to be
remembered and updated by hand every time `User` changes.

### Summary

- Utility types are built-in, generically reusable mapped/conditional types.
- `Pick`/`Omit` select or remove keys; `Partial`/`Required`/`Readonly` toggle
  modifiers across every key; `Record` builds a typed dictionary from a known key set.
- `ReturnType`/`Parameters` introspect function types; `Awaited` unwraps Promises.
- Derive DTOs and payload types from one source type instead of duplicating shapes —
  this keeps the model and its derived types from drifting apart.

## 5.4 — Template-literal types

Types can be built from **string template patterns**, the same way you'd build a
runtime string with a template literal — but computed entirely at the type level,
generating a union of concrete string-literal types.

```ts
type Entity = "user" | "order";
type Event = `${Entity}:created` | `${Entity}:deleted`;
const ev: Event = "user:created";
// @ts-expect-error — "user:updated" isn't in the generated union
const badEv: Event = "user:updated";
console.assert(ev === "user:created", "template-literal union");

// Combined with mapped types + `as` key remapping: generate getter names.
type Getters<T> = { [K in keyof T & string as `get${Capitalize<K>}`]: () => T[K] };
type PointGetters = Getters<{ x: number; y: number }>;
type _t1 = Expect<Equal<keyof PointGetters, "getX" | "getY">>;
```

In this example: `Event` expands `` `${Entity}:created` `` across every member of
the `Entity` union, producing the concrete four-member union `"user:created" |
"order:created" | "user:deleted" | "order:deleted"` — TypeScript computes the full
cross product automatically. `"user:updated"` was never one of those generated
literals, so assigning it is a compile error, just like any other literal-union
mismatch. `Getters<T>` combines a mapped type with **key remapping** (`as`): for
each key `K` of `T`, instead of keeping the same key name, it renames it to
`` `get${Capitalize<K>}` `` using the intrinsic `Capitalize` string-manipulation
type — so `Getters<{ x: number; y: number }>` produces a type with keys `getX` and
`getY`, each a function returning the original property's type. The intrinsic types
`Uppercase`, `Lowercase`, `Capitalize`, and `Uncapitalize` are compiler built-ins for
exactly this kind of string transformation at the type level.

### Why it's useful

Template-literal types make previously "stringly-typed" APIs statically checked:
typed event names, typed route paths, typed CSS custom-property names, and
(combined with key remapping) generated member names like getters/setters or Redux
action-type constants — all validated by the compiler instead of relying on
convention and hoping nobody typos a string.

### Summary

- `` `${Union}suffix` `` expands across every member of a union, generating the
  literal cross product as a new union type.
- Intrinsic string types (`Uppercase`, `Lowercase`, `Capitalize`, `Uncapitalize`)
  manipulate string-literal types at compile time.
- Mapped-type key remapping (`as`) combined with template literals can generate
  derived member names (getters, action types) from an existing type.

## When to stop: type-level programming is addictive

Type-level programming can become unreadable fast, and it has a real compile-time
performance cost on very deep or recursive constructs. Guidance:

- Use **discriminated unions and the standard utility types liberally** — they're
  clear, idiomatic, and everyone reading TS recognizes them instantly.
- Reach for hand-written conditional/mapped-type gymnastics **sparingly**, and only
  to make a genuinely public API safer or DRYer — not to show off.
- If a type needs a comment to explain *how* it computes its result, consider
  whether a simpler, explicit type would serve future readers better — the same
  KISS principle from the software-design track applies to the type system too.

## Perspective

Discriminated unions plus exhaustive `never` checks are the everyday, high-value
tool — reach for them any time you're modelling "one of N shapes" (API responses, UI
state, domain events). Mapped, conditional, and utility types are how you keep one
source of truth and mechanically derive everything else from it instead of hand-
duplicating shapes. Together, these are the mechanism behind Phase 7's central idea:
**make illegal states unrepresentable** — if the type system can't even express the
bad case, the bug literally cannot compile.
