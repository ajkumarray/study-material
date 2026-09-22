<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 2 · oop](../phase-2-oop/NOTES.md) | [Phase 4 · modern java ➡](../phase-4-modern-java/NOTES.md)
<!-- /nav -->

# Phase 3 — Core APIs & Error Handling: Notes

*Source: `lesson-3-1` (`Exceptions.java`) through `lesson-3-4` (`FileIO.java`).*

## 3.1 — Exceptions

An **exception** is an object representing an abnormal event that disrupted a program's normal flow — Java's mechanism for separating "what to do when things go right" from "what to do when they don't," instead of every function returning an error code that every caller must remember to check.

### The Exception Hierarchy

```
Throwable
├── Error                      JVM-level disasters: OutOfMemoryError, StackOverflowError.
│                               Don't catch; you generally can't meaningfully recover.
└── Exception
    ├── (checked)               IOException, SQLException, InterruptedException…
    │                           expected failures of the outside world;
    │                           the compiler forces catch-or-declare.
    └── RuntimeException        NullPointerException, IllegalArgumentException,
        (unchecked)             IllegalStateException, IndexOutOfBoundsException,
                                 ClassCastException, ArithmeticException…
                                 programming bugs; fix the code, don't catch.
```

### Key Concepts

- **`Throwable`** is the root of everything catchable/throwable. It splits into `Error` and `Exception`.
- **`Error`**: signals a serious JVM-level condition (`OutOfMemoryError`, `StackOverflowError`) that application code generally cannot recover from. Catching it is legal but rarely meaningful — after `OutOfMemoryError`, the JVM itself may not be in a state where your `catch` block can reliably run.
- **Checked exceptions** (`Exception` minus `RuntimeException`): represent failures that can happen *no matter how correct your code is* — a file that vanished, a network that dropped, a database that's down. The compiler forces every caller to either `catch` them or `declare` them (`throws`), because these are failures a caller genuinely needs to plan for.
- **Unchecked exceptions** (`RuntimeException` and its subtypes): represent *programming bugs* — the caller violated a contract (passed `null` where forbidden, used a bad index, cast to the wrong type). The fix is correcting the code, not catching the exception; propagating them to a top-level handler (rather than catching locally) is usually right.
- **Design-intent debate**: modern Java APIs (Spring, Kotlin interop, many newer JDK APIs) lean toward unchecked exceptions, because checked exceptions compose badly with lambdas and streams (a lambda implementing a standard functional interface cannot throw a checked exception without wrapping it). But the JDK's I/O and JDBC APIs remain checked for historical reasons, so production Java code must handle both styles fluently.

### Worked Example: try / catch / finally

```java
try {
    int[] arr = new int[3];
    arr[5] = 1;                              // throws ArrayIndexOutOfBoundsException
    System.out.println("never reached");     // skipped — control jumps straight to catch
} catch (ArrayIndexOutOfBoundsException e) {
    System.out.println("caught: " + e.getMessage());
    // caught: Index 5 out of bounds for length 3
} finally {
    System.out.println("finally ALWAYS runs (cleanup lives here)");
    // finally ALWAYS runs (cleanup lives here)
}
```

The moment `arr[5] = 1` throws, the rest of the `try` block is skipped entirely — `"never reached"` never prints. The JVM looks for a matching `catch` in the enclosing `try`; here `ArrayIndexOutOfBoundsException` matches directly, so the `catch` block runs. **`finally` runs regardless** of whether the `try` completed normally, threw-and-was-caught, or threw-and-is-propagating further up the stack. The only ways to skip it are `System.exit()`, JVM crash, or the thread never reaching it (e.g. an infinite loop inside `try`).

### Catch Order and Multi-Catch

```java
try {
    Object o = "not a number";
    Integer n = (Integer) o;                  // throws ClassCastException
} catch (ClassCastException | NullPointerException e) {
    // MULTI-CATCH: one block, several unrelated exception types, no duplication.
    System.out.println("multi-caught: " + e.getClass().getSimpleName());
    // multi-caught: ClassCastException
} catch (RuntimeException e) {
    // A broader type must come AFTER narrower ones.
    System.out.println("some other runtime problem");
}
```

- **Catch order matters**: the first matching `catch` block wins, so you must order blocks from most specific (subtype) to least specific (supertype). Putting a `catch (RuntimeException e)` *before* `catch (ClassCastException e)` is a **compile error** — the compiler recognizes the second block can never be reached, since the first already matches everything the second would.
- **Multi-catch** (`catch (A | B e)`, Java 7+) lets one block handle several *unrelated* exception types identically, avoiding duplicated handler code. The caught variable `e`'s static type is the common supertype of the alternatives, and it is implicitly **effectively final** — you cannot reassign it inside the block.

### `throw` vs `throws`

```java
// UNCHECKED: signals a caller bug (bad argument). No `throws` clause required.
static void registerUser(String username) {
    if (username.length() < 3) {
        throw new IllegalArgumentException("username too short: '" + username + "'");
    }
    System.out.println("registered " + username);
}

// CHECKED: the outside world can fail regardless of code quality.
// We don't handle it here — we DECLARE it and let the caller decide.
static String readConfig(String path) throws IOException {
    try (BufferedReader r = new BufferedReader(new FileReader(path))) {
        return r.readLine();
    }
}
```

