<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects](../phase-3-objects/NOTES.md) | [Phase 5 · async ➡](../phase-5-async/NOTES.md)
<!-- /nav -->

# Phase 4 — Arrays & Collections: Notes

## 1. `map()`

`map()` creates a **new array** by calling a transformation function on every element of the original array. It never modifies the original array — it always returns a brand-new one, the same length as the input, with each element replaced by whatever the callback returned for it.

### Key Concepts

- **Non-mutating**: the source array is untouched; `map` always returns a new array.
- **Same length in, same length out**: `map` never adds or removes elements, only transforms them in place (in the new array).
- **Callback signature**: `(currentValue, index, array) => newValue` — index and the full array are available but optional.
- **Chainable**: since it returns an array, `map` composes naturally with `filter`, `reduce`, and further `map` calls.

### Worked Example: basic transformation

```js
const numbers = [1, 2, 3, 4, 5];
const squaredNumbers = numbers.map(num => num * num);
console.log(squaredNumbers);   // [1, 4, 9, 16, 25]
console.log(numbers);          // [1, 2, 3, 4, 5] — original untouched
```

`map` calls the callback once per element of `numbers`, collects each return value in order, and hands back a new array — `numbers` itself is never modified.

### Worked Example: transforming objects

```js
const users = [
  { name: "Alice", age: 25 },
  { name: "Bob", age: 30 },
  { name: "Charlie", age: 35 },
];
const userNames = users.map(user => user.name);
console.log(userNames);   // ['Alice', 'Bob', 'Charlie']
```

A very common real-world shape: extracting a single field from an array of objects — here, pulling just the `name` out of each user record.

### Worked Example: using the index and array arguments

```js
const results = [1, 2, 3, 4].map((num, index, arr) => ({
  value: num,
  index,
  arrayLength: arr.length,
}));
console.log(results);
/*
[
  { value: 1, index: 0, arrayLength: 4 },
  { value: 2, index: 1, arrayLength: 4 },
  { value: 3, index: 2, arrayLength: 4 },
  { value: 4, index: 3, arrayLength: 4 }
]
*/
```

The callback's second and third parameters — `index` and the original `arr` — are rarely needed but available whenever a transformation depends on position or on comparing against the whole array.

### Why It's Useful

`map` is the standard way to reshape data — turning an array of database rows into an array of view models, an array of user objects into an array of display names, or an array of raw API values into an array of formatted strings. It's also the direct JS equivalent of Java's `Stream.map(...)`, except it runs eagerly and returns a real array rather than a lazy stream.

### Summary

- `map()` transforms every element and returns a new array of the same length.
- It never mutates the original array.
- Use it whenever you need "the same data, shaped differently" — not for side effects (use `forEach` for that).

---

## 2. `filter()`

`filter()` creates a **new array** containing only the elements of the original array for which a provided test function returned `true`. Like `map`, it never modifies the original array.

### Key Concepts

- **Non-mutating**: returns a new, possibly shorter, array.
- **Callback must return a boolean** (or a truthy/falsy value): `true`/truthy keeps the element, `false`/falsy drops it.
- **Callback signature**: `(currentValue, index, array) => boolean`.
- **Can return an empty array**: if nothing passes the test, `filter` correctly returns `[]`, not `undefined` or `null`.

### Worked Example: filtering numbers

```js
const numbers = [1, 2, 3, 4, 5, 6];
const evenNumbers = numbers.filter(num => num % 2 === 0);
console.log(evenNumbers);   // [2, 4, 6]
```

The callback runs once per element; `filter` keeps only the elements where it returned `true` — here, the even numbers.

### Worked Example: filtering objects by a property

```js
const people = [
  { name: "Alice", age: 25 },
  { name: "Bob", age: 10 },
  { name: "Charlie", age: 30 },
];
const adults = people.filter(person => person.age > 21);
console.log(adults);
// [{ name: 'Alice', age: 25 }, { name: 'Charlie', age: 30 }]
```

Filtering on an object property is one of the most common real-world uses — here, keeping only the people over 21.

### Why It's Useful

`filter` replaces manual "loop, `if`, push to a new array" boilerplate with a single declarative call, and it's the natural first step in a `filter().map()` (or `filter().map().reduce()`) pipeline: narrow down the data set first, then transform, then aggregate. It's the direct equivalent of Java's `Stream.filter(...)`.

