<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 4 · stacks queues](../phase-4-stacks-queues/NOTES.md) | [Phase 6 · recursion backtracking ➡](../phase-6-recursion-backtracking/NOTES.md)
<!-- /nav -->

# Phase 5 — Hashing: Notes

All code below is exercised (and asserted correct) in `HashingPatterns.java` in this
directory unless otherwise noted. Run it with `java -ea HashingPatterns.java`.

## 1. How a hash map actually works

A **hash map** is a data structure that stores key→value pairs in an array of
"buckets," using a hash function to compute which bucket a given key belongs in.
It trades memory for speed: instead of scanning every element to find something
(O(n)), it jumps straight to the right bucket (O(1) average).

- **Backing array of buckets**: internally a `HashMap` holds a `Node[]` table.
  `Integer.hashCode()` (or your override) produces an `int` hash, which is then
  spread/mixed and reduced modulo the table length (`hash & (capacity - 1)`,
  since capacity is always a power of two) to pick a bucket index.
- **Collision resolution by chaining**: two keys that land in the same bucket are
  stored as a linked list off that bucket (`Node.next`). Looking a key up means
  walking that chain and calling `.equals()` on each candidate until a match is
  found.
- **Treeification (Java 8+)**: if a single bucket's chain grows to 8+ nodes *and*
  the table capacity is at least 64, Java converts that bucket's linked list into
  a small red-black tree, dropping worst-case lookup in that bucket from O(n) to
  O(log n). This only kicks in under pathological collisions — it's a safety net,
  not something you should rely on for correctness.
- **Load factor & resizing**: `HashMap`'s default initial capacity is 16 and
  default load factor is 0.75. Once `size > capacity * loadFactor` (so, after the
  13th entry in a fresh map), the table doubles in size and every entry is
  rehashed into the new table. This resize is what keeps average bucket chain
  length short (and thus lookups close to O(1)) as the map grows — but it makes
  a single `put` call that triggers a resize an O(n) operation, amortized to O(1)
  across many inserts.