`throw` is a **statement** — the actual act of raising an exception at a specific point in the code. `throws` is a **declaration** on a method signature, meaning "this method may let this exception escape; callers must handle it or re-declare it themselves." Only *checked* exceptions require a `throws` declaration to compile — you're free to list unchecked exceptions in `throws` too, but it's purely documentation; the compiler doesn't enforce it.

### The `finally`-Return Trap

```java
@SuppressWarnings("finally")
static int trickyValue() {
    try {
        return 1;
    } finally {
        return 2;    // overrides try's return AND swallows any in-flight exception!
    }
}

System.out.println(trickyValue());   // 2 — NOT 1
```

If `finally` itself contains a `return` (or a `throw`), it **hijacks** whatever the `try` block was about to return — and worse, if the `try` block was in the middle of propagating an exception, that exception is **silently swallowed**, replaced by whatever the `finally` block does instead. This is one of the most dangerous corners of exception handling because it fails silently. **Rule: never `return` or `throw` from inside a `finally` block.**

### try-with-resources (Java 7+) — the Modern Cleanup Idiom

```java
class AuditLog implements AutoCloseable {
    AuditLog() { System.out.println("  audit log opened"); }
    void write(String entry) { System.out.println("  audit: " + entry); }
    @Override public void close() { System.out.println("  audit log closed (automatically!)"); }
}

try (AuditLog log = new AuditLog()) {
    log.write("payment processed");
}   // log.close() is called HERE, guaranteed — success or exception
```
```
  audit log opened
  audit: payment processed
  audit log closed (automatically!)
```

- Every resource declared inside the parentheses of `try (...)` must implement **`AutoCloseable`** (a single-method interface: `void close() throws Exception`). Once the `try` block finishes — normally or via an exception — every declared resource is closed **automatically, in reverse declaration order**.
- This replaces the old, error-prone pattern of a `finally { if (r != null) r.close(); }` block written out by hand for every resource.
- **Suppressed exceptions**: if *both* the `try` body and a resource's `close()` throw, the body's exception is the one that propagates — `close()`'s exception is attached to it as a **suppressed exception**, retrievable via `e.getSuppressed()`. The old manual `finally`-close idiom got this wrong: the `close()` exception thrown from `finally` would silently *overwrite* the real exception from the body, destroying the original evidence. Correctly preserving both is a senior-level talking point that shows you understand *why* try-with-resources exists, not just its syntax.
- Any resource-holding class you write yourself (a custom connection, a file handle wrapper, a lock) should implement `AutoCloseable` so it plays well with this idiom.

### Exception Translation and Chaining

```java
// EXCEPTION TRANSLATION: catch the low-level checked exception, rethrow
// as one meaningful to THIS layer — with the cause chained.
static void loadProfile(String user) throws ProfileLoadException {
    try {
        readConfig(user + ".profile");
    } catch (IOException e) {
        throw new ProfileLoadException("could not load profile for " + user, e);
    }
}

try {
    loadProfile("ajay");
} catch (ProfileLoadException e) {
    System.out.println("caught: " + e.getMessage());
    System.out.println("  caused by: " + e.getCause());
    // caught: could not load profile for ajay
    //   caused by: java.io.FileNotFoundException: ajay.profile (No such file or directory)
}
```

Each architectural layer should throw exceptions meaningful **at its own level of abstraction**: a data-access layer shouldn't leak `SQLException` up into business logic that has no idea what SQL is. Catch the low-level exception, and **rethrow a domain-specific exception with the original attached as the `cause`** (`new ProfileLoadException(msg, ioEx)`). The full chain then prints in the stack trace with `Caused by:` sections, so debugging never loses the root cause. **Never swallow the cause** — omitting it (`throw new ProfileLoadException(msg)`) throws away the actual evidence of what went wrong. This exact pattern is what Spring's data-access layer does institutionally: it translates every JDBC `SQLException` into its own unchecked `DataAccessException` hierarchy.

### Designing Custom Exceptions

