<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · heaps](../phase-8-heaps/NOTES.md) | [Phase 10 · sorting searching ➡](../phase-10-sorting-searching/NOTES.md)
<!-- /nav -->

# Phase 9 — Graphs: Interview Q&A + Problems

⭐ = asked constantly.

**Q: BFS vs DFS — when do you use each?** ⭐⭐

BFS (queue-based) explores level by level, and on an unweighted graph this guarantees the first time you reach any node is via a shortest path — so BFS is the tool for "fewest steps/edges" questions. DFS (recursion or an explicit stack) goes as deep as possible before backtracking, which is the natural fit for anything about **structure** rather than distance: connectivity, cycle detection, topological ordering, and "does any path exist" questions. Both run in O(V+E) time. The practical difference in memory: BFS can hold an entire "frontier" of nodes in its queue at once (worst case O(V), e.g. a star graph where the center connects to everything), while DFS's memory footprint tracks the depth of a single path at a time (also worst case O(V) for a graph shaped like one long chain, but the *pattern* of memory use differs).

```java
// BFS marks visited on ENQUEUE
if (visited.add(nb)) q.offer(nb);

// DFS marks visited on ENTRY (via recursion)
if (!visited.add(node)) return;
```

*Follow-up: Give an example where DFS would find a valid path but not the shortest one, while BFS would find the shortest.* In the example graph `0-1, 0-2, 1-3, 2-3, 3-4`, a DFS starting at `0` might explore `0 -> 1 -> 3 -> 4` (3 edges) — which happens to be optimal here — but on a graph where the DFS traversal order happens to wander through a long detour before reaching the target, DFS would return whatever path it stumbled onto first, which is not guaranteed to be shortest. BFS, by construction, always explores by increasing distance, so it can't return a longer path before a shorter one exists.

---

**Q: How do you represent a graph, and which do you pick?** ⭐

An adjacency list — `Map<node, List<neighbor>>` or `List<List<Integer>>` when nodes are integers `0..n-1` — for sparse graphs (most interview graphs): O(V+E) space, and iterating a node's neighbors only costs as much as that node's actual degree. An adjacency matrix — `boolean[n][n]` or `int[n][n]` for weights — for dense graphs, or whenever "does an edge exist between u and v" needs to be O(1): it costs O(V²) space regardless of how many edges actually exist, which is wasteful for sparse graphs but fine (or even preferable, for cache-friendliness) when the graph is genuinely dense.

*Follow-up: For a graph with 10,000 nodes and only 20,000 edges, which representation would you choose, and why?* Adjacency list — the graph is extremely sparse (average degree of 2), so an adjacency matrix would allocate 10,000² = 100 million cells, the overwhelming majority storing "no edge," while an adjacency list uses space proportional to the actual ~20,000 edges.

---

**Q: Number of islands / connected components.** ⭐⭐

Treat the grid as an implicit graph — each cell is a node, its neighbors are the up/down/left/right adjacent cells. Scan every cell; the moment you find an unvisited "land" cell, run DFS (or BFS) outward from it, marking every reachable land cell visited (here, by mutating it in place to `'0'` — "sinking" it) so the outer scan never revisits or recounts it. Each time the outer loop finds a still-unvisited land cell and *starts* a new flood, that's one new island.

```java
static void flood(char[][] grid, int r, int c) {
    if (r < 0 || c < 0 || r >= grid.length || c >= grid[0].length || grid[r][c] != '1') return;
    grid[r][c] = '0';
    flood(grid, r + 1, c); flood(grid, r - 1, c);
    flood(grid, r, c + 1); flood(grid, r, c - 1);
}
```

This is O(rows · cols) time — every cell is visited (and sunk) at most once across the entire algorithm, even though the outer loop calls `flood` once per cell (most of those calls immediately return because the cell is no longer `'1'`). Space is O(rows · cols) worst case for the recursion stack, if the grid is one giant connected blob. Union-Find is an equally valid alternative: union every pair of adjacent land cells, then `components()` gives the island count directly.

