<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · advanced types](../phase-5-advanced-types/NOTES.md) | [Phase 7 · in practice ➡](../phase-7-in-practice/NOTES.md)
<!-- /nav -->

# Phase 6 — Async, Modules, Tooling & Config: Interview Q&A

⭐ = asked constantly.

**Q: What type does an `async` function return?** ⭐
Always `Promise<T>`, where the declared return type `T` is the *resolved* value — TS
wraps it automatically, so `return 42` inside `async (): Promise<number>` is fine.
`await` unwraps `Promise<T>` back to `T`.

**Q: What is `Awaited<T>`?**
The type-level equivalent of `await`: it unwraps `Promise<T>` to `T` and recurses
through nested promises. It's how the compiler computes the type of an `await`
expression and is handy in generic code that may receive a value or a promise.

**Q: Why is `catch (e)` typed `unknown`?** ⭐⭐
Because JavaScript can `throw` *any* value, not just `Error`. Under `strict`, TS types
the catch variable as `unknown` to force you to narrow (`e instanceof Error`) before
using it — preventing the common bug of assuming `e.message` exists.

**Q: `import type` vs `import`?** ⭐
`import type` brings in only type information and is fully erased — no runtime import
is emitted. Use it for types to avoid unnecessary runtime dependencies and to break
circular imports. `verbatimModuleSyntax` makes the type/value distinction explicit.

**Q: What is a `.d.ts` file?** ⭐
A declaration file containing only type information (no implementation) that describes
the shape of JavaScript code — how libraries ship types, or how you type an untyped
dependency. `declare` introduces ambient declarations for values that exist at runtime
but aren't visible to TS (globals, modules).

**Q: Which `tsconfig` options matter most?** ⭐⭐
`strict: true` above all (bundles null checks, no-implicit-any, unknown catch, etc.).
Then `noUncheckedIndexedAccess` (indexed reads become `T | undefined`), `target`/`lib`
(JS version + APIs), `module`/`moduleResolution` (import handling), and `noEmit` when
a bundler does the transpile. Always develop with `strict` on.

**Q: `as` vs `satisfies` vs a type annotation?** ⭐⭐
`: T` widens the value to `T`. `as T` forcibly asserts the type with **no runtime
check** (can lie — a smell unless verified). `satisfies T` checks the value conforms
to `T` **without widening**, preserving the precise inferred types — the best choice
for config objects and literal maps.

**Q: What does a type assertion actually do at runtime?** *nuance*
Nothing — it's erased. `as` only changes the compile-time view; it is **not** a cast
or conversion and inserts no check. If the value doesn't actually match, you get a
runtime error later at the point of misuse. That's why assertions should follow real
validation.

**Q: When is the non-null assertion `!` acceptable?**
When you provably know a value isn't null/undefined but the compiler can't see it
(e.g. right after a guard it can't track, or a DOM element you know exists). Prefer a
real narrow (`if (x)`); reserve `!` for the rare justified case, since it's unchecked.

**Q: How do fast runners (tsx/esbuild/swc) relate to `tsc`?** *nuance*
They **strip** types for speed and do **not** type-check — they'll happily run code
with type errors. So the modern pipeline pairs a fast runner/bundler for execution
with `tsc --noEmit` (in CI or an editor) for the actual type checking. This track runs
both for exactly that reason.
