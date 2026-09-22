<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · arrays ➡](../phase-4-arrays/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects, Prototypes & OOP: Notes

## 1. Objects: Literals, Properties, Getters & Setters

An **object** is JavaScript's everyday data structure — an open, dynamic bag of key/value pairs (methods included), roughly like a `Map<String, Object>` with dedicated syntax and no compile-time type constraints. Objects underpin almost everything else in this phase: prototypes, classes, and instances are all, at the engine level, still just objects.

### Key Concepts

- **Object literal**: `{ key: value, method() {} }` — the standard way to build a plain object.
- **Static vs dynamic access**: `obj.key` for a known, literal property name; `obj[expr]` for a computed/dynamic one.
- **Objects are open**: properties can be added or removed at any time (`obj.newProp = 1`, `delete obj.prop`) — unlike a Java class's fixed field set.
- **Getters/setters**: `get x() {}` / `set x(v) {}` look like plain fields from the outside but run code on read/write — used for validation or computed values.
- **Shorthand & computed keys**: `{ val }` is shorthand for `{ val: val }`; `{ [expr]: value }` computes the key name at creation time.

### Worked Example: literal, dynamic keys, and access styles

```js
const book = {
  title: "Effective Java",
  year: 2018,
  "multi word key": true,             // quoted keys are allowed for non-identifier names
  read() { return `reading ${this.title}`; },
};
console.log(book.title, book["multi word key"], book.read());
// Effective Java true reading Effective Java

book.rating = 5;                       // objects are open — add whenever
delete book.year;                      // and remove whenever
const field = "rating";
console.log(book[field]);              // 5 — bracket notation for a dynamic key
```

`book.title` uses **dot notation**, which requires the key to be a literal, valid identifier known at write time. `book[field]` uses **bracket notation**, which evaluates `field` as an expression — this is the only way to access a property whose name is computed at runtime (e.g. coming from user input or a loop variable).

### Worked Example: getters and setters

```js
const account = {
  _balance: 100,                        // convention: leading _ signals "treat as private"
  get balance() { return this._balance; },
  set balance(v) {
    if (v < 0) throw new Error("negative balance");
    this._balance = v;
  },
};
account.balance = 250;                  // looks like a plain assignment, calls the SETTER
console.log(account.balance);           // looks like a plain read, calls the GETTER -> 250
```

From the outside, `account.balance` reads and writes exactly like a normal property — there's no `()` anywhere in the call site. Internally, though, every read runs the `get balance()` function and every write runs `set balance(v)`, which is what lets the setter reject a negative value before it's ever stored.

### Worked Example: shorthand and computed property names

```js
const key = "dynamic";
const val = 42;
const obj = { [key]: val, val };
console.log(obj);   // { dynamic: 42, val: 42 }
```

`[key]: val` computes the property name from the `key` variable at object-creation time (`"dynamic"`), while the bare `val` is shorthand for `val: val` — both are common in modern JS for building objects from variables without repeating names.

### Why It's Useful

Object literals are the default shape for configuration objects, API payloads, and component props throughout the JS ecosystem. Getters/setters let a class or object expose a clean, field-like API while still validating input or computing derived values on the fly (e.g. a `fullName` getter combining `firstName`/`lastName`) — this shows up constantly in class design, covered later in this phase.

### Summary

- Objects are open, dynamic key/value collections — no fixed shape, unlike a Java class.
- Dot notation for known keys, bracket notation for computed/dynamic keys.
- Getters/setters give you field-like syntax with function-like control over reads/writes.
- Shorthand (`{ val }`) and computed keys (`{ [expr]: val }`) are common object-building idioms.

---

## 2. Prototypes & the Prototype Chain

**Prototypes** are the mechanism JavaScript uses to share properties and methods across objects — its built-in answer to inheritance. Every object has an internal link (historically exposed as `__proto__`, the `[[Prototype]]` internal slot) to another object, and when a property lookup misses on the object itself, JavaScript automatically walks up that link to look on the prototype, then the prototype's prototype, and so on, until it finds the property or runs out of chain.

### Key Concepts

- **`[[Prototype]]` / `__proto__`**: every object's internal reference to the object it delegates to when a lookup misses locally.
- **`Function.prototype`**: every ordinary function (not arrows) has a `prototype` property — an object used as the `[[Prototype]]` for every instance created with `new ThatFunction()`.
- **The prototype chain**: the sequence of linked objects a lookup walks through; it always ends at `Object.prototype`, whose own `[[Prototype]]` is `null`.
- **Delegation, not copying**: unlike Java's class-based inheritance (resolved largely at compile time), JS objects look properties up dynamically, at runtime, by walking live links between actual objects.
- **`Object.create(proto)`**: creates a brand-new object whose prototype is explicitly `proto`.

### Worked Example: building a prototype chain with `Object.create`

```js
const animal = {
  breathe() { return `${this.name} breathes`; },
};
const dog = Object.create(animal);   // dog's prototype IS animal
dog.name = "Rex";
dog.bark = () => "woof";

console.log(dog.bark());              // "woof" — dog's own property
console.log(dog.breathe());           // "Rex breathes" — found on animal via the chain
console.log(Object.getPrototypeOf(dog) === animal);   // true
console.log(Object.hasOwn(dog, "breathe"), "breathe" in dog);  // false true
```

`dog` has no `breathe` method of its own — when `dog.breathe()` is called, the engine looks on `dog` first, doesn't find it, then looks on `dog`'s prototype (`animal`), finds it there, and calls it with `this` still bound to `dog`. `Object.hasOwn(dog, "breathe")` correctly reports `false` (it's not `dog`'s own property), while `"breathe" in dog` reports `true` (it's reachable somewhere on the chain) — a distinction worth knowing cold.

### Worked Example: adding methods to a constructor function's prototype

```js
function Person(name, age) {
  this.name = name;
  this.age = age;
}
Person.prototype.greet = function () {
  console.log("Hello, my name is " + this.name);
};

const alice = new Person("Alice", 25);
alice.greet();   // Hello, my name is Alice
```

`greet` is defined exactly **once**, on `Person.prototype`, not copied into every instance. Every object created with `new Person(...)` gets its `[[Prototype]]` set to `Person.prototype`, so all of them share the same underlying `greet` function via delegation — a major memory-efficiency win over attaching a fresh copy of `greet` to every instance.

### Worked Example: manual prototype-based inheritance (pre-ES6)

```js
function Animal(name) { this.name = name; }
Animal.prototype.speak = function () {
  console.log(`${this.name} makes a sound.`);
};

function Dog(name) {
  Animal.call(this, name);              // borrow Animal's constructor logic
}
Dog.prototype = Object.create(Animal.prototype);   // wire up the chain
Dog.prototype.constructor = Dog;                   // fix the constructor reference

Dog.prototype.speak = function () {                // override
  console.log(`${this.name} barks.`);
};

const dog = new Dog("Buddy");
dog.speak();   // Buddy barks.
```

This is the manual version of what the `class`/`extends` syntax (next section) does for you automatically: `Animal.call(this, name)` runs the parent's field-setup logic against the new `Dog` instance, and `Object.create(Animal.prototype)` makes `Dog.prototype`'s own prototype be `Animal.prototype`, so `Dog` instances can fall through to `Animal`'s methods for anything `Dog` doesn't override.

### Worked Example: `prototype` vs `__proto__`

```js
function Person(name) { this.name = name; }
const person1 = new Person("Alice");

console.log(Person.prototype);     // the prototype OBJECT belonging to the function
console.log(person1.__proto__);    // person1's link TO that object
console.log(person1.__proto__ === Person.prototype);  // true
```

`Person.prototype` is a property that exists only on **functions** — it's the object that will become the `[[Prototype]]` of every instance built with `new Person(...)`. `person1.__proto__` is the actual internal link that `person1` holds, pointing at that same object. They describe the relationship from two different sides: the function's "prototype to hand out" vs. an instance's "prototype I was given."

### Comparison Table: `prototype` vs `__proto__`

| | `Constructor.prototype` | `instance.__proto__` |
|---|---|---|
| Exists on | Functions (constructors) | Every object |
| Purpose | The object that will be linked as the prototype of new instances | The actual link to that object, on a specific instance |
| Typical use | Defining shared methods once | Rarely accessed directly — use `Object.getPrototypeOf` instead |

### Why It's Useful

The prototype chain is *why* every object in JS can call `.toString()`, `.hasOwnProperty()`, and other `Object.prototype` methods without anyone explicitly defining them — those methods live at the very top of the chain and are reachable from anywhere. It's also the mechanism `class`/`extends` compiles down to, so understanding prototypes directly demystifies what "JavaScript classes" actually are under the hood, and explains memory-efficient method sharing (one `greet` function object, reused by every instance, instead of one copy per instance).

### Summary

- Every object has a `[[Prototype]]` link; a missed property lookup walks up that chain until found or `null`.
- `Function.prototype` is the object handed out as the `[[Prototype]]` of every `new`-created instance.
- The chain always bottoms out at `Object.prototype → null`.
- `Object.hasOwn(o, k)` checks only the object's own properties; `k in o` checks the whole chain.
- This is delegation between live objects at runtime — fundamentally different from Java's compile-time class inheritance.

---

## 3. `class` Syntax, Constructors & Objects

ES6 introduced the `class` keyword as a cleaner syntax for creating objects and setting up inheritance — but underneath, it's **syntactic sugar over the same prototype system** from the previous section, not a genuinely new object model. A `class` defines a blueprint; `new` produces instances (objects) from that blueprint.

### Key Concepts

- **`class` declares a blueprint**: it groups a `constructor`, instance methods, getters/setters, and static members in one block.
- **`constructor(...)`**: a special method automatically called by `new`, used to initialize the new instance's properties.
- **Methods live on the prototype**: `Square.prototype.area` — not copied per instance — exactly like the manual prototype pattern above.
- **`new ClassName(...)`**: creates a new object, sets its `[[Prototype]]` to `ClassName.prototype`, runs the constructor with `this` bound to the new object, and (unless the constructor explicitly returns another object) returns that new object.
- **Default constructor**: if you don't write one, JS supplies an empty `constructor() {}` (or, in a subclass, one that just forwards arguments to `super(...)`).
- **`#private` fields (ES2022+)**: fields prefixed with `#` are accessible only from inside the class body — a real, enforced privacy boundary, not just a naming convention.

### Worked Example: defining and instantiating a class

```js
class Car {
  constructor(brand, model, year) {
    this.brand = brand;
    this.model = model;
    this.year = year;
  }
  start() { console.log(`${this.brand} ${this.model} is starting.`); }
  stop() { console.log(`${this.brand} ${this.model} is stopping.`); }
  get carInfo() { return `${this.brand} ${this.model}, manufactured in ${this.year}`; }
  static honk() { console.log("Beep beep!"); }
}

const myCar = new Car("Toyota", "Corolla", 2021);
myCar.start();              // Toyota Corolla is starting.
console.log(myCar.carInfo); // Toyota Corolla, manufactured in 2021
Car.honk();                 // Beep beep! — called on the CLASS, not an instance
```

`new Car(...)` triggers the `constructor`, which sets `this.brand`/`this.model`/`this.year` on the freshly-created object. `start`, `stop`, and the `carInfo` getter are all defined once on `Car.prototype` and shared by every `Car` instance. `honk` is `static` — it belongs to the class itself, not to instances (more on that below).

### Worked Example: the constructor's return-value rule

```js
class Example {
  constructor() {
    return { message: "This will override the instance" };
  }
}
const obj = new Example();
console.log(obj);   // { message: 'This will override the instance' }
```

Constructors don't need an explicit `return` — JS automatically returns the freshly-created `this` object. But if a constructor *explicitly* returns another **object**, that object is returned instead of the new instance; returning a primitive value, by contrast, is silently ignored and the normal `this` is still returned. This is a rarely-used but real corner case, occasionally tested in interviews.

### Worked Example: constructor functions (pre-ES6) vs classes

```js
// Pre-ES6: a constructor FUNCTION
function Person(name, age) {
  this.name = name;
  this.age = age;
}
Person.prototype.greet = function () {
  console.log(`Hello, my name is ${this.name} and I am ${this.age} years old.`);
};
const jane = new Person("Jane", 25);
jane.greet();

// ES6: the equivalent class
class PersonClass {
  constructor(name, age) {
    this.name = name;
    this.age = age;
  }
  greet() {
    console.log(`Hello, my name is ${this.name} and I am ${this.age} years old.`);
  }
}
const john = new PersonClass("John", 30);
john.greet();
```

Both produce the same runtime shape — an object with `name`/`age` own properties and a shared `greet` method reachable through the prototype chain. `class` is simply a more readable, more constrained way to write the same thing (it also enforces `new` — calling a class without `new` throws, unlike a constructor function).

### Worked Example: private fields (ES2022)

```js
class Counter {
  #count = 0;                 // private field — only visible inside this class body

  increment() {
    this.#count++;
    console.log(`Count: ${this.#count}`);
  }
}
const counter = new Counter();
counter.increment();  // Count: 1
counter.increment();  // Count: 2
// counter.#count;    // SyntaxError — #count doesn't exist outside the class body
```

`#count` cannot be read, written, or even referenced by name from outside `Counter` — attempting `counter.#count` is a syntax error at parse time, not just a runtime access failure. This is genuinely enforced privacy, unlike the older `_count` naming convention, which is just a hint to other developers with no actual protection.

### Comparison Table: constructor function vs `class`

| | Constructor function (pre-ES6) | `class` (ES6+) |
|---|---|---|
| Method placement | Manually assigned to `Fn.prototype` | Written inside the class body, placed on the prototype automatically |
| Calling without `new` | Silently misbehaves (`this` is wrong) | Throws a `TypeError` |
| Private fields | Only by convention (`_name`) or closures | Enforced with `#field` |
| Inheritance setup | Manual `Object.create` + fixing `.constructor` | `extends` + `super()` |
| Readability | Verbose, easy to get wrong | Concise, closer to other OOP languages |

### Why It's Useful

Classes are the standard way modern JS code models entities with both state and behavior — React class components (legacy), Node.js service/model layers, and most object-oriented library code use them. Knowing that `class` is sugar over prototypes means you can debug "why does `instance.method` work" by reasoning about the prototype chain even when the code never mentions `prototype` directly.

### Summary

- `class` is syntactic sugar over the prototype system — methods still live on `ClassName.prototype`.
- The `constructor` runs automatically on `new`, initializing instance state.
- A constructor that explicitly returns an object overrides the new instance; returning a primitive is ignored.
- `#private` fields (ES2022) are real, enforced privacy — a syntax error to access from outside the class.
- Constructor functions and `class` produce equivalent runtime shapes; `class` is the modern, safer syntax.

---

## 4. Inheritance, Method Overriding & `super`

**Inheritance** lets one class build on another, reusing its properties and methods while adding or changing behavior. In ES6, this is expressed with `extends` (declaring the parent) and `super` (reaching into the parent from the child).

### Key Concepts

- **`extends`**: declares that a subclass's prototype chain includes the superclass's prototype — instances of the subclass inherit everything the superclass defines.
- **`super(...)` in a constructor**: calls the parent class's constructor; **must** be called before `this` can be used anywhere in a subclass constructor, or JS throws a `ReferenceError`.
- **`super.method()`**: calls a specific method from the parent class, typically from inside an overriding method of the same name.
- **Method overriding**: a subclass defines a method with the same name as one in its superclass — the subclass version wins when called on a subclass instance.
- **`instanceof`**: walks the prototype chain to check whether an object was constructed by (or inherits from) a given class.

### Worked Example: basic class inheritance

```js
class Animal {
  constructor(name) { this.name = name; }
  speak() { console.log(`${this.name} makes a noise.`); }
}

class Dog extends Animal {
  constructor(name, breed) {
    super(name);              // must run before using `this` below
    this.breed = breed;
  }
  bark() { console.log(`${this.name} barks. It's a ${this.breed}.`); }
}

