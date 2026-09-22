<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · oop](../phase-2-oop/NOTES.md) | [Phase 4 · modern java ➡](../phase-4-modern-java/NOTES.md)
<!-- /nav -->

# Phase 3 — Core APIs: Interview Q&A

Answer out loud before reading. ⭐ = asked constantly.

## 3.1 — Exceptions

**Q: Explain the exception hierarchy.** ⭐

`Throwable` is the root of everything the JVM can throw and catch. It splits into two branches: `Error`, which represents JVM-level disasters (`OutOfMemoryError`, `StackOverflowError`) that application code generally can't meaningfully recover from and shouldn't catch, and `Exception`. `Exception`'s subtree is entirely **checked** — meaning the compiler forces callers to `catch` or `throws`-declare them (`IOException`, `SQLException`) — *except* for the `RuntimeException` branch specifically, which is **unchecked** (`NullPointerException`, `IllegalArgumentException`, `IndexOutOfBoundsException`, `ClassCastException`, `ArithmeticException`).

**Q: Checked vs unchecked — what's the design philosophy behind the split?** ⭐⭐

Checked exceptions model failures that can happen *no matter how correct your code is* — the environment fails around you regardless of logic quality (a file that disappeared, a network connection that dropped). Since a caller genuinely needs a plan for these, the compiler enforces one. Unchecked exceptions model contract violations — programming bugs, essentially (a `null` where the contract forbids it, an out-of-range index). The fix there is correcting the code, not writing a `catch` block around the symptom.

It's worth knowing the debate too, since interviewers like hearing both sides: checked exceptions compose badly with modern functional-style code — a lambda implementing `Function<T, R>` literally cannot throw a checked exception without wrapping it, because the interface's method signature doesn't declare one. This friction is a major reason Spring and much of the modern JDK ecosystem lean toward unchecked exceptions, even though the JDK's own I/O and JDBC layers remain checked for historical/compatibility reasons.

*Follow-up: "Name an unchecked exception you'd deliberately throw yourself, and why."* `IllegalArgumentException` or `IllegalStateException` for precondition/invariant violations — e.g. `if (username.length() < 3) throw new IllegalArgumentException(...)`. These are genuine caller bugs, so unchecked is the right choice; forcing every caller to `catch` or declare a checked exception for "you passed a bad argument" would be poor API design.

**Q: What's the difference between `throw` and `throws`?**

`throw e;` is a **statement** — the actual point in code where an exception object is raised. `throws IOException` in a method signature is a **declaration**, meaning "this method may let this exception escape; the caller must handle it or redeclare it." Only checked exceptions require declaration to compile; you can optionally list unchecked exceptions in a `throws` clause too, but that's pure documentation with no compiler enforcement behind it.

**Q: Does `finally` always execute?** ⭐

Yes — on normal completion of the `try`, on a caught exception, and even while an exception is actively propagating past the `try`/`catch`. The practical exceptions to "always" are `System.exit()`, a JVM crash, or the thread simply never reaching the `finally` (an infinite loop inside `try`, or the JVM being killed at the OS level).

*Follow-up trap: "`trickyValue()` returns 1 in the try and 2 in a finally — what does the caller actually see?"*

```java
static int trickyValue() {
    try {
        return 1;
    } finally {
        return 2;    // hijacks the try's return AND swallows any in-flight exception
    }
}
```

The answer is **2**. A `return` (or `throw`) inside `finally` completely overrides whatever the `try` block was about to do — including silently swallowing an exception that was mid-propagation. This is exactly why the rule is: never `return`/`throw` from inside a `finally` block.

**Q: What is try-with-resources, and why is it better than the old `finally`-close pattern?** ⭐

`try (BufferedReader r = ...) { }` auto-closes every resource declared in the parentheses, in reverse declaration order, whether the `try` block completes normally or exceptionally — as long as each resource implements `AutoCloseable`. It's superior to hand-writing `finally { if (r != null) r.close(); }` for two reasons: first, it's impossible to forget, since the compiler generates the close calls for you; second, and more subtly, it correctly handles the case where **both** the `try` body and `close()` throw. The body's exception is the one that propagates to the caller, and `close()`'s exception is attached to it as a **suppressed exception**, retrievable via `e.getSuppressed()`. The old manual `finally`-close idiom got this wrong: an exception thrown from inside `finally`'s `close()` call would silently *overwrite* the real exception from the body, permanently losing the original evidence. This suppressed-exception detail is a genuine senior-level differentiator in an interview.

