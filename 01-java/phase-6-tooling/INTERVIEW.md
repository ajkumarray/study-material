<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · concurrency](../phase-5-concurrency/NOTES.md) | [Phase 7 · data ➡](../phase-7-data/NOTES.md)
<!-- /nav -->

# Phase 6 — Tooling & Professional Practice: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 6.1 — Maven

**Q: What does Maven actually do?** ⭐
Three jobs: **build automation** (compile → test → package via a standard lifecycle), **dependency management** (declare GAV coordinates; Maven fetches from Central into `~/.m2` with transitive resolution), and **project convention** (`src/main/java`, `src/test/java` — every project the same shape). One `pom.xml` drives it all.

**Q: Explain the Maven lifecycle.** ⭐
Ordered phases where running one runs all before it: `validate → compile → test → package → verify → install → deploy`. So `mvn package` compiles and tests first; `install` puts the artifact in the local repo for other local projects; `deploy` publishes to a remote repository. `clean` is a separate lifecycle wiping `target/`.
*Follow-up: "Phases vs goals?" — phases are lifecycle slots; plugins provide* goals *that bind into them (surefire:test binds to the test phase). Plugins do all real work; the lifecycle is just the schedule.*

**Q: What are dependency scopes?** ⭐
`compile` (default, all classpaths), `runtime` (run/test but not compile — JDBC drivers, logging backends), `test` (test classpath only — JUnit/AssertJ; never shipped), `provided` (compile against it, container supplies it — servlet API). Wrong scoping = bloated artifacts or leaked test libs.

