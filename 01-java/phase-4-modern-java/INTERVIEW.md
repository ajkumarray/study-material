<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · core apis](../phase-3-core-apis/NOTES.md) | [Phase 5 · concurrency ➡](../phase-5-concurrency/NOTES.md)
<!-- /nav -->

# Phase 4 — Modern & Functional Java: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 4.1 — Lambdas & Functional Interfaces

**Q: What is a functional interface?** ⭐

An interface with exactly one abstract method — a "SAM type" (Single Abstract Method) — and specifically the kind of type a lambda expression can implement. `default` and `static` methods on the interface don't count toward that limit, so an interface can have many concrete helper methods and still be a valid functional interface as long as exactly one method remains abstract. `@FunctionalInterface` is an optional annotation that makes the compiler actively *enforce* this — adding a second abstract method to an annotated interface becomes a compile error rather than a silent design mistake. Common examples: `Runnable`, `Comparator`, `Callable`, and the whole `java.util.function` package.

**Q: Name the four core functional interfaces and their methods.** ⭐⭐

`Predicate<T>` (`T → boolean`, method `test`), `Function<T,R>` (`T → R`, method `apply`), `Consumer<T>` (`T → void`, method `accept`), `Supplier<T>` (`() → T`, method `get`). Interviewers commonly expect the exact method names, not just the shapes, since knowing them signals you've actually used these types rather than just read about them.

*Follow-up: "Why do primitive specializations like `IntPredicate` and `ToIntFunction` exist?"* To avoid autoboxing overhead. `Predicate<Integer>` would box every `int` into an `Integer` to satisfy the generic type parameter; `IntPredicate` operates on a raw `int` directly. This is exactly why `IntStream`/`LongStream`/`DoubleStream` exist as separate types alongside `Stream<T>` — they carry primitive-specialized functional interfaces through the whole pipeline.

**Q: Lambda vs. anonymous class — what actually differs beyond syntax?** ⭐

Several real differences, not just brevity: (1) **`this` binding** — inside a lambda, `this` refers to the *enclosing instance*; an anonymous class gets its own distinct `this`. (2) **Compilation strategy** — lambdas compile via `invokedynamic` with no separate `.class` file generated per lambda site, while every anonymous class produces its own `Outer$1.class`-style file, adding class-loading overhead. (3) **Applicability** — a lambda can only implement a SAM (functional interface) type; an anonymous class can extend a concrete class or implement an interface with multiple abstract methods. (4) **Scoping** — lambdas introduce no new block scope for variable names and therefore can't shadow a variable from the enclosing scope, while an anonymous class body can.

**Q: What does "effectively final" mean, and why does Java require it for captured variables?** ⭐

A local variable captured by a lambda (or an anonymous class) must never be reassigned after its initial assignment — even without the explicit `final` keyword, the compiler infers and enforces this. The reason: a lambda captures a **copy** of the variable's value at the point of capture, not a live reference into the enclosing method's stack frame. If reassignment were allowed, the lambda's captured copy and the "live" variable could silently diverge — and worse, by the time a lambda actually executes (especially if it escaped the method, e.g. as a returned value or a stored callback), the original stack frame the local lived on may no longer even exist. Code that wants this kind of workaround (an incrementing counter closed over by a lambda) almost always actually wants a stream-based reduction or an explicit mutable holder object instead.

**Q: What are the four kinds of method references?**

Static (`Integer::parseInt`, equivalent to `s -> Integer.parseInt(s)`); unbound instance (`String::length`, equivalent to `s -> s.length()` — the receiver becomes the method reference's first parameter); bound instance (`"java"::equals`, equivalent to `s -> "java".equals(s)` — the receiver is already fixed at the point the reference is created); and constructor (`ArrayList::new`, equivalent to `() -> new ArrayList<>()`). Rule of thumb: whenever a lambda's entire body is just a call to one existing method, prefer the method reference — it names the intent directly.

**Q: Can a lambda throw a checked exception?**

Only if the functional interface's abstract method itself declares it in its signature. `Callable<V>.call()` declares `throws Exception`, so a lambda implementing `Callable` can throw checked exceptions freely; `Function<T,R>.apply()` declares no checked exceptions, so a lambda implementing `Function` cannot throw one without wrapping it first. This is exactly why checked exceptions inside stream lambdas (Phase 3's `Files.size` example) must be caught locally and rethrown wrapped, typically as `UncheckedIOException` — a natural segue into the broader checked-vs-unchecked design debate.

