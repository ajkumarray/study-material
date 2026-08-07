<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · objects](../phase-3-objects/NOTES.md) | [Phase 5 · async ➡](../phase-5-async/NOTES.md)
<!-- /nav -->

# Phase 4 — Arrays & Collections: Notes

## 4.1 — Array methods (Java Streams transfer here)
`map`/`filter`/`reduce` are the core — but unlike Java Streams they run **eagerly** and return new **arrays** (no lazy pipeline, no terminal op). `reduce` is the universal builder (sums, histograms, any shape). Search/test: `find`, `findIndex`, `some`, `every`, `includes`. Chain them for pipelines. `flat`/`flatMap` flatten nesting.

**Gotchas:** `sort()` is **in-place** and sorts as **strings** by default (`[10,2,1].sort()` → `[1,10,2]`) — pass a comparator `(a,b)=>a-b` for numbers. Know **mutating** (`push`/`pop`/`splice`/`sort`/`reverse`) vs **non-mutating** (`map`/`filter`/`slice`/`concat`/spread) methods — React state updates must be non-mutating.

## 4.2 — Destructuring, spread, iteration
**Array destructuring** by position: `const [a, b, ...rest] = arr`; skip with holes (`[, , third]`), defaults, and the temp-free swap `[a, b] = [b, a]`. Destructure in params (`({x, y}) => ...`) — everywhere in React/Node. **Iteration protocol**: `for...of` works on any *iterable* (arrays, strings, Map, Set, generators); objects are not iterable (use `Object.entries`). `.entries()` yields `[index, value]`.

## 4.3 — Map, Set, JSON
**Set** — unique values; dedupe with `[...new Set(arr)]`; `add`/`has`/`delete`/`size`. **Map** — key/value with **any** key type (objects included), insertion-order preserved, real `.size`, directly iterable; prefer over plain objects for dynamic dictionaries (no string-only keys, no prototype-key collisions). `set` returns the map (chainable). **JSON**: `JSON.stringify` (serialize; 3rd arg = pretty-print indent) / `JSON.parse` (deserialize). Gotchas: `undefined`/functions are dropped, dates become strings, Map/Set don't serialize, and `stringify` throws on BigInt and on circular references.
