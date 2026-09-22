<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · recursion backtracking](../phase-6-recursion-backtracking/NOTES.md) | [Phase 8 · heaps ➡](../phase-8-heaps/NOTES.md)
<!-- /nav -->

# Phase 7 — Trees & BSTs: Notes

All code below is from `TreePatterns.java` in this directory (run with `java -ea TreePatterns.java`) unless a section is marked "not implemented in this file" — those round out the picture with classic follow-up problems and related structures.

The example tree used throughout the traversal sections:

```
      1
     / \
    2   3
   / \
  4   5
```

## 1. Binary Trees — Structure and Vocabulary

A **binary tree** is a structure where each node has at most two children, conventionally called `left` and `right`. Most tree problems are solved by **recursion** — "solve the problem for my children, then combine their answers" — because a tree *is* a recursive structure: every subtree is itself a smaller binary tree.

- **Node**: holds a value and references to (up to) two children.
  ```java
  static class TreeNode {
      int val;
      TreeNode left, right;
      TreeNode(int val) { this.val = val; }
      TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
  }
  ```
- **Height / depth**: the number of *edges* on the longest path from a node down to the deepest leaf in its subtree. A single-node tree has height 0 (no edges to traverse).
- **Leaf**: a node with no children (`left == null && right == null`).
- **Balanced**: for every node, the heights of its left and right subtrees differ by at most 1. Balance is what keeps recursive tree operations at O(log n) instead of degrading to O(n) — see the BST section below.
- **Complete / perfect / full**: shape constraints that show up in heap discussions (Phase 8) — complete means every level is fully filled except possibly the last, which fills left to right; perfect means every level is completely filled; full means every node has either 0 or 2 children (never exactly 1).

**Why it's useful**: nearly every tree algorithm is "define what a node returns to its parent, then figure out the recursive relationship between a node's answer and its children's answers." Internalizing that framing turns tree problems from "memorize the solution" into "derive the solution."

**Summary — Key Takeaways:**
- A tree is a recursive structure; think "solve for children, combine at the parent."
- Height counts edges, not nodes; a single node has height 0.
- Balance (subtree heights differ by ≤ 1) is the property that keeps operations O(log n) — losing it is what makes a BST degrade to O(n).

---

## 2. DFS Traversals — Preorder, Inorder, Postorder

The three depth-first traversals visit the same three things (the node itself, its left subtree, its right subtree) — they differ **only in the order** those three things happen relative to each other.

- **Preorder** (node, left, right): visit the node *before* either child. Used for copying/serializing a tree (you can reconstruct the tree from a preorder sequence if you also know where subtrees end, or combine it with inorder — see section 8) and for building prefix expressions.
- **Inorder** (left, node, right): visit the node *between* its children. On a **binary search tree**, this yields values in **sorted order** — this is the single most important fact about inorder traversal and the reason BSTs support `kthSmallest`, range queries, and "next greater element" so cleanly.
- **Postorder** (left, right, node): visit the node *after* both children. Used for deleting/freeing a tree bottom-up (you must free children before their parent), evaluating expression trees, and any "compute a value from my children, then use it at my own level" pattern (heights, sizes, diameter).
- **Complexity**: all three are O(n) time (visit every node once) and O(h) **stack** space, where `h` is the tree's height — O(log n) for a balanced tree, but O(n) worst case for a completely skewed (linked-list-shaped) tree.
- **Iterative forms**: each traversal has an iterative version using an explicit `Stack` (Phase 4 tie-in) instead of the call stack — interviewers sometimes ask for this specifically to test comfort with manual stack management. Inorder traversal additionally has a clever O(1)-*extra*-space version called **Morris traversal**, which temporarily rewires `null` right-child pointers into "threads" back to the successor node, then undoes the rewiring as it walks — advanced, but worth knowing the name and the space-complexity claim.

