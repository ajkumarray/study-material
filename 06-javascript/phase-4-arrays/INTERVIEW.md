<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects](../phase-3-objects/NOTES.md) | [Phase 5 · async ➡](../phase-5-async/NOTES.md)
<!-- /nav -->

# Phase 4 — Arrays & Collections: Interview Q&A

⭐ = asked constantly.

**Q: `map` vs `forEach`?** ⭐
`map` returns a new array of transformed values (pure, chainable); `forEach` returns `undefined` and is for side effects only. Use `map` when you want a result, `forEach` when you just act on each element.

**Q: How does `reduce` work?** ⭐
It folds an array into a single accumulated value: `arr.reduce((acc, item) => next, initialValue)`. The accumulator threads through each element. Can build any shape — sum, object, grouped map, even reimplement map/filter. Always pass an initial value.

**Q: What's the `sort()` gotcha?** ⭐
`sort()` mutates the array in place and, by default, compares elements as **strings** — so `[10, 2, 1].sort()` gives `[1, 10, 2]`. Pass a comparator `(a, b) => a - b` for numeric order. Copy first (`[...arr].sort()`) if you need to preserve the original.

**Q: Which array methods mutate vs return new?** ⭐
Mutate: `push`, `pop`, `shift`, `unshift`, `splice`, `sort`, `reverse`, `fill`. Non-mutating (return new): `map`, `filter`, `slice`, `concat`, `flat`, `flatMap`, spread. Critical for React — state must be updated immutably.

**Q: Object vs Map — when to use which?** ⭐
Map when: keys aren't strings (objects/any type), you need insertion order guaranteed, frequent add/delete, or a real `.size`. Object when: fixed known keys, JSON serialization, or record-like data. Map also avoids prototype-key collisions (`obj["toString"]`).

**Q: How do you remove duplicates from an array?**
`[...new Set(arr)]` — Set stores unique values, spread turns it back into an array. For objects (deduped by a field) use a Map keyed on that field.

**Q: `Set` vs `WeakSet` / `Map` vs `WeakMap`?**
Weak variants hold keys *weakly* — entries are garbage-collected when the key object has no other references, and they're not iterable and have no `.size`. Used for metadata/caches keyed on objects without preventing their collection (e.g., memoization tied to object lifetime).

**Q: What are common `JSON.stringify` pitfalls?**
Drops `undefined` and functions, converts `Date` to ISO strings, can't serialize `Map`/`Set`/`BigInt` (throws on BigInt), and throws on circular references. Deep-cloning via `JSON.parse(JSON.stringify(x))` inherits all these losses — prefer `structuredClone`.
