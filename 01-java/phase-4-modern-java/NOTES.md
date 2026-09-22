<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 3 · core apis](../phase-3-core-apis/NOTES.md) | [Phase 5 · concurrency ➡](../phase-5-concurrency/NOTES.md)
<!-- /nav -->

# Phase 4 — Modern & Functional Java: Notes

*Source: `lesson-4-1` (`Lambdas.java`) through `lesson-4-4` (`PatternMatching.java`).*

## 4.1 — Lambdas & Functional Interfaces

A **lambda expression** is a compact, inline implementation of a **functional interface** — an interface with exactly one abstract method (a "SAM type," Single Abstract Method). Lambdas let you pass a *behavior* as a value, the same way you'd pass a `String` or an `int`.

### Key Concepts

- **A lambda has no type of its own.** It adopts its type from context — the "target type" — whatever functional interface it's assigned to, passed as a parameter for, or returned as. The exact same lambda text can implement two different functional interfaces in two different contexts.
- **Syntax**: `(parameters) -> expression` or `(parameters) -> { statements; return x; }`.
- **`@FunctionalInterface`** is an annotation that makes the compiler *enforce* "exactly one abstract method" — adding a second abstract method becomes a compile error. `default` and `static` methods on the interface don't count against that limit.
- **Lambdas vs. anonymous classes** (the pre-Java-8 way to do this): a lambda's `this` refers to the **enclosing instance** (an anonymous class gets its own new `this`); lambdas compile via `invokedynamic` rather than generating a new `.class` file per lambda site (less class-loading overhead); lambdas only fit SAM types (an anonymous class can extend a concrete class or implement a multi-method interface); and lambdas introduce no new scope for variable names, so they can't shadow an enclosing variable the way an anonymous class body can.

### Worked Example: From Anonymous Class to Lambda

```java
@FunctionalInterface
interface Greeter {
    String greet(String name);
}

// 2004 style:
Greeter old = new Greeter() {
    @Override
    public String greet(String name) {
        return "Hello, " + name;
    }
};

// Modern: identical semantics, the boilerplate deleted.
Greeter lambda = name -> "Hello, " + name;

System.out.println(old.greet("anonymous class"));   // Hello, anonymous class
System.out.println(lambda.greet("lambda"));          // Hello, lambda
```

Both `old` and `lambda` implement `Greeter`'s single abstract method `greet`. The lambda form has no visible class, no `@Override`, no explicit method name — the compiler infers all of that from the target type (`Greeter`) at the assignment site.

### The Big Four (`java.util.function`)

| interface | shape | method | question it answers |
|---|---|---|---|
| `Predicate<T>` | `T → boolean` | `test` | does it pass? |
| `Function<T,R>` | `T → R` | `apply` | transform it |
| `Consumer<T>` | `T → void` | `accept` | use it up |
| `Supplier<T>` | `() → T` | `get` | make one on demand |

```java
Predicate<String> isLong = s -> s.length() > 5;
System.out.println(isLong.test("kafka"));            // false

Function<String, Integer> length = s -> s.length();
System.out.println(length.apply("docker"));            // 6

Consumer<String> printer = s -> System.out.println("consuming " + s);
printer.accept("redis");                                 // consuming redis

Supplier<List<String>> freshList = () -> new ArrayList<>();
System.out.println(freshList.get());                     // []
```

Variants worth knowing: `BiFunction<T,U,R>` (two inputs, one output), `UnaryOperator<T>` (a `Function<T,T>` — input and output the same type), `BinaryOperator<T>` (a `BiFunction<T,T,T>`), `BiConsumer<T,U>`, and **primitive specializations** (`IntPredicate`, `ToIntFunction<T>`, `IntUnaryOperator`, …) that operate directly on primitives instead of their boxed wrappers, avoiding autoboxing overhead in hot loops — the same reason `IntStream`/`LongStream`/`DoubleStream` exist alongside `Stream<T>`.

```java
BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b;
UnaryOperator<String> shout = s -> s.toUpperCase() + "!";
System.out.println(add.apply(17, 25));   // 42
System.out.println(shout.apply("go"));    // GO!
```

### Method References — Four Kinds

```java
Function<String, Integer> parse = Integer::parseInt;      // static
Function<String, Integer> len   = String::length;         // instance method, UNBOUND
Predicate<String> isJava        = "java"::equals;         // instance method, BOUND
Supplier<ArrayList<String>> mk  = ArrayList::new;         // constructor

System.out.println(parse.apply("42"));    // 42
System.out.println(len.apply("spring"));   // 6
System.out.println(isJava.test("java"));   // true
System.out.println(mk.get());               // []
```

