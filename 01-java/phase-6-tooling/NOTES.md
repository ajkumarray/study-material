<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 5 · concurrency](../phase-5-concurrency/NOTES.md) | [Phase 7 · data ➡](../phase-7-data/NOTES.md)
<!-- /nav -->

# Phase 6 — Tooling & Professional Practice: Notes

*This phase leaves single-file scripts behind: everything lives in the `expense-tracker/` Maven project — which is also the seed of the capstone.*

## 6.1 — Maven

**What Maven is:** build tool + dependency manager + project convention, driven by one file (`pom.xml` — the Project Object Model). Its philosophy: **convention over configuration** — every Maven project has the same shape, so every Java developer is instantly at home:

```
project/
├── pom.xml
├── src/main/java          production code
├── src/main/resources     config bundled into the artifact
├── src/test/java          tests (never shipped)
└── target/                ALL build output — never commit (.gitignore it)
```

**GAV coordinates** identify every artifact in the world: `groupId` (reverse-domain namespace) : `artifactId` (name) : `version`. `-SNAPSHOT` suffix = under development, re-resolved on each build; releases are immutable. Your project has GAV; every dependency is referenced by GAV.

**Dependency management:** declared in `<dependencies>`, fetched from **Maven Central** into the local cache `~/.m2/repository` (shared by all projects), **transitively** — your deps' deps come along. Version conflicts resolved by "nearest wins" (closest to the root of the dependency tree — not highest!); inspect with `mvn dependency:tree`, the #1 debugging command for "wrong jar on classpath" and NoSuchMethodError archaeology.

**Scopes** control classpath visibility: `compile` (default — everywhere), `runtime` (needed to run, invisible at compile: JDBC drivers, logging backends), `test` (test classpath only: JUnit, AssertJ), `provided` (compiled against, supplied by the container: servlet API). Right-scoping keeps your published artifact's footprint honest.

**The lifecycle** — phases run *in order*, each implying all before it:
`validate → compile → test → package → verify → install → deploy`
So `mvn package` = compile + test + jar. `install` = copy to `~/.m2` (other local projects can depend on it); `deploy` = push to a remote repo. `mvn clean` wipes `target/` (a separate lifecycle — hence the idiom `mvn clean package`... but plain `package` is fine day-to-day; clean when weird stale-state issues appear).

**Plugins do all actual work** — the lifecycle is just slots that plugin *goals* bind into: compiler-plugin (compile), **surefire** (test), jar-plugin (package). **Pin plugin versions** — lived experience from this very lesson: Maven 3.8's default compiler plugin (3.1, from 2013) ignores `maven.compiler.release` and targets Java 5 → instant build failure. Pinning 3.13.0 fixed it; builds should be reproducible, not dependent on Maven's bundled defaults.

**Properties** (`<properties>`) centralize versions (`${junit.version}`); `maven.compiler.release=21` replaces the old source/target pair (and actually validates against the right API). Multi-module projects share a parent POM; Spring Boot's `spring-boot-starter-parent` is exactly that (track 02 preview). Gradle: the other build tool — Groovy/Kotlin DSL instead of XML, faster incremental builds; concepts (GAV, scopes→configurations, lifecycle→tasks) transfer directly.

## 6.2 — Unit testing with JUnit 5 + AssertJ

**The layout contract:** test class `X` lives at `src/test/java`, same package as `X`, named `XTest`. Surefire finds `*Test` classes during `mvn test`; test code and deps (scope `test`) never ship.

**JUnit 5 (Jupiter) core:**
- `@Test` — a test method; plain `void`, no `public` needed (JUnit 5 relaxed that).
- **Lifecycle:** fresh test-class instance *per test* + `@BeforeEach`/`@AfterEach` around each; `@BeforeAll`/`@AfterAll` (static) once per class. Fresh-instance-per-test is the isolation guarantee: no shared mutable state between tests.
- `@DisplayName` — human-readable names in reports; `@Nested` — inner classes grouping related tests into a tree (our `Validation` group); `@Disabled` — skip with reason; `@Tag` — filterable categories.
- **Parameterized tests** (`@ParameterizedTest`) kill copy-paste: `@ValueSource` (one arg), `@CsvSource` (rows of args), `@MethodSource` (arbitrary objects from a factory method). One test body, N cases, each reported individually.