- **`hashCode`/`equals` contract**: for a hash map to work correctly, if
  `a.equals(b)` is `true`, then `a.hashCode() == b.hashCode()` **must** also be
  true. The reverse isn't required — two unequal objects can share a hash code
  (that's just a collision, handled by chaining/treeification + `equals`). Java's
  boxed types (`Integer`, `String`, etc.) already implement this contract
  correctly; custom objects used as keys must override both methods together.

```java
Map<Integer, Integer> seen = new HashMap<>();   // value -> index
seen.put(2, 0);
seen.put(7, 1);
seen.get(7);              // -> 1  (bucket for hash(7), chain walked, equals(7,7) true)
seen.get(99);              // -> null (bucket empty or no equals() match in its chain)
```
In this example: `put(2, 0)` computes `hash(2)`, picks a bucket, and stores a
`Node` there. `get(7)` recomputes `hash(7)`, jumps to that bucket, and — because
only one key ever hashed there — returns its value immediately without scanning
anything else. That "jump directly to the answer" behavior is what gives O(1)
average lookup, as opposed to an array/list where `contains`/`indexOf` is O(n).

### Why it's useful
Hashing is the single most common way to turn an O(n²) brute-force double loop
into an O(n) single pass. Any time a problem asks "have I seen X?", "how many
times does X occur?", or "group these by some derived property," a hash
map/set is the first tool to reach for.

### Summary
- A hash map buckets entries by `hashCode()`, resolves collisions within a
  bucket by `equals()` (chained, treeified past 8 entries in a large table).
- Average O(1) insert/lookup/delete; worst case O(n) (or O(log n) once
  treeified) if many keys collide into the same bucket.
- Resizing (default load factor 0.75, capacity doubles) keeps chains short —
  amortized O(1) even though any single `put` can trigger an O(n) rehash.
- `equals(a,b) == true` **must** imply `hashCode(a) == hashCode(b)`; violating
  this silently breaks lookups.

## 2. Pattern: complement lookup ("have I seen X?")

**Definition**: store each element (or a value derived from it) in a map as you
scan once, and for each new element check whether its *complement* — the value
that would combine with it to satisfy the condition — has already been seen.
This collapses the classic "for each i, for each j" double loop into a single
pass.

- **One pass, not two**: you don't need to first build the whole map and then
  search it in a second loop — checking and inserting can happen in the same
  iteration as long as you check *before* you insert the current element (so an
  element doesn't pair with itself).
- **Map direction matters**: store `value -> index` (or `value -> count`)
  depending on what the answer needs back.

```java
// TWO-SUM, unsorted: remember each value's index; for x, look for target-x.
static int[] twoSum(int[] a, int target) {
    Map<Integer, Integer> seen = new HashMap<>();     // value -> index
    for (int i = 0; i < a.length; i++) {
        Integer j = seen.get(target - a[i]);
        if (j != null) return new int[]{j, i};
        seen.put(a[i], i);
    }
    return new int[]{-1, -1};
}

twoSum(new int[]{2, 7, 11, 15}, 9);   // -> [0, 1]   (2 + 7 == 9)
twoSum(new int[]{3, 2, 4}, 6);        // -> [1, 2]   (2 + 4 == 6)
```
In this example: at `i=0` (`a[0]=2`), `seen` is empty, so `seen.get(7)` (9-2)
returns `null`; we then store `2 -> 0`. At `i=1` (`a[1]=7`), `seen.get(2)` (9-7)
now finds the index we stored a moment ago, so we immediately return `[0, 1]`.
No inner loop, no sorting — a single O(n) pass with O(n) auxiliary space. This
is a direct upgrade over the two-pointer version of Two Sum (Phase 2), which
needs the array **sorted** first (O(n log n)) and only works because sorting
doesn't destroy the answer for that variant — the unsorted case with original
indices needed *preserved* is exactly where a hash map wins.

### Why it's useful
This is the archetypal hashing interview problem because the trick generalizes
directly: "does a pair summing to k exist," "is there a duplicate," "does the
running total minus k exist" (prefix-sum pattern below) are all the same shape
— remember what you've seen, check the complement, in one pass.

## 3. Pattern: frequency counting

**Definition**: tally how many times each element/character occurs using a
map from element to count, then answer questions about the distribution (first
unique element, most frequent element, whether two collections are
permutations of each other, etc.).

- **`merge` for tallying**: `map.merge(key, 1, Integer::sum)` is the idiomatic
  one-liner for "increment this key's count, or insert it with count 1 if it's
  new." It replaces the more verbose
  `map.put(key, map.getOrDefault(key, 0) + 1)`.
- **Array beats map for small, fixed alphabets**: if keys are bounded and
  small (lowercase letters → `int[26]`, ASCII → `int[128]`), a plain array
  indexed by `(char) - 'a'` is faster than a `HashMap<Character, Integer>` —
  no hashing, no boxing, no bucket lookups, just direct array indexing.

```java
// FREQUENCY COUNT: tally, then scan for the first count-1 char.
static int firstUniqueChar(String s) {
    Map<Character, Integer> count = new HashMap<>();
    for (char c : s.toCharArray()) count.merge(c, 1, Integer::sum);
    for (int i = 0; i < s.length(); i++) if (count.get(s.charAt(i)) == 1) return i;
    return -1;
}

firstUniqueChar("leetcode");      // -> 0   ('l' occurs once, and first)
firstUniqueChar("loveleetcode");  // -> 2   ('v' is the first count-1 char)
firstUniqueChar("aabb");          // -> -1  (no character occurs exactly once)
```
In this example: the first loop builds `{l:1, e:3, t:2, c:1, o:2, d:1}` for
`"leetcode"`. The second loop then re-walks the string **in original order**
and returns the index of the first character whose tallied count is 1 — index
0, `'l'`. Two O(n) passes, O(1) auxiliary space if the alphabet is fixed
(O(k) in general for k distinct characters).

### Comparison: HashMap counting vs. fixed-size array counting

| Approach | Time | Space | When to use |
|---|---|---|---|
| `HashMap<Character/Integer, Integer>` | O(n) build + O(1) avg per lookup | O(k) distinct keys | Unknown/large/non-contiguous key domain (arbitrary objects, Unicode, big integers) |
| `int[26]` / `int[128]` / `int[256]` | O(n) build + O(1) per lookup | O(1) — fixed size | Known small domain (lowercase letters, ASCII, byte values) — faster in practice, no boxing/hashing overhead |

### Why it's useful
Frequency counting underlies valid-anagram checks (do two strings have
identical count arrays?), majority-element detection, top-K-frequent
(counting is always step 1), and "first non-repeating" style stream problems.

## 4. Pattern: grouping by a canonical key

**Definition**: derive a canonical (normalized) key from each item such that
items that should be grouped together produce the *same* key, then use a
`Map<Key, List<Item>>` — a "lazy multimap" — to bucket items by that key in a
single pass.

- **Canonical key choice matters**: for anagrams, sorting each word's
  characters (`"eat"` → `"aet"`, `"tea"` → `"aet"`) is one valid canonical key;
  a 26-length character-count signature is another (avoids the O(k log k) sort
  per word, at the cost of building/comparing a fixed-size array or turning it
  into a string key).
- **`computeIfAbsent` is the lazy-multimap idiom**: `map.computeIfAbsent(key, k
  -> new ArrayList<>()).add(item)` inserts an empty list only the first time a
  key is seen, then always appends — no manual "if absent, create; else, get and
  add" branching.

```java
// GROUPING by a canonical key: anagrams share their sorted letters.
static List<List<String>> groupAnagrams(String[] words) {
    Map<String, List<String>> map = new HashMap<>();
    for (String w : words) {
        char[] chars = w.toCharArray();
        Arrays.sort(chars);                            // "eat" -> "aet"
        String key = new String(chars);
        map.computeIfAbsent(key, k -> new ArrayList<>()).add(w);   // lazy multimap
    }
    return new ArrayList<>(map.values());
}

groupAnagrams(new String[]{"eat","tea","tan","ate","nat","bat"});
// -> [[eat, tea, ate], [tan, nat], [bat]]   (3 groups; order of groups is not guaranteed)
```
In this example: `"eat"`, `"tea"`, and `"ate"` all sort to the key `"aet"`, so
`computeIfAbsent("aet", ...)` returns the same list for all three, and each
`.add(w)` appends to it. `"tan"` and `"nat"` both sort to `"ant"`, forming the
second group. `"bat"` sorts to `"abt"`, unique, forming a singleton group.
Total cost: O(n · k log k) where k is average word length (dominated by
sorting each word); using a 26-count-array key instead drops this to O(n · k).

### Why it's useful
"Group by a derived property" recurs constantly: group points by slope
through a common origin, group words by their sorted letters, group log lines
by a normalized message template, bucket numbers by `(num % k)` for
subarray-divisible-by-k problems. The shape is always the same: compute a key,
`computeIfAbsent`, append.

## 5. Pattern: set membership for O(1) reach

**Definition**: put all elements into a `HashSet` so that "does this value
exist?" becomes O(1), then use that membership test to avoid redundant work —
most powerfully, to make sure each element is only ever the **start** of a
scan once, bounding total work across the whole input to O(n).

```java
// SET MEMBERSHIP for O(1) reach: only start counting a run at its SMALLEST
// element (n-1 not present). Each number is visited O(1) times -> O(n) total.
static int longestConsecutive(int[] a) {
    Set<Integer> set = new HashSet<>();
    for (int x : a) set.add(x);
    int best = 0;
    for (int x : set) {
        if (!set.contains(x - 1)) {                    // x is a run start
            int len = 1;
            while (set.contains(x + len)) len++;
            best = Math.max(best, len);
        }
    }
    return best;
}

longestConsecutive(new int[]{100, 4, 200, 1, 3, 2});   // -> 4  (run is 1,2,3,4)
longestConsecutive(new int[]{});                       // -> 0  (empty input)
```
In this example: the set is `{100, 4, 200, 1, 3, 2}`. For `x=1`, `set.contains(0)`
is false, so `1` is a run start; the `while` loop then finds `2, 3, 4` all
present, giving `len=4`. For `x=4`, `set.contains(3)` is `true`, so `4` is
**skipped** as a start — it will be counted as part of the run that starts at
`1`. Because every number is only ever extended-from once it's identified as a
run start, and every number is only ever visited by the inner `while` loop
once total (each number belongs to exactly one run), the total work across
*all* iterations of the outer loop is O(n), even though there's a nested loop
in the code. This beats sorting first (`Arrays.sort` then a linear scan for
consecutive runs), which is O(n log n).

### Why it's useful
This "only enter the inner loop from a boundary condition" trick — checking a
neighbor's absence before doing expensive work — is a general technique for
turning an apparently-nested-loop algorithm into a linear one whenever you can
prove each element is only ever processed by the inner loop a constant number
of times in total.

## 6. Pattern: index / last-seen maps

**Definition**: map each value to the most recent index (or timestamp) at
which it was seen, so that on each new occurrence you can immediately compute
"how far back was the last one?" without rescanning.

```java
// Sliding set of the last k indices -> duplicate within distance k.
static boolean containsNearbyDuplicate(int[] a, int k) {
    Map<Integer, Integer> last = new HashMap<>();
    for (int i = 0; i < a.length; i++) {
        Integer j = last.get(a[i]);
        if (j != null && i - j <= k) return true;
        last.put(a[i], i);
    }
    return false;
}

containsNearbyDuplicate(new int[]{1, 2, 3, 1}, 3);   // -> true   (two 1s, distance 3 <= 3)
containsNearbyDuplicate(new int[]{1, 2, 3, 1}, 2);   // -> false  (distance 3 > 2)
```
In this example: the two `1`s sit at indices 0 and 3. With `k=3`,
`i - j = 3 - 0 = 3 <= 3`, so the function returns `true` as soon as it reaches
index 3. With `k=2`, the same gap of 3 exceeds `k`, so it's rejected and the
map simply updates `1`'s last-seen index to 3 and continues (there's nothing
left to compare against). Every value is checked against only its *most
recent* prior occurrence — O(n) time, O(min(n, distinct values)) space.

### Why it's useful
This is the map-based cousin of the Phase 2 variable-size sliding window:
instead of physically shrinking a window, you use a map to answer "was this
seen recently?" directly. It generalizes to "duplicate within a value range
*and* index range" (bucket the value by `value / (k+1)` and check adjacent
buckets), a common follow-up.