```java
class ProfileLoadException extends Exception {
    ProfileLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- Name it ending in `Exception`.
- Extend `RuntimeException` (unchecked — the more common modern default) or `Exception` (checked — reserve this for cases where callers genuinely have a recovery path and should be forced to consider it).
- Always provide `(String message)` and `(String message, Throwable cause)` constructors so it plays well with exception chaining.
- Add structured fields (an error code, the offending entity's id) when handlers need to act on specific data — don't make callers parse your message string to extract information.

### Practices That Show Interview/Production Maturity

- **Catch the narrowest type you can actually handle**; let everything else propagate to a handler that can actually do something about it.
- **Don't use exceptions for control flow.** They exist to signal exceptional states, not to implement expected branching — and constructing an exception is genuinely expensive, since `fillInStackTrace()` walks the entire call stack at construction time regardless of whether you ever read it.
- **Empty catch blocks hide bugs.** At an absolute minimum, log the exception; usually you should rethrow (possibly wrapped/translated).
- **Validate early** (`Objects.requireNonNull(x, "x must not be null")`, guard clauses at the top of a method) so failures surface close to their actual cause, not three layers downstream as a confusing `NullPointerException`.
- **Don't `e.printStackTrace()` in production code** — it writes to stderr with no context, log level, or aggregation. Use a real logger (Phase 6).

### Why It's Useful

Exception design decisions — checked vs. unchecked, where to translate, whether to wrap a cause — show up in every layered application: web controllers translating service exceptions into HTTP status codes, repositories translating `SQLException` into domain exceptions, and background jobs deciding what's retryable vs. fatal. Getting `finally` and try-with-resources right is the difference between resources (file handles, DB connections, sockets) leaking under load and code that cleans up reliably even when things go wrong.

### Summary / Key Takeaways

- `Throwable` → `Error` (don't catch) and `Exception` → checked (compiler-enforced) or `RuntimeException` (unchecked, a bug).
- `finally` always runs; never `return`/`throw` from inside it — it hijacks the `try`'s result and swallows in-flight exceptions.
- try-with-resources auto-closes `AutoCloseable`s in reverse order and correctly preserves the primary exception via suppressed exceptions when both body and `close()` throw.
- Translate exceptions across layers, always chaining the original as `cause` — never swallow it.
- Catch narrow, don't use exceptions for control flow, never leave a catch block empty.

## 3.2 — The Collections Framework

The **Collections Framework** is Java's standard library of reusable data structures — dynamic arrays, hash tables, trees, queues — unified behind a small set of interfaces so code can be written against the *interface* and swapped between implementations freely.

### The Map of the Territory

```
Collection
├── List      ordered, indexed, duplicates OK        -> ArrayList (default), LinkedList
├── Set       no duplicates                          -> HashSet (default), LinkedHashSet, TreeSet
└── Queue     processing order                        -> ArrayDeque (default), PriorityQueue

Map (a SEPARATE hierarchy — key -> value, not a Collection)
                                                        -> HashMap (default), LinkedHashMap, TreeMap
```

### Key Concepts

- **Golden rule: declare the interface, choose the implementation.** `List<String> names = new ArrayList<>();` — callers depend only on `List`'s contract, so you can swap the concrete class later without touching calling code.
- **`Map` is not a `Collection`.** It has its own root interface because it models a key→value association, not a sequence of elements — though you access its contents *through* collection views (`keySet()`, `values()`, `entrySet()`, each backed by the same map).

### Decision Table — Which Implementation to Use

| need | use | backing structure | key costs |
|---|---|---|---|
| ordered, indexed (default `List`) | `ArrayList` | growable array (grows ×1.5) | `get(i)` O(1); end-add amortized O(1); middle insert O(n); `contains` O(n) |
| many head/middle inserts via iterator | `LinkedList` | doubly-linked nodes | `get(i)` O(n) — walks!; node insert O(1); poor cache locality, rarely the right call |
| uniqueness (default `Set`) | `HashSet` | a `HashMap` internally, values ignored | add/contains O(1) average |
| uniqueness + insertion order | `LinkedHashSet` | hash table + linked list | O(1), predictable iteration order |
| uniqueness + sorted | `TreeSet` | red-black tree | O(log n); elements must be `Comparable` or given a `Comparator`; supports `first()`/`last()`/range queries |
| key→value (default `Map`) | `HashMap` | bucket array | `get`/`put` O(1) average |
| …plus insertion order / LRU | `LinkedHashMap` | + linked list threading entries | LRU cache achievable by overriding `removeEldestEntry` |
| …plus sorted keys / range queries | `TreeMap` | red-black tree | O(log n); `firstKey()`, `subMap()` |
| FIFO queue / LIFO stack | `ArrayDeque` | circular array | O(1) at both ends; the modern replacement for legacy `Stack` |
| priority order | `PriorityQueue` | binary heap | `peek` O(1); `poll`/`offer` O(log n); **not** fully sorted internally, only the head is guaranteed smallest/largest |

### Worked Example: `List`

```java
List<String> stack = new ArrayList<>();     // resizable array (Phase 1's Arrays.copyOf, automated)
stack.add("java");
stack.add("spring");
stack.add("java");                          // duplicate — fine in a List
stack.add(1, "docker");                     // insert at index — shifts everything after it right

System.out.println(stack);                          // [java, docker, spring, java]
System.out.println(stack.get(0));                    // java   — O(1), it's array-backed
System.out.println(stack.contains("spring"));        // true   — O(n), linear scan
```

`ArrayList` wins the majority of the time over `LinkedList` even where `LinkedList`'s Big-O looks better on paper (e.g. inserting into the middle) — contiguous array memory means CPU cache hits dominate, while `LinkedList`'s per-node heap allocations and pointer-chasing eat the theoretical advantage in practice. `LinkedList`'s honest niche is `Deque` operations at both ends.

### Immutable Collection Factories (Java 9+)

```java
List<Integer> fib = List.of(1, 1, 2, 3, 5);
try {
    fib.add(8);
} catch (UnsupportedOperationException e) {
    System.out.println("List.of() is immutable — add() threw UnsupportedOperationException");
}
```

`List.of(...)`, `Set.of(...)`, and `Map.of(...)` create genuinely immutable, compact, **null-hostile** collections (they throw `NullPointerException` if you pass a `null` element) — every mutating method throws `UnsupportedOperationException`. This is different from `Collections.unmodifiableList(list)`, which returns a **live view** over a still-mutable backing list — someone holding the original mutable reference can still change what the "unmodifiable" view shows. `List.copyOf(existing)` takes an immutable **snapshot** copy, which is the right tool for a defensive copy you want to hand out safely.

### The Fail-Fast Iterator Trap

```java
List<String> langs = new ArrayList<>(List.of("java", "perl", "kotlin", "cobol"));
try {
    for (String l : langs) {
        if (l.equals("perl")) langs.remove(l);   // structural change mid-iteration
    }
} catch (java.util.ConcurrentModificationException e) {
    System.out.println("for-each remove -> ConcurrentModificationException (fail-fast!)");
}