**Q: What are the rules for multiple `catch` blocks, and what's multi-catch?**

First matching block wins, so blocks must be ordered from most specific (subtype) to least specific (supertype) — putting a broader catch before a narrower one is a **compile error**, because the compiler can prove the narrower block is unreachable. **Multi-catch** (`catch (A | B e)`, Java 7+) lets one block handle several *unrelated* exception types identically without duplicating the handler body; the caught variable's static type becomes the common supertype of the alternatives, and it's implicitly effectively final (you can't reassign it).

**Q: What is exception chaining / translation, and why does it matter?** ⭐

Each architectural layer should throw exceptions meaningful **at its own level of abstraction** — a persistence layer shouldn't leak `SQLException` up into business-logic code that has no concept of SQL. The pattern: catch the low-level exception, and rethrow a domain-appropriate one **with the original attached as the cause** — `throw new ProfileLoadException(msg, ioEx)`. The resulting stack trace shows the full `Caused by:` chain, so you never lose the root cause while debugging. Swallowing the cause (throwing a new exception with just a message, discarding the original) destroys real evidence and is a common, frustrating anti-pattern to debug around. This is exactly what Spring's persistence layer does at scale: every checked `SQLException` gets translated into Spring's own unchecked `DataAccessException` hierarchy.

**Q: How do you design a custom exception class?**

Extend `RuntimeException` by default (unchecked — the more common modern choice), or `Exception` only when callers genuinely have a realistic recovery path and should be forced to consider it. Name it ending in `Exception`. Always provide both a `(String message)` constructor and a `(String message, Throwable cause)` constructor, so it plays cleanly with exception chaining. Add structured fields (an error code, an entity id) when a handler needs to act on specific data programmatically — don't make callers parse your message string.

**Q: Why avoid using exceptions for control flow?**

Two reasons: semantically, exceptions are meant to signal *exceptional* states, not expected branches of ordinary logic — using them for the latter makes code harder to reason about. Practically, they're genuinely expensive to construct: `fillInStackTrace()` walks the entire call stack at construction time, regardless of whether anything ever reads that stack trace. Expected, non-exceptional outcomes deserve real return types — `Optional`, a sealed result type, or a plain boolean — not a thrown-and-caught exception on the happy path.

**Q: What happens to a completely uncaught exception?**

It unwinds the entire call stack of the thread it occurred on. The thread's `UncaughtExceptionHandler` runs (the default implementation prints the stack trace to `stderr`), and *that thread* then dies. If it's the `main` thread, the JVM process exits with a nonzero status. In server frameworks, the request-dispatching layer typically installs a top-level handler per request, so one bad request's uncaught exception doesn't take down the whole process — only that one request fails.

**Q: Can you catch `Error` or even `Throwable` directly? Should you?**

Syntactically, yes — nothing stops you from writing `catch (Throwable t)`. Practically, almost never a good idea: after something like `OutOfMemoryError`, the JVM may not be in a reliable enough state for your handler code to run correctly at all. The one legitimate use is last-ditch logging at a genuine top-level boundary before letting the process die fast and cleanly. Catching `Throwable` broadly and then attempting to continue normal operation is an anti-pattern interviewers specifically probe for, because it masks real, unrecoverable failures as if they were routine.

**Q: `final`, `finally`, `finalize` — what's the difference?** ⭐ *old classic, still asked*

`final` is a keyword marking a variable unreassignable, a method unoverridable, or a class unextendable. `finally` is the always-runs block attached to a `try`. `finalize()` was a deprecated (and removed in Java 18) garbage-collector hook meant to run cleanup before an object was reclaimed — it was unreliable (no guaranteed timing, sometimes never called at all) and the modern answer is try-with-resources or `java.lang.ref.Cleaner`. Knowing that `finalize` is effectively dead, and *why* it was removed, is often the more valuable half of this answer.

## 3.2 — Collections Framework

**Q: `ArrayList` vs `LinkedList` — which should you actually use?** ⭐⭐

`ArrayList` is backed by a growable array: `get(i)` is O(1), appending at the end is amortized O(1) (occasionally growing ×1.5 and copying), but inserting into the middle is O(n) (everything after the insertion point shifts). `LinkedList` is backed by doubly-linked nodes: `get(i)` is O(n) — it has to walk node-by-node from an end — while insertion/removal *at an already-known node* is O(1). The senior-level answer: **`ArrayList` wins almost always in practice**, even for supposedly "`LinkedList`-favorable" workloads, because contiguous array memory means CPU cache hits dominate real-world performance, while `LinkedList`'s per-node heap allocations and pointer-chasing eat its theoretical Big-O advantage. `LinkedList`'s one honest niche is `Deque` usage at both ends — though `ArrayDeque` usually beats it there too.

**Q: How does `HashMap` work internally?** ⭐⭐ *the most-asked collections question*

A `HashMap` holds a bucket array; a key's bucket index is `hash(key) & (capacity − 1)` (capacity is always kept as a power of two so this bitmask works, and the hash is further spread by XOR-folding its high bits into the low bits before the mask, reducing clustering). Keys that collide into the same bucket chain as a small linked list. Since Java 8, once a single bucket accumulates 8 or more entries *and* the whole table has at least 64 buckets, that bucket **treeifies** into a small red-black tree, dropping worst-case per-bucket lookup from O(n) to O(log n) — which also incidentally defeats a class of deliberate hash-collision denial-of-service attack. Once the number of stored entries exceeds `capacity × loadFactor` (default `0.75`), the table **resizes**: capacity doubles and every entry gets rehashed into its new bucket. A `get` operation: hash the key, find the bucket, walk its chain comparing `hashCode` first (cheap) then `equals` (only if hashes match) until it finds the entry.

*Follow-up: "Why must keys be immutable while stored in a map?"* Because the bucket a key lives in is determined by its hash at insertion time. Mutate a field that participates in that key's `hashCode()` after insertion, and future lookups compute a *different* bucket index than the one the entry actually sits in — the entry is still technically in the map, but effectively lost; `get` will return `null` even though the key "should" be there.

*Follow-up: "Why 0.75 specifically for the load factor?"* It's a space/collision trade-off: a higher load factor packs more entries per bucket before resizing (saves memory, more collisions, slower lookups); a lower one resizes more eagerly (uses more memory, fewer collisions, faster lookups). 0.75 is the JDK's empirically chosen default balance.

*Follow-up: "Can `HashMap` hold a null key or null values?"* One `null` key is allowed (it's specially routed to bucket 0), and any number of `null` values. `Hashtable` and `ConcurrentHashMap`, by contrast, allow **no** null keys or values at all.

**Q: `HashMap` vs `Hashtable` vs `ConcurrentHashMap`?** ⭐

`HashMap` is unsynchronized and fast — the right default for single-threaded use or when you handle synchronization externally. `Hashtable` is a legacy class where every method is synchronized individually, which is both slower and still not actually safe for compound operations (check-then-act races) — effectively dead; mention it only to explicitly reject it. `ConcurrentHashMap` is the modern answer for a map genuinely shared across threads: it uses lock striping/CAS internally rather than one global lock, allows high concurrent throughput, and (like `Hashtable`) disallows `null` keys/values. Full mechanics are covered in Phase 5.

**Q: What is a fail-fast iterator, and what triggers `ConcurrentModificationException`?** ⭐

Every standard collection tracks a `modCount` (a structural-modification counter). An iterator snapshots that count when it's created and checks it on each `next()`/`hasNext()` call; if the collection was structurally changed by anything other than the iterator's own `remove()`, it throws `ConcurrentModificationException` — even in purely single-threaded code, like removing from a `List` mid for-each loop. Fixes: `collection.removeIf(predicate)` (cleanest), or an explicit `Iterator` with `it.remove()`.

*Follow-up: "Is this a thread-safety mechanism?"* No — it's explicitly a **best-effort bug-detection aid**, not a guarantee. Two threads racing unsynchronized on a plain `HashMap` can corrupt its internal structure without ever throwing this exception at all. Genuinely thread-safe alternatives are `CopyOnWriteArrayList` and `ConcurrentHashMap`, which iterate over a snapshot or a weakly-consistent live view instead of failing fast.

**Q: `HashSet` vs `LinkedHashSet` vs `TreeSet`?**

All three guarantee uniqueness. `HashSet` (internally a `HashMap` with dummy values) is O(1) average with arbitrary iteration order. `LinkedHashSet` adds O(1) operations plus predictable insertion-order iteration. `TreeSet` is a red-black tree — O(log n) operations, always iterates in sorted order, and supports range queries (`first()`, `last()`, `headSet()`, `subSet()`); its elements must implement `Comparable` or you must supply a `Comparator`.

*Follow-up trap: "Does `TreeSet` use `equals()` to decide duplicates?"* No — it uses `compareTo() == 0` (or the supplied `Comparator` returning 0). Two elements that are `compareTo`-equal but `equals`-unequal will silently collapse into a single entry in a `TreeSet`, which is a genuine, easy-to-miss bug if a custom `Comparable`/`Comparator` doesn't stay consistent with `equals`.

**Q: `Comparable` vs `Comparator`?** ⭐

`Comparable` defines a type's single, intrinsic **natural** order — you implement `compareTo` once, directly on the class. `Comparator` is an **external** strategy object — you can define as many different orderings as you like without ever touching the target class, and they compose cleanly (`Comparator.comparing(Dev::exp).reversed().thenComparing(Dev::name)`). `Collections.sort`/`List.sort` use the natural order by default (requires `Comparable`) or an explicit `Comparator` if one is supplied.

**Q: How do you safely remove elements while iterating over a collection?** ⭐

`collection.removeIf(predicate)` is the clearest modern approach. The alternative is an explicit `Iterator` with `it.remove()` inside a `while (it.hasNext())` loop — the iterator's own `remove()` is specifically exempt from the fail-fast check since it updates `modCount` correctly itself. A plain for-each loop combined with `collection.remove(x)` throws `ConcurrentModificationException`. (For a functional style, an alternative is filtering into a brand-new collection with a stream instead of mutating in place.)

**Q: `List.of()` vs `Collections.unmodifiableList()` vs `List.copyOf()`?**

`List.of(...)` builds a genuinely immutable list directly from the given elements (and is null-hostile — passing `null` throws). `Collections.unmodifiableList(list)` returns a **live view** wrapping a still-mutable backing list — the view itself rejects mutation, but if you hold a reference to the original backing list, changes there are still visible through the "unmodifiable" view, so it doesn't actually guarantee immutability. `List.copyOf(existing)` takes an immutable **snapshot** — the right choice when you want to hand out a defensive copy that can never change again, regardless of what happens to the source.

**Q: Why prefer `ArrayDeque` over the legacy `Stack` (or `LinkedList`) for stack/queue usage?**

`Stack` is a legacy class that extends `Vector` — every method is synchronized (unnecessary overhead in single-threaded code), and worse, since it *is* a `List`, nothing prevents inserting into the "middle" of what's supposed to be a strict LIFO structure, breaking the abstraction it's meant to enforce. `ArrayDeque` is a circular array offering O(1) operations at both ends, better cache locality than a linked structure, and an honest, purpose-built LIFO/FIFO API (`push`/`pop` for stack use, `offer`/`poll` for queue use).

**Q: What's the classic `list.remove(2)` vs `list.remove(Integer.valueOf(2))` trap on a `List<Integer>`?** *trap*

Overload resolution prefers the exact primitive match, so `list.remove(2)` — a plain `int` literal — resolves to `remove(int index)` and removes the element at **position** 2. To remove by **value** instead, you must force autoboxing explicitly: `list.remove(Integer.valueOf(2))` resolves to `remove(Object o)` and removes the element **equal to** 2, wherever it is. Autoboxing colliding with overload resolution is widely regarded as the sneakiest bug shape in the Collections API.

## 3.3 — Generics

**Q: Why do generics exist?**

Compile-time type safety, plus eliminating casts. Before generics, collections held raw `Object`s, and every read required an explicit cast that could throw `ClassCastException` at runtime, potentially far away from the actual mistake. Generics surface those type errors at compile time instead, and let a method or class's signature document its intent directly (`List<String>` says exactly what it holds; a raw `List` says nothing).

**Q: What is type erasure?** ⭐⭐

Generics are **compile-time only**. After the compiler finishes checking your code against declared type parameters, it erases those parameters — replacing them with `Object` (or their bound, if one is declared) — and inserts the necessary casts automatically at each use site. The practical effect: there is exactly **one** runtime class per generic class; `List<String>` and `List<Integer>` share the identical `Class` object. It was chosen specifically for backward compatibility with pre-2004 (pre-Java 5) bytecode.

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass());   // true
```

*Follow-up: "What are the practical consequences of erasure?"* No `new T()` (the JVM doesn't know what `T` is at runtime to instantiate it), no `T.class` (no runtime `Class<T>` object exists), no `instanceof List<String>` (only bare `instanceof List` compiles), no generic arrays (`new T[n]` doesn't compile — arrays need to know their element type at runtime for store-checking, which erasure removes), and overloads differing only by type argument collide at the bytecode level (`f(List<String>)` and `f(List<Integer>)` erase to the identical signature and can't coexist).

**Q: Explain PECS.** ⭐⭐

**Producer Extends, Consumer Super.** If a parameterized structure only **produces** values for you to read (you never write into it), declare it with `? extends T` — you get read access typed as `T`, but write access is unsafe since the exact concrete type is unknown, so it's blocked. If a structure only **consumes** values you write into it (you never read meaningful typed values back out), declare it with `? super T` — you can safely add `T`s, but anything read back comes out only as `Object`. The canonical JDK example is `Collections.copy(List<? super T> dest, List<? extends T> src)` — the source only ever produces elements being copied, the destination only ever consumes them.

**Q: Why isn't `List<Integer>` a subtype of `List<Number>`, even though `Integer` is a subtype of `Number`?** ⭐

Because generics are deliberately **invariant**. If `List<Integer>` were allowed as a `List<Number>`, code like `List<Number> nums = intList; nums.add(3.14);` would compile — inserting a `Double` into what's actually backed by an `int`-only list, a genuine type-safety hole with no compile-time warning. Wildcards exist specifically to express the safe subset of that relationship explicitly, rather than allowing the unsafe general case.

*Follow-up: "Do arrays have the same restriction?"* No — **arrays are covariant** in Java (`Integer[]` is-a `Object[]` at compile time), a design decision made before generics existed. The trade-off is that arrays pay for that flexibility at **runtime** instead, via `ArrayStoreException` if you actually attempt to store an incompatible element through such a reference. Generics deliberately corrected this by choosing compile-time invariance plus opt-in wildcards.

**Q: What's a bounded type parameter for?**

`<T extends Comparable<T>>` restricts which types can be substituted for `T` (only types implementing `Comparable<T>`), and in exchange **grants access** to the bound's methods inside the generic code — you can call `.compareTo()` on a `T` only because the bound guarantees it exists. Multiple bounds are written `<T extends A & B>`, with at most one class (listed first) and any number of interfaces after it.

*Follow-up: "The real JDK signature for something like `max` is `<T extends Comparable<? super T>>`, not just `<T extends Comparable<T>>` — why the extra wildcard?"* It's PECS applied to the bound itself: it lets `T` satisfy the bound even if `T` itself doesn't directly implement `Comparable<T>`, as long as some **supertype** of `T` does — a more permissive, still-safe bound that accepts a wider range of real-world types.

**Q: What happens if you use a raw type — `List` without `<>`?**

All generic type checking is disabled for that reference. You get "unchecked" compiler warnings, real heap-pollution risk, and the exact `ClassCastException`-at-runtime problem generics were introduced to prevent. Raw types exist purely for compatibility with code written before generics existed (pre-2004). There's essentially never a reason to write one deliberately in new code — the diamond operator (`<>`) costs nothing to add.

**Q: Generic method vs. generic class — what's the actual difference?**

A generic **class**'s type parameter lives with each instance (`Pair<A, B>` — each `Pair` object has its own concrete `A`/`B`). A generic **method** declares its own type parameter, independent of any enclosing class (`static <T> T firstOf(List<T> list)`), inferred fresh per call site — this is usable even in a non-generic class, and it's *required* for `static` methods specifically, since a static method has no enclosing instance and therefore cannot see a class-level type parameter even if the class itself is generic.

## 3.4 — File I/O

**Q: `java.io.File` vs NIO.2's `Path`/`Files` — what changed and why does it matter?**

Legacy `File` conflates "the name of a location" with "operations on it," and reports failures as silent `boolean` returns — a failed delete or rename gives you no information about *why* it failed. NIO.2 (Java 7) separates these cleanly: `Path` is purely the location value, `Files` is the static toolbox of operations performed on it. NIO.2 failures throw `IOException` with an actual, specific message, and it adds capabilities `File` never had: symlink-aware operations, file attribute queries, atomic moves, and lazy, stream-based directory traversal. Practical guidance: write NIO.2 in new code; expect to still read `File`-based code in legacy codebases.

**Q: How do you decide which method to use for reading a file, and why does it matter?** ⭐

For small files, slurp the whole thing: `Files.readString` (text) or `Files.readAllBytes` (binary). For medium files where you want random access to individual lines, `Files.readAllLines` loads everything into a `List<String>`. For huge files, `Files.lines` returns a **lazy** `Stream<String>` that never loads the whole file into memory at once — critical for multi-gigabyte logs — but because it holds an **open file handle** under the hood, it must always be used inside try-with-resources; forgetting that leaks a file descriptor. `Files.newBufferedReader` gives manual, line-by-line control when neither extreme fits.

*Follow-up: "Why does buffering matter at all — why not just read byte-by-byte?"* Every unbuffered read is a system call, and syscalls are relatively expensive compared to reading from an in-memory buffer. Buffering batches many small logical reads into far fewer actual syscalls — the difference is easily orders of magnitude for line-by-line text processing.

**Q: Byte streams vs. character streams — when do you use which?**

`InputStream`/`OutputStream` move raw bytes with no interpretation — the correct choice for binary data like images, archives, or arbitrary blobs. `Reader`/`Writer` move *characters*, decoding/encoding through a specified charset — the correct choice for text. Historically, text I/O without an explicit charset meant relying on the platform's default charset, a real source of "works on my machine" bugs when moving between operating systems with different defaults; NIO.2's text helpers default to UTF-8 explicitly, and since Java 18 the entire platform's default charset is UTF-8, closing that gap.

**Q: How do you delete a non-empty directory in Java? Is there a built-in recursive delete?**

There is no built-in recursive delete in the JDK — `Files.delete` on a non-empty directory fails, since a directory must be empty before it can be removed. The standard idiom: `Files.walk(dir)` to get every path in the tree as a lazy stream, `.sorted(Comparator.reverseOrder())` to process the **deepest** paths first (files before the directories that contain them), then `Files.delete` each one. Interviewers ask this specifically to check whether you actually understand that `Files.walk` returns a closeable, lazy stream — and to see whether you reach for the sort trick rather than trying (and failing) to write your own recursive traversal from scratch.

**Q: How do checked exceptions interact with lambdas and streams?** *modern probe*

Poorly, which is part of the broader checked-vs-unchecked debate from 3.1. A lambda implementing a standard functional interface — like the one `Stream.map`/`mapToLong` expects — cannot throw a checked exception, because the interface's abstract method doesn't declare one in its signature. The standard workaround is to catch the checked exception inside the lambda and immediately wrap-and-rethrow it as an unchecked one; `UncheckedIOException` exists in the JDK specifically for this pattern with `IOException`. The alternative is extracting the logic into a named method that handles the checked exception normally, outside the lambda's constraints.

```java
tree.filter(Files::isRegularFile)
    .mapToLong(p -> {
        try {
            return Files.size(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    })
    .sum();
```
