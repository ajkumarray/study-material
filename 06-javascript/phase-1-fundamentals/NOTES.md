<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
[Phase 2 · functions ➡](../phase-2-functions/NOTES.md)
<!-- /nav -->

# Phase 1 — JavaScript Fundamentals: Notes

*Taught against your Java knowledge — the contrast IS the lesson.*

## 1. Values, Types & Dynamic Typing

JavaScript is a **dynamically typed** language: types live on *values*, not on the variables that hold them. A variable is just a name — it can point at a number this line and a string the next, and nothing stops it at compile time because there is no compile time. The engine (V8 inside Node) parses and runs your source directly, so a "type error" shows up as a runtime surprise rather than a build failure. This single fact explains most of the JS-vs-Java culture shock, and it's exactly the gap TypeScript (track 07) was built to close.

### Key Concepts

- **Value has the type, not the variable**: `let x = 42; x = "now a string";` is perfectly legal — nothing like Java's `int x` binds the variable to a type forever.
- **7 primitive types + 1 object type**: `number`, `bigint`, `string`, `boolean`, `undefined`, `null`, `symbol`, and everything else is an `object`.
- **One numeric type**: `number` is a single 64-bit IEEE 754 double. There is no `int`/`long`/`float`/`short` split like Java.
- **`undefined` vs `null`**: both mean "empty," but `undefined` is the *engine's* default for "nothing assigned yet"; `null` is the *programmer's* deliberate "empty on purpose."
- **Primitives are immutable and compared by value**; **objects are mutable and compared by reference** — this half of JS behaves exactly like Java's object model.
- **Auto-boxing**: primitives borrow object methods (`"hi".toUpperCase()`) through invisible, temporary wrapper objects.

### Worked Example: the type zoo

```js
const n = 42;
const pi = 3.14;
console.log(typeof n, typeof pi);          // number number  — same type for int and float

console.log(0.1 + 0.2);                    // 0.30000000000000004 — IEEE 754 rounding
console.log(2 ** 53 + 1 === 2 ** 53);      // true — precision lost past 2^53

const huge = 9_000_000_000_000_000_000n;   // trailing `n` makes a bigint
console.log(typeof huge, huge + 1n);       // bigint 9000000000000000001n

let notAssigned;
const deliberatelyEmpty = null;
console.log(typeof notAssigned, notAssigned);              // undefined undefined
console.log(typeof deliberatelyEmpty, deliberatelyEmpty);  // object null  <- famous bug

const arr = [1, 2, 3];
console.log(typeof arr, Array.isArray(arr));  // object true — typeof can't see arrays
```

In this example: every number — whether it looks like an int or a float — reports `typeof "number"`, because JS never distinguishes them; only literals suffixed with `n` become the separate `bigint` type. `typeof null` printing `"object"` is not a design choice, it's a 25-year-old bug baked into the original 1995 implementation (values were tagged by a type bit pattern, and `null`'s pattern collided with the object tag); it's kept forever because fixing it would break the web. Because `typeof` reports `"object"` for arrays too, `Array.isArray()` is the only reliable array check.

### `undefined` vs `null` — comparison

| | `undefined` | `null` |
|---|---|---|
| Who sets it | The engine (default) | The programmer (explicit) |
| Meaning | "Not assigned yet" | "Deliberately empty" |
| `typeof` | `"undefined"` | `"object"` (bug) |
| `== undefined` | `true` | `true` |
| `=== undefined` | `true` | `false` |
| Typical source | Missing property, unset variable, missing return, unpassed argument | You wrote `= null` on purpose |

### Why It's Useful

Knowing the type system cold prevents entire bug classes: comparing objects with `===` and being confused why two identical-looking arrays aren't equal, assuming `parseInt`/`Number` behave like a cast, or being surprised that `"5" * "2"` works but `"5" + "2"` doesn't do what you expect (covered below in coercion). It's also the number-one whiteboard warm-up question in JS interviews — expect to be asked to list the primitives and explain `typeof null` from memory.

### Summary

