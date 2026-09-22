<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · concurrency](../phase-5-concurrency/NOTES.md) | [Phase 7 · data ➡](../phase-7-data/NOTES.md)
<!-- /nav -->

# Phase 6 — Tooling & Professional Practice: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 6.1 — Maven

**Q: What does Maven actually do?** ⭐

Three distinct jobs, all driven by one `pom.xml` file: **build automation** (a standard, ordered lifecycle — compile, test, package, and so on), **dependency management** (you declare dependencies by GAV coordinate, and Maven fetches them, along with everything *they* transitively depend on, from Maven Central into a shared local cache at `~/.m2/repository`), and **project convention** (every Maven project has the identical directory shape — `src/main/java`, `src/test/java`, and so on — so any Java developer instantly knows where to look).

**Q: Explain the Maven build lifecycle.** ⭐

It's a fixed, ordered sequence of phases: `validate → compile → test → package → verify → install → deploy`. Running any given phase automatically runs every phase before it first — so `mvn package` implicitly compiles and runs the test suite before actually producing the jar. `mvn install` additionally copies the resulting artifact into your local `~/.m2` repository, making it available to *other* local Maven projects on the same machine as a dependency. `mvn deploy` publishes it out to a remote, shared repository. `mvn clean`, notably, belongs to a completely separate lifecycle — it just deletes the `target/` directory.

*Follow-up: "What's the difference between a phase and a goal?"* Phases are just named slots in the lifecycle sequence; the actual work at each phase is performed by plugin **goals** that get bound into that phase — for example, the Surefire plugin's `test` goal binds to the `test` phase. Plugins do essentially all of the real work; the lifecycle itself is just the schedule they run against.

**Q: What are Maven dependency scopes, and what does each one control?** ⭐

`compile` (the default) — visible on every classpath, and shipped with the final artifact. `runtime` — needed to actually run the application or its tests, but not visible at compile time (JDBC drivers and logging backend implementations are the classic examples, since application code compiles against an interface/facade, not the concrete implementation). `test` — visible only on the test classpath, never shipped to consumers of the built artifact (JUnit, AssertJ). `provided` — code compiles against it, but a container or runtime environment is expected to supply the actual implementation at runtime, so it isn't bundled (a servlet API supplied by an application server is the classic example). Getting scopes wrong produces either a bloated published artifact (a `test` dependency accidentally left as `compile`) or a broken runtime (a `runtime`-only dependency accidentally omitted).

**Q: How does Maven resolve version conflicts when two dependencies transitively pull in different versions of the same artifact?** ⭐ *senior probe*

Via "**nearest wins**" — Maven picks whichever version sits closest to your own project in the dependency tree, which is explicitly **not** the same thing as picking the highest version number; a shallow, older version can win over a deeper, newer one. You can override this by declaring the dependency directly at depth 0 in your own POM, or centrally via `<dependencyManagement>` — exactly what Spring Boot's parent POM does across hundreds of managed library versions. `mvn dependency:tree` is the standard first command to run when debugging a `NoSuchMethodError` or `ClassNotFoundException` that smells like a version conflict.

**Q: What is a `-SNAPSHOT` version?**

It signals "still under active development" — a snapshot dependency is re-checked and potentially re-downloaded on every build rather than cached forever, since its actual content can still change under the same version string. Release versions, by contrast, are treated as permanently immutable — republishing different content under an already-released version number like `1.0.0` is forbidden by convention, and CI pipelines typically actively block SNAPSHOT dependencies from ever appearing in a release build.

**Q: Why would you explicitly pin plugin versions in a POM?**

For build reproducibility — without an explicit version, a build depends on whichever plugin defaults happen to be bundled with whatever Maven version is installed on a given machine, which can silently differ across developers' machines or CI. This project hit a real, concrete case of this: Maven 3.8's bundled default compiler plugin (version 3.1, from 2013) predates the `maven.compiler.release` property entirely and silently targeted Java 5 instead, breaking the build outright until the plugin version was explicitly pinned to `3.13.0`. The underlying principle: the same source code, on any machine, with any installed Maven version, should always produce the same build.

**Q: Maven vs. Gradle — what's the actual difference?**

They solve the same problems in different styles. Maven uses declarative XML with rigid, opinionated convention — verbose but highly predictable and consistent across projects. Gradle uses a Groovy or Kotlin DSL, offering more flexibility along with real incremental-build and background-daemon speed advantages for large projects. The underlying concepts map almost one-to-one between them: GAV coordinates exist in both, Maven's scopes correspond to Gradle's "configurations," and Maven's lifecycle phases correspond to Gradle's "tasks." Spring Boot supports both build tools equally; Android tooling has standardized specifically on Gradle. In practice, understanding one deeply makes learning the other largely a matter of new syntax for already-familiar concepts.

