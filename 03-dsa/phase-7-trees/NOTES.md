<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · recursion backtracking](../phase-6-recursion-backtracking/NOTES.md) | [Phase 8 · heaps ➡](../phase-8-heaps/NOTES.md)
<!-- /nav -->

# Phase 7 — Trees & BSTs: Notes

## 7.1 — Binary trees & traversals
A **binary tree**: each node has ≤ 2 children. Most tree problems are **recursion** — "solve for my children, combine their answers" — because a tree *is* a recursive structure. Terms: **height/depth** (edges to the deepest leaf), **leaf** (no children), **balanced** (subtree heights differ by ≤1), **complete/perfect/full** (shape constraints).

**DFS traversals** differ only in *when* the node is visited relative to its children:
- **Preorder** (node, left, right) — copy/serialize a tree, prefix expressions.
- **Inorder** (left, node, right) — on a **BST, yields sorted order** (the key property).
- **Postorder** (left, right, node) — delete/free a tree, evaluate expression trees, any "compute from children up" (heights, sizes).
All are O(n) time, O(h) stack space (O(n) worst case for a skewed tree). Each has an iterative form with an explicit stack (Phase 4 tie-in); Morris traversal does inorder in O(1) space.

**BFS / level order** uses a **queue**: process level by level, snapshotting `queue.size()` to bound each level. The basis for level-order, right-side view, zigzag, min-depth, and shortest-path-in-unweighted (Phase 9).

## 7.2 — Binary Search Trees
A **BST** invariant: for every node, *all* left descendants < node < *all* right descendants (not just immediate children — the validation subtlety). This gives ordered operations:
- **search / insert / delete:** O(h) — go left or right by comparison, touching one path. **O(log n) if balanced, O(n) if degenerate** (inserting sorted data makes a linked list — why self-balancing trees exist).
- **validate:** track a tightening `(low, high)` range as you descend; checking only node-vs-child is a classic wrong answer.
- **kth smallest:** the kth inorder element (inorder = sorted).
- **LCA in a BST:** descend while both targets are on the same side; the first split point (or a match) is the lowest common ancestor — O(h).

**Self-balancing trees** (AVL, Red-Black — Java's `TreeMap`/`TreeSet`, Phase 3.2) keep height O(log n) under insertion/deletion, guaranteeing O(log n) ops. You use them via the JDK; knowing *why* they exist (avoid the degenerate O(n) BST) is the interview point.

## 7.3 — Common problem shapes
- **"Combine children" metrics:** maxDepth (`1 + max(l, r)`), count nodes, sum, min/max.
- **"Track a global while returning a local":** diameter (return height, update the global longest path), max path sum, balanced check (return height, short-circuit to −1 on imbalance). This return-one-thing-track-another pattern is worth internalizing.
- **Two-tree recursion:** isSymmetric/isSameTree/isSubtree compare structure and values in lockstep.
- **LCA (general binary tree):** recurse; if a node equals a target or finds targets in both subtrees, it's the LCA.
- **Build/serialize:** construct from preorder+inorder; serialize/deserialize via preorder with null markers.

Trees are also the gateway to **tries** (prefix trees for strings), **segment trees / Fenwick trees** (range queries), and **heaps** (Phase 8) — all specialized trees.