const rex = new Dog("Rex", "Golden Retriever");
rex.speak();   // Rex makes a noise.  — inherited, unmodified
rex.bark();    // Rex barks. It's a Golden Retriever.  — Dog's own method
```

`Dog extends Animal` links `Dog.prototype`'s `[[Prototype]]` to `Animal.prototype`, so `rex.speak()` — a method `Dog` never defines — is found by walking up the chain to `Animal.prototype`. `super(name)` runs `Animal`'s constructor logic against the new `Dog` instance, setting `this.name` before the subclass constructor adds `this.breed`.

### Worked Example: method overriding with `super.method()`

```js
class Animal {
  speak() { console.log("Animal makes a noise."); }
}
class Cat extends Animal {
  speak() {
    super.speak();                       // run the parent's version first
    console.log(`${this.name ?? "The cat"} meows.`);
  }
}
new Cat().speak();
// Animal makes a noise.
// The cat meows.
```

`Cat` overrides `speak`, but instead of replacing the parent's behavior entirely, it calls `super.speak()` to run the original implementation first and then adds its own behavior on top — extension rather than pure replacement. Omitting `super.speak()` would fully replace the behavior instead.

### Worked Example: overriding the constructor with extra logic

```js
class Animal {
  constructor(name) {
    this.name = name;
    console.log(`Animal constructor: ${this.name}`);
  }
}
class Dog extends Animal {
  constructor(name, breed = "Mixed") {
    super(name);                        // parent's init logic runs first
    this.breed = breed;                 // then subclass-specific setup
    console.log(`Dog constructor: ${this.name}, Breed: ${this.breed}`);
  }
}
const dog1 = new Dog("Buddy", "Beagle");
// Animal constructor: Buddy
// Dog constructor: Buddy, Breed: Beagle
```

If a subclass defines no constructor at all, JavaScript supplies an implicit one that just forwards all arguments to `super(...)` — so `class Cat extends Animal {}` with no constructor still works exactly like calling `Animal`'s constructor directly.

### Worked Example: static methods and inheritance

```js
class Animal {
  static identify() { console.log("I am an Animal."); }
}
class Dog extends Animal {
  static identify() { console.log("I am a Dog."); }
}
Animal.identify();   // I am an Animal.
Dog.identify();      // I am a Dog. — overridden, like instance methods
```

Static methods are inherited by subclasses too (`Dog.identify` would fall back to `Animal.identify` if `Dog` didn't define its own), but they're called on the **class itself**, never on an instance — `new Dog().identify()` is not valid unless `identify` were also defined as an instance method.

### Worked Example: `instanceof`

```js
class Animal {}
class Dog extends Animal {}
const rex = new Dog();

