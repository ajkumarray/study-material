<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 1 · fundamentals](../phase-1-fundamentals/NOTES.md) | [Phase 3 · objects ➡](../phase-3-objects/NOTES.md)
<!-- /nav -->

# Phase 2 — Functions, Scope & Closures: Notes

## 1. First-Class Functions

A language has **first-class functions** when functions are treated like any other value — they can be assigned to variables, passed as arguments, returned from other functions, and stored in data structures. JavaScript functions are first-class citizens, and this single property is the foundation for almost everything that makes JS feel "functional": callbacks, `map`/`filter`/`reduce`, higher-order functions, currying, and closures all exist because a function is just a value.

### Key Concepts

- **Assignable**: a function can be stored in a variable exactly like a number or string.
- **Passable**: a function can be handed to another function as an argument (a callback).
- **Returnable**: a function can be the return value of another function (a factory).
- **Storable**: functions can live inside arrays or objects, just like any other data.

### Worked Example: functions behaving like values

```js
const sayHello = function () {
  console.log("Hello!");
};
sayHello();                          // "Hello!" — assigned to a variable, then called

function greet(callback) {
  callback();                        // a function is just an argument here
}
function sayHi() {
  console.log("Hi!");
}
greet(sayHi);                        // "Hi!"

function multiplyBy(x) {
  return function (y) {              // returning a function
    return x * y;
  };
}
const double = multiplyBy(2);
console.log(double(5));              // 10

const functionsArray = [
  a => a * 2,
  a => a * 3,
];
console.log(functionsArray[0](10));  // 20
console.log(functionsArray[1](10));  // 30
```

In this example: `sayHello` shows a function stored in a variable; `greet(sayHi)` shows one passed as an argument; `multiplyBy` shows one returned from another function (and `double` remembering `x = 2` is a preview of closures, next section); and `functionsArray` shows functions living inside a normal array, callable by index.

### Why It's Useful

First-class functions are what let `setTimeout(fn, 2000)` schedule arbitrary work, what let `array.map(fn)` transform data with a caller-supplied rule, and what let a library like Express accept a request handler as a plain function argument. Without this property, JavaScript would need special syntax for every one of those patterns — instead, they're all just "pass a function as data."

### Summary

- Functions in JS are ordinary values: assignable, passable, returnable, storable.
- This is the prerequisite for callbacks, higher-order functions, and closures.
- `array.map(fn)`, `setTimeout(fn, ms)`, and Express route handlers are all this concept in production use.

---

## 2. Higher-Order Functions

A **higher-order function** is a function that either accepts one or more functions as arguments, or returns a function as its result (or both). They're a direct consequence of functions being first-class — they exist to let you parameterize *behavior*, not just data.

### Key Concepts

- **Taking a function as an argument**: lets the caller inject custom behavior into shared logic (a "strategy").
- **Returning a function**: lets you build specialized functions from a general template (a "factory").
- **Enables reusability**: the same higher-order function can drive wildly different behavior depending on what's passed in.
- **Enables abstraction**: common iteration/control patterns (loop-and-transform, loop-and-filter, loop-and-accumulate) get pulled out once instead of hand-rolled everywhere.

### Worked Example: function as an argument

```js
function operateOnNumbers(a, b, operation) {
  return operation(a, b);
}
function add(x, y) { return x + y; }
function multiply(x, y) { return x * y; }

console.log(operateOnNumbers(5, 3, add));       // 8
console.log(operateOnNumbers(5, 3, multiply));  // 15
```

`operateOnNumbers` doesn't know or care what math it's doing — it just calls whatever function it was handed. `add` and `multiply` are interchangeable "strategies" plugged into the same shared logic.

### Worked Example: function returning a function

```js
function createMultiplier(multiplier) {
  return function (value) {
    return value * multiplier;
  };
}
const double = createMultiplier(2);
const triple = createMultiplier(3);
console.log(double(5));  // 10
console.log(triple(5));  // 15
```

`createMultiplier` is a factory: each call produces a new, independent function pre-configured with its own `multiplier`. This pattern (a function returning a function that "remembers" a value) only works because of closures, covered next.

### Worked Example: the built-in higher-order trio

