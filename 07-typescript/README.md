<!-- nav -->
**[🏠 Repo Home](../README.md)**
<!-- /nav -->

# 07 — TypeScript Deep Dive

JavaScript with a **static type system bolted on at compile time**. This is the
track where your Java background pays the biggest dividend on the frontend side:
you already think in types — TypeScript just applies that thinking to JavaScript
(track 06), then *erases* it before the code runs.

Taught **for someone who knows Java (track 01) and JavaScript (track 06)**. Every
lesson leans on both contrasts at once:

- **vs Java** — TS is *structural* (duck) typing, not *nominal*; types are
  erased at runtime (no reflection, no `instanceof` on interfaces); `unknown`
  ≠ `Object`; generics are real (no erasure-to-`Object`, no wildcards — but
  `keyof`/mapped/conditional types go far beyond Java generics).
- **vs JavaScript** — same runtime semantics (`tsx`/`tsc` produce plain JS), but
  the compiler catches the whole class of `undefined is not a function` /
  coercion bugs from JS Phase 1 *before* you run.

Same format as every track: **runnable, type-checked** `.ts` files (each is both
executed with `tsx` and type-checked with `tsc --noEmit`), plus phase-wise
`NOTES.md` and `INTERVIEW.md` with ⭐ markers.

## Toolchain

- **TypeScript 5** (`tsc`) — the type checker / compiler.
- **tsx** — runs `.ts` files directly (`npx tsx file.ts`), no build step.
- **`tsconfig.json`** — `strict: true` + `noUncheckedIndexedAccess`. Verified with
  `npm run typecheck` (`tsc --noEmit` over the whole track).

```bash
cd 07-typescript
npm install              # typescript + tsx (dev deps)
npx tsx phase-1-fundamentals/fundamentals.ts   # run one demo
npm run typecheck        # type-check the entire track
```

Demos prove type errors on purpose with `// @ts-expect-error` — a line that
*fails to compile* if the next line does **not** have the expected type error.
So the type checker verifies our claims about what TS rejects.

## Curriculum

### Phase 1 — Fundamentals & the type system ✅
- [x] 1.1 Why TS; types are erased; structural vs nominal (vs Java)
- [x] 1.2 Primitives, annotations vs inference, `any` vs `unknown` vs `never`
- [x] 1.3 `type` aliases vs `interface`; union & literal types
- [x] 1.4 Narrowing & type guards (`typeof`, `in`, truthiness, `===`)
- File: `phase-1-fundamentals/fundamentals.ts` · NOTES · INTERVIEW

### Phase 2 — Functions & everyday types ✅
- [x] 2.1 Function types, optional/default/rest params, `void` vs `undefined`
- [x] 2.2 Union types & narrowing; literal types; `as const`
- [x] 2.3 Arrays, tuples, `readonly`, enums (and why to prefer unions)
- [x] 2.4 Call signatures, overloads, `this` typing
- File: `phase-2-functions/functions.ts` · NOTES · INTERVIEW

### Phase 3 — Objects, interfaces & classes ✅
- [x] 3.1 `interface` vs `type`, optional/readonly, index signatures
- [x] 3.2 Structural compatibility & excess-property checks
- [x] 3.3 Classes: access modifiers, `implements`, `abstract`, parameter properties
- [x] 3.4 Interface merging, `extends`, intersections
- File: `phase-3-objects-classes/objectsClasses.ts` · NOTES · INTERVIEW

### Phase 4 — Generics ✅
- [x] 4.1 Generic functions & inference (vs Java generics/erasure)
- [x] 4.2 Constraints (`extends`), `keyof`, indexed access `T[K]`
- [x] 4.3 Generic interfaces/classes; default type params
- [x] 4.4 Practical generics: a typed `Result<T,E>`, a mini repository
- File: `phase-4-generics/generics.ts` · NOTES · INTERVIEW

### Phase 5 — Advanced & type-level programming ✅
- [x] 5.1 Unions/intersections; **discriminated unions** + exhaustive `never`
- [x] 5.2 Mapped types & modifiers; conditional types & `infer`
- [x] 5.3 Utility types: `Partial`/`Pick`/`Omit`/`Record`/`Readonly`/`ReturnType`
- [x] 5.4 Template-literal types; typing `keyof` transformations
- File: `phase-5-advanced-types/advancedTypes.ts` · NOTES · INTERVIEW

### Phase 6 — Async, modules, tooling & config ✅
- [x] 6.1 Typing Promises & `async/await`; `Awaited<T>`
- [x] 6.2 Modules, `import type`, declaration files (`.d.ts`) & `declare`
- [x] 6.3 `tsconfig` that matters: `strict`, `noUncheckedIndexedAccess`, targets
- [x] 6.4 Type assertions, `satisfies`, non-null `!`, and when each is a smell
- File: `phase-6-async-tooling/asyncTooling.ts` · NOTES · INTERVIEW

### Phase 7 — TypeScript in practice ✅
- [x] 7.1 Typing an API boundary: DTOs, parsing `unknown`, runtime validation
- [x] 7.2 Modelling with the type system: make illegal states unrepresentable
- [x] 7.3 Error handling: `Result` vs exceptions; typed errors
- [x] 7.4 Migrating JS→TS; common pitfalls & anti-patterns
- File: `phase-7-in-practice/inPractice.ts` · NOTES · INTERVIEW

### Capstone ✅
- [x] Fully-typed expense-tracker domain: discriminated-union events, a generic
  store, utility-type-derived DTOs, a typed `Result` API — all assertion-tested.
- Files: `capstone/expenseDomain.ts` · `CAPSTONE.md`

## How this connects to the rest of the repo

- **← JavaScript (06):** same runtime; TS is JS + types. Every JS gotcha (coercion,
  `undefined`, `this`) is what the type checker now guards.
- **← Java (01):** you already reason in types; the deltas (structural typing,
  erasure-at-runtime, richer generics) are the whole lesson.
- **→ React (08) / Next (09) / Angular (10):** all are written in TS in the real
  world. This track is the prerequisite for typing components, props and hooks.

<!-- phases-nav -->
## 📂 Phase files

- **Phase 1 · fundamentals** — [Notes](phase-1-fundamentals/NOTES.md) · [Interview](phase-1-fundamentals/INTERVIEW.md)
- **Phase 2 · functions** — [Notes](phase-2-functions/NOTES.md) · [Interview](phase-2-functions/INTERVIEW.md)
- **Phase 3 · objects classes** — [Notes](phase-3-objects-classes/NOTES.md) · [Interview](phase-3-objects-classes/INTERVIEW.md)
- **Phase 4 · generics** — [Notes](phase-4-generics/NOTES.md) · [Interview](phase-4-generics/INTERVIEW.md)
- **Phase 5 · advanced types** — [Notes](phase-5-advanced-types/NOTES.md) · [Interview](phase-5-advanced-types/INTERVIEW.md)
- **Phase 6 · async tooling** — [Notes](phase-6-async-tooling/NOTES.md) · [Interview](phase-6-async-tooling/INTERVIEW.md)
- **Phase 7 · in practice** — [Notes](phase-7-in-practice/NOTES.md) · [Interview](phase-7-in-practice/INTERVIEW.md)
<!-- /phases-nav -->
