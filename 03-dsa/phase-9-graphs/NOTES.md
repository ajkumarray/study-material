<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · heaps](../phase-8-heaps/NOTES.md) | [Phase 10 · sorting searching ➡](../phase-10-sorting-searching/NOTES.md)
<!-- /nav -->

# Phase 9 — Graphs: Notes

All code below is from `GraphPatterns.java` in this directory (run with `java -ea GraphPatterns.java`) unless a section is marked "not implemented in this file" — those round out the picture with classic follow-up problems.

The example undirected graph used throughout: edges `0-1, 0-2, 1-3, 2-3, 3-4`.

```
0 --- 1
|     |
2 --- 3 --- 4
```

## 1. Graph Representations

A **graph** is a set of vertices (nodes) plus a set of edges connecting them. Graphs can be **directed** (edges have a direction, `u -> v`) or **undirected** (edges go both ways), **weighted** (edges carry a cost) or **unweighted**, and **cyclic** or **acyclic** (a graph with no cycles). A tree (Phase 7) is a special case: a connected, acyclic graph — which is exactly why DFS/BFS from Phase 7 generalize directly to graphs, with one crucial addition: a **visited set**, because unlike a tree, a graph can have cycles and multiple distinct paths between the same two nodes, so naive recursion/traversal without tracking visited nodes can loop forever.

- **Adjacency list**: `Map<node, List<neighbor>>` (or `List<List<Integer>>` when nodes are `0..n-1`). Space is O(V + E) — proportional to the actual number of vertices and edges, not V². This is the default choice for **sparse** graphs (most interview graphs), since it avoids wasting space on non-existent edges and iterating a node's neighbors only costs as much as that node's actual degree.
- **Adjacency matrix**: `boolean[n][n]` (or `int[n][n]` for weighted edges). Space is O(V²) regardless of how many edges actually exist. The payoff is O(1) edge-existence checks (`matrix[u][v]`) — useful for **dense** graphs where most possible edges exist, or when "does an edge exist between u and v" is queried far more often than "iterate u's neighbors."
- **Implicit graphs (grids/matrices)**: a 2D grid is itself a graph where each cell is a node and its neighbors are the up/down/left/right (or diagonal) adjacent cells — no explicit adjacency list is ever built; the grid's own indices double as the graph structure.

```java
static void addEdge(Map<Integer, List<Integer>> g, int u, int v) {
    g.computeIfAbsent(u, k -> new ArrayList<>()).add(v);
    g.computeIfAbsent(v, k -> new ArrayList<>()).add(u);   // undirected: both ways
}
```

In this example: `computeIfAbsent` lazily creates the neighbor list for a node the first time an edge touches it — a common idiom for building adjacency lists incrementally. For an **undirected** edge, both directions must be recorded (`u`'s list gets `v`, and `v`'s list gets `u`); a **directed** edge would only add `v` to `u`'s list.

| | Adjacency list | Adjacency matrix |
|---|---|---|
| Space | O(V + E) | O(V²) |
| Edge existence check | O(degree) — scan neighbor list | **O(1)** — direct index |
| Iterate all neighbors of a node | **O(degree)** | O(V) — scan the full row |
| Best for | Sparse graphs (most interview problems) | Dense graphs, or frequent edge-existence checks |

**Why it's useful**: choosing the right representation up front avoids accidentally writing an O(V²) algorithm where O(V+E) was achievable, or vice versa wasting time hand-rolling sparse structures when a dense matrix would be simpler and the space cost is acceptable.

**Summary — Key Takeaways:**
- A tree is a connected, acyclic graph — DFS/BFS generalize directly, but graphs need a **visited set** to handle cycles and multiple paths.
- Adjacency list: O(V+E) space, the default for sparse graphs (most interview problems).
- Adjacency matrix: O(V²) space, O(1) edge checks — use for dense graphs.
- Grids are implicit graphs — cells are nodes, adjacency is positional, no explicit list needed.

---

