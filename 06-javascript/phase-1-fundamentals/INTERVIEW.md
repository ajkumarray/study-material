<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — JavaScript Fundamentals: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 1.1 — Values & types

**Q: What are the primitive types in JavaScript?** ⭐
Seven: `number`, `bigint`, `string`, `boolean`, `undefined`, `null`, `symbol`. Everything else is an `object` (arrays, functions, dates, plain objects). Primitives are immutable and compared by value; objects are mutable and compared by reference.

**Q: Static vs dynamic typing — where does JS sit, and what's the trade-off?** ⭐
Dynamically typed: values carry types, variables don't, and checks happen at runtime — no compile step catches type mismatches. Trade-off: fast to write and flexible, but a whole class of errors surfaces only at runtime. TypeScript adds a static type layer on top to recover compile-time safety.

**Q: `undefined` vs `null`?** ⭐⭐
`undefined` = the engine's "no value yet" — uninitialized variables, missing properties, functions with no `return`, unpassed parameters. `null` = the programmer's explicit "intentionally empty." They're loosely equal (`null == undefined` is `true`) but strictly unequal (`null === undefined` is `false`).

**Q: Why is `typeof null === "object"`?** ⭐
A bug from JavaScript's 1995 implementation (values were tagged by type bits; `null` was the null pointer, tag `000`, same as objects). Fixing it would break existing code, so it's frozen forever. To test for null: `x === null`.

**Q: How many number types does JS have?**
One: `number`, a 64-bit IEEE 754 double (plus a separate `bigint` primitive for arbitrary-precision integers). No int/long/float. So all the floating-point caveats (`0.1 + 0.2 !== 0.3`, precision loss above 2^53) apply to *every* number.

