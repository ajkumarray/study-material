<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · core apis](../phase-3-core-apis/NOTES.md) | [Phase 5 · concurrency ➡](../phase-5-concurrency/NOTES.md)
<!-- /nav -->

# Phase 4 — Modern & Functional Java: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 4.1 — Lambdas & functional interfaces

**Q: What is a functional interface?** ⭐
An interface with exactly one abstract method (SAM) — the type a lambda implements. `default`/`static` methods don't count against the one. `@FunctionalInterface` makes the compiler enforce it. Examples: `Runnable`, `Comparator`, `Callable`, the `java.util.function` family.

**Q: Name the four core functional interfaces.** ⭐⭐
`Predicate<T>` (T→boolean, `test`), `Function<T,R>` (T→R, `apply`), `Consumer<T>` (T→void, `accept`), `Supplier<T>` (()→T, `get`). Interviewers expect the method names too.
*Follow-up: primitive specializations (`IntPredicate`, `ToIntFunction`) exist to avoid autoboxing — and are why `IntStream` is separate.*

**Q: Lambda vs anonymous class?** ⭐
Differences that matter: (1) `this` — in a lambda it's the *enclosing instance*; an anonymous class gets its own. (2) Compilation — lambdas use `invokedynamic`, no `Outer$1.class` per site, less overhead. (3) Lambdas fit only SAM types; anonymous classes can extend classes/implement multi-method interfaces. (4) No variable shadowing inside lambdas.

**Q: What does "effectively final" mean and why is it required?** ⭐
A local captured by a lambda must never be reassigned (even without the `final` keyword). The lambda captures a *copy* of the value; permitting reassignment would let copy and variable silently diverge (and locals live on a stack frame that may be gone when the lambda runs). Workarounds smell — usually the code wants a stream/collector instead of a mutated counter.

**Q: The four kinds of method references?**
Static (`Integer::parseInt`); unbound instance (`String::length` — receiver becomes the first argument); bound instance (`"java"::equals` — receiver captured now); constructor (`ArrayList::new`). If a lambda just calls one method, prefer the reference.

**Q: Can a lambda throw a checked exception?**
Only if the functional interface's method declares it (`Callable.call` does; `Function.apply` doesn't) — which is why checked exceptions inside stream lambdas must be caught and wrapped (`UncheckedIOException`, 3.4). A standard interview segue into the checked-vs-unchecked debate.

## 4.2 — Streams

**Q: What is a stream? How does it differ from a collection?** ⭐
A collection stores data; a stream describes a *computation over* data — a lazy pipeline (source → intermediate ops → one terminal op) that's one-shot and doesn't mutate its source. Streams are the "what", loops the "how".

**Q: Intermediate vs terminal operations?** ⭐⭐
Intermediate (filter/map/sorted/…) return a Stream and are **lazy** — literally nothing executes until a terminal op (collect/count/forEach/findFirst/…) pulls the pipeline. Benefits of laziness: fused single pass (no intermediate collections) and short-circuiting (`findFirst`/`anyMatch`/`limit` stop consuming as soon as the answer is known).
*Follow-up: "Prove laziness" — side-effecting print in `filter`, no terminal: nothing prints.*

**Q: map vs flatMap?** ⭐⭐
`map`: one→one transform. `flatMap`: one→many — mapper returns a Stream and results are flattened (`List<List<T>>` → `Stream<T>`, sentence → words). Same question reappears with Optional and in every reactive framework — the concept transfers.

**Q: What does `groupingBy` return, and what's a downstream collector?** ⭐
`groupingBy(Employee::team)` → `Map<K, List<T>>`. The second argument transforms each group: `counting()`, `averagingInt(...)`, `mapping(Employee::name, toList())`, even nested `groupingBy` — SQL GROUP BY in one expression. `partitioningBy(pred)` is the boolean special case (always exactly two keys).

**Q: The `toMap` duplicate-key trap?**
`Collectors.toMap(keyFn, valFn)` throws `IllegalStateException` on the first duplicate key. Fix: three-arg form with a merge function (`Integer::max`, `(a, b) -> b`…). Interviewers plant this deliberately.

**Q: What is `reduce`?**
Fold a stream to one value via an associative accumulator: `reduce(0, Integer::sum)`; the identity-less form returns Optional. `sum/min/max/count` are prepackaged reduces — prefer them (and `max(comparator)` over hand-rolled ternary reduces) for intent.

**Q: When should you NOT use parallel streams?** ⭐ *senior probe*
Small data or cheap per-element work (fork/join overhead dominates); stateful/order-dependent lambdas; shared mutable state (races); I/O-bound work in the common pool (starves other parallel streams); inside a server already handling concurrent requests. Honest answer: default sequential, go parallel only after measuring.

**Q: Why can't you reuse a stream?**
Terminal ops consume the pipeline — reuse throws `IllegalStateException`. Streams model data *in motion*; re-derive a fresh stream from the source (cheap — laziness means construction costs nothing).

## 4.3 — Optional

**Q: What problem does Optional solve?** ⭐
It puts "may be absent" into the method's *type*, forcing callers to handle emptiness — versus null returns, which are invisible in signatures and explode later as NPEs far from the cause. Designed specifically for return values of lookup-style methods.

