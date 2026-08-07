<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · arrays ➡](../phase-4-arrays/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects & Prototypes: Notes

## 3.1 — Objects
The everyday data structure (like a `Map<String,Object>` with syntax support, no type constraints). Literals `{ key: value, method() {} }`; dynamic add/`delete`; `obj.key` (static) vs `obj[expr]` (dynamic/computed keys). **Getters/setters** (`get x()`/`set x(v)`) look like fields but run code — validation, computed values. Shorthand (`{ val }`) and computed keys (`{ [k]: v }`).

## 3.2 — The prototype chain (JS's inheritance core)
Every object has a hidden link (`[[Prototype]]`) to another object. A property miss on the object walks **up the prototype chain** until found or `null`. This is **delegation between objects**, not class-to-class copying — the deep difference from Java. `Object.create(proto)` makes an object with a given prototype; `Object.getPrototypeOf(o)` reads it. Chain ends: `obj → ... → Object.prototype → null`. `Object.hasOwn(o, k)` tests the object's *own* property; `k in o` also checks the chain. Methods like `toString` come from `Object.prototype` via the chain.

## 3.3 — `class` syntax
Sugar over prototypes: `class`, `constructor`, methods (stored on the prototype, shared by all instances), `extends`, `super()` (must be called first in a subclass constructor — like Java), `super.method()`, `instanceof`. Modern additions: **`#private` fields** (true privacy — inaccessible outside the class body, a *compile-time* error, invisible to `Object.keys`), `static` fields/methods, getters/setters. Looks Java-like but is prototypes underneath: `sq.describe` is found on `Square.prototype`.

## 3.4 — Object toolbox & destructuring
Objects aren't directly iterable — convert: `Object.keys/values/entries` (entries → `[k,v]` pairs, great for map/filter), `Object.fromEntries` (the inverse — the transform idiom). **Destructuring** pulls fields into variables (`const { host, port = 8080 } = config`) with renaming (`{ data: d }`), nesting, defaults, and rest (`{ x, ...rest }`) — ubiquitous in React props and Node. **Spread** `{ ...obj, override }` copies/merges (shallow) for immutable updates (React state). **`Object.freeze`** = shallow immutability; in strict mode (all modules) writing to a frozen prop throws.
