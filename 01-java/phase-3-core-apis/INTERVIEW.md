<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · oop](../phase-2-oop/NOTES.md) | [Phase 4 · modern java ➡](../phase-4-modern-java/NOTES.md)
<!-- /nav -->

# Phase 3 — Core APIs: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 3.1 — Exceptions

**Q: Explain the exception hierarchy.** ⭐
`Throwable` splits into `Error` (JVM-level: `OutOfMemoryError`, `StackOverflowError` — don't catch) and `Exception`. `Exception`'s subtree is *checked* (compiler-enforced catch-or-declare: `IOException`, `SQLException`) except for the `RuntimeException` branch, which is *unchecked* (`NullPointerException`, `IllegalArgumentException`, `IndexOutOfBoundsException`).

**Q: Checked vs unchecked — what's the philosophy?** ⭐⭐
Checked: recoverable failures of the environment — can happen no matter how correct the code is (missing file, dead network); the compiler forces callers to plan for them. Unchecked: contract violations/bugs — the fix is correcting code, not catching. Know the debate too: checked exceptions compose terribly with lambdas/streams, so modern APIs (Spring, newer JDK) lean unchecked; interviewers like hearing both sides.
*Follow-up: "Name an unchecked exception you'd deliberately throw" — `IllegalArgumentException`/`IllegalStateException` for precondition violations.*

**Q: `throw` vs `throws`?**
`throw e;` — the statement that raises. `throws IOException` — the method-signature declaration meaning "this may escape; caller must handle or redeclare." Only checked exceptions require declaration.

**Q: Does `finally` always execute?** ⭐
Yes — on normal completion, caught exceptions, and propagating exceptions alike. Exceptions to "always": `System.exit()`, JVM crash, infinite loop/never-ending try, daemon-thread death at shutdown.
*Follow-up trap: "try returns 1, finally returns 2 — result?" — 2. finally's return hijacks the try's, and worse, it silently swallows any in-flight exception. Never return/throw from finally.*

**Q: What is try-with-resources and why is it better than finally-close?** ⭐
`try (BufferedReader r = ...) { }` auto-closes every declared `AutoCloseable`, reverse order, exception or not. Superior because: no null-check/close boilerplate, impossible to forget, and it handles the double-throw case correctly — if body and `close()` both throw, the *body's* exception propagates with close()'s attached as **suppressed** (`getSuppressed()`); the old idiom lost the primary exception. That suppressed-exception detail is the senior-level differentiator.

**Q: Rules for multiple catch blocks?**
First match wins → order narrowest-to-widest; a catch after its supertype is *unreachable* → compile error. Multi-catch `catch (A | B e)` merges unrelated types (parameter is effectively final).

**Q: What is exception chaining / translation?** ⭐
Each layer throws exceptions at its own abstraction level: catch the low-level one, rethrow a domain one **with the original as cause** — `throw new ProfileLoadException(msg, ioEx)`. Stack traces show the full `Caused by:` chain. Swallowing the cause = destroying the evidence. Spring's whole persistence story does this: `SQLException` → unchecked `DataAccessException`.

**Q: How do you design a custom exception?**
Extend `RuntimeException` (default) or `Exception` (only when recovery is realistic); name ends `Exception`; ship `(message)` and `(message, cause)` constructors; add structured fields (error code, id) so handlers don't parse messages.

**Q: Why not use exceptions for control flow?**
Semantics (they signal *exceptional* states, not expected branches) and cost — `fillInStackTrace` walks the whole stack at construction. Expected outcomes deserve return types: `Optional`, sealed result types (2.6), or plain booleans.

**Q: What happens to an uncaught exception?**
It unwinds the whole stack; the thread's `UncaughtExceptionHandler` runs (default: print trace to stderr) and *that thread* dies. In `main`, the JVM exits nonzero. In servers, the framework's dispatcher catches at the top per request — one bad request doesn't kill the process.

**Q: Can you catch `Error` or `Throwable`? Should you?**
Syntactically yes, practically no — after `OutOfMemoryError` the JVM can't reliably run your handler. Legitimate exceptions: last-ditch logging at a top-level boundary, then die fast. Catching `Throwable` and continuing is an anti-pattern interviewers probe deliberately.

**Q: `final`, `finally`, `finalize`?** ⭐ *old classic, still asked*
`final` — keyword: unchangeable variable / unoverridable method / unextendable class. `finally` — the always-runs block. `finalize()` — deprecated (removed in 18) GC hook; the modern answer is try-with-resources/`Cleaner`. Bonus points for saying "finalize is dead, and knowing that is the real answer."

## 3.2 — Collections Framework

**Q: ArrayList vs LinkedList?** ⭐⭐
ArrayList: growable array — `get(i)` O(1), end-append amortized O(1) (grows ×1.5 + copy), middle insert O(n). LinkedList: doubly-linked nodes — `get(i)` O(n) (walks!), O(1) insert *at a known node*. The senior answer: **ArrayList wins almost always** — contiguous memory means cache hits; LinkedList's Big-O advantages are eaten by pointer-chasing and per-node allocation. LinkedList's honest niche: Deque operations at both ends.

**Q: How does HashMap work internally?** ⭐⭐ *the most-asked collections question*
Bucket array; index = `hash & (capacity−1)` (power-of-two capacity; the hash is spread by XORing high bits). Collisions chain as a linked list; Java 8+: bucket ≥8 (table ≥64) becomes a red-black tree — O(n) worst case → O(log n), also killing collision-DoS attacks. Passing `capacity × loadFactor (0.75)` doubles + rehashes. `get`: hash → bucket → walk comparing hash then equals.
*Follow-ups: "Why must keys be immutable?" — mutate a key's hash-relevant field and lookups probe the wrong bucket: entry lost (2.5). "Why 0.75?" — space/collision trade-off. "null keys?" — one allowed, bucket 0 (Hashtable/ConcurrentHashMap: none).*

**Q: HashMap vs Hashtable vs ConcurrentHashMap?** ⭐
HashMap: unsynchronized, fast, single-threaded (or externally synchronized). Hashtable: legacy, every method synchronized — dead, mention only to reject. ConcurrentHashMap: modern concurrent map — lock-striped/CAS, no global lock, no nulls; the real answer for shared maps (details Phase 5).

**Q: What is a fail-fast iterator?** ⭐
Collections track a `modCount`; the iterator snapshots it and throws `ConcurrentModificationException` on any structural change it didn't make — including single-threaded for-each removal. Fixes: `removeIf`, or `Iterator.remove()`. It's best-effort debugging aid, not thread safety.
*Follow-up: fail-safe alternatives — `CopyOnWriteArrayList`, `ConcurrentHashMap` iterate over snapshots/weakly-consistent views instead.*

**Q: HashSet vs LinkedHashSet vs TreeSet?**
All unique. HashSet: O(1), arbitrary order (it's a HashMap with dummy values). LinkedHashSet: O(1) + insertion-order iteration. TreeSet: red-black tree — O(log n), sorted, range queries (`first/last/headSet/subSet`), elements must be Comparable or given a Comparator.
*Follow-up trap: TreeSet decides "duplicate" by `compareTo == 0`, not equals.*

**Q: Comparable vs Comparator?** ⭐
Comparable: the type's own *natural* order — `compareTo` implemented once on the class. Comparator: external strategy — unlimited ad-hoc orders, composable (`comparing(...).reversed().thenComparing(...)`), no access to the class needed. Sorting uses natural order by default or an explicit comparator.

**Q: How do you remove elements while iterating?** ⭐
`collection.removeIf(predicate)` (clearest), or explicit `Iterator` + `it.remove()`. For-each + `collection.remove` throws `ConcurrentModificationException`. (Streams alternative: filter into a new collection.)

**Q: `List.of()` vs `Collections.unmodifiableList()` vs `List.copyOf()`?**
`List.of`: new immutable list from elements (null-hostile). `unmodifiableList`: read-only *view* — the backing list can still change beneath it. `copyOf`: immutable *snapshot* copy. Defensive-copy returns want `copyOf`.

**Q: Why prefer ArrayDeque over Stack/LinkedList for stacks and queues?**
`Stack` is legacy: extends Vector — synchronized and, worse, *is a List* (you can insert mid-"stack" — broken abstraction). ArrayDeque: circular array, O(1) both ends, cache-friendly, honest LIFO/FIFO API (`push/pop/offer/poll`).

**Q: `list.remove(2)` vs `list.remove(Integer.valueOf(2))` on a `List<Integer>`?** *trap*
Overload resolution prefers the exact primitive match: `remove(int index)` removes *position* 2; the boxed call hits `remove(Object)` and removes the *value* 2. Autoboxing + overloading is the sneakiest collections bug.

## 3.3 — Generics

**Q: Why do generics exist?**
Compile-time type safety + no casts: pre-generics collections held raw Objects, and every read was a cast that could throw `ClassCastException` at runtime. Generics surface those errors at compile time and document intent in the signature.

**Q: What is type erasure?** ⭐⭐
Generics are compile-time only: the compiler checks, then erases type parameters to `Object` (or the bound) and inserts casts at use sites. One runtime class per generic type — `List<String>` and `List<Integer>` share it. Chosen for backward compatibility with pre-2004 bytecode.
*Follow-up: consequences — no `new T()`, no `T.class`, no `instanceof List<String>`, no generic arrays (`new T[n]`), overloads differing only in type argument collide (same erasure).*

**Q: Explain PECS.** ⭐⭐
**Producer Extends, Consumer Super.** Reading from a structure → `? extends T` (it *produces* Ts; you can read but not safely add). Writing into it → `? super T` (it *consumes* Ts; you can add Ts but reads come back as Object). JDK exemplar: `Collections.copy(List<? super T> dest, List<? extends T> src)`.

**Q: Why isn't `List<Integer>` a subtype of `List<Number>`?** ⭐
Generics are *invariant*: were it allowed, `List<Number> nums = ints; nums.add(3.14);` would poison the int list — a compile-time hole. Wildcards express the safe relationships explicitly.
*Follow-up: arrays ARE covariant (`Integer[]` is-a `Number[]`) and pay at runtime with `ArrayStoreException` — generics deliberately fixed that mistake.*

**Q: What's a bounded type parameter for?**
`<T extends Comparable<T>>` restricts T *and thereby grants access* to the bound's methods inside the code (`compareTo` becomes callable). Multiple bounds: `<T extends A & B>`, class first.
*Follow-up: the real JDK signature `<T extends Comparable<? super T>>` — lets a subtype use its parent's compareTo; PECS applied to bounds.*

**Q: Raw types — what happens if you use `List` without `<>`?**
All generic checking disabled for that reference — "unchecked" warnings, heap pollution risk, runtime `ClassCastException`s. Exists purely for pre-generics compatibility. Never write raw types; the diamond `<>` costs nothing.

**Q: Generic method vs generic class?**
Class-level parameter lives with the instance (`Pair<A,B>`); a generic method declares its own (`static <T> T firstOf(List<T>)`) inferred per call — usable in non-generic classes, and static methods *must* declare their own (they can't see class parameters — no instance).

## 3.4 — File I/O

**Q: `java.io.File` vs NIO.2 `Path`/`Files`?**
Legacy `File` mixes the name with the operations and returns booleans on failure (silent!). NIO.2 (Java 7): `Path` is the location value, `Files` the toolbox; failures throw `IOException` with detail, plus symlink handling, attributes, atomic moves, and stream-based directory walking. Write NIO.2; read `File` in old code.

**Q: How do you read a file — and when do you pick which method?** ⭐
Small: `Files.readString` / `readAllBytes`. Medium: `readAllLines` → List. Huge: `Files.lines` — a *lazy* stream that never loads the whole file; it holds an open handle so it **must** be in try-with-resources. Manual/mixed: `newBufferedReader`.
*Follow-up: "Why buffer at all?" — every unbuffered read is a syscall; buffering batches them (orders of magnitude).*

**Q: Byte streams vs character streams?**
`InputStream`/`OutputStream` move raw bytes (binary: images, zips). `Reader`/`Writer` move *characters* through a charset decode/encode. Text without an explicit charset historically meant platform-default bugs; NIO.2 helpers default UTF-8 (and Java 18 made UTF-8 the default everywhere).

**Q: How do you delete a non-empty directory?**
No recursive delete in the JDK: `Files.walk` sorted `Comparator.reverseOrder()` (deepest first — directories must be empty), delete each. Interviewers use it to check you actually know `walk` returns a closeable lazy stream.

**Q: How do checked exceptions interact with lambdas/streams?** *modern probe*
Lambdas implementing standard functional interfaces can't throw checked exceptions — wrap and rethrow (`UncheckedIOException` exists precisely for this), or extract a method. This friction is a main argument in the checked-vs-unchecked debate (3.1).