### Summary

- `filter()` returns a new array containing only elements that pass a test.
- The original array is never mutated.
- Combine with `map`/`reduce` for a full "narrow → transform → aggregate" pipeline.

---

## 3. `reduce()`

`reduce()` runs a callback over every element of an array, accumulating a single result as it goes — it's the most general of the three core array methods, capable of building anything: a sum, an object, a grouped map, or even reimplementing `map`/`filter` themselves.

### Key Concepts

- **Accumulator pattern**: the callback receives the running `accumulator` and the `currentValue`, and returns the *next* accumulator value.
- **`initialValue` (second argument to `reduce`)**: seeds the accumulator before the first element. If omitted, the first array element itself becomes the initial accumulator, and iteration starts from the second element — usually you should pass one explicitly to avoid surprises on empty arrays.
- **Callback signature**: `(accumulator, currentValue, currentIndex, array) => nextAccumulator`.
- **Can build any shape**: numbers, strings, arrays, objects — the accumulator can be anything.

### Worked Example: summing numbers

```js
const numbers = [1, 2, 3, 4, 5];
const sum = numbers.reduce((accumulator, currentValue) => accumulator + currentValue, 0);
console.log(sum);   // 15
```

`reduce` starts `accumulator` at `0` (the `initialValue`), then for each element adds it in, threading the running total through every step until one final number remains.

### Worked Example: flattening an array

```js
const nestedArray = [[1, 2], [3, 4], [5, 6]];
const flatArray = nestedArray.reduce((acc, curr) => acc.concat(curr), []);
console.log(flatArray);   // [1, 2, 3, 4, 5, 6]
```

Here the accumulator is itself an array, starting empty (`[]`) and growing by `concat`-ing each nested sub-array onto it — a manual version of what `.flat()` does natively.

### Worked Example: building a grouped structure (histogram)

```js
const words = ["apple", "banana", "avocado", "cherry", "blueberry"];
const byFirstLetter = words.reduce((acc, w) => {
  (acc[w[0]] ??= []).push(w);   // ??= : create the array the first time this letter appears
  return acc;
}, {});
console.log(byFirstLetter);
// { a: ['apple', 'avocado'], b: ['banana', 'blueberry'], c: ['cherry'] }
```

The accumulator here is an object, grouping words by their first letter — `reduce` is general enough to build data structures far more complex than a single number, which is what makes it the "swiss-army-knife" of array methods.

### Comparison Table: `map` vs `filter` vs `reduce`

| Method | Returns | Callback returns | Typical use |
|---|---|---|---|
| `map` | New array, same length | The replacement value | Transform every element |
| `filter` | New array, same or shorter length | `true`/`false` | Keep a subset of elements |
| `reduce` | Anything — a single value of any type | The next accumulator | Aggregate into a sum, object, or any shape |

### Why It's Useful

`reduce` is the one array method that can express everything the others do (you can implement `map` and `filter` purely with `reduce`), which is exactly why it's such a common interview exercise ("implement `map` using `reduce`"). In practice it's most useful whenever the output isn't simply "the same array, transformed or narrowed" — sums, counts, grouped objects, deduplicated structures, and building lookup maps from arrays.

### Summary

- `reduce()` folds an array down to a single accumulated value of any shape.
- Always pass an explicit `initialValue` unless you specifically want the first element as the seed.
- It's general enough to reimplement `map`/`filter`, and the standard tool for sums, groupings, and custom aggregation.

---

## 4. Search, Test, and Chaining Methods

Beyond the core transform-and-aggregate trio, arrays have a family of methods for finding elements and testing conditions across the whole array — and all of the non-mutating methods above chain together naturally into read-as-you-go pipelines.

### Key Concepts

- **`find(fn)`**: returns the *first* element for which `fn` returns truthy, or `undefined` if none match.
- **`findIndex(fn)`**: same search, but returns the index of the match (or `-1`).
- **`some(fn)`**: returns `true` if *at least one* element passes the test.
- **`every(fn)`**: returns `true` only if *all* elements pass the test.
- **`includes(value)`**: returns `true` if the array contains that exact value (uses `===`-like comparison, with special handling for `NaN`).
- **`flat(depth)` / `flatMap(fn)`**: `flat` flattens nested arrays up to `depth` levels (default `1`); `flatMap` is `map` immediately followed by a one-level `flat` — useful when each element maps to *zero or more* results.

