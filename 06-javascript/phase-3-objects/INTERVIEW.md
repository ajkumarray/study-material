<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · functions](../phase-2-functions/NOTES.md) | [Phase 4 · arrays ➡](../phase-4-arrays/NOTES.md)
<!-- /nav -->

# Phase 3 — Objects & Prototypes: Interview Q&A

⭐ = asked constantly.

**Q: What is the prototype chain?** ⭐⭐
Every object links to a prototype object; property/method lookups that miss on the object itself walk up the chain until found or `null`. It's how JS does inheritance — delegation between objects at runtime, not class-based copying. The chain typically ends at `Object.prototype → null`.

**Q: How is JS inheritance different from Java's?** ⭐
Java: classes inherit from classes, resolved largely at compile time. JS: objects delegate to other objects (prototypes) at runtime; `class` is syntactic sugar over this. You can even change an object's prototype at runtime (though you shouldn't for performance).

**Q: Is `class` in JavaScript "real" classes?**
No — it's sugar over prototypes. Methods defined in a class live on `ClassName.prototype`, shared by all instances via the chain. `class` improves readability and adds features (`#private`, `super`) but the underlying model is still prototypal.

**Q: How do you create private fields?** ⭐
Modern: `#field` syntax — truly private, accessible only inside the class body (outside access is a *syntax error*), not enumerable, invisible to `Object.keys`/JSON. Legacy patterns: closures (module pattern) or a `_name` naming convention (private by convention only).

**Q: `Object.keys` vs `for...in` vs `Object.getOwnPropertyNames`?**
`Object.keys` — own enumerable string keys. `for...in` — enumerable keys *including inherited* ones (usually guard with `Object.hasOwn`). `getOwnPropertyNames` — all own keys including non-enumerable. Prefer `Object.keys`/`entries`.

**Q: `hasOwnProperty`/`Object.hasOwn` vs the `in` operator?**
`Object.hasOwn(o, k)` (modern, safe) checks the object's *own* property. `k in o` returns true if the key exists anywhere on the prototype chain too. Use `hasOwn` to exclude inherited properties.

**Q: How do you copy an object? Shallow vs deep?** ⭐
Shallow: `{ ...obj }` or `Object.assign({}, obj)` — nested objects are still shared references. Deep: `structuredClone(obj)` (modern, handles most types), or `JSON.parse(JSON.stringify(obj))` (loses functions/dates/undefined). Know that spread is shallow — a top source of bugs in React state updates.

**Q: What does `Object.freeze` do? Is it deep?**
Makes an object's own properties non-writable/non-configurable — shallow only (nested objects stay mutable). In strict mode (ES modules) writes to frozen props throw; in sloppy mode they're silently ignored.
