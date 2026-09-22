<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — JavaScript Fundamentals: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## Values & Types

**Q: What are the primitive types in JavaScript?** ⭐

Seven: `number`, `bigint`, `string`, `boolean`, `undefined`, `null`, and `symbol`. Everything else — arrays, functions, dates, plain object literals — is an `object`. The distinction matters because primitives are compared **by value** and are immutable, while objects are compared **by reference** and are mutable. `2 === 2` is `true` because both sides are the same value, but `[1] === [1]` is `false` because they're two different array objects that happen to look alike.

Follow-up: *is a function a primitive or an object?* It's an object — a callable one — which is why `typeof` gives it a special-cased result of `"function"` rather than the generic `"object"` it returns for everything else non-primitive.

**Q: Static vs dynamic typing — where does JS sit, and what's the trade-off?** ⭐

JavaScript is dynamically typed: types are attached to *values*, not to *variables*, and type checking happens at runtime rather than at compile time. A variable declared with a number today can hold a string tomorrow, and nothing stops that until the code actually runs and something breaks. The trade-off is flexibility and speed of writing code versus losing an entire category of compile-time safety net — a whole class of bugs (calling `.toUpperCase()` on a number, say) only shows up when that exact code path executes. TypeScript exists specifically to add a static type layer back on top without changing the runtime.

**Q: `undefined` vs `null` — what's the difference, and when does each show up?** ⭐⭐

`undefined` is the engine's own "nothing here yet" — it's what you get from a declared-but-unassigned variable, a missing object property, a function with no `return` statement, or a parameter that wasn't passed. `null` is the programmer's explicit "this is intentionally empty" — you have to write `= null` yourself; the engine never produces it on its own. They're loosely equal (`null == undefined` is `true`, a special case carved out in the spec) but strictly unequal (`null === undefined` is `false`, since they're different types).

Follow-up: *how would you check for "either null or undefined" in one line?* `if (x == null)` — the one place `==` is idiomatic in modern JS, because it's specifically checking "is this one of the two empties," not doing general type coercion.

**Q: Why does `typeof null === "object"`?** ⭐

It's a bug from the original 1995 JS implementation. Values were internally tagged with a type identifier in their low bits, and objects were tagged `000`. `null` was represented as the all-zero pointer, which happened to share that same tag — so `typeof` reports `"object"` for it. It's been in the language for 30 years and can never be fixed without breaking existing code that (knowingly or not) depends on it, so it's permanent. To reliably check for `null`, always use `x === null`, never `typeof x === "object"`.

**Q: How many numeric types does JavaScript actually have?**

Functionally one: `number`, a single 64-bit IEEE 754 double, used for everything from `1` to `3.14` to `-7`. There's no `int`/`long`/`float`/`short` split like Java. `bigint` exists as a *separate* type (literal suffix `n`) for arbitrary-precision integers, but you can't mix a `bigint` and a `number` in the same arithmetic expression without an explicit conversion. Since everything is a double, you inherit floating-point rounding error (`0.1 + 0.2 !== 0.3`) and integer precision loss above `2 ** 53`.

**Q: How do you reliably check whether a value is an array?** ⭐

`Array.isArray(x)`. `typeof` is useless here — it returns `"object"` for arrays, same as plain objects. `x instanceof Array` mostly works too, but breaks across different execution "realms" (e.g. an array passed from an iframe or a different VM context has a different `Array` constructor), which `Array.isArray` correctly handles.

**Q: What happens when you call a method on a primitive, like `"hi".toUpperCase()`?**

JavaScript performs invisible **auto-boxing**: it temporarily wraps the primitive string in a `String` object, calls the method on that wrapper, returns the result, and immediately discards the wrapper. It's conceptually similar to Java's autoboxing (`int` ↔ `Integer`), except it's fully automatic and momentary — you never see or hold the wrapper object. The practical rule that follows: never write `new String("x")` yourself; it produces a real, persistent object where `typeof` is `"object"` and `===` comparisons against string literals fail.

---

## `var` / `let` / `const`, Scope & Hoisting

**Q: What's the difference between `var`, `let`, and `const`?** ⭐⭐

`var` is function-scoped (or global), hoisted and initialized to `undefined` immediately, reassignable, and redeclarable — it's the legacy option and should be avoided in new code. `let` is block-scoped, hoisted but left uninitialized in the Temporal Dead Zone until its declaration line, reassignable, and cannot be redeclared in the same scope. `const` behaves exactly like `let` except it cannot be reassigned after its initial assignment. The modern convention is `const` by default, `let` only when the binding genuinely needs to change, and `var` essentially never.

**Q: Does `const` make a value immutable?** ⭐

No — it makes the **binding** immutable, not the value. `const arr = []; arr.push(1);` works fine because you're mutating the array's contents, not rebinding the name `arr` to a different array. `arr = []` (a rebind) would throw a `TypeError`. If you also need the *value* to be immutable, use `Object.freeze(obj)` — though note that's a shallow freeze; nested objects inside a frozen object are still mutable unless you freeze them too.