*Follow-up: How would this change for "max area of island" instead of just counting islands?* Have `flood` return the count of cells it sank (1 plus the sum of the four recursive calls' returns) instead of a `void` side effect, and track the maximum of these counts across all flood-fill calls in the outer loop.

*Follow-up: What if the grid used 8-directional adjacency (including diagonals) instead of 4?* Add four more recursive calls to `flood` for the diagonal neighbors `(r±1, c±1)` — the rest of the algorithm (bounds check, sink-on-visit, outer counting loop) is unchanged.

---

**Q: Detect a cycle in a graph.** ⭐

**Undirected**: DFS, tracking the parent you arrived from at each step; if you reach a neighbor that's already visited **and it isn't your immediate parent**, that's a back edge to an ancestor — a cycle. The parent check is essential, since in an undirected graph the edge you just traversed always makes the node you came from look like "an already-visited neighbor," which is not itself a cycle.

```java
static boolean cycleDfs(Map<Integer, List<Integer>> g, int node, int parent, Set<Integer> visited) {
    visited.add(node);
    for (int nb : g.getOrDefault(node, List.of())) {
        if (!visited.contains(nb)) { if (cycleDfs(g, nb, node, visited)) return true; }
        else if (nb != parent) return true;
    }
    return false;
}
```

**Directed**: a plain visited set isn't enough, because reaching an already-visited node via a different path is normal in a DAG (a valid convergence point, not a cycle). Use three-coloring: **white** (unvisited), **gray** (on the current DFS path — an active ancestor), **black** (fully explored, done). An edge to a **gray** node means a cycle (you've looped back onto your own current path); an edge to a **black** node is fine. The equivalent, often simpler, alternative is running topological sort (Kahn's algorithm) — if it can't place all n nodes, the leftovers are involved in a cycle.

*Follow-up: Union-Find can also detect cycles as edges are added — how, and for which graph type?* For undirected graphs: before adding an edge `(u, v)`, check `connected(u, v)` — if they're already in the same component, adding this edge would create a cycle (there's already a path between them). This only works cleanly for undirected graphs; Union-Find doesn't naturally capture edge direction, so it can't directly detect directed cycles.

---

**Q: Topological sort / course schedule.** ⭐⭐

Order a DAG so every dependency comes before whatever depends on it. Kahn's algorithm (BFS on in-degrees): compute every node's in-degree (number of incoming edges); seed a queue with all in-degree-0 nodes (nothing blocking them); repeatedly poll a node, append it to the result, and decrement the in-degree of each of its neighbors, enqueuing any neighbor whose in-degree just hit 0. If the final result has fewer than n nodes, some nodes never reached in-degree 0 — they're stuck in a cycle, meaning no valid schedule exists.

```java
static List<Integer> topoSort(int n, int[][] edges) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    int[] indeg = new int[n];
    for (int[] e : edges) { adj.get(e[1]).add(e[0]); indeg[e[0]]++; }
    Queue<Integer> q = new ArrayDeque<>();
    for (int i = 0; i < n; i++) if (indeg[i] == 0) q.offer(i);
    List<Integer> order = new ArrayList<>();
    while (!q.isEmpty()) {
        int node = q.poll();
        order.add(node);
        for (int nb : adj.get(node)) if (--indeg[nb] == 0) q.offer(nb);
    }
    return order.size() == n ? order : List.of();
}
```

O(V+E) — every node processed once, every edge examined once when decrementing its target's in-degree.

*Follow-up: "Course Schedule" (can you finish all courses?) vs "Course Schedule II" (return the actual order) — how do the solutions differ?* They're the same algorithm; "can you finish" just checks whether `topoSort` returns a list of size n (true) or an incomplete list (false, meaning a cycle exists), while "return the order" returns the list itself. No new algorithm is needed for the harder-sounding variant.

*Follow-up: How would you solve topological sort with DFS instead of Kahn's BFS approach?* Run DFS from every unvisited node; on finishing a node (postorder — after all its descendants have been fully explored), push it onto the *front* of a result list (or push onto a stack and reverse at the end). A node is only fully "finished" after everything it depends on transitively has already been placed, so building the order in reverse-postorder produces a valid topological sort.

---

**Q: Dijkstra's algorithm — what, and its constraint?** ⭐⭐

Single-source shortest paths in a weighted graph, requiring **all edge weights to be non-negative**. Greedy approach with a min-heap: repeatedly pop the closest not-yet-finalized node (the heap gives you this in O(log V)), and **relax** its outgoing edges — for each neighbor, check if reaching it through the current node beats the best distance found so far, and update + re-push if so.

