/**
 * Phase 2 — Functions & everyday types.
 *
 * Run:        npx tsx phase-2-functions/functions.ts
 * Type-check: npm run typecheck
 */

// ---------------------------------------------------------------------------
// 2.1 — Function types; optional / default / rest params; void vs undefined.
// ---------------------------------------------------------------------------

// A function type describes params AND return. Return type is usually inferred.
const add = (a: number, b: number): number => a + b;
console.assert(add(2, 3) === 5, "typed function");

// Optional (?) params come after required ones; they are `T | undefined` inside.
function greet(name: string, title?: string): string {
  return title ? `${title} ${name}` : name;
}
console.assert(greet("Ada") === "Ada", "optional omitted");
console.assert(greet("Ada", "Dr.") === "Dr. Ada", "optional provided");

// Default params: the type is inferred from the default; caller may omit.
function pow(base: number, exp = 2): number {
  return base ** exp;
}
console.assert(pow(5) === 25, "default param");

// Rest params are a typed array.
function sum(...xs: number[]): number {
  return xs.reduce((a, b) => a + b, 0);
}
console.assert(sum(1, 2, 3, 4) === 10, "rest params");

// A function-type alias (a "call signature"). Great for callbacks.
type BinaryOp = (a: number, b: number) => number;
const mul: BinaryOp = (a, b) => a * b; // params inferred from the alias
console.assert(mul(3, 4) === 12, "function-type alias infers param types");

// `void` return means "ignore whatever it returns" — subtly different from
// `undefined`. A `void`-typed callback MAY return a value; the caller ignores it.
// This is why `arr.forEach(x => arr2.push(x))` type-checks (push returns number).
const logs: string[] = [];
function each<T>(xs: T[], fn: (x: T) => void): void {
  for (const x of xs) fn(x);
}
each([1, 2, 3], (x) => logs.push(String(x))); // push returns number; void ignores it
console.assert(logs.length === 3, "void return ignores the callback's result");

// ---------------------------------------------------------------------------
// 2.2 — Union types & narrowing; `as const`.
// ---------------------------------------------------------------------------

// A union parameter must be narrowed before arm-specific use.
function idToString(id: number | string): string {
  return typeof id === "number" ? id.toString(16) : id.trim();
}
console.assert(idToString(255) === "ff", "number arm");
console.assert(idToString("  x ") === "x", "string arm");

// `as const` freezes a literal to its narrowest, readonly type. Without it,
// `role` widens to `string`; with it, it's the literal "admin".
const cfgWide = { role: "admin" }; // role: string
const cfgConst = { role: "admin" } as const; // role: "admin" (readonly)
cfgWide.role = "user"; // allowed — widened to string
console.assert(cfgConst.role === "admin", "as const pins the literal");
// @ts-expect-error — `as const` made every property readonly (compile-time only;
// note tsx would still MUTATE at runtime, so we assert BEFORE this line)
cfgConst.role = "user";

// `as const` on an array gives a readonly tuple of literals — perfect for
// deriving a union type from runtime data (Phase 5 pattern).
const ROLES = ["admin", "editor", "viewer"] as const;
type Role = (typeof ROLES)[number]; // "admin" | "editor" | "viewer"
const r: Role = "editor";
// @ts-expect-error — "root" isn't in the derived union
const badRole: Role = "root";
void badRole;
console.assert(ROLES.includes(r), "union derived from a const array");

// ---------------------------------------------------------------------------
// 2.3 — Arrays, tuples, readonly, enums (and why unions often beat enums).
// ---------------------------------------------------------------------------

const nums: number[] = [1, 2, 3]; // == Array<number>
const grid: number[][] = [[1], [2, 3]];

// Tuple: a FIXED-length, position-typed array (Java has no first-class tuple).
let pair: [string, number] = ["age", 30];
pair = ["height", 180];
console.assert(pair[0] === "height", "tuple positions are typed");
// @ts-expect-error — wrong element type at position 1 (compile-time only)
pair = ["x", "y"];

// Named tuple (typing e.g. React's useState return). NB: `Range` is a DOM global,
// so we name ours `Span`.
type Span = [start: number, end: number];
const span: Span = [0, 10];
console.assert(span[1] - span[0] === 10, "named tuple");

// readonly array: no push/mutation (compile-time only).
const frozen: readonly number[] = [1, 2, 3];
console.assert(frozen.length === 3, "readonly array blocks mutation at compile time");
// @ts-expect-error — push doesn't exist on a readonly array
frozen.push(4);

// Enums: a TS feature that EMITS runtime code (unlike everything else, which
// erases). A numeric enum is a two-way object. Modern guidance: prefer a union of
// string literals (or `as const` object) — zero runtime cost, easier to narrow.
enum Status {
  Active,
  Suspended,
}
console.assert(Status.Active === 0, "numeric enum is 0-indexed");
// Preferred alternative — a const object + derived union (no emitted enum):
const Level = { Low: "low", High: "high" } as const;
// value and type can share a name (separate namespaces). Indexing the const
// object's value type by its keys yields the union of its values:
type Level = (typeof Level)[keyof typeof Level]; // "low" | "high"
const lvl: Level = "high";
console.assert(lvl === "high", "union-as-enum alternative");

// ---------------------------------------------------------------------------
// 2.4 — Overloads.
// ---------------------------------------------------------------------------

// Overload signatures let one function present several typed shapes. The
// implementation signature is broad and NOT directly callable.
function len(x: string): number;
function len(x: unknown[]): number;
function len(x: string | unknown[]): number {
  return x.length;
}
console.assert(len("hello") === 5, "overload: string");
console.assert(len([1, 2, 3]) === 3, "overload: array");
// (In practice a single union signature is often clearer than overloads.)

console.log("Phase 2: all function/everyday-type assertions passed.");

export {}; // make this file a module (isolates top-level declarations per phase)
