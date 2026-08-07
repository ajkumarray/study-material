<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · heaps](../phase-8-heaps/NOTES.md) | [Phase 10 · sorting searching ➡](../phase-10-sorting-searching/NOTES.md)
<!-- /nav -->

# Phase 9 — Graphs: Interview Q&A + Problems

⭐ = asked constantly.

**Q: BFS vs DFS — when do you use each?** ⭐⭐
BFS (queue): shortest path in an unweighted graph, level-by-level exploration, "minimum steps." DFS (recursion/stack): connectivity, cycle detection, topological sort, path existence, backtracking on graphs. Both O(V+E). BFS uses more memory (a whole frontier); DFS uses stack depth.

**Q: How do you represent a graph, and which do you pick?** ⭐
Adjacency list (map/array of neighbor lists) for sparse graphs — O(V+E) space, efficient iteration. Adjacency matrix for dense graphs or O(1) edge-existence checks — O(V²) space. Most interview graphs are sparse → adjacency list.

**Q: Number of islands / connected components.** ⭐⭐
Treat the grid as a graph; DFS/BFS flood-fill from each unvisited land cell, marking visited, counting starts. Or Union-Find. O(V+E) / O(rows·cols).

**Q: Detect a cycle in a graph.** ⭐
Undirected: DFS, and a visited neighbor that isn't the parent means a cycle. Directed: DFS with a recursion-stack/gray set (edge to an in-progress node = cycle), or a failed topological sort. Union-Find detects cycles as you add edges (endpoints already connected).

**Q: Topological sort / course schedule.** ⭐⭐
Order a DAG so dependencies come first. Kahn's algorithm: queue nodes with in-degree 0, emit them, decrement neighbors' in-degrees. If fewer than n nodes are emitted, there's a cycle (impossible schedule). O(V+E).

**Q: Dijkstra's algorithm — what, and its constraint?** ⭐⭐
Single-source shortest paths in a weighted graph with **non-negative** edges: a min-heap always expands the closest unfinalized node and relaxes its edges. O((V+E) log V). Fails with negative edges — use Bellman-Ford (O(VE)) there.

**Q: BFS vs Dijkstra?**
BFS finds shortest paths when all edges have equal weight (unweighted) in O(V+E). Dijkstra generalizes to non-negative weighted edges using a priority queue. On an unweighted graph they agree, but BFS is simpler/faster.

**Q: What is Union-Find and why is it fast?** ⭐
A disjoint-set structure for dynamic connectivity: `find` (which group) and `union` (merge groups). With path compression + union by rank, operations are near-constant (inverse-Ackermann). Uses: connected components, cycle detection, Kruskal's MST.

**Q: Minimum spanning tree — algorithms?**
Kruskal (sort edges, add the cheapest that doesn't form a cycle, using Union-Find) or Prim (grow a tree via a min-heap of crossing edges, like Dijkstra). Both O(E log V).

**Q: Word ladder / shortest transformation / rotting oranges / maze.**
Model states as graph nodes and valid moves as edges; BFS for the fewest-steps answer (multi-source BFS when several starts, like all rotten oranges at once).

**Q: Clone a graph / course schedule II / alien dictionary.**
Clone: DFS/BFS copying nodes with a visited map (old→new). Schedule II: return the topological order. Alien dictionary: derive edges from adjacent words, then topological sort — recognizing the hidden graph is the trick.
