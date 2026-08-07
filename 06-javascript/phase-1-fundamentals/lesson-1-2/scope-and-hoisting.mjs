/*
 * Lesson 1.2 — Variables: var / let / const, scope, hoisting, the TDZ
 *
 * Run:  node scope-and-hoisting.mjs
 *
 * Java has ONE way to declare a local (`int x`) with block scope and no
 * surprises. JS has THREE keywords with different scoping and lifecycle
 * rules — and the differences are a huge share of JS interview questions.
 */

console.log("=== const / let / var: the rules ===");

// const — can't be REASSIGNED (default choice; use it 95% of the time)
const name = "ajay";
// name = "ravi";        // TypeError: Assignment to constant variable

// BUT const freezes the BINDING, not the VALUE. Objects stay mutable:
const user = { role: "dev" };
user.role = "lead";        // fine! we mutate the object, not rebind `user`
console.log("const object mutated:", user);   // { role: 'lead' }
// (To freeze the value too: Object.freeze(user) — lesson 3.x.)

// let — reassignable, block-scoped. Use when a value must change.
let count = 0;
count += 1;
console.log("let reassigned:", count);

// var — the LEGACY keyword. FUNCTION-scoped, not block-scoped. Avoid it.
// Modern code is const-by-default, let-when-needed, var-never.

console.log("\n=== block scope: let/const vs var ===");

{
    let blockScoped = "only in this block";
    var functionScoped = "leaks out of the block";
}
console.log("var leaked:", functionScoped);   // works — var ignores the { }
try {
    console.log(blockScoped);
} catch (e) {
    console.log("let did NOT leak:", e.constructor.name);   // ReferenceError
}

// The classic loop bug this causes:
console.log("\n=== the loop-closure classic ===");

const withVar = [];
for (var i = 0; i < 3; i++) {
    withVar.push(() => i);       // all closures share ONE function-scoped i
}
console.log("var loop:", withVar.map((f) => f()));   // [3, 3, 3] — surprise!

const withLet = [];
for (let j = 0; j < 3; j++) {
    withLet.push(() => j);       // let creates a NEW binding per iteration
}
console.log("let loop:", withLet.map((f) => f()));   // [0, 1, 2] — correct
// This single difference is why `let` was added. Interviewers love it.

console.log("\n=== hoisting ===");

// HOISTING: declarations are conceptually "moved to the top" of their scope
// during compilation. But var and let/const hoist DIFFERENTLY.

// var is hoisted AND initialized to `undefined` — so reading it early is
// legal but gives undefined (a silent bug):
console.log("var before decl:", typeof hoistedVar);   // undefined (not an error)
var hoistedVar = "assigned later";

// let/const are hoisted too, but NOT initialized — they sit in the
// "Temporal Dead Zone" (TDZ) from the top of the block until the
// declaration line. Touching them there THROWS:
try {
    console.log(hoistedLet);
    let hoistedLet = "x";
} catch (e) {
    console.log("let in TDZ:", e.constructor.name);    // ReferenceError
}
// The TDZ is a FEATURE: it turns "used before declared" from a silent
// undefined bug into a loud error.

console.log("\n=== function hoisting ===");

// FUNCTION DECLARATIONS are fully hoisted — callable before their line:
console.log("called before defined:", greet("ajay"));
function greet(n) {
    return "hi " + n;
}

// FUNCTION EXPRESSIONS assigned to const/let are NOT (the variable is in
// the TDZ, only the binding is hoisted):
try {
    shout("hey");
    const shout = (n) => n.toUpperCase();
} catch (e) {
    console.log("expression before decl:", e.constructor.name);  // ReferenceError
}

console.log("\n=== global scope note ===");
// In a Node ES module (.mjs), top-level `this` is undefined and there's no
// implicit global leakage — each module has its own scope. (In old browser
// scripts, a top-level var became a window property — a footgun ES modules
// fixed.) This file being .mjs is why `import`/top-level scoping is clean.
console.log("module-scoped, no globals leaked. done.");
