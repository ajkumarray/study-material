<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · async tooling](../phase-6-async-tooling/NOTES.md)
<!-- /nav -->

# Phase 7 — TypeScript in Practice: Notes

Where the type system meets real applications: validating the untrusted boundary,
modelling domains so bad states can't be constructed, choosing between typed error
handling and exceptions, and deliberately defeating structural typing when identity
genuinely matters. This is the "how to actually use everything from Phases 1–6"
phase, and it's where a TypeScript interview usually moves once the fundamentals are
established.

## 7.1 — Type the boundary; validate the untrusted

The single most important mental model in applied TypeScript: **a type annotation
is a promise you make to the compiler, not a runtime check.** Nothing about writing
`const data: UserDto = await response.json()` verifies that the JSON actually has
that shape — it just tells TypeScript to *believe* it does, and TypeScript trusts
you completely. Data crossing a trust boundary — a `fetch` response, `JSON.parse`,
form input, `localStorage`, environment variables, a message queue payload — arrives
as effectively unverified and should be typed `unknown` until proven otherwise.

- **Parse, don't cast.** At the boundary, run an actual **runtime validator** and
  only afterward treat the value as its type. Casting untrusted data with `as
  UserDto` is lying to the compiler if you haven't actually verified it — the type
  system will happily let you dereference fields that don't exist, and the failure
  shows up later, confusingly, far from where the bad data entered.
- A **user-defined type guard** (`x is UserDto`, from Phase 1) is the idiomatic way
  to narrow `unknown` to your type in the success branch, backed by real runtime
  checks in the guard's body.
- In real projects, prefer a **schema-validation library** (Zod, valibot, ArkType,
  and similar) over hand-rolled guards: you define the schema once, and the
  library both performs the runtime check *and* lets you `infer` the static
  TypeScript type from that same schema (`z.infer<typeof schema>`), so the runtime
  check and the compile-time type can never drift apart. The hand-rolled guard
  below is what such a library automates and is perfectly fine for a small,
  one-off case.

```ts
interface UserDto {
  id: string;
  name: string;
  age: number;
}

function isUserDto(x: unknown): x is UserDto {
  return (
    typeof x === "object" &&
    x !== null &&
    "id" in x &&
    typeof (x as Record<string, unknown>).id === "string" &&
    "name" in x &&
    typeof (x as Record<string, unknown>).name === "string" &&
    "age" in x &&
    typeof (x as Record<string, unknown>).age === "number"
  );
}

function parseUser(json: string): UserDto | null {
  const raw: unknown = JSON.parse(json);   // JSON.parse's return type is `any`, but
  return isUserDto(raw) ? raw : null;      // treat it as unknown until validated
}

console.assert(parseUser('{"id":"u1","name":"Ada","age":36}')?.name === "Ada", "valid DTO parses");
console.assert(parseUser('{"id":"u1","name":"Ada"}') === null, "missing field rejected");
console.assert(parseUser('{"id":1,"name":"Ada","age":36}') === null, "wrong type rejected");
```

In this example: `raw` is explicitly annotated `unknown` even though
`JSON.parse`'s actual return type is `any` — this deliberately re-introduces the
safety `any` would otherwise silently discard. `isUserDto` checks each required
field's presence and runtime type one by one; only when every check passes does it
return `true`, and only then does `parseUser`'s `isUserDto(raw) ? raw : null`
narrow `raw` to `UserDto` in the successful branch. The three assertions cover the
three cases a real trust boundary needs to handle: valid data parses, a missing
field is rejected, and a field with the *wrong runtime type* (an `id` that's a
`number` instead of a `string` — something a type annotation alone would never
catch) is also rejected.

### Why it's useful

Validating at the boundary is what stops "the type checker said this was fine" from
becoming "and then it crashed in production because the actual JSON didn't match" —
the type system's guarantees only ever extend as far as your own code's boundaries;
beyond that, nothing enforces them without an explicit runtime check. A
schema-inferred type additionally removes the maintenance burden of keeping a
hand-written validator and a hand-written interface in sync as the shape evolves.

### Summary

- A type annotation is a compile-time promise, not a runtime guarantee.
- Data from outside your program (network, disk, user input) should be typed
  `unknown` until validated.
- Parse, don't cast: use a type guard (or better, a schema library) to prove the
  shape before treating a value as its type.