- JS has 7 primitives (`number`, `bigint`, `string`, `boolean`, `undefined`, `null`, `symbol`) plus `object` for everything else.
- One numeric type for everything — watch for float rounding and the 2^53 precision cliff.
- `undefined` = engine default; `null` = deliberate emptiness.
- `typeof null === "object"` is a known bug, not a feature — memorize it.
- Primitives compare by value; objects compare by reference — use `Array.isArray()`, never `typeof`, to detect arrays.

---

## 2. `var`, `let`, and `const` — Declarations & Scope

Java gives you one way to declare a local variable, block-scoped, no surprises. JavaScript gives you three keywords — `var`, `let`, `const` — each with different scope and lifecycle rules, and the differences between them are one of the highest-yield topics in any JS interview.

### Key Concepts

- **`var` — Scope**: Function-scoped. A `var` declared anywhere inside a function is visible throughout that whole function, ignoring `{ }` block boundaries.
- **`var` — Reassignment & redeclaration**: Can be reassigned and redeclared freely in the same scope.
- **`let` — Scope**: Block-scoped — confined to the nearest enclosing `{ }` (`if`, `for`, a bare block, a function body).
- **`let` — Reassignment & redeclaration**: Can be reassigned; cannot be redeclared in the same scope.
- **`const` — Scope**: Block-scoped, same as `let`.
- **`const` — Reassignment**: Cannot be reassigned after initialization — but this only freezes the *binding*, not the value. `const obj = {}` still lets you mutate `obj.x = 1`; only `obj = {}` (a rebind) throws.
- **Modern rule of thumb**: `const` by default (~95% of declarations), `let` only when a value must change, `var` essentially never in new code.

### Worked Example: block scope leaking (or not)

```js
{
  let blockScoped = "only in this block";
  var functionScoped = "leaks out of the block";
}
console.log(functionScoped);   // "leaks out of the block" — var ignores { }
console.log(blockScoped);      // ReferenceError: blockScoped is not defined
```

In this example: `var` treats the block as transparent — the variable is really scoped to the enclosing function (or the module, at top level), so it's still readable after the block ends. `let` respects the block boundary, so `blockScoped` simply doesn't exist outside the `{ }` — attempting to read it throws a `ReferenceError`. This is the core reason `var` is considered dangerous: it silently leaks state further than it looks like it should.

### Worked Example: `const` freezes the binding, not the value

```js
const z = 30;
// z = 40;                 // TypeError: Assignment to constant variable

const arr = [1, 2, 3];
arr.push(4);               // fine — mutating contents, not rebinding the name
console.log(arr);          // [1, 2, 3, 4]

const user = { role: "dev" };
user.role = "lead";        // fine — same reason
console.log(user);         // { role: 'lead' }
```

`const` only locks the *name-to-value binding*. Rebinding `z` to a new primitive throws immediately, but mutating the contents of an object or array that `z`/`arr`/`user` points at is completely legal, because the binding itself (which object the name refers to) never changes. To freeze the value too, use `Object.freeze(obj)` (shallow — nested objects are still mutable).

### Worked Example: the classic loop-closure bug

```js
const withVar = [];
for (var i = 0; i < 3; i++) {
  withVar.push(() => i);
}
console.log(withVar.map(f => f()));   // [3, 3, 3] — surprise!

const withLet = [];
for (let j = 0; j < 3; j++) {
  withLet.push(() => j);
}
console.log(withLet.map(f => f()));   // [0, 1, 2] — correct
```

With `var`, there is exactly **one** `i` shared by every iteration (function scope), so all three closures capture the same variable — and by the time they're called, the loop has already finished and `i` is `3`. With `let`, the loop engine creates a **fresh binding of `j` for every iteration**, so each closure captures its own private copy. This single behavior difference is the textbook reason `let` was added to the language in ES6, and it's asked constantly.

### Comparison Table: `var` vs `let` vs `const`

