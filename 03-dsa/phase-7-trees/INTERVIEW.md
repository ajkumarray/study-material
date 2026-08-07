<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · recursion backtracking](../phase-6-recursion-backtracking/NOTES.md) | [Phase 8 · heaps ➡](../phase-8-heaps/NOTES.md)
<!-- /nav -->

# Phase 7 — Trees & BSTs: Interview Q&A + Problems

⭐ = asked constantly.

**Q: The three DFS traversals — and when to use each?** ⭐⭐
Preorder (node, left, right) — serialize/copy. Inorder (left, node, right) — **sorted order on a BST**. Postorder (left, right, node) — delete/free, or compute values from children upward. Know the recursive form cold and be able to write the iterative (stack) version.

**Q: Level-order traversal / BFS on a tree.** ⭐
Queue-based; snapshot `queue.size()` at each level to group nodes per level. Foundation for right-side view, zigzag, min depth, and connect-level-siblings.

**Q: Validate a BST.** ⭐⭐
Recurse with a tightening `(min, max)` range; each node must lie strictly inside it. Checking only node-vs-immediate-children is wrong (a deep descendant can violate the global invariant). Alternatively, inorder traversal must be strictly increasing.

**Q: Maximum depth / height of a binary tree.** ⭐
`1 + max(depth(left), depth(right))`, base case 0 for null. O(n).

**Q: Diameter of a binary tree.** ⭐
The longest path between any two nodes. At each node, the path through it = left height + right height; compute heights recursively and track the global max. O(n) — the "return height, update a global" pattern.

**Q: Lowest common ancestor.** ⭐⭐
BST: descend while both targets are on the same side; the first split (or a match) is the LCA — O(h). General binary tree: recurse; a node that is a target, or finds targets in both subtrees, is the LCA.

**Q: Is a tree balanced?** ⭐
Recurse returning height, but return −1 to signal imbalance and short-circuit up (avoids recomputing heights). O(n) vs the naive O(n²).

**Q: kth smallest element in a BST.** ⭐
Inorder traversal gives sorted order; stop at the kth. O(h + k). For repeated queries, augment nodes with subtree sizes for O(h).

**Q: Why can a BST degrade to O(n)? What fixes it?** ⭐
Inserting sorted/near-sorted data creates a skewed, linked-list-like tree (height n). Self-balancing trees (AVL, Red-Black — Java's TreeMap/TreeSet) rebalance on insert/delete to keep height O(log n).

**Q: Serialize and deserialize a binary tree.**
Preorder with explicit null markers (or level-order). Deserialize by consuming the same order. Tests careful recursion and encoding.

**Q: Symmetric tree / same tree / subtree of another tree.**
Two-pointer recursion comparing structure and values in lockstep (mirror for symmetric: `left.left` vs `right.right`). O(n).

**Q: Build a tree from preorder + inorder.**
Preorder's first element is the root; find it in inorder to split left/right subtrees; recurse. Use a hashmap of value→inorder-index for O(n).
