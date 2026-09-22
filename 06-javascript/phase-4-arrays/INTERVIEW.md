<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects](../phase-3-objects/NOTES.md) | [Phase 5 · async ➡](../phase-5-async/NOTES.md)
<!-- /nav -->

# Phase 4 — Arrays & Collections: Interview Q&A

⭐ = asked constantly.

## `map` / `filter` / `reduce`

**Q: `map` vs `forEach`?** ⭐

`map()` calls a function on every element and returns a **new array** built from the return values — it's pure (doesn't mutate the input) and chainable with other array methods. `forEach()` also calls a function on every element, but always returns `undefined` — it exists purely for side effects (logging, pushing into an external array, mutating something outside the loop). If you find yourself doing `const results = []; arr.forEach(x => results.push(transform(x)))`, that's a sign you should have used `map` instead.

**Q: How does `reduce` work, exactly? Walk through evaluating `[1,2,3].reduce((acc, n) => acc + n, 0)`.** ⭐

`reduce` threads an `accumulator` value through every element of the array, calling `(accumulator, currentValue, index, array) => nextAccumulator` once per element and using each return value as the accumulator for the next call. Starting with `initialValue = 0`: call 1 is `(0, 1) → 1`; call 2 is `(1, 2) → 3`; call 3 is `(3, 3) → 6`. The final accumulator, `6`, is `reduce`'s return value. Because the accumulator can be any type — a number, a string, an array, an object — `reduce` is general enough to build any shape of result, including reimplementing `map` and `filter` themselves.

**Q: What happens if you call `reduce` without an initial value, on an empty array?**

It throws a `TypeError: Reduce of empty array with no initial value`. When no `initialValue` is passed, `reduce` uses the array's first element as the starting accumulator and begins iterating from the second element — but an empty array has no first element to fall back on, so there's nothing to return and nothing to start from. This is exactly why it's best practice to always pass an explicit `initialValue`, even when the array is expected to be non-empty.

**Q: Implement `map` using `reduce`.**

```js
function mapWithReduce(arr, fn) {
  return arr.reduce((acc, item, index, array) => {
    acc.push(fn(item, index, array));
    return acc;
  }, []);
}
console.log(mapWithReduce([1, 2, 3], n => n * 2));  // [2, 4, 6]
```

This demonstrates why `reduce` is considered the most general array method — an empty array accumulator, pushed into on every element, and returned as the final accumulator, reproduces exactly what `map` does natively. The same technique works for `filter` (only push when the predicate is truthy).

**Q: What's the `sort()` gotcha, and how do you avoid it?** ⭐

`sort()` mutates the array in place, and — critically — with no comparator, it converts every element to a **string** and compares lexicographically, not numerically. `[10, 2, 1, 20].sort()` gives `[1, 10, 2, 20]`, because `"10"` sorts before `"2"` as strings. The fix is always to pass an explicit comparator for numeric data: `arr.sort((a, b) => a - b)` for ascending, `(a, b) => b - a` for descending. If the original array must stay untouched, sort a copy: `[...arr].sort(...)`.

**Q: Which array methods mutate, and which return a new array?** ⭐

Mutating: `push`, `pop`, `shift`, `unshift`, `splice`, `sort`, `reverse`, `fill`. Non-mutating (always return a new array, leaving the original untouched): `map`, `filter`, `slice`, `concat`, `flat`, `flatMap`, and spread (`[...arr]`). This matters most in React/Redux, where state updates must always be non-mutating — `state.push(x)` doesn't change the array reference, so React's change detection never notices; `setState([...state, x])` produces a genuinely new reference React can compare against the old one.

---

## `slice` / `splice`

**Q: `slice()` vs `splice()` — what's the difference, and how would you remember which is which?** ⭐

