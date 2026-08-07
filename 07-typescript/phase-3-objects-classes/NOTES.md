<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · generics ➡](../phase-4-generics/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects, Interfaces & Classes: Notes

Object shapes are the bread and butter of application types. This is also where the
**structural typing** from Phase 1 has the most surprising consequences for a Java
developer.

## 3.1 — `interface` / `type`, optional, readonly, index signatures

- `readonly` members are assign-once (compile-time; like a Java `final` field).
- `?` optional members are `T | undefined` and may be omitted.
- **Index signatures** (`{ [key: string]: number }`) type objects used as maps/dicts
  when keys aren't known statically. Under **`noUncheckedIndexedAccess`** (on in this
  track), an index read is `T | undefined` — the compiler forces you to handle the
  "missing key" case, closing a common runtime hole. Prefer `Record<K, V>` (Phase 5)
  or a `Map` when keys *are* known.
- `interface` vs `type` recap (Phase 1): `interface` for extendable object/class
  contracts (and it can merge), `type` for unions/tuples/computed types.

## 3.2 — Structural compatibility & excess-property checks

- **Assignability is structural.** An object with *more* properties is assignable
  where *fewer* are required — the extra members are simply ignored. This is what
  makes duck typing ergonomic and is unlike Java's nominal `implements`.
- **Excess-property checks** are the deliberate exception: when you pass a **fresh
  object literal** directly, TS flags properties that don't exist on the target
  type. This catches typos (`{ colour: ... }` when the type wants `color`) that pure
  structural typing would miss. Assign the literal to a variable first and the check
  goes away (you've opted into structural compatibility). Escape hatches: a type
  assertion, or an index signature on the target.

## 3.3 — Classes

TS classes are JS classes (track 06) plus compile-time typing:

- **Access modifiers** `public`/`protected`/`private` are **compile-time only**
  (erased) — a `private` field is still reachable via `obj["field"]` in emitted JS.
  For **true runtime privacy**, use JS `#private` fields. Prefer `#` when privacy is
  a real invariant, `private` when it's just API hygiene.
- **Parameter properties** — `constructor(private readonly r: number)` declares the
  field *and* assigns it, removing `this.r = r` boilerplate (a TS-only convenience).
- **`implements`** checks a class against an interface **structurally** — it doesn't
  add members, just verifies them. A class can `implements` many interfaces.
- **`abstract`** classes can't be instantiated and may declare `abstract` members
  subclasses must implement — the vehicle for Template Method (Software Design
  Phase 5). Under `noImplicitOverride`, overriding methods need the **`override`**
  keyword, catching typos and signature drift.
- **`get`/`set`** accessors present computed properties; `static` members live on the
  class.

## 3.4 — Composition of shapes

- **`extends`** — interface inheritance; an interface can extend several. Classes use
  `extends` for a single base class and `implements` for interfaces.
- **Intersection `A & B`** — the `type` way to combine shapes (everything from both).
  It's the composition counterpart to union's "one of." Great for mixins and adding
  cross-cutting fields (`Entity & Audited`).
- **Declaration merging** — two `interface`s (or an `interface` and a `namespace`)
  with the same name **merge** into one. Only interfaces support this; `type` cannot
  be re-opened. Its main real use is **augmenting third-party/global types** (e.g.
  adding a property to `Window` or an Express `Request`).

## Perspective

Model data with `interface`/`type` and behavior with classes — but note idiomatic TS
often skips classes for plain typed objects + functions (closer to the JS grain, and
what React/Redux favor). Two things to keep straight coming from Java: **structural
`implements`** (a class matches an interface by shape, and unrelated objects can too)
and **erased access modifiers** (reach for `#private` when you need a real runtime
boundary). Excess-property checks are your typo safety net on object literals.