## 7. Pattern: prefix sum + map

**Definition**: combine a running prefix sum with a map of *how many times
each prefix-sum value has occurred so far* to count (or find) subarrays whose
elements sum to a target — in one O(n) pass, without ever materializing a
subarray.

- **Why it works**: `sum(i..j) = prefix[j] - prefix[i-1]`. A subarray ending
  at `j` sums to `k` exactly when some earlier prefix equals `prefix[j] - k`.
  So instead of trying every `(i, j)` pair (O(n²)), you ask the map "how many
  times has `running - k` occurred as a prefix sum before now?" — that count
  *is* the number of valid subarrays ending at `j`.
- **Seed the map with `{0: 1}`**: this accounts for a subarray that starts at
  index 0 itself (its "sum before it started" is 0, occurring once, before
  any elements are processed).

```java
// Prefix sum + map: count subarrays summing to k in O(n).
static int subarraySum(int[] a, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    count.put(0, 1);
    int running = 0, res = 0;
    for (int x : a) {
        running += x;
        res += count.getOrDefault(running - k, 0);
        count.merge(running, 1, Integer::sum);
    }
    return res;
}

subarraySum(new int[]{1, 1, 1}, 2);   // -> 2   ([1,1] at (0,1) and (1,2))
subarraySum(new int[]{1, 2, 3}, 3);   // -> 2   ([1,2] and [3])
```
In this example for `{1,1,1}`, `k=2`: prefixes are `1, 2, 3` after each
element. At `running=2` (index 1), `count.getOrDefault(0, 0)` is 1 (the seeded
entry) → one subarray found (`a[0..1]`). At `running=3` (index 2),
`count.getOrDefault(1, 0)` is 1 (prefix `1` occurred at index 0) → a second
subarray found (`a[1..2]`). Total: 2, matching the assertion.

