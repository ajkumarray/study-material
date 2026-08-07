/*
 * Lesson 1.4 — Control flow & functions
 *
 * Run:  node control-flow-and-functions.mjs
 *
 * Control flow looks just like Java (if/else, for, while, switch, ternary) —
 * so we skip the basics and focus on what's DIFFERENT, then dig into
 * functions, where JS diverges hard (and where Phase 2 begins).
 */

console.log("=== control flow: the JS-specific bits ===");

// for...of iterates VALUES of an iterable (like Java's enhanced for):
for (const c of "js") process.stdout.write(c + " ");
console.log();

// for...in iterates KEYS (property names) — for objects, NOT arrays.
// (On arrays it gives string indices "0","1" — a classic bug; use for...of.)
const scores = { math: 90, cs: 95 };
for (const key in scores) console.log("  for...in:", key, "=", scores[key]);

// switch uses === matching (no coercion) and still falls through without break:
function classify(n) {
    switch (true) {                    // switch(true) + boolean cases = clean ranges
        case n < 0:  return "negative";
        case n === 0: return "zero";
        default:     return "positive";
    }
}
console.log("classify(-5, 0, 7):", classify(-5), classify(0), classify(7));

console.log("\n=== four ways to make a function ===");

// 1. Function DECLARATION — hoisted (callable before its line, lesson 1.2)
function add(a, b) {
    return a + b;
}

// 2. Function EXPRESSION — assigned to a variable, not hoisted
const subtract = function (a, b) {
    return a - b;
};

// 3. ARROW function — concise; the key semantic difference (no own `this`)
//    is Phase 2.3, but syntactically: params => expression is an implicit return
const multiply = (a, b) => a * b;
const square = (x) => x * x;              // one param, parens optional
const makeTag = () => ({ tag: "div" });  // return an object literal -> wrap in ()

// 4. METHODS — functions stored on objects (shorthand syntax):
const calc = {
    value: 10,
    double() { return this.value * 2; },   // `this` = calc (Phase 2.3)
};

console.log(add(2, 3), subtract(9, 4), multiply(3, 4), square(5), calc.double());
console.log("arrow returning object:", makeTag());

console.log("\n=== functions are VALUES (first-class) ===");

// The idea that unlocks all of functional JS (Phase 2): functions can be
// stored, passed, and returned like any value. Array methods rely on it.
const ops = [add, subtract, multiply];               // array OF functions
console.log("apply each:", ops.map((f) => f(6, 2))); // [8, 4, 12]

// A function that RETURNS a function (a closure — Phase 2.2 proper):
const times = (n) => (x) => x * n;
const triple = times(3);
console.log("triple(10):", triple(10));              // 30

console.log("\n=== parameters: defaults, rest, spread ===");

// DEFAULT parameters (like nothing in old Java; cleaner than overloading):
function greet(name, greeting = "Hello") {
    return `${greeting}, ${name}!`;                  // template literal, backticks
}
console.log(greet("Ajay"), "|", greet("Ravi", "Hi"));

// REST parameters — gather "the rest" into a real array (Java's varargs,
// but an actual Array, not a quirky T[]):
function sum(...nums) {                               // nums is a real array
    return nums.reduce((total, n) => total + n, 0);
}
console.log("sum():", sum(), "sum(1,2,3,4):", sum(1, 2, 3, 4));

// SPREAD — the mirror image: explode an array INTO arguments (or elements):
const numbers = [5, 10, 15];
console.log("spread into args:", sum(...numbers));   // sum(5,10,15)
console.log("Math.max(...):", Math.max(...numbers)); // 15
console.log("array clone:", [...numbers, 20]);       // [5,10,15,20]

console.log("\n=== arguments quirks vs Java ===");

// JS does NOT check argument count. Missing args become `undefined`;
// extra args are ignored (but see rest params to capture them):
function pair(a, b) {
    return [a, b];
}
console.log("too few :", pair(1));        // [1, undefined] — no error!
console.log("too many:", pair(1, 2, 3));  // [1, 2] — extra dropped

// There's no method overloading (dynamic typing makes it meaningless) —
// one function name = one function. You branch on arguments.length or types
// inside instead. Default + rest params usually replace the need entirely.

console.log("\n=== template literals & tagged templates ===");
const who = "world", n = 3;
console.log(`multiline
and interpolated: ${who}, ${n * n} squared`);        // backticks, ${} expressions
