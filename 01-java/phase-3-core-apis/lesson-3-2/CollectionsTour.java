import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/*
 * Lesson 3.2 — The Collections Framework
 *
 * THE MAP OF THE TERRITORY (interfaces -> main implementations):
 *
 *   Collection
 *   ├── List      ordered, indexed, duplicates OK
 *   │             -> ArrayList (default), LinkedList
 *   ├── Set       no duplicates
 *   │             -> HashSet (default), LinkedHashSet (keeps insertion order),
 *   │                TreeSet (sorted)
 *   └── Queue     processing order
 *                 -> ArrayDeque (FIFO/LIFO default), PriorityQueue (by priority)
 *
 *   Map (separate hierarchy — key -> value)
 *                 -> HashMap (default), LinkedHashMap (insertion order),
 *                    TreeMap (sorted by key)
 *
 * GOLDEN RULE: declare the INTERFACE, choose the IMPLEMENTATION:
 *   List<String> names = new ArrayList<>();
 */
public class CollectionsTour {

    public static void main(String[] args) {

        // ============================================================
        // 1. LIST — ordered, indexed, duplicates allowed
        // ============================================================
        System.out.println("=== List ===");

        List<String> stack = new ArrayList<>();     // resizable array (lesson 1.5's copyOf, automated)
        stack.add("java");
        stack.add("spring");
        stack.add("java");                          // duplicate: fine in a List
        stack.add(1, "docker");                     // insert at index (shifts the rest right)

        System.out.println("list      : " + stack);
        System.out.println("get(0)    : " + stack.get(0) + "   (O(1) — it's an array inside)");
        System.out.println("contains  : " + stack.contains("spring") + "  (O(n) — linear scan)");

        // ArrayList vs LinkedList in one breath:
        //   ArrayList : O(1) get(i); add at END amortized O(1); middle insert O(n) (shift)
        //   LinkedList: O(n) get(i) (walks nodes!); O(1) insert/remove AT A KNOWN NODE
        // In practice ArrayList wins almost always — cache locality beats
        // pointer chasing. LinkedList's real niche: Deque usage.

        // Immutable literals (Java 9+): great for constants and test data.
        List<Integer> fib = List.of(1, 1, 2, 3, 5);
        try {
            fib.add(8);
        } catch (UnsupportedOperationException e) {
            System.out.println("List.of() : immutable — add() threw UnsupportedOperationException");
        }

        // ============================================================
        // 2. THE ITERATION TRAP — fail-fast iterators
        // ============================================================
        System.out.println("\n=== fail-fast ===");

        List<String> langs = new ArrayList<>(List.of("java", "perl", "kotlin", "cobol"));

        // Removing DURING a for-each throws ConcurrentModificationException:
        try {
            for (String l : langs) {
                if (l.equals("perl")) langs.remove(l);   // structural change mid-iteration
            }
        } catch (java.util.ConcurrentModificationException e) {
            System.out.println("for-each remove -> ConcurrentModificationException (fail-fast!)");
        }

        // Right ways:
        langs.removeIf(l -> l.equals("perl"));           // 1) removeIf (best)
        Iterator<String> it = langs.iterator();          // 2) explicit iterator
        while (it.hasNext()) {
            if (it.next().equals("cobol")) it.remove();  //    iterator's OWN remove is safe
        }
        System.out.println("cleaned   : " + langs);

        // ============================================================
        // 3. SET — uniqueness (powered by equals/hashCode, lesson 2.5!)
        // ============================================================
        System.out.println("\n=== Set ===");

        Set<String> tags = new HashSet<>(List.of("api", "db", "api", "cache", "db"));
        System.out.println("HashSet   : " + tags + "   (dupes gone, order arbitrary)");

        Set<String> ordered = new java.util.LinkedHashSet<>(List.of("api", "db", "api", "cache"));
        System.out.println("LinkedHash: " + ordered + "   (insertion order kept)");

        Set<String> sorted = new TreeSet<>(List.of("api", "db", "cache"));
        System.out.println("TreeSet   : " + sorted + "   (sorted; O(log n) ops; needs Comparable)");

        // Classic use: dedupe a list, keeping order:
        List<String> deduped = new ArrayList<>(new java.util.LinkedHashSet<>(stack));
        System.out.println("deduped   : " + deduped);

        // ============================================================
        // 4. MAP — the most important data structure in programming
        // ============================================================
        System.out.println("\n=== Map ===");

        Map<String, Integer> wordCount = new HashMap<>();
        String sentence = "to be or not to be";
        for (String word : sentence.split(" ")) {
            wordCount.merge(word, 1, Integer::sum);      // the word-count one-liner
        }
        System.out.println("counts    : " + wordCount);

        // The essential API:
        System.out.println("get       : " + wordCount.get("be"));
        System.out.println("getOrDflt : " + wordCount.getOrDefault("java", 0));
        wordCount.putIfAbsent("stream", 0);
        wordCount.computeIfAbsent("lambda", k -> k.length());  // lazily create values

        // Iterating a map — entrySet is THE way (one pass, key+value together):
        Map<String, Integer> prices = new TreeMap<>(Map.of("kafka", 3, "redis", 2, "docker", 1));
        for (Map.Entry<String, Integer> e : prices.entrySet()) {
            System.out.println("  " + e.getKey() + " -> " + e.getValue());
        }
        // TreeMap printed sorted by key. LinkedHashMap would keep insertion
        // order — and can be tuned into an LRU cache (removeEldestEntry).

        // ============================================================
        // 5. QUEUE & DEQUE — processing pipelines and stacks
        // ============================================================
        System.out.println("\n=== Queue / Deque ===");

        Deque<String> queue = new ArrayDeque<>();        // FIFO: offer at tail, poll from head
        queue.offer("job-1");
        queue.offer("job-2");
        queue.offer("job-3");
        System.out.println("poll      : " + queue.poll() + ", then " + queue.poll() + "  (FIFO)");

        Deque<String> undo = new ArrayDeque<>();         // LIFO: same class as a STACK
        undo.push("typed 'a'");
        undo.push("typed 'b'");
        System.out.println("pop       : " + undo.pop() + "   (LIFO — use Deque, not legacy Stack)");
        // peek()/poll() return null when empty; element()/remove()/pop() THROW.

        PriorityQueue<Integer> pq = new PriorityQueue<>(Comparator.reverseOrder());
        pq.addAll(List.of(3, 41, 7, 12));
        System.out.print("priority  : ");
        while (!pq.isEmpty()) System.out.print(pq.poll() + " ");   // always the max first
        System.out.println("  (a heap — top is O(log n), NOT fully sorted inside)");

        // ============================================================
        // 6. SORTING with Comparators
        // ============================================================
        System.out.println("\n=== sorting ===");

        record Dev(String name, int exp) { }
        List<Dev> devs = new ArrayList<>(List.of(
                new Dev("ajay", 4), new Dev("meera", 9), new Dev("ravi", 4), new Dev("zoya", 1)));

        // Comparator.comparing + thenComparing: declarative multi-key sort.
        devs.sort(Comparator.comparing(Dev::exp).reversed()
                            .thenComparing(Dev::name));
        System.out.println("sorted    : " + devs);

        // Comparable = a type's NATURAL order (implement compareTo once);
        // Comparator = an EXTERNAL, ad-hoc order (define as many as you like).

        // ============================================================
        // 7. The autoboxing trap, now with collections
        // ============================================================
        System.out.println("\n=== autoboxing trap ===");

        List<Integer> nums = new ArrayList<>(List.of(1, 2, 3));
        nums.remove(Integer.valueOf(2));   // removes the VALUE 2
        System.out.println("remove(Integer.valueOf(2)) -> " + nums);
        nums.remove(0);                    // removes the element AT INDEX 0 — overload trap!
        System.out.println("remove(0)                  -> " + nums + "  (index, not value!)");
    }
}