**Q: Explain the classic `var`-in-a-loop-with-closures bug, and how `let` fixes it.** ⭐⭐ *the classic*

```js
const fns = [];
for (var i = 0; i < 3; i++) fns.push(() => i);
console.log(fns.map(f => f()));  // [3, 3, 3]
```

`var` is function-scoped, so there is exactly one `i` for the entire loop — every closure pushed into `fns` captures a reference to that same variable, not a snapshot of its value. By the time any of the closures actually run, the loop has already finished and `i` sits at its final value, `3`. Swapping to `let i` fixes it because `let` creates a **brand-new binding of `i` for every iteration** of the loop — each closure captures its own private copy, so the result becomes `[0, 1, 2]`. This is the textbook reason `let` was added to the language, and it appears constantly in real code involving `setTimeout`, event listener registration, or async operations inside a loop.

Follow-up: *how would you fix this using only `var`, without switching to `let`?* Wrap the loop body in an IIFE that takes `i` as a parameter, creating a new scope per iteration manually — the pattern `let` was invented to make unnecessary.

**Q: What is hoisting?** ⭐⭐

Hoisting is JavaScript conceptually processing all declarations in a scope before running any code in it. What "processing" means differs by declaration type: function declarations are hoisted completely (name and body), so they're callable before their written line. `var` declarations are hoisted but only initialized to `undefined` — the assignment stays where it was written. `let`/`const` declarations are hoisted too, but are not initialized at all — they remain inaccessible (in the TDZ) until their declaration line runs. Function *expressions* and arrow functions assigned to a variable are only as hoisted as that variable is (so a `const`-assigned one is in the TDZ; a `var`-assigned one reads `undefined`).

**Q: What is the Temporal Dead Zone?** ⭐

The span between the top of a block and the line where a `let`/`const` variable is actually declared. The variable exists (it was hoisted, the engine knows the name), but it hasn't been initialized, so any read or write during that span throws a `ReferenceError` — "Cannot access 'x' before initialization." It exists specifically as a **safety improvement over `var`**: instead of a mis-ordered reference silently returning `undefined` and letting a bug propagate quietly, the TDZ makes it fail loudly at the exact point of the mistake.

```js
{
  console.log(a);   // ReferenceError — 'a' is in the TDZ
  let a = 3;
}
```

Follow-up: *does `var` have a TDZ?* No — `var` skips straight to being initialized to `undefined`, so there's no dead zone; reading it early just gives `undefined`, never a `ReferenceError`.

**Q: What is variable shadowing / the scope chain?**

An inner scope can declare a variable with the same name as one in an outer scope, and inside that inner scope the inner variable "shadows" (hides) the outer one — only the inner value is visible there. Name resolution works by walking the **scope chain**: JS looks in the current scope first, then each enclosing scope outward, until it finds the name or runs out of scopes and throws a `ReferenceError`. Closures (Phase 2) work by capturing a live reference into this chain, which is exactly how the `var`/`let` loop example above behaves differently.

---

## Operators & Coercion

**Q: `==` vs `===`?** ⭐⭐ *guaranteed*

`===` is strict equality — no type conversion happens; both the type and the value must match. `==` is loose equality — it coerces the operands to a common type first, which produces well-known surprises: `0 == ""` is `true`, `1 == "1"` is `true`, `[] == false` is `true`. The practical rule is to always use `===`/`!==`; the single defensible use of `==` in modern code is `x == null`, a deliberate idiom for "x is null or undefined" that relies on the spec's special-cased `null == undefined`.

**Q: List the eight falsy values.** ⭐

`false`, `0`, `-0`, `0n`, `""` (empty string), `null`, `undefined`, and `NaN`. Every other value is truthy — including ones people often assume are falsy, like the string `"0"`, the string `"false"`, an empty array `[]`, and an empty object `{}`.

**Q: What do `&&` and `||` actually return?** ⭐

Neither returns a boolean — they return one of their **operands**. `&&` evaluates left to right and returns the first falsy operand it hits, or the last operand if all are truthy. `||` returns the first truthy operand it hits, or the last operand if all are falsy. Both short-circuit — the right side is never evaluated if the left side already determined the result. This is what makes `a && a.b.c` a safe way to avoid a crash on `a` being `null`, and `a || fallback` a compact default-value idiom.

**Q: `||` vs `??` — when do they differ, and why does it matter?** ⭐

`||` falls back to its right operand whenever the left is *any* falsy value. `??` (nullish coalescing) falls back only when the left is specifically `null` or `undefined`. They differ exactly when `0`, `""`, or `false` are legitimate, intentional values: `const count = input.count || 10` incorrectly overrides an explicit `0` with `10`, while `const count = input.count ?? 10` correctly keeps the `0`. In production code where falsy-but-valid values are possible, `??` is the safer default.

