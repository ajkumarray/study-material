<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 8 · heaps](../phase-8-heaps/NOTES.md) | [Phase 10 · sorting searching ➡](../phase-10-sorting-searching/NOTES.md)
<!-- /nav -->

# Phase 9 — Graphs: Notes

## 9.1 — Representations & the two traversals
A **graph** = vertices + edges; directed/undirected, weighted/unweighted, cyclic/acyclic. A tree is a connected acyclic graph, so DFS/BFS (Phase 7) carry over — with one addition: a **visited set**, because graphs have cycles and multiple paths.

**Representations:** *adjacency list* (`Map<node, List<neighbor>>` or `List<List<>>`) — space O(V+E), the usual choice for sparse graphs; *adjacency matrix* (`boolean[n][n]`) — O(V²) space, O(1) edge lookup, for dense graphs. Grids/matrices are *implicit* graphs (each cell a node, neighbors = up/down/left/right).

**BFS** (queue) — explores level by level; finds **shortest paths in an unweighted graph** (fewest edges). **DFS** (recursion or explicit stack) — goes deep; natural for connectivity, cycles, topological order, path existence. Both **O(V + E)**. Always mark visited *when enqueuing/entering* to avoid reprocessing.

## 9.2 — Core algorithms
- **Connected components / flood fill** — number of islands, friend circles: DFS/BFS from each unvisited node, or Union-Find. Grid flood-fill marks cells as it sinks them.
- **Cycle detection** — *undirected*: DFS, a visited neighbor that isn't your parent = a back edge = cycle. *Directed*: DFS with three colors (white/gray/black) — an edge to a gray (in-progress) node = cycle; or topological sort failing to order all nodes.
- **Topological sort** (DAGs only) — a linear order where every edge points forward. **Kahn's algorithm** (BFS): repeatedly emit nodes with in-degree 0, decrement neighbors; if you can't emit all n, there's a cycle. Uses: build systems, course schedules, task dependencies. O(V + E).

## 9.3 — Shortest paths & Union-Find
- **Unweighted shortest path** → BFS (each edge = 1).
- **Dijkstra** → weighted, **non-negative** edges: greedy + min-heap, always expand the closest unfinalized node, **relax** its edges. O((V+E) log V). (Negative edges → Bellman-Ford, O(VE); all pairs → Floyd-Warshall, O(V³); with a heuristic → A*.)
- **Union-Find (Disjoint Set Union)** — near-**O(α(n)) ≈ O(1)** `union`/`find` with **path compression** + **union by rank**. Answers "are these connected?" and merges groups incrementally. Uses: connected components on a stream of edges, cycle detection while adding edges, and **Kruskal's MST**. (Prim's MST uses a heap, like Dijkstra.)

## Choosing the tool
- Fewest edges / unweighted shortest → **BFS**.
- Path exists / explore / order dependencies → **DFS / topological sort**.
- Weighted shortest (non-negative) → **Dijkstra** (heap).
- Dynamic connectivity / grouping / MST → **Union-Find** (or Prim/Kruskal for MST).
- Grid problems → treat cells as nodes; DFS/BFS with 4- (or 8-) directional neighbors.

Graphs unify much of the track: the visited-set is hashing (Phase 5), BFS is a queue (Phase 4), DFS is recursion/stack (Phase 6), Dijkstra/Prim are heaps (Phase 8). Recognizing a problem *as* a graph is often the whole battle.