| Feature | `var` | `let` | `const` |
|---|---|---|---|
| Scope | Function / global | Block | Block |
| Hoisting | Hoisted, initialized to `undefined` | Hoisted, but in TDZ | Hoisted, but in TDZ |
| Reassignment | Yes | Yes | No (binding only) |
| Redeclaration in same scope | Yes | No | No |
| Leaks out of `{ }` | Yes | No | No |
| Recommended usage | Avoid in new code | When value must change | Default choice |

### Why It's Useful

Choosing the right declaration keyword avoids two entire bug classes: accidental leakage of loop/temp variables into outer scope (`var`), and accidental reassignment of a reference that should never change (using `let` where `const` was intended signals "this can change" to every future reader — using `const` by default documents intent). The loop-closure difference between `var` and `let` shows up constantly in real code — event handler registration in a loop, `setTimeout` inside a loop, async operations inside `for` — and picking `let` is what makes the "obvious" version of that code actually correct.

### Summary

- `const` by default, `let` when it must change, `var` never in new code.
- `var` is function-scoped and leaks out of blocks; `let`/`const` are block-scoped.
- `const` freezes the binding, not the value — objects/arrays stay mutable.
- The `var`-in-a-loop-with-closures bug (`[3,3,3]`) is exactly why `let` exists — `let` gives each iteration its own binding.

---

## 3. Hoisting

Hoisting is the mechanism by which JavaScript conceptually processes variable and function **declarations** before running any code in a scope. It's not that code physically moves — the engine does a pass over the scope first, registering names, so you can sometimes reference something before the line where it's written. Exactly what happens on early access depends on *what* was declared.

### Key Concepts

- **Function declarations** (`function f() {}`) are hoisted completely — both the name and the function body — so they're callable anywhere in their scope, even above the line where they're written.
- **`var` declarations** are hoisted but only the declaration, not the assignment — the variable exists from the top of the scope, initialized to `undefined`, until the assignment line actually runs.
- **`let`/`const` declarations** are hoisted too, but are *not* initialized — they sit in the **Temporal Dead Zone** (see next section) until their declaration line executes; touching them earlier throws.
- **Function expressions / arrow functions** assigned to a variable are hoisted only as far as their *variable* is (so `var funcExpr` is `undefined` until assigned; `const funcExpr` is in the TDZ).

### Worked Example: function declaration hoisting

```js
console.log(greet("Ajay"));   // "hi Ajay" — works, called before its definition

function greet(name) {
  return "hi " + name;
}
```

The entire `greet` function — name and body — is registered in the scope before the first line runs, so calling it above its written position works exactly as if it had been defined first.

### Worked Example: `var` hoisting (declaration only)

```js
console.log(hoistedVar);      // undefined — NOT a ReferenceError
var hoistedVar = "I'm hoisted";
console.log(hoistedVar);      // "I'm hoisted"
```

JavaScript hoists the `var hoistedVar;` declaration to the top of the scope automatically, but leaves the assignment (`= "I'm hoisted"`) exactly where it was written. So reading it before that line gives `undefined` — a silent, easy-to-miss bug — rather than an error.

### Worked Example: `let`/`const` and the TDZ

```js
console.log(hoistedLet);      // ReferenceError: Cannot access 'hoistedLet' before initialization
let hoistedLet = "not accessible before this point";
```

`hoistedLet` is hoisted (the engine knows the name exists in this scope), but it is never initialized to `undefined` the way `var` is. It stays in the Temporal Dead Zone until its declaration line runs, so accessing it early throws loudly instead of silently returning `undefined`.

### Worked Example: function expressions are not fully hoisted

```js
console.log(notHoisted);      // undefined — the var binding IS hoisted
notHoisted();                 // TypeError: notHoisted is not a function
var notHoisted = function () {
  console.log("function expression is not hoisted!");
};
```