### Why it's useful
This exact combination — prefix sum + frequency map — reappears any time the
question is phrased as "count/find subarrays matching some running-total
condition": sum equals k, sum divisible by k (key on `running % k` instead),
equal number of 0s and 1s (key on `running` after mapping 0→-1), and so on.
It's one of the highest-leverage patterns in array/hashing interviews because
it turns an O(n²) subarray enumeration into a single O(n) pass.

## 8. Custom objects as hash keys

**Definition**: any type used as a `HashMap`/`HashSet` key must correctly
implement `hashCode()` and `equals()` together, and its hash-relevant state
must not change while it's stored as a key — otherwise lookups silently fail.

- **The contract**: `a.equals(b) == true` implies `a.hashCode() == b.hashCode()`.
  Java's default `Object.hashCode()`/`equals()` compare *identity* (memory
  address), which is almost never what you want for a value-like key (e.g. a
  `Point(x, y)` class) — you must override both.
- **The mutated-key bug**: if a key's fields used in `hashCode()` change after
  insertion, the entry is still physically sitting in its *old* bucket, but a
  later `get`/`containsKey` call recomputes the hash from the object's
  *current* (mutated) state and looks in the *new* (wrong) bucket — the entry
  becomes permanently unreachable through normal lookup, even though
  `map.values()` would still show it during iteration.