langs.removeIf(l -> l.equals("perl"));          // correct way #1 — clearest
Iterator<String> it = langs.iterator();
while (it.hasNext()) {
    if (it.next().equals("cobol")) it.remove();  // correct way #2 — the ITERATOR's own remove
}
System.out.println(langs);                        // [java, kotlin]
```

Every `ArrayList`/`HashMap`/etc. tracks a `modCount` (structural modification counter). An iterator captures that count when created and checks it on every `next()`/`hasNext()` call; if the collection was structurally changed by anything *other than the iterator's own `remove()`*, it throws `ConcurrentModificationException` — even in single-threaded code, as shown above. This is **best-effort bug detection**, not a thread-safety guarantee: two threads racing on a plain `HashMap` can corrupt its internal state without ever throwing this exception. Genuine concurrent access needs `ConcurrentHashMap` or `CopyOnWriteArrayList` (Phase 5).

### `Set` Variants

```java
Set<String> tags = new HashSet<>(List.of("api", "db", "api", "cache", "db"));
System.out.println(tags);        // e.g. [cache, api, db] — dupes gone, order arbitrary

Set<String> ordered = new LinkedHashSet<>(List.of("api", "db", "api", "cache"));
System.out.println(ordered);     // [api, db, cache] — insertion order preserved

Set<String> sorted = new TreeSet<>(List.of("api", "db", "cache"));
System.out.println(sorted);      // [api, cache, db] — sorted; O(log n) ops; needs Comparable
```

`HashSet` uniqueness (and `HashMap` key uniqueness) is powered by `equals`/`hashCode` — which is precisely why those contracts matter (Phase 2). A common idiom is deduplicating a list while keeping first-seen order: `new ArrayList<>(new LinkedHashSet<>(originalList))`.

**Trap**: `TreeSet` decides "is this a duplicate" using `compareTo() == 0`, **not** `equals()`. Two elements that are `compareTo`-equal but `equals`-unequal will silently collapse into one entry in a `TreeSet` — a subtle bug if your `Comparable` implementation doesn't align with `equals`.

### `Map` — the Most Important Data Structure in Programming

```java
Map<String, Integer> wordCount = new HashMap<>();
for (String word : "to be or not to be".split(" ")) {
    wordCount.merge(word, 1, Integer::sum);      // the classic word-count one-liner
}
System.out.println(wordCount);   // {to=2, be=2, or=1, not=1} (order arbitrary for HashMap)

System.out.println(wordCount.getOrDefault("java", 0));       // 0 — no exception for a missing key
wordCount.computeIfAbsent("lambda", k -> k.length());          // lazily computes only if absent
```

- **`merge(key, value, remappingFn)`**: if the key is absent, inserts `value`; if present, replaces the value with `remappingFn.apply(oldValue, value)`. The word-count idiom above is the canonical example.
- **`getOrDefault(key, fallback)`**: avoids a manual `containsKey` + `get` dance.
- **`computeIfAbsent(key, fn)`**: the standard idiom for building a "multimap" lazily — `map.computeIfAbsent(key, k -> new ArrayList<>()).add(value)` creates the list only the first time a key is seen.
- **Iterate with `entrySet()`**, not `keySet()` followed by a separate `get(key)` — the latter does two lookups per entry for no reason:

```java
Map<String, Integer> prices = new TreeMap<>(Map.of("kafka", 3, "redis", 2, "docker", 1));
for (Map.Entry<String, Integer> e : prices.entrySet()) {
    System.out.println(e.getKey() + " -> " + e.getValue());
}
// docker -> 1
// kafka -> 3
// redis -> 2      (TreeMap prints sorted by key)
```

### HashMap Internals — the Star Interview Topic

A `HashMap` is backed by an array of **buckets**. The bucket index for a key is `hash(key) & (capacity − 1)` — capacity is always a power of two, which turns the modulo into a fast bitmask operation. Two keys landing in the same bucket **collide**, and their entries chain as a small linked list within that bucket. Since Java 8, once a single bucket accumulates **8 or more entries** *and* the table has at least 64 buckets, that bucket **treeifies** into a small red-black tree — turning worst-case O(n) lookup for that bucket into O(log n), which incidentally also defeats a class of hash-collision denial-of-service attacks. Once the number of entries exceeds `capacity × loadFactor` (default load factor `0.75`), the table **resizes** — doubling capacity and rehashing every entry into its new bucket. This machinery is exactly why keys must be **immutable while stored** (mutating a key's hash-relevant fields after insertion moves it to the wrong logical bucket, effectively losing it) and why correct, stable `equals`/`hashCode` implementations (Phase 2) are load-bearing for correctness, not just style.

| Map variant | thread safety | order | notes |
|---|---|---|---|
| `HashMap` | none | arbitrary | the default; fast |
| `Hashtable` | every method synchronized | arbitrary | legacy; essentially never the right choice today |
| `LinkedHashMap` | none | insertion order (or access order, configurable) | override `removeEldestEntry` for an LRU cache |
| `TreeMap` | none | sorted by key | red-black tree, O(log n), range queries |
| `ConcurrentHashMap` | lock-striped/CAS, no global lock | arbitrary | the real answer for a map shared across threads (Phase 5) |

### `Comparable` vs `Comparator`

```java
record Dev(String name, int exp) { }
List<Dev> devs = new ArrayList<>(List.of(
        new Dev("ajay", 4), new Dev("meera", 9), new Dev("ravi", 4), new Dev("zoya", 1)));

