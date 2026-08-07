/*
 * Phase 4 — Arrays & collections
 * Run:  node arrays-and-collections.mjs
 *
 * Covers 4.1 array methods (map/filter/reduce — Java Streams déjà vu),
 * 4.2 destructuring/spread/iteration, 4.3 Map/Set/JSON.
 */

// ===========================================================================
// 4.1 — Array methods (your Java Streams knowledge transfers directly)
// ===========================================================================
console.log("=== 4.1 array methods ===");

const nums = [1, 2, 3, 4, 5, 6, 7, 8];

// The core trio — but note: unlike Java Streams, these run EAGERLY (no
// terminal op needed) and return new ARRAYS, not a lazy pipeline.
console.log("map:", nums.map((n) => n * n));
console.log("filter:", nums.filter((n) => n % 2 === 0));
console.log("reduce:", nums.reduce((sum, n) => sum + n, 0));         // 36

// reduce is the swiss army knife — build any shape (here, a histogram):
const words = ["apple", "banana", "avocado", "cherry", "blueberry"];
const byFirstLetter = words.reduce((acc, w) => {
    (acc[w[0]] ??= []).push(w);          // ??= : init the array if missing
    return acc;
}, {});
console.log("reduce->groups:", byFirstLetter);

// Search & test:
console.log("find:", nums.find((n) => n > 5));                       // 6
console.log("findIndex:", nums.findIndex((n) => n > 5));             // 5
console.log("some/every:", nums.some((n) => n > 7), nums.every((n) => n > 0));
console.log("includes:", nums.includes(3));

// Chaining (the Streams-like pipeline):
const result = words
    .filter((w) => w.length > 5)
    .map((w) => w.toUpperCase())
    .sort();
console.log("chain:", result);

// GOTCHA: sort() is IN-PLACE and sorts as STRINGS by default!
console.log("bad sort:", [10, 2, 1, 20].sort());                    // [1, 10, 2, 20] (!)
console.log("num sort:", [10, 2, 1, 20].sort((a, b) => a - b));     // [1, 2, 10, 20]

// flat / flatMap:
console.log("flat:", [[1, 2], [3, [4]]].flat());                    // [1,2,3,[4]]
console.log("flatMap:", [1, 2, 3].flatMap((n) => [n, n * 10]));     // [1,10,2,20,3,30]

// Mutating vs non-mutating (know which is which — React needs non-mutating):
const arr = [1, 2, 3];
console.log("non-mutating slice:", arr.slice(1), "orig:", arr);     // [2,3] [1,2,3]
const copy = [...arr];
copy.push(4);                                                        // mutate the COPY
console.log("immutable add:", arr, copy);                           // [1,2,3] [1,2,3,4]

// ===========================================================================
// 4.2 — Destructuring, spread, iteration protocols
// ===========================================================================
console.log("\n=== 4.2 destructuring & iteration ===");

// Array destructuring by position:
const [first, second, ...others] = [10, 20, 30, 40];
console.log("destructure:", first, second, others);                 // 10 20 [30,40]

// Skip elements, defaults, swap:
const [, , third = 0] = [1, 2];                                     // third defaults to 0
let a = 1, b = 2;
[a, b] = [b, a];                                                     // swap without a temp!
console.log("skip/default/swap:", third, a, b);                     // 0 2 1

// Destructuring in function params (React/Node everywhere):
const dist = ({ x, y }) => Math.hypot(x, y);
console.log("param destructure:", dist({ x: 3, y: 4 }));            // 5

// ITERATION PROTOCOL — for...of works on anything ITERABLE (arrays, strings,
// Map, Set, generators). Objects are NOT iterable by default.
for (const [i, v] of ["a", "b", "c"].entries()) {                  // index + value
    process.stdout.write(`${i}:${v} `);
}
console.log();

// ===========================================================================
// 4.3 — Map, Set, and JSON
// ===========================================================================
console.log("\n=== 4.3 Map / Set / JSON ===");

// SET — unique values (like Java HashSet). Great for dedupe:
const tags = new Set(["js", "ts", "js", "react", "ts"]);
console.log("set:", [...tags], "size:", tags.size);                 // ['js','ts','react'] 3
console.log("dedupe array:", [...new Set([1, 1, 2, 3, 3])]);       // [1,2,3]
console.log("has:", tags.has("react"));

// MAP — key/value with ANY key type (objects too!), insertion order kept.
// Prefer over plain objects for dynamic dictionaries (real size, any key,
// no prototype-key collisions).
const scores = new Map();
scores.set("ajay", 90).set("ravi", 85);          // set returns the map -> chainable
const objKey = { id: 1 };
scores.set(objKey, "object as key!");            // impossible with plain objects
console.log("map get:", scores.get("ajay"), "size:", scores.size);
for (const [name, score] of scores) {            // Maps are directly iterable
    if (typeof score === "number") console.log(`  ${name} -> ${score}`);
}

// Object vs Map: object keys are always strings/symbols; Map keeps any type
// and any insertion order, and has a clean .size / iteration.

// JSON — the wire format. stringify (serialize) / parse (deserialize):
const data = { name: "Ajay", roles: ["dev", "lead"], active: true, joined: null };
const json = JSON.stringify(data);
console.log("stringify:", json);
const back = JSON.parse(json);
console.log("parse -> object:", back.roles[1]);                    // 'lead'

// JSON gotchas: undefined/functions are DROPPED; dates become strings;
// Map/Set don't serialize; and stringify takes a pretty-print arg:
console.log("pretty:", JSON.stringify({ a: 1, b: 2 }, null, 2));
console.log("drops undefined:", JSON.stringify({ x: undefined, y: 1 }));  // {"y":1}
