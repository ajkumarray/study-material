/*
 * Lesson 1.3 — Operators & coercion: == vs ===, truthiness, the gotchas
 *
 * Run:  node operators-and-coercion.mjs
 *
 * This is JavaScript's most infamous topic — the "wat" talks are all about
 * COERCION: JS's habit of silently converting types to make an operation
 * work. Java would refuse ("+" between int and boolean = compile error).
 * JS just... finds a way, sometimes a deeply weird one. The rules are
 * learnable, and knowing them is core interview material.
 */

console.log("=== == vs === (the #1 rule) ===");

// === is STRICT equality: same type AND same value. No coercion. Use this.
// ==  is LOOSE equality: coerces types first, then compares. Avoid it.
console.log("1 === 1 :", 1 === 1);           // true
console.log("1 === '1':", 1 === "1");        // false — different types
console.log("1 == '1' :", 1 == "1");         // true  — '1' coerced to 1 (!)
console.log("0 == false:", 0 == false);      // true  — false -> 0
console.log("'' == false:", "" == false);    // true  — both -> 0
console.log("null == undefined:", null == undefined);  // true (special-cased)
console.log("null === undefined:", null === undefined);// false

// RULE: always use === and !==. The only defensible == is `x == null`,
// a deliberate idiom meaning "x is null OR undefined".

console.log("\n=== truthiness ===");

// Any value can be used as a boolean (in `if`, `&&`, `||`, `!`). There are
// exactly EIGHT falsy values; EVERYTHING else is truthy. Memorize the falsy list:
//   false, 0, -0, 0n, "", null, undefined, NaN
for (const v of [false, 0, "", null, undefined, NaN, 0n]) {
    console.log(`  falsy:  ${String(v).padEnd(10)} -> ${Boolean(v)}`);
}
// The surprises — these are all TRUTHY:
for (const v of ["0", "false", [], {}, " "]) {
    console.log(`  truthy: ${JSON.stringify(v).padEnd(10)} -> ${Boolean(v)}`);
}
// "[] is truthy but [] == false is true" is a classic head-scratcher —
// truthiness and == use DIFFERENT coercion paths. Don't rely on ==.

console.log("\n=== && and || return VALUES, not booleans ===");

// Unlike Java, && and || don't return true/false — they return one of the
// OPERANDS, short-circuiting (Java's short-circuit, but the value survives):
console.log("'a' && 'b':", "a" && "b");        // 'b'  (both truthy -> last)
console.log("'' && 'b' :", "" && "b");         // ''   (first falsy -> it)
console.log("'a' || 'b':", "a" || "b");        // 'a'  (first truthy -> it)
console.log("'' || 'x' :", "" || "x");         // 'x'  (first falsy, fall through)

// This powers the classic "default value" idiom (pre-2020):
const config = {};
const port = config.port || 3000;              // 3000 if port is falsy/missing
console.log("|| default:", port);

// ...but it has a bug: 0 and "" are valid values yet falsy, so || overrides
// them. The FIX is ?? (nullish coalescing) — only null/undefined trigger it:
const timeout = config.timeout ?? 5000;
const explicitZero = 0 ?? 99;                  // 0 (not null/undefined -> kept)
console.log("?? keeps 0:", explicitZero, ", ?? default:", timeout);

console.log("\n=== the + operator: math OR string glue ===");

// + is overloaded: number addition, but STRING CONCATENATION wins if either
// side is a string — and it coerces the other side to a string:
console.log("1 + 2      :", 1 + 2);            // 3
console.log("'1' + 2    :", "1" + 2);          // '12'  (2 -> '2')
console.log("1 + '2'    :", 1 + "2");          // '12'
console.log("1 + 2 + '3':", 1 + 2 + "3");      // '33'  (left-to-right: 3, then '33')
console.log("'1' + 2 + 3:", "1" + 2 + 3);      // '123' (string from the start)

// Other math operators (-, *, /) have no string meaning, so they coerce
// TO number instead — asymmetry that trips everyone:
console.log("'5' - 2    :", "5" - 2);          // 3   (string -> number)
console.log("'5' * '2'  :", "5" * "2");        // 10
console.log("'abc' - 1  :", "abc" - 1);        // NaN (can't numberify 'abc')

console.log("\n=== NaN — the value that isn't equal to itself ===");

console.log("NaN === NaN:", NaN === NaN);      // false (!!) — by IEEE 754 spec
console.log("Number.isNaN(NaN):", Number.isNaN(NaN));  // true — the correct test
console.log("typeof NaN:", typeof NaN);        // 'number' (ironically)
// NaN appears from invalid math: parseInt('x'), 0/0, undefined + 1, etc.
// To detect it you MUST use Number.isNaN — never x === NaN.

console.log("\n=== safe conversions (do these explicitly) ===");

console.log("Number('42') :", Number("42"));       // 42
console.log("Number('')   :", Number(""));         // 0  (surprise — '' -> 0)
console.log("parseInt('42px'):", parseInt("42px", 10));  // 42 (stops at non-digit)
console.log("String(42)   :", String(42));         // '42'
console.log("Boolean(0)   :", Boolean(0));         // false
// Explicit conversion = readable and predictable. Let implicit coercion
// happen and you'll ship a '12' where you meant 3.