```java
static List<Integer> inorderH(TreeNode n, List<Integer> out) {
    if (n == null) return out;
    inorderH(n.left, out); out.add(n.val); inorderH(n.right, out);
    return out;
}
// inorder(tree)   -> [4, 2, 5, 1, 3]
// preorder(tree)  -> [1, 2, 4, 5, 3]
// postorder(tree) -> [4, 5, 2, 3, 1]
```

In this example: preorder visits `1` first (the node), then dives left into `2`'s subtree entirely (`2, 4, 5`) before ever visiting `3`. Inorder visits `4` first because it keeps descending left (`1 -> 2 -> 4`, and `4` has no left child, so it's emitted immediately), then backs up to emit `2`, then descends into `2`'s right subtree (`5`), then finally backs all the way up to emit `1`, then descends right into `3`. Postorder emits leaves before their parents (`4` and `5` before `2`; `2` and `3` before `1`), which is exactly why it's the traversal used for "compute from children, then use at parent" problems.

| Traversal | Order | Classic use case |
|---|---|---|
| Preorder | node, left, right | Serialize/copy a tree; prefix expression |
| Inorder | left, node, right | **BST → sorted order**; kth smallest; validate BST |
| Postorder | left, right, node | Delete/free a tree; expression evaluation; compute-from-children (height, diameter, balance) |

**Why it's useful**: recognizing which traversal a problem needs is often the whole battle. "Give me sorted BST output" screams inorder; "I need to know a subtree's height before I can process the current node" screams postorder; "I'm reconstructing or copying the tree top-down" screams preorder.

**Summary — Key Takeaways:**
- All three DFS traversals are O(n) time, O(h) space — the only difference is *when* the node itself is visited relative to its children.
- Inorder on a BST = sorted order. Memorize this; it unlocks kth-smallest, validation, and range problems.
- Postorder is the "compute from children first" traversal — reach for it whenever a node's answer depends on its children's answers.
- Each has an iterative (explicit-stack) form; inorder also has an O(1)-space Morris traversal.

---

## 3. BFS / Level-Order Traversal

**Level-order traversal** (breadth-first search on a tree) visits nodes level by level, left to right within each level, using a **queue** instead of the recursion/stack that DFS uses.