**Structure & quality:**
- **Arrange–Act–Assert** in every test; helper factory methods keep the arrange section readable.
- **FIRST**: Fast, Independent (any order), Repeatable (no environment luck), Self-validating (no human inspection), Timely.
- Test the **unhappy paths** deliberately — validation, exceptions (`assertThatThrownBy(...).isInstanceOf(...).hasMessageContaining(...)`), empties.
- One logical assertion *concept* per test (multiple `assertThat` lines on one outcome is fine).

**AssertJ over bare JUnit assertions:** fluent, discoverable (type `.` and explore), rich failure messages, and it *understands types*: Optional (`isPresent`, `hasValueSatisfying`), collections (`extracting(Expense::description).containsExactly(...)` — project-then-assert, order-checked), maps (`containsEntry`), doubles (`isCloseTo(x, offset)` — floating point, lesson 1.2 echo). `assertEquals(expected, actual)` argument-order bugs simply disappear.

**What's next in testing (know the names):** Mockito — mock collaborators to test a unit in isolation; Testcontainers — real DB in Docker for integration tests; JaCoCo — coverage. All arrive naturally in the Spring Boot track.

## 6.3 — Logging (SLF4J) & Javadoc

**Why not `System.out.println`:** no levels (can't silence debug noise in prod without editing code), no timestamps/thread/context, no routing (file, rotation, aggregators), no per-package control. Logging frameworks give all of it via *configuration*.

**The facade pattern:** code depends only on **SLF4J** (`slf4j-api`); a concrete **backend** is chosen at runtime by what's on the classpath — `slf4j-simple` (stderr, our demo), **logback** (production default, XML-configurable), log4j2, JUL. Swap backends without touching a line of code — the same dependency-inversion idea as JDBC drivers. (Spring Boot ships SLF4J+logback preconfigured.)

**Usage canon:**
- One `private static final Logger log = LoggerFactory.getLogger(TheClass.class);` per class — logger names mirror package structure, enabling per-package level config.
- **`{}` placeholders, never string concat:** `log.debug("adding {}", expense)` — the message renders *only if the level is enabled*; concat pays the cost even when silenced. (Seen live: our `debug` lines printed nothing under the default `info` level.)
- **Levels:** `error` (broken, act now) > `warn` (suspicious, keeps working — our large-expense alert) > `info` (lifecycle milestones) > `debug` (diagnostic detail) > `trace` (firehose). Prod runs info+; dev flips to debug via config only.
- Log exceptions as the *last argument* — `log.error("save failed for {}", id, e)` — the backend prints the full stack trace; never `e.printStackTrace()`.

**Javadoc:** `/** ... */` on public types/methods → HTML API docs (`mvn javadoc:javadoc`). Document the **contract** (what/params/returns/throws — `@param`, `@return`, `@throws`, `{@link}`, `{@code}`), not the implementation. Rule of thumb: every `public` member of library-ish code; skip obvious getters. The records in our project show param docs on the record header.

**Debugging beyond println:** IDE breakpoints (+conditional breakpoints — break only when `amount > 10000`), step over/into, evaluate-expression; `jshell` for API experiments; `jstack <pid>` for thread dumps (5.2's deadlock detector); `jmap`/heap dumps for memory. The debugger is faster than println archaeology once breakpoints are muscle memory.

## 6.4 — JVM memory & Garbage Collection

Phase 2 said objects live on the **GC-managed heap** and dropping the last reference makes one *eligible* for collection. Here's the mechanism behind that — a top Java interview topic, and the thing you actually tune/observe with the tools above (`jstat -gcutil`, `jmap`, Eclipse MAT).

**JVM runtime memory areas:**
- **Heap** — shared across threads; holds all objects and arrays; managed by the GC. Where `OutOfMemoryError: Java heap space` comes from. Sized with `-Xms` (initial) / `-Xmx` (max).
- **Stack** — per-thread; frames with locals/params/return addresses; freed automatically on return; overflow → `StackOverflowError` (Phase 2).
- **Metaspace** — class metadata (replaced the old PermGen in Java 8); native memory, auto-grows; a ClassLoader leak on redeploy exhausts it (System Design Phase 7).
- Plus PC registers and native method stacks.

**What GC does:** automatic memory management — it finds objects that are no longer **reachable** (from GC *roots*: stack locals, static fields, active threads, JNI refs) and reclaims their space, so you never `free`/`delete`. "Unreferenced" isn't quite it — the precise rule is **reachability**: an object survives iff a chain of references reaches it from a root. This is why a *leak* in Java = objects still reachable but never used again (unbounded caches, static registries, un-removed listeners — System Design Phase 7).

**The generational hypothesis** — *most objects die young*. So the heap is split into generations and the GC works each differently:
- **Young generation** — where new objects are allocated: an **Eden** space + two **Survivor** spaces (S0/S1). A **Minor GC** (frequent, fast) collects Eden: survivors are copied to a survivor space, and objects that survive enough minor GCs are **promoted (tenured)** to the old gen. Because most objects die young, minor GC reclaims most of them cheaply.
- **Old (tenured) generation** — long-lived objects. A **Major/Full GC** collects it: rarer but more expensive, and the classic source of long pauses.
- **Stop-the-world (STW):** to move/reclaim safely, GC pauses all application threads. Minor GCs are short STW pauses; full GCs can be long. Reducing STW pause time is the goal of modern collectors.

**Copying/mark phases (how a collection runs):**
- **Mark** — trace from the roots, marking every reachable object.
- **Sweep / copy / compact** — reclaim unmarked space. Young gen uses **copying** (survivors copied out, Eden wiped wholesale — fast, and compacts as a side effect). Old gen uses **mark-sweep-compact** (compaction avoids fragmentation).

**The collectors (know the names + when):**
- **Serial** (`-XX:+UseSerialGC`) — single-threaded, tiny heaps / single-core (containers, small CLIs).
- **Parallel / Throughput** (`-XX:+UseParallelGC`) — multi-threaded, maximizes throughput, accepts longer pauses. Batch jobs.
- **G1** (`-XX:+UseG1GC`, the **default since Java 9**) — divides the heap into regions, collects incrementally aiming at a **pause-time target** (`-XX:MaxGCPauseMillis`). Balanced default for most server apps.
- **ZGC** (`-XX:+UseZGC`) / **Shenandoah** — low-latency, **concurrent** collectors targeting **sub-millisecond pauses** on very large (multi-GB/TB) heaps; most GC work happens *without* stopping the app. Latency-sensitive services.
- **Epsilon** — a no-op collector (allocates, never frees) for benchmarking/short-lived jobs.

**Escape analysis** — a JIT optimization related to GC: if the compiler proves an object never *escapes* a method, it can allocate it on the stack (or scalar-replace it), so it's freed for free on return — no heap/GC involvement at all.

**Tuning & observing (ties to the tooling above):**
- Right-size the heap (`-Xmx`/`-Xms`, often equal in prod to avoid resizing), pick the collector for your goal (throughput vs latency), and set a pause target for G1.
- **Observe** with `jstat -gcutil <pid>` (live gen occupancy + GC time — spot GC thrash), **GC logs** (`-Xlog:gc*`), and heap dumps (`jmap` → Eclipse MAT) to find what's filling old gen. A steadily rising old-gen *after* full GCs is the signature of a memory leak.
- Golden rule: **measure before tuning.** Most "GC problems" are really allocation problems (churning garbage) or leaks — fix the code (bounded caches, fewer short-lived allocations) before reaching for flags.

**`finalize()` is dead** (deprecated, removed in 18) — never a reliable cleanup hook (no guarantee it runs, or when). For releasing resources use **try-with-resources** (`AutoCloseable`, Phase 3) or a `Cleaner`; GC is for *memory*, not for files/sockets/locks (those are your job — System Design Phase 7).
