<!-- nav -->
**[🏠 Home](../../README.md)** · **[📚 Track](../README.md)** · [📖 Notes](./NOTES.md) · [💬 Interview](./INTERVIEW.md)  
⬅ [Phase 6 · recursion backtracking](../phase-6-recursion-backtracking/NOTES.md) | [Phase 8 · heaps ➡](../phase-8-heaps/NOTES.md)
<!-- /nav -->

# Phase 7 — Trees & BSTs: Interview Q&A + Problems

⭐ = asked constantly.

**Q: The three DFS traversals — and when to use each?** ⭐⭐

All three visit the same three things — the node, its left subtree, its right subtree — differing only in *when* the node itself is visited relative to its children. Preorder (node, left, right) visits the node first, which is what you want for serializing/copying a tree, since you always emit the root of a subtree before its contents. Inorder (left, node, right) visits the node between its children, and on a **binary search tree specifically, this produces the values in sorted order** — the single most important fact in this whole phase, since it's what makes kth-smallest, validation, and range queries on a BST clean. Postorder (left, right, node) visits the node last, which is required whenever a node's own computation depends on results from its children first — deleting a tree bottom-up, evaluating an expression tree, or computing height/size/diameter.

```java
static void inorderH(TreeNode n, List<Integer> out) {
    if (n == null) return;
    inorderH(n.left, out); out.add(n.val); inorderH(n.right, out);
}
```

You should know the recursive form of all three cold, and be able to write at least one of them iteratively with an explicit `Stack` — the standard trick for preorder is push right child then left child (so left pops first); inorder needs a "go left as far as possible, then pop and go right" loop; postorder is the trickiest and is often done by reversing a modified preorder (node, right, left) traversal.

*Follow-up: What would preorder, inorder, and postorder output for the tree `1(2(4,5),3)`?* Preorder: `1,2,4,5,3` (node, then entire left subtree, then entire right subtree). Inorder: `4,2,5,1,3` (leftmost leaf first, working back up). Postorder: `4,5,2,3,1` (both leaves under `2` before `2` itself, both children of `1` before `1` itself).

*Follow-up: Given only a preorder sequence, can you reconstruct the tree?* Not uniquely, unless the traversal also includes explicit null markers, or you're additionally given the inorder sequence — a bare preorder list is ambiguous about where one subtree ends and a sibling begins.

---

**Q: Level-order traversal / BFS on a tree.** ⭐

Use a queue, starting with the root. The key technique is to snapshot `queue.size()` immediately before processing a level — that count is exactly how many nodes belong to the current level (everything already queued), even though you're simultaneously enqueueing the *next* level's children into the same queue during that loop. Looping exactly that many times and collecting each polled value's data keeps the levels cleanly separated. O(n) time (every node enqueued/dequeued once), O(w) space where `w` is the tree's maximum width.

```java
static List<List<Integer>> levelOrder(TreeNode root) {
    List<List<Integer>> res = new ArrayList<>();
    if (root == null) return res;
    Queue<TreeNode> q = new ArrayDeque<>();
    q.offer(root);
    while (!q.isEmpty()) {
        int size = q.size();
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
```

This is the foundation for right-side view (keep only the last node processed at each level), zigzag traversal (reverse the order of alternating levels before adding them to the result), minimum depth (return as soon as you find the first leaf — BFS guarantees it's the *shallowest* one, since you process level by level), and connecting same-level siblings via `next` pointers (link consecutive nodes within each level's inner loop).

*Follow-up: Why is BFS, not DFS, the right choice for "minimum depth of a binary tree"?* DFS might dive down a long path before ever reaching a shallow leaf on a different branch, so you'd have to explore the *entire* tree and take a min at the end — still correct, but wasteful. BFS processes level by level, so the very first leaf it encounters is guaranteed to be at the minimum depth, letting you return immediately without visiting the rest of the tree.

*Follow-up: How would you implement zigzag level-order traversal?* Same level-order loop, but track whether the current level index is even or odd, and reverse the `level` list (or build it back-to-front) on odd levels before adding it to the result.

---

**Q: Validate a BST.** ⭐⭐