Here `notHoisted` is a `var`, so its *declaration* hoists and reads as `undefined` early — but the function itself is only assigned when execution reaches that line. Calling `undefined()` throws a `TypeError`, which is a different failure mode than calling an unhoisted function declaration (which just wouldn't exist yet).

### Comparison Table: hoisting behavior by declaration type

| Declaration | Hoisted? | Initial value before declaration line | Early access |
|---|---|---|---|
| `function f() {}` | Yes, fully | Complete function | Works normally |
| `var x` | Yes, declaration only | `undefined` | Reads `undefined`, no error |
| `let x` / `const x` | Yes, but uninitialized | (in TDZ) | `ReferenceError` |
| `const f = function(){}` / arrow | Only the binding | (in TDZ, for `let`/`const`) | `ReferenceError` |
| `var f = function(){}` | Only the binding | `undefined` | `TypeError` if called |

### Why It's Useful

Understanding hoisting explains a huge share of "why did this work/not work" JS bugs: why you can call a helper function defined further down the file, why a `var` sometimes silently reads `undefined` instead of throwing, and why `let`/`const` fail loudly (which is *better* — it surfaces bugs immediately instead of letting `undefined` propagate silently through your program).

### Summary

- Function **declarations** hoist completely — callable before their line.
- **`var`** hoists the declaration only, initialized to `undefined` — silent bugs.
- **`let`/`const`** hoist into the TDZ — loud `ReferenceError` on early access, which is safer.
- **Function expressions/arrows** are only as hoisted as the variable holding them.

---

## 4. Temporal Dead Zone (TDZ)

The Temporal Dead Zone is the span of code between the start of a block and the line where a `let`/`const` variable is actually declared. During that span the variable technically exists (it was hoisted) but cannot be touched — any read or write throws a `ReferenceError`. It only applies to block-scoped declarations; `var` has no TDZ.

### Key Concepts

- **Applies to `let` and `const` only** — never to `var`, which is initialized to `undefined` immediately instead.
- **Scoped per block**: the TDZ starts at the top of the nearest `{ }` and ends exactly at the declaration line.
- **Fails loudly**: accessing a TDZ variable throws `ReferenceError`, not a silent `undefined`.
- **A safety feature, not a bug**: it converts "used a variable before declaring it" from a silent footgun into an immediate, obvious error.

### Worked Example

```js
{
  console.log(a);   // ReferenceError: Cannot access 'a' before initialization
  let a = 3;
}
```

`a` is hoisted to the top of the block (so the engine already knows the name exists), but it isn't initialized until `let a = 3;` runs. Reading it any time before that line — even one statement earlier — throws.

### Worked Example: `var` has no TDZ

```js
function example() {
  console.log(c);   // undefined — no TDZ for var
  var c = 10;
}
```

Because `c` is a `var`, it's hoisted **and** initialized to `undefined` up front, so there's no dead zone to fall into — just the usual "reads as `undefined` until assigned" behavior.

### Worked Example: writing TDZ-safe code

```js
{
  let x = 10;      // declared AND initialized together
  console.log(x);  // 10 — no TDZ issue, because we never read before declaring
}
```

As long as you always declare a `let`/`const` before you reference it (the natural way to write code), the TDZ is invisible — you only ever meet it as an error when something (often a hoisted function or a mis-ordered `console.log`) reaches back for a variable too early.

### Why It's Useful

The TDZ is what makes `let`/`const` strictly safer than `var` for catching real bugs: instead of a typo or ordering mistake quietly producing `undefined` somewhere downstream (which might not surface until production), it throws immediately at the exact line where the mistake happened. It's a common "explain this behavior" interview question, usually paired with a hoisting question.

### Summary

- TDZ = the gap between a block starting and a `let`/`const` declaration actually running.
- Only `let`/`const` have a TDZ; `var` doesn't (it's `undefined` immediately).
- Touching a TDZ variable throws `ReferenceError` — a feature, not a flaw.
- Always declare before use and the TDZ never surfaces in your own code.

---

## 5. Operators & Coercion

**Coercion** is JavaScript automatically converting a value from one type to another so an operation can proceed, instead of refusing outright the way Java's compiler would (`int + boolean` is a compile error in Java; JS just finds *some* answer). The rules are learnable, and they are some of the most heavily tested interview material in the language.

### Key Concepts