console.log(rex instanceof Dog);      // true
console.log(rex instanceof Animal);   // true — Dog extends Animal
console.log(rex instanceof Object);   // true — everything ultimately extends Object
```

`instanceof` checks whether `Constructor.prototype` appears anywhere on the object's prototype chain — since `Dog.prototype`'s chain includes `Animal.prototype` (via `extends`) and, ultimately, `Object.prototype`, `rex` reports `true` for all three.

### Comparison Table: pre-ES6 prototype inheritance vs `class`/`extends`

| | Prototype-based (pre-ES6) | `class` + `extends` (ES6+) |
|---|---|---|
| Parent constructor call | `Animal.call(this, name)` | `super(name)` |
| Wiring the chain | `Dog.prototype = Object.create(Animal.prototype)` | Automatic via `extends` |
| Fixing `.constructor` | Manual (`Dog.prototype.constructor = Dog`) | Automatic |
| Calling a parent method | `Animal.prototype.speak.call(this)` | `super.speak()` |
| Readability | Verbose, error-prone | Concise, structured |

### Why It's Useful

Inheritance and method overriding let you model "is-a" relationships and share common behavior without duplicating it — a `Dog` and a `Cat` both being `Animal`s means shared setup (`name`) and shared default behavior (`speak`) live in one place, while each subclass customizes only what's actually different. `instanceof` checks and `super` calls are everyday tools when working with any class hierarchy, from custom error classes (`class ValidationError extends Error`) to framework base classes.

### Summary

- `extends` links a subclass's prototype chain to its superclass; `super(...)` must run before `this` is used in a subclass constructor.
- `super.method()` calls the parent's version of an overridden method — useful for extending rather than replacing behavior.
- Static methods are inherited by subclasses but are called on the class, never an instance.
- `instanceof` walks the prototype chain — true for the exact class and every ancestor class.

---

## 5. Static Methods, Getters & Setters (Class Context)

**Static** members belong to the class itself, not to any instance — they're for functionality that's logically related to the class but doesn't need (or shouldn't have) access to any particular object's data. **Getters/setters** inside a class work exactly like the object-literal versions from section 1, but scoped to instances of that class.

### Key Concepts

- **`static` keyword**: defines a method or field on the class/constructor function itself, not on `ClassName.prototype`.
- **Called on the class, not an instance**: `ClassName.staticMethod()`, never `instance.staticMethod()`.
- **No access to instance data**: a static method has no `this` referring to any particular instance (unless explicitly given one) because it isn't called on one.
- **Typical uses**: utility/helper functions, factory methods, counters tracking all instances.

### Worked Example: defining and using static methods

```js
class Animal {
  constructor(name) { this.name = name; }
  speak() { console.log(`${this.name} makes a noise.`); }
  static identify() { console.log("I am an animal."); }
}
Animal.identify();          // I am an animal. — called on the class