- **Key technique**: before processing a level, snapshot `queue.size()` — that count is exactly how many nodes belong to the *current* level (every node already in the queue before you start adding the next level's children). Loop that many times, and every node you poll during that loop is guaranteed to be from the current level, even though you're also pushing next-level nodes into the same queue as you go.
- **Time Complexity**: O(n) — every node enqueued and dequeued exactly once.
- **Space Complexity**: O(w) where `w` is the tree's maximum width (the widest level) — O(n) worst case for a tree whose bottom level holds roughly half the nodes.

```java
static List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> res = new ArrayList<>();
    if (root == null) return res;
    Queue<TreeNode> q = new ArrayDeque<>();
    q.offer(root);
    while (!q.isEmpty()) {
        int size = q.size();                       // fix the current level's width
        List<Integer> level = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            TreeNode n = q.poll();
            level.add(n.val);
            if (n.left != null) q.offer(n.left);
            if (n.right != null) q.offer(n.right);
        }
        res.add(level);
    }
    return res;
}
// levelOrder(tree) -> [[1], [2, 3], [4, 5]]
```

In this example: the queue starts with just `1`. `size = 1`, so the inner loop runs once, polling `1`, recording it, and enqueueing its children `2` and `3`. The outer loop repeats: now `size = 2` (both `2` and `3` are queued), so the inner loop polls exactly those two, recording `[2, 3]` and enqueueing `4` and `5`. A third pass records `[4, 5]` with nothing left to enqueue, and the queue empties — loop ends.

**Why it's useful**: level-order is the foundation for right-side view (keep the last node processed per level), zigzag traversal (reverse alternate levels), minimum depth (return the level index of the first leaf found — BFS finds the *shallowest* answer first, unlike DFS which might explore a deep path before a shallow one), and connect-level-siblings (link `node.next` pointers using the same-level grouping). It's also the direct blueprint for BFS shortest-path in unweighted graphs (Phase 9) — a tree is just a graph with no cycles, so the level-by-level idea transfers directly.

**Summary — Key Takeaways:**
- Snapshot `queue.size()` before each level's inner loop — that's what separates "level order" from a plain queue-based traversal that loses level boundaries.
- O(n) time, O(w) space where `w` is the widest level.
- BFS finds the shallowest matching node first — this matters for "minimum depth" and unweighted shortest-path problems.

---

## 4. Metrics: Combine-Children Recursion

The simplest tree-recursion shape: compute something for each child, combine the two results at the current node, return that combined value up to the parent.

- **Max depth / height**: `1 + max(depth(left), depth(right))`, with the base case `depth(null) == 0`. O(n) time (visits every node once), O(h) space.
- **Count nodes**: `1 + countNodes(left) + countNodes(right)`, base case `0` for null. O(n).

```java
static int maxDepth(TreeNode n) {
    return n == null ? 0 : 1 + Math.max(maxDepth(n.left), maxDepth(n.right));
}
static int countNodes(TreeNode n) {
    return n == null ? 0 : 1 + countNodes(n.left) + countNodes(n.right);
}
// maxDepth(tree)   -> 3   (path 1 -> 2 -> 4, three nodes, two edges... but height counts EDGES
//                          from root to deepest leaf via node-count recursion: 1 + max(...) = 3
//                          because this implementation counts NODES on the path, not edges)
// countNodes(tree) -> 5
```

In this example: `maxDepth` as written here returns the number of nodes on the longest root-to-leaf path (3, for `1 -> 2 -> 4` or `1 -> 2 -> 5`), which is one more than the *edge-count* height described in section 1 — both conventions ("nodes on path" vs "edges on path") appear in interview problems, so always clarify which one is being asked for. `countNodes` simply sums `1` (for the current node) plus however many nodes each subtree recursively reports.

**Why it's useful**: this "return a value that combines the children's returned values" shape is the template for a huge fraction of tree problems — sum of all values, minimum value, checking if a value exists, counting nodes matching a predicate — they're all this same recursion with a different combining rule.

**Summary — Key Takeaways:**
- `n == null` is almost always the base case, usually returning `0` (for counts/depths) or an identity value appropriate to the combine operation.
- The recursive step is "compute for both children, combine, add 1 for the current node" — recognize this shape immediately.
- O(n) time is standard for any traversal that visits every node exactly once.

---

## 5. "Track a Global While Returning a Local" Pattern

Some problems need each recursive call to *return* one value to its parent (so the parent's own recursion can use it) while simultaneously *updating* a separate answer that isn't naturally "returnable" from a single call. Diameter, max path sum, and the balanced-tree check all follow this shape.

### Diameter of a binary tree

- **Definition**: the length (in edges) of the longest path between *any* two nodes in the tree — the path does not have to pass through the root.
- **Key Insight**: at any given node, the longest path *that passes through that node* equals `leftHeight + rightHeight` (go down the left subtree as far as possible, then down the right). The overall diameter is the maximum of this quantity over *all* nodes, not just the root — which is why you can't just compute `height(root.left) + height(root.right)` once and call it done.
- **The pattern**: the recursive function `height()` returns the height of the subtree (needed by the parent's own height computation), while a separate variable (`diameterBest`) is updated as a side effect at every node with `left height + right height` for *that* node.

```java
static int diameterBest;
static int diameter(TreeNode root) {
    diameterBest = 0;
    height(root);
    return diameterBest;
}
static int height(TreeNode n) {
    if (n == null) return 0;
    int l = height(n.left), r = height(n.right);
    diameterBest = Math.max(diameterBest, l + r);   // path through n
    return 1 + Math.max(l, r);
}
// diameter(tree) -> 3   (the longest path is 4 -> 2 -> 1 -> 3, three edges)
```

In this example: at node `2`, `l = height(4) = 1` and `r = height(5) = 1`, so the path-through-`2` candidate is `1 + 1 = 2`. At node `1`, `l = height(2) = 2` and `r = height(3) = 1`, so the path-through-`1` candidate is `2 + 1 = 3` — this is the winner, and it corresponds to the path `4 -> 2 -> 1 -> 3`. `diameterBest` is updated at *every* node visited (a global side effect), while each call's *return value* (the height) is what the parent call needs to compute its own `l + r`.

### Balanced tree check (efficient version)

- **Naive approach**: for every node, call `height()` on its left and right subtrees separately and compare — this recomputes heights repeatedly (once per ancestor), giving O(n²) worst case.
- **Efficient approach**: have the recursive function return the height *and* use a sentinel value (`-1`) to signal "already found an imbalance somewhere below" — the moment any subtree reports `-1`, every ancestor immediately propagates `-1` upward without doing any more work, short-circuiting the rest of the traversal.

```java
static boolean isBalanced(TreeNode n) { return checkBalance(n) >= 0; }
static int checkBalance(TreeNode n) {
    if (n == null) return 0;
    int l = checkBalance(n.left); if (l < 0) return -1;
    int r = checkBalance(n.right); if (r < 0) return -1;
    if (Math.abs(l - r) > 1) return -1;             // unbalanced -> propagate failure
    return 1 + Math.max(l, r);
}
// isBalanced(tree) -> true  (every subtree's left/right heights differ by <= 1)
```

In this example: the five-node example tree is balanced (node `2` has left/right heights `1` and `1`; node `1` has left/right heights `2` and `1`; both differ by at most `1`), so `checkBalance` never returns `-1` and the final height is returned normally, making `isBalanced` return `true` via `checkBalance(n) >= 0`.

| Approach | Time | Why |
|---|---|---|
| Naive (separate height() calls per node) | O(n²) worst case | Each ancestor re-walks its subtrees to compute height |
| Return-height-or-(-1) short circuit | **O(n)** | Each node visited once; imbalance propagates up without extra work |

**Why it's useful**: this "return one thing, track another as a side effect" pattern appears constantly — max path sum (return the best *single-branch* extension to the parent, but track the best *full path*, which may bend through the current node using both branches), and any "find the best X anywhere in the tree" problem where "best" isn't cleanly composable from a simple return value alone.

**Summary — Key Takeaways:**
- When "the best answer overall" and "what a node hands back to its parent" are different things, use a global/instance variable for the former and a return value for the latter.
- Diameter: return height to the parent, update a global max of `leftHeight + rightHeight` at every node.
- Balanced check: return height, but short-circuit with `-1` the moment any subtree is already unbalanced — turns O(n²) into O(n).

---

## 6. Two-Tree / Structural Recursion

Some problems compare **two trees** (or two subtrees of the same tree) structurally, recursing on both simultaneously.

- **Symmetric tree**: check whether a tree is a mirror of itself. Recurse on `(left.left vs right.right)` and `(left.right vs right.left)` simultaneously — the "mirror" comparison, not the same-position comparison `isSameTree` would use.
- **Same tree** (not implemented in this file, but the same shape): compare two entire trees node by node — `a == null && b == null` is a match, `a == null || b == null || a.val != b.val` is a mismatch, otherwise recurse on `(a.left, b.left)` and `(a.right, b.right)`.
- **Subtree of another tree** (not implemented in this file): for every node in the larger tree, check whether `isSameTree` matches starting from that node — O(n·m) in the worst case (n nodes in the big tree, m nodes in the candidate subtree, since each of the n starting points can trigger an O(m) same-tree check).

```java
static boolean isSymmetric(TreeNode root) {
    return root == null || mirror(root.left, root.right);
}
static boolean mirror(TreeNode a, TreeNode b) {
    if (a == null && b == null) return true;
    if (a == null || b == null || a.val != b.val) return false;
    return mirror(a.left, b.right) && mirror(a.right, b.left);
}
// isSymmetric( 1(2(3,null), 2(null,3)) ) -> true
```

In this example: the tree's left subtree is `2(3, null)` and right subtree is `2(null, 3)`. `mirror` compares `left.left` (`3`) against `right.right` (`3`) — match — and `left.right` (`null`) against `right.left` (`null`) — also a match — so the whole tree is symmetric. Had the right subtree instead been `2(3, null)` (an exact copy rather than a mirror), `mirror` would compare `left.left` (`3`) against `right.right` (`null`) and correctly report a mismatch.

**Why it's useful**: two-tree recursion is the standard template whenever a problem says "compare," "is a mirror of," or "contains as a subtree" — always identify exactly which pair of subtrees each recursive call should compare (same-position for `isSameTree`, cross-position for `isSymmetric`) before writing the recursion.

**Summary — Key Takeaways:**
- `isSymmetric` compares cross-position (`left.left` vs `right.right`); `isSameTree` compares same-position (`a.left` vs `b.left`).
- Base cases: both null → match; exactly one null, or unequal values → mismatch.
- "Subtree of another tree" = run `isSameTree` from every node of the larger tree — O(n·m).

---

## 7. Binary Search Trees (BSTs)

A **binary search tree** adds an ordering invariant on top of the plain binary tree structure: for *every* node, all values in its left subtree are less than the node's value, and all values in its right subtree are greater. This is a **global** property, not just "less than my immediate left child" — a common validation bug is checking only the immediate parent/child relationship instead of the full ancestor chain.

- **Search**: compare the target against the current node; go left if smaller, right if larger, repeat. O(h) — touches only one root-to-leaf path.
- **Insert**: same idea — descend by comparison until you fall off the tree (`null`), then attach the new node there. O(h).
- **Complexity**: both are **O(log n) if the tree is balanced**, but **O(n) if the tree is degenerate** (e.g., inserting already-sorted data produces a tree that's really just a linked list — every node has only a right child). This degeneration is *the* reason self-balancing BSTs (AVL, Red-Black trees) exist.

```java
static TreeNode insertBST(TreeNode n, int v) {
    if (n == null) return new TreeNode(v);
    if (v < n.val) n.left = insertBST(n.left, v);
    else n.right = insertBST(n.right, v);
    return n;
}
static boolean searchBST(TreeNode n, int v) {
    if (n == null) return false;
    if (v == n.val) return true;
    return v < n.val ? searchBST(n.left, v) : searchBST(n.right, v);   // go one direction only
}
// inserting 5, 3, 7, 2, 4, 6, 8 in that order builds:
//         5
//        / \
//       3   7
//      / \ / \
//     2  4 6  8
// searchBST(bst, 4) -> true    searchBST(bst, 9) -> false
```

In this example: inserting `5` first makes it the root; `3 < 5` goes left, `7 > 5` goes right; `2 < 5` then `2 < 3` goes left of `3`; `4 < 5` then `4 > 3` goes right of `3`; similarly `6` and `8` land under `7`. The resulting tree happens to be balanced (height 2 for 7 nodes) purely because the insertion order alternates around the median — a different insertion order (e.g., inserting `2,3,4,5,6,7,8` in sorted order) would instead build a completely skewed, O(n)-height tree.

### Validating a BST

- **The subtlety**: checking `node.val > node.left.val && node.val < node.right.val` for every node is **not sufficient** — it only verifies the immediate parent/child relationship, but the BST invariant is global (a node deep in the left subtree could still be larger than an ancestor several levels up, which the immediate-child check would miss entirely).
- **The correct approach**: track a tightening `(low, high)` range as you recurse. The root can be anything; descending left tightens the `high` bound to the parent's value; descending right tightens the `low` bound. A node is valid only if it falls strictly inside its current range.

```java
static boolean isValidBST(TreeNode n) { return valid(n, Long.MIN_VALUE, Long.MAX_VALUE); }
static boolean valid(TreeNode n, long low, long high) {
    if (n == null) return true;
    if (n.val <= low || n.val >= high) return false;
    return valid(n.left, low, n.val) && valid(n.right, n.val, high);
}
// isValidBST(bst built above) -> true
// a "bad" tree: 5 with left child 3, right child 4 (4 < 5 but placed on the right) -> false
```

In this example: for the "bad" tree `5(3, 4)`, the immediate-child check (`5 > 3` and `5 < 4`? no, `4 < 5`) would already catch this specific case — but the range-based check generalizes correctly to deeper violations that immediate-child checks miss, such as a right-subtree grandchild that's smaller than the root. Note the range uses `long`, not `int` — a common edge case is a node holding `Integer.MAX_VALUE` or `Integer.MIN_VALUE`, which would silently break an `int`-typed range boundary.

### kth smallest element

- **Key Insight**: since an inorder traversal of a BST visits nodes in sorted order (section 2), the kth smallest element is simply the kth element of the inorder traversal.
- **Complexity**: O(n) for the straightforward "traverse everything, then index" approach shown here (O(h + k) if you stop the traversal early the moment you've counted k nodes, which avoids visiting the rest of the tree). For repeated queries on the same tree, you can augment each node with its subtree size at construction time, turning each query into an O(h) descent (go left if the target rank is within the left subtree's size, right and adjust otherwise).

```java
static int kthSmallest(TreeNode root, int k) {
    List<Integer> sorted = inorder(root);
    return sorted.get(k - 1);
}
// kthSmallest(bst, 3) -> 4   (sorted order is 2,3,4,5,6,7,8 — the 3rd is 4)
```

### Lowest common ancestor (LCA) in a BST

- **Key Insight**: descend from the root; if both targets are less than the current node, the LCA must be in the left subtree (recurse left); if both are greater, recurse right; the moment they're *not* both on the same side — one is less-or-equal and the other is greater-or-equal — the current node is the split point, and it is the LCA (this also correctly handles the case where the current node *is* one of the two targets).
- **Complexity**: O(h) — a single descent, no backtracking needed, unlike the general-binary-tree LCA (section 8) which must explore both subtrees.

```java
static TreeNode lca(TreeNode n, int p, int q) {
    if (p < n.val && q < n.val) return lca(n.left, p, q);
    if (p > n.val && q > n.val) return lca(n.right, p, q);
    return n;                                         // split point (or equals one)
}
// lca(bst, 2, 4).val -> 3   (2 < 3, 4 > 3 -- they split at node 3)
```

In this example: starting at the root `5`, both `2` and `4` are less than `5`, so recurse left to node `3`. At node `3`, `2 < 3` but `4 > 3` — they no longer agree on direction — so `3` is the split point and is returned as the LCA.

**Why it's useful**: BSTs are the ordered-lookup workhorse — Java's `TreeMap`/`TreeSet` are backed by a self-balancing BST (a Red-Black tree) precisely so you get O(log n) search/insert/delete *and* sorted-order iteration for free. Understanding the plain-BST mechanics here is what makes `TreeMap`'s guarantees make sense rather than feeling like magic.

**Summary — Key Takeaways:**
- BST invariant is global (all left descendants < node < all right descendants), not just immediate-child — validate with a tightening `(low, high)` range, not a node-vs-child check.
- Search/insert/delete are O(h): O(log n) balanced, **O(n) degenerate** (e.g., inserting sorted data) — this is why self-balancing trees exist.
- Inorder traversal of a BST = sorted order; kth smallest is the kth inorder element.
- LCA in a BST is found in O(h) by descending while both targets stay on the same side; the split point is the answer.

---

## 8. Self-Balancing Trees (AVL, Red-Black) — Not Implemented in This File

A plain BST's O(log n) guarantee depends entirely on the tree staying roughly balanced — and nothing about `insertBST` above prevents it from becoming skewed. **Self-balancing binary search trees** solve this by performing local restructuring (**rotations**) after insert/delete operations to keep the height provably O(log n) regardless of insertion order.

- **AVL trees**: maintain the strict invariant that every node's left/right subtree heights differ by at most 1 (exactly the "balanced" check from section 5), enforced via rotations after every insert/delete. Very tightly balanced, so lookups are fast, but rebalancing is triggered often.
- **Red-Black trees**: a looser balance invariant (via node "colors" and a set of structural rules) that allows slightly less-tight balance in exchange for fewer rotations on average — a better fit for workloads with frequent insertions/deletions. **Java's `TreeMap` and `TreeSet` are backed by a Red-Black tree.**

| | AVL | Red-Black |
|---|---|---|
| Balance strictness | Tighter (height diff ≤ 1 everywhere) | Looser (bounded by color rules) |
| Lookup speed | Slightly faster (shorter trees) | Slightly slower |
| Insert/delete cost | More rotations on average | Fewer rotations on average |
| Real-world use | Less common in general-purpose libraries | **Java `TreeMap`/`TreeSet`, C++ `std::map`** |

**Why it's useful**: you virtually never implement AVL/Red-Black rotation logic by hand in an interview — the interview-relevant knowledge is *why* they exist (to prevent the O(n)-degenerate-BST failure mode) and *that* `TreeMap`/`TreeSet` already give you these guarantees for free. Knowing this connects Phase 7's tree theory directly to reaching for the right JDK collection in production code.

**Summary — Key Takeaways:**
- A plain BST degrades to O(n) on adversarial (e.g., sorted) insertion order — self-balancing trees exist to prevent this.
- AVL trees are strictly balanced (faster lookups); Red-Black trees are more loosely balanced (faster mutation) — Java's `TreeMap`/`TreeSet` use Red-Black trees.
- You need to *know why* these exist and *when to reach for `TreeMap`*, not implement rotations from scratch, for most interviews.

---

## 9. Lowest Common Ancestor — General Binary Tree — Not Implemented in This File

The BST-specific LCA (section 7) relies on the ordering invariant to know which direction to recurse. A **general binary tree** has no such ordering, so the algorithm must actually search both subtrees.

- **Approach**: recurse into both `left` and `right`. If the current node *is* one of the two targets, return it immediately (it might be an ancestor of the other target, or it might just be that target itself — both are valid LCA answers per the standard problem definition). If the recursive calls on both children return non-null (meaning one target was found in each subtree), the current node is the split point — the LCA. If only one side returns non-null, propagate that result upward (the LCA must be higher up, or it's entirely contained within that one subtree).

```java
static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) return root;   // p and q found in different subtrees
    return left != null ? left : right;                // both found in the same subtree, or only one found
}
```

**Time Complexity**: O(n) — in the worst case, every node is visited once (unlike the BST version's O(h), because there's no ordering to prune the search with).

**Why it's useful**: this is the general-purpose LCA algorithm — it works on *any* binary tree, not just BSTs, and is the version interviewers expect if they don't explicitly say "binary search tree." Compare it directly against the BST version to reinforce why ordering information (when available) lets you do dramatically less work (O(h) vs O(n)).

**Summary — Key Takeaways:**
- General-tree LCA has no ordering to exploit — it must search both subtrees, giving O(n) vs the BST version's O(h).
- A node found to *be* one of the targets is immediately a valid answer (it could be an ancestor of the other target).
- Both children returning non-null means the current node is the split point.

---

## 10. Build / Serialize / Deserialize — Not Implemented in This File

- **Build a tree from preorder + inorder traversals**: the first element of a preorder sequence is always the root. Find that value's position in the inorder sequence — everything to its left in the inorder sequence is the entire left subtree, everything to its right is the entire right subtree. Recurse on the corresponding slices of both sequences. Using a `HashMap<value, inorderIndex>` built once up front turns each lookup from O(n) (linear search) into O(1), bringing the total algorithm to O(n) instead of O(n²).
  ```java
  static TreeNode buildTree(int[] preorder, int[] inorder) {
      Map<Integer, Integer> inorderIndex = new HashMap<>();
      for (int i = 0; i < inorder.length; i++) inorderIndex.put(inorder[i], i);
      int[] preIdx = {0};
      return build(preorder, preIdx, inorderIndex, 0, inorder.length - 1);
  }
  static TreeNode build(int[] pre, int[] preIdx, Map<Integer, Integer> idx, int lo, int hi) {
      if (lo > hi) return null;
      int rootVal = pre[preIdx[0]++];
      TreeNode root = new TreeNode(rootVal);
      int mid = idx.get(rootVal);
      root.left = build(pre, preIdx, idx, lo, mid - 1);
      root.right = build(pre, preIdx, idx, mid + 1, hi);
      return root;
  }
  ```
- **Serialize / deserialize**: preorder traversal with explicit **null markers** (e.g., the string `"#"`) fully captures a tree's shape and values in a way that a bare preorder sequence alone cannot (without null markers, you can't tell where one subtree ends and a sibling begins, unless you also have the inorder sequence as in the build problem above). Deserializing just consumes tokens in the same preorder order, recursively rebuilding: read a token, if it's the null marker return `null`, otherwise create a node and recursively build its left and right children from the remaining tokens.

**Why it's useful**: reconstruction problems test whether you actually understand what information each traversal order encodes — preorder alone is ambiguous about tree shape unless paired with either inorder or explicit null markers, which is a subtlety worth stating explicitly if asked "can preorder alone reconstruct a tree?"

**Summary — Key Takeaways:**
- Preorder's first element is always the root; use it to split the inorder sequence into left/right subtree ranges, and recurse.
- A `value -> inorderIndex` hash map turns the O(n²) naive approach into O(n).
- Serialization needs either two traversal orders together, or explicit null markers in a single traversal — a bare preorder sequence alone is ambiguous.

---

## 11. Related Specialized Trees

Trees are also the gateway to several specialized structures that show up in interviews once "plain binary tree" questions are exhausted:

- **Tries (prefix trees)**: a tree where each edge represents one character, and each root-to-node path represents a string prefix. Used for autocomplete, spell-checking, and IP-routing longest-prefix-match. Insert/search of a string of length `L` is O(L), independent of how many strings are stored — this is the key selling point over a hash set of strings for prefix-based queries.
- **Segment trees**: a balanced binary tree over an array where each node stores an aggregate (sum, min, max) of a range of the array. Supports range queries and point updates in O(log n), compared to O(n) for a naive re-scan on every query. Used heavily in competitive programming and in real systems needing fast range aggregation over frequently-updated data.
- **Fenwick trees (Binary Indexed Trees)**: a more compact alternative to segment trees specifically for prefix-sum-style queries and point updates, also O(log n) per operation, with a smaller constant factor and simpler implementation (though less flexible than a general segment tree).
- **Heaps** (Phase 8): a tree-shaped structure (usually array-backed, implicitly indexed) maintaining a min-or-max-at-the-root invariant — covered in full in the next phase.

**Why it's useful**: recognizing "this problem wants fast prefix-string lookups" (trie), "this problem wants fast range-sum/range-min queries with updates" (segment tree/Fenwick tree), or "this problem wants the running min/max/top-k" (heap) as soon as it's stated saves enormous time versus trying to force a plain-BST or hash-map solution onto a problem that has a purpose-built structure.

**Summary — Key Takeaways:**
- Tries: O(L) prefix operations independent of dictionary size — the go-to for autocomplete/prefix-matching.
- Segment trees / Fenwick trees: O(log n) range query + point update, vs O(n) naive rescans — the go-to for "range sum/min/max with updates."
- All of these are still fundamentally trees — the same recursive "solve for children, combine" instinct from section 1 applies to understanding how they work.
