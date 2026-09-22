<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · advanced types](../phase-5-advanced-types/NOTES.md) | [Phase 7 · in practice ➡](../phase-7-in-practice/NOTES.md)
<!-- /nav -->

# Phase 6 — Async, Modules, Tooling & Config: Interview Q&A

⭐ = asked constantly.

**Q: What type does an `async` function return, and what does the annotated return
type actually refer to?** ⭐
Always `Promise<T>` at the type level — even if you never write `Promise` in the
signature, calling the function produces a Promise at runtime. The type you
annotate on an `async` function is the **resolved value**, `T`, not the Promise
itself; TypeScript automatically wraps it, so `async (): Promise<number> { return
42; }` is correct even though the body returns a bare `42`, not a
`Promise.resolve(42)`. `await` is the inverse: it unwraps `Promise<T>` back down to
`T` at the point it's used.

**Q: What is `Awaited<T>`, and why does it need to be recursive?** ⭐
It's the type-level equivalent of `await` — but unlike unwrapping a Promise once, it
recurses through **nested** Promises: `Awaited<Promise<Promise<string>>>` resolves
all the way to `string`, not `Promise<string>`. It needs to be recursive because
`await`ing a Promise that itself resolves to another Promise is automatically
flattened by the JavaScript runtime (you never end up holding a Promise-of-a-Promise
after an `await`), and `Awaited<T>` exists specifically to model that runtime
flattening behavior at the type level — a naive single-level `T extends
Promise<infer U> ? U : T` would get this wrong for nested cases.

**Q: Why is `catch (e)` typed `unknown` under `strict`?** ⭐⭐
Because JavaScript's `throw` statement can throw **any value** — not just an
`Error` instance, but a string, a plain object, `undefined`, anything — so assuming
`e: Error` by default would be an unsound assumption baked into the type system.
Under `strict` (specifically the `useUnknownInCatchVariables` flag it bundles), TS
types the catch variable as `unknown` to force you to **narrow** it — typically
`e instanceof Error` — before accessing anything like `e.message`. This closes off
a genuinely common runtime bug: code that assumes every caught value is an `Error`
and crashes (or produces a confusing "undefined is not an object" message) the one
time something throws a plain string or a rejected Promise's non-Error reason.

```ts
try { throw "boom"; }
catch (e) {
  if (e instanceof Error) console.log(e.message);
  else console.log(String(e));   // handles the non-Error case explicitly
}
```

**Q: `import type` vs a plain `import` — what's the actual difference?** ⭐
`import type` brings in **only** type information and is completely erased from the
compiled output — no runtime `import` statement is emitted at all for it. A plain
`import` (when it imports a value, or a mix of a value and a type under looser
settings) is emitted as a real runtime import. Use `import type` specifically for
imports that exist purely to reference a type (an `interface`, a `type` alias) —
this avoids pulling in runtime code you don't actually need at that call site and
helps break circular-import cycles between modules that only reference each other's
types, not each other's values. `verbatimModuleSyntax` makes this distinction an
enforced, explicit rule rather than something left to the compiler's inference.

**Q: What is a `.d.ts` file, and what does `declare` do?** ⭐
A declaration file contains **only type information — no implementation** — and
describes the shape of JavaScript code that exists somewhere else (a library's
actual `.js` files, a global provided by the runtime environment). This is the
mechanism that makes the broader, mostly-untyped JS ecosystem usable from
TypeScript: a library either ships its own `.d.ts` files, or the community
maintains them separately as an `@types/<package>` npm package (the
DefinitelyTyped project) that you install alongside the untyped library. `declare`
introduces an **ambient** declaration — telling TypeScript "trust that this value
exists at runtime" (a global variable, a module, a function) without TS itself
generating any code to create it; it's how you describe things provided by an
environment TS doesn't control, like a script-tag global or a runtime-injected
config object.