devs.sort(Comparator.comparing(Dev::exp).reversed()
                    .thenComparing(Dev::name));
System.out.println(devs);
// [Dev[name=meera, exp=9], Dev[name=ajay, exp=4], Dev[name=ravi, exp=4], Dev[name=zoya, exp=1]]
```

`Comparable` (`compareTo`, implemented directly on a class) defines a type's single **natural** order — `String`, `Integer`, and `LocalDate` all implement it. `Comparator` is an **external**, ad-hoc order you define as many of as you like, without touching the class itself, and Java's `Comparator.comparing(...).reversed().thenComparing(...)` builder makes composing multi-key sorts declarative and readable — here, sorted by experience descending, ties broken alphabetically by name.

### Queue and Deque

```java
Deque<String> queue = new ArrayDeque<>();        // FIFO: offer at tail, poll from head
queue.offer("job-1"); queue.offer("job-2"); queue.offer("job-3");
System.out.println(queue.poll() + ", " + queue.poll());   // job-1, job-2

Deque<String> undo = new ArrayDeque<>();         // LIFO: same class doubles as a stack
undo.push("typed 'a'"); undo.push("typed 'b'");
System.out.println(undo.pop());                             // typed 'b'

PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.reverseOrder());
pq.addAll(List.of(3, 41, 7, 12));
while (!pq.isEmpty()) System.out.print(pq.poll() + " ");    // 41 12 7 3 — always the current max first
```

`ArrayDeque` is a circular array supporting O(1) insertion/removal at **both** ends, and is the modern replacement for the legacy `Stack` class (`Stack` is deprecated in spirit — it extends `Vector`, is needlessly synchronized, and, worse, *is a `List`*, so nothing stops you from inserting into the "middle" of a supposed stack, breaking the abstraction entirely). `Deque`'s two method families differ in failure behavior: `peek()`/`poll()` return `null` on an empty deque; `element()`/`remove()`/`pop()` **throw** instead. `PriorityQueue` is a binary heap — `peek()` is O(1) and always the current minimum (or maximum, with a reversed comparator), but the queue is **not** internally sorted as a whole; only the head ordering is guaranteed.

### The Autoboxing + Overloading Trap

```java
List<Integer> nums = new ArrayList<>(List.of(1, 2, 3));
nums.remove(Integer.valueOf(2));   // hits remove(Object) — removes the VALUE 2
System.out.println(nums);          // [1, 3]
nums.remove(0);                    // hits remove(int index) — removes the element AT INDEX 0
System.out.println(nums);          // [3]
```

`List<Integer>` has two overloads named `remove`: `remove(int index)` and `remove(Object o)`. A raw `int` argument always resolves to the `int` overload (exact match beats autoboxing in overload resolution — Phase 1), so `list.remove(2)` removes by **position**. To remove by **value**, you must force autoboxing explicitly with `Integer.valueOf(2)` (or `(Integer) 2`), which resolves to `remove(Object)`. This is one of the sneakiest real bugs in Java collections code.

### Why It's Useful

Collections choice directly drives application performance: picking `LinkedList` for random-access-heavy code, or forgetting `HashMap` keys must be immutable, causes real production slowdowns and subtle bugs. The fail-fast iterator trap and the autoboxing `remove` trap are both extremely common real-world defects that compile cleanly and only misbehave on specific inputs — exactly the kind of bug interviewers use to separate memorized syntax from genuine understanding.

### Summary / Key Takeaways

- Declare collections by interface, choose implementation by need; know the decision table cold — especially "`ArrayList` wins almost always."
- `HashMap` buckets by `hash & (capacity−1)`, treeifies dense buckets since Java 8, and resizes at `capacity × 0.75`; keys must stay immutable while stored.
- Fail-fast iterators throw `ConcurrentModificationException` on structural changes not made through the iterator itself — use `removeIf` or `Iterator.remove()`.
- `List.of`/`Set.of`/`Map.of` are truly immutable; `Collections.unmodifiableX` is only a live view over a still-mutable backing collection.
- `Comparable` = one natural order on the type itself; `Comparator` = unlimited external, composable orders.

## 3.3 — Generics

**Generics** let you write a class or method once, parameterized by a type, and have the compiler enforce type safety for every use — without generics, pre-2004 Java collections held raw `Object`s, and every read required an unchecked cast that could blow up at runtime.

### Key Concepts

- **Why generics exist**: move `ClassCastException` from *runtime* (when you least expect it) to *compile time* (where the compiler tells you immediately), and eliminate the casts that raw-`Object` collections forced on every read.
- **Raw types**: using a generic class without its type argument (`List` instead of `List<String>`) disables all generic type checking for that reference — the compiler emits "unchecked" warnings, and it opens the door to `ClassCastException` at runtime again. Raw types exist purely for backward compatibility with pre-generics (pre-Java 5) code — never write one deliberately.
- **Vocabulary**: a **generic class** declares its type parameters after the class name (`class Pair<A, B>`); a **generic method** declares its own type parameter, independent of any enclosing class (`static <T> T firstOf(List<T> list)`), inferred fresh per call site.
- **Bounded type parameters**: `<T extends Comparable<T>>` restricts what `T` can be *and*, in exchange, **grants access** to the bound's methods inside the generic code — you can call `.compareTo()` on a `T` only because the bound promises it exists. Multiple bounds are written `<T extends A & B>` (at most one class, listed first, then any number of interfaces).
- **Convention**: `T` (Type), `E` (Element), `K`/`V` (Key/Value), `R` (Result).

### Worked Example: Why Generics Exist

```java
List<String> safe = new ArrayList<>();      // <> = diamond operator, type inferred
safe.add("java");
// safe.add(42);                            // compile error — caught EARLY
String s = safe.get(0);                     // no cast needed