const dog = new Animal("Rover");
// dog.identify();          // TypeError: dog.identify is not a function
```

`identify` simply doesn't exist on instances — it was never placed on `Animal.prototype`, only directly on the `Animal` function/class object. Calling it on `dog` fails exactly the way calling any undefined method would.

### Worked Example: static utility functions

```js
class MathUtil {
  static add(a, b) { return a + b; }
  static subtract(a, b) { return a - b; }
}
console.log(MathUtil.add(5, 3));       // 8
console.log(MathUtil.subtract(10, 7)); // 3
```

`MathUtil` never needs to be instantiated — its methods don't depend on any instance state, so grouping them as statics under a namespacing class (rather than free-floating functions) is purely organizational.

### Worked Example: getters/setters inside a class

```js
class Person {
  constructor(firstName, lastName) {
    this.firstName = firstName;
    this.lastName = lastName;
  }
  get fullName() { return `${this.firstName} ${this.lastName}`; }
  set fullName(name) {
    const [first, last] = name.split(" ");
    this.firstName = first;
    this.lastName = last;
  }
}
const person = new Person("John", "Doe");
console.log(person.fullName);     // John Doe — getter
person.fullName = "Jane Smith";   // setter — splits and reassigns firstName/lastName
console.log(person.firstName, person.lastName);   // Jane Smith
```

`fullName` isn't a stored property at all — it's computed on read from `firstName`/`lastName`, and on write it decomposes the incoming string back into those two underlying fields. Callers interact with `person.fullName` as if it were a plain field, unaware there's logic running behind it.

### Why It's Useful

Static methods are how utility/helper logic gets attached to a class without needing an instance (`Array.isArray`, `Object.keys`, `Math.max` are all static methods on built-in classes/objects). Getters/setters let a class expose a clean, field-like public API while enforcing validation or computing derived values internally — critical for encapsulation, covered next.

### Summary

- `static` members live on the class itself and are called as `ClassName.member`, never on an instance.
- Static methods can't access instance-specific data — there's no particular instance behind the call.
- Getters/setters look like plain properties from the outside but run code on every read/write.
- Statics are inherited by subclasses and can be overridden, same as instance methods.

---

## 6. Encapsulation

**Encapsulation** is the OOP principle of bundling data (properties) and the methods that operate on that data into a single unit, while restricting direct outside access to some of that data — protecting an object's internal state from unintended interference or misuse.

### Key Concepts

- **Bundling**: state and the behavior that manipulates it live together in one class, rather than scattered across the codebase.
- **Restricted access**: only specific, intentional methods can read or modify sensitive internal state.
- **Multiple JS mechanisms**: plain public properties (weak encapsulation), `#private` fields/methods (ES2022, strong encapsulation), and closures (pre-dates classes, still valid).