### Worked Example: find, findIndex, some, every, includes

```js
const nums = [1, 2, 3, 4, 5, 6, 7, 8];
console.log(nums.find(n => n > 5));        // 6 — first match
console.log(nums.findIndex(n => n > 5));   // 5 — its index
console.log(nums.some(n => n > 7));        // true — at least one qualifies
console.log(nums.every(n => n > 0));       // true — all qualify
console.log(nums.includes(3));             // true
```

### Worked Example: chaining into a pipeline

```js
const words = ["apple", "banana", "avocado", "cherry", "blueberry"];
const result = words
  .filter(w => w.length > 5)
  .map(w => w.toUpperCase())
  .sort();
console.log(result);   // ['AVOCADO', 'BANANA', 'BLUEBERRY']
```

Because `filter` and `map` both return new arrays, they chain directly — read top to bottom as "keep the long words, uppercase them, then sort" — a Java-Streams-like pipeline, except each stage runs eagerly and materializes a full intermediate array (no lazy evaluation).

### Worked Example: `flat` and `flatMap`

```js
console.log([[1, 2], [3, [4]]].flat());              // [1, 2, 3, [4]] — only 1 level by default
console.log([[1, 2], [3, [4]]].flat(2));              // [1, 2, 3, 4]  — 2 levels
console.log([1, 2, 3].flatMap(n => [n, n * 10]));     // [1, 10, 2, 20, 3, 30]
```

`flatMap` is especially handy when a `map` callback would naturally want to return *multiple* items (or none) per input element — mapping each number to a pair `[n, n*10]` and flattening in one step avoids a separate `.flat()` call afterward.

### Why It's Useful

`find`/`some`/`every` communicate intent more precisely than a hand-rolled loop with a `break` — "is there at least one," "do all of them," "give me the first match" read directly as English. Chaining non-mutating methods keeps transformations declarative and easy to reason about top-to-bottom, which is why it's the default style in modern JS codebases.

### Summary

- `find`/`findIndex` locate a single element; `some`/`every` test across the whole array.
- `includes` is the simplest existence check for a literal value.
- `flat`/`flatMap` handle nested-array flattening, including the common "map to multiple results" pattern.
- Non-mutating methods chain naturally into readable, Streams-like pipelines.

---

## 5. `sort()` and the In-Place Gotcha

`sort()` reorders an array's elements **in place** (mutating the original array) and, critically, **compares elements as strings by default** — a frequent source of bugs when sorting numbers.

### Key Concepts

- **Mutates the original array**: unlike `map`/`filter`, `sort()` reorders the array it's called on and also returns that same (now-reordered) array.
- **Default comparison is string-based**: without a comparator, elements are converted to strings and compared lexicographically.
- **Custom comparator**: `arr.sort((a, b) => a - b)` for ascending numeric order; `(a, b) => b - a` for descending.
- **Copy first if you need the original preserved**: `[...arr].sort(...)`.

### Worked Example: the string-sort trap

```js
console.log([10, 2, 1, 20].sort());               // [1, 10, 2, 20] — WRONG for numbers!
console.log([10, 2, 1, 20].sort((a, b) => a - b)); // [1, 2, 10, 20] — correct
```

Without a comparator, `sort` stringifies each element (`"10"`, `"2"`, `"1"`, `"20"`) and compares them character by character — `"10"` sorts before `"2"` because `'1' < '2'` as characters, even though `10 > 2` numerically. Passing `(a, b) => a - b` tells `sort` explicitly how to order two numbers: negative means `a` comes first, positive means `b` comes first, zero means they're equal.

### Worked Example: preserving the original array

```js
const original = [3, 1, 2];
const sorted = [...original].sort((a, b) => a - b);
console.log(original);   // [3, 1, 2] — untouched
console.log(sorted);     // [1, 2, 3]
```

Since `sort()` mutates in place, spreading into a new array first (`[...original]`) before sorting is the standard way to get a sorted *copy* without disturbing the original — essential in React/Redux-style code where state must never be mutated directly.