**Q: `orElse` vs `orElseGet`?** ⭐⭐ *the Optional trap question*
`orElse(buildDefault())` evaluates its argument **always** — even when the Optional is present (ordinary argument evaluation). `orElseGet(() -> buildDefault())` invokes the supplier **only when empty**. Constant fallback → orElse; constructed/expensive fallback → orElseGet.

**Q: map vs flatMap on Optional?**
`map` wraps the mapper's result; if the mapper *already returns Optional*, map yields `Optional<Optional<T>>` — `flatMap` flattens. Mirrors the streams answer; saying "same monadic idea" earns points.

**Q: Where should Optional NOT be used?** ⭐
Parameters (forces callers to wrap — overload instead); fields as a habit (not Serializable, extra indirection; nullable field + Optional getter is the accepted style); `Optional<Collection>` (empty collection already says none); bare `get()` (throws `NoSuchElementException`; if you must assert, `orElseThrow()` says it explicitly). And never return null from an Optional-typed method.

**Q: Is Optional just a fancy null check?**
The chaining is the difference: `findUser(id).map(User::email).filter(e -> e.endsWith(".io")).orElse("-")` — each step handles absence implicitly; the null-check version nests three ifs. Plus `or()` chains alternative sources. It converts control flow into data flow.

## 4.4 — Pattern matching & modern syntax

**Q: What is pattern matching for instanceof?** ⭐
Test + cast + bind in one: `if (o instanceof String s)` — `s` is flow-scoped to where the match is proven (including after negated early returns). Kills the test-cast-typo class of bugs.

**Q: What arrived in switch in Java 21?** ⭐
Type patterns (`case String s ->`), guards (`case Integer i when i > 40 ->`), record deconstruction patterns (`case Point(int x, int y) ->`), `case null`. Ordering rules mirror catch blocks — narrower/guarded first; unreachable = compile error. Over sealed types: exhaustive without default.

**Q: Why do sealed + records + switch matter together?** ⭐ *modern-Java signal*
They form algebraic data types with compiler-checked matching: model "exactly these variants" (sealed), each variant pure data (records), handle by shape (exhaustive switch). The lesson's 6-line `eval` interpreter replaces the Visitor pattern's ~50 lines of accept/visit ceremony — and adding a variant *breaks the build* at every unhandled switch, instead of failing at runtime.

**Q: What does a record pattern do?**
Deconstructs by component, positionally, nested: `case Line(Point(var x1, var y1), Point(var x2, var y2))`. Combines type test + component extraction; `var` allowed per component.

**Q: When should you avoid `var`?**
When the right side doesn't reveal the type (`var result = svc.process()`), in long methods where declarations drift far from use, or when the inferred type is surprisingly specific/anonymous. Use it where the constructor/literal makes the type obvious. It's readability judgment, not dogma — having an opinion is the point.

**Q: What is an LTS release and which versions are LTS?** ⭐⭐
LTS (Long-Term Support) releases get years of updates and are what you run in production; feature releases ship every 6 months but LTS lands every ~2 years. The LTS line is **8, 11, 17, 21** (25 next). Non-LTS releases (12–16, 18–20…) are stepping stones for early adopters. Rule of thumb: develop and deploy on the latest LTS.

**Q: What were the headline features of Java 8?** ⭐⭐
The functional watershed: **lambdas**, the **Streams** API, **`Optional`**, functional interfaces, interface default methods, and the new `java.time` API. It's the baseline much legacy code still assumes, which is why "migrating off Java 8" is such a common theme.

**Q: What's new between Java 8 and 17/21?** ⭐⭐
9: the **module system** + `jshell` and the 6-month cadence. 10/11: **`var`**, new `HttpClient`. 14–16: **records**, `instanceof` pattern matching, **text blocks**, switch expressions. 17: **sealed classes** (an LTS consolidating all of the above). 21: **virtual threads (Loom)**, **pattern matching for `switch`**, record patterns. So 8→21 adds records, sealed+pattern-matching, text blocks, `var`, and cheap concurrency.

**Q: What's the biggest feature in Java 21?** ⭐
**Virtual threads (Project Loom)** — extremely lightweight threads that make blocking, thread-per-request code scale to millions of concurrent tasks without the complexity of async/reactive. Plus pattern matching for `switch` and record patterns are finalized. It's the current recommended LTS.

**Q: What can break when upgrading from Java 8?** *nuance*
Mainly the **module system's stronger encapsulation** (9+) blocking reflective access to JDK internals (breaking older libraries like some bytecode/serialization tools), **removed/deprecated APIs** (`finalize`, older GC flags, `javax.*` pieces), and framework/dependency version compatibility. The language is largely backward-compatible; the friction is usually libraries and JDK-internal reflection, so upgrade dependencies alongside the JDK.

**Q: Why the 6-month release cadence and preview features?** *nuance*
Faster, smaller, more predictable releases (vs the old multi-year mega-releases) let features land incrementally. **Preview features** (`--enable-preview`) ship finalized-but-opt-in so the community can use and give feedback before they're permanent — records, pattern matching, and virtual threads all went through preview. Production teams track LTS releases and enable previews only deliberately.