## 2. BFS — Breadth-First Search

**BFS** explores a graph level by level using a **queue**, visiting every node at distance 1 from the start before any node at distance 2, and so on.

- **Key property**: on an **unweighted** graph, BFS finds the **shortest path** (fewest edges) from the start to every reachable node, because it explores in strict order of distance — the first time a node is reached is guaranteed to be via a shortest path.
- **Visited-on-enqueue**: mark a node visited the moment it's *added to the queue*, not when it's polled — marking on poll would allow the same node to be enqueued multiple times (once per incoming edge discovered before it's first processed), wasting work and, worse, potentially producing wrong distances.
- **Time Complexity**: O(V + E) — every vertex is enqueued/dequeued once, and every edge is examined once (from whichever endpoint discovers it first).
- **Space Complexity**: O(V) for the visited set and the queue, which in the worst case can hold an entire "frontier" of nodes at once (e.g., a star graph's center connects to all n-1 other nodes, all discovered in a single BFS step).

```java
static List<Integer> bfs(Map<Integer, List<Integer>> g, int start) {
    List<Integer> order = new ArrayList<>();
    Set<Integer> visited = new HashSet<>();
    Queue<Integer> q = new ArrayDeque<>();
    q.offer(start); visited.add(start);
    while (!q.isEmpty()) {
        int node = q.poll();
        order.add(node);
        for (int nb : g.getOrDefault(node, List.of())) {
            if (visited.add(nb)) q.offer(nb);   // add returns false if already present
        }
    }
    return order;
}
// bfs(g, 0) -> [0, 1, 2, 3, 4]
```

In this example: starting from `0`, both `1` and `2` are one edge away and get discovered (and enqueued) during the very first iteration, before either is polled — that's what makes this "level by level." `3` is discovered next (reachable from both `1` and `2`, but only enqueued the first time, thanks to `visited.add()`'s built-in duplicate check), and finally `4` is discovered from `3`.

### Shortest path length via BFS

To find the shortest path *distance* (not just reachability), pair each queued node with its distance from the start, incrementing by 1 with each hop:

```java
static int shortestUnweighted(Map<Integer, List<Integer>> g, int start, int target) {
    Set<Integer> visited = new HashSet<>();
    Queue<int[]> q = new ArrayDeque<>();        // {node, distance}
    q.offer(new int[]{start, 0}); visited.add(start);
    while (!q.isEmpty()) {
        int[] cur = q.poll();
        if (cur[0] == target) return cur[1];
        for (int nb : g.getOrDefault(cur[0], List.of()))
            if (visited.add(nb)) q.offer(new int[]{nb, cur[1] + 1});
    }
    return -1;
}
// shortestUnweighted(g, 0, 4) -> 3   (0->1->3->4 or 0->2->3->4, both 3 edges)
```

**Why it's useful**: BFS is the foundation for "fewest steps/moves" problems whenever each step has equal cost — word ladder, minimum knight moves, rotting oranges, sliding puzzle, maze shortest path. Any time a problem says "minimum number of X to reach Y" with uniform-cost steps, BFS is the tool.

**Summary — Key Takeaways:**
- BFS explores level by level via a queue; on unweighted graphs, this guarantees shortest-path-by-edge-count.
- Mark visited when *enqueuing*, not when polling — this is the #1 correctness detail that prevents duplicate work and wrong distances.
- O(V+E) time; O(V) space, potentially holding an entire frontier at once.

---

## 3. DFS — Depth-First Search

**DFS** explores as deep as possible along each branch before backtracking, using either recursion (the call stack acts as the stack) or an explicit `Stack`.

- **Key property**: DFS doesn't find shortest paths the way BFS does (it may find *a* path before the *shortest* one), but it's the natural fit for anything about **structure**: connectivity, cycle detection, topological ordering, and existence checks ("is there *any* path from A to B").
- **The visited set matters differently here than in BFS**: without it, DFS on a graph with a cycle would recurse forever, re-visiting the same nodes in a loop.
- **Time Complexity**: O(V + E) — same reasoning as BFS.
- **Space Complexity**: O(V) worst case for the recursion/explicit stack (a graph that's essentially one long path visits all V nodes before any backtracking happens).

```java
static void dfsH(Map<Integer, List<Integer>> g, int node, Set<Integer> visited, List<Integer> order) {
    if (!visited.add(node)) return;             // already seen -> stop (handles cycles)
    order.add(node);
    for (int nb : g.getOrDefault(node, List.of())) dfsH(g, nb, visited, order);
}
// dfs(g, 0).size() -> 5   (visits all 5 reachable nodes, order depends on adjacency-list ordering)
```

In this example: `visited.add(node)` doubles as both the "have I seen this?" check and the act of marking it seen — if `add` returns `false` (already present), the function returns immediately, which is exactly what prevents infinite recursion on a graph like this one that actually contains a cycle (`0-1-3-2-0`).

| | BFS | DFS |
|---|---|---|
| Data structure | Queue | Recursion (call stack) or explicit `Stack` |
| Finds shortest path (unweighted)? | **Yes** | No (finds *a* path, not necessarily shortest) |
| Natural fit for | Fewest-steps problems, level-order | Connectivity, cycles, topological order, exhaustive path exploration |
| Memory pattern | Can hold a whole "frontier" at once | Holds one path's worth of depth at a time |
| Time | O(V+E) | O(V+E) |

**Why it's useful**: DFS's "go deep, backtrack" shape is exactly what powers flood-fill (section 4), cycle detection (section 5), and topological sort (section 6) — recognizing when a problem is fundamentally about *structure* rather than *shortest distance* is what tells you to reach for DFS over BFS.

**Summary — Key Takeaways:**
- DFS goes deep via recursion/explicit stack; O(V+E) time, O(V) worst-case space.
- Doesn't guarantee shortest paths — use BFS for that. DFS is for structure: connectivity, cycles, ordering, existence.
- `visited` prevents infinite loops on cyclic graphs — mark it the instant a node is first entered.

---

## 4. Connected Components / Flood Fill

**Connected components**: groups of nodes where every node in a group is reachable from every other node in that same group, but not from nodes in a different group. Counting components (or exploring one fully) is DFS/BFS from every unvisited node, or equivalently Union-Find (section 8).

- **Grid flood-fill** ("number of islands"): treat the grid as an implicit graph and DFS (or BFS) outward from each unvisited "land" cell, marking every reachable land cell as visited (here, by mutating it to `'0'` in place — "sinking" it) so it's never counted or explored again. Each DFS call that *starts* from a still-unvisited land cell represents discovering one new island.

```java
static int numIslands(char[][] grid) {
    int count = 0;
    for (int r = 0; r < grid.length; r++)
        for (int c = 0; c < grid[0].length; c++)
            if (grid[r][c] == '1') { flood(grid, r, c); count++; }
    return count;
}
static void flood(char[][] grid, int r, int c) {
    if (r < 0 || c < 0 || r >= grid.length || c >= grid[0].length || grid[r][c] != '1') return;
    grid[r][c] = '0';                            // sink it -> mark visited
    flood(grid, r + 1, c); flood(grid, r - 1, c);
    flood(grid, r, c + 1); flood(grid, r, c - 1);
}
// grid:
// 1 1 0 0
// 1 0 0 1
// 0 0 1 1
// numIslands(grid) -> 2   (top-left blob of four 1s, and the bottom-right blob of three 1s)
```

In this example: the outer double loop scans every cell; the moment it finds an unvisited `'1'`, it calls `flood`, which recursively sinks every land cell reachable via up/down/left/right moves — turning the entire connected blob to `'0'` in one call. Because the outer loop only increments `count` when it *starts* a new flood from a still-`'1'` cell, and flood-fill guarantees the whole connected blob gets sunk before the loop moves on, each connected blob is counted exactly once. The top-left `1,1` / `1,0` blob (cells `(0,0),(0,1),(1,0)`) is one island; the bottom-right `1,1` / `1,1` blob (cells `(1,3),(2,2),(2,3)`) is the second.

- **Time Complexity**: O(rows × cols) — every cell is visited (and sunk) at most once across the whole algorithm, even though `flood` is called once per cell in the outer loop (the vast majority of those calls immediately return because the cell is no longer `'1'`).
- **Space Complexity**: O(rows × cols) worst case for the recursion stack, if the entire grid is one connected blob of land.

**Why it's useful**: "count groups / regions / connected clusters" is one of the most common graph-shaped interview problems, and it's frequently disguised as a grid problem ("number of islands," "max area of island," "surrounded regions," "friend circles / number of provinces") rather than stated as an explicit graph — recognizing the grid *as* a graph is the key insight.

**Summary — Key Takeaways:**
- Connected components: DFS/BFS from every unvisited node (or Union-Find), counting how many times a traversal has to *start* fresh.
- Grid flood-fill is DFS/BFS on an implicit graph — no adjacency list needed, just 4-directional (or 8-directional) neighbor checks with bounds validation.
- O(rows × cols) time/space for grid flood-fill — every cell is processed once overall, despite being "visited" from the outer loop's perspective once per cell.

---

## 5. Cycle Detection

Cycle detection differs meaningfully between undirected and directed graphs, because what counts as "going backward" is different.

### Undirected graphs

- **Approach**: DFS, tracking each node's immediate parent (the node you arrived *from*). If DFS reaches a neighbor that's already visited **and that neighbor isn't the parent you just came from**, you've found a back edge to some earlier ancestor — a cycle. (Ignoring the parent is essential: in an undirected graph, the edge you just traversed to get here always looks like "a visited neighbor" from the current node's perspective, since the edge goes both ways — that's not a cycle, just the edge you came in on.)
- **Multiple components**: the graph might not be fully connected, so the outer loop must try DFS from every still-unvisited node, not just node 0.

```java
static boolean cycleDfs(Map<Integer, List<Integer>> g, int node, int parent, Set<Integer> visited) {
    visited.add(node);
    for (int nb : g.getOrDefault(node, List.of())) {
        if (!visited.contains(nb)) { if (cycleDfs(g, nb, node, visited)) return true; }
        else if (nb != parent) return true;      // visited & not parent -> back edge -> cycle
    }
    return false;
}
// hasCycleUndirected(triangle: 0-1, 1-2, 2-0) -> true
// hasCycleUndirected(tree: 0-1, 0-2, 1-3)     -> false   (a real tree has no cycles)
// hasCycleUndirected(g: 0-1,0-2,1-3,2-3,3-4)  -> true    (0-1-3-2-0 is a cycle)
```

In this example: for the triangle `0-1-2-0`, DFS from `0` visits `1` (parent `0`), then from `1` visits `2` (parent `1`), then from `2` examines its neighbor `0` — which is visited, and `0 != parent(1)`, so a cycle is correctly reported. For the acyclic tree, DFS never encounters a visited non-parent neighbor, so it correctly reports no cycle.

### Directed graphs

- **Approach** (not implemented in this file, but the natural extension): a plain "visited" set is insufficient for directed graphs, because reaching an already-visited node via a *different* path isn't necessarily a cycle (it could just be a shared descendant with multiple incoming paths — a **DAG** can have that shape validly). Instead, track three states per node: **white** (unvisited), **gray** (currently on the current DFS path, i.e. an ancestor in the recursion), **black** (fully finished, all its descendants explored). An edge to a **gray** node — meaning "back to something currently on the stack above me" — is what actually indicates a cycle; an edge to a black node is fine (it's a legitimate cross-edge to an already-fully-explored, non-ancestor part of the graph).
- **Alternative**: run topological sort (section 6) — if it can't order all n nodes, the leftover nodes are involved in a cycle. This is often simpler to implement than three-coloring, and it doubles as the topological-sort algorithm itself.

| | Undirected cycle check | Directed cycle check |
|---|---|---|
| What indicates a cycle | Visited neighbor that isn't the parent | Edge to a **gray** (in-progress) node |
| Why "visited but not cycle" can happen | N/A (parent check handles it) | A DAG can have multiple paths converging on the same descendant — visiting a **black** node isn't a cycle |
| Simpler alternative | — | Topological sort fails to order all nodes |

**Why it's useful**: cycle detection underlies deadlock detection (a directed "waits-for" graph with a cycle means a deadlock), dependency validation (circular imports, circular build dependencies), and is a direct building block for topological sort's correctness check.

**Summary — Key Takeaways:**
- Undirected: a visited neighbor that isn't your immediate parent means a cycle — DFS, one pass, try every unvisited component.
- Directed: need three-coloring (white/gray/black) — a visited-but-not-yet-finished ("gray") node means a cycle; a fully-finished ("black") node does not.
- Directed cycle detection is equivalent to "topological sort fails to place all nodes" — often the simpler mental model.

---

## 6. Topological Sort

A **topological sort** produces a linear ordering of a **DAG** (directed acyclic graph) such that for every directed edge `u -> v`, `u` appears before `v` in the ordering. It only exists for DAGs — a cyclic graph has no valid topological order, since some pair of nodes would need to come both before and after each other.

- **Kahn's algorithm (BFS-based)**: compute the **in-degree** (number of incoming edges) of every node. Start with all nodes that have in-degree 0 (no unmet dependencies) in a queue. Repeatedly poll a node, add it to the result, and decrement the in-degree of each of its neighbors — any neighbor whose in-degree drops to 0 as a result becomes newly "ready" and is enqueued. If the final result contains fewer than n nodes, some nodes never reached in-degree 0, which means they're part of a cycle (a cyclic sub-region can never have all its mutual dependencies satisfied).
- **Time Complexity**: O(V + E) — every node is enqueued/dequeued once, and every edge is examined once (when decrementing its target's in-degree).

```java
static List<Integer> topoSort(int n, int[][] edges) {
    List<List<Integer>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    int[] indeg = new int[n];
    for (int[] e : edges) { adj.get(e[1]).add(e[0]); indeg[e[0]]++; }   // e = {node, prereq}

    Queue<Integer> q = new ArrayDeque<>();
    for (int i = 0; i < n; i++) if (indeg[i] == 0) q.offer(i);   // no dependencies -> ready
    List<Integer> order = new ArrayList<>();
    while (!q.isEmpty()) {
        int node = q.poll();
        order.add(node);
        for (int nb : adj.get(node)) if (--indeg[nb] == 0) q.offer(nb);
    }
    return order.size() == n ? order : List.of();   // incomplete => a cycle exists
}
// prereqs = {{1,0},{2,0},{3,1},{3,2}}   (course 1 needs 0; course 2 needs 0; course 3 needs 1 AND 2)
// topoSort(4, prereqs) -> a valid order with course 0 first, course 3 last
//   e.g. [0, 1, 2, 3]  (0 has no prereqs, unlocks 1 and 2; 3 needs both 1 and 2 done)
```

In this example: the edge encoding here is `{course, prerequisite}`, so `adj.get(e[1]).add(e[0])` builds an edge from the *prerequisite* to the *course that depends on it* (`0 -> 1`, `0 -> 2`, `1 -> 3`, `2 -> 3`), and `indeg[e[0]]++` counts how many prerequisites each course still needs. Course `0` starts with in-degree 0 (no prerequisites) and is the only node ready initially; processing it decrements the in-degree of both `1` and `2` (each drops to 0, since their only prerequisite was `0`), making them both ready next; only once *both* `1` and `2` have been processed does `3`'s in-degree finally reach 0.

**Why it's useful**: topological sort is the standard model for build systems (compile dependencies in the right order), course scheduling (as in the worked example), task pipelines, and the "alien dictionary" style problem (derive a partial character ordering from word comparisons, then topologically sort the letters).

**Summary — Key Takeaways:**
- Topological sort only exists for DAGs; Kahn's algorithm both produces the order *and* detects a cycle (incomplete result = cycle) in one pass.
- Start with all in-degree-0 nodes; processing a node decrements its neighbors' in-degrees, feeding newly-ready nodes into the queue.
- O(V+E) time — same complexity class as plain BFS, since it's fundamentally the same "process, then unlock neighbors" shape.

---

## 7. Dijkstra's Algorithm — Weighted Shortest Path

**Dijkstra's algorithm** finds the shortest path from a single source to every other node in a **weighted** graph, as long as **all edge weights are non-negative**.

- **The greedy idea**: always expand the closest **not-yet-finalized** node next (using a min-heap to efficiently find it), and **relax** its outgoing edges — for each neighbor, check whether reaching it via the current node would be shorter than the best distance found so far, and update if so.
- **Why non-negative weights are required**: the algorithm finalizes a node's shortest distance the moment it's popped from the heap, on the assumption that no future (unexplored) path could possibly be shorter — which is only guaranteed to be true if all remaining edge weights are `>= 0`. A negative edge could retroactively create a shorter path through a node that's already been finalized, invalidating that assumption.
- **Time Complexity**: O((V + E) log V) — each of the V nodes' entries and each of the E edge relaxations can trigger a heap operation, each costing O(log V).

```java
static int[] dijkstra(int n, int[][] edges, int source) {
    List<List<int[]>> adj = new ArrayList<>();
    for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
    for (int[] e : edges) adj.get(e[0]).add(new int[]{e[1], e[2]});   // {to, weight}, directed

    int[] dist = new int[n];
    Arrays.fill(dist, Integer.MAX_VALUE);
    dist[source] = 0;
    PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);   // {node, dist}
    pq.offer(new int[]{source, 0});
    while (!pq.isEmpty()) {
        int[] cur = pq.poll();
        int node = cur[0], d = cur[1];
        if (d > dist[node]) continue;                 // stale entry -> skip
        for (int[] edge : adj.get(node)) {
            int next = edge[0], nd = d + edge[1];
            if (nd < dist[next]) { dist[next] = nd; pq.offer(new int[]{next, nd}); }  // relax
        }
    }
    return dist;
}
// edges: 0->1(4), 0->2(1), 2->1(2), 1->3(1), 2->3(5)   (directed, weighted)
// dijkstra(4, edges, 0) -> dist[1] == 3, dist[3] == 4
```

In this example: the direct edge `0->1` costs `4`, but the path `0->2->1` costs `1 + 2 = 3` — shorter — so `dist[1]` ends up `3`, not `4`. The heap-driven processing order is what discovers this: after popping `0` (distance 0) and relaxing its edges, both `1` (tentative distance 4) and `2` (tentative distance 1) are in the heap; because it's a *min*-heap, `2` (the smaller tentative distance) is popped next, and relaxing `2`'s edges discovers the cheaper `0->2->1` path, updating `dist[1]` from `4` down to `3` and pushing a fresher, smaller heap entry for node `1`. When that fresher entry for `1` is eventually popped, its edge to `3` gives `dist[3] = 3 + 1 = 4`.

- **Stale entries**: because a node's distance can be updated (and a new, smaller entry pushed) *after* an older, larger entry for the same node is already sitting in the heap, the algorithm must check `if (d > dist[node]) continue;` — this skips processing an outdated heap entry whose distance no longer matches the best known distance, since a shorter path was already found and processed via a different entry.

| Shortest-path scenario | Algorithm | Complexity |
|---|---|---|
| Unweighted (or all edges equal cost) | **BFS** | O(V + E) |
| Weighted, all edges **non-negative** | **Dijkstra** | O((V+E) log V) |
| Weighted, negative edges allowed (no negative cycle) | **Bellman-Ford** | O(V·E) |
| All-pairs shortest paths | **Floyd-Warshall** | O(V³) |
| Weighted, with a good distance heuristic to a known target | **A\*** | Varies (heuristic-dependent, typically better than plain Dijkstra in practice) |

**Why it's useful**: Dijkstra is the standard "cheapest route" algorithm — network routing, flight/road cost minimization, and any "minimum cost to reach a target with weighted transitions" problem. Recognizing it as "BFS, but with a heap instead of a plain queue because edges aren't uniform cost" ties it directly back to the BFS mental model from section 2.

**Summary — Key Takeaways:**
- Dijkstra = greedy + min-heap: always finalize the closest not-yet-finalized node next, relaxing its edges.
- Requires non-negative edge weights — a negative edge can invalidate the "once popped, distance is final" assumption the algorithm relies on.
- O((V+E) log V); reduces to plain BFS's O(V+E) in spirit when all weights are equal.
- Negative edges → Bellman-Ford (O(VE)); all-pairs → Floyd-Warshall (O(V³)); with a target-directed heuristic → A*.

---

## 8. Union-Find (Disjoint Set Union)

**Union-Find** maintains a collection of disjoint (non-overlapping) sets, answering two questions efficiently as elements get merged over time: "are these two elements in the same set?" (`find`/`connected`) and "merge these two elements' sets into one" (`union`).

- **Two optimizations make it fast**:
  - **Path compression**: during `find`, re-point every node visited along the way directly to the root, so future `find` calls on those nodes are near-instant (they no longer need to walk the original chain).
  - **Union by rank**: when merging two sets, always attach the smaller/shallower tree under the root of the larger/taller one, rather than arbitrarily — this keeps the trees from growing unnecessarily tall.
- **Combined complexity**: with both optimizations, `find` and `union` run in amortized **O(α(n))** time, where α is the inverse Ackermann function — a function that grows so slowly it's effectively a constant (under 5) for any input size that could ever exist in practice. This is commonly just described as "near-O(1)."

```java
static class UnionFind {
    private final int[] parent, rank;
    private int count;
    UnionFind(int n) {
        parent = new int[n]; rank = new int[n]; count = n;
        for (int i = 0; i < n; i++) parent[i] = i;    // each its own root
    }
    int find(int x) {
        while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x]; }  // path compression
        return x;
    }
    void union(int a, int b) {
        int ra = find(a), rb = find(b);
        if (ra == rb) return;
        if (rank[ra] < rank[rb]) { int t = ra; ra = rb; rb = t; }   // attach smaller under larger
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) rank[ra]++;
        count--;
    }
    boolean connected(int a, int b) { return find(a) == find(b); }
    int components() { return count; }
}
// union(0,1); union(1,2); union(3,4)
// connected(0,2) -> true   (0 and 2 are now in the same set via 0-1-2)
// connected(0,3) -> false  (different sets: {0,1,2} and {3,4})
// components()   -> 2
```

In this example: `find(x)`'s loop walks up parent pointers to the root, and along the way rewrites `parent[x] = parent[parent[x]]` — a "halve the path" compression that isn't a full one-shot flattening but still dramatically shortens future lookups over repeated calls. `union` finds both elements' roots; if they're already the same root, nothing needs to happen (`return`); otherwise, the shallower tree (by `rank`) is attached under the deeper one's root, and `count` (the number of distinct components) decrements by one.

**Why it's useful**: Union-Find is the go-to structure for **dynamic** connectivity — situations where edges are added incrementally over time (rather than the whole graph being known up front), such as "process a stream of edges and answer connectivity queries as you go," cycle detection while building a graph edge by edge (an edge whose two endpoints are already `connected()` would create a cycle), and **Kruskal's algorithm** for minimum spanning trees (sort all edges by weight, greedily add each edge that doesn't connect two already-connected components, using Union-Find to check that in near-O(1)).

**Summary — Key Takeaways:**
- Union-Find answers "same group?" and "merge these groups" in amortized O(α(n)) ≈ O(1) with path compression + union by rank.
- Best suited for *dynamic* connectivity — edges/merges arriving incrementally, not a graph that's fully known upfront (for that, DFS/BFS is often simpler).
- Direct use cases: connected components on a stream of edges, cycle detection while adding edges, Kruskal's MST.

---

## 9. Minimum Spanning Tree — Not Implemented in This File

A **minimum spanning tree (MST)** connects all vertices of a weighted, undirected, connected graph using the minimum possible total edge weight, with no cycles (exactly `V - 1` edges for `V` vertices).

- **Kruskal's algorithm**: sort all edges by weight ascending; greedily add each edge to the MST *unless* it would connect two vertices already in the same component (which would create a cycle) — checked via Union-Find's `connected()` in near-O(1). Stop once `V - 1` edges have been added.
- **Prim's algorithm**: grow a single tree from an arbitrary starting vertex, at each step adding the cheapest edge that connects a vertex already in the tree to a vertex not yet in the tree — structurally similar to Dijkstra, using a min-heap of "crossing edges" (edges with one endpoint inside the growing tree, one outside).
- **Complexity**: both are O(E log V) with a binary heap / efficient Union-Find.

| | Kruskal | Prim |
|---|---|---|
| Core idea | Sort all edges, greedily add if no cycle (Union-Find) | Grow one tree, always add the cheapest crossing edge (heap) |
| Best for | Sparse graphs (few edges relative to vertices) | Dense graphs (many edges) |
| Data structure | Union-Find | Min-heap (like Dijkstra) |
| Complexity | O(E log E) (dominated by the sort) | O(E log V) |

**Why it's useful**: MST problems model "connect everything as cheaply as possible" — network cabling, road-building, circuit design — and this section directly ties together Union-Find (section 8) and the heap-based greedy pattern from Dijkstra (section 7) as the two standard ways to solve the same class of problem.

**Summary — Key Takeaways:**
- MST = connect all vertices with minimum total edge weight, no cycles, exactly V-1 edges.
- Kruskal: sort edges, greedily add via Union-Find cycle check — good for sparse graphs.
- Prim: grow one tree via a min-heap of crossing edges — good for dense graphs, structurally similar to Dijkstra.

---

## 10. Choosing the Right Graph Tool

| Problem signal | Reach for |
|---|---|
| Fewest edges / unweighted shortest path | **BFS** |
| Does a path exist? / explore everything / connectivity | **DFS** (or BFS) |
| Order dependencies so prerequisites come first | **Topological sort** (Kahn's / BFS on in-degrees) |
| Weighted shortest path, all weights non-negative | **Dijkstra** (min-heap) |
| Weighted shortest path, negative weights allowed | **Bellman-Ford** |
| Dynamic connectivity / incremental edge stream / grouping | **Union-Find** |
| Connect everything as cheaply as possible (MST) | **Kruskal** (Union-Find) or **Prim** (heap) |
| Grid problem (islands, maze, flood fill) | Treat cells as nodes; **DFS/BFS** with 4- (or 8-) directional neighbors |

**Why it's useful**: graphs are where much of this DSA track converges — the visited set is a hash set (Phase 5), BFS is built on a queue (Phase 4), DFS is recursion or an explicit stack (Phase 6), and Dijkstra/Prim are both heap-driven (Phase 8). Recognizing that a problem *is* a graph — even when it's disguised as a grid, a word-transformation chain, or a dependency list — is very often the entire difficulty of the problem; the algorithm itself, once the graph is identified, is usually one of the small set above.

**Summary — Key Takeaways:**
- Unweighted shortest → BFS. Weighted non-negative shortest → Dijkstra. Negative weights → Bellman-Ford.
- Structure/connectivity/ordering questions (not "shortest") → DFS or topological sort.
- Dynamic/incremental connectivity → Union-Find; "connect cheaply" → Kruskal/Prim.
- The hardest part is often recognizing the hidden graph in a non-obviously-graph-shaped problem.
