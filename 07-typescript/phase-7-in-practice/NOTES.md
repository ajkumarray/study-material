<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · async tooling](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — TypeScript in Practice: Notes

Where the type system meets real applications: the untrusted boundary, domain
modelling, error handling, and defeating structural typing when identity matters.
This is the "how to actually use all of the above" phase.

## 7.1 — Type the boundary; validate the untrusted

The critical mental model: **a type annotation is a promise you make to the compiler,
not a runtime check.** Data crossing a trust boundary — `fetch`, `JSON.parse`, form
input, `localStorage`, env vars — arrives as `unknown` (or is *claimed* to be a type
it may not be). Casting it (`as UserDto`) is lying if you haven't verified it.

- **Parse, don't cast.** At the boundary, run a **runtime validator** and only then
  treat the value as its type. A user-defined type guard (`x is UserDto`) narrows
  `unknown` → your type in the success branch.
- In real projects use a **schema library** (Zod, valibot, ArkType): you define the
  schema once and *infer* the static type from it (`z.infer<typeof schema>`), so the
  runtime check and the compile-time type can never drift. Hand-rolled guards (as in
  the demo) are fine for small cases and show what the library automates.

## 7.2 — Make illegal states unrepresentable

The highest-leverage idea in applied TS. Model your domain so the type system **cannot
express** an invalid combination — then bad states become compile errors, not runtime
bugs.

- **Anti-pattern:** parallel booleans/optionals — `{ loading: boolean; data?: T;
  error?: string }` permits `{ loading: true, data, error }`, a contradictory state
  the type happily allows.
- **Pattern:** a **discriminated union** where each state carries exactly its valid
  fields — `idle | loading | { success, data } | { error, message }`. Now `data` only
  exists in `success`, `message` only in `error`, and you can't even *construct* an
  invalid state. Combined with exhaustive `switch` (Phase 5), the compiler guarantees
  every state is handled. This is *the* pattern for UI/request state, forms, and
  domain events.

## 7.3 — Error handling: `Result` vs exceptions

Two valid strategies; know the trade-off:

- **Exceptions** — idiomatic JS/TS, good for truly exceptional/unrecoverable cases and
  cross-cutting failures. Downside: the type system doesn't track what a function can
  throw (no checked exceptions), so failure is invisible in the signature.
- **`Result<T, E>`** (discriminated union) — makes failure **explicit in the return
  type**; the caller *must* check `.ok` before touching `.value` (the compiler blocks
  the error arm otherwise). Pair it with a **discriminated `AppError`** union so
  callers can `switch` on `error.code` exhaustively. Great for expected, recoverable
  domain failures (not found, validation). Trade-off: more plumbing, no automatic
  propagation like `throw`.
- Pragmatic split: `Result` for expected domain outcomes at boundaries you control;
  exceptions for programmer errors and unrecoverable faults. Don't dogmatically pick
  one.

## 7.4 — Branded (nominal) types

Structural typing means two aliases of the same primitive are **interchangeable** —
`type UserId = string` and `type OrderId = string` can be swapped, a real bug source.
A **brand** intersects a phantom, never-constructed tag (`string & { __brand:
"UserId" }`) so the two become incompatible, with **zero runtime cost** (the brand is
erased). You "mint" a branded value through one sanctioned constructor
(`asUserId(s)`), the single place an `as` assertion is allowed. Use for IDs, money,
validated/normalized strings (`Email`, `NonEmptyString`) — anywhere identity matters
and mixing values would be a bug.

## 7.5 — Migrating JS → TS & anti-patterns

- **Migration path:** rename `.js`→`.ts`, start with loose settings, enable `allowJs`
  to mix, add types incrementally, turn on `strict` **per-file** (or `// @ts-check` in
  JSDoc first), then tighten globally. Types at the boundaries first (public APIs,
  data models) give the most value per effort.
- **Anti-patterns to avoid:** reflexive `any` (use `unknown` + narrow); `as` to
  silence errors instead of fixing the type; `@ts-ignore` (prefer `@ts-expect-error`,
  which errors if the problem is fixed, so it can't rot); over-engineered conditional/
  mapped types where an explicit type is clearer; treating external data as trusted.

## Perspective

Applied TypeScript is mostly two habits: **validate at the edges** (types don't check
runtime data — you do) and **model so illegal states can't compile** (discriminated
unions + branded types + `Result`). Do those two things and the compiler stops being a
formality and starts eliminating whole categories of bug before the code runs — the
same "make the right thing easy and the wrong thing impossible" goal as the Software
Design track, enforced by the type system.
