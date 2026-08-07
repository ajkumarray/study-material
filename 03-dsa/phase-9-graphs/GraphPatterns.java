import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/*
 * Phase 9 — Graphs
 * Run:  java -ea GraphPatterns.java
 *
 * A GRAPH = nodes (vertices) + edges. Directed or undirected, weighted or not,
 * cyclic or acyclic. Trees are just acyclic connected graphs — so DFS/BFS from
 * Phase 7 generalize here, with one addition: a VISITED set, because graphs
 * have cycles and multiple paths (a tree doesn't).
 *
 * Representations:
 *   adjacency list  Map<node, List<neighbors>>   — sparse graphs (usual choice)
 *   adjacency matrix boolean[n][n]               — dense graphs, O(1) edge check
 */
public class GraphPatterns {

    public static void main(String[] args) {
        // graph:  0-1, 0-2, 1-3, 2-3, 3-4   (undirected)
        Map<Integer, List<Integer>> g = new HashMap<>();
        addEdge(g, 0, 1); addEdge(g, 0, 2); addEdge(g, 1, 3); addEdge(g, 2, 3); addEdge(g, 3, 4);

        assert bfs(g, 0).equals(List.of(0, 1, 2, 3, 4));
        assert dfs(g, 0).size() == 5;
        assert shortestUnweighted(g, 0, 4) == 3;            // 0-1-3-4 or 0-2-3-4

        // connected components on a grid ("number of islands")
        char[][] grid = {
                {'1','1','0','0'},
                {'1','0','0','1'},
                {'0','0','1','1'},
        };
        assert numIslands(grid) == 2;

        // cycle detection (undirected)
        Map<Integer, List<Integer>> cyclic = new HashMap<>();
        addEdge(cyclic, 0, 1); addEdge(cyclic, 1, 2); addEdge(cyclic, 2, 0);   // triangle
        assert hasCycleUndirected(cyclic, 3);
        assert hasCycleUndirected(g, 5);                    // g HAS a cycle: 0-1-3-2-0
        Map<Integer, List<Integer>> tree = new HashMap<>(); // a real acyclic graph
        addEdge(tree, 0, 1); addEdge(tree, 0, 2); addEdge(tree, 1, 3);
        assert !hasCycleUndirected(tree, 4);

        // topological sort (DAG: course prerequisites)
        int[][] prereqs = {{1, 0}, {2, 0}, {3, 1}, {3, 2}};   // 3 depends on 1&2, both on 0
        List<Integer> order = topoSort(4, prereqs);
        assert order.size() == 4 && order.get(0) == 0;      // 0 must come first

        // Dijkstra shortest path (weighted)
        int[][] edges = {{0, 1, 4}, {0, 2, 1}, {2, 1, 2}, {1, 3, 1}, {2, 3, 5}};
        int[] dist = dijkstra(4, edges, 0);
        assert dist[1] == 3 && dist[3] == 4;                // 0->2->1 = 3, 0->2->1->3 = 4

        // Union-Find (disjoint set)
        UnionFind uf = new UnionFind(5);
        uf.union(0, 1); uf.union(1, 2); uf.union(3, 4);
        assert uf.connected(0, 2) && !uf.connected(0, 3);
        assert uf.components() == 2;

        System.out.println("All graph tests passed.");
        demo(g);
    }

    static void addEdge(Map<Integer, List<Integer>> g, int u, int v) {
        g.computeIfAbsent(u, k -> new ArrayList<>()).add(v);
        g.computeIfAbsent(v, k -> new ArrayList<>()).add(u);   // undirected: both ways
    }

    // ============================================================
    // BFS — explore level by level with a QUEUE. Finds SHORTEST paths in an
    // UNWEIGHTED graph (fewest edges). O(V + E).
    // ============================================================
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

    // ============================================================
    // DFS — go deep with recursion (or an explicit stack). O(V + E).
    // ============================================================
    static List<Integer> dfs(Map<Integer, List<Integer>> g, int start) {
        List<Integer> order = new ArrayList<>();
        dfsH(g, start, new HashSet<>(), order);
        return order;
    }
    static void dfsH(Map<Integer, List<Integer>> g, int node, Set<Integer> visited, List<Integer> order) {
        if (!visited.add(node)) return;             // already seen -> stop (handles cycles)
        order.add(node);
        for (int nb : g.getOrDefault(node, List.of())) dfsH(g, nb, visited, order);
    }

    // Shortest path length in an unweighted graph = BFS with a distance count.
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

    // NUMBER OF ISLANDS — connected components on a grid. Flood-fill each
    // unvisited '1' via DFS, marking cells. The grid IS an implicit graph.
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

    // Cycle detection in an undirected graph: DFS, and if we reach a visited
    // node that ISN'T the parent we came from, there's a cycle.
    static boolean hasCycleUndirected(Map<Integer, List<Integer>> g, int n) {
        Set<Integer> visited = new HashSet<>();
        for (int start = 0; start < n; start++)
            if (!visited.contains(start) && cycleDfs(g, start, -1, visited)) return true;
        return false;
    }
    static boolean cycleDfs(Map<Integer, List<Integer>> g, int node, int parent, Set<Integer> visited) {
        visited.add(node);
        for (int nb : g.getOrDefault(node, List.of())) {
            if (!visited.contains(nb)) { if (cycleDfs(g, nb, node, visited)) return true; }
            else if (nb != parent) return true;      // visited & not parent -> back edge -> cycle
        }
        return false;
    }

    // ============================================================
    // TOPOLOGICAL SORT (Kahn's, BFS on in-degrees) — linear ordering of a DAG
    // so every edge u->v has u before v. Detects cycles (order incomplete).
    // Use: build order, course schedule, task dependencies.
    // ============================================================
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

    // ============================================================
    // DIJKSTRA — shortest paths from a source in a WEIGHTED graph with
    // non-negative weights. Greedy + min-heap: always expand the closest
    // unfinalized node. O((V + E) log V).
    // ============================================================
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

    // ============================================================
    // UNION-FIND (Disjoint Set Union) — near-O(1) "are these connected?" and
    // "merge these groups", via path compression + union by rank. Use:
    // connected components, cycle detection, Kruskal's MST.
    // ============================================================
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

    static void demo(Map<Integer, List<Integer>> g) {
        System.out.println("\n=== worked examples ===");
        System.out.println("BFS from 0            -> " + bfs(g, 0));
        System.out.println("DFS from 0            -> " + dfs(g, 0));
        System.out.println("shortest 0->4 (edges) -> " + shortestUnweighted(g, 0, 4));
        int[][] prereqs = {{1, 0}, {2, 0}, {3, 1}, {3, 2}};
        System.out.println("topoSort(courses)     -> " + topoSort(4, prereqs));
    }
}