### Worked Example: encapsulation with `#private` fields

```js
class Person {
  #name;    // private field — declared up front
  #age;

  constructor(name, age) {
    this.#name = name;
    this.#age = age;
  }
  greet() { console.log(`Hello, my name is ${this.#name}.`); }
  #getAge() { return this.#age; }          // private METHOD too
  showAge() { console.log(`I am ${this.#getAge()} years old.`); }
}

const person = new Person("Alice", 30);
person.greet();      // Hello, my name is Alice.
person.showAge();    // I am 30 years old.
// person.#name;     // SyntaxError — #name doesn't exist outside the class
// person.#getAge(); // SyntaxError — same for private methods
```

`#name`, `#age`, and `#getAge` are completely inaccessible outside `Person`'s class body — not just hidden by convention, but a genuine parse-time restriction. The only way to interact with this state from outside is through the public methods `greet` and `showAge`, which fully control what's exposed and how.

### Worked Example: encapsulation via closures (pre-dates `#private`)

```js
function createPerson(name, age) {
  let _name = name;               // private via closure, not a class field
  let _age = age;
  return {
    greet() { console.log(`Hello, my name is ${_name}.`); },
    getAge() { return _age; },
    setAge(newAge) {
      if (newAge > 0) _age = newAge;
      else console.log("Age must be positive.");
    },
  };
}
const person = createPerson("Alice", 30);
person.greet();             // Hello, my name is Alice.
console.log(person.getAge());   // 30
person.setAge(35);
console.log(person.getAge());   // 35
console.log(person._name);      // undefined — _name was never a property of the returned object
```