- Schema libraries let one schema serve as both the runtime check and the inferred
  static type, eliminating drift between the two.

## 7.2 — Make illegal states unrepresentable

Arguably the highest-leverage idea in applied TypeScript: model your domain so the
type system **cannot even express** an invalid combination of data — then bad
states become compile errors (or, better, simply un-writable code), not runtime
bugs discovered after the fact.

- **Anti-pattern:** parallel independent booleans/optionals —
  `interface State { loading: boolean; data?: Data; error?: string }` — permits
  nonsense combinations like `{ loading: true, data, error }` (loading, but also
  somehow already has both a successful result *and* an error) that the type
  system happily allows, because nothing ties these fields together.
- **Pattern:** a **discriminated union** (Phase 5) where each state carries
  *exactly* its own valid fields, and no others:

```ts
type RequestState<T> =
  | { status: "idle" }
  | { status: "loading" }
  | { status: "success"; data: T }
  | { status: "error"; message: string };

function render(state: RequestState<string[]>): string {
  switch (state.status) {
    case "idle":
      return "—";
    case "loading":
      return "Loading…";
    case "success":
      return `${state.data.length} items`;   // `data` only exists here
    case "error":
      return `Error: ${state.message}`;      // `message` only exists here
  }
}
console.assert(render({ status: "loading" }) === "Loading…", "loading state");
console.assert(render({ status: "success", data: ["a", "b"] }) === "2 items", "success state");
console.assert(render({ status: "error", message: "boom" }) === "Error: boom", "error state");
// You CANNOT construct { status: "success" } without `data` — illegal state won't compile:
// @ts-expect-error — success requires `data`
const illegal: RequestState<string[]> = { status: "success" };
```

In this example: `RequestState<T>` has four arms, and each one carries *only* the
fields that make sense for that specific state — `data` literally does not exist as
a property on the `"loading"` or `"error"` arms, so there is no way to accidentally
read stale `data` while `status` is `"loading"`. `render`'s `switch` narrows `state`
per case exactly as in Phase 5, and TypeScript won't compile `state.data` inside
the `"loading"` case at all (not "it's `undefined`" — it flags the property as not
existing on that narrowed type). The final line shows the real payoff: you cannot
even *construct* `{ status: "success" }` without also supplying `data` — the
illegal state isn't just hard to reach at runtime, it's impossible to write down in
the first place.

### Why it's useful

This pattern is *the* standard way to model UI/request state, multi-step forms, and
domain events, because it eliminates an entire category of "how did we get into
this combination of flags" bug that boolean-soup state representations are
notorious for. Combined with the exhaustive `switch` from Phase 5, the compiler
additionally guarantees every state is handled somewhere — add a new state, and
every `switch` over that union that doesn't yet handle it becomes a build error.

### Summary

- Parallel booleans/optionals allow contradictory states the type system doesn't
  catch.
- A discriminated union ties each field to exactly the state(s) it's valid in.
- Illegal combinations aren't just hard to reach — they're impossible to construct.
- Pair with exhaustive `switch` (Phase 5) so new states can't be silently unhandled.

## 7.3 — Typed error handling: `Result<T, E>` vs exceptions

Two valid error-handling strategies exist in TypeScript, and knowing the trade-off
between them — not just how to use each — is what interviewers are actually testing.

- **Exceptions** (`throw`/`try`/`catch`) are idiomatic JavaScript/TypeScript and
  well suited to truly exceptional, unrecoverable situations and cross-cutting
  failures (a network layer that fails everywhere the same way, a programmer error
  like a broken invariant). The downside: TypeScript has **no checked exceptions** —
  a function's type signature says nothing about what it might throw, so a caller
  has no compile-time way to know a `try`/`catch` is even necessary, let alone
  which errors to expect.
- **`Result<T, E>`** (the generic discriminated union from Phase 4) makes failure
  **explicit in the return type**. The caller is compelled by the compiler to check
  `.ok` before it's allowed to read `.value` — the error arm's `.value` property
  simply doesn't exist as far as the type checker is concerned until narrowed.
  Pairing it with a **discriminated `AppError`** union lets the caller `switch`
  exhaustively on `error.code`, the same exhaustiveness guarantee from 7.2 applied
  to error handling specifically.

