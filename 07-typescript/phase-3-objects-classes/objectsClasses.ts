/**
 * Phase 3 — Objects, interfaces & classes.
 *
 * Run:        npx tsx phase-3-objects-classes/objectsClasses.ts
 * Type-check: npm run typecheck
 */

// ---------------------------------------------------------------------------
// 3.1 — interface vs type; optional / readonly; index signatures.
// ---------------------------------------------------------------------------

interface Product {
  readonly sku: string; // assign-once (like Java `final` field)
  name: string;
  price: number;
  discount?: number; // optional
}

const book: Product = { sku: "BK-1", name: "TS in Depth", price: 30 };
// @ts-expect-error — sku is readonly
book.sku = "BK-2";
console.assert(book.discount === undefined, "optional prop absent");

// Index signature: an object whose keys aren't known ahead of time (a map/dict).
interface StringScores {
  [name: string]: number;
}
const scores: StringScores = { ada: 90, alan: 85 };
scores.grace = 99; // any string key -> number
console.assert(scores.grace === 99, "index signature allows arbitrary keys");
// Under noUncheckedIndexedAccess, reads are `number | undefined`:
const maybeScore = scores.unknownPerson; // number | undefined
console.assert(maybeScore === undefined, "index read may be undefined (safe)");

// ---------------------------------------------------------------------------
// 3.2 — Structural compatibility & excess-property checks.
// ---------------------------------------------------------------------------

// Structural: a bigger object is assignable where a smaller shape is expected,
// AS LONG AS it comes through a variable (not a fresh literal).
interface HasName {
  name: string;
}
function printName(x: HasName): string {
  return x.name;
}
const richer = { name: "Ada", age: 36, role: "engineer" };
console.assert(printName(richer) === "Ada", "extra props OK via a variable (structural)");

// Excess-property check: a FRESH object literal with unknown props is flagged —
// a targeted safety net against typos, even though structural typing would allow it.
// @ts-expect-error — `nickname` is an excess property on a fresh literal
printName({ name: "Ada", nickname: "A" });

// ---------------------------------------------------------------------------
// 3.3 — Classes: access modifiers, implements, abstract, parameter properties.
// ---------------------------------------------------------------------------

interface Shape {
  area(): number;
}

// `abstract` base with a template method (cross-ref Software Design Phase 5).
abstract class BaseShape implements Shape {
  abstract area(): number;
  describe(): string {
    return `area=${this.area().toFixed(2)}`;
  }
}

class Circle extends BaseShape {
  // Parameter property: `private readonly r` declares AND assigns the field in one
  // go — no boilerplate `this.r = r`. `#`-private (JS) is also available.
  constructor(private readonly r: number) {
    super();
  }
  override area(): number {
    return Math.PI * this.r ** 2;
  }
}

class Rect extends BaseShape {
  constructor(
    private readonly w: number,
    private readonly h: number,
  ) {
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

// Access modifiers are COMPILE-TIME (erased). `#private` (native JS) is truly
// private at runtime. `private` fields are still visible via bracket access in JS.
class Counter {
  #count = 0; // hard-private at runtime
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

// ---------------------------------------------------------------------------
// 3.4 — Interface merging, extends, intersections.
// ---------------------------------------------------------------------------

// `extends`: interface inheritance (can extend multiple).
interface Timestamped {
  createdAt: number;
}
interface Entity extends Timestamped {
  id: string;
}
const e: Entity = { id: "x", createdAt: 1 };
console.assert(e.id === "x" && e.createdAt === 1, "interface extends");

// Intersection (`&`): combine shapes — the `type` equivalent of multiple extends.
type WithAudit = { updatedBy: string };
type AuditedEntity = Entity & WithAudit;
const ae: AuditedEntity = { id: "y", createdAt: 2, updatedBy: "ada" };
console.assert(ae.updatedBy === "ada", "intersection type combines shapes");

// Declaration merging: two interfaces with the same name MERGE. (Only interfaces —
// `type` cannot be re-opened.) Used to augment library types.
interface Box {
  width: number;
}
interface Box {
  height: number;
}
const bx: Box = { width: 2, height: 3 };
console.assert(bx.width * bx.height === 6, "interface declaration merging");

console.log("Phase 3: all object/class assertions passed.");

export {}; // make this file a module (isolates top-level declarations per phase)