@SuppressWarnings({"rawtypes", "unchecked"})
List raw = new ArrayList();                 // RAW type — pre-generics style, avoid
raw.add("java");
raw.add(42);                                // compiler allows it — no type checking on a raw type
try {
    String boom = (String) raw.get(1);      // ...and it explodes at RUNTIME instead
} catch (ClassCastException e) {
    System.out.println("raw types -> ClassCastException at RUNTIME (generics prevent this)");
}
```

### Worked Example: Generic Classes and Methods

```java
class Pair<A, B> {
    private final A first;
    private final B second;
    Pair(A first, B second) { this.first = first; this.second = second; }
    A first()  { return first; }
    B second() { return second; }
}

Pair<String, Integer> entry = new Pair<>("kafka", 9092);
System.out.println(entry.first() + " -> " + entry.second());   // kafka -> 9092

static <T> T firstOf(List<T> list) { return list.get(0); }
System.out.println(firstOf(List.of("a", "b")));   // a
System.out.println(firstOf(List.of(1, 2, 3)));    // 1
```

`Pair<A, B>`'s type parameters are tied to a specific *instance* — each `Pair` object carries its own concrete `A`/`B`. `firstOf`'s `<T>` belongs to the *method call*, not to any object — the compiler infers a fresh `T` for every invocation (`String` for the first call, `Integer` for the second), which is also why `static` methods must declare their own type parameters: a static method has no enclosing instance, so it cannot see a class's type parameters even if it's called on a generic class.

### Bounded Type Parameters

```java
static <T extends Comparable<T>> T max(List<T> list) {
    T best = list.get(0);
    for (T item : list) {
        if (item.compareTo(best) > 0) best = item;   // compareTo exists BECAUSE of the bound
    }
    return best;
}

System.out.println(max(List.of(3, 41, 7)));           // 41
System.out.println(max(List.of("kafka", "redis")));   // redis
// max(List.of(new Object()));   // compile error: Object doesn't implement Comparable
```

Without the `extends Comparable<T>` bound, the compiler wouldn't let you call `.compareTo()` on a plain `T` — it only knows `T` is *some* type, with no guaranteed methods beyond `Object`'s. The bound is a two-way deal: it restricts what callers can pass in, and in exchange it unlocks methods inside the generic code.

### Invariance and Wildcards — PECS

```java
List<Integer> ints = List.of(1, 2, 3);
List<Double> doubles = List.of(1.5, 2.5);

// PRODUCER (we only READ from it) -> extends
static double sum(List<? extends Number> numbers) {
    double total = 0;
    for (Number n : numbers) total += n.doubleValue();
    return total;
}
System.out.println(sum(ints));      // 6.0  — one method serves both List<Integer> and List<Double>
System.out.println(sum(doubles));   // 4.0

// CONSUMER (we only WRITE into it) -> super
static void fillWithSquares(List<? super Integer> target, int upTo) {
    for (int i = 1; i <= upTo; i++) target.add(i * i);
}
List<Number> sink = new ArrayList<>();
fillWithSquares(sink, 4);
System.out.println(sink);   // [1, 4, 9, 16]
```

**Generics are invariant**: `List<Integer>` is **not** a subtype of `List<Number>`, even though `Integer` *is* a subtype of `Number`. If it were allowed, code like `List<Number> nums = intList; nums.add(3.14);` would compile — silently poisoning what's actually an `int`-only list with a `Double`, a hole the type system is specifically designed to prevent. Wildcards restore the flexibility you actually want, safely:

| wildcard | means | you can | role |
|---|---|---|---|
| `? extends T` | some unknown *subtype* of `T` | **read** elements out as `T` | **producer** |
| `? super T` | some unknown *supertype* of `T` | **write** `T` elements in | **consumer** |

**PECS: Producer Extends, Consumer Super.** The JDK itself embodies this everywhere, most famously `Collections.copy(List<? super T> dest, List<? extends T> src)` — the destination only ever receives (consumes) elements, the source only ever gives (produces) them.

*Related contrast*: **arrays**, unlike generics, are **covariant** — `Integer[]` *is* considered an `Object[]` at compile time — and pay for that flexibility with a runtime `ArrayStoreException` if you actually try to store the wrong type through such a reference. Generics deliberately chose invariance plus wildcards specifically to catch this class of error at compile time instead.

### Type Erasure

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass());   // true — one shared runtime class
```