**Q: Walk through `1 + '2'` vs `'5' - 2` — why do they behave differently?** ⭐

`1 + '2'` is `'12'`: the `+` operator checks whether either operand is a string, and if so, it concatenates — coercing the number `1` into `'1'` first. `'5' - 2` is `3`: `-` has no string meaning at all, so it always coerces both operands *toward* number instead, turning `'5'` into `5`. This asymmetry — `+` prefers strings, every other arithmetic operator prefers numbers — is responsible for most JS coercion "wat" moments, and interviewers love asking you to predict the output of a chain like `1 + 2 + '3' + 4 + 5` (`'33' + 4 + 5` → `'3345'`).

**Q: Why is `NaN === NaN` false? How do you correctly test for `NaN`?** ⭐

Per the IEEE 754 floating-point spec, `NaN` represents "the result of an invalid operation," and the spec defines it as unequal to every value, including another `NaN` — two different invalid results aren't considered "the same" value. `typeof NaN` is, ironically, `'number'`. Since `x === NaN` can never be `true` for any `x` (even `NaN` itself), you must use `Number.isNaN(x)` (or `Object.is(x, NaN)`) to test for it. `NaN` shows up from operations like `0/0`, `Number("abc")`, or `undefined + 1`.

---

## Control Flow & Functions

**Q: `for...of` vs `for...in`?** ⭐

`for...of` iterates the **values** of anything iterable — arrays, strings, `Map`s, `Set`s. `for...in` iterates the **enumerable property names (keys)** of an object, and is meant for plain objects, not arrays. Using `for...in` on an array technically works but yields the indices as *strings* (`"0"`, `"1"`, not `0`, `1`) and can also enumerate inherited properties — both are common bugs. Rule of thumb: `for...of` for arrays and other iterables, `for...in` (or better, `Object.keys`/`Object.entries`) for plain objects.

**Q: What's the difference between `for` and `forEach`, and when would you still reach for a plain `for` loop?** ⭐

`forEach` is an array method that runs a callback once per element and returns `undefined` — it's terser and communicates "I'm doing a side effect per element." Its major limitation is that you **cannot `break` or `continue`** out of it — the callback runs for every element unconditionally (short of throwing, which is a heavy-handed workaround). A classic `for` loop supports `break`/`continue` for early exit, and it's also the only loop form that plays well with `await` for sequential asynchronous work inside the loop body — `await` inside a `forEach` callback doesn't pause the outer loop, since `forEach` doesn't know or care that its callback returned a promise.

**Q: Function declaration vs function expression?** ⭐

A declaration (`function f() {}`) is fully hoisted — name and body — so it's callable anywhere in its scope, even above where it's written. An expression (`const f = function () {}`, including arrow functions) is not hoisted the same way — only the variable binding hoists, and depending on `const`/`let`/`var` it's either in the TDZ or `undefined` until the assignment line runs. Use declarations for top-level, broadly-reusable functions where hoisting is convenient; use expressions for callbacks, conditionally-defined functions, or anywhere you specifically want no hoisting.

**Q: Does JavaScript support method overloading?**

No. A function name always refers to exactly one function — if you "redefine" a function with the same name, the second definition simply replaces the first; there's no dispatch based on argument types or count the way Java resolves overloads at compile time. Dynamic typing makes signature-based overloading meaningless anyway, since there's no static type information to dispatch on. The idiomatic replacements are default parameters for optional arguments, rest parameters for variable-length argument lists, and manual branching on `arguments.length` or `typeof` inside a single function when behavior genuinely needs to vary by input shape.

**Q: Rest vs spread — they're both written as `...`, so what's the difference?** ⭐

Rest **gathers**: used in a function's parameter list, `...args` collects any remaining arguments into a real array (`function f(...args) {}`). Spread **expands**: used in a function call, array literal, or object literal, `...arr` explodes an existing iterable's elements out into individual items (`f(...arr)`, `[...a, ...b]`, `{...obj1, ...obj2}`). Same three-dot syntax, opposite direction — the surrounding context (parameter list vs. call/literal) tells them apart.

**Q: What happens if you call a JS function with too few or too many arguments?**

Nothing throws. Missing parameters simply become `undefined` inside the function body; extra arguments are silently ignored by name (though they're still reachable via the `arguments` object, or explicitly captured with a rest parameter). This is exactly why defensive default parameter values (`function f(x = 10)`) and rest parameters matter so much more in JS than in a statically-checked language — there's no compiler enforcing the contract for you.

**Q: What are template literals, and what do tagged templates add on top?**

Template literals are backtick-delimited strings supporting `${expression}` interpolation and genuine multi-line text without escape characters — the standard modern replacement for `+`-based string concatenation. A **tagged template** puts a function name directly before the backticks (`` html`<div>${x}</div>` ``); that function receives the literal string pieces and the interpolated values separately, letting it post-process the result — this is how libraries like styled-components implement CSS-in-JS, and how safe SQL/HTML-escaping tag functions work.
