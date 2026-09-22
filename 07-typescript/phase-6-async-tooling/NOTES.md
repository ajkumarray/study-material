<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · advanced types](../phase-5-advanced-types/NOTES.md) | [Phase 7 · in practice ➡](../phase-7-in-practice/NOTES.md)
<!-- /nav -->

# Phase 6 — Async, Modules, Tooling & Config: Notes

The practical layer: how types flow through `Promise`/`async`/`await`, how modules
and declaration files connect TypeScript to plain-JS libraries, which `tsconfig`
settings genuinely matter day to day, and the escape hatches (`as`, `satisfies`,
`!`) — including a clear read on when each one is a code smell.

## 6.1 — Typing Promises and `async`/`await`

- An **`async` function always returns a `Promise<T>`**, where the type you annotate
  is the **resolved** value, not the Promise wrapper itself — TypeScript inserts the
  `Promise<>` wrapping automatically. A bare `return 42;` inside a function declared
  `async (): Promise<number>` type-checks fine; you never write `return
  Promise.resolve(42)` yourself just to satisfy the return type.
- **`await`** unwraps `Promise<T>` down to `T` at the expression level.
  **`Awaited<T>`** is the equivalent operation as a *type*, and — unlike a single
  `await` — it recurses through nested promises (`Awaited<Promise<Promise<string>>>`
  is `string`, not `Promise<string>`), which matters because `await` on a Promise
  that itself resolves to another Promise is automatically flattened by the JS
  runtime, and `Awaited<T>` models that flattening at the type level.
- **`Promise.all`** preserves each input's type **positionally**, as a tuple
  (thanks to variadic tuple types), so `Promise.all([p1, p2])` where `p1:
  Promise<number>` and `p2: Promise<string>` resolves to `[number, string]` — not
  the looser `(number | string)[]`. Use `Promise.allSettled` instead when you need
  per-item success/failure information; each settled result is itself a
  discriminated union, `{ status: "fulfilled"; value: T } | { status: "rejected";
  reason: unknown }`.
- **`catch (e)` is typed `unknown`**, not `Error` and not `any`, under `strict`
  (specifically the `useUnknownInCatchVariables` flag it bundles). This is because
  JavaScript's `throw` can throw *any* value at all — a string, a plain object, a
  genuine `Error` — so assuming `e` is always an `Error` is an unsafe assumption TS
  refuses to bake in by default.

```ts
async function fetchNumber(): Promise<number> {
  return 42;   // a bare 42 is fine — auto-wrapped in Promise<number>
}

type _a1 = Expect<Equal<Awaited<Promise<number>>, number>>;
type _a2 = Expect<Equal<Awaited<Promise<Promise<string>>>, string>>;

async function loadBoth(): Promise<[number, string]> {
  const results = await Promise.all([fetchNumber(), Promise.resolve("ok")]);
  return results;   // typed [number, string], not (number | string)[]
}

function describeError(e: unknown): string {
  if (e instanceof Error) return e.message;   // narrowed to Error
  return String(e);
}

(async () => {
  const n = await fetchNumber();
  console.assert(n === 42, "async returns resolved value");

  const both = await loadBoth();
  console.assert(both[0] === 42 && both[1] === "ok", "Promise.all preserves tuple types");

  try {
    throw new Error("boom");
  } catch (e) {
    console.assert(describeError(e) === "boom", "catch (e: unknown) narrowed via instanceof");
  }
})();
```