## 4.2 — Streams

**Q: What is a stream, and how does it differ from a collection?** ⭐

A collection (`List`, `Set`, `Map`) *stores* data in memory. A stream *describes a computation over* data — a lazy pipeline consisting of a source, intermediate operations, and one terminal operation — and it is inherently one-shot and non-mutating: it never changes its source and can't be consumed twice. The short version: collections are the "what," streams are the "how to process it," and loops are "how to process it, spelled out manually, step by step."

**Q: Intermediate vs. terminal operations — what's the contract between them?** ⭐⭐

Intermediate operations (`filter`, `map`, `sorted`, `distinct`, …) return another `Stream` and are entirely **lazy** — none of them do any actual work when called; they just extend the pipeline's description. A **terminal** operation (`collect`, `count`, `forEach`, `findFirst`, `reduce`, …) is what actually triggers execution, pulling elements through every intermediate step in a single pass. This laziness buys two real benefits: a **fused single pass** (no intermediate collections materialize between stages) and **short-circuiting** (`findFirst`/`anyMatch`/`limit` can stop pulling elements the moment the answer is known, without processing the rest of the source).

*Follow-up: "How would you prove a pipeline is actually lazy, rather than just taking it on faith?"* Put a side-effecting `System.out.println` inside a `filter`'s predicate, build the pipeline, and print something *before* calling any terminal operation. Nothing from inside the `filter` prints until a terminal operation like `.count()` is finally called — direct, observable proof.

**Q: `map` vs. `flatMap` — what's the difference, and where else does this same distinction show up?** ⭐⭐

`map` is a one-to-one transform: each input element becomes exactly one output element. `flatMap` is one-to-*many*: the mapping function itself returns a `Stream`, and `flatMap` flattens all of those inner streams into a single, flat output stream — the standard way to turn `List<List<T>>` into `Stream<T>`, or a stream of sentences into a stream of their individual words. This exact `map`/`flatMap` distinction reappears identically with `Optional` (4.3) and in essentially every reactive-programming framework (Reactor, RxJava) — recognizing it as one recurring concept, not three separate ones, is worth stating explicitly in an interview.

**Q: What does `Collectors.groupingBy` return, and what's a "downstream collector"?** ⭐

`groupingBy(Employee::team)` alone returns `Map<K, List<T>>` — every element grouped by key, collected into a list per group. Passing a second argument — a **downstream collector** — lets you aggregate each group instead of just collecting its raw elements: `counting()` (a count per group), `averagingInt(...)` (an average per group), `mapping(Employee::name, toList())` (transform then collect each group), or even a nested `groupingBy` call for multi-level grouping. It's essentially SQL's `GROUP BY` expressed as one Java expression. `partitioningBy(predicate)` is the specialized boolean case — the result always has exactly two keys, `true` and `false`, even if one bucket ends up empty.

**Q: Describe the `toMap` duplicate-key trap.**

`Collectors.toMap(keyFn, valueFn)` — the two-argument form — throws `IllegalStateException` the instant it encounters a second element that maps to a key already seen. The fix is the three-argument overload, `toMap(keyFn, valueFn, mergeFn)`, where the merge function decides what happens on collision — `Integer::max` to keep the larger value, `(existing, incoming) -> incoming` to overwrite, etc. Interviewers plant this specifically to see whether a candidate reaches for `toMap` reflexively without considering duplicate keys.

**Q: What is `reduce`, and when should you avoid writing one by hand?**

`reduce` folds an entire stream down to a single value using an associative accumulator function. The form with an identity value, `reduce(identity, accumulator)`, always returns a plain value; the identity-less form, `reduce(accumulator)`, returns an `Optional<T>` since there's no sensible result to hand back for an empty stream. In practice, prefer the JDK's already-"prepackaged" reduces wherever they fit — `sum()`, `min()`, `max()`, `count()` — and prefer `.max(Comparator.comparingInt(...))` over hand-writing an equivalent `reduce` with a ternary, since the named method states intent more directly than a general-purpose fold.

