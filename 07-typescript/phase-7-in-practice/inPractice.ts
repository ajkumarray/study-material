/**
 * Phase 7 — TypeScript in practice.
 *
 * Run:        npx tsx phase-7-in-practice/inPractice.ts
 * Type-check: npm run typecheck
 *
 * Real-world patterns: validating the untyped boundary, making illegal states
 * unrepresentable, typed error handling, and branded types.
 */

// ---------------------------------------------------------------------------
// 7.1 — Typing an API boundary: parse `unknown`, don't trust it.
// ---------------------------------------------------------------------------

// Data from the network is `unknown`. A type annotation is a PROMISE, not a check —
// so we validate at the boundary and only then treat the value as its type. This is
// a hand-rolled validator (in real code you'd use Zod/valibot, which infer the type).
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
  const raw: unknown = JSON.parse(json);
  return isUserDto(raw) ? raw : null; // narrowed to UserDto in the true branch
}

console.assert(parseUser('{"id":"u1","name":"Ada","age":36}')?.name === "Ada", "valid DTO parses");
console.assert(parseUser('{"id":"u1","name":"Ada"}') === null, "missing field rejected");
console.assert(parseUser('{"id":1,"name":"Ada","age":36}') === null, "wrong type rejected");

// ---------------------------------------------------------------------------
// 7.2 — Make illegal states unrepresentable (model with the type system).
// ---------------------------------------------------------------------------

// BAD (don't do this): booleans that allow contradictory combinations —
//   interface State { loading: boolean; data?: Data; error?: string }
// permits { loading: true, data, error } — a nonsense state the type allows.

// GOOD: a discriminated union where each state carries EXACTLY its valid fields.
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
      return `${state.data.length} items`; // `data` only exists here
    case "error":
      return `Error: ${state.message}`; // `message` only exists here
  }
}
console.assert(render({ status: "loading" }) === "Loading…", "loading state");
console.assert(render({ status: "success", data: ["a", "b"] }) === "2 items", "success state");
console.assert(render({ status: "error", message: "boom" }) === "Error: boom", "error state");
// You CANNOT construct { status: "success" } without `data` — illegal state won't compile:
// @ts-expect-error — success requires `data`
const illegal: RequestState<string[]> = { status: "success" };
void illegal;

// ---------------------------------------------------------------------------
// 7.3 — Typed error handling: Result vs exceptions.
// ---------------------------------------------------------------------------

type Result<T, E = AppError> = { ok: true; value: T } | { ok: false; error: E };

// Typed, discriminated domain errors — the caller can switch on `code` exhaustively.
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

// ---------------------------------------------------------------------------
// 7.4 — Branded types: simulate nominal typing for identity safety.
// ---------------------------------------------------------------------------

// Structural typing means a UserId and an OrderId (both strings) are interchangeable
// — a real source of bugs. A "brand" adds a phantom tag so they're incompatible,
// without any runtime cost.
type Brand<T, B> = T & { readonly __brand: B };
type UserId = Brand<string, "UserId">;
type OrderId = Brand<string, "OrderId">;

function asUserId(s: string): UserId {
  return s as UserId; // the ONE sanctioned assertion, at the constructor
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

console.log("Phase 7: all in-practice assertions passed.");

export {}; // make this file a module (isolates top-level declarations per phase)