```java
class Point {
    int x, y;
    Point(int x, int y) { this.x = x; this.y = y; }

    @Override public boolean equals(Object o) {
        if (!(o instanceof Point p)) return false;
        return x == p.x && y == p.y;
    }
    @Override public int hashCode() { return Objects.hash(x, y); }
}

Map<Point, String> labels = new HashMap<>();
Point p = new Point(1, 2);
labels.put(p, "origin-ish");
labels.get(new Point(1, 2));   // -> "origin-ish"  (different object, equals() says same key)

p.x = 99;                       // MUTATING a stored key's hash-relevant field
labels.get(new Point(99, 2));   // -> null!  (looked in the bucket for hash(99,2),
                                 //             but the entry still lives in the bucket for hash(1,2))
labels.get(p);                  // -> null!  (same reason — p's hash changed, bucket didn't move)
```
In this example: the first `get` succeeds because `equals()` correctly
compares field values, not identity, and the key's hash hadn't changed since
insertion. After `p.x = 99`, both subsequent `get` calls fail — not because
the entry was removed, but because the map is now looking in the wrong bucket
for it. This is why keys should be treated as **effectively immutable** while
stored in a map or set.

### Why it's useful
This is a real production bug class, not just a trivia question — caching
layers, deduplication sets, and graph "visited" sets built from mutable
domain objects are classic places this bites. The fix is either to make key
types immutable, or to key by a stable derived value (an ID string/long)
instead of the mutable object itself.

## 9. When *not* to reach for a hash map

| Situation | Better tool | Why |
|---|---|---|
| Need sorted iteration, range queries, floor/ceiling/first/last | `TreeMap`/`TreeSet` | Balanced BST (red-black tree) keeps keys ordered; O(log n) ops, but every op is ordered-aware |
| Need to preserve insertion order (or LRU access order) while iterating | `LinkedHashMap`/`LinkedHashSet` | Hash table + doubly linked list of entries in insertion/access order; still ~O(1) ops |
| Small, fixed, contiguous key domain (lowercase letters, bytes) | Plain array | Direct indexing beats hashing overhead; O(1) with a tiny constant, no boxing |
| Keys are mutable objects whose identity/state changes after insertion | Immutable wrapper or a stable derived key (ID) | Avoids the mutated-key bug above |
| Extremely tight memory budget, duplicate detection only | Bit set / bloom filter, or sort-first | A `HashSet<Integer>` has real per-entry overhead (node objects, boxing); a `BitSet` or sorting in place can be far more memory-efficient |

### `HashMap` vs `TreeMap` vs `LinkedHashMap`

| | `HashMap` | `TreeMap` | `LinkedHashMap` |
|---|---|---|---|
| Backing structure | Array of buckets (hash table) | Red-black tree | Hash table + doubly linked list |
| Iteration order | Unspecified/undefined | Sorted by key (natural or `Comparator`) | Insertion order (or access order, if configured) |
| `get`/`put`/`remove` | O(1) average, O(log n) worst case (treeified bucket) | O(log n) always | O(1) average |
| Extra ops | — | `firstKey`, `lastKey`, `floorKey`, `ceilingKey`, `headMap`, `tailMap` | `removeEldestEntry` hook (build an LRU cache) |
| Use when | Order doesn't matter, want max speed | Need sorted/range queries | Need predictable iteration order or an LRU policy |