- **`===` (strict equality)**: Compares type and value with no coercion. This is the one to use, always.
- **`==` (loose equality)**: Coerces operands to a common type first, then compares — a frequent source of surprising results.
- **Truthiness**: every value can act as a boolean; there are exactly **8 falsy values**, everything else is truthy.
- **`&&`/`||` return operands, not booleans**: they short-circuit but hand back one of the actual values involved.
- **`??` (nullish coalescing)**: like `||` but only falls back on `null`/`undefined`, not on other falsy values like `0` or `""`.
- **`+` is overloaded**: it means numeric addition *or* string concatenation depending on the operand types, with string concatenation taking priority whenever either side is a string.
- **`NaN`**: the one value that is never equal to itself, per the IEEE 754 spec.

### Worked Example: `==` vs `===`

```js
console.log(1 === 1);              // true
console.log(1 === "1");            // false — different types, no coercion
console.log(1 == "1");             // true  — '1' coerced to 1
console.log(0 == false);           // true  — false coerced to 0
console.log("" == false);          // true  — both coerced to 0
console.log(null == undefined);    // true  — special-cased by the spec
console.log(null === undefined);   // false — different types
```

`===` never converts anything, so it's predictable: `1 === "1"` is `false` because a number and a string are never the same type. `==` tries to make both sides comparable first, which is why `0 == false` is `true` (`false` becomes `0`) and why `null == undefined` is specifically carved out as `true` in the spec (even though nothing else coerces to/from `null`). The one defensible use of `==` in modern code is `x == null`, a deliberate idiom meaning "x is null or undefined."

### Worked Example: the 8 falsy values

```js
for (const v of [false, 0, -0, 0n, "", null, undefined, NaN]) {
  console.log(Boolean(v));   // false, every time
}
// Everything else is truthy — including these commonly-mistaken values:
console.log(Boolean("0"), Boolean("false"), Boolean([]), Boolean({}), Boolean(" "));
// true true true true true
```

Memorize the falsy list — it's short and closed: `false, 0, -0, 0n, "", null, undefined, NaN`. Every other value, including the string `"0"`, an empty array `[]`, and an empty object `{}`, is truthy. This trips people up because `[] == false` is actually `true` (arrays coerce to `""` then to `0` under `==`), even though `Boolean([])` is `true` — truthiness and `==` walk different coercion paths, which is one more reason to avoid `==` entirely.

### Worked Example: `&&`, `||`, and `??`

```js
console.log("a" && "b");    // 'b' — both truthy, returns the LAST one
console.log("" && "b");     // ''  — first falsy short-circuits, returns it
console.log("a" || "b");    // 'a' — first truthy short-circuits, returns it
console.log("" || "x");     // 'x' — first was falsy, falls through to next

const config = {};
console.log(config.port || 3000);      // 3000 — classic (imperfect) default idiom
console.log(config.timeout ?? 5000);   // 5000 — safer default idiom
console.log(0 ?? 99);                  // 0    — 0 is NOT null/undefined, so kept
console.log(0 || 99);                  // 99   — BUG: 0 is falsy, wrongly overridden
```

`&&` and `||` don't produce `true`/`false` — they hand back whichever actual operand decided the outcome. That makes `a || defaultValue` a compact way to supply a fallback, but it has a bug: any falsy value (`0`, `""`, `false`) triggers the fallback even when it was a legitimate value. `??` fixes exactly that by checking only for `null`/`undefined`.

### Worked Example: `+` vs the other math operators

```js
console.log(1 + 2);          // 3      — both numbers, normal addition
console.log("1" + 2);        // '12'   — string wins, 2 becomes '2'
console.log(1 + "2");        // '12'   — same, order doesn't matter
console.log(1 + 2 + "3");    // '33'   — left to right: 1+2=3, then 3+'3'='33'
console.log("1" + 2 + 3);    // '123'  — string from the very first operand

console.log("5" - 2);        // 3      — '-' has no string meaning, coerces to number
console.log("5" * "2");      // 10
console.log("abc" - 1);      // NaN    — "abc" can't become a number
```

