<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · core apis](../phase-3-core-apis/NOTES.md) | [Phase 5 · concurrency ➡](../phase-5-concurrency/NOTES.md)
<!-- /nav -->

# Phase 4 — Modern & Functional Java: Notes

## 4.1 — Lambdas & functional interfaces

**A lambda implements a functional interface** — an interface with exactly one abstract method (SAM type). `@FunctionalInterface` makes the compiler enforce the "exactly one" (default/static methods don't count). A lambda has **no type of its own** — it adopts the *target type* from context (assignment, parameter, return); the same text can implement different interfaces.

**The big four (`java.util.function`) — know cold:**

| interface | shape | question it answers |
|---|---|---|
| `Predicate<T>` | T → boolean | does it pass? (`test`) |
| `Function<T,R>` | T → R | transform it (`apply`) |
| `Consumer<T>` | T → void | use it up (`accept`) |
| `Supplier<T>` | () → T | make one on demand (`get`) |

Variants: `BiFunction<T,U,R>`, `UnaryOperator<T>`/`BinaryOperator<T>` (same-type function), `BiConsumer`, and **primitive specializations** (`IntPredicate`, `ToIntFunction`…) that skip boxing in hot paths — which is why `IntStream` exists.

**Method references — four kinds:**
1. static — `Integer::parseInt` ≡ `s -> Integer.parseInt(s)`
2. unbound instance — `String::length` ≡ `s -> s.length()` (receiver becomes param 1)
3. bound instance — `"java"::equals` ≡ `s -> "java".equals(s)` (receiver fixed now)
4. constructor — `ArrayList::new`

Rule: lambda only calls one existing method → use the reference.

**Composition:** `f.andThen(g)` (f first), `f.compose(g)` (g first); predicates `and`/`or`/`negate`/`Predicate.not`. Small named functions snapped together beat one mega-lambda.

**Capture:** lambdas close over locals **by value** → captured locals must be *effectively final* (never reassigned) — same rule and reason as 2.7's anonymous classes. Instance fields are read through `this` and aren't so restricted. Differences from anonymous classes: no new `this` scope (lambda's `this` = enclosing instance), no shadowing, no class file per lambda (compiled via `invokedynamic`, not `Outer$1.class`).

**The unlocking idea — behavior parameterization:** methods that accept *what to do* (`pickWhere(list, predicate)`) instead of hardcoding it. Streams are this idea industrialized.

## 4.2 — Streams

**Pipeline anatomy:** source → 0+ *intermediate* ops (lazy, return Stream) → exactly 1 *terminal* op (triggers everything). Three facts explain all behavior: **lazy** (nothing runs until the terminal — proven by the print-inside-filter demo), **one-shot** (reuse → `IllegalStateException`), **non-mutating** (source untouched; results are new).

**Intermediate toolbox:** `filter` `map` `flatMap` (one→many, flattens — `nested.stream().flatMap(List::stream)`) `distinct` `sorted` `limit` `skip` `peek` (debugging only) `mapToInt/Long/Double` (→ primitive streams: no boxing, plus `sum/average/summaryStatistics`).

**Terminals:** `toList` (16+), `collect`, `count`, `forEach`, `findFirst`/`findAny`, `anyMatch`/`allMatch`/`noneMatch` (short-circuit — laziness means upstream elements aren't even examined past the hit; proven in the lesson), `min`/`max`, `reduce`.

**Collectors — the interview set:**
- `groupingBy(classifier)` → `Map<K, List<T>>`; with downstream: `groupingBy(k, counting()/averagingInt(...)/mapping(f, toList()))` — the #1 asked collector.
- `partitioningBy(predicate)` — the two-bucket special case (always both keys).
- `joining(sep, prefix, suffix)`.
- `toMap(keyFn, valFn, mergeFn)` — **duplicate keys throw `IllegalStateException` without the merge function**; classic trap.

**reduce:** fold to one value — `reduce(identity, accumulator)` or Optional-returning `reduce(accumulator)`. `sum/min/max/count` are packaged reduces; prefer them, and prefer `max(comparator)` over hand-rolled reduce for readability.

**Judgment calls:** don't mutate shared state in `forEach` (wrong model; breaks parallel) — collect instead. `parallelStream()` only after measuring: fork/join overhead loses on small/cheap workloads. Streams express *what*; loops with early exit/index math often stay clearer as loops. Word count: `groupingBy(w -> w, counting())`.

## 4.3 — Optional

**Purpose:** move "might be absent" into the signature. `Optional<User> findUser(id)` makes the empty case impossible to forget; a nullable return hides it until the NPE. It's a *return-type* tool, not a general null replacement.

**Creating:** `of` (NPEs on null — asserts presence), `ofNullable` (bridges legacy nulls), `empty`.

**Consuming spectrum:** `isPresent()+get()` = null-check with extra steps (avoid; `get` throws `NoSuchElementException` on empty). Prefer declarative: `orElse(fallback)`, `orElseGet(supplier)`, `orElseThrow(exSupplier)`, `ifPresent(action)`, `ifPresentOrElse(action, emptyAction)`.

**`orElse` vs `orElseGet` — the perf trap (demoed):** `orElse(expensive())` evaluates the fallback *always*, present or not (it's an ordinary argument); `orElseGet(() -> expensive())` runs only when empty. Cheap constant → `orElse`; anything built → `orElseGet`.

**Transforming:** `map` (empty flows through), `filter` (present→empty on fail), **`flatMap`** when the mapper itself returns Optional (else you'd nest `Optional<Optional<T>>`), `or(() -> alternativeOptional)` to chain fallback *sources* (9+).

**The rules:**
- DO return Optional from find/lookup methods; chain map/filter/flatMap; end with orElse*/ifPresent.
- DON'T take Optional *parameters* (callers forced to wrap; overload instead); avoid Optional *fields* as a habit (not Serializable, a box per access — nullable field + Optional getter is the common style); no `Optional<Collection>` (empty collection already means "none"); no bare `get()`.
- NEVER return null where Optional is declared.
- Primitive flavors `OptionalInt/Long/Double` avoid boxing — what `IntStream.max()` returns.

## 4.4 — Pattern matching (+ var, text blocks)

**Timeline:** instanceof patterns (16) → switch type patterns + guards + record patterns (21). Pattern = *test shape + bind variables* in one step.

**instanceof patterns:** `if (o instanceof List<?> list && !list.isEmpty())` — binding is *flow-scoped*: usable exactly where the compiler proves the match, including after a negated early-return (`if (!(o instanceof String s)) return; // s usable here`).

**Switch patterns:** `case Integer i when i > 40 ->` (`when` = guard); ordering narrower-before-wider and guarded-before-unguarded — unreachable case = compile error (same spirit as catch ordering). `case null` is expressible; absent it, null still NPEs. Switch over `Object` needs `default` (open-ended); switch over a **sealed** type doesn't — exhaustiveness checked (2.6's payoff).

**Record patterns:** deconstruct components positionally, nested arbitrarily: `case Line(Point(var x1, var y1), Point(var x2, var y2))`. Types in patterns can be `var`.

**The showcase — sealed + records + switch = interpreter:** the `Expr` evaluator is a complete recursive tree interpreter in six lines, exhaustive with *no default* — adding a fifth `Expr` record makes `eval` stop compiling until handled. Pre-21 this required the Visitor pattern's accept/visit ceremony. This trio is Java's answer to functional languages' algebraic data types + match.

**var recap:** locals only, inference not dynamism; style — use where the right side makes the type obvious, avoid where it hides it. **Text blocks:** `"""`, closing-delimiter position controls indent stripping; ideal for JSON/SQL/HTML in code.

## 4.5 — Java version history & the release cadence

Every feature in this phase arrived in a specific release — here's the timeline, and the release model, which is itself a common interview question.

**The release model (since Java 9, 2017):**
- A **new feature release every 6 months** (March & September): Java 9, 10, 11, 12… 21, 22…
- **LTS (Long-Term Support)** releases get years of updates and are what companies actually run in production: **8, 11, 17, 21** (and 25 next). LTS cadence moved to every **2 years** (from every 3). Non-LTS releases are stepping stones, mostly for early adopters.
- **Preview features** ship disabled-by-default (`--enable-preview`) so the community can try them before they're finalized — how records, pattern matching, and virtual threads were incubated before becoming permanent.
- This repo targets **Java 21 (LTS)** and runs on OpenJDK.

**The milestones that matter (know these cold):**

| Version (year) | Headline features |
|---|---|
| **8** (2014) *LTS* | The big functional leap: **lambdas**, the **Streams** API, **`Optional`**, functional interfaces, default methods on interfaces, the new `java.time` API. Still the baseline much legacy code assumes. |
| **9** (2017) | The **module system (JPMS / Project Jigsaw)**, `jshell` (REPL), the 6-month cadence begins, private interface methods. |
| **10** (2018) | **`var`** (local-variable type inference). |
| **11** (2018) *LTS* | First LTS after 8 — the common "modern baseline." `var` in lambda params, the new `HttpClient`, run a single `.java` file directly (`java File.java`), many API additions. Oracle licensing change pushed adoption of OpenJDK builds. |
| **14–16** (2020–21) | **Records** (14 preview → 16 final), **pattern matching for `instanceof`**, **helpful NullPointerExceptions**, **text blocks** (`"""`), `switch` expressions finalized. |
| **17** (2021) *LTS* | **Sealed classes/interfaces** finalized, records/text blocks/switch-expressions all stable — the "modern Java" LTS. A very common migration target from 8/11. |
| **21** (2023) *LTS* | **Virtual threads (Project Loom)** — massive cheap concurrency (Phase 5 territory); **pattern matching for `switch`** finalized; **record patterns** (deconstruction); sequenced collections. The current recommended LTS and this repo's target. |

**The "8 → 17/21" migration story (a frequent question):** most modern Java adoption is about jumping off Java 8. What you gain: records (kill boilerplate DTOs), sealed types + pattern-matching switch (algebraic-data-type modeling, the interpreter demo above), text blocks, `var`, a better GC default (G1, Phase 6.4), stronger APIs, and — at 21 — virtual threads. What you must watch: the **module system** (9) and stricter encapsulation of JDK internals can break old reflective libraries; removed/deprecated APIs (e.g. `finalize`); and third-party/framework version compatibility.

**Rule of thumb:** develop and deploy on the **latest LTS** (21 today), use non-LTS releases to preview what's coming, and treat "which Java version?" as "which LTS, plus what preview features do I need." Knowing the 8 → 11 → 17 → 21 feature arc — and that 8 is the functional watershed while 21 brings virtual threads — is the interview-ready summary.