Recurse while tracking a tightening `(low, high)` range: the root can be any value, but as you descend left the `high` bound tightens to the parent's value, and as you descend right the `low` bound tightens to the parent's value. A node is valid only if it falls strictly inside its current `(low, high)` range. Checking only a node against its *immediate* children is **not sufficient** — the BST invariant is global (every node in a left subtree must be less than *every* ancestor above it, not just its direct parent), and a node several levels down could violate an ancestor's constraint while still satisfying its immediate parent's.

```java
static boolean valid(TreeNode n, long low, long high) {
    if (n == null) return true;
    if (n.val <= low || n.val >= high) return false;
    return valid(n.left, low, n.val) && valid(n.right, n.val, high);
}
```

An alternative approach: run an inorder traversal and check that the resulting sequence is strictly increasing — since a valid BST's inorder traversal is sorted by definition, any violation of that invariant reveals an invalid BST just as reliably as the range-tracking approach, and can be done in O(1) extra space by comparing each visited value only to the previously visited value rather than storing the whole sequence.

*Follow-up: Why does the code use `long` for the bounds instead of `int`?* To handle the edge case where a node's value is exactly `Integer.MAX_VALUE` or `Integer.MIN_VALUE` — if the bounds were `int`, initializing them to `Integer.MIN_VALUE`/`Integer.MAX_VALUE` would make a root holding one of those exact values incorrectly fail the `n.val <= low || n.val >= high` check due to no room being left above/below it.

*Follow-up: Give a tree that passes an immediate-child-only check but fails real BST validation.* A root `10` with left child `5`, whose right child is `15` — `5 < 15` satisfies the immediate parent-child check at that pair, but `15 > 10` violates the global invariant since `15` sits in `10`'s *left* subtree and must be less than `10`.

---

**Q: Maximum depth / height of a binary tree.** ⭐

`1 + max(depth(left), depth(right))`, with the base case `depth(null) == 0`. O(n) time (every node visited once), O(h) recursion-stack space.

```java
static int maxDepth(TreeNode n) {
    return n == null ? 0 : 1 + Math.max(maxDepth(n.left), maxDepth(n.right));
}
```

*Follow-up: Is this "nodes on the path" or "edges on the path"?* As written, it counts nodes on the longest root-to-leaf path (a single-node tree returns `1`), which is one more than the edge-count convention (a single-node tree has height `0` edges). Both conventions show up in different problem statements — always clarify which one is expected before assuming.

*Follow-up: How would you find the max depth iteratively instead of recursively?* Level-order BFS, incrementing a counter once per completed level — the counter after the queue empties is the max depth (in the node-count convention, count the levels; in the edge-count convention, count levels minus one).

---

**Q: Diameter of a binary tree.** ⭐

The diameter is the length of the longest path between *any* two nodes in the tree, not necessarily passing through the root. The key insight: at any given node, the longest path passing *through that node* equals `leftHeight + rightHeight`. So a helper function computes height recursively as usual, but as a side effect at every node it updates a separate global "best diameter so far" with that node's `left height + right height`. The function's *return value* (height) is what the parent needs for its own computation; the *global variable* tracks the actual answer, since the best path doesn't have to run through the root.

```java
static int diameterBest;
static int height(TreeNode n) {
    if (n == null) return 0;
    int l = height(n.left), r = height(n.right);
    diameterBest = Math.max(diameterBest, l + r);
    return 1 + Math.max(l, r);
}
```

This is O(n) — the "return one thing to the parent, track a different global answer" pattern, which also shows up in "binary tree maximum path sum" (return the best single-branch extension upward, but track the best full path, which may use both branches at a bending point) and the efficient balanced-tree check below.

*Follow-up: Why can't you just compute `height(root.left) + height(root.right)` once at the root and call that the diameter?* Because the longest path might not pass through the root at all — it could be entirely within a subtree, e.g., a long path connecting two leaves deep inside the left subtree while the root itself only has a single short branch. The global-max-at-every-node approach is what guarantees you catch that.

---

**Q: Lowest common ancestor.** ⭐⭐

In a **BST**, use the ordering invariant: descend from the root; if both targets are less than the current node, recurse left; if both are greater, recurse right; the moment they're not both on the same side (or the current node equals one of them), that node is the split point and the answer — O(h), a single descent with no backtracking.

```java
static TreeNode lca(TreeNode n, int p, int q) {
    if (p < n.val && q < n.val) return lca(n.left, p, q);
    if (p > n.val && q > n.val) return lca(n.right, p, q);
    return n;
}
```