`+` checks: is either operand a string? If yes, it concatenates (coercing the other side to a string too). Every other arithmetic operator (`-`, `*`, `/`, `%`) has no string meaning at all, so it always coerces *toward* number instead. This asymmetry — `+` prefers strings, everything else prefers numbers — is the root of most "wat" coercion surprises.

### Worked Example: `NaN`

```js
console.log(NaN === NaN);          // false — IEEE 754: NaN is never equal to itself
console.log(Number.isNaN(NaN));    // true  — the correct way to test
console.log(typeof NaN);           // 'number' — ironically
```

`NaN` ("Not a Number") represents the result of an invalid numeric operation (`0/0`, `Number("abc")`, `undefined + 1`). Per IEEE 754, two "invalid" results are never considered equal to each other, so `NaN === NaN` is famously `false`. Testing `x === NaN` therefore never works — always use `Number.isNaN(x)`.

### Comparison Table: coercion-related operators

| Operator | Behavior | Use it? |
|---|---|---|
| `===` / `!==` | No coercion, strict | Always |
| `==` / `!=` | Coerces first | Only for `x == null` |
| `\|\|` | Fallback on any falsy value | When `0`/`""`/`false` are never valid |
| `??` | Fallback only on `null`/`undefined` | Preferred default-value idiom |
| `+` | Number add OR string concat | Be explicit about types |
| `-`, `*`, `/` | Always coerces to number | Predictable but silent on bad input |

### Why It's Useful

Coercion rules explain nearly every "JavaScript is weird" meme, and interviewers use them to test whether you actually understand the language or just pattern-match code. In production code, the practical takeaway is simple: use `===`, use `??` over `||` for defaults, use `Number.isNaN` for NaN checks, and convert types explicitly (`Number(x)`, `String(x)`, `Boolean(x)`) rather than relying on implicit coercion to do the right thing.

### Summary

- Always use `===`/`!==`; the only sanctioned `==` is `x == null`.
- Eight falsy values, memorized: `false, 0, -0, 0n, "", null, undefined, NaN`.
- `&&`/`||` return operands, not booleans; prefer `??` over `||` for defaults involving `0`/`""`.
- `+` prefers string concatenation if either side is a string; every other math operator coerces to number.
- Test `NaN` with `Number.isNaN()`, never `=== NaN`.

---

## 6. Control Flow: `for`, `for...in`, `for...of`, `forEach`

JavaScript's control flow reads like Java's for the basics (`if`/`else`, `while`, ternary), so the interesting material is in the four different ways to iterate, each suited to a different situation.

### Key Concepts

- **`for` (classic)**: an initializer, a condition, and an update expression — full manual control, including `break`/`continue`, and the only loop form here that supports `await` cleanly inside async code.
- **`for...in`**: iterates **enumerable keys** (property names) of an object. On arrays it yields string indices and can pick up inherited properties — it's the wrong tool for arrays.
- **`for...of`**: iterates the **values** of any iterable (arrays, strings, `Map`, `Set`). This is the array-iteration tool of choice.
- **`forEach`**: an array *method* that calls a callback once per element — concise, but it cannot be stopped early and cannot be `await`ed meaningfully.

### Worked Example: `for` with `break`/`continue`

```js
for (let i = 0; i < 5; i++) {
  if (i === 3) break;      // exits the loop entirely
  console.log(i);
}
// 0 1 2

for (let i = 0; i < 5; i++) {
  if (i === 3) continue;   // skips just this iteration
  console.log(i);
}
// 0 1 2 4
```

The classic `for` loop is the only one of the four that gives you `break` and `continue` — full manual control over which iterations run and when the loop ends early.

### Worked Example: `for...in` vs `for...of`

```js
const scores = { math: 90, cs: 95 };
for (const key in scores) {
  console.log(key, "=", scores[key]);   // "math = 90", "cs = 95"
}

const fruits = ["apple", "banana", "orange"];
for (const fruit of fruits) {
  console.log(fruit);                   // "apple", "banana", "orange"
}

for (const index in fruits) {
  console.log(typeof index, index);     // "string 0", "string 1", "string 2" — a trap!
}
```