```java
while (!pq.isEmpty()) {
    int[] cur = pq.poll();
    int node = cur[0], d = cur[1];
    if (d > dist[node]) continue;   // stale entry
    for (int[] edge : adj.get(node)) {
        int next = edge[0], nd = d + edge[1];
        if (nd < dist[next]) { dist[next] = nd; pq.offer(new int[]{next, nd}); }
    }
}
```

O((V+E) log V). It fails with negative edges because the algorithm finalizes a node's distance the instant it's popped, on the assumption nothing unexplored could possibly beat it — a negative edge could retroactively create a shorter route through an already-finalized node, breaking that assumption. Bellman-Ford (O(V·E)) handles negative edges correctly (and can detect negative cycles) by relaxing every edge V-1 times instead of relying on a greedy pop order.

*Follow-up: Why does the code check `if (d > dist[node]) continue;`?* Because a node's `dist` entry can be improved (and a fresh, smaller heap entry pushed) *after* an older, larger entry for that same node is already sitting in the heap — when that stale, larger entry is eventually popped, its distance no longer matches the current best known distance, so it's skipped rather than incorrectly re-processed.

*Follow-up: What data structure would you use if you needed to *decrease* a key already in the heap, rather than just pushing duplicates?* A heap supporting `decrease-key` (e.g., an indexed/Fibonacci heap) avoids the duplicate-entries-plus-staleness-check pattern entirely, giving a theoretically better O(E + V log V) bound — but in practice, Java's `PriorityQueue` doesn't support decrease-key directly, so the "push duplicates, skip stale entries on pop" trick shown here is the standard, pragmatic workaround.

---

**Q: BFS vs Dijkstra?**

BFS finds shortest paths when every edge has equal weight (or the graph is unweighted), in O(V+E), using a plain queue. Dijkstra generalizes this to non-negative *weighted* edges by replacing the plain queue with a min-heap ordered by cumulative distance, at O((V+E) log V). On a graph where every edge weight happens to be 1, running Dijkstra would produce the same answer as BFS, but BFS is strictly simpler and asymptotically faster (no log factor) — so always prefer BFS when weights are uniform, and only reach for Dijkstra when they genuinely differ.

*Follow-up: Could you simulate Dijkstra's behavior using only BFS, for a graph where edge weights are small non-negative integers (say, 0 to 10)?* Yes — this is "0-1 BFS" (for weights of only 0 or 1, using a deque and pushing 0-weight edges to the front, 1-weight edges to the back) generalized to small integer weights via "dial's algorithm" (bucket queues indexed by distance) — both avoid the log factor of a full binary heap by exploiting the small, bounded range of possible edge weights.

---

**Q: What is Union-Find and why is it fast?** ⭐

A disjoint-set structure for **dynamic connectivity**: `find(x)` returns which group `x` currently belongs to, and `union(a, b)` merges two groups into one. With two optimizations — **path compression** (during `find`, re-point visited nodes closer to the root so future lookups are faster) and **union by rank** (always attach the smaller/shallower tree under the larger/taller one's root, rather than arbitrarily) — both operations run in amortized **O(α(n))**, where α is the inverse Ackermann function, a function that grows so slowly it's under 5 for any n that could ever exist in practice, so it's commonly described as "near-O(1)."

```java
int find(int x) {
    while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; }
    return x;
}
```

Primary uses: connected components over a *stream* of edges (rather than a graph known fully up front, where a single DFS/BFS pass would be simpler), cycle detection while incrementally adding edges (checking `connected()` before each new edge), and Kruskal's MST algorithm.

*Follow-up: Why does `find` update `parent[x] = parent[parent[x]]` instead of pointing `x` directly at the root in one shot?* This "path halving" is a lighter-weight compression that still gives the same amortized complexity bound as full path compression (pointing every visited node directly at the root), but it's simpler to write as a single loop without a second pass. Either variant is acceptable and commonly used.

*Follow-up: When would you prefer a plain DFS/BFS-based connected-components pass over Union-Find?* When the entire graph is known upfront and static (no further edges arriving) — a single DFS/BFS sweep is O(V+E), simpler to reason about, and doesn't need the extra bookkeeping arrays. Union-Find earns its keep specifically when edges arrive incrementally and you need connectivity answers *between* additions.

---

**Q: Minimum spanning tree — algorithms?**

**Kruskal's algorithm**: sort all edges by weight ascending; greedily add each edge to the MST unless its two endpoints are already connected (which would create a cycle) — checked via Union-Find's `connected()` in near-O(1); stop once V-1 edges have been added. **Prim's algorithm**: grow a single tree starting from an arbitrary vertex, at each step adding the cheapest edge connecting a vertex already in the tree to one outside it — structurally identical to Dijkstra, using a min-heap of "crossing edges." Both run in O(E log V) (Kruskal's is more precisely O(E log E), dominated by the sort, but `log E` and `log V` are the same order of magnitude since `E` is at most `V²`).

