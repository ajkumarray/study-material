<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · concurrency](../phase-5-concurrency/NOTES.md) | [Phase 7 · data ➡](../phase-7-data/NOTES.md)
<!-- /nav -->

# Phase 6 — Tooling & Professional Practice: Notes

*This phase leaves single-file scripts behind: everything lives in the `expense-tracker/` Maven project — which is also the seed of the capstone.*

## 6.1 — Maven

**Maven** is a build tool, dependency manager, and project convention, all driven by a single declarative configuration file: `pom.xml` (the "Project Object Model"). Its core philosophy is **convention over configuration** — every Maven project has the same directory shape, so any Java developer can open any Maven project and immediately know where everything lives, with no project-specific setup to learn first.

### Key Concepts

- **Standard directory layout**:
```
project/
├── pom.xml
├── src/main/java          production code
├── src/main/resources     config bundled into the artifact
├── src/test/java          tests (compiled separately, never shipped)
└── target/                ALL build output — never commit it (.gitignore it)
```
- **GAV coordinates** uniquely identify every artifact in the Maven ecosystem: **`groupId`** (a reverse-domain namespace — "who"), **`artifactId`** (the project's name — "what"), **`version`**. Every project has its own GAV, and every dependency is referenced by the GAV of the artifact it needs.
- **`-SNAPSHOT` versions** mean "still under active development" — a snapshot dependency is re-resolved/re-downloaded on each build rather than cached forever, since its content can still change. **Release versions are immutable** — republishing a changed `1.0.0` under the same coordinates is forbidden by convention (and by most real repositories' policy).
- **Dependencies are transitive**: declaring a dependency in `<dependencies>` pulls in not just that artifact but everything *it* depends on too, all fetched from **Maven Central** into a shared local cache at `~/.m2/repository` used by every Maven project on the machine.
- **Version conflict resolution** uses "**nearest wins**" — when two different versions of the same artifact appear at different depths in the transitive dependency tree, Maven picks whichever is closest to your project's own `pom.xml`, **not** the highest version number. This trips people up constantly; `mvn dependency:tree` is the standard first command to run when debugging a `NoSuchMethodError` or unexpected classpath conflict.

### Worked Example: This Project's GAV and Properties

```xml
<groupId>dev.ajay</groupId>
<artifactId>expense-tracker</artifactId>
<version>0.1.0-SNAPSHOT</version>
<packaging>jar</packaging>

<properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.10.2</junit.version>
</properties>
```

This project's GAV is `dev.ajay:expense-tracker:0.1.0-SNAPSHOT`. The `<properties>` block centralizes values used elsewhere in the POM — `${junit.version}` is referenced later in the JUnit dependency declaration, so bumping the JUnit version means editing one line, not every dependency that uses it. `maven.compiler.release=21` is the modern replacement for the old separate `source`/`target` properties — it not only sets the target bytecode version but also actually validates your code against *that specific* Java release's API surface, catching accidental use of a newer API than you intend to support.

### Dependency Scopes

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.13</version>
</dependency>                                    <!-- no <scope> = "compile" (default) -->

<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-simple</artifactId>
    <version>2.0.13</version>
    <scope>runtime</scope>                       <!-- needed to RUN, invisible at compile -->
</dependency>

<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>${junit.version}</version>
    <scope>test</scope>                          <!-- test classpath only; never shipped -->
</dependency>
```

| scope | visible at compile | visible at test | shipped to consumers | typical use |
|---|---|---|---|---|
| `compile` (default) | yes | yes | yes | ordinary application dependencies |
| `runtime` | no | yes | yes | JDBC drivers, logging backends — code never references the class directly |
| `test` | no | yes | no | JUnit, AssertJ, Mockito |
| `provided` | yes | yes | no | container-supplied APIs (e.g. a servlet API the app server already provides) |

Right-scoping a dependency keeps a published artifact's footprint honest — a `test`-scoped dependency accidentally left as `compile` would ship test-only libraries to every consumer of your jar.

### The Build Lifecycle

```
validate → compile → test → package → verify → install → deploy
```

Maven's lifecycle is a fixed, ordered sequence of **phases**; running any phase automatically runs every phase before it first. So `mvn package` implicitly runs `validate`, `compile`, and `test` before actually producing the jar. `mvn install` additionally copies the built artifact into your local `~/.m2` repository, making it available as a dependency for *other* local projects on the same machine. `mvn deploy` publishes it to a remote, shared repository for other people/machines to consume. `mvn clean` belongs to a **separate** lifecycle entirely — it simply wipes the `target/` directory — which is why you'll see the idiom `mvn clean package` when stale build state might be causing weird failures, even though plain `mvn package` is fine for routine day-to-day builds.

### Plugins Do the Real Work

The lifecycle itself is just a sequence of named **slots** — the actual work happens because plugin **goals** are bound into those slots: the compiler plugin's `compile` goal binds to the `compile` phase, the **Surefire** plugin's `test` goal binds to the `test` phase, the jar plugin's goal binds to `package`, and so on.

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.13.0</version>
        </plugin>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.2.5</version>
        </plugin>
    </plugins>
</build>
```

**Pinning plugin versions explicitly is a real, learned lesson, not paranoia**: without an explicit version, Maven 3.8 falls back to a very old bundled default compiler plugin (3.1, from 2013) that predates the `maven.compiler.release` property entirely and silently targets Java 5 — producing an immediate, confusing build failure on modern code. Pinning `3.13.0` fixed it in this exact project. The broader principle: a build should be reproducible on any machine with any Maven version, not dependent on whatever defaults happen to ship with the Maven binary someone has installed.

### Multi-Module Projects and Gradle

Larger, multi-module Maven projects share common configuration through a **parent POM** — Spring Boot's `spring-boot-starter-parent` is exactly this pattern, centralizing dependency and plugin versions for every module that inherits from it (previewed further in the Spring Boot track). **Gradle** is the other major JVM build tool: it uses a Groovy or Kotlin DSL instead of XML, and generally offers faster incremental builds via its daemon process and build cache. The core concepts transfer directly between the two: GAV coordinates, scopes (called "configurations" in Gradle), and lifecycle phases (called "tasks" in Gradle) are the same ideas under different names.

### Why It's Useful

Maven's conventions are the reason a new team member can clone essentially any Java project and immediately know to look in `src/main/java` for production code and run `mvn test` to run the test suite, without reading project-specific documentation first. Understanding scopes and the nearest-wins conflict resolution directly explains real, common build failures — a missing runtime dependency, a leaked test library, or a `NoSuchMethodError` from two conflicting versions of the same library on the classpath.

### Summary / Key Takeaways

- GAV coordinates (`groupId:artifactId:version`) uniquely identify every Maven artifact; `-SNAPSHOT` means mutable/in-development, releases are immutable.
- Scopes control classpath visibility: `compile` (everywhere, default), `runtime` (needed to run, not to compile), `test` (test-only, never shipped), `provided` (compiled against, container-supplied).
- The lifecycle is ordered (`validate → compile → test → package → verify → install → deploy`) — running a later phase runs every phase before it.
- Plugins provide the goals that actually do the work at each lifecycle phase; pin plugin versions for reproducible builds.
- Transitive dependency conflicts resolve by "nearest wins," not highest version — debug with `mvn dependency:tree`.

## 6.2 — Unit Testing With JUnit 5 and AssertJ

**JUnit 5 (Jupiter)** is the standard framework for writing and running automated tests in Java; **AssertJ** is a fluent assertion library commonly paired with it for more readable, expressive test assertions than JUnit's own built-in `assertEquals`-style methods.

### The Layout Contract

A test class for a class `X` lives at `src/test/java`, in the **same package** as `X`, conventionally named `XTest`. Maven's **Surefire** plugin automatically discovers and runs classes matching this naming pattern during `mvn test`. Test code and its `test`-scoped dependencies never ship in the final packaged artifact.

### Key Concepts

- **`@Test`** marks a test method — in JUnit 5, it can be plain `void` with no `public` modifier required (JUnit 5 relaxed that restriction from JUnit 4).
- **Fresh instance per test**: JUnit 5 creates a **brand-new instance of the test class for every single test method**. This is the framework's built-in isolation guarantee — instance fields set up in one test method can never leak state into another, by construction, without any extra effort from the test author.
- **`@BeforeEach`/`@AfterEach`** run around every individual test method; **`@BeforeAll`/`@AfterAll`** (which must be `static`) run exactly once for the whole class.
- **`@DisplayName`** gives a human-readable name shown in test reports and IDE test runners. **`@Nested`** groups related tests into inner classes, which show up as a tree in reports — useful for grouping, e.g., all validation-related tests together. **`@Disabled`** skips a test with a documented reason. **`@Tag`** applies filterable categories (e.g. running only `@Tag("slow")` tests separately in CI).
- **Parameterized tests** (`@ParameterizedTest`) eliminate copy-pasted, near-identical test methods: `@ValueSource` supplies a single argument per run, `@CsvSource` supplies rows of multiple arguments, and `@MethodSource` pulls arbitrary objects from a factory method. One test body runs and reports as N separate, individually-named cases.

### Worked Example: Fixture Setup and the Happy Path

```java
@DisplayName("ExpenseTracker")
class ExpenseTrackerTest {

    private ExpenseTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new ExpenseTracker();     // a FRESH tracker before every single test
    }

    private static Expense expense(String desc, double amount, Category cat) {
        return new Expense(desc, amount, cat, LocalDate.of(2026, 7, 15));
    }

    @Test
    @DisplayName("total sums all expenses")
    void totalSumsAllExpenses() {
        // arrange
        tracker.add(expense("chai", 20, Category.FOOD));
        tracker.add(expense("bus", 45, Category.TRANSPORT));

        // act + assert
        assertThat(tracker.total()).isEqualTo(65.0);
        assertThat(tracker.count()).isEqualTo(2);
    }
}
```

Every test follows **Arrange–Act–Assert**: set up the world, do the one thing being tested, then check the result. The `expense(...)` helper factory method keeps the "arrange" section readable by hiding constructor noise that isn't the point of any individual test. `@BeforeEach` guarantees `totalSumsAllExpenses` (and every other test in the class) starts from a completely fresh, empty `tracker` — no test can accidentally see leftover state from a previous test, regardless of execution order.

### Testing Collections, Maps, and `Optional` With AssertJ

```java
@Test
@DisplayName("groups totals by category")
void groupsByCategory() {
    tracker.add(expense("chai", 20, Category.FOOD));
    tracker.add(expense("lunch", 180, Category.FOOD));
    tracker.add(expense("bus", 45, Category.TRANSPORT));

    Map<Category, Double> report = tracker.byCategory();

    assertThat(report)
            .hasSize(2)
            .containsEntry(Category.FOOD, 200.0)
            .containsEntry(Category.TRANSPORT, 45.0)
            .doesNotContainKey(Category.RENT);
}

@Test
@DisplayName("since() filters and sorts newest first")
void sinceFiltersAndSorts() {
    var today = LocalDate.of(2026, 7, 15);
    tracker.add(new Expense("old", 10, Category.OTHER, today.minusDays(30)));
    tracker.add(new Expense("mid", 20, Category.OTHER, today.minusDays(5)));
    tracker.add(new Expense("new", 30, Category.OTHER, today));

    var recent = tracker.since(today.minusDays(7));

    assertThat(recent)
            .hasSize(2)
            .extracting(Expense::description)          // project, then assert
            .containsExactly("new", "mid");             // exact ORDER checked
}
```

AssertJ's fluent, chainable API reads almost like a specification, and it's genuinely **type-aware**: `Map` assertions understand `containsEntry`/`doesNotContainKey` directly; `.extracting(Expense::description)` projects a list of objects down to a list of one field before asserting on it, and `containsExactly` checks both content **and order** in one call. Compare this to plain JUnit assertions, which would need several separate `assertEquals` calls and manual list/map traversal to express the same checks.

### Testing `Optional`

```java
@Test
@DisplayName("finds the largest expense")
void findsLargest() {
    tracker.add(expense("chai", 20, Category.FOOD));
    tracker.add(expense("rent", 22_000, Category.RENT));

    assertThat(tracker.largest())
            .isPresent()
            .hasValueSatisfying(e -> assertThat(e.description()).isEqualTo("rent"));
}

@Test
@DisplayName("starts empty")
void startsEmpty() {
    assertThat(tracker.count()).isZero();
    assertThat(tracker.total()).isEqualTo(0.0);
    assertThat(tracker.largest()).isEmpty();          // AssertJ knows Optional!
}
```

`isPresent()`/`isEmpty()`/`hasValueSatisfying(...)` let you assert directly on an `Optional<Expense>` without manually calling `.get()` or `.isPresent()` yourself — which also avoids the exact bare-`get()` anti-pattern Phase 4 warns against.

### Testing the Unhappy Path

```java
@Nested
@DisplayName("validation")
class Validation {

    @Test
    @DisplayName("rejects a blank description")
    void rejectsBlankDescription() {
        assertThatThrownBy(() -> expense("   ", 10, Category.FOOD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @ParameterizedTest(name = "rejects non-positive amount: {0}")
    @ValueSource(doubles = {0, -1, -0.01, -99_999, -0.0})
    void rejectsNonPositiveAmounts(double bad) {
        assertThatThrownBy(() -> expense("thing", bad, Category.OTHER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @ParameterizedTest(name = "{0} + {1} -> total {2}")
    @CsvSource({
            "100, 250, 350",
            "0.5, 0.25, 0.75",
            "9999, 1, 10000",
    })
    void totalsAddUp(double first, double second, double expected) {
        tracker.add(expense("a", first, Category.OTHER));
        tracker.add(expense("b", second, Category.OTHER));

        // Floating point (Phase 1!): compare with a tolerance, never isEqualTo.
        assertThat(tracker.total()).isCloseTo(expected, Offset.offset(1e-9));
    }
}
```

`@Nested` groups the five validation-related tests into their own inner class, appearing as a distinct sub-tree in test reports. `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)` asserts both the **type** and the **message** of a thrown exception — checking the message matters, because it proves the *specific* validation rule fired, rather than some unrelated `NullPointerException` accidentally satisfying a looser "it threw *something*" check. `@ParameterizedTest` with `@ValueSource` runs the same test body against five different bad amounts, each reported as its own named case — one test method, five results in the report, no copy-paste. `@CsvSource` supplies multiple arguments per row; the floating-point total is compared with `isCloseTo(expected, Offset.offset(1e-9))` rather than `isEqualTo`, a direct callback to Phase 1's lesson that `double` arithmetic accumulates small representational errors.

### What Makes a Good Unit Test: FIRST

| letter | means | why it matters |
|---|---|---|
| **F**ast | runs in milliseconds | a slow suite doesn't get run often, which defeats its purpose |
| **I**ndependent | passes in any order, with no shared state | order-dependence hides bugs and makes parallel test execution unsafe |
| **R**epeatable | same result every time, everywhere | no reliance on wall-clock time, network, or environment "luck" |
| **S**elf-validating | pass/fail is automatic | no human needs to eyeball printed output to judge success |
| **T**imely | written close to the code it tests | tests written long after the code tend not to get written at all |

Beyond FIRST: deliberately test **unhappy paths** (validation failures, exceptions, empty/boundary inputs), not just the happy path — as the `Validation` nested class above does. Prefer testing **behavior through the public API** rather than internal implementation details, so a safe refactor doesn't break tests that were never actually testing observable behavior in the first place.

### Mocks, and Unit vs. Integration Tests

A **mock** is a stand-in for a collaborator (a repository, an HTTP client, anything slow/nondeterministic/external) that lets you test one unit of code in isolation — you program its canned answers and, often, verify it was called correctly (Mockito's `when(...).thenReturn(...)` / `verify(...)` being the standard library for this, covered in depth in the Spring Boot track). Reach for a mock specifically when the real collaborator is too slow, too unpredictable, or genuinely external to include directly in a fast unit test; over-mocking simple, cheap, pure classes tends to couple tests too tightly to implementation details rather than behavior, so prefer real objects there. **Unit tests** exercise one class/behavior with collaborators faked, run in milliseconds, and typically number in the thousands for a real codebase. **Integration tests** wire real pieces together (a real database, a real HTTP call, a full Spring context) to verify the seams between components actually work — slower, and deliberately fewer of them. This is the classic "testing pyramid": many unit tests, a moderate number of integration tests, very few full end-to-end tests. Related tools worth knowing by name for later: **Testcontainers** (spins up a real database in Docker for genuine integration tests) and **JaCoCo** (measures code coverage) — both arrive naturally in the Spring Boot track.

### Why It's Useful

A well-structured, FIRST-compliant test suite is what actually lets a team refactor and ship confidently — the `ExpenseTrackerTest` class above exercises every public behavior of `ExpenseTracker` (adding, totaling, grouping, filtering, sorting, and rejecting invalid input), so any future change that breaks one of those behaviors fails loudly and immediately, in seconds, rather than being discovered by a user in production.

### Summary / Key Takeaways

- Test classes live at `src/test/java`, same package, named `XTest`; JUnit 5 creates a fresh instance per test method for built-in isolation.
- Every test: Arrange–Act–Assert; every good test suite: Fast, Independent, Repeatable, Self-validating, Timely (FIRST).
- AssertJ's fluent, type-aware assertions (`containsEntry`, `extracting(...).containsExactly(...)`, `isCloseTo`, `hasValueSatisfying`) read like specifications and give richer failure messages than plain JUnit assertions.
- `@ParameterizedTest` (`@ValueSource`/`@CsvSource`/`@MethodSource`) turns copy-pasted near-identical tests into one test body reported as multiple cases.
- Deliberately test unhappy paths with `assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`, checking both exception type and message.

## 6.3 — Logging (SLF4J) and Javadoc

**Logging** is how a running application records what it's doing for later inspection — and a dedicated logging framework is what lets that recording be filtered, routed, and formatted through configuration alone, without editing and redeploying code.

### Why Not `System.out.println`

`println` has none of the following, all of which a real logging framework provides: **levels** (you cannot selectively silence verbose debug output in production without changing and redeploying code), **automatic context** (no timestamp, thread name, or logger name attached automatically), **routing** (no built-in way to send output to a file, rotate it, or ship it to a log aggregator — just the console), and **per-package control** (no way to turn on verbose logging for just one troublesome package while leaving everything else quiet).

### The Facade Pattern: SLF4J

```java
private static final Logger log = LoggerFactory.getLogger(ExpenseTracker.class);
```

Application code in this project compiles against **SLF4J** (`slf4j-api`) only — a pure **facade**, an API with no logging implementation of its own. The actual concrete **backend** that does the real work of formatting and writing log output is chosen at *runtime*, entirely by what's present on the classpath: `slf4j-simple` (a bare stderr printer, used in this lesson for simplicity), **Logback** (the typical production default, fully configurable via XML), Log4j2, or `java.util.logging`. Swapping backends requires **zero changes to application code** — exactly the same dependency-inversion idea JDBC drivers apply to database access. Spring Boot, notably, ships SLF4J paired with Logback preconfigured out of the box.

### Usage Conventions

```java
public void add(Expense expense) {
    log.debug("adding expense: {}", expense);
    expenses.add(expense);
    if (expense.amount() > 10_000) {
        log.warn("large expense recorded: {} ({})", expense.description(), expense.amount());
    }
}
```

- **One logger per class**, declared `private static final Logger log = LoggerFactory.getLogger(TheClass.class);` — logger names mirror the class/package structure, which is exactly what enables per-package verbosity configuration later.
- **Use `{}` placeholders, never string concatenation.** `log.debug("adding {}", expense)` only actually renders the message (calling `expense.toString()`, building the string) **if the `debug` level is currently enabled** — under this project's default `info` level, that `debug` call costs essentially nothing at runtime, since the message is never actually constructed. `log.debug("adding " + expense)`, by contrast, always pays the string-concatenation and `toString()` cost, even when the resulting message is immediately thrown away because the level is disabled. This was directly observable in the lesson: the `debug` lines produced no output at all under the default `info` level.
- **Levels, from most to least severe**: `error` (something is broken and needs action now — e.g. a failed payment, an unreachable database) > `warn` (abnormal but the system keeps working — e.g. this project's large-expense alert) > `info` (routine lifecycle milestones — startup, config loaded, request served) > `debug` (developer-facing diagnostic detail) > `trace` (extremely verbose, essentially a firehose). Production systems typically run at `info` and above; developers flip a specific package to `debug` via configuration only, when actively investigating something, without touching code.
- **Log an exception as the last argument**: `log.error("save failed for {}", id, e)` — the logging backend recognizes a trailing `Throwable` argument and prints its full stack trace as part of the log entry. **Never** call `e.printStackTrace()` in real code — it writes unstructured, unlevelled, unroutable output directly to stderr, bypassing every benefit of the logging framework entirely.

### Javadoc

```java
/**
 * Records a new expense.
 *
 * @param expense the entry to add; validation lives in {@link Expense}
 */
public void add(Expense expense) { ... }
```

`/** ... */` comments on public types and methods are **Javadoc** — structured API documentation that the `javadoc` tool (invoked via `mvn javadoc:javadoc`) turns into browsable HTML, and that your IDE shows directly on hover when someone calls your method. Javadoc should document the **contract** — what a method does, what its parameters mean and constrain (`@param`), what it returns (`@return`), and under what conditions it throws (`@throws`) — not how it's implemented internally (that's what regular inline comments are for, and are rarely even needed for clear code). `{@link}` cross-references another type or member; `{@code}` formats inline code. Rule of thumb: write Javadoc on every `public` member of library-style, reusable code; self-evident simple accessors (a plain `getName()`) can usually skip it. This project's `Expense` record documents each component directly in the record's header Javadoc, which the `javadoc` tool associates with the generated accessor for each component.

### Debugging Beyond `println`

Real diagnostic tools available once logging alone isn't enough: **IDE breakpoints**, including *conditional* breakpoints that only actually pause execution when a given expression is true (e.g. break only when `amount > 10000`), plus step-over/step-into and live expression evaluation; **`jshell`**, the JDK's interactive REPL, for quickly experimenting with an API without writing and compiling a whole throwaway class; **`jstack <pid>`** for full thread dumps (this is exactly the tool that auto-detects the deadlock scenario from Phase 5.2); and **`jmap`**/heap dumps for memory investigation. Once conditional breakpoints are muscle memory, a debugger is almost always faster than inserting and removing `println` statements to narrow down a bug.

### Why It's Useful

Correct log-level discipline is what lets an operations team turn on verbose diagnostic logging for one misbehaving service in production, investigate, and turn it back off — all through configuration, with zero code deploys, zero downtime, and zero risk of accidentally leaving debug logging permanently on and flooding disk/log-aggregation costs. Javadoc written as a contract, not an implementation narration, is what makes a library usable by someone who has never read its source code.

### Summary / Key Takeaways

- Logging frameworks add levels, automatic context, and configurable routing that `println` fundamentally cannot provide.
- SLF4J is a facade — code depends on the API only; the concrete backend (Logback, etc.) is swapped purely by classpath/configuration.
- Use `{}` placeholders, never string concatenation, so disabled log levels cost nothing at runtime; log exceptions as the trailing argument, never `printStackTrace()`.
- Know the five levels (`error` > `warn` > `info` > `debug` > `trace`) and when each applies.
- Javadoc documents the public contract (what/params/returns/throws), not the implementation.

## 6.4 — JVM Memory and Garbage Collection

Phase 2 established that Java objects live on a **garbage-collected heap**, and that dropping the last reference to an object makes it merely *eligible* for collection, not immediately freed. This lesson covers the actual mechanism behind that — a top-tier Java interview topic, and the exact thing the diagnostic tools from 6.3 (`jstat`, `jmap`, heap-dump analyzers) are used to observe and tune.

### JVM Runtime Memory Areas

- **Heap** — shared across all threads; holds every object and array ever allocated; entirely managed by the garbage collector. `OutOfMemoryError: Java heap space` comes from here. Sized with `-Xms` (initial size) and `-Xmx` (maximum size).
- **Stack** — private per thread; holds call frames with local variables, parameters, and return addresses (Phase 1); memory is freed automatically and immediately when a method returns. Stack overflow throws `StackOverflowError` (Phase 2).
- **Metaspace** — holds class metadata; native (off-heap) memory, and auto-grows by default. It replaced the old, fixed-size "PermGen" area starting in Java 8. A `ClassLoader` leak (classes that should have been unloaded on redeploy but weren't) can exhaust Metaspace over time.
- Additionally, each thread has PC (program counter) registers and native method stacks, generally not a focus of everyday tuning.

### What Garbage Collection Actually Does

Garbage collection is automatic memory management: it finds objects that are no longer **reachable** and reclaims their memory, so application code never manually `free`s or `delete`s anything. The precise rule is **reachability, not usage**: an object survives a collection if and only if some chain of references reaches it starting from a **GC root** (local variables on any thread's stack, static fields, actively running threads themselves, JNI references), *even if the running program never actually touches that object again*. This precision matters: it's exactly why a **memory leak in Java** looks different from a leak in a manually-memory-managed language — a Java "leak" is an object that's still technically **reachable** (through an unbounded cache, a static registry that's never cleared, a listener that was registered but never unregistered) yet is never going to be used again. The GC correctly keeps it alive by its own rules; the bug is in the application holding a reference it should have dropped.

### The Generational Hypothesis

Most JVM garbage collectors are built around one empirical observation: **most objects die young**. A request-scoped object, a temporary string, a short-lived loop variable — the overwhelming majority of allocations become garbage almost immediately, while a small minority survive for the life of the application. The heap is split into **generations** to exploit this directly:

- **Young generation** — where every new object is initially allocated, itself split into an **Eden** space and two **Survivor** spaces (S0/S1). A **Minor GC** — frequent and fast — collects Eden: objects still reachable get copied into a survivor space, and Eden is then wiped wholesale. Objects that survive enough successive minor GCs get **promoted (tenured)** into the old generation. Because most objects genuinely do die young, minor GC reclaims the vast majority of garbage cheaply, touching only a small region of the heap.
- **Old (tenured) generation** — holds long-lived objects that have survived enough young-gen collections to be promoted. A **Major/Full GC** collects this generation — rarer, but significantly more expensive, and the classic cause of long, application-pausing GC pauses.
- **Stop-the-world (STW) pauses**: to safely mark, move, or reclaim objects without the application concurrently mutating references out from under it, the GC must pause every application thread for at least part of a collection. Minor GCs are typically short STW pauses; full GCs can be considerably longer. Minimizing STW pause time is the central design goal of every modern collector.

### How a Collection Runs: Mark and Sweep/Copy/Compact

- **Mark** — trace outward from every GC root, marking every object reachable by that trace as "alive."
- **Sweep / copy / compact** — reclaim the space occupied by everything *not* marked. The young generation uses a **copying** approach (surviving objects are copied out of Eden into a survivor space, and Eden is wiped entirely) — fast, and it compacts memory as an automatic side effect of the copy. The old generation instead uses **mark-sweep-compact**, where compaction is a deliberate additional step specifically to avoid long-term memory fragmentation from repeated allocation/deallocation in place.

### The Collectors — Names and When to Use Each

| collector | flag | characteristics | best for |
|---|---|---|---|
| Serial | `-XX:+UseSerialGC` | single-threaded | tiny heaps, single-core environments (small containers, small CLIs) |
| Parallel / Throughput | `-XX:+UseParallelGC` | multi-threaded, maximizes total throughput, accepts longer individual pauses | batch jobs where total run time matters more than pause latency |
| **G1** | `-XX:+UseG1GC` | region-based heap, collects incrementally toward a configurable pause-time target | the **default collector since Java 9** — a balanced default for most server applications |
| ZGC / Shenandoah | `-XX:+UseZGC` | concurrent, targets **sub-millisecond** pauses even on very large (multi-GB/TB) heaps — most GC work happens without stopping the app at all | latency-sensitive services where any noticeable pause is unacceptable |
| Epsilon | `-XX:+UseEpsilonGC` | a genuine no-op collector — allocates, never reclaims anything | benchmarking allocation behavior, or deliberately short-lived jobs that will exit before running out of memory |

`-XX:MaxGCPauseMillis` sets G1's pause-time goal specifically (a target the collector aims for, not a hard guarantee).

### Escape Analysis — a Related JIT Optimization

If the JIT compiler can *prove* an object never "escapes" the method it's created in (never gets stored somewhere reachable after the method returns, never gets passed elsewhere in a way that keeps it alive), it can allocate that object directly on the **stack** instead of the heap (or eliminate it entirely via scalar replacement) — meaning it's automatically freed the instant the method returns, with zero garbage collector involvement at all. This is a genuine runtime optimization, not a language feature you write explicitly.

### Tuning and Observing

- **Right-size the heap** (`-Xmx`/`-Xms` — in production these are often set equal to each other specifically to avoid the overhead of the heap resizing itself at runtime), pick a collector matching your actual goal (throughput vs. latency), and set a pause-time target for G1 if it's the chosen collector.
- **Observe** with `jstat -gcutil <pid>` (live generation occupancy and cumulative GC time — useful for spotting GC "thrashing," where the collector is running constantly and barely reclaiming anything), **GC logs** (`-Xlog:gc*`), and **heap dumps** (captured with `jmap`, analyzed with a tool like Eclipse MAT) to identify precisely what's filling the old generation. A steadily rising old-generation occupancy *even immediately after full GCs* is the classic, reliable signature of an actual memory leak, as opposed to normal allocation churn.
- **Golden rule: measure before tuning.** Most apparent "GC problems" are actually either allocation churn (the application is simply generating far more short-lived garbage than necessary) or a genuine leak — fixing the underlying code (bounding caches, reducing unnecessary short-lived allocations) is almost always the right first move, well before reaching for JVM flags.

### `finalize()` Is Dead

`Object.finalize()` — a hook the GC could historically call before reclaiming an object's memory — is deprecated and was **removed entirely in Java 18**. It was never a reliable cleanup mechanism to begin with: there was no guarantee it would ever run at all, and no guarantee *when* it would run even if it did. For deterministic resource cleanup, use **try-with-resources** (`AutoCloseable`, Phase 3) or, for genuinely GC-triggered cleanup as a last resort, a `java.lang.ref.Cleaner`. The GC's job is managing **memory** specifically — files, sockets, locks, and other non-memory resources remain the application's own responsibility to close deterministically.

### Why It's Useful

Understanding generational GC and reachability directly explains real production symptoms: a service with steadily climbing memory usage that eventually throws `OutOfMemoryError` almost always has a reachability leak (an unbounded cache, a registered-but-never-removed listener), not a "GC bug" — and diagnosing it correctly means reaching for a heap dump and MAT, not randomly adjusting `-Xmx`. Knowing G1 is the modern default, and when ZGC/Shenandoah are worth the switch, is directly applicable when choosing JVM flags for a real service under latency requirements.

### Summary / Key Takeaways

- The heap (shared, GC-managed) and stack (per-thread, automatically freed on return) are separate memory areas; Metaspace holds class metadata.
- GC reclaims objects by **reachability from GC roots**, not by usage — a Java "leak" is an object that's still reachable but should have been dereferenced.
- Generational GC exploits "most objects die young": frequent, cheap minor GCs collect the young generation; rarer, costlier major/full GCs collect the old generation; both require stop-the-world pauses.
- G1 is the default collector since Java 9, targeting a configurable pause-time goal; ZGC/Shenandoah target sub-millisecond pauses on very large heaps.
- Measure before tuning — most GC problems are actually allocation churn or a genuine reachability leak, best fixed in code, not with JVM flags; `finalize()` is removed — use try-with-resources or `Cleaner` for deterministic cleanup.
