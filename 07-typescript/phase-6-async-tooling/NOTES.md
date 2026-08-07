<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · advanced types](../phase-5-advanced-types/NOTES.md) | [Phase 7 · in practice ➡](../phase-7-in-practice/NOTES.md)
<!-- /nav -->

# Phase 6 — Async, Modules, Tooling & Config: Notes

The practical layer: how types flow through async code, how modules and declaration
files work, the `tsconfig` settings that actually matter, and the escape hatches
(`as`, `satisfies`, `!`) — including when each is a smell.

## 6.1 — Typing Promises & async/await

- An **`async` function always returns `Promise<T>`**; the annotated return type is
  the **resolved** value, and TS wraps it. A bare `return 42` in `async (): Promise<
  number>` is auto-wrapped. `await` unwraps `Promise<T>` to `T`.
- **`Awaited<T>`** is the type-level unwrap, and it recurses through nested promises
  (`Promise<Promise<string>>` → `string`). It's how `await`'s result type is computed.
- **`Promise.all`** preserves each input's type positionally as a **tuple** (variadic
  tuple types), so you get `[number, string]`, not `(number | string)[]`. Use
  `Promise.allSettled` when you need per-item success/failure (each result is a
  discriminated union `{ status: "fulfilled"; value } | { status: "rejected"; reason }`).
- **`catch (e)` is `unknown`** under `strict` (`useUnknownInCatchVariables`) — because
  JS can throw *anything*, not just `Error`. Narrow with `instanceof Error` before
  using it. This is stricter and safer than assuming `e: Error`.

## 6.2 — Modules & declaration files

- **ES modules**: `import`/`export`; this project is `"type": "module"`. Prefer named
  exports for tree-shaking and refactor-safety.
- **`import type { T }`** imports a **type only** — fully erased, never emitted as a
  runtime `import`. Use it to avoid pulling in runtime code for a type and to break
  import cycles. `verbatimModuleSyntax` enforces the type/value split explicitly.
- **Declaration files `.d.ts`** describe the *types* of plain-JS code with no
  implementation. `declare` introduces **ambient** declarations (a global, a module,
  a value that exists at runtime but TS can't see). Libraries ship `.d.ts` (or you
  install `@types/xxx` from DefinitelyTyped) so their JS is typed for you.

## 6.3 — `tsconfig` that matters

- **`strict: true`** — turn it on and leave it on. It bundles `strictNullChecks`,
  `noImplicitAny`, `strictFunctionTypes`, `useUnknownInCatchVariables`, and more. It's
  the difference between TS catching real bugs and being decorative.
- **`noUncheckedIndexedAccess`** — `arr[i]` / `obj[key]` become `T | undefined`,
  forcing you to handle the missing case (this track enables it). High value.
- **`target` / `lib`** — the JS version emitted and the built-in APIs assumed present
  (`ES2022`, `DOM`, etc.).
- **`module` / `moduleResolution`** — how imports are emitted and resolved
  (`ESNext` + `Bundler` here, matching modern toolchains).
- **`noEmit`** — type-check only; a bundler/`tsx`/esbuild does the actual transpile.
  This is the norm now: **TS checks, esbuild/swc strips types fast** (no full type
  check), which is why running `tsc --noEmit` separately still matters — the fast
  runners *don't* type-check.
- Others worth knowing: `noImplicitReturns`, `noFallthroughCasesInSwitch`,
  `exactOptionalPropertyTypes`, `skipLibCheck` (skip checking `.d.ts` — speeds builds).

## 6.4 — Escape hatches (and when they're smells)

- **`as` (type assertion)** — "trust me." **No runtime check**; it can lie and cause a
  crash later. Legitimate only after you've *actually* verified the value (post-
  validation, well-understood DOM casts). `as any`/double casts are red flags. It is
  **not** a conversion — it changes only the compile-time view.
- **`satisfies` (TS 4.9+)** — check a value conforms to a type **without widening it**.
  Unlike `: T` (which widens the value to `T`) or `as T` (which forces it), `satisfies`
  keeps the precise inferred literal types *and* validates the constraint. Ideal for
  config objects, palettes, route maps — best of both worlds.
- **Non-null `!`** — drops `null | undefined` from a type with no check. Convenient
  against `noUncheckedIndexedAccess`/optional chaining, but unchecked — prefer a real
  narrow (`if (x)`), and reserve `!` for cases where you provably know better than the
  compiler.

Rule: `satisfies` is usually the *good* tool; `as` and `!` are **assertions you're
overriding the compiler** — keep them rare, local, and justified.

## Perspective

Async types "just work" once you internalise that `async` wraps in `Promise` and
`await`/`Awaited` unwrap it, and that `catch` is `unknown` by design. On tooling, the
modern setup is **a fast runner (tsx/esbuild/swc) for running + `tsc --noEmit` for
checking**, with `strict` on. And treat the escape hatches as a spectrum: reach for
`satisfies` freely, `as`/`!` reluctantly and with a reason.