## 10. Related structure: `LinkedHashMap` for LRU caches

**Definition**: `LinkedHashMap` is a `HashMap` that additionally threads every
entry through a doubly linked list, so iteration order is either **insertion
order** (default) or **access order** (pass `accessOrder=true` to the
3-argument constructor, which moves an entry to the end of the list on every
`get`, not just `put`). Overriding `removeEldestEntry` lets it auto-evict its
oldest entry, which is exactly the eviction policy an LRU cache needs.

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    LRUCache(int capacity) {
        super(16, 0.75f, true);   // accessOrder=true: get() moves entry to "most recent"
        this.capacity = capacity;
    }
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;   // called automatically after every put()
    }
}

LRUCache<Integer, String> cache = new LRUCache<>(2);
cache.put(1, "a"); cache.put(2, "b");
cache.get(1);          // touches 1 -> now most-recently-used; order is now [2, 1]
cache.put(3, "c");     // capacity exceeded -> evicts eldest (2), NOT 1
cache.containsKey(2);  // -> false (evicted)
cache.containsKey(1);  // -> true  (was touched, survived)
```
In this example: because the cache was constructed with `accessOrder=true`,
calling `get(1)` reorders the internal linked list so `1` is no longer the
least-recently-used entry. When `put(3, "c")` pushes the map's size to 3
(over capacity 2), `removeEldestEntry` returns `true` and `LinkedHashMap`
automatically removes the *actual* eldest entry at that point — `2`, not `1`
— giving true LRU semantics with O(1) get/put.

An alternative hand-rolled version (common in interviews, since you can't
always assume `LinkedHashMap` is allowed) pairs a `HashMap<K, Node>` for O(1)
lookup with a hand-built doubly linked list for O(1) move-to-front/evict — the
"hashing + linked list mashup" referenced in Phase 3.

### Why it's useful
LRU is one of the most common system-design-adjacent coding questions
precisely because it forces combining two structures to hit O(1) on *every*
operation — a map alone can't maintain order/recency, and a linked list alone
can't do O(1) lookup by key.

## 11. Related trick: XOR for "the one that appears once"

**Definition**: XOR (`^`) is commutative, associative, and self-inverse
(`a ^ a == 0`, `a ^ 0 == a`). XOR-ing every element of an array where every
value appears an even number of times except one cancels all the paired
values down to `0`, leaving exactly the unpaired value — no hash set needed.

```java
static int singleNumber(int[] a) {
    int result = 0;
    for (int x : a) result ^= x;
    return result;
}