### Why It's Useful

The string-vs-numeric sort gotcha is one of the most common real bugs in JS code (and one of the most common interview "what does this log" questions) — knowing to always pass a comparator for numeric data avoids it entirely. Knowing that `sort` mutates is equally important any time the array in question is shared state.

### Summary

- `sort()` mutates the array in place and defaults to **string** comparison.
- Always pass a comparator (`(a, b) => a - b`) for correct numeric sorting.
- Copy with `[...arr]` first if the original array must stay untouched.

---

## 6. Mutating vs. Non-Mutating Array Methods

Some array methods change the array they're called on; others always return a new array and leave the original alone. Knowing which is which is essential for predictable code — especially in React/Redux, where state must always be updated immutably.

### Key Concepts

- **Mutating**: `push`, `pop`, `shift`, `unshift`, `splice`, `sort`, `reverse`, `fill` — all change the original array and (mostly) return something other than a full copy (often the removed elements or the new length).
- **Non-mutating**: `map`, `filter`, `slice`, `concat`, `flat`, `flatMap`, and the spread operator — all return a brand-new array, leaving the original untouched.
- **Why it matters for React**: React (and Redux) detect changes by comparing references; mutating an array in place means the reference never changes, so a UI relying on that array may fail to re-render.

### Worked Example: mutating vs. non-mutating side by side

```js
const arr = [1, 2, 3];

console.log(arr.slice(1), arr);   // [2, 3]  [1, 2, 3]  — slice() did NOT touch arr

const copy = [...arr];
copy.push(4);                     // mutates the COPY, not arr
console.log(arr, copy);           // [1, 2, 3]  [1, 2, 3, 4]
```

`slice()` (non-mutating) returns a new array without altering `arr`; even the mutating `push()` is safe here because it's called on `copy`, a separate array produced by spreading `arr` first — the standard pattern for "add an item without mutating the original."

### Comparison Table: mutating vs. non-mutating methods

| Mutating (changes original) | Non-mutating (returns new array) |
|---|---|
| `push()`, `pop()` | `map()`, `filter()` |
| `shift()`, `unshift()` | `slice()`, `concat()` |
| `splice()` | `flat()`, `flatMap()` |
| `sort()`, `reverse()` | spread (`[...arr]`) |
| `fill()` | `Array.from(arr)` |

### Why It's Useful

This distinction is the single most important thing to internalize before writing React state updates: `state.push(item)` silently breaks React's change detection (same array reference, so no re-render is triggered), while `setState([...state, item])` correctly produces a new array reference that React notices. Even outside React, knowing which methods mutate prevents "spooky action at a distance" bugs where a function you called changed an array you still held a reference to elsewhere.

### Summary

- Mutating methods change the array in place; non-mutating methods always return a new array.
- Prefer non-mutating methods (or `[...arr]` + a mutating method on the copy) when the original array is shared state.
- This distinction is the foundation of correct, predictable state updates in React/Redux.

---

## 7. `slice()`

`slice()` extracts a **portion** of an array (or string) and returns it as a new array (or string), without modifying the original. It's purely a read operation — nothing about the source is ever changed.

### Key Concepts

- **Syntax**: `array.slice(start, end)` — `start` is inclusive, `end` is exclusive.
- **Non-mutating**: always returns a new array/string; the original is untouched.
- **Negative indices**: count backward from the end of the array/string.
- **Omitted `end`**: slices through to the end of the array/string.

### Worked Example: array slicing

```js
let fruits = ["apple", "banana", "cherry", "date", "elderberry"];

console.log(fruits.slice(1, 3));    // ['banana', 'cherry'] — indices 1 up to (not including) 3
console.log(fruits.slice(2));       // ['cherry', 'date', 'elderberry'] — to the end
console.log(fruits.slice(-3, -1));  // ['cherry', 'date'] — counting from the end
console.log(fruits);                // unchanged — slice never mutates
```

`slice(1, 3)` grabs the elements at index 1 and 2 (index 3 is excluded, since `end` is exclusive); `slice(-3, -1)` treats `-3` as "3rd from the end" and `-1` as "1st from the end" (also excluded), landing on the same two middle elements from a different direction.

