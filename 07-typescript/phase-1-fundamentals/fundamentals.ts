/**
 * Phase 1 — Fundamentals & the type system.
 *
 * Run:        npx tsx phase-1-fundamentals/fundamentals.ts
 * Type-check: npm run typecheck   (tsc --noEmit over the whole track)
 *
 * Every `@ts-expect-error` line PROVES a type error: if the line below it were
 * actually valid, tsc would fail this file. So the compiler verifies our claims.
 */

// ---------------------------------------------------------------------------
// 1.1 — Types are erased. Structural (duck) typing, not nominal (unlike Java).
// ---------------------------------------------------------------------------

// A type only exists at compile time. At runtime this is plain JS — there is no
// `Point` class, no reflection, nothing to `instanceof`. Types are ERASED.
type Point = { x: number; y: number };

// Structural typing: compatibility is by SHAPE, not by name. In Java, an object
// must *declare* `implements Point`. In TS, anything with the right shape IS a
// Point. `named` was never told about `Point`, yet it fits.
const named = { x: 1, y: 2, label: "origin" };
const p: Point = named; // OK — has x and y (extra `label` is fine here)
console.assert(p.x === 1 && p.y === 2, "structural typing: shape is enough");

// Nominal typing would reject this; structural typing embraces it. This is the
// single biggest mental shift coming from Java.

// ---------------------------------------------------------------------------
// 1.2 — Inference vs annotation; any vs unknown vs never.
// ---------------------------------------------------------------------------

// Inference: TS reads the initializer. `n` is `number` with no annotation.
let n = 42; // : number (inferred)
// @ts-expect-error — a string is not assignable to the inferred `number`
n = "nope";

// `any` — opt OUT of type checking. It's contagious and disables safety.
// Avoid it; it's how bugs sneak back in.
const dirty: any = "text";
const bad: number = dirty; // no error — `any` silences the checker (the danger)
void bad;

// `unknown` — the SAFE top type. You can assign anything TO it, but you must
// narrow before you use it. This is TS's answer to Java's `Object`, but stricter:
// Java lets you call `.toString()` on Object; TS lets you do NOTHING with unknown
// until you prove what it is.
const maybe: unknown = JSON.parse('{"x":1}');
// @ts-expect-error — can't access properties on `unknown` without narrowing
maybe.x;
let width = 0;
if (typeof maybe === "object" && maybe !== null && "x" in maybe) {
  // narrowed: now safe to read
  width = (maybe as { x: number }).x;
}
console.assert(width === 1, "unknown must be narrowed before use");

// `never` — the bottom type: a value that can never exist. Used for code that
// never returns, and for exhaustiveness checks (Phase 5).
function fail(msg: string): never {
  throw new Error(msg);
}
void fail;

// ---------------------------------------------------------------------------
// 1.3 — type aliases vs interface; union & literal types.
// ---------------------------------------------------------------------------

// Literal types: a value can be pinned to an exact literal, not just its base type.
type Direction = "north" | "south" | "east" | "west"; // union of string literals
const heading: Direction = "north";
// @ts-expect-error — "up" is not one of the allowed literals
const wrong: Direction = "up";
void wrong;

// Union types: "one of several". No Java equivalent (closest: a sealed interface).
type Id = number | string;
function stringifyId(id: Id): string {
  // Must NARROW a union before using type-specific members:
  return typeof id === "number" ? id.toFixed(0) : id.toUpperCase();
}
console.assert(stringifyId(7) === "7", "union narrowed to number");
console.assert(stringifyId("ab") === "AB", "union narrowed to string");

// `type` vs `interface`: both describe object shapes. `type` also does unions,
// tuples, primitives, mapped/conditional types. `interface` can be re-opened
// (declaration merging) and reads better for public object contracts. Rule of
// thumb: `interface` for object/class shapes, `type` for everything else.
interface User {
  readonly id: Id; // readonly = assign once
  name: string;
  email?: string; // optional
}
const u: User = { id: 1, name: "Ada" };
// @ts-expect-error — `id` is readonly
u.id = 2;
console.assert(u.email === undefined, "optional prop is absent");

// ---------------------------------------------------------------------------
// 1.4 — Narrowing & type guards.
// ---------------------------------------------------------------------------

// The compiler tracks control flow. Inside a `typeof`/`in`/truthiness check, the
// variable's type is NARROWED — this is how you safely use unions and unknown.
function measure(x: string | string[] | null): number {
  if (x === null) return 0; // narrowed away null
  if (typeof x === "string") return x.length; // x: string here
  return x.length; // x: string[] here (only remaining option)
}
console.assert(measure(null) === 0, "null guard");
console.assert(measure("hi") === 2, "string branch");
console.assert(measure(["a", "b", "c"]) === 3, "array branch");

// Custom type guard: a function returning `x is T` teaches the compiler.
type Cat = { kind: "cat"; meow: () => string };
type Dog = { kind: "dog"; bark: () => string };
function isCat(a: Cat | Dog): a is Cat {
  return a.kind === "cat";
}
const pet: Cat | Dog = { kind: "cat", meow: () => "meow" };
console.assert(isCat(pet) && pet.meow() === "meow", "user-defined type guard narrows");

console.log("Phase 1: all fundamentals assertions passed.");

export {}; // make this file a module (isolates top-level declarations per phase)
