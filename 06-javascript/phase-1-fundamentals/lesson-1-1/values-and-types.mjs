/*
 * Lesson 1.1 — Values & types; dynamic typing
 *
 * Run:  node values-and-types.mjs
 *
 * THE BIG SHIFT FROM JAVA:
 *   Java: a VARIABLE has a type, fixed at compile time. `int x` is forever int.
 *   JS:   a VALUE has a type; a variable is just a name that can hold any value,
 *         and can hold a different type a moment later. Types are checked at
 *         RUNTIME, not by a compiler. This is "dynamic typing".
 *
 * There is no `javac` here. The engine (V8, in Node) reads and runs your
 * source directly — so a "type error" is a runtime surprise, not a build
 * failure. (That pain is exactly why TypeScript exists — track 05.)
 */

console.log("=== the 7 primitive types + object ===");

// Unlike Java's 8 numeric-ish primitives, JS has ONE number type: `number`,
// a 64-bit IEEE 754 double. No int/long/float distinction at all.
const n = 42;
const pi = 3.14;
const big = 9_000_000_000;      // still a `number` — no `long`, no L suffix
console.log(typeof n, typeof pi, typeof big);   // number number number

// Because every number is a double, integer math past 2^53 loses precision —
// the same IEEE 754 lesson from Java 1.2, but now it's ALL numbers:
console.log("0.1 + 0.2 =", 0.1 + 0.2);                    // 0.30000000000000004
console.log("2^53 + 1 =", 2 ** 53 + 1 === 2 ** 53);       // true (!) precision lost

// For real big integers there's a separate primitive type, `bigint`:
const huge = 9_000_000_000_000_000_000n;   // the `n` suffix makes a bigint
console.log(typeof huge, huge + 1n);       // bigint 9000000000000000001n

// string — no separate `char` type; single or double quotes are identical.
const s = "javascript";
console.log(typeof s, s.length, s.toUpperCase());

// boolean
console.log(typeof true);

// THE TWO "EMPTY" VALUES — JS has two, and the distinction matters:
let notAssigned;                 // declared, no value -> `undefined`
const deliberatelyEmpty = null;  // YOU set it to "no value on purpose"
console.log(typeof notAssigned, notAssigned);      // undefined undefined
console.log(typeof deliberatelyEmpty, deliberatelyEmpty);  // object null (!!)
// `typeof null === "object"` is a 25-year-old BUG in the language, kept
// forever for backward compatibility. Interviewers LOVE asking about it.

// symbol — unique, unforgeable keys (used for meta-behavior; more later)
const id = Symbol("id");
console.log(typeof id);

console.log("\n=== dynamic typing in action ===");

// One variable, three types over its life — perfectly legal, no compiler to stop it:
let thing = 42;          console.log(typeof thing, thing);
thing = "now a string";  console.log(typeof thing, thing);
thing = true;            console.log(typeof thing, thing);
// In Java this wouldn't compile. Here it just... runs. Power and footgun both.

console.log("\n=== everything non-primitive is an object ===");

// Arrays, functions, dates, plain {} — all `object` (functions report "function",
// but a function IS an object with a callable behavior).
const arr = [1, 2, 3];
const obj = { name: "ajay", role: "dev" };
const fn = (x) => x * 2;
console.log(typeof arr, Array.isArray(arr));   // object true  (typeof can't detect arrays!)
console.log(typeof obj);                        // object
console.log(typeof fn);                         // function

// Primitives are IMMUTABLE and compared BY VALUE; objects are mutable and
// compared BY REFERENCE (same as Java's == on objects):
console.log("2 === 2:", 2 === 2);                        // true
console.log("[1] === [1]:", [1] === [1]);                // false — different objects
const a = { x: 1 };
const b = a;
b.x = 99;
console.log("shared reference:", a.x);                    // 99 — a and b are the same object

console.log("\n=== the wrapper-method trick ===");

// Primitives have no methods, yet `"hi".toUpperCase()` works. Why?
// JS auto-boxes the primitive into a temporary String object, calls the
// method, then throws the wrapper away — like Java autoboxing, but invisible
// and instantaneous. (So you never write `new String(...)`; don't.)
console.log("hello".toUpperCase(), (5).toString(), (3.14159).toFixed(2));

console.log("\n=== a value's type, at a glance ===");
for (const v of [42, "s", true, undefined, null, [1], { a: 1 }, () => {}, 10n]) {
    // Note: String(v) not JSON.stringify(v) — JSON.stringify THROWS on a
    // bigint and returns undefined for a function. String() handles all.
    const label = Array.isArray(v) ? "(array)" : String(v);
    console.log(String(typeof v).padEnd(10), label);
}