### Worked Example: string slicing

```js
let text = "JavaScript is fun";
console.log(text.slice(0, 10));   // 'JavaScript'
console.log(text.slice(11));      // 'is fun'
console.log(text.slice(-3));      // 'fun'
```

`slice()` on a string works identically to arrays — it's index-based extraction, not mutation (strings are immutable anyway, so a mutating version couldn't exist).

### Why It's Useful

`slice()` is the standard way to make a shallow **copy** of an entire array (`arr.slice()` with no arguments), to grab a "page" of results out of a larger list, or to extract a substring without regular expressions. Because it never mutates, it's always safe to use on shared/state data.

### Summary

- `slice(start, end)` extracts a portion into a new array/string; `end` is exclusive.
- Negative indices count from the end.
- Never mutates the source — safe for cloning or extracting from shared data.

---

## 8. `splice()`

`splice()` **adds, removes, or replaces** elements directly inside an array — unlike `slice()`, it mutates the array it's called on, and it returns an array of whatever elements were removed.

### Key Concepts

- **Syntax**: `array.splice(start, deleteCount, item1, item2, ...)`.
- **`start`**: index to begin changing the array at (negative counts from the end).
- **`deleteCount`**: how many elements to remove starting at `start`; `0` removes nothing.
- **`item1, item2, ...`**: elements to insert at `start`, after the removal — omit these to only remove.
- **Mutating**: changes the original array directly and returns the removed elements (an empty array if none were removed).

### Worked Example: removing elements

```js
let fruits = ["apple", "banana", "cherry", "date", "elderberry"];
let removedFruits = fruits.splice(1, 2);
console.log(fruits);          // ['apple', 'date', 'elderberry'] — 2 elements gone
console.log(removedFruits);   // ['banana', 'cherry'] — what was removed
```

Starting at index 1 (`banana`), `splice` removes 2 elements (`banana`, `cherry`) directly from `fruits` and hands them back as its return value.

### Worked Example: inserting elements

```js
let colors = ["red", "blue", "green"];
colors.splice(1, 0, "yellow", "purple");
console.log(colors);   // ['red', 'yellow', 'purple', 'blue', 'green']
```

With `deleteCount` set to `0`, nothing is removed — `'yellow'` and `'purple'` are simply inserted at index 1, shifting the rest of the array to the right.

### Worked Example: replacing elements

```js
let numbers = [10, 20, 30, 40, 50];
numbers.splice(2, 2, 100, 200);
console.log(numbers);   // [10, 20, 100, 200, 50]
```

Two elements (`30`, `40`) are removed starting at index 2, and two new ones (`100`, `200`) are inserted in their place — a single call does both the removal and the insertion.

### Worked Example: truncating from an index onward

```js
let names = ["John", "Jane", "Doe", "Smith"];
names.splice(2);
console.log(names);   // ['John', 'Jane']
```

Omitting `deleteCount` entirely removes every element from `start` through the end of the array.

### Comparison Table: `slice()` vs `splice()`

| | `slice()` | `splice()` |
|---|---|---|
| Purpose | Extract a section into a new array | Add/remove/replace elements in the original array |
| Mutates original | No | Yes |
| Returns | A new array of the extracted elements | An array of the removed elements |
| Typical use | Copying, pagination, non-destructive extraction | In-place insertion, deletion, or replacement |

### Why It's Useful

`splice()` is the tool for genuine in-place array surgery — removing a specific item from a to-do list, inserting an item at a particular position, or replacing a range of elements — while `slice()` is for read-only extraction/copying. Mixing them up is a common bug source: reaching for `splice()` when you actually wanted a non-mutating copy will silently corrupt shared state.

### Summary

- `splice(start, deleteCount, ...items)` mutates the array — removes, inserts, or replaces elements.
- It returns the array of removed elements (empty if nothing was removed).
- Use `slice()` for non-destructive extraction/copying; use `splice()` only when you specifically want to mutate.

---

## 9. Destructuring, Spread & Iteration Protocols

Array **destructuring** extracts values by position into individual variables; the **spread** operator explodes an array into individual elements/arguments; and the **iteration protocol** (`for...of`, the underlying mechanism array/string/`Map`/`Set` all implement) defines what it means for something to be loopable at all.

