<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · oop](../phase-2-oop/NOTES.md) | [Phase 4 · modern java ➡](../phase-4-modern-java/NOTES.md)
<!-- /nav -->

# Phase 3 — Core APIs & Error Handling: Notes

## 3.1 — Exceptions

**The hierarchy (memorize):**

```
Throwable
├── Error                      JVM disasters: OutOfMemoryError, StackOverflowError.
│                              Don't catch; you can't meaningfully recover.
└── Exception
    ├── (checked)              IOException, SQLException, InterruptedException…
    │                          expected failures of the outside world;
    │                          compiler forces catch-or-declare
    └── RuntimeException       NullPointerException, IllegalArgument/State,
        (unchecked)            IndexOutOfBounds, ClassCast, Arithmetic…
                               programming bugs; fix code, don't catch
```

**Checked vs unchecked — the design intent:** checked = "this can fail no matter how correct your code is" (file missing, network down) → the compiler makes callers acknowledge it. Unchecked = "the caller broke the contract" (null where forbidden, bad index) → propagate to a top-level handler; catching them locally usually hides bugs. Modern trend (Spring, Kotlin, newer JDK APIs) leans unchecked — checked exceptions compose badly with lambdas/streams — but the JDK's I/O and JDBC remain checked, so you must master both.

**Mechanics:**
- When a statement throws, the rest of the `try` is skipped; the JVM walks up the call stack frame by frame until a matching `catch` is found (unwinding); no handler → thread dies with the stack trace.
- **Catch order:** subtypes before supertypes — the first matching block wins, and an unreachable catch is a *compile error*.
- **Multi-catch** `catch (A | B e)` — one block, several unrelated types; `e` is effectively final, its static type the common supertype.
- **`throw`** raises; **`throws`** declares. Only *checked* exceptions require declaring; listing unchecked ones is optional documentation.
- **`finally` always runs** — normal exit, caught throw, propagating throw; skipped only by `System.exit()`/JVM death/infinite loop. **Never `return`/`throw` inside `finally`:** it overrides the try's result and *swallows in-flight exceptions* (demo: `trickyValue()` returns 2, not 1).

**try-with-resources (Java 7+)** — the modern idiom:
- `try (Res r = ...) { }` auto-calls `close()` on everything declared, in **reverse order**, exception or not. Requires `AutoCloseable` (one method: `close()`).
- **Suppressed exceptions:** if body and `close()` both throw, the body's exception propagates and close()'s is attached via `addSuppressed` (readable via `getSuppressed()`). The old finally-based idiom *lost* the primary exception in that case — this is the senior-level talking point.
- Any resource-holding class you write should implement `AutoCloseable`.

**Exception translation & chaining:** each layer should throw exceptions meaningful at *its* abstraction level — catch `IOException` at the profile layer, rethrow `ProfileLoadException` **with the cause attached** (`new X(msg, cause)`). The stack trace then prints the full `Caused by:` chain. Never swallow the cause. (Spring institutionalizes this: `SQLException` → `DataAccessException` hierarchy.)

**Custom exceptions:** suffix `Exception`; extend `RuntimeException` (unchecked, usual modern choice) or `Exception` (checked, when callers genuinely can recover); always provide `(String message)` and `(String message, Throwable cause)` constructors; add structured fields (error code, entity id) when handlers need data, not string-parsing.

**Practices that show maturity:**
- Catch the *narrowest* type you can actually handle; let the rest fly.
- Don't use exceptions for control flow — they're for exceptional states, and filling in stack traces is expensive.
- Empty catch blocks are bug-hiding; at minimum log, usually rethrow.
- Validate early (`Objects.requireNonNull`, guard clauses) so failures happen close to their cause.
- `e.printStackTrace()` in production code → use a logger (Phase 6).

## 3.2 — The Collections Framework

**The map of the territory:** `Collection` → `List` (ordered, indexed, dupes), `Set` (unique), `Queue`/`Deque` (processing order); `Map` is a separate hierarchy (key→value). **Golden rule: declare the interface, choose the implementation** — `List<String> xs = new ArrayList<>()` keeps callers implementation-agnostic.

**Choosing an implementation — the decision table:**

| need | use | backing | key costs |
|---|---|---|---|
| ordered, indexed (default) | `ArrayList` | growable array (×1.5) | get O(1); end-add amortized O(1); middle insert O(n); contains O(n) |
| many head/middle inserts via iterator | `LinkedList` | doubly-linked nodes | get(i) O(n)!; node insert O(1); poor cache locality — rarely wins |
| uniqueness (default) | `HashSet` | HashMap under the hood | add/contains O(1) avg |
| uniqueness + insertion order | `LinkedHashSet` | hash + linked list | O(1), predictable iteration |
| uniqueness + sorted | `TreeSet` | red-black tree | O(log n); needs Comparable/Comparator; first/last/range queries |
| key→value (default) | `HashMap` | bucket array | get/put O(1) avg |
| …+ insertion order / LRU | `LinkedHashMap` | + linked list | LRU via `removeEldestEntry` |
| …+ sorted keys / range queries | `TreeMap` | red-black tree | O(log n), `firstKey`, `subMap` |
| FIFO queue / LIFO stack | `ArrayDeque` | circular array | O(1) both ends; replaces legacy `Stack` |
| priority order | `PriorityQueue` | binary heap | peek O(1), poll/offer O(log n); *not* sorted internally |

**HashMap internals (the star interview topic):** an array of buckets; index = `hash(key) & (capacity−1)` (capacity is a power of two). Collisions chain in a linked list; since Java 8 a bucket ≥8 entries (with table ≥64) *treeifies* into a red-black tree — worst case O(n) → O(log n), which also blunted hash-collision DoS attacks. Exceeding `capacity × loadFactor (0.75)` triggers resize (doubling + rehash). This is why `equals`/`hashCode` (2.5) matter and why keys must be immutable while stored.