## 6.2 — JUnit 5 & AssertJ

**Q: Walk through the JUnit 5 lifecycle annotations.** ⭐

`@BeforeAll`/`@AfterAll` are `static` methods that run exactly once for the entire test class. `@BeforeEach`/`@AfterEach` run around **every individual** test method. The key underlying design fact that makes this safe: JUnit 5 creates a **completely fresh instance of the test class for every single test method** — so instance fields set up by one test can never leak into another test, purely by construction, with no extra discipline required from whoever writes the tests.

**Q: What makes a good unit test?** ⭐

Structurally, every test follows **Arrange–Act–Assert**: set up the scenario, perform the one action being tested, then check the outcome. Qualitatively, a good test is **FIRST**: **F**ast (runs in milliseconds, so the whole suite gets run constantly, not occasionally), **I**ndependent (passes regardless of execution order, with no shared state between tests), **R**epeatable (the same result every time, with no dependency on wall-clock time, network access, or other environmental "luck"), **S**elf-validating (an automatic pass/fail, never requiring a human to read and judge printed output), and **T**imely (written close in time to the code it tests, since tests written long after tend not to get written at all). Beyond FIRST: deliberately cover unhappy paths (validation failures, thrown exceptions, empty/boundary inputs), and test observable **behavior through the public API** rather than internal implementation details, so a safe internal refactor doesn't unnecessarily break tests that were never really testing behavior in the first place.

**Q: How do you test that a piece of code throws an exception?** ⭐

Plain JUnit provides `assertThrows(IllegalArgumentException.class, () -> ...)`. AssertJ offers a richer, more readable alternative: `assertThatThrownBy(() -> ...).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank")`, which asserts both the exception's **type** and its **message** in one fluent chain. Checking the message specifically matters — it's what proves the *right* validation rule actually fired, rather than some unrelated bug (an accidental `NullPointerException`, say) happening to also be an `IllegalArgumentException`'s sibling and passing a too-loose "it threw *something*" check.

**Q: What are parameterized tests, and why use them?** ⭐

`@ParameterizedTest` runs a single test method body against multiple sets of input, reporting each run as its own individually-named test case in the results. `@ValueSource` supplies a single argument per run (e.g. a list of invalid `double` amounts to test rejection against); `@CsvSource` supplies rows of multiple comma-separated arguments per run; `@MethodSource` pulls arbitrary, more complex objects from a factory method when simple literal values aren't expressive enough. This directly eliminates copy-pasted, nearly-identical test methods, and turns an edge-case table (zero, negative, a boundary value, …) into one compact, readable annotation instead of five separate methods.

**Q: Why reach for AssertJ instead of plain JUnit assertions?**

