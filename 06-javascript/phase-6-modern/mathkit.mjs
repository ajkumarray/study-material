/*
 * A module demonstrating EXPORTS (lesson 6.1).
 * Each file is its own module with its own scope; nothing is global.
 */

// NAMED exports — export as many as you like, imported by exact name:
export const PI = 3.14159;
export function square(x) { return x * x; }
export const cube = (x) => x ** 3;

// A private helper — NOT exported, so it's invisible outside this file
// (true module encapsulation, unlike a plain script's shared globals):
function secretFactor() { return 42; }
export function withSecret(x) { return x * secretFactor(); }

// DEFAULT export — the module's "main" thing; imported under any name:
export default class Calculator {
    add(a, b) { return a + b; }
    mul(a, b) { return a * b; }
}
