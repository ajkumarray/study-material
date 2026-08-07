/*
 * Phase 6 — Modern JS & modules
 * Run:  node modern-js.mjs
 *
 * Covers 6.1 ES modules, 6.2 iterators/generators, 6.3 optional chaining/
 * nullish, 6.4 error handling.
 */

// ===========================================================================
// 6.1 — ES modules (import / export)
// ===========================================================================
console.log("=== 6.1 modules ===");

// Default import (any name) + named imports (exact names) from one file:
import Calculator, { PI, square, cube, withSecret } from "./mathkit.mjs";
// import * as math from "./mathkit.mjs";   // namespace import — everything under `math`

console.log("named:", PI, square(5), cube(3));
console.log("uses private helper:", withSecret(2));   // 84 — secretFactor() is hidden
console.log("default:", new Calculator().add(2, 3));

// ES modules vs CommonJS (Node's older require/module.exports):
//   ESM:  import/export, static (analyzable at build time), async, .mjs or
//         "type":"module". THE standard — browsers + modern Node.
//   CJS:  const x = require('...'); module.exports = ... — dynamic, sync,
//         legacy Node. You'll still see it in older packages.
// Static imports enable "tree shaking": bundlers drop unused exports (7.1).

// ===========================================================================
// 6.2 — Iterators & generators
// ===========================================================================
console.log("\n=== 6.2 iterators & generators ===");

// A GENERATOR function (function*) can PAUSE at `yield` and resume later.
// It produces values lazily, on demand — like a Java Stream/Iterator, but
// you write the production logic imperatively.
function* range(start, end, step = 1) {
    for (let i = start; i < end; i += step) {
        yield i;                                    // pause here, hand back a value
    }
}
console.log("generator:", [...range(0, 10, 2)]);   // [0,2,4,6,8]
for (const n of range(1, 4)) process.stdout.write(n + " ");   // iterable
console.log();

// LAZY / infinite sequences — impossible with a plain array, natural here:
function* naturals() {
    let n = 1;
    while (true) yield n++;                          // infinite, but lazy
}
const gen = naturals();
console.log("lazy infinite:", gen.next().value, gen.next().value, gen.next().value); // 1 2 3

// Custom iterable — implement Symbol.iterator to make any object for...of-able:
const playlist = {
    songs: ["a", "b", "c"],
    [Symbol.iterator]() {
        let i = 0;
        return { next: () => i < this.songs.length
            ? { value: this.songs[i++], done: false }
            : { value: undefined, done: true } };
    },
};
console.log("custom iterable:", [...playlist]);

// ===========================================================================
// 6.3 — Optional chaining & nullish coalescing
// ===========================================================================
console.log("\n=== 6.3 optional chaining & nullish ===");

const user = { name: "Ajay", address: { city: "Pune" } };
const guest = { name: "Guest" };                    // no address

// ?. short-circuits to undefined instead of throwing on null/undefined —
// no more `user && user.address && user.address.city` ladders:
console.log("deep access:", user?.address?.city);           // 'Pune'
console.log("missing safe:", guest?.address?.city);         // undefined (no crash)
console.log("optional call:", user.greet?.());              // undefined (method absent)
console.log("optional index:", user?.tags?.[0]);            // undefined

// ?? provides a default ONLY for null/undefined (not for 0/''/false — 1.3):
console.log("nullish default:", guest?.address?.city ?? "unknown");   // 'unknown'
const settings = { volume: 0 };
console.log("?? keeps 0:", settings.volume ?? 100);         // 0 (|| would give 100)

// Combine — the modern safe-access idiom:
const port = user?.config?.port ?? 8080;
console.log("combined:", port);

// ===========================================================================
// 6.4 — Error handling
// ===========================================================================
console.log("\n=== 6.4 error handling ===");

// try/catch/finally — like Java, but you can throw ANY value (throw
// strings, objects...). Best practice: always throw Error instances.
class ValidationError extends Error {               // custom error type
    constructor(message, field) {
        super(message);
        this.name = "ValidationError";              // set name for good stack traces
        this.field = field;                         // attach structured data
    }
}

function validateAge(age) {
    if (typeof age !== "number") throw new ValidationError("must be a number", "age");
    if (age < 0) throw new ValidationError("must be non-negative", "age");
    return age;
}

for (const input of [25, -5, "oops"]) {
    try {
        console.log("valid:", validateAge(input));
    } catch (e) {
        // Narrow by type, like a Java catch hierarchy:
        if (e instanceof ValidationError) {
            console.log(`  ValidationError on '${e.field}': ${e.message}`);
        } else {
            throw e;                                 // rethrow unexpected errors
        }
    }
}

// Async errors: a rejected promise in an async function is caught by
// try/catch around the await (shown in Phase 5). Uncaught promise
// rejections crash the process in modern Node — always handle them.
await Promise.reject(new Error("handled")).catch((e) =>
    console.log("async error caught:", e.message));

// The `cause` option chains errors (like Java's exception cause):
try {
    try {
        throw new Error("low-level DB error");
    } catch (dbErr) {
        throw new Error("failed to load user", { cause: dbErr });
    }
} catch (e) {
    console.log("error with cause:", e.message, "<-", e.cause.message);
}

console.log("\nphase 6 done.");