```js
const numbers = [1, 2, 3, 4];

const doubled = numbers.map(num => num * 2);
console.log(doubled);                                // [2, 4, 6, 8]

const evenNumbers = numbers.filter(num => num % 2 === 0);
console.log(evenNumbers);                             // [2, 4]

const sum = numbers.reduce((acc, num) => acc + num, 0);
console.log(sum);                                      // 10
```

`map`, `filter`, and `reduce` (full depth in Phase 4) are the most common higher-order functions you'll write in real JS — each one takes a function describing *what to do per element* and handles the looping mechanics itself.

### Why It's Useful

Higher-order functions are the backbone of reusable, composable code: `Array.prototype` methods, Express/Koa middleware chains, React hooks, and Redux reducers are all higher-order functions at their core. They let you write the looping/control-flow logic exactly once and vary only the behavior plugged into it.

### Summary

- A higher-order function takes and/or returns functions.
- It enables "behavior parameterization" — the same generic logic, different plugged-in strategies.
- `map`/`filter`/`reduce`, factories like `createMultiplier`, and virtually every middleware/hook system in the JS ecosystem are higher-order functions.

---

## 3. Callbacks

A **callback** is a function passed as an argument to another function, with the expectation that the receiving function will invoke ("call back") that function at the appropriate time — often after finishing some work, or in response to an event. Callbacks are the original mechanism JavaScript used for asynchronous programming, before Promises and `async`/`await` existed.

### Key Concepts

- **Passed, not called, by the caller**: the function that receives the callback decides *when* (and *whether*, and with *what arguments*) to invoke it.
- **Synchronous or asynchronous**: `array.forEach(cb)` calls its callback synchronously; `setTimeout(cb, 2000)` calls it asynchronously, later.
- **Error-first convention**: classic Node.js callbacks conventionally take `(err, result)` as their first two parameters, so the caller always checks `err` first.

### Worked Example: a synchronous callback

```js
const firstFun = (name, callback) => {
  console.log(`My name is ${name}`);
  callback();
};
const secondFun = () => {
  console.log("This is a callback");
};
firstFun("Azhar", secondFun);
// My name is Azhar
// This is a callback
```

`firstFun` runs its own logic first, then calls whatever function it was handed as `callback` — in this case, `secondFun`. The caller of `firstFun` controls *which* function runs at that point, without `firstFun` needing to know anything about `secondFun`'s implementation.

### Worked Example: an asynchronous callback

```js
console.log("1: scheduling...");
setTimeout(function () {
  console.log("3: executed after 2 seconds");
}, 2000);
console.log("2: this runs immediately, before the timeout fires");
```

`setTimeout` doesn't call its callback right away — it registers the function to run later (once at least 2000ms have passed and the call stack is clear), then returns immediately, letting the rest of the script continue. This is the seed of everything in Phase 5 (async/await, promises, the event loop in Phase 7).

### Why It's Useful

Callbacks are how JS handles "do this, then when it's done, do that" without blocking the rest of the program — event handlers (`button.addEventListener('click', cb)`), timers, and legacy Node.js APIs (`fs.readFile(path, cb)`) all work this way. Understanding callbacks is the prerequisite for understanding *why* promises and `async`/`await` were introduced — they solve a real problem callbacks create at scale (next section).

### Summary

- A callback is a function passed to another function to be invoked later, by the receiver.
- Callbacks can be synchronous (`array.forEach`) or asynchronous (`setTimeout`, event listeners).
- They're the historical foundation of async JS, and the reason promises exist (see Callback Hell, next).

---

## 4. Callback Hell (Pyramid of Doom)

**Callback hell** is what happens when several asynchronous operations each depend on the previous one's result, and each is expressed as a nested callback — the code drifts rightward with every step, becoming hard to read, hard to modify, and hard to handle errors in consistently.

### Key Concepts

- **Deep nesting**: each async step wraps the next one, so the indentation grows with every dependency.
- **Scattered error handling**: without a unifying pattern, every nested callback needs its own error check.
- **Hard to reason about control flow**: "what runs after what" is buried in nested closures rather than read top-to-bottom.

### Worked Example: the pyramid

```js
asyncOperation1(function (result1) {
  asyncOperation2(result1, function (result2) {
    asyncOperation3(result2, function (result3) {
      asyncOperation4(result3, function (result4) {
        console.log("Final result:", result4);
      });
    });
  });
});
```