`createPerson` never attaches `_name`/`_age` to the object it returns — they only exist as local variables captured by the closures of `greet`/`getAge`/`setAge`. This is the exact same closure mechanism from Phase 2, applied here specifically for data hiding — it's how encapsulation was done in JS long before `#private` fields existed, and it still works in any JS environment.

### Why It's Useful

Encapsulation protects an object's internal consistency — a `BankAccount` class can guarantee its `#balance` never goes negative only if nothing outside the class can set it directly, bypassing the `withdraw` method's validation. It also improves maintainability: because outside code can only interact through the public methods, the private implementation can be changed freely without breaking any code that depends on the class.

### Summary

- Encapsulation bundles data and behavior together and restricts direct access to sensitive state.
- `#private` fields/methods (ES2022) give genuine, enforced privacy inside a class body.
- Closures achieve the same effect without classes — private variables live in an outer function's scope, reachable only through returned methods.
- The payoff: internal implementation can change freely as long as the public method contract stays the same.

---

## 7. Abstraction

**Abstraction** means exposing only the relevant, high-level parts of a system while hiding the complex implementation details behind a simple interface. Where encapsulation is about *restricting access* to data, abstraction is about *simplifying the interface* to a system — they're closely related and usually implemented together.

### Key Concepts

- **Simple public interface, hidden complexity**: callers interact with a few well-named public methods, unaware of everything happening behind them.
- **Achieved via `#private` methods**: implementation details can be marked private, leaving only the intended entry points public.
- **No true "abstract classes" in JS**: unlike Java, JS has no `abstract` keyword — the effect is simulated with a base class whose method throws unless a subclass overrides it.
- **Modules as abstraction**: encapsulating logic in a module and exporting only select functions is abstraction at the file level.

### Worked Example: hiding steps behind one public method

```js
class Car {
  #startEngine() { console.log("Engine started"); }
  #checkFuel() { console.log("Fuel level is good"); }

  drive() {                       // the ONE thing callers need to know about
    this.#checkFuel();
    this.#startEngine();
    console.log("Car is now driving");
  }
}
const myCar = new Car();
myCar.drive();
// Fuel level is good
// Engine started
// Car is now driving
```

Callers of `myCar.drive()` never need to know that starting a car involves checking fuel and starting the engine in a particular order — `drive()` is the abstraction, and `#checkFuel`/`#startEngine` are implementation details hidden behind it.

### Worked Example: simulating an abstract base class

```js
class Shape {
  constructor(name) { this.name = name; }
  calculateArea() {
    throw new Error("Method 'calculateArea()' must be implemented");
  }
}
class Circle extends Shape {
  constructor(radius) { super("Circle"); this.radius = radius; }
  calculateArea() { return Math.PI * this.radius * this.radius; }
}
const myCircle = new Circle(5);
console.log(myCircle.calculateArea());   // 78.53981633974483
```