**Q: How do you reliably check if something is an array?** ⭐
`Array.isArray(x)`. Not `typeof` — it returns `"object"` for arrays. (`instanceof Array` mostly works but breaks across different execution realms/iframes; `Array.isArray` doesn't.)

**Q: What does `typeof` return for a function?**
`"function"` — a special case. Functions are objects (callable ones), but `typeof` distinguishes them. For everything else object-like (arrays, null, dates, plain objects) it returns `"object"`.

**Q: Are strings mutable in JS?**
No — like Java, strings are immutable; methods like `toUpperCase()` return new strings. (JS has no `StringBuilder`; building strings in loops uses arrays + `join`, or template literals.)

**Q: What happens when you call a method on a primitive, e.g. `"hi".length`?**
JS temporarily wraps the primitive in its object wrapper (`String`, `Number`, `Boolean`), reads the property/method, then discards the wrapper — automatic, invisible boxing. This is why you should never explicitly `new String(...)`: it makes a real object where `typeof` is `"object"` and `===` comparisons fail.

## 1.2 — var / let / const, scope, hoisting

**Q: Difference between `var`, `let`, and `const`?** ⭐⭐
`var`: function-scoped, hoisted-and-initialized-to-undefined, reassignable — legacy. `let`: block-scoped, in the TDZ until declared, reassignable. `const`: block-scoped, TDZ, *not reassignable*. Modern practice: const by default, let when reassignment is needed, never var.

**Q: Does `const` make the value immutable?** ⭐
No — it makes the *binding* immutable. `const arr = []; arr.push(1)` is fine; `arr = []` throws. For value immutability use `Object.freeze` (shallow) or immutable data patterns.

**Q: What is hoisting?** ⭐⭐
Declarations are processed before code runs. `var` and function *declarations* are hoisted to the top of their scope — `var` initialized to `undefined`, functions fully (callable before their line). `let`/`const` are hoisted but uninitialized (TDZ). Function *expressions* aren't callable early (the variable is in the TDZ).

**Q: What is the Temporal Dead Zone?** ⭐
The span from the top of a block to a `let`/`const` declaration where the variable exists but can't be accessed — touching it throws `ReferenceError`. It exists to make "use before declaration" a loud error instead of a silent `undefined` (the `var` behavior).

**Q: Why does `for (var i…)` with closures print the final value, and `let` fixes it?** ⭐⭐ *the classic*
`var` is function-scoped: all iterations share one `i`, so every closure reads the same variable, which ends at its final value (`[3,3,3]`). `let` creates a *new binding per iteration*, so each closure captures its own copy (`[0,1,2]`). This is the flagship reason `let` was introduced.

**Q: What is variable shadowing / scope chain?**
Inner scopes can declare a variable with the same name as an outer one (shadowing); name lookup walks outward through enclosing scopes (the scope chain) until found or ReferenceError. Closures (Phase 2) capture variables via this chain.

## 1.3 — Operators & coercion

**Q: `==` vs `===`?** ⭐⭐ *guaranteed*
`===` strict: no coercion, must match type and value. `==` loose: coerces types before comparing, producing surprises (`0 == ''`, `1 == '1'`, `[] == false` all true). Always use `===`; the only reasonable `==` is `x == null` to catch null-or-undefined.

**Q: List the falsy values.** ⭐
Eight: `false`, `0`, `-0`, `0n`, `""`, `null`, `undefined`, `NaN`. Everything else is truthy — including `"0"`, `"false"`, `[]`, `{}`, `" "`.

**Q: What do `&&` and `||` return?** ⭐
Not booleans — an operand. `&&` returns the first falsy operand or the last one; `||` returns the first truthy operand or the last one; both short-circuit. Enables `a || defaultValue`, though `??` is safer when `0`/`""` are valid values.

**Q: `||` vs `??`?** ⭐
`||` falls back on any falsy value; `??` (nullish coalescing) falls back only on `null`/`undefined`. Use `??` for defaults when `0`, `""`, or `false` are legitimate inputs — `0 || 5` is `5` (bug), `0 ?? 5` is `0` (correct).

**Q: `1 + '2'` vs `'5' - 2`? Explain.** ⭐
`1 + '2'` is `'12'`: `+` prefers string concatenation when either operand is a string, coercing the number. `'5' - 2` is `3`: `-` has no string meaning, so both coerce to number. The `+`-vs-other-operators asymmetry is the root of most coercion "wat" moments.

**Q: Why is `NaN === NaN` false? How do you test for NaN?** ⭐
IEEE 754 defines NaN as unequal to everything, itself included (it represents "an invalid result," and two invalid results aren't "the same"). Test with `Number.isNaN(x)` (or `Object.is(x, NaN)`), never `x === NaN`. `typeof NaN` is ironically `'number'`.

## 1.4 — Control flow & functions

**Q: `for...of` vs `for...in`?** ⭐
`for...of` iterates the *values* of an iterable (arrays, strings, Maps, Sets). `for...in` iterates *enumerable keys* (property names) and is meant for objects — on arrays it yields string indices and includes inherited props, so it's the wrong tool there. Rule: `for...of` for arrays, `for...in` (or `Object.keys`) for objects.

**Q: Function declaration vs function expression?** ⭐
Declarations (`function f(){}`) are hoisted — callable before their line. Expressions (`const f = function(){}` / arrow) are not — only the variable binding hoists (into the TDZ for const/let). Declarations for top-level named functions; expressions for callbacks and when you want no hoisting.

**Q: Does JavaScript support method overloading?**
No — one name is one function; a later definition replaces an earlier one. Dynamic typing makes signature-based overloading meaningless. You branch on `arguments.length`/types inside, or (better) use default and rest parameters.

**Q: Rest vs spread — both are `...`?** ⭐
Rest *gathers*: in a parameter list, `...args` collects remaining arguments into an array. Spread *expands*: in a call/array/object, `...arr` explodes elements out (`f(...arr)`, `[...a, ...b]`, `{...o1, ...o2}`). Same syntax, opposite directions — context tells them apart.

**Q: What happens if you call a function with too few or too many arguments?**
No error. Missing parameters are `undefined`; extra arguments are ignored (still accessible via the `arguments` object or captured with rest). This is why defensive defaults and rest params matter.

**Q: What are template literals?**
Backtick strings supporting `${expression}` interpolation and real multiline text — the standard way to build strings in modern JS (replacing `+` concatenation). Tagged templates (a function before the backticks) allow custom processing (used by libraries like styled-components and for safe SQL/HTML).
