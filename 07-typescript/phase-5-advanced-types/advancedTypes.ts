/**
 * Phase 5 — Advanced & type-level programming.
 *
 * Run:        npx tsx phase-5-advanced-types/advancedTypes.ts
 * Type-check: npm run typecheck
 *
 * Much of this phase is compile-time-only (types have no runtime value). We prove
 * those with type-level assertions: a helper `Expect<Equal<A,B>>` fails to compile
 * if A and B differ. So `npm run typecheck` IS the test for the type-only parts.
 */

// --- tiny type-level test kit -----------------------------------------------
type Equal<A, B> =
  (<T>() => T extends A ? 1 : 2) extends <T>() => T extends B ? 1 : 2 ? true : false;
type Expect<T extends true> = T;

// ---------------------------------------------------------------------------
// 5.1 — Discriminated unions + exhaustive `never`.
// ---------------------------------------------------------------------------

// A discriminated (tagged) union: each arm shares a literal `kind` field. This is
// the idiomatic way to model "one of several shapes" — the type-safe sum type.
type Shape =
  | { kind: "circle"; radius: number }
  | { kind: "rect"; width: number; height: number }
  | { kind: "square"; side: number };

function area(s: Shape): number {
  switch (s.kind) {
    case "circle":
      return Math.PI * s.radius ** 2; // narrowed to the circle arm
    case "rect":
      return s.width * s.height;
    case "square":
      return s.side ** 2;
    default: {
      // Exhaustiveness: if a new arm is added and not handled, `s` is no longer
      // `never` here and this line fails to compile — a compile-time TODO.
      const _exhaustive: never = s;
      return _exhaustive;
    }
  }
}
console.assert(Math.abs(area({ kind: "square", side: 3 }) - 9) < 1e-9, "discriminated union area");
console.assert(area({ kind: "rect", width: 2, height: 5 }) === 10, "rect arm");

// ---------------------------------------------------------------------------
// 5.2 — Mapped types & modifiers; conditional types & `infer`.
// ---------------------------------------------------------------------------

// Mapped type: build a new type by iterating the keys of another. Here: make every
// property optional (this is literally how the built-in `Partial<T>` is defined).
type MyPartial<T> = { [K in keyof T]?: T[K] };
// ...and make everything readonly and required, stripping modifiers with -?:
type MyRequired<T> = { [K in keyof T]-?: T[K] };

interface Config {
  host: string;
  port?: number;
}
type _p1 = Expect<Equal<MyPartial<Config>, { host?: string; port?: number }>>;
type _p2 = Expect<Equal<MyRequired<Config>, { host: string; port: number }>>;

// Conditional type: `T extends U ? X : Y` chooses a branch by a type relationship.
type ElementType<T> = T extends (infer E)[] ? E : T; // `infer` captures a sub-type
type _c1 = Expect<Equal<ElementType<string[]>, string>>;
type _c2 = Expect<Equal<ElementType<number>, number>>; // not an array -> itself

// Conditional types distribute over unions — a subtle, powerful default.
type NonNull<T> = T extends null | undefined ? never : T;
type _c3 = Expect<Equal<NonNull<string | null | number>, string | number>>;

// ---------------------------------------------------------------------------
// 5.3 — Utility types (the standard library of type transforms).
// ---------------------------------------------------------------------------

interface User {
  id: string;
  name: string;
  email: string;
  password: string;
}

// Pick: keep a subset of keys. Omit: drop a subset. Great for DTOs.
type PublicUser = Omit<User, "password">; // no password leaves the boundary
type Credentials = Pick<User, "email" | "password">;
type _u1 = Expect<Equal<keyof PublicUser, "id" | "name" | "email">>;
type _u2 = Expect<Equal<keyof Credentials, "email" | "password">>;

// Partial (all optional) — perfect for update/patch payloads.
type UserPatch = Partial<Omit<User, "id">>;
const patch: UserPatch = { name: "New Name" }; // any subset, id excluded
console.assert(patch.name === "New Name", "Partial patch type");

// Record<K, V>: a fully-typed map/dictionary with known keys.
type RolePermissions = Record<"admin" | "viewer", string[]>;
const perms: RolePermissions = { admin: ["read", "write"], viewer: ["read"] };
console.assert(perms.admin.length === 2, "Record maps each key to V");

// Readonly<T>, ReturnType<F>, Parameters<F>, Awaited<P> (Phase 6) round it out.
function makeUser(name: string): { id: string; name: string } {
  return { id: "x", name };
}
type MadeUser = ReturnType<typeof makeUser>; // { id: string; name: string }
type _u3 = Expect<Equal<MadeUser, { id: string; name: string }>>;

// ---------------------------------------------------------------------------
// 5.4 — Template-literal types.
// ---------------------------------------------------------------------------

// Types can be built from string templates — enabling typed event names, routes…
type Entity = "user" | "order";
type Event = `${Entity}:created` | `${Entity}:deleted`;
const ev: Event = "user:created";
// @ts-expect-error — "user:updated" isn't in the generated union
const badEv: Event = "user:updated";
void badEv;
console.assert(ev === "user:created", "template-literal union");

// Combined with mapped types + `as` key remapping: generate getter names.
type Getters<T> = { [K in keyof T & string as `get${Capitalize<K>}`]: () => T[K] };
type PointGetters = Getters<{ x: number; y: number }>;
type _t1 = Expect<Equal<keyof PointGetters, "getX" | "getY">>;

// Silence "unused type" noise for the type-level checks (they run at compile time).
export type _All = [_p1, _p2, _c1, _c2, _c3, _u1, _u2, _u3, _t1];

console.log("Phase 5: runtime assertions passed (type-level checks verified by tsc).");
