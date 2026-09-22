<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · async tooling](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — TypeScript in Practice: Interview Q&A

⭐ = asked constantly.

**Q: TypeScript types are erased at runtime — how do you handle untrusted external
data safely?** ⭐⭐
By treating a type annotation as a promise, not a guarantee, and validating at
runtime everywhere data crosses a trust boundary — `fetch` responses,
`JSON.parse` output, form input, environment variables. Concretely: type the raw
value `unknown` (not the target interface, and not `any`), then run a real runtime
check — a hand-written type guard (`function isUserDto(x): x is UserDto`), or,
better, a schema-validation library like Zod — and only treat the value as the
target type in the branch where that check actually passed. With a schema library
specifically, you *infer* the static type from the schema (`z.infer<typeof
schema>`) rather than writing the interface and the validator separately, so the
compile-time type and the runtime check can never drift apart as the shape
evolves. Never `as`-cast untrusted data directly — that's asserting a promise with
no check behind it, exactly the failure mode this whole practice exists to prevent.

**Q: What does "make illegal states unrepresentable" mean, and how do you actually
do it?** ⭐⭐
It means designing your types so that an invalid combination of data literally
cannot be constructed — not just discouraged by convention, but rejected by the
compiler at the point you'd try to write it down. The classic anti-pattern is
parallel independent fields: `{ loading: boolean; data?: Data; error?: string }`
permits `{ loading: true, data, error }` all set simultaneously, a state that makes
no sense but that the type system happily allows because nothing ties those fields
together. The fix is a **discriminated union** — `{ status: "idle" } | { status:
"loading" } | { status: "success"; data } | { status: "error"; message }` — where
each arm carries *exactly* the fields valid for that state and no others. Now
`data` doesn't exist as a property at all on the `"loading"` arm, so there's no way
to even attempt reading stale data while loading, and you can't construct `{
status: "success" }` without also supplying `data` — the bug isn't caught later,
it's impossible to write in the first place.

**Q: Why prefer a discriminated union for UI/request state specifically?** ⭐
Because it ties each field to precisely the state(s) where it's valid, eliminates
whole classes of "how did we get into this combination" bug that boolean-soup state
allows, and — paired with an exhaustive `switch` (Phase 5's `never`-based check) —
the compiler guarantees every state is actually handled somewhere. Add a fifth
state to the union later, and every `switch` over it that doesn't yet handle the
new case becomes a compile error, rather than a silently-missed UI branch that only
surfaces when a user happens to hit that state in production.

**Q: `Result<T, E>` vs throwing exceptions — what's the actual trade-off, and which
would you pick for a given situation?** ⭐⭐
`Result` encodes failure as an explicit part of the return type, and the compiler
enforces that the caller checks `.ok` before it's allowed to read `.value` — the
error arm simply doesn't have a `.value` property until narrowed, so "forgot to
handle the failure path" becomes a compile error rather than a runtime surprise.
Paired with a discriminated `AppError` union, callers can also `switch`
exhaustively on `error.code`. Exceptions remain idiomatic JS/TS and propagate
automatically up the call stack with no plumbing, which suits truly unrecoverable
faults or programmer errors well — but TypeScript has **no checked exceptions**, so
a function's signature gives zero compile-time indication of what it might throw;
a caller has to already know (from docs, convention, or reading the implementation)
that a `try`/`catch` is even necessary. The pragmatic split: `Result` for expected,
recoverable domain outcomes at boundaries you control (not-found, validation);
exceptions for unrecoverable/programmer errors. Don't dogmatically commit an entire
codebase to only one strategy — match the tool to the failure kind.

```ts
function findUser(id: string): Result<UserDto> {
  const u = users.get(id);
  return u ? { ok: true, value: u } : { ok: false, error: { code: "NOT_FOUND", id } };
}
```

**Q: What is a branded/nominal type, and why would you use one when TypeScript is
structurally typed?** ⭐
A branded type intersects a phantom, never-actually-constructed tag onto a
primitive — `type UserId = string & { readonly __brand: "UserId" }` — so that two
otherwise structurally-identical types (like `UserId` and `OrderId`, both backed by
`string`) become incompatible with each other, entirely at the type level and with
**zero runtime cost** (the brand is erased along with everything else). It exists
specifically to prevent the class of bug structural typing otherwise allows:
accidentally passing an `OrderId` where a `UserId` is expected, since at runtime
they're both just plain strings with no distinguishing feature — only the type
system's brand tells them apart, and only if you use it consistently. Use branded
types for entity IDs across different domains, currency-tagged money amounts, and
validated/normalized strings (an `Email` that's already passed validation, distinct
from a raw, unvalidated `string`).

*Follow-up: where is the single `as` assertion allowed for a branded type, and
why does it matter that it's only in one place?* Inside the one sanctioned
"minting" constructor function (`asUserId(s: string): UserId { return s as UserId;
}`). Concentrating the unchecked assertion into a single reviewable function — every
other place in the codebase calls that constructor instead of asserting the brand
themselves — means there's exactly one place to audit or add real validation logic
to, rather than the assertion being scattered wherever a raw string happens to be
used as an id.

**Q: How would you migrate a sizeable JS codebase to TypeScript?**
Incrementally, never as a big-bang rewrite. Enable `allowJs` so `.js` and `.ts`
files coexist during the transition; rename files to `.ts` gradually, file by file
or module by module; add types starting at **public API boundaries and data
models** first, since that's where a caught bug prevents the most downstream
damage and where the return-on-effort is highest; optionally use `// @ts-check` in
JSDoc comments as a lightweight, non-committal first pass on files not yet
converted; turn on `strict` per-file or per-directory as each part is actually
ready (rather than flipping it globally on day one of the migration and being
buried in errors), then tighten it project-wide once the bulk of the conversion is
done.