Fluent, chainable assertions read almost like a specification of expected behavior (`assertThat(report).hasSize(2).containsEntry(...)`, versus several separate `assertEquals` calls). Failure messages show the full expected-vs-actual context automatically. Its API is genuinely **type-aware** — dedicated assertions exist for `Optional` (`isPresent`, `hasValueSatisfying`), collections (`.extracting(Expense::description).containsExactly(...)` — project down to one field, then assert both content *and* order), maps (`containsEntry`, `doesNotContainKey`), and floating-point values (`isCloseTo(expected, offset)`, correctly avoiding an exact-equality comparison on a computed `double`, echoing Phase 1's floating-point precision lesson). It also structurally eliminates the classic `assertEquals(expected, actual)` argument-order mistake, since AssertJ's fluent style makes the roles of each value unambiguous from the method chain itself.

**Q: What is a mock, and when do you actually need one?** *bridge question*

A mock is a programmable stand-in for a real collaborator — a repository, an HTTP client, anything slow, non-deterministic, or genuinely external — that lets you test one unit of code in true isolation from its dependencies. You program its canned responses and, often, verify it was invoked correctly (Mockito's `when(...).thenReturn(...)` and `verify(...)` are the standard tools for this, covered more deeply in the Spring Boot track). Reach for a mock specifically when the real collaborator would make a test slow, flaky, or dependent on external systems; for cheap, pure classes with no external dependencies, prefer using the real object directly — over-mocking tends to couple tests too tightly to implementation details rather than actual observable behavior.

**Q: Unit test vs. integration test — what's the practical difference?**

A unit test exercises one class or one focused behavior, with all of its collaborators faked/mocked, runs in milliseconds, and a healthy codebase typically has thousands of them. An integration test wires real components together — an actual database, a real HTTP call, a fully wired Spring application context — to verify that the seams between components genuinely work correctly; these are slower and deliberately far fewer in number. This is the classic "testing pyramid" shape: many fast unit tests forming the base, a moderate number of integration tests in the middle, and very few slow, expensive end-to-end tests at the top.

## 6.3 — Logging & Javadoc

**Q: Why use a logging framework instead of `System.out.println`?** ⭐

Because a real logging framework gives you levels that are filterable **per environment without any code changes at all** (verbose `debug` output in a developer's local environment, quiet `info`-and-above in production), automatically attached context (timestamp, thread name, logger name — none of which `println` provides), configurable routing (console, rotating files, remote aggregators — controlled entirely by configuration, not code), and per-package verbosity control. `println` has none of this, and critically, it cannot be selectively silenced in production without actually editing and redeploying code.

**Q: What is SLF4J, precisely?** ⭐

A logging **facade** — application code compiles against the `slf4j-api` artifact only, with no concrete logging implementation baked in at compile time. Whichever concrete **backend** happens to be present on the runtime classpath — Logback (the typical production default), Log4j2, `java.util.logging`, or a trivial one like `slf4j-simple` — is what actually does the work of formatting and writing log output, and it can be swapped with **zero changes to application code**. This is dependency inversion applied directly to logging, and it's the same underlying idea as swapping a JDBC driver. Spring Boot ships SLF4J paired with Logback preconfigured by default.

**Q: Why write `log.debug("user {}", id)` instead of `log.debug("user " + id)`?** ⭐

Because the `{}` placeholder form **defers** the actual construction of the log message: if the `debug` level is currently disabled, the string concatenation and any `toString()` calls involved never actually happen at all — the call is essentially free. The plain string-concatenation form, by contrast, always pays that construction cost on **every** call site, even when the resulting message is immediately thrown away because that level is disabled. In a hot code path called thousands of times per second, that difference is real, measurable overhead. (For a genuinely expensive-to-compute log argument, you can additionally guard with `log.isDebugEnabled()` before doing the expensive work, or use a lambda-based logging API where available.)

*Follow-up: "How do you log an exception correctly?"* Pass it as the **last** argument: `log.error("save failed for {}", id, e)` — the logging backend recognizes a trailing `Throwable` and includes its full stack trace as part of the structured log entry. Never call `e.printStackTrace()` in real application code — it bypasses the entire logging framework, writing unlevelled, unroutable, unstructured text directly to stderr.

**Q: Name the log levels and when each applies.**

`error` — something is genuinely broken and needs action (a failed payment, an unreachable database). `warn` — abnormal, but the system is still functioning correctly (a retry that succeeded, use of a deprecated path, this project's large-expense alert). `info` — routine lifecycle milestones (application started, configuration loaded, a request was served). `debug` — developer-facing diagnostic detail, not meant for routine production visibility. `trace` — extremely verbose, near-firehose-level detail. Production systems default to `info` and above; a developer flips a specific troublesome package to `debug` via configuration when actively investigating an issue, then flips it back — no code changes involved either way.

**Q: What belongs in Javadoc, and what doesn't?**

The **contract**: what the method does, what its parameters mean and require (`@param`), what it returns and under what conditions (`@return`), and precisely when/why it throws (`@throws`) — never a narration of the implementation's internal logic, which belongs in ordinary inline comments (and is rarely even needed for genuinely clear code). Write it on the public API surface of library-style, reusable code; self-evident simple accessors can typically skip it without loss. `{@link}` and `{@code}` support cross-referencing other types/members and formatting inline code respectively. It's generated into browsable HTML via `mvn javadoc:javadoc`, and it's precisely what an IDE displays on hover when someone else calls your method — write it for that reader specifically, not for someone reading your source file top to bottom.

**Q: A production JVM appears stuck or unusually slow — what are your first diagnostic steps?** *practical senior probe*

Start with `jstack <pid>` for a full thread dump — it's essentially free to capture, and the JVM automatically detects and explicitly reports genuine deadlocks in its output; looking for a large number of threads in the `BLOCKED` state (Phase 5) is often immediately diagnostic on its own. Capturing two thread dumps a few seconds apart and comparing them shows whether threads are making progress or are genuinely stuck. From there: `jmap -heap` or a full heap dump analyzed with a tool like Eclipse MAT for memory-related issues, and `jstat -gcutil` for GC thrashing. The thread dump is always the right first step precisely because it's free to capture and frequently names the actual culprit directly.

**Q: How does Java garbage collection work, at a high level?** ⭐⭐

The GC automatically reclaims heap memory occupied by objects that are no longer **reachable** — traced from a set of GC roots (local variables on any thread's stack, static fields, live threads themselves). It marks every object it can reach from those roots as alive, then reclaims the space used by everything left unmarked, so application code never manually frees or deletes anything. Because safely marking and moving objects requires the object graph to hold still, modern JVMs briefly pause application threads to do this work — a "stop-the-world" pause — and organize the heap **generationally** to keep the vast majority of those pauses short.

**Q: Explain generational GC — young generation vs. old generation.** ⭐⭐

It's built on one empirical observation: **most objects die young**. New objects are allocated in the **young generation** (an Eden space plus two survivor spaces); a frequent, cheap **minor GC** collects it by copying still-reachable objects into a survivor space and wiping Eden entirely, and objects that survive enough successive minor GCs get **promoted** into the **old generation**. The old generation is collected by comparatively rare but significantly more expensive **major/full GCs**. Splitting the heap by object age this way lets the overwhelming common case — short-lived garbage — get reclaimed cheaply and often, while the much smaller set of genuinely long-lived objects is handled separately and less frequently.

**Q: What does "reachable" actually mean, and why isn't an object collected just because the program stopped using it?** ⭐

GC eligibility is determined strictly by **reachability**, not by whether the program is actually still "using" an object in any meaningful sense — an object survives collection as long as *any* chain of references reaches it from a GC root, even if the running program will genuinely never touch it again. This precise distinction is exactly what a Java "memory leak" actually is: objects that remain technically **reachable** — sitting in an unbounded cache, a static registry that's never cleared, a listener collection an object forgot to unregister from — yet are functionally dead weight the program will never use again. The GC correctly keeps them alive per its own rules; the bug lives entirely in the application code holding a reference it should have released.

**Q: What is a stop-the-world pause?** ⭐

A pause during which the garbage collector halts every application thread so it can safely mark and/or move objects without the running application concurrently mutating the reference graph out from under it. Minor GCs typically cause short stop-the-world pauses; full GCs can cause considerably longer ones. Minimizing the length (and, for the newest collectors, even the existence) of these pauses is the central design goal driving the evolution of modern JVM garbage collectors.

**Q: Which garbage collectors does the JVM offer, and when would you choose each?** ⭐⭐

Serial (single-threaded — tiny heaps or single-core environments like small containers). Parallel/Throughput (multi-threaded, optimizes total throughput and accepts longer individual pauses — a good fit for batch jobs). **G1** (region-based, collects incrementally toward a configurable pause-time target, and has been the **default collector since Java 9** — a solid, balanced default for most server applications). ZGC and Shenandoah (fully concurrent collectors targeting sub-millisecond pauses even on very large multi-GB/TB heaps, doing most of their work without stopping the application at all — the right fit for genuinely latency-sensitive services). Choice generally comes down to heap size and whether the priority is raw throughput or minimizing latency.

**Q: How do you actually diagnose and fix a GC or memory problem?** *senior probe*

Observe before touching anything: `jstat -gcutil <pid>` and GC logs (`-Xlog:gc*`) reveal pause frequency/duration and overall throughput impact, and a heap dump (captured with `jmap`, examined in a tool like Eclipse MAT) reveals precisely what's filling the old generation and via what reference chain. A steadily climbing old-generation occupancy that persists even immediately *after* full GCs is the reliable signature of an actual leak — find the dominant retained-object set in the heap dump analyzer to pinpoint it. In practice, most apparent "GC problems" turn out to be either unnecessary allocation churn (the application is generating far more short-lived garbage than it needs to) or a genuine leak — fixing the underlying code (bounding caches, reducing unnecessary allocations, properly unregistering listeners) should almost always come before reaching for `-Xmx` or collector-choice tuning flags.

**Q: Should you use `finalize()` to release resources?** ⭐

No — `finalize()` is deprecated and was removed entirely in Java 18, and it was never actually reliable even while it existed: there was no guarantee it would run at all, and no guarantee of *when* it would run relative to when the object actually became garbage. For deterministic resource cleanup, use **try-with-resources** (`AutoCloseable`, Phase 3) for anything with a clear scope, or a `java.lang.ref.Cleaner` as a genuine last-resort safety net. The garbage collector's responsibility is strictly **memory** — files, sockets, locks, and any other non-memory resource remain the application's own responsibility to close deterministically and promptly.