**Q: How does Maven resolve version conflicts in transitive dependencies?** ⭐ *senior probe*
"**Nearest wins**" — the version closest to your project in the dependency tree, NOT the newest. Override by declaring the dependency directly (depth 0) or via `<dependencyManagement>` (what Spring Boot's parent POM does for hundreds of libraries). Debug with `mvn dependency:tree` — the first command to run on any NoSuchMethodError/ClassNotFoundException.

**Q: What is a SNAPSHOT version?**
`-SNAPSHOT` = in-development: re-checked/re-downloaded on each build, mutable. Release versions are immutable forever — republishing a changed 1.0.0 is forbidden. CI pipelines typically block SNAPSHOT dependencies in release builds.

**Q: Why pin plugin versions in the POM?**
Reproducibility — otherwise the build depends on whichever defaults your Maven version bundles. Real case from this phase: Maven 3.8's default compiler plugin (3.1) predates `maven.compiler.release` and targets Java 5 — the build broke until we pinned 3.13.0. Same build, any machine, any Maven → pin.

**Q: Maven vs Gradle?**
Same problems, different style: Maven = declarative XML, rigid convention, verbose but predictable; Gradle = Groovy/Kotlin DSL, flexible, incremental-build and daemon speed advantages. Concepts map 1:1 (GAV, scopes↔configurations, phases↔tasks). Spring Boot supports both; Android standardized on Gradle. Knowing one deeply makes the other a syntax change.

## 6.2 — JUnit 5 & AssertJ

**Q: Walk me through the JUnit 5 lifecycle annotations.** ⭐
`@BeforeAll`/`@AfterAll` (static, once per class), `@BeforeEach`/`@AfterEach` (around every test). Key design fact: JUnit creates a **fresh test-class instance per test method** — instance fields don't leak between tests; isolation by construction.

**Q: What makes a good unit test?** ⭐
Structure: **Arrange–Act–Assert**. Qualities: **FIRST** — Fast, Independent (any order, no shared state), Repeatable (no environment/clock/network luck), Self-validating (asserts, not eyeballing output), Timely. Plus: cover unhappy paths (validation, exceptions, empty inputs), and test *behavior through the public API*, not implementation details — refactoring shouldn't break tests.

**Q: How do you test that code throws?** ⭐
JUnit: `assertThrows(IllegalArgumentException.class, () -> ...)`. AssertJ (richer): `assertThatThrownBy(() -> ...).isInstanceOf(...).hasMessageContaining("blank")` — asserts type AND message. Testing the message matters: it proves the *right* validation fired, not some accidental NPE.

**Q: What are parameterized tests?** ⭐
One test body, many cases: `@ParameterizedTest` + a source — `@ValueSource` (single args), `@CsvSource` (arg rows), `@MethodSource` (arbitrary objects). Each case reports individually. Kills copy-pasted near-identical tests; edge-case tables (0, -1, boundary…) become one readable annotation.

**Q: Why AssertJ over plain JUnit assertions?**
Fluent chains read as specs (`assertThat(report).hasSize(2).containsEntry(...)`); failure messages show full expected-vs-actual context; type-aware APIs for Optional, collections (`extracting(...).containsExactly(...)` — projection + order), maps, and floating point (`isCloseTo` with tolerance — never `isEqualTo` on computed doubles); and no `assertEquals(expected, actual)` argument-order bugs.

**Q: What is a mock and when do you need one?** *bridge question*
A stand-in for a collaborator (repository, HTTP client) letting you test one unit in isolation — program its answers, verify interactions (Mockito: `when(...).thenReturn(...)`, `verify(...)`). Need it when the real collaborator is slow/nondeterministic/external. Balance: over-mocking couples tests to implementation; prefer real objects for cheap pure classes. (Deep dive in the Spring track, with Testcontainers for real-DB integration tests.)

**Q: Unit vs integration test?**
Unit: one class/behavior, collaborators faked, milliseconds, thousands of them. Integration: real pieces wired together (DB, HTTP, Spring context), slower, fewer — verifying the seams. The classic pyramid: many unit, some integration, few end-to-end.

## 6.3 — Logging & Javadoc

**Q: Why a logging framework instead of System.out.println?** ⭐
Levels filterable per environment *without code changes* (debug in dev, info in prod); automatic context (timestamp, thread, logger name); routing/rotation/aggregation by config; per-package verbosity. println has none of that and can't be silenced selectively.

**Q: What is SLF4J, exactly?** ⭐
A logging **facade**: code compiles against `slf4j-api` only; the concrete backend (logback, log4j2, JUL, slf4j-simple) is whatever's on the runtime classpath. Swap backends with zero code changes — dependency inversion applied to logging. Spring Boot ships SLF4J over logback preconfigured.

**Q: Why `log.debug("user {}", id)` instead of `"user " + id`?** ⭐
Placeholders defer message construction: if debug is disabled, no string is built, no toString called — concat pays those costs on every silenced call. In hot paths that's real money. (For an expensive computation, guard with `log.isDebugEnabled()` or pass a lambda-based API.)
*Follow-up: log an exception by passing it LAST — `log.error("save failed for {}", id, e)` — full stack trace included; never `e.printStackTrace()`.*

**Q: Name the log levels and when each applies.**
`error` — broken, needs action (failed payment, unreachable DB). `warn` — abnormal but functioning (retry succeeded, deprecated path, our large-expense alert). `info` — lifecycle milestones (started, config loaded, request served). `debug` — developer diagnostics. `trace` — extreme detail (per-iteration). Production default info; debug turned on per-package via config when investigating.

**Q: What belongs in Javadoc?**
The **contract**: what it does, `@param` constraints, `@return` meaning, `@throws` conditions — never the implementation (that's comments' job, and rarely needed). Write it on public API of library-style code; skip self-evident accessors. `{@link}`/`@code` for cross-references. Generated via `mvn javadoc:javadoc`; it's what your IDE shows on hover — write for that reader.

**Q: A production JVM is stuck/slow — first diagnostic steps?** *practical senior probe*
`jstack <pid>` (thread dump: deadlocks are auto-detected, look for many BLOCKED threads); repeat twice to see movement. `jmap -heap`/heap dump + Eclipse MAT for memory. `jstat -gcutil` for GC thrash. Thread dump first, always — it's free and usually names the culprit.

**Q: How does Java garbage collection work?** ⭐⭐
The GC automatically reclaims heap objects that are no longer **reachable** from GC roots (stack locals, static fields, live threads). It marks reachable objects by tracing from the roots, then reclaims the rest — so you never `free`/`delete`. Modern JVMs do this generationally, and safely reclaiming/moving objects requires briefly pausing app threads (stop-the-world).

**Q: What is generational GC (young vs old gen)?** ⭐⭐
It exploits the observation that *most objects die young*. New objects go in the **young gen** (Eden + two survivor spaces); a frequent, cheap **minor GC** collects it, copying survivors and promoting long-lived ones to the **old gen**. The old gen is collected by rarer, costlier **major/full GCs**. Splitting by age lets the common case (short-lived garbage) be reclaimed fast.

**Q: What does "reachable" mean, and why isn't an object collected just because it's unused?** ⭐
GC eligibility is by reachability, not usage: an object survives if any reference chain reaches it from a root, even if the program never touches it again. That's exactly a Java memory leak — still-reachable-but-unused objects that grow without bound (unbounded caches, static registries, un-removed listeners). Break the reference and it becomes collectable.

**Q: What is a stop-the-world pause?** ⭐
A pause where the GC halts all application threads so it can mark/move objects consistently. Minor GCs cause short pauses; full GCs can cause long ones. Minimizing STW pause time is the whole point of modern low-latency collectors.

**Q: Which garbage collectors does the JVM have, and when do you pick each?** ⭐⭐
Serial (tiny heaps/single core), Parallel/throughput (batch jobs, accepts longer pauses), **G1** (the default since Java 9 — region-based, aims at a pause-time target, good general server default), and **ZGC/Shenandoah** (concurrent, sub-millisecond pauses on very large heaps for latency-sensitive services). Choose by heap size and whether you optimize throughput or latency.

**Q: How do you diagnose and fix a GC/memory problem?** *senior probe*
Observe first: `jstat -gcutil` and GC logs (`-Xlog:gc*`) for pause/throughput, and a heap dump (`jmap` → Eclipse MAT) to see what fills old gen. A steadily rising old-gen after full GCs signals a leak — find the growing dominator set in MAT. Most "GC problems" are really allocation churn or leaks, so fix the code (bound caches, allocate less) before tuning `-Xmx`/collector flags. Measure before tuning.

**Q: Should you use `finalize()` to release resources?** ⭐
No — `finalize()` is deprecated (removed in 18) and unreliable (no guarantee it runs, or when). Use try-with-resources (`AutoCloseable`) or a `Cleaner`. GC manages *memory*; non-memory resources (files, sockets, locks) are your responsibility to close deterministically.