### Key Concepts

- **Positional destructuring**: `const [a, b] = arr` — order matters, unlike object destructuring's name-based matching.
- **Skipping elements**: leaving a slot blank (`const [, , third] = arr`) skips that position.
- **Defaults**: `const [x = 0] = arr` applies if the value at that position is `undefined`.
- **The classic swap**: `[a, b] = [b, a]` swaps two variables with no temporary variable needed.
- **Destructuring in parameters**: `({ x, y }) => ...` (object) or `([a, b]) => ...` (array) destructure directly in a function signature.
- **Iterables**: anything implementing the iteration protocol — arrays, strings, `Map`, `Set`, generators — works with `for...of` and spread; plain objects do **not**, by default.

### Worked Example: positional destructuring, skipping, defaults, swapping

```js
const [first, second, ...others] = [10, 20, 30, 40];
console.log(first, second, others);   // 10 20 [30, 40]

const [, , third = 0] = [1, 2];       // skip two slots; third position is missing -> default
console.log(third);                   // 0

let a = 1, b = 2;
[a, b] = [b, a];                      // swap without a temp variable
console.log(a, b);                    // 2 1
```

The commas with nothing between them (`[, , third]`) intentionally skip the first two array elements — only the third position is bound to a variable. The swap trick works because the right-hand side `[b, a]` is fully evaluated into a temporary array *before* the destructuring assignment happens, so there's no risk of overwriting `b` before it's read.

### Worked Example: destructuring in function parameters

```js
const dist = ({ x, y }) => Math.hypot(x, y);
console.log(dist({ x: 3, y: 4 }));   // 5
```

Destructuring directly in a parameter list is extremely common in React (props) and Node (options objects) — the function signature itself documents exactly which fields it expects.

### Worked Example: iteration protocol — `for...of` and `.entries()`

```js
for (const [i, v] of ["a", "b", "c"].entries()) {
  console.log(i, v);
}
// 0 'a'
// 1 'b'
// 2 'c'
```

`.entries()` returns an iterator of `[index, value]` pairs, and `for...of` (which only works on iterables) destructures each pair directly in the loop header — a common pattern when both the index and the value are needed without falling back to a classic indexed `for` loop.

### Why It's Useful

Destructuring eliminates a large amount of `const x = arr[0]; const y = arr[1];` boilerplate, and it's the standard way modern JS function signatures accept structured input. Understanding the iteration protocol explains exactly why `for...of` and spread work on arrays/strings/`Map`/`Set` but throw a `TypeError` on a plain object — plain objects simply don't implement the iterable interface (use `Object.entries`/`keys`/`values` to bridge that gap, Phase 3).

### Summary

- Array destructuring is positional; object destructuring (Phase 3) is by name.
- Skipping, defaults, and the temp-free swap are all standard destructuring idioms.
- `for...of` and spread work on anything *iterable* — arrays, strings, `Map`, `Set`, generators — but not plain objects.
- `.entries()` gives you index + value together, ideal for destructuring inside a `for...of` loop.

---

## 10. `Map`, `Set` & JSON

`Map` and `Set` are dedicated collection types — `Map` for key/value pairs with **any** key type, `Set` for a collection of **unique** values — that complement plain objects and arrays. `JSON` is the text-based serialization format used to move JS data across the wire (APIs, `localStorage`, config files).

### Key Concepts

- **`Set`**: stores unique values only; adding a duplicate is a silent no-op. Methods: `add`, `has`, `delete`, and a real `.size` property.
- **`Map`**: stores key/value pairs where keys can be **any** type, including objects and functions — not just strings/symbols like plain object keys. Preserves insertion order, has a real `.size`, and is directly iterable.
- **Object vs Map**: prefer `Map` for dynamic dictionaries (unknown/changing key sets, non-string keys, frequent add/delete); prefer plain objects for fixed, known shapes (especially anything that needs JSON serialization).
- **`JSON.stringify`/`JSON.parse`**: serialize a JS value to a JSON string and back; several JS-only types don't survive the round trip.

### Worked Example: `Set` for uniqueness and deduplication

