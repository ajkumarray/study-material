<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · async tooling](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — TypeScript in Practice: Interview Q&A

⭐ = asked constantly.

**Q: TS types are erased — how do you handle untrusted external data?** ⭐⭐
Validate at runtime at the boundary. A type annotation is a promise, not a check, so
data from `fetch`/`JSON.parse`/forms arrives as `unknown` and must be parsed before
use — via a type guard or (better) a schema library like Zod, from which you *infer*
the static type so the runtime check and the type can't drift. Never `as`-cast
untrusted data.

**Q: What does "make illegal states unrepresentable" mean?** ⭐⭐
Design types so invalid combinations can't be expressed. Instead of
`{ loading: boolean; data?; error? }` (which allows contradictory states), use a
discriminated union — `idle | loading | {success, data} | {error, message}` — so each
state carries exactly its valid fields and you can't construct a bad one. Bugs become
compile errors.

**Q: Why prefer a discriminated union for UI/request state?** ⭐
It ties each field to the state where it's valid (`data` only in `success`), makes
impossible states impossible, and with an exhaustive `switch` the compiler forces you
to handle every case. It replaces error-prone parallel booleans/optionals.

**Q: `Result<T,E>` vs throwing exceptions?** ⭐⭐
`Result` encodes failure in the return type, so the caller must handle it (can't touch
`.value` without checking `.ok`) — good for expected, recoverable domain errors, and
you can `switch` exhaustively on a typed error union. Exceptions suit unrecoverable/
programmer errors and propagate automatically, but the type system doesn't track what
can be thrown (no checked exceptions). Use both, matched to the failure kind.

**Q: What is a branded/nominal type and why use one?** ⭐
An intersection with a phantom tag (`string & { __brand: "UserId" }`) that makes two
otherwise-structurally-identical types incompatible, with no runtime cost. It prevents
mixing values that happen to share a representation — `UserId` vs `OrderId`, validated
`Email` vs raw `string` — a class of bug structural typing allows.

**Q: How would you migrate a JS codebase to TS?**
Incrementally: enable `allowJs` to mix, rename files to `.ts` gradually, add types at
boundaries (public APIs, data models) first, use `// @ts-check`/JSDoc for a soft start,
then turn on `strict` file-by-file and finally globally. Avoid a big-bang rewrite;
types where data enters the system pay off first.

**Q: `@ts-ignore` vs `@ts-expect-error`?** ⭐
`@ts-expect-error` suppresses an error *and* fails the build if there is **no** error
on the next line — so it can't silently rot when the underlying issue is fixed.
`@ts-ignore` suppresses unconditionally and lingers. Prefer `@ts-expect-error`
(ideally with a comment), and better still, fix the type.

**Q: Common TypeScript anti-patterns?** ⭐
Reflexive `any` (use `unknown` + narrow); `as` to silence the compiler instead of
modelling correctly; trusting external data without validation; over-clever
conditional/mapped types where a plain type is clearer; and disabling `strict`. Each
trades away the safety that's the whole point of TS.

**Q: If a schema library infers types, why keep hand-written types at all?** *nuance*
For *internal* data you fully control, hand-written types are simpler and have no
dependency. Schema inference shines at **trust boundaries**, where you need both a
runtime check and a type from one source. Use inference where validation is required,
plain types elsewhere — don't validate data that never leaves your own code.