| kind | example | equivalent lambda |
|---|---|---|
| static | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| unbound instance | `String::length` | `s -> s.length()` (the receiver becomes parameter 1) |
| bound instance | `"java"::equals` | `s -> "java".equals(s)` (the receiver is already fixed) |
| constructor | `ArrayList::new` | `() -> new ArrayList<>()` |

**Rule of thumb**: if a lambda's entire body is a call to one existing method, prefer the method reference — it names the intent directly rather than re-describing an existing method through a wrapper.

### Composition

```java
Function<String, String> trim = String::strip;
Function<String, String> upper = String::toUpperCase;
Function<String, String> cleaned = trim.andThen(upper);       // trim FIRST, then upper
System.out.println(cleaned.apply("  ship it  "));               // SHIP IT

Predicate<String> notBlank = Predicate.not(String::isBlank);
Predicate<String> shortEnough = s -> s.length() <= 10;
Predicate<String> valid = notBlank.and(shortEnough);
System.out.println(valid.test("ok"));   // true
System.out.println(valid.test(""));      // false
```

`Function` supplies `.andThen(g)` (run `this` first, then `g` on the result) and `.compose(g)` (run `g` first, then `this`). `Predicate` supplies `.and()`, `.or()`, `.negate()`, and the static `Predicate.not(...)` helper (handy for negating a method reference directly, since you can't write `!String::isBlank`). Composing small, named functions is generally more readable than one large lambda trying to do everything at once.

### Capture — Lambdas Close Over Their Environment

```java
int taxRate = 18;                             // captured variable
Function<Integer, Integer> withTax = price -> price + price * taxRate / 100;
System.out.println(withTax.apply(100));         // 118
// taxRate = 20;   // uncomment this line: BOTH lines now fail to compile.
```

A local variable captured by a lambda must be **effectively final** — assigned once and never reassigned afterward, even if it's not explicitly marked `final`. Why: the lambda captures a *copy* of the local's value at the point of capture (not a live reference to the stack slot), and allowing reassignment afterward would let that copy silently drift out of sync with the "real" variable — worse, the local's stack frame may not even still exist by the time the lambda actually runs (e.g. if the lambda escapes the method as a return value or gets stored somewhere and called later). This is the exact same rule, for the exact same reason, that Phase 2 covered for anonymous inner classes. Unlike anonymous classes, though, lambdas introduce **no new `this` scope** — `this` inside a lambda body refers to the enclosing instance, not to some hidden lambda object — and lambdas can't shadow an enclosing variable name.

### Behavior Parameterization — the Idea Streams Industrialize

```java
static List<String> pickWhere(List<String> items, Predicate<String> keep) {
    List<String> out = new ArrayList<>();
    for (String item : items) {
        if (keep.test(item)) out.add(item);
    }
    return out;
}

List<String> services = List.of("auth", "payments", "notifications", "search");
System.out.println(pickWhere(services, s -> s.length() > 6));       // [payments, notifications]
System.out.println(pickWhere(services, s -> s.contains("a")));      // [auth, payments, search]
```

`pickWhere` is written once, and an unlimited number of different filtering behaviors can be plugged in by passing different lambdas — instead of writing a near-duplicate method for every filtering rule you'll ever need. This pattern, called **behavior parameterization**, is the conceptual foundation the entire Streams API (4.2) is built on.

### Why It's Useful

Lambdas and method references are the vocabulary every modern Java API speaks — Streams, `CompletableFuture` (Phase 5), event listeners, and most testing/mocking frameworks all take functional interfaces as parameters. Understanding capture semantics (effectively final) prevents a whole class of "why won't this compile" confusion, and recognizing the four method-reference kinds lets you read library code fluently instead of mentally expanding every `Foo::bar` back into a lambda.

### Summary / Key Takeaways

- A lambda implements a functional interface (exactly one abstract method); its type comes entirely from context, not from itself.
- Know the big four (`Predicate`, `Function`, `Consumer`, `Supplier`) and their method names cold.
- Method references have four kinds: static, unbound instance, bound instance, constructor — use one whenever a lambda would just call one existing method.
- Captured locals must be effectively final; lambdas share the enclosing `this`, unlike anonymous classes.
- Behavior parameterization (passing a `Predicate`/`Function` into a method) is the idea Streams scale up into a full pipeline API.

## 4.2 — Streams

A **stream** is not a data structure — it's a **lazy pipeline of computation over data**: a source, zero or more intermediate operations, and exactly one terminal operation that actually triggers everything.

```
source  ->  intermediate ops (0+)  ->  terminal op (exactly 1)
.stream()   filter, map, sorted...    collect, count, forEach...
```

### Key Concepts

- **Lazy**: intermediate operations (`filter`, `map`, `sorted`, …) do absolutely nothing by themselves — they just build up a description of the pipeline. Nothing actually runs until a terminal operation is called.
- **One-shot**: once a terminal operation has consumed a stream, that stream is dead — calling another terminal operation on it throws `IllegalStateException`. Streams model data *in motion*, not a reusable container; if you need to run the pipeline again, build a fresh stream from the source.
- **Non-mutating**: a stream never changes its source collection — results come back as new streams/collections/values.
- Streams express **what** you want computed; ordinary loops express **how** to compute it step by step.

### Worked Example: the Shape of a Pipeline

```java
record Employee(String name, String team, int salary) { }
List<Employee> staff = List.of(
        new Employee("ajay",  "backend",  95_000),
        new Employee("meera", "backend", 130_000),
        new Employee("ravi",  "frontend", 88_000));

List<String> wellPaidBackend = staff.stream()
        .filter(e -> e.team().equals("backend"))
        .filter(e -> e.salary() > 90_000)
        .map(Employee::name)
        .sorted()
        .toList();                                   // terminal (Java 16 shorthand for collect(toList()))

System.out.println(wellPaidBackend);   // [ajay, meera]
```

The equivalent hand-written loop needs a mutable accumulator list, an if-check, and manual sorting afterward — roughly 8 lines of bookkeeping. The stream version reads almost exactly like the requirement ("well-paid backend names, sorted").

### Laziness, Proven

```java
Stream<Employee> pipeline = staff.stream()
        .filter(e -> {
            System.out.println("  filtering " + e.name());
            return e.salary() > 100_000;
        });
System.out.println("pipeline built — notice NOTHING printed yet");
System.out.println("now the terminal op: count = " + pipeline.count());
```
```
pipeline built — notice NOTHING printed yet
  filtering ajay
  filtering meera
  filtering ravi
now the terminal op: count = 1
```

Building `pipeline` with a `filter` that prints does **not** print anything — the intermediate operation is only a description at that point. Only when `.count()` (the terminal operation) runs does the pipeline actually execute, printing once per element as it's filtered. This is directly observable evidence of laziness, not just a claim.

### Short-Circuiting

```java
Optional<Employee> firstData = staff.stream()
        .peek(e -> System.out.println("  examining " + e.name()))
        .filter(e -> e.team().equals("data"))
        .findFirst();
```

`findFirst()`, `anyMatch()`, `limit(n)`, and similar operations **short-circuit**: once the pipeline knows the answer, it stops pulling further elements through — laziness means upstream elements past the point of the answer are never even examined, which is a real performance property, not just a convenience, on large or infinite sources.

### The Core Intermediate Toolbox

```java
System.out.println(Stream.of(1, 2, 2, 3, 3, 3).distinct().toList());              // [1, 2, 3]
System.out.println(IntStream.rangeClosed(1, 100).limit(5).boxed().toList());       // [1, 2, 3, 4, 5]
System.out.println(IntStream.rangeClosed(1, 10).skip(7).boxed().toList());          // [8, 9, 10]
System.out.println(Stream.of("c", "a", "b").sorted().toList());                     // [a, b, c]

// flatMap: one element -> many; flattens nested structure.
List<List<String>> nested = List.of(List.of("a", "b"), List.of("c"), List.of("d", "e"));
System.out.println(nested.stream().flatMap(List::stream).toList());   // [a, b, c, d, e]

// mapToInt -> a primitive IntStream: no boxing, plus numeric ops for free.
var stats = staff.stream().mapToInt(Employee::salary).summaryStatistics();
System.out.println((int) stats.getAverage() + " " + stats.getMin() + " " + stats.getMax());
// 104333 88000 130000
```

`map` transforms **one element into one element**; `flatMap` transforms **one element into a stream of elements, then flattens** all those streams into a single stream — the standard way to go from `List<List<T>>` to `Stream<T>`, or "a sentence" into "its words." `peek` exists mainly for debugging/observing a pipeline — it's not meant for side effects that matter to correctness. `mapToInt`/`mapToLong`/`mapToDouble` switch you onto a primitive stream, unlocking `sum()`, `average()`, and `summaryStatistics()` without any boxing overhead.

### Collectors — the Interview Set

```java
// groupingBy: List -> Map<key, group> — the #1 asked collector.
Map<String, List<String>> byTeam = staff.stream()
        .collect(Collectors.groupingBy(
                Employee::team,
                Collectors.mapping(Employee::name, Collectors.toList())));
System.out.println(byTeam);   // {frontend=[ravi], backend=[ajay, meera]}

// ...with a downstream aggregation instead of a raw list:
Map<String, Double> avgSalary = staff.stream()
        .collect(Collectors.groupingBy(Employee::team, Collectors.averagingInt(Employee::salary)));
System.out.println(avgSalary);   // {frontend=88000.0, backend=112500.0}

// partitioningBy: the two-bucket special case — ALWAYS exactly two keys, true and false.
Map<Boolean, Long> sixFigure = staff.stream()
        .collect(Collectors.partitioningBy(e -> e.salary() >= 100_000, Collectors.counting()));
System.out.println(sixFigure);   // {false=2, true=1}

// joining: strings with separator/prefix/suffix.
String roster = staff.stream().map(Employee::name).collect(Collectors.joining(", ", "[", "]"));
System.out.println(roster);   // [ajay, meera, ravi]

// toMap: watch for duplicate keys — throws IllegalStateException WITHOUT a merge function!
Map<String, Integer> topPerTeam = staff.stream()
        .collect(Collectors.toMap(Employee::team, Employee::salary, Integer::max));
System.out.println(topPerTeam);   // {frontend=88000, backend=130000}
```

`groupingBy(classifier)` alone returns `Map<K, List<T>>`; passing a **downstream collector** as a second argument lets you aggregate each group instead of just collecting it into a list — `counting()`, `averagingInt(...)`, `mapping(f, toList())`, or even a nested `groupingBy` for multi-level grouping. It's the closest thing Java has to SQL's `GROUP BY`, in one expression. `partitioningBy` is a specialized `groupingBy` for a boolean predicate — the result map always has exactly two keys (`true`/`false`), even if one bucket is empty. `Collectors.toMap(keyFn, valueFn)` (two-argument form) **throws `IllegalStateException`** the moment it hits a second element mapping to an already-seen key — the fix is the three-argument form with a merge function (`Integer::max`, `(a, b) -> b`, …) to decide what happens on collision. This is a classic, deliberately-planted interview trap.

### `reduce` — Folding to a Single Value

```java
int totalPayroll = staff.stream().mapToInt(Employee::salary).sum();   // packaged reduce
System.out.println(totalPayroll);   // 313000

Optional<Employee> topEarner = staff.stream()
        .reduce((a, b) -> a.salary() >= b.salary() ? a : b);
System.out.println(topEarner.map(Employee::name).orElse("-"));   // meera
```

`reduce` folds a stream down to one value using an associative accumulator function: the identity-supplying form `reduce(identity, accumulator)` always returns a plain value, while the identity-less form `reduce(accumulator)` returns an `Optional` (there's no sensible result for an empty stream without an identity). `sum`/`min`/`max`/`count` are all "prepackaged" reduces the JDK provides for common cases — prefer them over hand-rolling the equivalent `reduce` call, and prefer `.max(Comparator.comparingInt(...))` over a manual ternary-based `reduce` for the same reason: it states intent more directly.

### Word Count, Stream Edition

```java
String sentence = "to be or not to be";
Map<String, Long> counts = Stream.of(sentence.split(" "))
        .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
System.out.println(counts);   // {not=1, or=1, to=2, be=2}
```

This collapses the Phase 3 manual `merge`-based word-count loop into a single expression — `groupingBy(identity, counting())` is the idiomatic "count occurrences" pattern for streams.

### The Rules of the Road

```java
Stream<String> once = Stream.of("a", "b");
once.count();
try {
    once.count();
} catch (IllegalStateException e) {
    System.out.println("reuse -> IllegalStateException (streams are one-shot)");
}
```

- A stream can be consumed **once**; reuse throws `IllegalStateException`. Since building a stream is cheap (laziness means the construction itself does no real work), just create a fresh one from the source when you need to run the "computation" again.
- **Don't mutate shared state inside `forEach`** (e.g. incrementing an external counter) — it defeats the functional model the API is built around and actively breaks correctness under `parallelStream()`. Use `collect`/`reduce` to accumulate a result instead.
- `.parallelStream()` exists, but **measure before reaching for it**: for small collections or cheap per-element work, the fork/join coordination overhead typically *loses* to a plain sequential stream. Default to sequential.
- Streams complement loops rather than replace them entirely — a two-line loop with an early exit or index math is often genuinely clearer written as a loop than forced into a stream pipeline.

### Why It's Useful

Streams are the standard modern Java idiom for transforming, filtering, and aggregating collections — real production code uses `groupingBy`/`partitioningBy` constantly for reporting and analytics-style logic (per-team averages, pass/fail splits, top-N per group). Understanding laziness and short-circuiting explains real performance characteristics (why `findFirst()` on a huge stream can be fast even though `filter` looks like it should scan everything), and knowing the one-shot rule prevents a genuinely common runtime bug (accidentally reusing a stream reference across two operations).

### Summary / Key Takeaways

- A stream pipeline is lazy, one-shot, and non-mutating; nothing runs until the terminal operation.
- `map` is one-to-one; `flatMap` is one-to-many-then-flatten.
- `groupingBy`/`partitioningBy` are the collectors interviewers ask about most; `toMap` without a merge function throws on duplicate keys.
- `sum`/`min`/`max`/`count` are packaged reduces — prefer them over hand-rolled `reduce` calls.
- Measure before using `parallelStream()`; for small/cheap workloads it usually loses to sequential.

## 4.3 — Optional

`Optional<T>` is a container that holds **either exactly one `T` or nothing**. Its purpose is to move "this might be absent" into a method's **type signature**, so a caller cannot silently forget to handle the empty case — unlike returning `null`, which is invisible in the signature and only surfaces as a `NullPointerException` later, often far from the actual cause. (Tony Hoare, who invented the null reference, has called it "my billion-dollar mistake.")

```java
Optional<User> findUser(String id)   // absence is documented AND enforced by the type
User findUser(String id)             // might return null... surprise, discovered at 2am
```

### Key Concepts

- **`Optional` is a return-type tool**, specifically for methods where "not found" is a legitimate, expected outcome — it is not meant as a general-purpose replacement for `null` everywhere in a codebase.
- **Creation**: `Optional.of(value)` — throws `NullPointerException` immediately if `value` is `null` (it *asserts* presence); `Optional.ofNullable(value)` — safely bridges a legacy, possibly-null value into an `Optional`, becoming `empty()` if `value` is `null`; `Optional.empty()` — an explicitly empty instance.
- **Primitive flavors** (`OptionalInt`, `OptionalLong`, `OptionalDouble`) avoid boxing — this is exactly what `IntStream.max()` and similar terminal operations return.

### Worked Example: the Consuming Spectrum, Bad to Good

```java
record User(String name, String email, Optional<String> nickname) { }

Optional<User> found = findUser("u1");

// BAD: isPresent() + get() is a null-check with extra steps.
// get() on an empty Optional throws NoSuchElementException.
if (found.isPresent()) {
    System.out.println(found.get().name());
}

// GOOD: declare what happens in both cases, declaratively.
System.out.println(findUser("nope").map(User::name).orElse("guest"));   // guest

findUser("u1").ifPresent(u -> System.out.println("found " + u.name()));  // found Ajay

findUser("nope").ifPresentOrElse(
        u -> System.out.println("found " + u.name()),
        () -> System.out.println("not found branch"));                    // not found branch

// Fail loudly when absence is genuinely a bug at this point in the code:
try {
    findUser("nope").orElseThrow(() -> new IllegalStateException("user must exist here"));
} catch (IllegalStateException e) {
    System.out.println(e.getMessage());   // user must exist here
}
```

### `orElse` vs `orElseGet` — the Performance Trap

```java
static String buildDefault(String tag) {
    System.out.println("  (building fallback via " + tag + ")");
    return "default";
}

String v1 = present.orElse(buildDefault("orElse"));            // ALWAYS runs buildDefault — wasteful!
String v2 = present.orElseGet(() -> buildDefault("orElseGet")); // only runs if present is empty — silent here
```
```
  (building fallback via orElse)
```

`orElse(fallbackValue)` takes an **already-evaluated value** as an ordinary method argument — Java evaluates arguments before the call happens, so `buildDefault("orElse")` runs unconditionally, whether or not the `Optional` is actually empty. `orElseGet(supplier)` instead takes a `Supplier<T>` and only invokes it **lazily, on the empty path**. Rule of thumb: a cheap constant fallback (`orElse("guest")`) is fine either way; anything that does real work to build the fallback (`orElseGet(() -> buildExpensiveDefault())`) must use `orElseGet` to avoid paying for work whose result gets thrown away.

### Transforming: `map`, `filter`, `flatMap`, `or`

```java
// map: transform the value IF present; an empty Optional just flows through untouched.
String domain = findUser("u1")
        .map(User::email)
        .map(e -> e.substring(e.indexOf('@') + 1))
        .orElse("unknown");
System.out.println(domain);   // dev.io

// filter: present -> empty if the predicate fails.
System.out.println(findUser("u1").filter(u -> u.email().endsWith(".io")).map(User::name).orElse("no .io user"));
// Ajay

// flatMap: when the mapper ITSELF returns an Optional —
// map alone would give Optional<Optional<String>>; flatMap flattens it.
String nick = findUser("u1").flatMap(User::nickname).orElse("(no nickname)");
System.out.println(nick);   // aj

// or(): chain fallback SOURCES (Java 9+) — the second lookup runs only if the first is empty.
User user = findUser("nope").or(() -> findUser("u2")).orElseThrow();
System.out.println(user.name());   // Meera
```

The `map`/`flatMap` distinction mirrors the exact same distinction from Streams (4.2): `map` wraps whatever the function returns; if that function *already* returns an `Optional`, `map` would nest it, so `flatMap` exists to flatten one level away.

### The Rules — Where Optional Belongs

- **DO** return `Optional` from "find/lookup"-style methods where absence is a normal, expected outcome.
- **DO** chain `map`/`filter`/`flatMap`, ending in `orElse`/`orElseGet`/`orElseThrow`/`ifPresent`.
- **DON'T** take `Optional` as a method **parameter** — it forces every caller to wrap a value just to call your method; use overloading instead.
- **DON'T** use `Optional` as a class **field** as a habit — it's not `Serializable`, and it adds an extra allocation/indirection per read; a plain nullable field with an `Optional`-returning getter is the more common, accepted style (as this repo's own `User.nickname` field does, deliberately, to demonstrate `flatMap`).
- **DON'T** use bare `Optional.get()`, collections **of** `Optional`, or `Optional<Collection<T>>` — an already-empty collection already communicates "nothing here"; wrapping it in another `Optional` is redundant.
- **NEVER** return `null` from a method whose declared return type is `Optional<T>` — that completely defeats the type's entire purpose and reintroduces the exact bug it exists to prevent.

### Why It's Useful

`Optional` converts absence-handling from **control flow** (nested `if (x != null)` checks) into **data flow** (a chain of `map`/`filter`/`flatMap`/`orElse`), which composes far better as chains get longer: `findUser(id).map(User::email).filter(e -> e.endsWith(".io")).orElse("-")` handles three potential absence points implicitly in one readable line, versus three nested null-checks. This pattern shows up constantly in repository/service-layer lookup code.

### Summary / Key Takeaways

- `Optional<T>` puts "might be absent" into the method signature, forcing callers to handle it — it's a return-type tool, not a general `null` replacement.
- `orElse` always evaluates its argument eagerly; `orElseGet` only evaluates its supplier lazily on the empty path — use `orElseGet` for anything expensive.
- `flatMap` avoids `Optional<Optional<T>>` when the mapper itself returns an `Optional`, exactly like Streams.
- Never take `Optional` parameters, avoid it as a habitual field type, never call bare `get()`, and never return `null` from an `Optional`-declared method.

## 4.4 — Pattern Matching (+ `var` and Text Blocks Recap)

**Pattern matching** tests a value's *shape* and **deconstructs** it into bound variables in one step, replacing the old two-or-three-step dance of testing a type, casting to it, and then extracting data. It arrived across several releases: `instanceof` patterns (Java 16), then switch type patterns, guards, and record patterns together (Java 21).

### Key Concepts

- **`instanceof` patterns** (Java 16+): `if (o instanceof List<?> list)` tests the type **and** binds a variable in one expression — no separate cast needed.
- **Flow scoping**: a pattern-bound variable is usable exactly where the compiler can prove the match succeeded — including, notably, *after* a negated check with an early return, since the compiler's flow analysis understands that if the method didn't return, the match must have held.
- **Switch patterns** (Java 21): `case Integer i when i > 40 ->` — `when` introduces a **guard**, an extra boolean condition on top of the type/shape match.
- **Record patterns** (Java 21): deconstruct a record's components positionally, and can nest arbitrarily deep.
- Combined with **sealed types** (Phase 2), a `switch` over a sealed type's permitted subtypes is checked for **exhaustiveness** at compile time — no `default` branch needed, and adding a new permitted subtype later breaks the build everywhere that switch isn't updated to handle it.

### Worked Example: `instanceof` Patterns

```java
Object mystery = List.of("a", "b", "c");

// Old: test, THEN cast, THEN use — three separate chances for a mistake.
// New: test-and-bind in one expression, scoped to where the test is true.
if (mystery instanceof List<?> list && !list.isEmpty()) {
    System.out.println("a list of " + list.size());   // a list of 3
}

static String describeLength(String s) {
    // Flow scoping even works "inverted," via an early return:
    if (s.length() < 5) return "short";
    return "long: " + s.length() + " chars";
}
```

### Worked Example: Switch Type Patterns and Guards

```java
for (Object o : new Object[]{42, "hello", 3.14, List.of(1, 2)}) {
    String desc = switch (o) {
        case Integer i when i > 40 -> "big int: " + i;     // `when` = a guard
        case Integer i             -> "int: " + i;
        case String s              -> "string of length " + s.length();
        case List<?> l             -> "list sized " + l.size();
        default                    -> "something else: " + o;
    };
    System.out.println(desc);
}
// big int: 42
// string of length 5
// something else: 3.14
// list sized 2
```

Ordering rules mirror `catch` block ordering (Phase 3): guarded cases and narrower types must come before broader/unguarded ones, and an unreachable case (one a preceding case would already have matched) is a **compile error**, just like an unreachable `catch`. `switch` over `Object` still needs a `default` branch, since `Object`'s subtypes are inherently open-ended — the compiler can never prove exhaustiveness there. **`case null`** is now expressible directly in a switch; without it, switching on a `null` subject still throws `NullPointerException`, matching pre-21 behavior.

### Worked Example: Record Patterns

```java
record Point(int x, int y) { }
record Line(Point from, Point to) { }

Object point = new Point(3, -7);
if (point instanceof Point(int x, int y)) {
    System.out.println("x=" + x + ", y=" + y);   // x=3, y=-7
}

Object line = new Line(new Point(0, 0), new Point(3, 4));
if (line instanceof Line(Point(var x1, var y1), Point(var x2, var y2))) {
    double len = Math.hypot(x2 - x1, y2 - y1);
    System.out.println("length=" + len);          // length=5.0
}
```

A record pattern tests the type **and** pulls out its components positionally in one step — and it nests arbitrarily, as `Line`'s pattern deconstructing two nested `Point`s shows. Component types inside a pattern can also be written as `var`, letting the compiler infer them.

### The Payoff: Sealed + Records + Switch = an Interpreter

```java
sealed interface Expr permits Num, Add, Mul, Neg { }
record Num(double value)          implements Expr { }
record Add(Expr left, Expr right) implements Expr { }
record Mul(Expr left, Expr right) implements Expr { }
record Neg(Expr inner)            implements Expr { }

static double eval(Expr e) {
    return switch (e) {
        case Num(double v)          -> v;
        case Add(Expr l, Expr r)    -> eval(l) + eval(r);
        case Mul(Expr l, Expr r)    -> eval(l) * eval(r);
        case Neg(Expr inner)        -> -eval(inner);
    };
}

// (3 + 4) * -(2)  as a tree:
Expr expr = new Mul(new Add(new Num(3), new Num(4)), new Neg(new Num(2)));
System.out.println(eval(expr));   // -14.0
```

`eval` is a complete, recursive tree interpreter in six lines, and it's **exhaustive with no `default` branch** — because `Expr` is `sealed` and permits exactly these four record types, the compiler can prove every possible shape is handled. Adding a fifth `Expr` variant (say, `Sub`) makes this method — and every other switch over `Expr` in the codebase — **stop compiling** until it's updated to handle the new case. Before Java 21, achieving this kind of exhaustiveness check required the Visitor design pattern, with its `accept`/`visit` double-dispatch ceremony (roughly 50 lines for the same interpreter). This trio — sealed types, records, pattern-matching switch — is Java's answer to algebraic data types and pattern matching from functional languages, arriving natively rather than as a library trick.

### Guards in Practice

```java
sealed interface Event permits Click, KeyPress, Scroll { }
record Click(int x, int y, int button) implements Event { }
record KeyPress(char key)              implements Event { }
record Scroll(int amount)              implements Event { }

static String handle(Event e) {
    return switch (e) {
        case Click(int x, int y, int b) when b == 2 -> "right-click at " + x + "," + y;
        case Click(int x, int y, var b)             -> "click at " + x + "," + y;
        case KeyPress(char k) when k == 'q'         -> "quit requested";
        case KeyPress(char k)                       -> "key: " + k;
        case Scroll(int amt)                        -> "scroll by " + amt;
    };
}
```

Guards combine record deconstruction with an extra boolean condition (`when b == 2`), letting a single `case` branch on both a value's *shape* and its *contents* — here distinguishing a right-click from any other click by inspecting the deconstructed `button` field directly inside the pattern.

### `var` — Style Recap

`var` (from Phase 1) is compile-time local-variable type inference, not dynamic typing. Style guidance: use it where the right-hand side already makes the type obvious (`var users = new ArrayList<User>();`), and avoid it where it would hide meaningful information (`var report = service.process();` — process what, into what?). This is a readability judgment call, not a hard rule — having a clear, consistent opinion on when to use it is itself part of writing idiomatic modern Java.

### Text Blocks — Recap

```java
var report = """
        {
          "phase": 4,
          "status": "complete"
        }""";
```

Triple-quoted **text blocks** (Java 15+, also introduced in Phase 1) avoid `\n` and escaped-quote noise for multi-line string content like embedded JSON, SQL, or HTML — the position of the closing `"""` controls how much incidental leading whitespace gets stripped from every line.

### Why It's Useful

Pattern matching directly eliminates entire categories of bugs: forgotten casts, typo'd type checks, and — most importantly — silently-unhandled new variants of a sealed hierarchy, since the compiler now catches that at build time instead of a runtime `default: throw new IllegalStateException("unexpected")`. This matters enormously any time you're modeling a fixed, closed set of alternatives (event types, AST nodes, API response shapes, state-machine states) — exactly the situations sealed+records+switch is designed for.

### Summary / Key Takeaways

- Pattern matching tests shape and binds variables in one step; `instanceof` patterns (16), switch type patterns/guards/record patterns (21).
- Pattern-bound variables are flow-scoped — usable wherever the compiler can prove the match held, including after a negated early return.
- Switch case ordering mirrors catch-block ordering: narrower/guarded before broader/unguarded, or it's a compile error.
- A switch over a `sealed` type is exhaustive without `default`; over `Object` it still needs one.
- Sealed types + records + pattern-matching switch together replace the Visitor pattern for closed-set data modeling, with compiler-enforced exhaustiveness.

## 4.5 — Java Version History & the Release Cadence

Every feature in this phase arrived in a specific Java release — the timeline (and the release model itself) is a genuinely common interview question in its own right.

### The Release Model (Since Java 9, 2017)

- A **new feature release every 6 months** (March and September): 9, 10, 11, 12 … 21, 22 …
- **LTS (Long-Term Support)** releases get years of patches and security updates and are what companies actually run in production: **8, 11, 17, 21** (25 next). The LTS cadence itself moved from every 3 years to every **2 years**. Non-LTS releases are stepping stones, mostly of interest to early adopters and library authors testing forward-compatibility.
- **Preview features** ship disabled by default, requiring `--enable-preview` to try — this is how records, pattern matching, and virtual threads were all incubated and refined based on real feedback before being finalized as permanent language features.
- This repo targets **Java 21 (LTS)**, running on OpenJDK.

### The Milestones That Matter

| Version (year) | Headline features |
|---|---|
| **8** (2014) *LTS* | The functional watershed: **lambdas**, the **Streams API**, **`Optional`**, functional interfaces, `default` methods on interfaces, the new `java.time` API. Still the baseline a huge amount of legacy code assumes. |
| **9** (2017) | The **module system** (JPMS / Project Jigsaw), `jshell` (REPL), the 6-month release cadence begins, private interface methods. |
| **10** (2018) | **`var`** (local-variable type inference). |
| **11** (2018) *LTS* | First LTS after 8 — the common "modern baseline" for a long time. `var` usable in lambda parameters, the new `HttpClient`, running a single `.java` file directly (`java File.java`). An Oracle JDK licensing change around this time pushed wide adoption of OpenJDK builds specifically. |
| **14–16** (2020–21) | **Records** (14 as preview → 16 finalized), **pattern matching for `instanceof`**, helpful `NullPointerException` messages, **text blocks** (`"""`), `switch` expressions finalized. |
| **17** (2021) *LTS* | **Sealed classes/interfaces** finalized; records, text blocks, and switch expressions all now stable — widely considered the "modern Java" LTS and a very common migration target from 8/11. |
| **21** (2023) *LTS* | **Virtual threads** (Project Loom) — massively cheap concurrency (Phase 5 territory); **pattern matching for `switch`** finalized; **record patterns** (deconstruction); sequenced collections. The current recommended LTS and this repo's target. |

### The "8 → 17/21" Migration Story

Most real-world modern Java adoption today is specifically about jumping off Java 8, since it remained the default baseline for years. What you gain moving to 17/21: **records** (eliminating boilerplate DTOs/value classes), **sealed types + pattern-matching switch** (algebraic-data-type-style modeling, as demonstrated by the interpreter example above), **text blocks**, **`var`**, a better default garbage collector (G1, covered further in Phase 6), meaningfully stronger standard library APIs, and — specifically at 21 — **virtual threads**. What to watch for: the **module system**'s stronger encapsulation (introduced in 9) can break older libraries that rely on reflective access into JDK internals; some APIs were genuinely **removed** (`finalize()`, older/legacy GC flags); and third-party framework version compatibility needs checking alongside any JDK version bump.

### Why It's Useful

"Which Java version should we target?" and "what changed between 8 and now?" are both extremely common in interviews for any role touching an existing Java codebase, since so much production Java still runs on 8 or 11. Being able to name the LTS line, describe the 6-month/2-year cadence, and connect specific features (lambdas → 8, records/pattern matching → 16–21, virtual threads → 21) to their releases signals real, current familiarity rather than memorized trivia from a single version.

### Summary / Key Takeaways

- LTS releases — the ones production teams actually run — are **8, 11, 17, 21** (25 next), roughly every 2 years; non-LTS releases ship every 6 months as stepping stones.
- Java 8 is the functional watershed (lambdas, Streams, Optional); Java 21 is the current LTS, headlined by virtual threads and finalized pattern matching.
- Preview features (`--enable-preview`) let major features get real-world feedback before becoming permanent — records, pattern matching, and virtual threads all went through this process.
- Migrating off Java 8 mainly risks friction from the module system's stronger encapsulation and removed legacy APIs, not the core language itself.
- Rule of thumb: develop and deploy on the latest LTS, and treat "which Java version" as "which LTS, plus which preview features (if any) do we need."
