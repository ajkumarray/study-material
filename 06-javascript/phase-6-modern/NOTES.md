<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · async](../phase-5-async/NOTES.md) | [Phase 7 · runtime ➡](../phase-7-runtime/NOTES.md)
<!-- /nav -->

# Phase 6 — Modern JS & Modules: Notes

## 6.1 — ES modules
Each file is a **module** with its own scope (no globals). **Named exports** (`export const`/`function`) imported by exact name `{ }`; **default export** (`export default`) imported under any name; **namespace import** `import * as m`. Non-exported bindings are truly private to the file. **ESM vs CommonJS:** ESM = `import`/`export`, static (analyzable → tree-shaking), async, the standard (browsers + modern Node, `.mjs` or `"type":"module"`); CJS = `require`/`module.exports`, dynamic, synchronous, legacy Node. Static structure lets bundlers **tree-shake** (drop unused exports).

## 6.2 — Iterators & generators
A **generator** (`function*`) pauses at `yield` and resumes on `.next()`, producing values **lazily** — like a Java Stream/Iterator but written imperatively. Enables lazy/infinite sequences (`while (true) yield n++`) that plain arrays can't express. The **iteration protocol**: an object is iterable if it has a `[Symbol.iterator]()` returning `{ next() => { value, done } }`; then `for...of`, spread, and destructuring all work on it. Generators are the easiest way to make custom iterables.

## 6.3 — Optional chaining & nullish coalescing
**`?.`** short-circuits to `undefined` instead of throwing when a link is `null`/`undefined` — replaces `a && a.b && a.b.c` ladders. Forms: `a?.b` (property), `a?.()` (call if exists), `a?.[i]` (index). **`??`** supplies a default only for `null`/`undefined` (not for `0`/`''`/`false` — the `||` bug from Phase 1.3). Combined idiom: `user?.config?.port ?? 8080`.

## 6.4 — Error handling
`try/catch/finally` like Java, but you can `throw` any value — **always throw `Error` instances** (stack traces, `instanceof` checks). **Custom errors**: `class ValidationError extends Error` — set `this.name`, attach structured fields (e.g. `field`). Narrow by type in `catch` (`if (e instanceof X)`), rethrow the unexpected. **Async errors**: a rejected awaited promise is caught by surrounding `try/catch`; unhandled rejections crash modern Node. **Error `cause`**: `new Error(msg, { cause: originalErr })` chains errors (Java's exception cause) — read via `e.cause`.