```js
const tags = new Set(["js", "ts", "js", "react", "ts"]);
console.log([...tags], tags.size);       // ['js', 'ts', 'react'] 3
console.log(tags.has("react"));          // true

console.log([...new Set([1, 1, 2, 3, 3])]);  // [1, 2, 3] — the standard dedupe idiom
```

Constructing a `Set` from an array automatically drops duplicates (a `Set` simply can't hold two equal values); spreading it back into `[...tags]` produces a plain array again — this "wrap in Set, spread back out" round trip is the idiomatic one-liner for deduplicating an array.

### Worked Example: `Map` with object keys and chaining

```js
const scores = new Map();
scores.set("ajay", 90).set("ravi", 85);    // set() returns the map — chainable
const objKey = { id: 1 };
scores.set(objKey, "object as key!");      // impossible with a plain object

console.log(scores.get("ajay"), scores.size);   // 90 3
for (const [name, score] of scores) {            // Maps are directly iterable
  if (typeof score === "number") console.log(name, score);
}
```

A plain object can only have string or symbol keys — `scores[objKey]` would silently stringify `objKey` to `"[object Object]"`. `Map` supports genuinely *any* value as a key, including an object reference, and two different object instances are always distinct keys even if their contents look identical.

### Comparison Table: `Map` vs plain object

| | `Map` | Plain object |
|---|---|---|
| Key types | Any value (objects, functions, primitives) | Strings and symbols only |
| Insertion order | Guaranteed | Guaranteed for string keys (mostly, with integer-key exceptions) |
| Size | `.size` property | Manual (`Object.keys(obj).length`) |
| Iteration | Directly iterable (`for...of`) | Not iterable — needs `Object.entries` |
| Prototype collisions | None | Possible (`obj["toString"]` collides with inherited methods) |
| JSON serialization | Not supported directly | Native |

### Worked Example: JSON serialize/deserialize

```js
const data = { name: "Ajay", roles: ["dev", "lead"], active: true, joined: null };
const json = JSON.stringify(data);
console.log(json);   // '{"name":"Ajay","roles":["dev","lead"],"active":true,"joined":null}'

const back = JSON.parse(json);
console.log(back.roles[1]);   // 'lead'

console.log(JSON.stringify({ a: 1, b: 2 }, null, 2));   // pretty-printed with 2-space indent
console.log(JSON.stringify({ x: undefined, y: 1 }));    // '{"y":1}' — undefined is dropped
```

`JSON.stringify`'s third argument controls pretty-printing (a number of spaces, or a string like `'\t'`, to indent with); it's purely for readability and has no effect on `JSON.parse`. Note that `undefined` values are silently dropped from the output entirely — not converted to `null` — which is a common source of "why did this field disappear" confusion.

### Comparison Table: JSON serialization pitfalls

| Value type | Behavior in `JSON.stringify` |
|---|---|
| `undefined` (as a property value) | Property is dropped entirely |
| Functions | Dropped entirely |
| `Date` | Converted to an ISO string |
| `Map` / `Set` | Serialized as `{}` (empty object) — data is lost |
| `BigInt` | Throws a `TypeError` |
| Circular reference | Throws a `TypeError` |

### Why It's Useful

`Set` is the fastest way to deduplicate data or do membership checks (`has()` is O(1) on average, unlike `array.includes()`'s O(n) scan). `Map` is the right choice whenever keys aren't naturally strings, or when you need guaranteed insertion order and a reliable size — both come up constantly in caching, grouping, and graph/adjacency-list style data. `JSON` is the universal format for talking to APIs and persisting simple data (`localStorage`), but knowing its blind spots (dates, `Map`/`Set`, functions, `undefined`, circular references) prevents silent data loss — for a true deep clone that preserves more types, prefer `structuredClone()` over the `JSON.parse(JSON.stringify(x))` trick.

### Summary

- `Set` stores unique values; `[...new Set(arr)]` is the standard array-dedupe idiom.
- `Map` supports any key type, preserves insertion order, and is directly iterable — prefer it over objects for dynamic dictionaries.
- `JSON.stringify`/`parse` serialize/deserialize data for APIs and storage, but drop `undefined`/functions, stringify dates, and can't represent `Map`/`Set`/`BigInt`/circular references.
- For a true deep clone, prefer `structuredClone()` over a JSON round trip.