**Q: `@ts-ignore` vs `@ts-expect-error` — which would you use, and why?** ⭐
Prefer `@ts-expect-error`. It suppresses the error on the following line **and**
fails the build if that line turns out to have **no** error — meaning if the
underlying issue is ever actually fixed (a library updates its types, a refactor
resolves the mismatch), the now-unnecessary suppression comment itself becomes a
build failure, forcing you to remove it and confirm the fix. `@ts-ignore`
suppresses unconditionally with no such self-check, so it can linger indefinitely,
silently masking whatever error exists on that line forever — including a
*different*, unrelated error that shows up there later. Ideally you fix the
underlying type issue instead of suppressing it at all; when you genuinely can't
(a known upstream type-definition bug, for instance), `@ts-expect-error` with an
explanatory comment is the disciplined choice.

**Q: Common TypeScript anti-patterns you've seen or would flag in review?** ⭐
Reflexive `any` used as a default instead of `unknown` plus a real narrow; `as`
used to silence a compiler error rather than fixing the type mismatch it's
correctly flagging; `@ts-ignore` instead of `@ts-expect-error`; trusting external
data (network, disk, user input) without runtime validation; over-clever
conditional/mapped-type constructions where a plain, explicit type would be just as
correct and dramatically more readable; and disabling `strict` (or specific strict
sub-flags) to make errors go away rather than to genuinely opt out of a guarantee
the team has decided it doesn't need. Every one of these trades away exactly the
safety that's the entire reason to use TypeScript over plain JavaScript.

**Q: If a schema library can infer types from a runtime schema, why keep
hand-written `interface`/`type` definitions at all?** *nuance*
For **internal** data your own code fully controls and never validates against an
external source, hand-written types are simpler, have no extra dependency, and
don't pay the (small but real) runtime cost of a schema library's validation
logic. Schema inference earns its keep specifically at **trust boundaries**, where
you need both a runtime check *and* a type derived from the same source so the two
can't drift — API responses, form submissions, config files, anything crossing
from outside your program's control into it. The practical rule: use schema
inference where runtime validation is actually required; use plain hand-written
types everywhere else, where validating data that never leaves your own code would
just be unnecessary overhead.

**Q: How does branded-type "minting" compare to just using a `class` wrapper for
the same nominal-identity goal?** *nuance*
A `class` wrapper (e.g. `class UserId { constructor(readonly value: string) {} }`)
also achieves nominal-style identity — TS checks class instance compatibility more
strictly, closer to nominal typing, because private/protected members and (for
classes with any members at all) the specific class identity factor into
assignability — but it costs a real runtime allocation per id and requires
`.value` unwrapping everywhere you need the raw string (for serialization, string
concatenation, use as a `Map` key, etc.). A branded type costs **nothing** at
runtime — it's still literally a `string` underneath, usable anywhere a `string`
is, with the brand purely enforced by the compiler — which is why branded types are
generally preferred for high-volume, primitive-backed identifiers like IDs, while a
real class remains the right choice when you actually want encapsulated behavior or
invariants attached to the value, not just a type-level identity distinction.
