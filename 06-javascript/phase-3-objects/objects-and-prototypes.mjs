/*
 * Phase 3 — Objects & prototypes
 * Run:  node objects-and-prototypes.mjs
 *
 * Covers 3.1 objects, 3.2 the prototype chain, 3.3 class syntax,
 * 3.4 the Object toolbox + destructuring. JS's inheritance model is
 * fundamentally different from Java's — objects inherit from OBJECTS,
 * not classes from classes.
 */

// ===========================================================================
// 3.1 — Objects: literals, properties, getters/setters
// ===========================================================================
console.log("=== 3.1 objects ===");

// Object literal — the everyday data structure (like a Java Map<String,Object>
// but with syntax support and no type constraints):
const book = {
    title: "Effective Java",
    year: 2018,
    "multi word key": true,        // quoted keys allowed
    read() { return `reading ${this.title}`; },   // method shorthand
};
console.log(book.title, book["multi word key"], book.read());

// Dynamic keys, add/delete at will (objects are open, unlike Java classes):
book.rating = 5;
delete book.year;
const field = "rating";
console.log("computed access:", book[field]);      // bracket notation for dynamic keys

// GETTERS/SETTERS — computed properties that look like fields:
const account = {
    _balance: 100,                                 // convention: _ = "private-ish"
    get balance() { return this._balance; },
    set balance(v) {
        if (v < 0) throw new Error("negative balance");
        this._balance = v;
    },
};
account.balance = 250;                             // calls the setter
console.log("getter:", account.balance);           // calls the getter -> 250

// Computed property names + shorthand:
const key = "dynamic";
const val = 42;
const obj = { [key]: val, val };                   // { dynamic: 42, val: 42 }
console.log("computed/shorthand:", obj);

// ===========================================================================
// 3.2 — The prototype chain (the core of JS inheritance)
// ===========================================================================
console.log("\n=== 3.2 prototype chain ===");

// EVERY object has a hidden link to a PROTOTYPE object. Property lookup that
// misses on the object itself walks UP this chain until found or null.
// This is delegation, not copying — the Java analogy is the superclass
// chain, but here it's OBJECT-to-OBJECT at runtime.
const animal = {
    breathe() { return `${this.name} breathes`; },
};
const dog = Object.create(animal);                 // dog's prototype IS animal
dog.name = "Rex";
dog.bark = () => "woof";
console.log("own method:", dog.bark());
console.log("inherited:", dog.breathe());          // found on animal via the chain
console.log("chain:", Object.getPrototypeOf(dog) === animal);   // true
console.log("hasOwn vs in:",
    Object.hasOwn(dog, "breathe"),                 // false — not its OWN property
    "breathe" in dog);                             // true — reachable via chain

// The full chain ends at Object.prototype -> null:
//   dog -> animal -> Object.prototype -> null
console.log("toString from Object.prototype:", typeof dog.toString);  // 'function'

// ===========================================================================
// 3.3 — `class` syntax (sugar over prototypes)
// ===========================================================================
console.log("\n=== 3.3 class ===");

// `class` LOOKS like Java but is syntactic sugar over the prototype system.
// Methods live on the prototype; each `new` object delegates to it.
class Shape {
    #sides;                              // TRUE private field (# — since ES2022)
    static count = 0;                    // static field

    constructor(name, sides) {
        this.name = name;
        this.#sides = sides;
        Shape.count++;
    }
    describe() { return `${this.name} has ${this.#sides} sides`; }
    get sides() { return this.#sides; }  // read-only access to the private field
}

class Square extends Shape {             // inheritance
    constructor(size) {
        super("square", 4);              // must call super first (like Java)
        this.size = size;
    }
    area() { return this.size ** 2; }
    describe() { return `${super.describe()}, area ${this.area()}`; }  // super method
}

const sq = new Square(5);
console.log(sq.describe());              // square has 4 sides, area 25
console.log("sides getter:", sq.sides);
console.log("instanceof:", sq instanceof Square, sq instanceof Shape);
console.log("static:", Shape.count);
// #sides is truly private: not enumerable, not accessible from outside the
// class body (sq.#sides outside Shape is a compile-time SyntaxError), and
// invisible to reflection:
console.log("private truly hidden:", Object.keys(sq));   // ['name','size'] — no #sides

// ===========================================================================
// 3.4 — Object toolbox + destructuring
// ===========================================================================
console.log("\n=== 3.4 toolbox & destructuring ===");

const config = { host: "localhost", port: 8080, secure: false };

// Iterate objects (they're not directly iterable — convert first):
console.log("keys:", Object.keys(config));
console.log("values:", Object.values(config));
console.log("entries:", Object.entries(config));   // [[k,v],...] — great for map/filter

// Build an object from entries (the inverse) — the transform idiom:
const upperKeys = Object.fromEntries(
    Object.entries(config).map(([k, v]) => [k.toUpperCase(), v]));
console.log("fromEntries:", upperKeys);

// DESTRUCTURING — pull fields into variables (huge in React props):
const { host, port, secure = true } = config;      // secure has a default
console.log("destructured:", host, port, secure);

// Rename + nested + rest:
const response = { data: { user: { id: 1, name: "Ajay" } }, status: 200 };
const { data: { user: { name: userName } }, status } = response;
console.log("nested:", userName, status);

const { host: h, ...rest } = config;                // rest gathers remaining props
console.log("rest:", h, rest);

// SPREAD to copy/merge objects (shallow) — immutable-update style (React!):
const updated = { ...config, port: 9090, tls: true };
console.log("spread merge:", updated);
console.log("original untouched:", config.port);    // 8080 — spread didn't mutate

// Object.freeze for shallow immutability. In strict mode (which ES modules
// ALWAYS use), writing to a frozen property THROWS; in sloppy mode it's
// silently ignored. Another reason modules are safer:
const frozen = Object.freeze({ x: 1 });
try {
    frozen.x = 999;
} catch (e) {
    console.log("frozen write threw:", e.constructor.name);   // TypeError
}
console.log("frozen still:", frozen.x);              // 1
// Note: freeze is SHALLOW — a nested object inside a frozen object stays mutable.