```ts
type Result<T, E = AppError> = { ok: true; value: T } | { ok: false; error: E };

type AppError =
  | { code: "NOT_FOUND"; id: string }
  | { code: "VALIDATION"; field: string };

const users = new Map<string, UserDto>([["u1", { id: "u1", name: "Ada", age: 36 }]]);

function findUser(id: string): Result<UserDto> {
  const u = users.get(id);
  return u ? { ok: true, value: u } : { ok: false, error: { code: "NOT_FOUND", id } };
}

function errorMessage(e: AppError): string {
  switch (e.code) {
    case "NOT_FOUND":
      return `no user ${e.id}`;
    case "VALIDATION":
      return `bad ${e.field}`;
  }
}

const found = findUser("u1");
const missing = findUser("u9");
console.assert(found.ok && found.value.name === "Ada", "Result success");
console.assert(!missing.ok && errorMessage(missing.error) === "no user u9", "typed error in Result");
// Result forces the caller to handle failure (can't read .value without checking .ok):
// @ts-expect-error — `value` doesn't exist on the error arm until narrowed
missing.value;
```

In this example: `findUser` never throws — a missing user is represented as an
ordinary, typed return value, `{ ok: false, error: { code: "NOT_FOUND", id } }`.
`errorMessage` switches exhaustively on `e.code`, and because `AppError` is a
discriminated union with exactly two arms, TypeScript knows `e.id` is only valid
inside `"NOT_FOUND"` and `e.field` only inside `"VALIDATION"`. The final line
demonstrates the compiler-enforced discipline directly: `missing.value` is a
compile error, because `missing`'s type is the full `Result<UserDto>` union at that
point (not yet narrowed by checking `.ok`), and `.value` doesn't exist on the
`{ ok: false; ... }` arm at all.

- **Pragmatic split:** use `Result` for **expected, recoverable domain outcomes** at
  boundaries you control (not found, validation failure, a business rule
  violation) — these are cases callers *should* be forced to think about. Use
  exceptions for **programmer errors and unrecoverable faults** (a broken
  invariant, an out-of-memory condition, a bug) where propagating up and crashing
  loudly (or a global error boundary catching it) is the right behavior. Don't
  dogmatically pick only one strategy for an entire codebase — match the tool to the
  failure kind.

### Why it's useful

`Result` turns "did the caller remember to handle the failure path" from a code-
review-dependent convention into something the compiler actually verifies at every
call site — a meaningfully stronger guarantee than hoping every `try` has a
corresponding, correct `catch`. Exceptions remain the right default for failures
that genuinely shouldn't be part of a function's normal contract.

### Summary

- Exceptions: idiomatic, good for unrecoverable/cross-cutting failures, but
  invisible in the type signature (no checked exceptions in TS).
- `Result<T, E>`: makes failure explicit and compiler-enforced at the call site;
  pair with a discriminated `AppError` for exhaustive error handling.
- Match the strategy to the failure kind — expected/recoverable domain failures
  favor `Result`; unrecoverable/programmer errors favor exceptions.

## 7.4 — Branded (nominal) types

Structural typing (Phase 1) means two type aliases over the same primitive are
**freely interchangeable** — `type UserId = string` and `type OrderId = string` can
be swapped for each other with no compiler complaint, which is a real, easy-to-make
bug source (passing an order's id where a user's id was expected). A **branded
type** intersects a phantom, never-actually-constructed tag onto the primitive so
the two become **incompatible**, with **zero runtime cost** — the brand exists only
in the type system and is erased like everything else.

```ts
type Brand<T, B> = T & { readonly __brand: B };
type UserId = Brand<string, "UserId">;
type OrderId = Brand<string, "OrderId">;

function asUserId(s: string): UserId {
  return s as UserId;   // the ONE sanctioned assertion, at the constructor
}
function greetUser(id: UserId): string {
  return `hello ${id}`;
}

const uid = asUserId("u1");
console.assert(greetUser(uid) === "hello u1", "branded UserId works as a string");
// @ts-expect-error — a raw string is not a UserId (structural typing defeated on purpose)
greetUser("u1");
// @ts-expect-error — an OrderId is not a UserId even though both are strings
greetUser("o1" as OrderId);
```