**Fail-fast iterators:** collections carry a `modCount`; iterators snapshot it and throw `ConcurrentModificationException` on any structural change they didn't make — even single-threaded (the for-each remove bug). Correct removal: `removeIf` (best), or `Iterator.remove()`. It's best-effort bug detection, not a thread-safety guarantee — concurrent access needs `ConcurrentHashMap`/`CopyOnWriteArrayList` (Phase 5).

**Immutable collections:** `List.of/Set.of/Map.of` (Java 9) — compact, null-hostile, mutators throw `UnsupportedOperationException`. `List.copyOf` for defensive copies (2.2's leak, solved properly). Distinguish from `Collections.unmodifiableList` — a live *view* over a still-mutable backing list.

**Comparable vs Comparator:** `Comparable` = the type's one *natural* order (`compareTo` on the class — String, Integer, LocalDate have it). `Comparator` = external, ad-hoc orders, unlimited: `Comparator.comparing(Dev::exp).reversed().thenComparing(Dev::name)`. Contract: consistent with equals ideally; `TreeMap`/`TreeSet` use *compare* (not equals) for uniqueness.

**API gems:** `merge(k, 1, Integer::sum)` (word count one-liner), `getOrDefault`, `computeIfAbsent` (lazy multimaps: `map.computeIfAbsent(k, x -> new ArrayList<>()).add(v)`), `entrySet()` iteration (never iterate keySet then get), Deque's `peek/poll` (null on empty) vs `element/remove/pop` (throw).

**The `remove` overload trap:** on `List<Integer>`, `remove(2)` hits `remove(int index)`; `remove(Integer.valueOf(2))` hits `remove(Object)`. Autoboxing + overloading = the sneakiest bug in the lesson.

## 3.3 — Generics

**Purpose:** parameterized types move `ClassCastException`s from runtime to compile time and delete the casts. Raw types (`List` without `<>`) exist only for pre-2004 compatibility — never write them (the compiler's "unchecked" warnings are the alarm).

**Vocabulary:** generic class `class Pair<A, B>`; generic method `<T> T firstOf(List<T>)` (own parameter, inferred per call); bound `<T extends Comparable<T>>` — the bound is what *grants access* to the bounded type's methods inside the code. Multiple bounds: `<T extends A & B>` (class first, then interfaces). Conventions: T/E/K/V/R.

**Invariance & wildcards (the boss level):** `List<Integer>` is NOT a `List<Number>` — if it were, `nums.add(3.14)` would poison an int list. Wildcards restore safe flexibility:
- `? extends Number` — unknown *subtype*: safe to **read** as Number, unsafe to add (exact type unknown) → **producer**.
- `? super Integer` — unknown *supertype*: safe to **write** Integers, reads come out as Object → **consumer**.
- **PECS: Producer Extends, Consumer Super** (JDK: `copy(List<? super T> dest, List<? extends T> src)`).
- Arrays, by contrast, are *covariant* (`Integer[]` is-a `Number[]`) and pay with runtime `ArrayStoreException` — generics fixed that design mistake at compile time.

**Type erasure:** after compilation the type parameter is gone — erased to `Object`/the bound; casts are inserted at use sites. One runtime class per generic type (`new ArrayList<String>().getClass() == new ArrayList<Integer>().getClass()`). Consequences: no `new T()`, no `T.class`, no `instanceof List<String>`, no `new T[n]`, and `f(List<String>)`/`f(List<Integer>)` can't overload (same erasure). Why erasure: bytecode/backward compatibility with pre-generics Java. (Reflection can recover *declared* generic types on fields/methods — but not on the erased runtime objects.)

## 3.4 — File I/O (NIO.2)

**Two generations:** `java.io` (`File`, `FileReader` — read it in legacy code) vs `java.nio.file` (Java 7+: `Path` + `Files` — write this). **Mental model: `Path` = a *name* for a location (may not exist); `Files` = the static toolbox operating on paths.**

**Path algebra:** `Path.of("a", "b")`, `resolve` (join), `getParent`/`getFileName`, `toAbsolutePath`, `normalize` (folds `..`), `relativize`. Platform-independent separators handled for you.

**Reading — pick by size:** `readString`/`readAllBytes` (small — slurp), `readAllLines` (medium — List), `Files.lines` (large — *lazy stream*, holds an open handle → **always try-with-resources**), `newBufferedReader` (manual control). **Writing:** `writeString`, `write(path, lines)`; `StandardOpenOption.APPEND`/`CREATE`/`TRUNCATE_EXISTING` for modes. Text defaults to UTF-8.

**Bytes vs chars:** streams (`InputStream`/`OutputStream`) for binary; readers/writers + charset for text. Buffering exists because syscalls are expensive — wrap raw streams; the `Files.new*` helpers come pre-buffered.

**Management:** `copy`/`move` (+ `REPLACE_EXISTING`, `ATOMIC_MOVE`), `delete` (throws if absent) vs `deleteIfExists`, `createDirectories` (mkdir -p), `exists`/`isRegularFile`/`isDirectory`, `size`. Directory listing: `list` (one level) vs `walk` (recursive) — both lazy streams to close. Deleting a tree = walk sorted `reverseOrder()` (deepest first — dirs must be empty).

**Checked exceptions in lambdas:** lambdas can't throw checked exceptions, so wrap: `catch (IOException e) { throw new UncheckedIOException(e); }` — 3.1's chaining pattern applied. This friction is *why* modern APIs lean unchecked.