singleNumber(new int[]{4, 1, 2, 1, 2});   // -> 4
```
In this example: XOR-ing in any order, `1 ^ 1 = 0` and `2 ^ 2 = 0` cancel out,
leaving `0 ^ 4 = 4`. This is O(n) time like the `HashSet`-based approach, but
**O(1) space** instead of O(n) — a common "can you do it without extra space"
follow-up to a hashing solution.

### Why it's useful
It demonstrates that hashing/sets are a *general* tool, not always the
*optimal* one — bit tricks can solve certain hashing-shaped problems
(duplicates, uniqueness) with no auxiliary memory at all when the structure of
the problem (pairing, XOR-cancellation) allows it.

## 12. Related pattern: Top-K frequent elements

**Definition**: count frequencies first (pattern 3 above), then select the K
elements with the highest counts — either with a fixed-size min-heap, or with
bucket sort keyed by frequency.

| Approach | Time | Space | Notes |
|---|---|---|---|
| Sort all `(element, count)` pairs by count | O(n log n) | O(n) | Simplest, not optimal |
| Min-heap of size K (`PriorityQueue`) | O(n log k) | O(n) | Push every `(count, element)`, pop when size > k; heap always holds the current top-k candidates |
| Bucket sort by frequency | O(n) | O(n) | Frequencies are bounded by `n`, so `bucket[freq]` (a `List` per possible frequency 0..n) lets you collect the top-k by scanning buckets from `n` down to `0` |

```java
static List<Integer> topKFrequent(int[] a, int k) {
    Map<Integer, Integer> count = new HashMap<>();
    for (int x : a) count.merge(x, 1, Integer::sum);

    List<Integer>[] buckets = new List[a.length + 1];   // index = frequency
    for (Map.Entry<Integer, Integer> e : count.entrySet()) {
        int freq = e.getValue();
        if (buckets[freq] == null) buckets[freq] = new ArrayList<>();
        buckets[freq].add(e.getKey());
    }

    List<Integer> res = new ArrayList<>();
    for (int freq = buckets.length - 1; freq >= 0 && res.size() < k; freq--) {
        if (buckets[freq] != null) res.addAll(buckets[freq]);
    }
    return res.subList(0, k);
}
```
In this example, bucket sort avoids the `O(log)` factor of a heap entirely:
because a frequency can never exceed `a.length`, an array of that many
buckets can hold every possible frequency, and scanning it from the top down
naturally yields elements in decreasing frequency order.

### Why it's useful
This is a very common "combine two structures" interview question — it tests
whether you reach past the first correct answer (sort everything) for the
better-bounded one (heap or bucket sort), and it reinforces that frequency
counting is almost always step 1 of a bigger pipeline, not the final answer.

## 13. Complexity summary

| Pattern / structure | Time | Space | Example problem |
|---|---|---|---|
| Complement lookup | O(n) | O(n) | Two Sum (unsorted) |
| Frequency counting (map) | O(n) | O(k) distinct keys | First unique character |
| Frequency counting (fixed array) | O(n) | O(1) | Valid anagram, small alphabet |
| Grouping by canonical key | O(n·k) – O(n·k log k) | O(n·k) | Group anagrams |
| Set membership / run detection | O(n) | O(n) | Longest consecutive sequence |
| Index / last-seen map | O(n) | O(min(n, distinct)) | Contains duplicate within k |
| Prefix sum + map | O(n) | O(n) | Subarray sum equals k |
| `TreeMap`/`TreeSet` ops | O(log n) | O(n) | Ordered/range queries |
| `LinkedHashMap` LRU cache | O(1) avg per op | O(capacity) | LRU cache design |
| XOR trick | O(n) | **O(1)** | Single number |
| Top-K via heap | O(n log k) | O(n) | Top K frequent elements |
| Top-K via bucket sort | O(n) | O(n) | Top K frequent elements (optimal) |

Most hashing solutions in this phase are **O(n) time, O(n) space** — the core
trade being memory for speed. When space is constrained, the fallback is
often sort-first (O(n log n) time, O(1)/O(log n) extra space) or a bit/math
trick (XOR, cyclic sort for values known to be in range `1..n`).

## Key Takeaways
- A hash map gives O(1) *average* insert/lookup/delete by bucketing on
  `hashCode()` and resolving collisions via `equals()` (chained, treeified
  past 8 entries in a large-enough table); worst case degrades to O(n) (or
  O(log n) treeified) under heavy collisions.
- The recurring instinct across nearly every pattern here: **spend O(n) space
  to buy O(n) time**, converting an O(n²) brute-force scan into a single pass.
- Six shapes cover almost every hashing problem: complement lookup, frequency
  counting, canonical-key grouping, set-membership run detection, last-seen
  index tracking, and prefix-sum-plus-map.
- Reach for `TreeMap`/`TreeSet` instead when you need order (range, floor/
  ceiling, sorted iteration); reach for `LinkedHashMap` when you need
  insertion/access order (LRU caches); reach for a plain array instead of a
  map when the key domain is small and fixed.
- Custom objects used as keys must implement `hashCode`/`equals` together and
  must not be mutated (in hash-relevant fields) while stored — a mutated key
  makes its own entry unreachable.
- Some hashing-shaped problems have an O(1)-space bit-trick alternative (XOR
  for "find the single non-duplicate") — always mention it as a follow-up
  optimization if the interviewer asks "can you avoid extra space?"