**Q: Which `tsconfig` options matter most, and would you always turn them on?**
⭐⭐
`strict: true` above everything else — it bundles `strictNullChecks` (the highest-
value one: `null`/`undefined` become real, distinct types you must explicitly
handle), `noImplicitAny`, `useUnknownInCatchVariables`, and more; always develop
with it on from the start, since retrofitting it onto a large loosely-typed
codebase later surfaces every unhandled-`null` site and every implicit-`any` at
once, which is painful in bulk. Then `noUncheckedIndexedAccess` (indexed
reads become `T | undefined`, forcing you to handle a missing key/out-of-bounds
index — high value, this track enables it). `target`/`lib` control the emitted JS
version and the assumed-available built-in APIs, independently of each other.
`module`/`moduleResolution` control how imports are emitted and resolved — this
track uses `ESNext`/`Bundler`, matching modern bundler resolution rather than
older Node-specific algorithms. `noEmit` is set when a separate fast tool (not
`tsc`) actually does the transpile.

**Q: `as` vs `satisfies` vs a plain type annotation — what's the actual difference
between all three?** ⭐⭐
A plain annotation (`const x: T = value`) both validates `value` against `T` *and*
**widens** `x`'s inferred type to exactly `T`, losing any more-specific literal
information the value had. `as T` forcibly asserts the type with **no runtime
check at all** — it can lie, and if it does, the mismatch surfaces later as a
runtime bug exactly where TS was supposed to prevent one; treat it as a smell
unless you've verified the value some other way first. `satisfies T` **validates**
the value against `T` (a real compile-time check — a mismatch is still an error)
**without widening** the value's own inferred type, so it keeps whatever more
precise, literal type the value would have had on its own. `satisfies` is usually
the best choice for config objects, palettes, and literal maps — you get both the
structural guarantee and the precise downstream type in one operator.

```ts
const palette = {
  primary: [255, 0, 0],
} satisfies Record<string, [number, number, number]>;
// palette.primary is still the 3-tuple [number, number, number], not widened
```

*Follow-up: what would `palette.primary`'s type be if you used a plain annotation
(`const palette: Record<string, [number, number, number]> = {...}`) instead?* It
would widen to the annotation's index signature value type — effectively just
`[number, number, number]` accessed through an index signature, losing the
specific knowledge that `primary` in particular exists as a named key at all;
you'd lose the ability to autocomplete `palette.primary` as a known property versus
any other string key. `satisfies` avoids that loss entirely.

**Q: What does a type assertion actually do at runtime?** *nuance*
Nothing — it's completely erased at compile time, exactly like every other type
annotation. `as` only changes what the *compiler* believes about a value's static
type from that point forward; it is **not** a conversion or a cast in the runtime
sense (unlike, say, a numeric cast in a language like C or Java's primitive casts,
which can actually change bits). If the asserted type doesn't match reality, no
error occurs at the assertion itself — the mismatch surfaces later, at whatever
point code actually relies on the (incorrect) assumed shape, typically as a
confusing runtime `TypeError`. This is exactly why assertions should only follow
real validation, not replace it.

**Q: When is the non-null assertion `!` acceptable?**
When you can prove — from context the compiler genuinely cannot follow — that a
value isn't `null`/`undefined` at that point, and a real narrow (`if (x)`,
optional chaining, a default via `??`) isn't practical or is more awkward than the
situation warrants. A common case is indexing into an array right after checking
`.length`, which `noUncheckedIndexedAccess` still types as possibly-`undefined`
because the compiler doesn't correlate a length check with a specific index's
presence. Prefer a real narrow whenever one is reasonably available; reserve `!`
for the rare, specifically justified case, since — like `as` — it inserts zero
runtime verification.

**Q: How do fast runners (tsx/esbuild/swc) relate to `tsc`, and why do teams run
both?** *nuance*
Fast runners **strip** type annotations for speed and do **not** perform full type
checking — they will happily execute code that has real type errors in it, because
their whole value proposition is speed, achieved by skipping the expensive
type-checking pass entirely. `tsc --noEmit` is the tool that actually performs
type checking; it produces no output, its only job is to report errors. The modern
standard pipeline pairs a fast runner/bundler for execution (fast local iteration,
fast production bundling) with `tsc --noEmit` run separately — in CI, in an editor's
language server, or as this track's `npm run typecheck` script — for the actual
safety guarantee. Skipping the `tsc --noEmit` step and relying only on a fast
runner means type errors can silently ship to production.