Generics are a **compile-time-only** feature. After the compiler finishes checking your code against the type parameters, it **erases** every type parameter down to `Object` (or its bound, if it has one) and inserts the necessary casts at each use site automatically. At runtime there is exactly **one** class file per generic class — `ArrayList<String>` and `ArrayList<Integer>` are the *same* `.class`, the same `Class` object. This is why erasure has real, memorable consequences:

- **No `new T()`** — the JVM has no idea what `T` actually is at runtime, so it can't instantiate it.
- **No `T.class`** — same reason; there's no runtime `Class<T>` object to reference.
- **No `instanceof List<String>`** — only `instanceof List` compiles; the parameter is gone by then.
- **No generic arrays** — `new T[10]` doesn't compile (arrays remember their element type at runtime; erasure would make that impossible to enforce safely).
- **Overloads that differ only by type argument collide** — `void f(List<String> x)` and `void f(List<Integer> x)` cannot coexist; they erase to the identical signature `void f(List x)`.

Erasure exists specifically for **backward compatibility**: generics were bolted onto Java in 2004 (Java 5), and erasure let pre-generics bytecode keep interoperating with generic code without a breaking bytecode format change.

### Why It's Useful

Generics eliminate an entire category of runtime `ClassCastException`s and are unavoidable once you touch the Collections Framework, streams, or any modern library — you cannot use `List`, `Optional`, `Stream`, or `CompletableFuture` fluently without understanding bounds and wildcards. PECS specifically comes up any time you write a reusable method that copies, merges, or aggregates across collections of related types, which is extremely common in real utility code.

### Summary / Key Takeaways

- Generics move type errors from runtime (`ClassCastException`) to compile time; never use raw types.
- A bounded type parameter (`<T extends X>`) restricts what `T` can be and grants access to `X`'s methods inside the code.
- Generics are invariant; wildcards (`? extends`/`? super`) restore safe flexibility — remember PECS: Producer Extends, Consumer Super.
- Type erasure removes all type parameters at runtime, leaving one shared runtime class per generic type — hence no `new T()`, `T.class`, `instanceof List<X>`, generic arrays, or type-argument-only overloads.

## 3.4 — File I/O (NIO.2)

Java has two generations of file APIs: the original `java.io` (`File`, `FileReader`, 1996 — you will still **read** this in older code) and `java.nio.file` ("NIO.2," Java 7+: `Path` + `Files` — the API you should **write** in new code).

### Key Concepts

- **Mental model**: `Path` is a **location** — a name that may or may not actually exist on disk, similar to how a variable name doesn't guarantee the object exists. `Files` is the **static toolbox** of operations performed *on* a `Path` (read, write, copy, move, delete, walk, check existence, …).
- **Why NIO.2 over legacy `File`**: `File` mixes the "name" concept with operations and reports failures as silent `boolean` return values. NIO.2 throws `IOException` with a real, detailed message on failure, and adds symlink handling, file attribute queries, atomic moves, and lazy stream-based directory walking that `File` never had.
- **Bytes vs. characters**: `InputStream`/`OutputStream` move raw bytes — the right choice for binary data (images, zips, arbitrary blobs). `Reader`/`Writer` move *characters*, decoding/encoding through a charset — the right choice for text. NIO.2's text helper methods default to **UTF-8** (and since Java 18, `java.io`'s default charset is UTF-8 everywhere too, removing a historic source of platform-dependent bugs).
- **Buffering exists because syscalls are expensive.** Every unbuffered read/write is a system call; wrapping a raw stream in a buffer batches many small operations into far fewer syscalls — an orders-of-magnitude difference for line-by-line or byte-by-byte access. `Files.new*` helper methods (`newBufferedReader`, etc.) come pre-buffered.

### Worked Example: `Path` Algebra

```java
Path playground = Path.of("playground");
Files.createDirectories(playground);               // mkdir -p — no error if it already exists

Path notes = playground.resolve("notes.txt");       // playground/notes.txt — join operation
System.out.println(notes);                          // playground/notes.txt
System.out.println(notes.toAbsolutePath().normalize());  // /home/.../playground/notes.txt
System.out.println(notes.getFileName());             // notes.txt
System.out.println(notes.getParent());                // playground
System.out.println(Files.exists(notes));              // false — a Path is just a NAME, nothing exists yet
```

