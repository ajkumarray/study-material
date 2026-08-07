/**
 * Phase 6 — Async, modules, tooling & config.
 *
 * Run:        npx tsx phase-6-async-tooling/asyncTooling.ts
 * Type-check: npm run typecheck
 */

// Reuse the type-level test kit for the type-only claims.
type Equal<A, B> =
  (<T>() => T extends A ? 1 : 2) extends <T>() => T extends B ? 1 : 2 ? true : false;
type Expect<T extends true> = T;

// ---------------------------------------------------------------------------
// 6.1 — Typing Promises & async/await; Awaited<T>.
// ---------------------------------------------------------------------------

// An async function always returns a Promise. The annotated return type is the
// RESOLVED value; TS wraps it in Promise<> for you.
async function fetchNumber(): Promise<number> {
  return 42; // a bare 42 is fine — auto-wrapped in Promise<number>
}

// `await` unwraps Promise<T> to T. `Awaited<T>` is the type-level version and it
// unwraps NESTED promises too (Promise<Promise<T>> -> T).
type _a1 = Expect<Equal<Awaited<Promise<number>>, number>>;
type _a2 = Expect<Equal<Awaited<Promise<Promise<string>>>, string>>;

// Promise.all preserves each element's type as a tuple (variadic tuple types).
async function loadBoth(): Promise<[number, string]> {
  const results = await Promise.all([fetchNumber(), Promise.resolve("ok")]);
  return results; // typed [number, string], not (number|string)[]
}

// `catch (e)` is typed `unknown` (under useUnknownInCatchVariables, part of strict) —
// you MUST narrow before using it. This is the safe, correct default.
function describeError(e: unknown): string {
  if (e instanceof Error) return e.message; // narrowed to Error
  return String(e);
}

// Top-level async IIFE to actually run the assertions.
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

  // ---------------------------------------------------------------------------
  // 6.4 — Assertions, `satisfies`, non-null `!` — and when each is a smell.
  // ---------------------------------------------------------------------------

  // Type assertion `as`: "trust me, it's this type." NO runtime check — it can lie,
  // so it's a smell unless you've truly verified the value (e.g. after validation).
  const raw: unknown = "123";
  const asStr = raw as string; // compiles; unchecked
  console.assert(asStr.length === 3, "as assertion (unchecked — use sparingly)");

  // `satisfies` (TS 4.9+): check a value matches a type WITHOUT widening it. Here we
  // verify the object is a valid config AND keep the precise literal types of values.
  const palette = {
    primary: [255, 0, 0],
    secondary: [0, 255, 0],
  } satisfies Record<string, [number, number, number]>;
  // Because of `satisfies`, `palette.primary` stays a 3-tuple (not number[]):
  const _redChannel: number = palette.primary[0];
  console.assert(_redChannel === 255, "satisfies validates without widening");

  // Non-null assertion `!`: drop null/undefined from a type. Also unchecked — only
  // use when YOU know more than the compiler; otherwise narrow properly.
  const list: number[] = [10, 20];
  const first = list[0]!; // number (drops the `| undefined` from noUncheckedIndexedAccess)
  console.assert(first === 10, "non-null assertion drops undefined (unchecked)");

  console.log("Phase 6: all async/tooling assertions passed.");
})();

// ---------------------------------------------------------------------------
// 6.2 — Modules & import type (illustrative — see companion module below).
// ---------------------------------------------------------------------------
// ES modules: `import { x } from "./m"` / `export`. `import type { T }` imports a
// type ONLY (erased, never emitted as a runtime import) — good for avoiding
// accidental runtime dependencies and circular-import cycles. `.d.ts` files declare
// types for plain-JS libraries; `declare` describes an ambient value with no impl.
// (This track keeps each phase a single self-contained file, so the import is shown
// as a comment rather than split across modules.)
//
//   // types.ts
//   export interface Money { cents: number; currency: string }
//   // app.ts
//   import type { Money } from "./types";   // erased at runtime

// 6.3 — tsconfig that matters (theory; see NOTES for the full rundown):
//   strict: true                 -> the whole strictness bundle (always on)
//   noUncheckedIndexedAccess     -> arr[i]/obj[k] is T | undefined (this track uses it)
//   target / lib                 -> JS version emitted + built-in APIs available
//   module / moduleResolution    -> how imports are emitted/resolved
//   noEmit                       -> type-check only (tsx/esbuild do the running)

export {}; // make this file a module (isolates top-level declarations per phase)