`for...in` walks an object's enumerable property *names* — perfect for plain objects like `scores`. Used on an array, it still technically works, but it yields the indices as **strings** (`"0"`, not `0`) and would also pick up any inherited enumerable properties, which is rarely what you want. `for...of` is the correct tool for arrays (and any iterable): it hands you the actual **values**, no index bookkeeping needed.

### Worked Example: `forEach`

```js
const users = [
  { name: "John", age: 25 },
  { name: "Jane", age: 30 },
];

users.forEach(function (user, index, array) {
  console.log(`${index}: ${user.name} is ${user.age}`);
});
// 0: John is 25
// 1: Jane is 30

let sum = 0;
[10, 20, 30].forEach(n => { sum += n; });
console.log(sum);   // 60 — forEach is used for its SIDE EFFECT here, not a return value
```

`forEach`'s callback receives three arguments — the current value, its index, and the whole array — mirroring `for...of` plus an index for free. Note that `forEach` itself returns `undefined`; it's meant for side effects (logging, mutating an outer variable, pushing into another array), not for building a new value. Use `.map()` (Phase 4) when you need a transformed array back.

### Comparison Table: `for` vs `forEach`

| | `for` | `forEach` |
|---|---|---|
| Origin | Original iteration construct | Array method (ES5) |
| Can `break`/`continue` | Yes | No — the callback can't stop the loop |
| Works with `await` | Yes, cleanly | No — `await` inside the callback doesn't pause the loop |
| Return value | None (it's a statement) | `undefined` always |
| Arguments given | You control the counter yourself | `(currentValue, index, array)` |
| Readability | More boilerplate | Terser for simple "do this per element" |
| Performance | Marginally faster | Marginally slower (function-call overhead per element) |

### Why It's Useful

Picking the right loop construct communicates intent: `for...of` says "I care about each value," `for...in` says "I care about an object's keys," `forEach` says "I'm doing a side effect per element and don't need to stop early," and a classic `for` says "I need full manual control, including early exit or `await`ing sequentially." Using `for...in` on an array, or expecting `break` to work inside `forEach`, are both real bugs seen in production code and are common interview "spot the mistake" questions.

### Summary

- `for...of` → values of an iterable (arrays, strings, Map/Set) — the default choice for arrays.
- `for...in` → enumerable keys of an object — not arrays.
- `forEach` → concise per-element side effects, but no `break`, no `continue`, and `await` inside it doesn't do what you'd expect.
- Classic `for` → the only option when you need `break`, `continue`, or sequential `await`.

---

## 7. Functions: the Four Definition Styles & First-Class Values

JavaScript treats functions as ordinary values — they can be stored, passed around, and returned, exactly like a number or a string (Phase 2 builds heavily on this). Before that, it's worth being precise about the different *syntaxes* for creating a function, because each has different hoisting and `this` behavior.

### Key Concepts

- **Function declaration**: `function f() {}` — hoisted, named, callable before its line.
- **Function expression**: `const f = function () {}` — not hoisted, can be anonymous or named.
- **Arrow function**: `const f = () => x` — concise, implicit return for a single expression, and (critically, Phase 2.3) no own `this`.
- **Method shorthand**: `{ m() {} }` inside an object literal — sugar for `m: function () {}`.
- **Default, rest, and spread parameters**: JS's replacement for Java's method overloading and varargs.

### Worked Example: the four function forms

```js
function add(a, b) {              // 1. declaration — hoisted
  return a + b;
}

const subtract = function (a, b) {  // 2. expression — not hoisted
  return a - b;
};

const multiply = (a, b) => a * b;   // 3. arrow — implicit return
const square = x => x * x;          // single param, parens optional
const makeTag = () => ({ tag: "div" }); // returning an object literal needs ( )

const calc = {                       // 4. method shorthand
  value: 10,
  double() { return this.value * 2; },
};

console.log(add(2, 3), subtract(9, 4), multiply(3, 4), square(5), calc.double());
// 5 5 12 25 20
```

All four forms produce a callable function, but they differ in hoisting (only #1 is fully hoisted) and in `this` binding (arrow functions inherit `this` from their enclosing scope instead of getting their own — covered fully in Phase 2 and Phase 3). Note the parens around `({ tag: "div" })` in `makeTag` — without them, `() => { tag: "div" }` parses `{ }` as a function *body*, not an object literal, which is a common arrow-function gotcha.

### Worked Example: functions as first-class values

```js
const ops = [add, subtract, multiply];
console.log(ops.map(f => f(6, 2)));   // [8, 4, 12] — an array OF functions

const times = n => x => x * n;        // a function that returns a function
const triple = times(3);
console.log(triple(10));              // 30
```

Storing functions in an array and calling them dynamically, or having a function return another function, only works because functions are ordinary values in JS. This is the on-ramp to closures and higher-order functions, which Phase 2 covers in depth.

### Worked Example: default, rest, and spread parameters

```js
function greet(name, greeting = "Hello") {   // default parameter
  return `${greeting}, ${name}!`;
}
console.log(greet("Ajay"), greet("Ravi", "Hi"));
// Hello, Ajay! | Hi, Ravi!

function sum(...nums) {                       // rest parameter — gathers into a real array
  return nums.reduce((total, n) => total + n, 0);
}
console.log(sum(), sum(1, 2, 3, 4));           // 0 10

const numbers = [5, 10, 15];
console.log(sum(...numbers));                  // 15 — spread EXPANDS an array into args
console.log(Math.max(...numbers));             // 15
console.log([...numbers, 20]);                 // [5, 10, 15, 20] — spread also clones/extends
```

Default parameters replace the common Java pattern of overloaded methods for "optional" arguments. Rest parameters (`...nums`) gather any number of trailing arguments into a genuine `Array` — unlike Java varargs, you get every array method for free. Spread (`...numbers`) is the mirror operation: it explodes an existing array into individual arguments (or individual elements, inside another array/object literal).

### Worked Example: JS does not enforce argument count

```js
function pair(a, b) {
  return [a, b];
}
console.log(pair(1));        // [1, undefined] — missing arg, no error
console.log(pair(1, 2, 3));  // [1, 2] — extra arg silently dropped
```

Unlike Java, JavaScript never checks how many arguments you pass. Missing parameters simply become `undefined`; extra ones are ignored (though still reachable via `arguments` or captured with a rest parameter). Combined with the fact that JS has **no method overloading** — one function name always means one function, and defining it twice just makes the second definition win — default and rest parameters are the idiomatic way to build flexible APIs.

### Comparison Table: function declaration vs expression vs arrow

| Feature | Declaration | Expression | Arrow |
|---|---|---|---|
| Syntax | `function f() {}` | `const f = function () {}` | `const f = () => {}` |
| Hoisted | Fully | No (only the variable) | No (only the variable) |
| Naming | Required | Optional (can be anonymous) | Always anonymous (assign to name a variable) |
| Own `this` | Yes | Yes | No — inherits from enclosing scope |
| Typical use | Top-level reusable functions | Callbacks, conditional definitions | Callbacks, especially where `this` should stay lexical |

### Why It's Useful

Every array method you'll use daily (`map`, `filter`, `reduce`, `forEach`, `sort`) exists because functions are first-class — you're literally passing a function as data into another function. Default/rest/spread parameters are the idiomatic way modern JS APIs (and libraries like Express, React) accept flexible input without needing Java-style overloads. Getting the four function forms straight — and specifically knowing which ones hoist — prevents a whole category of "ReferenceError: Cannot access before initialization" bugs.

### Summary

- Four ways to write a function: declaration (hoisted), expression (not hoisted), arrow (concise, no own `this`), method shorthand.
- Functions are ordinary values — store them, pass them, return them.
- Default params replace overloading for optional args; rest gathers args into a real array; spread explodes an array into args/elements.
- JS never checks argument count and has no method overloading — one name, one function, always.