*Follow-up: When would you prefer Kruskal over Prim, or vice versa?* Kruskal tends to be simpler to implement when the edge list is already available and the graph is sparse (fewer edges to sort). Prim tends to be preferred for dense graphs, especially with an adjacency-matrix representation, since it avoids sorting the (potentially much larger) full edge list up front.

*Follow-up: Does MST make sense on a directed graph?* No — the standard MST problem is defined for undirected graphs. The directed analogue (minimum arborescence / "Chu-Liu/Edmonds' algorithm") is a related but distinct problem with its own algorithm, rarely expected in a standard interview.

---

**Q: Word ladder / shortest transformation / rotting oranges / maze.**

All of these model states as graph nodes and valid single-step transitions as edges, then run BFS to find the fewest-steps answer — the "fewest transformations/moves/steps" phrasing is the direct signal for BFS from section 2. **Word ladder**: each word is a node; an edge connects two words differing by exactly one letter; BFS from the start word finds the shortest transformation sequence to the target. **Rotting oranges**: a **multi-source BFS** — seed the queue with *all* initially-rotten oranges simultaneously (not just one), then BFS outward level by level; the number of BFS levels processed is the time until all reachable fresh oranges rot (or -1 if some remain unreachable). **Maze / shortest path in a grid**: each open cell is a node, 4-directional moves are edges, BFS from start to target.

```java
// Multi-source BFS seed for rotting oranges:
Queue<int[]> q = new ArrayDeque<>();
for (int r = 0; r < grid.length; r++)
    for (int c = 0; c < grid[0].length; c++)
        if (grid[r][c] == 2) q.offer(new int[]{r, c});   // ALL rotten oranges start together
```

*Follow-up: Why does multi-source BFS give the correct answer for rotting oranges, rather than running single-source BFS from each rotten orange separately and taking a minimum?* Seeding all rotten oranges into the queue at once and processing level by level correctly models "every currently-rotten orange spreads simultaneously each minute" — running separate single-source BFS passes would either be needlessly repeated work or require carefully combining results, whereas multi-source BFS gets the correct simultaneous-spread semantics for free as a natural consequence of level-order processing.

---

**Q: Clone a graph / course schedule II / alien dictionary.**

**Clone a graph**: DFS (or BFS) from the given start node, maintaining a `Map<oldNode, newNode>` to avoid infinite recursion on cycles and to ensure each node is only cloned once; when visiting a neighbor, check the map first — if a clone already exists, reuse it; otherwise create it and recurse. **Course Schedule II**: return the topological order itself (see the topological sort question above — same algorithm, just returning the built list instead of a boolean). **Alien dictionary**: given a list of words assumed to be sorted according to some unknown alien alphabet, derive a partial ordering between letters by comparing each pair of adjacent words (the first differing character tells you "this letter comes before that letter" in the alien alphabet), build those inferred relationships into a graph, then topologically sort the letters — the sorted result is the alien alphabet's order (or "impossible" if the derived graph has a cycle, meaning the input contradicts itself).

```java
// Clone graph sketch
static Node clone(Node node, Map<Node, Node> visited) {
    if (node == null) return null;
    if (visited.containsKey(node)) return visited.get(node);
    Node copy = new Node(node.val);
    visited.put(node, copy);
    for (Node nb : node.neighbors) copy.neighbors.add(clone(nb, visited));
    return copy;
}
```

*Follow-up: What's the hardest part of the alien dictionary problem, algorithmically?* It's not the topological sort itself (that's a direct application of section 6) — it's correctly deriving the edges from the word list. Only the *first* differing character between two adjacent words gives ordering information; if one word is a prefix of the next (e.g., `"abc"` before `"ab"`), that's actually an invalid/impossible ordering regardless of any derived edges, since a valid dictionary never puts a longer word with a shorter word as its prefix later. Recognizing and handling that edge case is usually what separates a correct solution from an almost-correct one.