`Shape` acts like an abstract base class: it defines the *shape* of the contract (`calculateArea` must exist) without providing a real implementation — calling it directly on a `Shape` throws, forcing every subclass to supply its own. This is a convention, not a language-enforced guarantee (JS never stops you from instantiating `Shape` itself or forgetting to override the method until it's called), but it's the standard way to express "subclasses must implement this."

### Worked Example: abstraction via a module (IIFE)

```js
const BankModule = (function () {
  let balance = 1000;              // private, via closure
  function deposit(amount) { if (amount > 0) balance += amount; }
  function withdraw(amount) { if (amount > 0 && amount <= balance) balance -= amount; }
  return { deposit, withdraw, getBalance() { return balance; } };   // public surface
})();

BankModule.deposit(200);
BankModule.withdraw(100);
console.log(BankModule.getBalance());   // 1100
```

The IIFE (Phase 2) creates a private scope for `balance`, and the returned object exposes only three intentional entry points — `deposit`, `withdraw`, `getBalance` — abstracting away exactly how the balance is stored and updated.

### Why It's Useful

Abstraction is what lets you use a complex library (say, `fetch`) without knowing anything about TCP sockets or HTTP parsing — you call one function and get a response. Inside your own codebase, the same principle keeps classes and modules usable without forcing every caller to understand their internals, which is essential once a codebase grows past a handful of files.

### Summary

- Abstraction hides implementation complexity behind a small, intentional public interface.
- `#private` methods let a class expose only the methods callers actually need.
- JS has no real `abstract` keyword — a base class with a throwing method simulates the same contract.
- Modules (via IIFEs or ES modules) achieve abstraction at the file level by exporting only select functions.

---

## 8. Polymorphism

**Polymorphism** is the ability of different objects to respond to the *same* method call, each in its own way. In JavaScript, this is achieved almost entirely through **method overriding** (run-time polymorphism) — the language has no built-in method overloading (compile-time polymorphism), since it's dynamically typed and there's no "signature" to dispatch on.

### Key Concepts

- **Run-time polymorphism (method overriding)**: subclasses override a parent's method; calling that method name on different subclass instances runs different code.
- **No compile-time polymorphism**: JS doesn't support method overloading the way Java does — a later function definition with the same name simply replaces an earlier one.
- **Duck typing**: objects with no inheritance relationship at all can still be treated polymorphically as long as they implement a method with the same name and compatible behavior.

### Worked Example: polymorphism via overriding

```js
class Animal {
  speak() { console.log("The animal makes a sound"); }
}
class Dog extends Animal {
  speak() { console.log("The dog barks"); }
}
class Cat extends Animal {
  speak() { console.log("The cat meows"); }
}

const animals = [new Animal(), new Dog(), new Cat()];
animals.forEach(animal => animal.speak());
// The animal makes a sound
// The dog barks
// The cat meows
```

The same call — `animal.speak()` — produces different output depending on the actual (runtime) type of each object in the array, even though the calling code (`animals.forEach(...)`) has no idea which specific subclass it's dealing with. This is the core of polymorphism: uniform calling code, type-specific behavior.

### Worked Example: polymorphism without a shared parent class (duck typing)

```js
class Bird { fly() { console.log("The bird flies"); } }
class Airplane { fly() { console.log("The airplane flies"); } }
class Kite { fly() { console.log("The kite flies"); } }

const flyingObjects = [new Bird(), new Airplane(), new Kite()];
flyingObjects.forEach(object => object.fly());
```

`Bird`, `Airplane`, and `Kite` share no inheritance relationship whatsoever — JS doesn't require one for this to work. As long as each object implements a `fly` method, code that calls `object.fly()` treats them uniformly. This is "duck typing": if it walks like a duck and quacks like a duck (implements the expected methods), it's treated as one.

### Worked Example: polymorphism as a design pattern (Strategy)

```js
class Payment {
  pay(amount) { console.log(`Paid ${amount}`); }
}
class CreditCardPayment extends Payment {
  pay(amount) { console.log(`Paid ${amount} with Credit Card`); }
}
class PayPalPayment extends Payment {
  pay(amount) { console.log(`Paid ${amount} with PayPal`); }
}

function processPayment(paymentMethod, amount) {
  paymentMethod.pay(amount);
}
processPayment(new CreditCardPayment(), 100);   // Paid 100 with Credit Card
processPayment(new PayPalPayment(), 150);       // Paid 150 with PayPal
```

`processPayment` is written once against the general `pay(amount)` contract and works correctly with any object implementing it — this is the Strategy design pattern, and it's polymorphism used deliberately as an architectural tool to keep `processPayment` open to new payment methods without ever needing to change.

### Why It's Useful

Polymorphism is what lets you write code against a general shape ("anything with a `speak()` method," "anything with a `pay()` method") instead of a long chain of `if (obj instanceof X) ... else if (obj instanceof Y) ...` checks. It's foundational to plugin systems, strategy patterns, and any codebase where new behaviors need to be added without modifying existing calling code.

### Summary

- JS achieves polymorphism through method overriding, not method overloading (which it doesn't support).
- The same method call on different objects runs different code, chosen at runtime based on the object's actual type.
- Duck typing means unrelated classes can be treated polymorphically as long as they share a method name/contract.
- Real-world use: the Strategy pattern, plugin systems, and any code written against "anything that implements X."

---

## 9. The Four Pillars of OOP — Summary

JavaScript supports all four traditional pillars of object-oriented programming, through a mix of `class` syntax and its underlying prototype system.

| Pillar | What it means | How JS achieves it |
|---|---|---|
| **Encapsulation** | Bundle data and behavior; restrict direct access to internals | `#private` fields/methods, or closures (module pattern) |
| **Inheritance** | Build a new class from an existing one, reusing its behavior | `extends` + `super()`, or manual `Object.create` prototype chaining |
| **Polymorphism** | The same method call behaves differently per object type | Method overriding (run-time); no method overloading exists |
| **Abstraction** | Expose a simple interface, hide implementation complexity | Public methods calling private ones; simulated abstract base classes; modules |

### Why It's Useful

Interviewers frequently ask "name the four pillars of OOP and give a JS example of each" as a single combined question — having one crisp example per pillar ready (a `BankAccount` with `#balance` for encapsulation, `Dog extends Animal` for inheritance, overridden `speak()` methods for polymorphism, and a `drive()` method hiding `#startEngine()`/`#checkFuel()` for abstraction) covers this cleanly.

### Summary

- **Encapsulation**: hide data, expose controlled access — `#private` fields or closures.
- **Inheritance**: `extends`/`super()` reuse a parent class's behavior.
- **Polymorphism**: overridden methods behave differently per object type — no overloading in JS.
- **Abstraction**: a small public interface hides internal complexity — private methods, simulated abstract classes, modules.

---

## 10. The Object Toolbox & Destructuring

Objects aren't directly iterable, so JavaScript provides a small toolbox of static `Object` methods to convert them into arrays (and back), plus **destructuring** syntax to pull values out of objects (and arrays) concisely.

### Key Concepts

- **`Object.keys(obj)` / `Object.values(obj)` / `Object.entries(obj)`**: convert an object's own enumerable properties into an array of keys, values, or `[key, value]` pairs respectively — the last is what enables `map`/`filter` over an object's contents.
- **`Object.fromEntries(pairs)`**: the inverse of `entries` — builds an object back up from an array of `[key, value]` pairs.
- **Destructuring**: `const { a, b } = obj` extracts named properties into variables in one step, with support for renaming, defaults, nesting, and rest.
- **Spread (`{ ...obj }`)**: shallow-copies or merges objects — the standard way to do immutable updates.
- **`Object.freeze(obj)`**: makes an object's own properties non-writable — shallow only.

### Worked Example: keys, values, entries, and the round trip

```js
const config = { host: "localhost", port: 8080, secure: false };

console.log(Object.keys(config));      // ['host', 'port', 'secure']
console.log(Object.values(config));    // ['localhost', 8080, false]
console.log(Object.entries(config));   // [['host','localhost'], ['port',8080], ['secure',false]]

const upperKeys = Object.fromEntries(
  Object.entries(config).map(([k, v]) => [k.toUpperCase(), v])
);
console.log(upperKeys);   // { HOST: 'localhost', PORT: 8080, SECURE: false }
```

`Object.entries` turns the object into an array of pairs so array methods (`map`, `filter`) become usable on it; `Object.fromEntries` turns a transformed array of pairs back into a plain object — together they form the standard "transform an object" idiom.

### Worked Example: destructuring — defaults, renaming, nesting, rest

```js
const config2 = { host: "localhost", port: 8080 };
const { host, port, secure = true } = config2;    // `secure` gets a default — it's missing
console.log(host, port, secure);   // localhost 8080 true

const response = { data: { user: { id: 1, name: "Ajay" } }, status: 200 };
const { data: { user: { name: userName } }, status } = response;   // nested + renamed
console.log(userName, status);     // Ajay 200

const { host: h, ...rest } = config2;   // rename `host` to `h`, gather the rest
console.log(h, rest);   // localhost { port: 8080 }
```

Destructuring is not just convenient syntax — `{ data: { user: { name: userName } } }` reaches three levels deep in a single statement, and `{ host: h, ...rest }` both renames one property and collects everything else into a new object in the same expression. This exact pattern is everywhere in React (`function Component({ title, onClick, ...rest })`) and Node (`const { PORT, DATABASE_URL } = process.env`).

### Worked Example: spread for immutable updates

```js
const updated = { ...config2, port: 9090, tls: true };
console.log(updated);          // { host: 'localhost', port: 9090, tls: true }
console.log(config2.port);     // 8080 — the original object is untouched
```

`{ ...config2, port: 9090, tls: true }` builds a brand-new object: it copies every property from `config2`, then overwrites `port` and adds `tls`. Because it produces a new object rather than mutating `config2`, this is the standard pattern for immutable state updates (React `setState`, Redux reducers).

### Worked Example: `Object.freeze` — shallow immutability

```js
const frozen = Object.freeze({ x: 1, nested: { y: 2 } });
try {
  frozen.x = 999;
} catch (e) {
  console.log(e.constructor.name);   // TypeError (in strict mode / ES modules)
}
console.log(frozen.x);       // 1 — write was rejected

frozen.nested.y = 999;       // no error — freeze is SHALLOW
console.log(frozen.nested.y);  // 999 — nested objects stay fully mutable
```

`Object.freeze` only locks the object's own top-level properties against reassignment — any object nested inside remains completely mutable unless it's frozen separately. In strict mode (which every ES module runs in), writing to a frozen property throws; in old-style sloppy-mode scripts, the write is silently ignored instead — one more reason ES modules are the safer default.

### Comparison Table: shallow vs deep copy

| | Shallow copy | Deep copy |
|---|---|---|
| Syntax | `{ ...obj }` / `Object.assign({}, obj)` | `structuredClone(obj)` / `JSON.parse(JSON.stringify(obj))` |
| Nested objects | Still shared references with the original | Fully independent copies |
| Handles functions/dates/`undefined` | N/A (copies references) | `structuredClone` mostly yes; `JSON` round-trip loses them |
| Typical use | React/Redux immutable updates (one level deep) | Cloning deeply nested state safely |

### Why It's Useful

`Object.entries`/`fromEntries` is the idiomatic way to transform an object the same way `map`/`filter` transform an array. Destructuring with defaults and renaming is everywhere in modern function signatures (especially React props and Node config objects). Spread-based immutable updates are the backbone of predictable state management (React, Redux) — mutating shared state directly is a common source of subtle bugs that spread-based copying avoids.

### Summary

- `Object.keys/values/entries` convert an object for iteration; `Object.fromEntries` builds one back.
- Destructuring extracts properties into variables with defaults, renaming, nesting, and rest — used constantly in function parameters.
- Spread (`{ ...obj }`) performs a **shallow** copy/merge — nested objects remain shared references.
- `Object.freeze` is shallow immutability only; use `structuredClone` for a true deep copy.