In this example: `UserId` is `string` intersected with a phantom `{ readonly
__brand: "UserId" }` field that no real object ever actually has — no runtime value
is ever constructed with a genuine `__brand` property, it exists purely to make the
*type* `UserId` structurally distinct from plain `string` (and from `OrderId`,
which carries a different brand). `asUserId` is the **one sanctioned place** an `as`
assertion is used — a single "minting" function through which every `UserId` must
pass, keeping the unchecked assertion contained to one reviewable spot rather than
scattered wherever a string happens to be used as an id. Both `@ts-expect-error`
lines demonstrate the payoff: a raw `string` literal, and a *differently*-branded
`OrderId`, are both rejected by `greetUser`, even though at runtime they're all
just plain strings with identical representations — the distinction exists purely
at the type level, at zero runtime cost.

### Why it's useful

Branded types are the sanctioned way to simulate Java-style nominal typing exactly
where identity matters and mixing values would be a real bug: entity IDs across
different tables/aggregates, money amounts in different currencies, and
validated/normalized strings (`Email`, `NonEmptyString`, `TrimmedString`) that have
already passed some check and shouldn't be re-validated or accidentally mixed with
an unvalidated raw string. They cost nothing at runtime and require no wrapper
object or class — just a type-level tag.

### Summary

- Structural typing makes same-primitive aliases interchangeable — a real bug
  source when identity matters.
- A brand (`T & { readonly __brand: B }`) makes two same-shape types incompatible,
  entirely at the type level, with zero runtime cost.
- Mint branded values through one sanctioned constructor function — the single
  place an `as` assertion is allowed for that type.
- Use for IDs, currency-tagged money, and validated/normalized strings — anywhere
  mixing structurally-identical values would be a bug.

## 7.5 — Migrating JS to TS, and common anti-patterns

- **Migration path:** rename `.js` files to `.ts` incrementally; enable `allowJs`
  to let TS and JS coexist during the transition; add types starting at **public
  API boundaries and data models** first, since that's where types pay off fastest
  (catching a wrong argument at the call site of a widely-used function, or a
  malformed data shape flowing through the app); optionally use `// @ts-check` in
  JSDoc comments on still-`.js` files for a soft, non-committal start; turn on
  `strict` per-file (or per-directory) as each part of the codebase is actually
  ready, then tighten it globally once the bulk of the migration is done. Avoid a
  big-bang rewrite — types at the edges of the system (where data enters and public
  functions are called) give the most value per unit of migration effort.
- **Anti-patterns to actively avoid:**
  - Reflexive `any` as a default — reach for `unknown` plus a real narrow instead.
  - `as` used to silence a compiler error rather than fixing the actual type
    mismatch it's flagging.
  - `@ts-ignore`, which suppresses an error unconditionally and can linger
    indefinitely even after the underlying issue is fixed, silently masking future
    problems on that line too. Prefer **`@ts-expect-error`** (ideally with a
    trailing comment explaining why), which specifically fails the build if there
    is *no* error on the next line — so it can't silently rot the way `@ts-ignore`
    can, and ideally, just fix the type instead of suppressing it at all.
  - Over-engineered conditional/mapped-type gymnastics where a plain, explicit
    type would be just as correct and far more readable (Phase 5's closing
    guidance).
  - Treating any externally-sourced data as trusted without runtime validation
    (7.1).

### Why it's useful

Migrating boundaries-first means the highest-traffic, highest-risk parts of a
codebase get type safety earliest, which is where bugs are most likely to originate
and where a caught bug prevents the most downstream damage. `@ts-expect-error`
specifically (versus `@ts-ignore`) is a small habit with an outsized payoff: it
turns a suppressed error into a self-verifying assertion that the error still
exists, rather than a silent, permanent escape hatch.

### Summary

- Migrate incrementally with `allowJs`; type boundaries and data models first;
  tighten `strict` per-file, then globally.
- Avoid: reflexive `any`, `as`-to-silence-errors, `@ts-ignore`, untrusted external
  data, and needlessly clever type-level code.
- Prefer `@ts-expect-error` over `@ts-ignore` — it fails the build if the
  underlying error is ever actually fixed, so it can't rot silently.

## Perspective

Applied TypeScript comes down to two habits repeated everywhere they're relevant:
**validate at the edges** (types don't check runtime data — you do, at every trust
boundary) and **model so illegal states can't compile** (discriminated unions,
branded types, and `Result` all serve this one goal from different angles). Do
those two things consistently and the compiler stops being a formality that merely
catches typos and starts eliminating entire categories of bug before the code ever
runs — the same "make the right thing easy and the wrong thing impossible" goal the
05-software-design track pursues through code structure, here enforced directly by
the type system.