`Path.of(...)`, `resolve` (join a relative path onto another), `getParent`/`getFileName`, `toAbsolutePath`, `normalize` (folds away `.`/`..` segments), and `relativize` (compute a relative path between two paths) form the core of path algebra — and platform-specific separator handling (`/` vs `\`) is entirely handled for you, unlike hand-built string paths.

### Worked Example: Writing

```java
Files.writeString(notes, """
        Phase 3 progress:
        exceptions done
        collections done
        generics done
        """);
System.out.println(Files.size(notes));    // e.g. 62 (bytes)

Files.writeString(notes, "io in progress\n", StandardOpenOption.APPEND);   // append, don't overwrite

Path langs = playground.resolve("langs.csv");
Files.write(langs, List.of("java,1995", "typescript,2012", "go,2009"));    // write a List<String> as lines
```

`StandardOpenOption` values (`APPEND`, `CREATE`, `CREATE_NEW`, `TRUNCATE_EXISTING`, …) control write mode — the default behavior of `Files.writeString`/`Files.write` without any option is to create-or-truncate-and-overwrite.

### Worked Example: Reading — Pick By Size

```java
// Small file: slurp it whole.
String content = Files.readString(notes);
System.out.println(content.lines().count());   // e.g. 5

// Medium file: all lines into a List.
List<String> lines = Files.readAllLines(langs);
System.out.println(lines);   // [java,1995, typescript,2012, go,2009]

// Large file: STREAM lines lazily — never loads the whole file into memory.
// The stream holds an OPEN file handle -> always try-with-resources.
try (Stream<String> stream = Files.lines(notes)) {
    long done = stream.filter(l -> l.contains("done")).count();
    System.out.println(done);   // 3
}

// Manual/mixed control:
try (BufferedReader reader = Files.newBufferedReader(langs)) {
    System.out.println(reader.readLine());   // java,1995
}
```

| method | loads | best for |
|---|---|---|
| `readString` / `readAllBytes` | whole file, eagerly | small files |
| `readAllLines` | whole file into a `List<String>`, eagerly | medium files where you need random access to lines |
| `Files.lines` | one line at a time, lazily | huge files — but it holds an open handle, so it **must** be closed |
| `newBufferedReader` | manual, buffered | fine-grained/streaming control |

### Copy, Move, Delete, and Directory Trees

```java
Path backup = playground.resolve("notes.bak");
Files.copy(notes, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
Files.move(backup, playground.resolve("notes.backup"));
System.out.println(Files.exists(playground.resolve("notes.backup")));   // true

System.out.println(Files.deleteIfExists(playground.resolve("ghost.txt")));  // false — never existed
// Files.delete(missingPath) would instead THROW NoSuchFileException.

try (Stream<Path> tree = Files.walk(playground)) {
    tree.filter(Files::isRegularFile).forEach(p -> System.out.println("found: " + p));
}
```

`Files.list(dir)` lists one directory level; `Files.walk(dir)` recurses the whole tree — both return **lazy streams that hold open resource handles**, so both need try-with-resources. There is deliberately no "delete a whole directory tree" convenience method in the JDK: deleting a non-empty directory fails (a directory must be empty first), so the standard idiom walks the tree, **sorts deepest-first** (`Comparator.reverseOrder()` on `Path`, since a `Path` naturally sorts shallower-before-deeper), and deletes files before their parent directories:

```java
try (Stream<Path> tree = Files.walk(playground)) {
    tree.sorted(java.util.Comparator.reverseOrder())
        .forEach(p -> {
            try {
                Files.delete(p);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
}
System.out.println(Files.exists(playground));   // false
```

### Checked Exceptions Inside Lambdas

```java
try (Stream<Path> tree = Files.walk(playground)) {
    long bytes = tree.filter(Files::isRegularFile)
            .mapToLong(p -> {
                try {
                    return Files.size(p);       // Files.size throws checked IOException
                } catch (IOException e) {
                    throw new UncheckedIOException(e);   // 3.1's chaining pattern, applied
                }
            })
            .sum();
}
```

Lambdas implementing standard functional interfaces (like the one `Stream.mapToLong` expects) **cannot throw checked exceptions** — the interface's abstract method simply doesn't declare `throws IOException`, so the compiler rejects it. The standard workaround wraps the checked exception in `UncheckedIOException` (a `RuntimeException` subtype built exactly for this purpose) using the same catch-translate-chain pattern from 3.1. This friction between checked exceptions and functional-style code is a major reason modern Java APIs increasingly favor unchecked exceptions.

### Why It's Useful

Correctly choosing between `readString`/`readAllLines`/`Files.lines` is the difference between a tool that works fine in dev on a small test file and one that runs out of memory in production on a multi-gigabyte log file. The lazy-stream-must-be-closed pattern for `Files.lines`/`Files.walk` is a direct, concrete application of try-with-resources from 3.1 — file I/O is one of the most common places resource leaks actually happen in real applications.

### Summary / Key Takeaways

- `Path` is a location (may not exist); `Files` is the static toolbox of operations on paths. Prefer NIO.2 over legacy `java.io.File` in new code.
- Choose the reading method by file size: `readString`/`readAllLines` for small/medium files, `Files.lines` (lazy, must-close) for huge ones.
- `Files.lines` and `Files.walk` return lazy streams holding open file handles — always try-with-resources them.
- There's no built-in recursive delete: walk the tree, sort deepest-first, delete files then directories.
- Lambdas can't throw checked exceptions directly — wrap with `UncheckedIOException` and the exception-chaining pattern from 3.1.