Each operation can only start once the previous one calls back with its result, so the code has no choice but to nest one level deeper per step. Adding a fifth step means another level of nesting; adding error handling to each step (a `try`/`catch` doesn't even work across callback boundaries) makes it worse still.

### Worked Example: fixing it with promises

```js
asyncOperation1()
  .then(result1 => asyncOperation2(result1))
  .then(result2 => asyncOperation3(result2))
  .then(result3 => asyncOperation4(result3))
  .then(result4 => console.log("Final result:", result4))
  .catch(error => console.error("Error:", error));
```

Rewriting each step to return a **promise** (Phase 5) flattens the nesting into a linear chain, and a single `.catch()` at the end handles an error from *any* step in the chain — no more per-callback error checks.

### Worked Example: fixing it with async/await

```js
async function performOperations() {
  try {
    const result1 = await asyncOperation1();
    const result2 = await asyncOperation2(result1);
    const result3 = await asyncOperation3(result2);
    const result4 = await asyncOperation4(result3);
    console.log("Final result:", result4);
  } catch (error) {
    console.error("Error:", error);
  }
}
performOperations();
```

`async`/`await` (Phase 5) goes further — the code now reads exactly like synchronous, top-to-bottom logic, and a single `try`/`catch` wraps the whole sequence.

### Worked Example: fixing it by modularizing

```js
function processResults(result1) {
  return asyncOperation2(result1)
    .then(result2 => asyncOperation3(result2))
    .then(result3 => asyncOperation4(result3));
}

asyncOperation1()
  .then(result1 => processResults(result1))
  .then(result4 => console.log("Final result:", result4))
  .catch(error => console.error("Error:", error));
```

Even without changing the async primitives, breaking a long chain into named, smaller functions reduces nesting and gives each step a readable name — a general technique that also helps synchronous code.

### Why It's Useful

Recognizing callback hell — and knowing all three escape hatches (promises, async/await, modularization) — is a routine interview topic, because it's the direct motivation for why promises exist at all. In real code, seeing 3+ levels of nested callbacks is a strong signal to refactor toward promises or `async`/`await`.

### Summary

- Callback hell = deeply nested callbacks for a sequence of dependent async steps.
- Symptoms: rightward drift, scattered/duplicated error handling, hard-to-follow control flow.
- Fixes: promise chaining (`.then().then()...catch()`), `async`/`await` with `try`/`catch`, or modularizing nested callbacks into named functions.

---

## 5. Closures

A **closure** is the combination of a function bundled together with references to its surrounding (lexical) state — it gives an inner function access to its outer function's scope, and critically, that access **survives even after the outer function has finished running**. This is arguably the single most-asked concept in JavaScript interviews.

### Key Concepts

- **Formed automatically**: any time a function is defined inside another function and references a variable from that outer scope, a closure exists — no special syntax required.
- **Outlives the outer call**: the inner function keeps a live reference to the outer variables, not a snapshot, even after the outer function returns and would otherwise be garbage-collected.
- **One closure per invocation**: calling the outer function again creates a completely separate closure with its own independent copy of the captured variables.
- **The mechanism behind private state**: since the captured variables aren't accessible from outside, closures are JavaScript's original way to fake private fields.

### Worked Example: the basic shape

```js
function outerFunction() {
  let outerVariable = "I am outside!";
  function innerFunction() {
    console.log(outerVariable);   // reaches into the outer scope
  }
  return innerFunction;
}

const closureFunc = outerFunction();
closureFunc();   // "I am outside!"
```

`outerFunction` finishes executing (and would normally have its local variables cleaned up), but because `innerFunction` was returned and still references `outerVariable`, JavaScript keeps that variable alive as part of the closure. Calling `closureFunc()` later proves `outerVariable` is still reachable.

### Worked Example: private state via a counter

```js
function makeCounter() {
  let count = 0;               // private — nothing outside can touch it directly
  return function () {
    count++;
    return count;
  };
}
const counter = makeCounter();
console.log(counter());  // 1
console.log(counter());  // 2
console.log(counter());  // 3
```

`count` cannot be read or modified from outside `makeCounter` except through the returned function — there's no `counter.count` property exposing it. This is data encapsulation via closure, and it predates JavaScript's `#privateField` class syntax (Phase 3) by many years.

### Worked Example: independent closures per call

```js
const c1 = makeCounter();
const c2 = makeCounter();
c1(); c1();
c2();
console.log(c1(), c2());  // 3 2 — each call to makeCounter made its own private `count`
```

Every call to `makeCounter()` creates a fresh `count` variable and a fresh closure around it — `c1` and `c2` never share state, exactly like two separate objects would.

### Worked Example: function factories

```js
function multiplyBy(factor) {
  return function (num) {
    return num * factor;
  };
}
const double = multiplyBy(2);
console.log(double(5));   // 10
const triple = multiplyBy(3);
console.log(triple(5));   // 15
```

`double` and `triple` are both produced by the same factory, but each closes over its own `factor` — this is the same mechanism as the counter example, just used to specialize behavior instead of hide state.

### Worked Example: the loop-closure classic, revisited

```js
function makeHandlers() {
  const handlers = [];
  for (let i = 0; i < 3; i++) {
    handlers.push(() => i);   // let -> a fresh binding captured per iteration
  }
  return handlers;
}
console.log(makeHandlers().map(h => h()));   // [0, 1, 2]
```

This is exactly the Phase 1 `var`-vs-`let` loop bug, reframed as a closures question: with `let`, every iteration creates a distinct `i` binding, so each arrow function closes over its own copy. Interviewers frequently ask this as "what does this log, and why," expecting you to name both hoisting/scope *and* closures in the answer.

### Worked Example: `once` and memoization

```js
function once(fn) {
  let called = false, result;
  return (...args) => {
    if (!called) { called = true; result = fn(...args); }
    return result;
  };
}
const init = once(() => { console.log("  (running expensive init)"); return 42; });
console.log(init(), init(), init());
// (running expensive init) — logs only ONCE
// 42 42 42
```

```js
function memoize(fn) {
  const cache = new Map();     // private cache, captured by closure
  return (n) => {
    if (cache.has(n)) return cache.get(n);
    const result = fn(n);
    cache.set(n, result);
    return result;
  };
}
const fastSquare = memoize(n => n * n);
console.log(fastSquare(9), fastSquare(9));  // 81 81 — second call hits the cache
```

Both `once` and `memoize` are higher-order functions that use a closure over a private variable (`called`/`result`, or `cache`) to remember state across multiple calls to the returned function — the caller never sees or touches that internal state directly.

### Why It's Useful

Closures underpin data encapsulation (module pattern, private counters), function factories (pre-configured functions), memoization/caching, `once`-style guards, and — critically for modern JS — React's `useState`/`useEffect` hooks, which are implemented as closures over component-local state. Any time you see a function "remembering" something between calls without a class or global variable, a closure is doing the remembering.

### Summary

- A closure = a function + the lexical scope it was defined in, kept alive as long as the function exists.
- It gives functions private state that persists across calls but is invisible from outside.
- Each invocation of the outer function creates an independent closure.
- Powers: private counters/module pattern, factories, `once`, memoization, event handlers, and the `let`-in-a-loop fix.

---

## 6. Function Types: Declarations, Expressions, Arrows, IIFEs, Generators, Async

JavaScript offers several distinct syntaxes for creating a function, and each has different hoisting behavior, different `this` semantics, or a different execution model entirely. Knowing all of them — and when each is the right tool — is standard interview ground.

### Key Concepts

- **Function declaration**: `function f() {}` — hoisted fully, named, standard top-level function.
- **Function expression**: `const f = function () {}` — not hoisted, can be named or anonymous.
- **Arrow function expression**: `const f = (x) => x * 2` — concise, lexical `this`, implicit return for a single expression body.
- **IIFE (Immediately Invoked Function Expression)**: a function defined and called in the same statement — runs exactly once, creates a private scope.
- **Generator function**: `function* g() {}` — can pause (`yield`) and resume, returning an iterator instead of a single value.
- **Async function**: `async function f() {}` — always returns a promise, enables `await` inside its body (Phase 5).

### Worked Example: declaration vs expression vs arrow

```js
function greet(name) {                       // declaration — hoisted
  return `Hello, ${name}!`;
}
const greet2 = function (name) {              // expression — not hoisted
  return `Hello, ${name}!`;
};
const greet3 = (name) => `Hello, ${name}!`;   // arrow — implicit return

console.log(greet("Alice"), greet2("Bob"), greet3("Charlie"));
// Hello, Alice! Hello, Bob! Hello, Charlie!
```

All three produce equivalent output, but only `greet` can be called before its line in the file (function declarations hoist completely; the other two are variable bindings that hoist only as far as `var`/`let`/`const` normally do — see Phase 1).

### Worked Example: IIFE

```js
(function () {
  console.log("This function runs immediately.");
})();

(function (name) {
  console.log(`Hello, ${name}!`);
})("Eve");
// This function runs immediately.
// Hello, Eve!
```

Wrapping a function in parentheses and immediately calling it creates a private scope that runs once and disappears — no name leaks into the surrounding scope. Before ES modules existed, this was the standard way to avoid polluting the global namespace (the "module pattern" from the closures section is often built on top of an IIFE).

### Worked Example: generator function

```js
function* generatorFunction() {
  yield "First";
  yield "Second";
  yield "Third";
}
const generator = generatorFunction();
console.log(generator.next().value);   // First
console.log(generator.next().value);   // Second
console.log(generator.next().value);   // Third
```

Calling `generatorFunction()` doesn't run the body — it returns an **iterator**. Each call to `.next()` resumes execution until the next `yield`, pausing there and handing back that value. This is the mechanism behind custom iterables and (historically) some async-flow libraries.

### Worked Example: async function

```js
async function greetAsync(name) {
  return `Hello, ${name}!`;
}
greetAsync("Frank").then(console.log);   // Hello, Frank!
```

An `async` function always returns a promise — even though the body just says `return "Hello, Frank!"`, the caller has to `.then()` (or `await`) it to get the value out, because JavaScript automatically wraps the return value in a resolved promise. Full depth in Phase 5.

### Comparison Table: function declaration vs expression

| Feature | Function Declaration | Function Expression |
|---|---|---|
| Syntax | `function myFunc() {}` | `const myFunc = function () {}` |
| Hoisting | Fully hoisted | Not hoisted (only the variable binding) |
| Naming | Must be named | Can be named or anonymous |
| Availability in scope | Whole enclosing scope, from the top | Only after the assignment line runs |
| Typical use | Reusable, globally-relevant functions | Callbacks, conditionally-created functions, IIFEs |

### Worked Example: named function expressions can self-reference

```js
const factorial = function fact(n) {
  if (n <= 1) return 1;
  return n * fact(n - 1);   // `fact` is only visible inside its own body
};
console.log(factorial(5));   // 120
// factorial(5) works; fact is not accessible from outside the expression.
```

Giving a function expression an internal name (`fact`) lets it call itself recursively without depending on the outer variable name (`factorial`), which is useful if the function is ever reassigned or passed around under a different name.

### Why It's Useful

Recognizing which function form you're looking at tells you immediately whether it's callable before its definition (declarations), whether `this` behaves lexically or dynamically (arrow vs. regular), and whether it runs once as isolation (IIFE) or produces a stream of values over time (generator). Modern frameworks lean heavily on arrow functions for callbacks (to avoid `this` bugs) and `async` functions for anything I/O-related.

### Summary

- Declarations hoist fully; expressions (including arrows) hoist only their binding.
- Arrow functions have no own `this`/`arguments`/`super` — they inherit from the enclosing scope (next section digs into `this` specifically).
- IIFEs create a private, one-time scope — the pre-ES-modules way to avoid global pollution.
- Generators (`function*`/`yield`) pause and resume, returning values over time via an iterator.
- `async` functions always return a promise and unlock `await` inside their body.

---

## 7. The `this` Keyword

In Java, `this` always refers to the current object instance — fixed by the class. In JavaScript, `this` is decided **entirely by how a function is called** (the "call site"), not by where the function is defined. This is JavaScript's biggest behavioral divergence from Java and a near-guaranteed interview topic.

### Key Concepts

- **Method call** (`obj.fn()`): `this` is the object to the left of the dot.
- **Plain call** (`fn()`): `this` is `undefined` in strict mode/modules, or the global object in old-style sloppy mode.
- **Constructor call** (`new Fn()`): `this` is the brand-new instance being constructed (Phase 3).
- **Event handler**: `this` is the DOM element the handler was attached to.
- **Explicit binding** (`call`/`apply`/`bind`): `this` is whatever you pass in, overriding all the rules above.
- **Arrow functions have no own `this`**: they capture `this` lexically from the enclosing scope at the time they're defined, and that binding can never be changed — not even by `call`/`apply`/`bind`.

### Worked Example: the four (plus one) call-site rules

```js
// 1. Method call — `this` = the object before the dot
const person = {
  name: "John",
  greet() { console.log(this.name); },
};
person.greet();   // John

// 2. Plain call — `this` = undefined (strict) or global object (sloppy)
function show() { console.log(this); }
show();           // undefined in an ES module / strict mode

// 3. Constructor call — `this` = the new instance
function Person(name) { this.name = name; }
const john = new Person("John");
console.log(john.name);   // John

// 4. Arrow function — `this` is lexical, inherited from where it's defined
const team = {
  name: "backend",
  members: ["a", "b"],
  listAll() {
    return this.members.map((m) => `${m}@${this.name}`);  // arrow inherits `this` from listAll
  },
};
console.log(team.listAll());   // ['a@backend', 'b@backend']
```

The method-call rule (#1) is the most common in everyday object-oriented code. The plain-call rule (#2) is what makes "detached methods" dangerous (next example). The arrow-function rule (#4) is why arrows are the default choice for callbacks nested inside methods — a regular function there would lose `this` entirely.

### Worked Example: the "lost `this`" bug

```js
const person = {
  name: "John",
  greet() { console.log(this.name); },
};
const detached = person.greet;   // pull the method off its object
try {
  detached();                    // called with no receiving object!
} catch (e) {
  console.log(e.constructor.name);   // TypeError — this is undefined, .name fails
}
```

`greet` doesn't remember it "belongs to" `person` — `this` is only established at call time. Once `greet` is assigned to a plain variable and called without an object in front of it (`detached()`), the method-call rule no longer applies, so `this` falls back to `undefined` (strict mode), and accessing `this.name` throws. This exact bug is why React class components historically needed `this.method = this.method.bind(this)` in their constructors, and it's why arrow class fields (`method = () => {}`) became the popular fix.

### Worked Example: `call`, `apply`, and `bind` fix `this` explicitly

```js
function greet() {
  console.log(this.name);
}
const other = { name: "Ravi" };

greet.call(other);              // Ravi — invoke now, this = other
greet.apply(other);             // Ravi — same, but args (if any) as an array
const bound = greet.bind(other); // returns a NEW function, permanently bound
bound();                        // Ravi — invoked later, this is still other
```

`call` and `apply` both invoke the function immediately with a chosen `this`; `bind` instead returns a brand-new function with `this` locked in, to be called whenever you like (full depth in the next section).

### Comparison Table: how `this` resolves

| Call form | Example | `this` value |
|---|---|---|
| Method call | `obj.fn()` | `obj` |
| Plain call | `fn()` | `undefined` (strict/module) or global object (sloppy) |
| Constructor call | `new Fn()` | The newly created instance |
| Explicit binding | `fn.call(x)` / `fn.apply(x)` / `fn.bind(x)()` | `x` |
| Arrow function | `() => {}` | Inherited lexically from the enclosing scope, fixed forever |
| Event handler (regular function) | `el.addEventListener('click', function(){ this... })` | The DOM element that fired the event |

### Why It's Useful

Getting `this` right is essential for writing object methods that behave correctly when passed around as callbacks (e.g. handing `obj.method` to `setTimeout`), for understanding why class methods sometimes need binding, and for choosing correctly between an arrow function and a regular function when defining a callback inside a method. It's one of the most reliable "explain what this logs and why" interview questions.

### Summary

- `this` is resolved by *how* a function is called, not where it's defined — the opposite of Java.
- Method call → the object before the dot; plain call → `undefined`/global; `new` → the new instance.
- Arrow functions have no own `this` — they inherit it lexically and it can never be rebound.
- `call`/`apply`/`bind` let you set `this` explicitly, overriding the default rules.
- Rule of thumb: arrow functions for callbacks (to preserve outer `this`), regular functions for object methods and any place you want dynamic `this`.

---

## 8. `call()`, `apply()`, and `bind()`

All three are built-in methods on every function that let you control what `this` refers to when the function runs — the difference is *when* the function executes and *how* arguments are supplied.

### Key Concepts

- **`call(thisArg, arg1, arg2, ...)`**: invokes the function immediately, with `this` set to `thisArg` and arguments passed individually.
- **`apply(thisArg, [argsArray])`**: invokes the function immediately too, but arguments are passed as a single array.
- **`bind(thisArg, arg1, ...)`**: does **not** invoke the function — it returns a brand-new function with `this` (and any given leading arguments) permanently fixed, to be called later.
- **Method borrowing**: all three let you reuse a method defined on one object against a completely different object.

### Worked Example: `call()`

```js
const person1 = { firstName: "John", lastName: "Doe" };
const person2 = { firstName: "Jane", lastName: "Smith" };

function sayHello(greeting) {
  console.log(`${greeting}, I am ${this.firstName} ${this.lastName}`);
}
sayHello.call(person1, "Hello");  // Hello, I am John Doe
sayHello.call(person2, "Hi");     // Hi, I am Jane Smith
```

`call` invokes `sayHello` right away, with `this` explicitly set to whichever object is passed as the first argument, and any further arguments (`"Hello"`, `"Hi"`) supplied one at a time after it.

### Worked Example: `apply()`

```js
function introduce(greeting, age) {
  console.log(`${greeting}, my name is ${this.firstName} and I'm ${age} years old.`);
}
const alice = { firstName: "Alice" };
introduce.apply(alice, ["Hi", 25]);
// Hi, my name is Alice and I'm 25 years old.

const numbers = [5, 6, 2, 3, 7];
console.log(Math.max.apply(null, numbers));   // 7
console.log(Math.min.apply(null, numbers));   // 2
```

`apply` behaves exactly like `call`, except the arguments are bundled into a single array. This makes it the natural fit whenever you already have an array of arguments — like passing `numbers` straight into `Math.max`, which normally only accepts individual arguments, not an array.

### Worked Example: `bind()`

```js
const person = {
  name: "Alice",
  greet() { console.log("Hello, " + this.name); },
};
const greetAlice = person.greet.bind(person);
greetAlice();   // Hello, Alice — this is now permanently person, even called standalone

function multiply(a, b) { return a * b; }
const double = multiply.bind(null, 2);   // pre-set the first argument too
console.log(double(5));                  // 10 — effectively multiply(2, 5)
```

Unlike `call`/`apply`, `bind` doesn't run the function immediately — it returns a *new* function with `this` locked in (and here, `a` pre-set to `2` as well — "partial application"). Calling `greetAlice()` later works correctly even with no object in front of it, because `this` was already fixed at `bind`-time, not determined by the call site.

### Worked Example: all three side by side

```js
function introduce(greeting, punctuation) {
  console.log(greeting + ", " + this.name + punctuation);
}
const person = { name: "Alice" };

introduce.call(person, "Hello", "!");        // Hello, Alice!  — invoked now
introduce.apply(person, ["Hello", "!"]);     // Hello, Alice!  — invoked now, args as array
const boundIntroduce = introduce.bind(person, "Hello");
boundIntroduce("!");                         // Hello, Alice!  — invoked LATER
```

### Comparison Table: `call` vs `apply` vs `bind`

| Method | Invocation timing | Arguments format | Returns |
|---|---|---|---|
| `call(thisArg, a, b, ...)` | Immediately | Individually | The function's return value |
| `apply(thisArg, [a, b, ...])` | Immediately | As an array | The function's return value |
| `bind(thisArg, a, ...)` | Deferred | Individually (pre-set), more can follow at call time | A new bound function |

### Why It's Useful

`call`/`apply` are how you borrow a method from one object and run it against another without copy-pasting the method (`car.start.call(bike)`), and how you pass an array of args into a function that expects them individually (`Math.max.apply(null, arr)` — mostly superseded now by spread: `Math.max(...arr)`). `bind` is the classic fix for the "lost `this`" problem when handing an object method to something else as a callback (`button.addEventListener('click', obj.method.bind(obj))`), and it's also a simple way to implement partial application.

### Summary

- `call(thisArg, a, b)` and `apply(thisArg, [a, b])` invoke immediately; the only difference is how arguments are supplied.
- `bind(thisArg, ...)` returns a new function, invoked later, with `this` (and optionally some leading arguments) permanently fixed.
- All three exist to control `this` explicitly, overriding the normal call-site rules.
- Mnemonic: **A**pply takes an **A**rray.

---

## 9. Currying

**Currying** is the technique of transforming a function that takes multiple arguments into a sequence of functions that each take exactly one argument — instead of `f(a, b, c)`, you call `f(a)(b)(c)`, where each call returns a new function (via closure) waiting for the next argument.

### Key Concepts

- **One argument at a time**: a curried function never takes more than one argument per call in its "pure" form.
- **Built on closures**: each returned function closes over the arguments already supplied.
- **Enables partial application**: you can call a curried function with fewer arguments than it ultimately needs, getting back a specialized function for the rest.
- **Distinct from generic partial application**: currying specifically breaks a function into unary (one-argument) steps; partial application more generally means "fix *some* arguments now, supply the rest later," which can happen in bigger chunks.

### Worked Example: manual currying

```js
function add(x, y, z) {
  return x + y + z;
}
console.log(add(1, 2, 3));   // 6 — normal call

function addCurried(x) {
  return function (y) {
    return function (z) {
      return x + y + z;
    };
  };
}
console.log(addCurried(1)(2)(3));   // 6 — same result, one argument at a time
```

`addCurried(1)` returns a function waiting for `y`; that function, called with `2`, returns another function waiting for `z`; calling that with `3` finally computes `1 + 2 + 3`. Each intermediate function is a closure that remembers the arguments already given.

### Worked Example: partial application from a curried function

```js
const addOne = addCurried(1);       // fix x = 1
console.log(addOne(2)(3));          // 6

const multiply = (a) => (b) => a * b;
const double = multiply(2);
const triple = multiply(3);
console.log(double(5), triple(5));  // 10 15
```

Once `addCurried(1)` (or `multiply(2)`) is called, you get back a reusable, specialized function — `addOne` always adds 1 to whatever comes next; `double` always multiplies by 2. This is a direct, practical use of currying: building families of related functions from one general one.

### Worked Example: a generic curry utility

```js
function curry(fn) {
  return function curried(...args) {
    if (args.length >= fn.length) {
      return fn.apply(this, args);
    }
    return function (...newArgs) {
      return curried.apply(this, args.concat(newArgs));
    };
  };
}

function sum(a, b, c) { return a + b + c; }
const curriedSum = curry(sum);

console.log(curriedSum(1)(2)(3));   // 6
console.log(curriedSum(1, 2)(3));   // 6 — can also take args in groups
console.log(curriedSum(1)(2, 3));   // 6
```

This general-purpose `curry` helper uses `fn.length` (the number of parameters `fn` was declared with) to know how many arguments it's still waiting for; once enough have accumulated, it calls the original function. This is the same idea libraries like Lodash (`_.curry`) and Ramda provide out of the box.

### Comparison Table: currying vs partial application

| | Currying | Partial application |
|---|---|---|
| Shape | Always breaks down to one argument per call | Fixes any number of arguments at once |
| Result of a partial call | Always another single-argument function | A function expecting the remaining arguments (any count) |
| Typical use | Building a strict chain of specializations | Pre-configuring a function with some known arguments |

### Worked Example: currying for real-world configuration

```js
function log(date) {
  return function (level) {
    return function (message) {
      console.log(`[${date}] [${level}]: ${message}`);
    };
  };
}
const logToday = log(new Date().toLocaleDateString());
const logInfo = logToday("INFO");
logInfo("Application started");   // [MM/DD/YYYY] [INFO]: Application started
logInfo("User logged in");        // [MM/DD/YYYY] [INFO]: User logged in
```

`logToday` and `logInfo` are both intermediate, reusable, specialized loggers built by supplying one argument at a time — a realistic use of the pattern for building configured utilities (loggers, API clients pre-bound to a base URL, validators pre-bound to a schema).

### Why It's Useful

Currying underlies functional-programming libraries (Lodash, Ramda), makes function composition cleaner (each step takes one input and produces one output), and is a common way to build specialized, reusable functions from a general-purpose one (event handlers pre-bound to an id, validators pre-configured with rules). It's also a frequent live-coding interview exercise: "implement a `curry` function."

### Summary

- Currying transforms `f(a, b, c)` into `f(a)(b)(c)` — a chain of one-argument closures.
- It enables partial application, reusability, and cleaner function composition.
- A generic `curry(fn)` utility uses `fn.length` to know when enough arguments have been collected.
- Currying is a specific, stricter form of the more general idea of partial application.