In this example: `fetchNumber`'s declared return type is `Promise<number>`, but the
function body just returns `42` — TypeScript automatically treats a bare returned
value inside an `async` function as the resolved value to wrap. `loadBoth` awaits
`Promise.all([...])` over two promises of *different* types (`Promise<number>` and
`Promise<string>`), and the result comes back as the precise tuple `[number,
string]`, so `results[0]` is known to be `number` and `results[1]` is known to be
`string` with no further narrowing needed. `describeError` takes `e: unknown` (the
`catch` variable's real type under `strict`) and must run `e instanceof Error`
before it can safely read `e.message` — without that check, `e.message` would be a
compile error, since `unknown` permits no member access at all until narrowed.

### Why it's useful

Typed Promises mean an `async` function's contract — "what value does this
eventually produce" — is checked the same way a synchronous function's return type
is, with no special-casing needed at call sites; `await` and `Awaited<T>` keep the
runtime unwrapping and the compile-time unwrapping perfectly in sync. `catch (e):
unknown` forces you to actually handle the "this might not be an `Error`" case
instead of assuming `e.message` exists and getting a runtime crash the one time
something throws a plain string or a non-Error rejection value — a real occurrence
with some third-party libraries and with `throw` used for control flow.

### Summary

- `async` functions always return `Promise<T>`; the annotated type is the resolved
  value, auto-wrapped.
- `Awaited<T>` is the type-level `await` and recurses through nested Promises.
- `Promise.all` preserves each item's type positionally as a tuple;
  `Promise.allSettled` gives per-item fulfilled/rejected results instead.
- `catch (e)` is `unknown` under `strict` — narrow with `instanceof Error` (or
  another check) before use, because JS can `throw` anything.

## 6.2 — Modules and declaration files

- **ES modules** — `import`/`export` — are how TypeScript files share code; this
  track's `package.json` sets `"type": "module"`, so Node treats `.js` output as ES
  modules natively. Named exports are generally preferred over default exports for
  better tree-shaking and safer rename-refactors (an IDE can track a named export's
  usages precisely; a default export's local name at each import site is arbitrary).
- **`import type { T }`** imports **only type information** — it is completely
  erased and never emitted as a runtime `import` statement at all. Use it whenever
  you're importing something purely for its type (an interface, a type alias) to
  avoid pulling in runtime code you don't need, and to help break circular-import
  cycles between modules that only reference each other's *types*.
  **`verbatimModuleSyntax`** (a `tsconfig` option) enforces this type/value split
  explicitly rather than leaving it to inference, so `import type` vs `import`
  becomes a required, checked distinction rather than a style preference.
- **Declaration files (`.d.ts`)** contain **only type information, no
  implementation** — they describe the shape of JavaScript code that already exists
  elsewhere. This is how you get types for a plain-JS library: either the library
  ships its own `.d.ts` files, or the community maintains them separately under
  `@types/<package>` on npm (the DefinitelyTyped project). **`declare`** introduces
  an **ambient** declaration — a value, module, or global that TypeScript should
  assume exists at runtime (because something else provides it) without TS itself
  generating any code for it.

```ts
// types.ts
export interface Money { cents: number; currency: string }

// app.ts
import type { Money } from "./types";   // erased entirely — no runtime import emitted
import { formatMoney } from "./format"; // a normal runtime import
```

### Why it's useful

`import type` keeps the compile-time-only parts of your module graph from leaking
into the runtime bundle — smaller output, fewer accidental runtime dependencies, and
one fewer way to accidentally create a circular-import problem. Declaration files
are the entire mechanism that lets the vast, mostly-untyped npm ecosystem be usable
from TypeScript at all without every library author having to rewrite their package
in TS.

### Summary

- `import type` is erased entirely — no runtime import for pure type imports.
- `.d.ts` files describe types with no implementation; `@types/<package>` supplies
  them for libraries that don't ship their own.
- `declare` introduces an ambient value/module TS should trust exists at runtime.
- `verbatimModuleSyntax` makes the type-vs-value import distinction an enforced rule.

## 6.3 — `tsconfig.json` options that actually matter

This track's `tsconfig.json` sets several of these explicitly — worth reading
alongside this section.

- **`strict: true`** — turn it on from day one and leave it on. It's a bundle of
  individually-toggleable flags: `strictNullChecks` (the big one — `null`/
  `undefined` become real, distinct types you must handle explicitly, rather than
  silently assignable to anything), `noImplicitAny` (a parameter/variable that would
  otherwise infer to `any` is an error instead), `strictFunctionTypes`,
  `useUnknownInCatchVariables` (6.1), and more. This is the difference between TS
  catching real bugs and being largely decorative.
- **`noUncheckedIndexedAccess`** (enabled in this track) — makes `arr[i]`/
  `obj[key]` reads `T | undefined` instead of assuming success, forcing explicit
  handling of a missing key or out-of-bounds index (Phase 3).
- **`target` / `lib`** — `target` is the JS language version actually emitted
  (`ES2022` here); `lib` controls which built-in APIs TypeScript assumes are
  available to type-check against (`DOM`, `ES2022`, etc.) — independent of `target`,
  so you can target older JS output while still type-checking against newer runtime
  APIs you know your actual deployment environment supports.
- **`module` / `moduleResolution`** — how `import`/`export` are emitted and how
  specifiers are resolved to files; this track uses `ESNext` + `Bundler`, matching
  how modern bundlers (esbuild, Vite, webpack 5+) resolve imports, rather than the
  older Node-specific resolution algorithms.
- **`noEmit`** — type-check only; produce no output files. A separate fast tool
  (`tsx`/esbuild/swc) does the actual transpile-and-run. This split is the modern
  norm: **a fast runner strips types for speed; `tsc --noEmit` is the thing that
  actually type-checks.** Fast runners do *not* type-check, which is exactly why
  running `tsc --noEmit` separately (as this track's `npm run typecheck` does)
  still matters — skipping it means type errors can silently ship.
- Other flags worth knowing: **`noImplicitReturns`** (every code path in a function
  with a non-`void` return type must actually return a value), **
  `noFallthroughCasesInSwitch`** (a `switch` case that falls through to the next
  without a `break`/`return` is an error, catching a classic JS footgun),
  **`exactOptionalPropertyTypes`** (an optional property `x?: T` means "may be
  absent," strictly distinct from "may be explicitly set to `undefined`" — off by
  default and off in this track, since it's a stricter, opt-in behavior many
  codebases don't need), **`skipLibCheck`** (skip type-checking `.d.ts` files
  entirely, which meaningfully speeds up compilation at the cost of not catching
  errors inside third-party type definitions themselves).

### Why it's useful

`strict` and `noUncheckedIndexedAccess` together close the two most common sources
of "it type-checked but crashed anyway" bugs: unhandled `null`/`undefined`, and
unhandled missing keys/out-of-bounds indices. Understanding `noEmit` plus a fast
runner is the actual shape of virtually every modern TS project's tooling — knowing
that the fast runner *doesn't* check types is what stops a false sense of security
during local development.

### Summary

- `strict: true` is non-negotiable — turn it on from the start of a project.
- `noUncheckedIndexedAccess` closes the "assumed the lookup succeeded" hole.
- `target`/`lib` control emitted JS version and assumed available APIs
  independently; `module`/`moduleResolution` control import emission/resolution.
- `noEmit` + a fast runner (tsx/esbuild/swc) for execution, `tsc --noEmit` for
  checking, is the standard modern pipeline — the fast runner alone does not
  type-check.

## 6.4 — Escape hatches: `as`, `satisfies`, and non-null `!`

Three operators let you override or refine what the compiler would otherwise infer.
They are not equally safe, and knowing which is "usually fine" versus "should be
rare and justified" is a frequently tested distinction.

- **Type assertion `as`** — "trust me, treat this as type `T`." It performs **no
  runtime check whatsoever**; it only changes what the compiler believes about the
  value's type from that point forward. If the value doesn't actually match `T`,
  nothing stops the assertion from compiling — the bug just resurfaces later, at
  the point the mistaken assumption is actually used, as a runtime error TS was
  supposed to prevent. Legitimate uses are narrow: right after you've genuinely
  verified a value's shape some other way (post-validation), or well-understood
  cases like DOM APIs that return a general type you know is more specific in
  context. `as any` or double-casting (`as unknown as T`) are red flags almost
  always worth a second look.
- **`satisfies`** (available since TypeScript 4.9 — supported in this track's
  TS 5.9.3) — checks that a value conforms to a type **without widening the value's
  inferred type**. This is different from both a plain annotation and `as`: `const
  x: T = value` widens `value`'s type to exactly `T` (losing literal precision), and
  `value as T` forcibly asserts `T` with no check at all; `value satisfies T`
  validates the value against `T` (a real compile-time check, catching a mismatch)
  while letting the value **keep its own precise inferred type** for everything
  downstream. It's close to ideal for configuration objects, color palettes, or
  route maps where you want both "prove this matches the shape" and "keep exact
  literal types for later use."
- **Non-null assertion `!`** — drops `null | undefined` from a type with, again,
  **no runtime check**. Handy for working around `noUncheckedIndexedAccess` or
  optional chaining when you are certain (from context the compiler can't see) that
  a value is actually present — but it's an assertion you're overriding the
  compiler with, so prefer a real narrow (`if (x)`) whenever one is practical, and
  reserve `!` for cases you've specifically reasoned through.

```ts
const raw: unknown = "123";
const asStr = raw as string;   // compiles; entirely unchecked
console.assert(asStr.length === 3, "as assertion (unchecked — use sparingly)");

const palette = {
  primary: [255, 0, 0],
  secondary: [0, 255, 0],
} satisfies Record<string, [number, number, number]>;
// Because of `satisfies`, `palette.primary` stays a 3-tuple (not widened to number[]):
const _redChannel: number = palette.primary[0];
console.assert(_redChannel === 255, "satisfies validates without widening");

const list: number[] = [10, 20];
const first = list[0]!;   // number (drops the "| undefined" from noUncheckedIndexedAccess)
console.assert(first === 10, "non-null assertion drops undefined (unchecked)");
```

In this example: `asStr` is produced with `as string` from an `unknown` value with
zero verification that it's actually a string at runtime — it happens to be correct
here, but the assertion itself proves nothing. `palette` is checked against
`Record<string, [number, number, number]>` via `satisfies` — if a value were, say,
a two-element array instead of three, this would be a compile error — while
`palette.primary`'s *inferred* type stays the specific 3-tuple `[number, number,
number]` rather than widening to the less precise `[number, number, number] |
number[]` a plain `: Record<...>` annotation would produce. `list[0]!` overrides
`noUncheckedIndexedAccess`'s `number | undefined` result, asserting "I know this
index exists" with no compiler-verified proof.

| | Plain annotation `: T` | `as T` | `satisfies T` | Non-null `!` |
|---|---|---|---|---|
| Validates the value against `T` | Yes (at declaration) | No (unchecked) | Yes | No (unchecked) |
| Widens the value's inferred type | Yes, to `T` | Yes, to `T` | No — keeps the precise type | No — only removes `null`/`undefined` |
| Runtime check | None | None | None | None |
| Best for | Ordinary variable/parameter declarations | Verified-elsewhere values, well-understood casts | Config objects, literal maps you'll also use precisely later | Provably-non-null values the compiler can't see the proof for |

### Why it's useful

`satisfies` gives you validation *and* precision at once — genuinely the best of
both worlds for the config-object use case — which is why it's usually the right
default over `as` or a plain annotation whenever you want both. `as` and `!` are
occasionally necessary, but both are ways of telling the compiler "trust me, I know
more than you do right here" with zero enforcement behind that claim — treat them as
a deliberate, rare override, not a routine tool.

### Summary

- `as` asserts a type with no runtime check — a smell unless the value was actually
  verified some other way first.
- `satisfies` (TS 4.9+) validates against a type *without* widening — usually the
  best tool for config/literal-map values.
- Non-null `!` drops `null | undefined` with no check — prefer a real narrow (`if
  (x)`) when practical.
- Rule of thumb: reach for `satisfies` freely; reach for `as`/`!` reluctantly, and
  only with a reason you could defend in review.

## Perspective

Async types "just work" once you internalize that `async` wraps in `Promise` and
`await`/`Awaited<T>` unwrap it in lockstep, and that `catch` being `unknown` is a
deliberate safety default, not an oversight. On tooling, the modern setup is
**a fast runner (tsx/esbuild/swc) for execution, `tsc --noEmit` for checking**, with
`strict` on from day one. And treat the escape hatches as a spectrum from safest to
riskiest: `satisfies` is usually the *good* tool that costs you nothing; `as` and
`!` are compiler-overriding assertions to use rarely, locally, and with a reason.
