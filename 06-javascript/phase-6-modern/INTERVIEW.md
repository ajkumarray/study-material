<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · async](../phase-5-async/NOTES.md) | [Phase 7 · runtime ➡](../phase-7-runtime/NOTES.md)
<!-- /nav -->

# Phase 6 — Modern JS & Modules: Interview Q&A

⭐ = asked constantly.

**Q: ES modules vs CommonJS?** ⭐
ESM: `import`/`export`, statically analyzable (enables tree-shaking), asynchronous loading, the standard for browsers and modern Node (`.mjs`/`"type":"module"`). CJS: `require`/`module.exports`, dynamic, synchronous, Node's legacy system. ESM's static structure is what lets bundlers drop unused code.

**Q: Named vs default exports?**
Named: any number per module, imported by exact name with `{ }` (rename with `as`). Default: one per module, imported under any name. Named exports aid discoverability, refactoring, and tree-shaking; many style guides prefer them over default.

**Q: What is tree-shaking?**
Dead-code elimination by bundlers: because ESM imports/exports are static, the bundler can see which exports are never imported and drop them from the output — smaller bundles. Requires ESM (not dynamic `require`).

**Q: What is a generator?** ⭐
A function (`function*`) that can pause at `yield` and resume, producing values lazily via `.next()`. Useful for lazy/infinite sequences, custom iterables, and (historically) async flow control. It returns an iterator that's also iterable.

**Q: What makes an object iterable?**
Having a `[Symbol.iterator]()` method that returns an iterator (an object with `next()` returning `{ value, done }`). Arrays, strings, Map, Set, and generators are built-in iterables; plain objects are not — that's why `for...of` doesn't work on them directly.

**Q: What problem does optional chaining `?.` solve?** ⭐
Safe access through possibly-null/undefined references without long `a && a.b && a.b.c` guards — `a?.b?.c` short-circuits to `undefined` at the first nullish link. Also `?.()` for maybe-missing methods and `?.[]` for dynamic keys.

**Q: `??` vs `||`?** ⭐
`??` falls back only on `null`/`undefined`; `||` falls back on any falsy value (`0`, `''`, `false`, `NaN`). Use `??` when zero/empty-string/false are valid inputs.

**Q: How do you create a custom error type?**
Extend `Error`, call `super(message)`, set `this.name`, and add any structured fields. Throwing `Error` subclasses (not strings) preserves stack traces and enables `instanceof` narrowing in `catch`.

**Q: How do you propagate an underlying error's context?**
`throw new Error("high-level message", { cause: originalError })` — the `cause` chains the original (like Java's exception cause), inspectable via `err.cause`, and shown in stack traces.
