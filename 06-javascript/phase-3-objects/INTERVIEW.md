<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · arrays ➡](../phase-4-arrays/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects, Prototypes & OOP: Interview Q&A

⭐ = asked constantly.

## Objects & Prototypes

**Q: What is the prototype chain?** ⭐⭐

Every JavaScript object has an internal link — historically exposed as `__proto__`, formally the `[[Prototype]]` slot — pointing to another object. When you access a property or method that doesn't exist directly on an object, JavaScript automatically looks it up on that linked object, then on *its* prototype, and so on, until the property is found or the chain ends at `null` (the chain always terminates at `Object.prototype`, whose own prototype is `null`). This lookup mechanism is how JavaScript implements inheritance: it's live delegation between actual objects at runtime, not a compile-time class hierarchy.

**Q: How is JavaScript's inheritance model different from Java's?** ⭐

Java resolves inheritance through classes, largely at compile time — a class's method set is fixed once compiled. JavaScript objects delegate to other *objects* at runtime through the prototype chain; `class` syntax is sugar layered on top of this, not a separate mechanism. A practical consequence: you can even reassign an object's prototype at runtime with `Object.setPrototypeOf` (though doing so hurts engine optimization and is rarely a good idea), something that has no equivalent in Java's static class model.

**Q: Is `class` in JavaScript a "real" class the way Java has classes?**

No — it's syntactic sugar over the prototype system. When you write `class Person { greet() {...} }`, the `greet` method is placed on `Person.prototype`, exactly as if you'd written `Person.prototype.greet = function() {...}` by hand on a constructor function. Every instance created with `new Person()` gets `Person.prototype` as its `[[Prototype]]` and reaches `greet` via delegation, not by having its own copy. `class` does add real behavior differences over a bare constructor function though — calling it without `new` throws, and it supports `#private` fields and cleaner `extends`/`super` inheritance.

**Q: What's the difference between `Function.prototype` and an instance's `__proto__`?**

`Person.prototype` is a property that exists on the **function** `Person` — it's the object that will be assigned as the `[[Prototype]]` of every instance created via `new Person(...)`. `person1.__proto__` is the actual link a specific *instance* holds, pointing at that shared prototype object. `person1.__proto__ === Person.prototype` is `true` — they're describing the same relationship from two different sides (the function's "here's what I hand out" vs. the instance's "here's what I was given").

**Q: `Object.keys` vs `for...in` vs `Object.getOwnPropertyNames` — how do they differ?**

`Object.keys(obj)` returns only the object's **own, enumerable, string** keys — the most common and safest choice. `for...in` iterates enumerable keys **including any inherited via the prototype chain**, which is why it's typically guarded with `Object.hasOwn(obj, key)` inside the loop if you only want own properties. `Object.getOwnPropertyNames(obj)` returns all of an object's own keys, including non-enumerable ones, but still excludes inherited ones. For everyday object iteration, prefer `Object.keys`/`Object.values`/`Object.entries`.

**Q: `hasOwnProperty`/`Object.hasOwn` vs the `in` operator?**

`Object.hasOwn(obj, key)` (the modern, recommended form — safer than `obj.hasOwnProperty(key)`, which can be shadowed or fail on objects with `null` prototypes) checks whether `key` is an **own** property of `obj`, ignoring the prototype chain entirely. `key in obj` returns `true` if `key` exists **anywhere** on the chain, including inherited properties and methods. Use `Object.hasOwn` whenever you specifically want to exclude inherited properties — e.g. filtering `for...in` results, or checking "did the caller actually pass this option" versus "does this exist because `Object.prototype` provides it."

**Q: How do you copy an object? What's the difference between a shallow and a deep copy?** ⭐

Shallow copy: `{ ...obj }` or `Object.assign({}, obj)` — copies the object's own top-level properties, but any **nested** object or array inside is still the *same reference* as in the original, so mutating a nested value through the copy also mutates the original. Deep copy: `structuredClone(obj)` (the modern built-in, handles most types including dates and nested structures) or the older `JSON.parse(JSON.stringify(obj))` trick (works for plain data but silently drops functions, `undefined` values, and mishandles dates/`Map`/`Set`). Forgetting that spread is only shallow is a very common source of bugs in React state updates when state contains nested objects.

**Q: What does `Object.freeze` do — is it a deep freeze?**

It makes an object's own properties non-writable and non-configurable, but only at the **top level** — any object nested inside a frozen object remains fully mutable unless it's frozen separately too. In strict mode (which every ES module runs under), attempting to write to a frozen property throws a `TypeError`; in old sloppy-mode scripts, the write is silently ignored instead, which is one more reason to prefer ES modules.

---

## Classes, Inheritance & the OOP Pillars

**Q: Walk through what happens when you call `new Person("Alice")`.** ⭐

Four things happen, roughly: (1) a brand-new, empty object is created; (2) its `[[Prototype]]` is set to `Person.prototype`, wiring it into the prototype chain so it can reach any methods defined there; (3) the constructor function runs with `this` bound to that new object, executing `this.name = "Alice"` and any other setup code; (4) unless the constructor explicitly returns a different *object*, the newly created and initialized object is returned automatically — you never need an explicit `return this`.

**Q: How does inheritance work with `extends` and `super`?** ⭐⭐

`class Dog extends Animal` wires `Dog.prototype`'s own `[[Prototype]]` to `Animal.prototype`, so any method not found directly on `Dog.prototype` falls through to `Animal.prototype` via the normal chain lookup. Inside `Dog`'s constructor, `super(name)` calls `Animal`'s constructor against the new instance, running its setup logic (e.g. `this.name = name`) — and this **must** happen before `this` is touched anywhere in `Dog`'s own constructor, or JS throws a `ReferenceError`. `super.speak()` (outside the constructor, inside an overriding method) calls `Animal`'s version of `speak`, typically so the subclass can extend rather than fully replace the behavior.

**Q: What happens if a subclass doesn't define its own constructor?**

JavaScript implicitly supplies one that simply forwards all its arguments to `super(...)` — so `class Cat extends Animal {}` behaves exactly as if `Animal`'s constructor were called directly with the same arguments. You only need to write an explicit constructor when the subclass needs to add fields, validate input, or otherwise do something beyond what the parent constructor already does.

**Q: Explain method overriding, and how it enables polymorphism.** ⭐⭐

A subclass defines a method with the same name as one in its superclass; when that method is called on a subclass instance, JS finds the subclass's version first during the prototype-chain walk, so it "wins" over the parent's implementation. Calling `super.methodName()` from inside the override lets you still run the parent's logic as part of the new behavior, rather than fully replacing it. This is exactly how JS achieves polymorphism — the same method call (`animal.speak()`) produces different behavior depending on the actual runtime type of `animal`, without the calling code needing to know or check which subclass it has.

**Q: Does JavaScript support method overloading, the way Java does?**

No. A function/method name is bound to exactly one implementation — defining "the same" method twice just makes the later definition win, there's no dispatch based on argument types or count. This is a direct consequence of dynamic typing: there's no static type information available to choose between overloads at compile time. What looks like overloading in JS libraries is usually achieved with default parameters, rest parameters, or manual branching on `arguments.length`/`typeof` inside a single implementation.

**Q: What are the four pillars of OOP, and how does JavaScript implement each?** ⭐⭐

**Encapsulation** — bundling data and the methods that act on it, restricting direct access to internals — achieved with `#private` class fields/methods (ES2022, true enforced privacy) or the older closure-based module pattern. **Inheritance** — building a new class on an existing one — achieved with `extends`/`super()`, which is sugar over manually chaining prototypes with `Object.create`. **Polymorphism** — the same method call behaving differently depending on the object — achieved through method overriding (run-time polymorphism); JS has no method overloading (compile-time polymorphism). **Abstraction** — exposing a simple interface while hiding implementation complexity — achieved with public methods that internally call `#private` helper methods, simulated "abstract" base classes whose methods throw unless overridden, or modules that export only select functions.

**Q: How do you create truly private data in a JS class — and what are the older alternatives?** ⭐

The modern answer is `#fieldName` (ES2022): fields and methods prefixed with `#` are accessible only from inside the class body — accessing `instance.#field` from outside is a **syntax error**, not just a convention, and `#`-prefixed members are invisible to `Object.keys`, `JSON.stringify`, and `for...in`. Before that syntax existed, JS code used closures (the module/factory pattern: private variables live in an outer function's scope, reachable only via methods returned from it) or, weakest of all, a `_name` naming convention that signals "treat as private" but provides zero actual enforcement — any code can still read or write `obj._name` directly.

**Q: What's the difference between encapsulation and abstraction? They sound similar.**

Encapsulation is about **restricting access** — bundling data with the methods that operate on it and hiding the data itself (a `#balance` field nobody outside the class can touch directly). Abstraction is about **simplifying the interface** — exposing only the high-level operations a caller actually needs (`drive()`) while hiding the multi-step complexity behind them (`#checkFuel()`, `#startEngine()`). In practice they're implemented with the same tools (`#private` members, closures) and usually go together: encapsulating the details is what *makes* the simplified abstraction possible in the first place.

**Q: What's "duck typing," and how does it relate to polymorphism in JavaScript?**

Duck typing means an object is treated as fulfilling a role based purely on which methods/properties it has, regardless of its actual class or inheritance chain — "if it walks like a duck and quacks like a duck, treat it like a duck." In JS, this means `Bird`, `Airplane`, and `Kite` classes with no shared parent at all can all be used polymorphically by any code that calls `.fly()` on them, as long as each one implements that method. It's a looser, more flexible form of polymorphism than Java's interface-based approach, since JS doesn't require declaring the shared contract up front.

**Q: `instanceof` — what does it actually check, and what's a case where it can be misleading?** ⭐

`obj instanceof Constructor` checks whether `Constructor.prototype` appears anywhere on `obj`'s prototype chain — it returns `true` for the object's exact class and every ancestor class (`rex instanceof Dog`, `rex instanceof Animal`, and `rex instanceof Object` can all be `true` simultaneously for one `Dog` instance). A known gotcha: `instanceof` can behave unexpectedly across different execution "realms" (an array created in a different iframe/VM context has a different `Array` constructor, so `arr instanceof Array` can be `false` there even though it's genuinely an array) — `Array.isArray()` avoids that specific trap.

---

## Object Utilities & Destructuring

**Q: How would you transform every value in an object using `map`-like logic, given objects aren't directly iterable?**

Convert to an array of pairs with `Object.entries(obj)`, run `.map()` over that array, then convert back with `Object.fromEntries(...)`. For example, uppercasing every key: `Object.fromEntries(Object.entries(obj).map(([k, v]) => [k.toUpperCase(), v]))`. This "entries → transform → fromEntries" round trip is the standard idiom whenever you need array-style transformation over an object's contents.

**Q: What's the difference between `Object.assign({}, obj)` and `{ ...obj }`?**

Functionally equivalent for a single-source shallow copy — both copy `obj`'s own enumerable properties onto a new empty object. Spread is generally preferred for readability, and it also naturally handles merging multiple sources with later ones winning (`{ ...defaults, ...overrides }`), the same way `Object.assign({}, defaults, overrides)` does. Neither is a deep copy — nested objects remain shared references in both cases.

**Q: Explain destructuring with a default value, a renamed variable, and a nested path, in one example.** ⭐

```js
const response = { data: { user: { id: 1, name: "Ajay" } }, status: 200 };
const { data: { user: { name: userName } }, status, extra = "n/a" } = response;
// userName = "Ajay", status = 200, extra = "n/a" (no `extra` key exists, so the default applies)
```

The pattern on the left mirrors the shape of the object on the right: `data: { user: { name: userName } }` walks three levels deep and binds the innermost value to a *new* variable name, `userName`, rather than `name` (avoiding a collision if `name` is used elsewhere). `extra = "n/a"` only takes effect because `response.extra` doesn't exist — defaults apply specifically when the corresponding value is `undefined`, not for any other falsy value like `0` or `""`.
