/**
 * Phase 4 — Generics.
 *
 * Run:        npx tsx phase-4-generics/generics.ts
 * Type-check: npm run typecheck
 */

// ---------------------------------------------------------------------------
// 4.1 — Generic functions & inference.
// ---------------------------------------------------------------------------

// A generic captures the caller's type so the return relates to the input.
// Unlike Java, there is NO erasure-to-Object: `identity(3)` returns `number`,
// and TS INFERS T from the argument — you rarely write `<number>` explicitly.
function identity<T>(x: T): T {
  return x;
}
const three = identity(3); // T inferred as number
const word = identity("hi"); // T inferred as string
console.assert(three === 3 && word === "hi", "generic identity infers T");

// A generic over two params — `firstOf` preserves the element type.
function firstOf<T>(xs: readonly T[]): T | undefined {
  return xs[0];
}
const f = firstOf([10, 20, 30]); // number | undefined
console.assert(f === 10, "generic array element type preserved");

// map, typed generically (what Array.prototype.map's signature looks like).
function mapArr<T, U>(xs: readonly T[], fn: (x: T) => U): U[] {
  return xs.map(fn);
}
const lengths = mapArr(["a", "bb", "ccc"], (s) => s.length); // number[]
console.assert(lengths.join(",") === "1,2,3", "generic map T->U");

// ---------------------------------------------------------------------------
// 4.2 — Constraints (extends), keyof, indexed access.
// ---------------------------------------------------------------------------

// Constraint: `T extends { length: number }` limits T to things with a length.
function longest<T extends { length: number }>(a: T, b: T): T {
  return a.length >= b.length ? a : b;
}
console.assert(longest("aa", "bbb") === "bbb", "constrained generic (string has length)");
console.assert(longest([1], [1, 2]).length === 2, "constrained generic (array has length)");
// @ts-expect-error — number has no `length`, so it fails the constraint
longest(1, 2);

// keyof: the union of an object type's keys. `K extends keyof T` ties a key to its
// object, and the return `T[K]` is the exact value type (indexed access). This is
// far more precise than Java generics can express.
function getProp<T, K extends keyof T>(obj: T, key: K): T[K] {
  return obj[key];
}
const person = { name: "Ada", age: 36 };
const nm: string = getProp(person, "name"); // T[K] = string
const ag: number = getProp(person, "age"); // T[K] = number
console.assert(nm === "Ada" && ag === 36, "keyof + indexed access give exact value type");
// @ts-expect-error — "email" is not a key of person
getProp(person, "email");

// ---------------------------------------------------------------------------
// 4.3 — Generic interfaces / classes; default type params.
// ---------------------------------------------------------------------------

// Generic interface. `E = Error` is a DEFAULT type parameter.
interface ApiResult<T, E = Error> {
  ok: boolean;
  data?: T;
  error?: E;
}
const good: ApiResult<number> = { ok: true, data: 42 }; // E defaults to Error
console.assert(good.data === 42, "generic interface with default type param");

// Generic class — a typed stack.
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

// ---------------------------------------------------------------------------
// 4.4 — Practical generics: a Result<T,E> and a tiny typed repository.
// ---------------------------------------------------------------------------

// Result: model success/failure WITHOUT exceptions, fully typed (Rust-style).
// This is a discriminated union (Phase 5) parameterised by T and E.
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

// A generic in-memory repository keyed by an `id` field (constraint via keyof-ish).
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

console.log("Phase 4: all generics assertions passed.");

export {}; // make this file a module (isolates top-level declarations per phase)