In a **general binary tree** with no ordering to exploit, you must actually search both subtrees: recurse into `left` and `right`; if the current node is itself one of the two targets, return it immediately (it's a valid LCA candidate — it could be an ancestor of the other target, or the answer itself); if both recursive calls return non-null, the current node is the split point where the two searches "met," making it the LCA; if only one side is non-null, propagate that result upward.

```java
static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
    if (root == null || root == p || root == q) return root;
    TreeNode left = lowestCommonAncestor(root.left, p, q);
    TreeNode right = lowestCommonAncestor(root.right, p, q);
    if (left != null && right != null) return root;
    return left != null ? left : right;
}
```

This general version is O(n) since there's no ordering to prune the search — contrast this directly with the BST version's O(h) to reinforce how much an ordering invariant saves.

*Follow-up: What if one of the two target nodes isn't actually in the tree?* The general recursive LCA as written would return some incorrect ancestor without any indication of failure — a robust version needs a separate existence check (or a modified return type that tracks "was p found" and "was q found" explicitly) before trusting the result.

---

**Q: Is a tree balanced?** ⭐

A tree is balanced if, for every node, the heights of its left and right subtrees differ by at most 1. The efficient implementation has the recursive function return the subtree's height *and* use a sentinel value of `-1` to mean "an imbalance was already found somewhere below" — the moment any call detects `-1` from either child (or detects the imbalance itself), it immediately returns `-1` too, short-circuiting the rest of the traversal without doing any more comparisons. The naive alternative — computing height fresh via a separate call for every node's left and right subtree — recomputes the same heights repeatedly (once per ancestor above each subtree), giving O(n²) worst case.

```java
static int checkBalance(TreeNode n) {
    if (n == null) return 0;
    int l = checkBalance(n.left); if (l < 0) return -1;
    int r = checkBalance(n.right); if (r < 0) return -1;
    if (Math.abs(l - r) > 1) return -1;
    return 1 + Math.max(l, r);
}
static boolean isBalanced(TreeNode n) { return checkBalance(n) >= 0; }
```

*Follow-up: Why is -1 a safe sentinel here instead of, say, a boolean flag?* Because heights are always non-negative (`0` for a null subtree, positive otherwise), `-1` can never be confused with a legitimate height value, so it doubles cleanly as both "signal an error" and "propagate that error upward" without needing a separate out-of-band flag or exception.

---

**Q: kth smallest element in a BST.** ⭐

Since a BST's inorder traversal visits nodes in sorted order, the kth smallest element is simply the kth element of that traversal. The straightforward version collects the full inorder list and indexes into it — O(n) time and space, or O(h + k) if you stop traversing the instant you've counted k nodes (no need to visit the rest of the tree).

```java
static int kthSmallest(TreeNode root, int k) {
    List<Integer> sorted = inorder(root);
    return sorted.get(k - 1);
}
```

*Follow-up: For repeated kth-smallest queries on a tree that rarely changes, how would you speed this up?* Augment every node with the size of its subtree, computed once (or maintained incrementally on insert/delete). Then each query becomes an O(h) descent: at each node, compare `k` against the size of the left subtree — if `k` is within it, recurse left; if `k` equals `leftSize + 1`, the current node is the answer; otherwise recurse right with `k` adjusted by subtracting `leftSize + 1`.

---

**Q: Why can a BST degrade to O(n)? What fixes it?** ⭐

Both search and insert on a BST are O(h), where `h` is the tree's height — and nothing about a plain BST insertion prevents the tree from becoming skewed. Inserting already-sorted (or nearly sorted) data always attaches the new node as the right child of the previous maximum (or left child of the previous minimum), producing a tree that's structurally just a linked list, with height `n`. That turns every "O(log n)" operation into O(n).

**Self-balancing trees** (AVL, Red-Black) fix this by performing local restructuring — rotations — after insert/delete to keep the height provably O(log n) regardless of insertion order. AVL trees enforce a strict height-balance invariant (differences ≤ 1 everywhere, exactly like the `isBalanced` check above) at the cost of more frequent rotations; Red-Black trees use a looser, color-based balance invariant that needs fewer rotations on average, which is why **Java's `TreeMap` and `TreeSet` use a Red-Black tree internally** — they need to handle frequent insertions and deletions efficiently while still guaranteeing O(log n) operations and sorted iteration.

*Follow-up: If you know your BST will be built once from data and then only queried (no further inserts), is there a simpler way to guarantee balance than AVL/Red-Black rotations?* Yes — sort the data once, then build the tree by always picking the middle element as the root and recursing on the two halves. This produces a perfectly balanced tree in O(n log n) (dominated by the sort, or O(n) if the data is already sorted) without needing any rotation logic, since there are no further mutations to rebalance around.

---

**Q: Serialize and deserialize a binary tree.**

Preorder traversal with explicit **null markers** fully captures both the shape and values of a tree in a single pass — a bare preorder sequence alone is ambiguous about where subtrees end, but adding an explicit token (e.g., `"#"`) every time you'd recurse into a `null` child removes that ambiguity. Deserializing just replays the same logic: consume tokens in order, and the moment you consume a null marker, that subtree is `null`; otherwise create a node and recursively build its left and right children from the remaining tokens in the stream.

```java
static void serialize(TreeNode n, StringBuilder sb) {
    if (n == null) { sb.append("#,"); return; }
    sb.append(n.val).append(",");
    serialize(n.left, sb);
    serialize(n.right, sb);
}
```

This tests careful recursion and encoding discipline more than any new algorithmic idea — the traversal itself is plain preorder; the entire trick is remembering to encode the `null`s explicitly, since without them the decoder has no way to know when a subtree ends.

*Follow-up: Could you use level-order (BFS) instead of preorder for serialization?* Yes — many real serialization formats (including LeetCode's own tree display format) use level-order with null markers instead. The tradeoff is mostly about which traversal is more natural for the encoding/decoding logic you're writing; both fully capture the tree given explicit null markers.

---

**Q: Symmetric tree / same tree / subtree of another tree.**

All three are **two-tree recursion**, comparing structure and values in lockstep, but they differ in which pair of subtrees each recursive call compares. `isSameTree` compares same-position children: `(a.left, b.left)` and `(a.right, b.right)`. `isSymmetric` compares cross-position children of a *single* tree's two halves: `(left.left, right.right)` and `(left.right, right.left)` — the "mirror" comparison. `isSubtree` runs an `isSameTree` check starting from *every* node of the larger tree, looking for any node whose subtree structurally matches the candidate — O(n·m) in the worst case.

```java
static boolean mirror(TreeNode a, TreeNode b) {
    if (a == null && b == null) return true;
    if (a == null || b == null || a.val != b.val) return false;
    return mirror(a.left, b.right) && mirror(a.right, b.left);
}
```

*Follow-up: Why is `isSubtree` O(n·m) and not O(n+m)?* Because for each of the `n` nodes in the larger tree, you might run a full O(m) `isSameTree` comparison against the candidate subtree before finding (or ruling out) a match at that starting point — there's no shortcut that avoids potentially re-checking structure at every candidate root. (A more advanced approach using serialization + string matching, e.g. KMP, can bring this down to roughly O(n+m), but it's rarely expected as the first answer.)

---

**Q: Build a tree from preorder + inorder.**

The first element of a preorder sequence is always the root of that (sub)tree. Find that value's position in the corresponding inorder sequence — everything to its left in the inorder sequence forms the entire left subtree, everything to its right forms the entire right subtree. Recurse on the matching slices of both sequences, consuming preorder elements left-to-right (a shared index pointer works well) as you build the tree top-down.

```java
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

Use a `HashMap<value, inorderIndex>` built once up front so each "find this value's position in inorder" lookup is O(1) instead of an O(n) linear scan — that's the difference between an O(n) total algorithm and an O(n²) one.

*Follow-up: Could you instead build the tree from inorder + postorder?* Yes, with a symmetric idea: the *last* element of a postorder sequence is the root (since postorder visits the root last), so you split the inorder sequence around it the same way, and you consume the postorder sequence from the *back* rather than the front as you recurse.

*Follow-up: Can you build a tree from preorder + postorder alone (no inorder)?* Only if the tree is known to be **full** (every node has 0 or 2 children) — otherwise the split point between left and right subtrees is ambiguous when a node has only one child, since preorder+postorder together don't disambiguate which side that single child is on without inorder's help.