**Q: When should you *not* use parallel streams?** ⭐ *senior probe*

Several concrete situations: small data sets or cheap per-element work, where fork/join coordination overhead dominates and outweighs any parallelism gained; stateful or order-dependent lambdas, which can produce wrong (nondeterministic) results under parallel execution; shared mutable state accessed from the lambda, which introduces real data races; I/O-bound work run via the common `ForkJoinPool` (the default pool `.parallelStream()` uses), which can starve *other* unrelated parallel streams or `CompletableFuture` work sharing that same pool; and inside a server that's already handling many concurrent requests, where adding another layer of internal parallelism usually just adds contention. The honest, defensible answer: default to sequential streams, and only reach for `.parallelStream()` after actually measuring a real bottleneck.

**Q: Why can't you reuse a stream after a terminal operation has run?**

Because a terminal operation genuinely **consumes** the pipeline — internally, the stream's state transitions to a "closed" state once a terminal op completes, and any further operation on it throws `IllegalStateException`. Conceptually, a stream models "data in motion" through a one-time pipeline, not a persistent, reusable container like a `List`. The fix is trivial though: since building a stream (and its intermediate operations) is itself cheap and does no real work until a terminal op runs, just derive a fresh stream from the original source whenever you need to run the "computation" again.

## 4.3 — Optional

**Q: What problem does `Optional` actually solve?** ⭐

It moves "this value might be absent" from an invisible runtime possibility into an explicit part of the method's **type signature** — `Optional<User> findUser(id)` forces every caller to consciously handle the empty case (via `map`/`orElse`/`ifPresent`/etc.) before they can even get at the value, whereas a plain `User findUser(id)` that sometimes returns `null` gives callers no compile-time signal at all, and the eventual `NullPointerException` typically surfaces far away from — and long after — the actual root cause. It's designed specifically for return values of lookup/find-style methods, not as a blanket replacement for `null` everywhere in a codebase.

**Q: `orElse` vs. `orElseGet` — walk through the difference precisely.** ⭐⭐ *the classic Optional trap question*

`orElse(fallbackValue)` takes an already-computed value as a plain method argument — and Java always evaluates method arguments before the call executes — so whatever expression you pass to `orElse` runs **unconditionally**, whether the `Optional` turns out to be present or empty. `orElseGet(supplier)` instead takes a `Supplier<T>`, and only actually invokes it **lazily, and only on the empty path**.

```java
String v1 = present.orElse(buildDefault("orElse"));             // buildDefault() ALWAYS runs
String v2 = present.orElseGet(() -> buildDefault("orElseGet"));  // buildDefault() only runs if empty
```

Rule of thumb: a cheap, already-available constant fallback (`orElse("guest")`) is fine with either method. Anything that does real work to construct — a database call, a new object allocation, string building — must use `orElseGet` specifically to avoid unconditionally paying for work whose result then gets thrown away when the `Optional` was actually present.

**Q: `map` vs. `flatMap` on `Optional` — same question as Streams?**

Exactly the same underlying idea. `map` wraps whatever its function returns inside a new `Optional`; if that function *itself* already returns an `Optional<T>`, plain `map` would produce `Optional<Optional<T>>` — a doubly-wrapped, awkward result. `flatMap` flattens that extra layer away, which is essential when chaining together multiple lookup-style calls that each already return `Optional`. Explicitly saying "it's the same monadic flattening idea as `Stream.flatMap`" is a strong signal in an interview that you understand the underlying concept rather than having separately memorized two unrelated method names.

**Q: Where should `Optional` *not* be used?** ⭐