`slice(start, end)` **extracts** a portion of an array into a **new** array — it never modifies the original, and `end` is exclusive. `splice(start, deleteCount, ...items)` **mutates** the original array directly, removing `deleteCount` elements starting at `start` and optionally inserting new `items` in their place — it returns an array of whatever was removed. A memory trick: "sl**i**ce" is non-destructive (think "slice off a piece to look at, leaving the loaf intact"); "sp**l**ice" is destructive (think "splice a cable" — you're physically altering it).

**Q: How would you remove one element from the middle of an array without knowing its index in advance, using `splice`?**

```js
const arr = ["a", "b", "c", "d"];
const index = arr.indexOf("c");
if (index !== -1) arr.splice(index, 1);
console.log(arr);   // ['a', 'b', 'd']
```

Find the index first with `indexOf` (or `findIndex` for object elements), then splice out exactly one element (`deleteCount = 1`) starting there. `splice` mutates `arr` directly — there's no need to reassign the variable.

**Q: How do you insert an element into the middle of an array without removing anything?**

`arr.splice(index, 0, newItem)` — passing `0` as `deleteCount` means nothing is removed, and `newItem` is inserted at `index`, shifting everything from that point onward to the right. This is `splice`'s "pure insertion" mode.

---

## Destructuring, Spread & Iteration

**Q: How is array destructuring different from object destructuring?** ⭐

Array destructuring is **positional** — `const [a, b] = arr` binds `a` to index 0 and `b` to index 1, regardless of any naming; skipping a position (`const [, , third] = arr`) is done with a blank comma slot. Object destructuring (Phase 3) is **by property name** — `const { a, b } = obj` looks for keys literally named `a` and `b`, in any order, and skipping is simply not mentioning that key. Both support default values and nesting, and both can appear directly in function parameter lists.

**Q: Explain the array-swap idiom `[a, b] = [b, a]` — why doesn't it need a temporary variable?**

The right-hand side, `[b, a]`, is fully evaluated into a brand-new temporary array *before* any assignment happens — so by the time destructuring assigns into `a` and `b`, both original values have already been captured safely. It's exactly equivalent to `const temp = a; a = b; b = temp;`, except the "temp" is the literal array JavaScript builds internally, not something you have to name yourself.

**Q: What makes something "iterable" in JavaScript, and why can't you `for...of` a plain object?** ⭐

An iterable is any value that implements the iteration protocol — specifically, it has a `Symbol.iterator` method that returns an iterator object with a `.next()` method producing `{ value, done }` pairs. Arrays, strings, `Map`, `Set`, and generators all implement this out of the box; plain object literals do not, by design (since object "iteration order/shape" isn't as well-defined as, say, an array's). `for...of` and spread (`[...x]`) only work on iterables — trying either on a plain object throws `TypeError: obj is not iterable`. To loop over an object's contents, use `Object.keys/values/entries` (Phase 3) to get an actual iterable array first.

---

## `Map`, `Set` & JSON

**Q: Object vs `Map` — when would you reach for `Map` instead of a plain object?** ⭐

Use `Map` when keys aren't naturally strings (you need object or function keys), when you need guaranteed insertion order plus a reliable `.size`, when keys are added/removed frequently (a `Map` is optimized for this), or when you want to avoid prototype-key collisions (`obj["toString"] = ...` silently shadows an inherited method; a `Map` has no such inherited keys at all). Use a plain object when the shape is fixed and known ahead of time, or when you need native JSON serialization — `Map` doesn't survive `JSON.stringify` intact.

**Q: How do you remove duplicates from an array?** ⭐

`[...new Set(arr)]` — a `Set` can't contain duplicate values, so constructing one from the array automatically discards repeats, and spreading it back turns it into a plain array again. For deduplicating an array of *objects* by some field (where object references are always distinct even if the field values match), use a `Map` keyed on that field instead: `[...new Map(arr.map(o => [o.id, o])).values()]`.

**Q: `Set`/`Map` vs `WeakSet`/`WeakMap` — what's the difference, and when would you use the weak variants?**

`WeakSet`/`WeakMap` hold their entries **weakly** — if the only remaining reference to a key object is the one inside the `WeakMap`/`WeakSet`, the garbage collector is free to reclaim it, and the entry disappears automatically. This trade-off comes with real restrictions: weak collections are not iterable and have no `.size`, because their contents can shrink invisibly at any time as garbage collection runs. They're used for metadata or caches keyed on an object's lifetime — e.g. caching computed data alongside a DOM node without preventing that node from being garbage-collected once it's removed from the page.

**Q: What are the common pitfalls of `JSON.stringify`/`JSON.parse`?** ⭐

`undefined` values (as object properties) and functions are silently dropped from the output entirely — not converted to `null`, just gone. `Date` objects are converted to ISO date strings, and parsing them back gives you a plain string, not a `Date`, unless you reconstruct it manually. `Map` and `Set` serialize as `{}`, losing all their data. `BigInt` values make `JSON.stringify` throw a `TypeError` outright. Circular references (an object that references itself, directly or indirectly) also throw. Deep-cloning via `JSON.parse(JSON.stringify(x))` inherits every one of these losses, which is why `structuredClone(x)` — which correctly handles dates, `Map`/`Set`, and circular references — is the better default for cloning in modern JS.