Four places, specifically: as a method **parameter** (it forces every caller to wrap a plain value just to invoke your method — use overloading instead); as a class **field**, as a general habit (`Optional` isn't `Serializable`, and it adds an extra object allocation on every access; a plain nullable field paired with an `Optional`-returning getter is the more accepted, lighter-weight style); a bare `Optional.get()` call (it throws `NoSuchElementException` on an empty `Optional` with no useful message — if you must assert presence, `orElseThrow()` at least states that assertion explicitly); and `Optional<Collection<T>>` (an already-empty collection *already* communicates "nothing here" — wrapping it in another `Optional` is redundant double-encoding of the same absence). And, unconditionally: never return `null` from a method whose declared return type is `Optional<T>` — doing so completely defeats the type's entire purpose and silently reintroduces the exact bug class it was created to eliminate.

**Q: Isn't `Optional` just a fancier null check?**

The real value is in the *chaining*. Compare:

```java
findUser(id).map(User::email).filter(e -> e.endsWith(".io")).orElse("-");
```

against the null-check equivalent, which needs three nested `if` statements, each one handling a different potential absence point along the way. Each `Optional` operation implicitly handles the "what if this step is empty" case for you, letting the chain read as a straight-line description of the happy path while absence-handling happens automatically underneath. `Optional` also supports `or(() -> alternativeSource)` (Java 9+) to chain fallback *sources* lazily — the second lookup only runs if the first came back empty. In short: it converts absence-handling from control flow into data flow.

## 4.4 — Pattern Matching & Modern Syntax

**Q: What is pattern matching for `instanceof`?** ⭐

It combines a type test, a cast, and a variable binding into one expression: `if (o instanceof String s)` tests whether `o` is a `String`, and if so, binds it to a new variable `s` — no separate explicit cast line needed. The bound variable is **flow-scoped**: usable exactly where the compiler can prove the match held, which includes the "inverted" case of a negated check followed by an early return (`if (!(o instanceof String s)) return; // s is usable from here on`). This eliminates the classic three-line "test, then cast, then use" pattern, along with the risk of a copy-paste typo between the test type and the cast type.

**Q: What arrived in `switch` specifically in Java 21?** ⭐

Type patterns (`case String s ->`), guards via the `when` keyword (`case Integer i when i > 40 ->`), record deconstruction patterns (`case Point(int x, int y) ->`), and the ability to explicitly write `case null ->`. Ordering rules directly mirror `catch` block ordering from Phase 3: narrower and guarded cases must come before broader/unguarded ones, and an unreachable case (one already fully covered by an earlier case) is a compile error. Switching over a `sealed` type's permitted subtypes is checked for **exhaustiveness** and needs no `default` branch at all; switching over an open type like `Object` still requires one, since the compiler can never prove every possible input type has been covered.

**Q: Why do sealed types, records, and pattern-matching switch matter together, as a trio?** ⭐ *modern-Java signal*

Because together they form Java's native version of algebraic data types with compiler-enforced exhaustive matching — a capability long associated with functional languages like Haskell or Scala. `sealed` declares "exactly these variants exist and no others may be added outside this file"; `record` makes each variant a pure, immutable data carrier with structural equality for free; and a pattern-matching `switch` handles every variant by shape, with the compiler statically proving every case is covered. The practical payoff, demonstrated by a small expression-tree interpreter, is dramatic: a six-line recursive `eval` method replaces what the Visitor design pattern would need roughly 50 lines of `accept`/`visit` double-dispatch ceremony to achieve — and, critically, adding a new variant to the sealed hierarchy later **breaks the build** at every switch statement that doesn't yet handle it, instead of silently compiling and failing (or worse, doing the wrong thing) at runtime.

**Q: What does a record pattern actually do, and can it nest?**

It deconstructs a record's components positionally in one step, combining a type test with component extraction: `case Point(int x, int y) ->` both confirms the value is a `Point` and binds `x`/`y` to its components directly, without a separate accessor call for each one. Record patterns nest arbitrarily: `case Line(Point(var x1, var y1), Point(var x2, var y2)) ->` deconstructs a `Line`'s two `Point` components in the same expression that deconstructs each `Point` itself. Components inside a pattern can be typed explicitly or written as `var`.

**Q: When should you avoid using `var`?**

When the right-hand side of the declaration doesn't make the type obvious on its own (`var result = svc.process();` — process what, producing what?), in long methods where the declaration and its actual usage drift far apart in the code, or when the inferred type turns out to be surprisingly specific or effectively anonymous (some builder/stream-chain return types are genuinely awkward to reason about once inferred). Use it comfortably where the right-hand side — a constructor call or literal — already states the type clearly (`var users = new ArrayList<User>();`). This is a readability judgment call rather than a hard rule, and having a clear, consistent, defensible opinion about when to reach for it is itself part of the answer interviewers are listening for.

## 4.5 — Java Version History

**Q: What is an LTS release, and which versions are LTS?** ⭐⭐

LTS (Long-Term Support) releases get years of ongoing patches and security updates and are what production teams actually deploy; the JDK ships a new feature release every 6 months regardless, but the LTS designation specifically lands roughly every 2 years. The LTS line so far is **8, 11, 17, 21** (with 25 next). Every non-LTS release in between (12–16, 18–20, 22–24, …) is essentially a stepping stone, mostly relevant to early adopters and library authors validating forward compatibility rather than to production deployments. Rule of thumb for "which Java version should we target?": develop and deploy on the latest LTS.

**Q: What were the headline features of Java 8?** ⭐⭐

The functional programming watershed for the language: **lambdas**, the **Streams API**, **`Optional`**, functional interfaces as a first-class concept, `default` methods on interfaces (allowing interface evolution without breaking implementers), and the completely new `java.time` API replacing the old, notoriously broken `Date`/`Calendar` classes. Java 8 remained the default baseline assumption for a huge amount of production Java code for years, which is exactly why "migrating off Java 8" is such a recurring, practical interview topic.

**Q: Summarize what changed between Java 8 and Java 17/21.** ⭐⭐

Java 9 brought the **module system** (JPMS) plus `jshell` and kicked off the 6-month release cadence. 10 and 11 brought **`var`** and the new standard `HttpClient`. 14 through 16 brought **records**, `instanceof` pattern matching, **text blocks**, and finalized switch expressions. 17 finalized **sealed classes/interfaces**, consolidating everything above into one stable, well-regarded LTS release. 21 brought **virtual threads** (Project Loom), finalized **pattern matching for `switch`**, and **record patterns**. Net effect across the whole arc: records eliminate DTO/value-class boilerplate, sealed types plus pattern-matching switch enable algebraic-data-type-style modeling with compiler-checked exhaustiveness, text blocks and `var` reduce syntactic noise, and — specifically at 21 — virtual threads unlock dramatically cheaper concurrency.

**Q: What's the single biggest feature in Java 21?** ⭐

**Virtual threads** (Project Loom) — extremely lightweight, JVM-managed threads that let ordinary blocking, thread-per-request-style code scale to handle millions of concurrent tasks, without needing to rewrite everything into asynchronous or fully reactive style. Alongside that, pattern matching for `switch` and record patterns were finalized in the same release, making 21 a particularly feature-dense LTS — it's the currently recommended LTS target for new Java projects.

**Q: What tends to actually break when upgrading a codebase from Java 8 to a modern LTS?** *nuance*

Mostly not the core language itself, which stays largely backward-compatible. The real friction points are: the **module system**'s stronger encapsulation (introduced in 9), which can block reflective access into JDK internals that some older libraries — particularly bytecode manipulation or legacy serialization tools — relied on; genuinely **removed or deprecated APIs** (`finalize()`, some older garbage-collector flags, a handful of `javax.*` pieces that moved out of the JDK entirely); and general **framework/dependency version compatibility**, since libraries themselves need their own updates to run cleanly on newer JDKs. The practical guidance: plan to upgrade key dependencies alongside the JDK version bump, not the JDK in isolation.

**Q: Why did Java move to a 6-month release cadence, and what are preview features for?** *nuance*

The 6-month cadence trades the old model of infrequent, enormous, unpredictable multi-year releases for faster, smaller, more predictable ones — features land incrementally rather than all bottlenecking behind one mega-release. **Preview features**, gated behind `--enable-preview`, ship in a finalized-but-opt-in state specifically so the broader community can use them in real code and give feedback before the feature becomes a permanent, unremovable part of the language. Records, pattern matching, and virtual threads all went through one or more preview cycles — sometimes changing based on that feedback — before being finalized. Production teams generally track LTS releases for their baseline and enable preview features only deliberately, on a case-by-case basis, understanding they could still change before finalization.
