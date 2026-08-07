import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

/*
 * Phase 7 — Trees & Binary Search Trees
 * Run:  java -ea TreePatterns.java
 *
 * A BINARY TREE: each node has up to two children (left, right). Most tree
 * problems are solved by RECURSION — "solve for my children, combine" — which
 * mirrors the tree's own recursive structure. Trees are where recursion (Phase
 * 6) becomes second nature.
 *
 * A BINARY SEARCH TREE (BST) adds an ORDERING invariant: for every node,
 * all left-subtree values < node < all right-subtree values. That makes
 * search/insert/delete O(h) — O(log n) if balanced, O(n) if degenerate.
 */
public class TreePatterns {

    static class TreeNode {
        int val;
        TreeNode left, right;
        TreeNode(int val) { this.val = val; }
        TreeNode(int val, TreeNode left, TreeNode right) { this.val = val; this.left = left; this.right = right; }
    }

    public static void main(String[] args) {
        //        1
        //       / \
        //      2   3
        //     / \
        //    4   5
        TreeNode root = new TreeNode(1,
                new TreeNode(2, new TreeNode(4), new TreeNode(5)),
                new TreeNode(3));

        // traversals
        assert inorder(root).toString().equals("[4, 2, 5, 1, 3]");
        assert preorder(root).toString().equals("[1, 2, 4, 5, 3]");
        assert postorder(root).toString().equals("[4, 5, 2, 3, 1]");
        assert levelOrder(root).toString().equals("[[1], [2, 3], [4, 5]]");

        // metrics
        assert maxDepth(root) == 3;
        assert countNodes(root) == 5;
        assert diameter(root) == 3;           // path 4-2-5 ... actually 4-2-1-3 = 3 edges

        // BST operations
        TreeNode bst = null;
        for (int v : new int[]{5, 3, 7, 2, 4, 6, 8}) bst = insertBST(bst, v);
        assert searchBST(bst, 4);
        assert !searchBST(bst, 9);
        assert isValidBST(bst);
        assert inorder(bst).toString().equals("[2, 3, 4, 5, 6, 7, 8]");   // BST inorder = sorted!
        assert kthSmallest(bst, 3) == 4;
        assert lca(bst, 2, 4).val == 3;       // lowest common ancestor of 2 and 4

        // a NON-BST should fail validation
        TreeNode bad = new TreeNode(5, new TreeNode(3), new TreeNode(4));  // 4 < 5 but on the right
        assert !isValidBST(bad);

        assert isBalanced(root);
        assert isSymmetric(new TreeNode(1,
                new TreeNode(2, new TreeNode(3), null),
                new TreeNode(2, null, new TreeNode(3))));

        System.out.println("All tree tests passed.");
        demo(root, bst);
    }

    // ============================================================
    // DFS TRAVERSALS — differ only in WHEN you visit the node vs its children.
    //   preorder:  node, left, right   (copy/serialize a tree)
    //   inorder:   left, node, right   (BST -> sorted order!)
    //   postorder: left, right, node   (delete/free; compute from children up)
    // ============================================================
    static List<Integer> inorder(TreeNode n) {
        List<Integer> out = new ArrayList<>(); inorderH(n, out); return out;
    }
    static void inorderH(TreeNode n, List<Integer> out) {
        if (n == null) return;
        inorderH(n.left, out); out.add(n.val); inorderH(n.right, out);
    }
    static List<Integer> preorder(TreeNode n) {
        List<Integer> out = new ArrayList<>(); preH(n, out); return out;
    }
    static void preH(TreeNode n, List<Integer> out) {
        if (n == null) return;
        out.add(n.val); preH(n.left, out); preH(n.right, out);
    }
    static List<Integer> postorder(TreeNode n) {
        List<Integer> out = new ArrayList<>(); postH(n, out); return out;
    }
    static void postH(TreeNode n, List<Integer> out) {
        if (n == null) return;
        postH(n.left, out); postH(n.right, out); out.add(n.val);
    }

    // BFS / LEVEL ORDER — a queue processes the tree level by level.
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

    // ============================================================
    // METRICS — "combine children's answers" recursion.
    // ============================================================
    static int maxDepth(TreeNode n) {
        return n == null ? 0 : 1 + Math.max(maxDepth(n.left), maxDepth(n.right));
    }
    static int countNodes(TreeNode n) {
        return n == null ? 0 : 1 + countNodes(n.left) + countNodes(n.right);
    }

    // Diameter = longest path (in edges) between any two nodes. At each node the
    // path through it = leftHeight + rightHeight; track the global max.
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

    static boolean isBalanced(TreeNode n) { return checkBalance(n) >= 0; }
    static int checkBalance(TreeNode n) {
        if (n == null) return 0;
        int l = checkBalance(n.left); if (l < 0) return -1;
        int r = checkBalance(n.right); if (r < 0) return -1;
        if (Math.abs(l - r) > 1) return -1;             // unbalanced -> propagate failure
        return 1 + Math.max(l, r);
    }

    static boolean isSymmetric(TreeNode root) {
        return root == null || mirror(root.left, root.right);
    }
    static boolean mirror(TreeNode a, TreeNode b) {
        if (a == null && b == null) return true;
        if (a == null || b == null || a.val != b.val) return false;
        return mirror(a.left, b.right) && mirror(a.right, b.left);
    }

    // ============================================================
    // BST OPERATIONS — the ordering invariant guides the recursion.
    // ============================================================
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

    // Validate BST: every node must fall within an (low, high) range that
    // tightens as you descend. Just checking node vs its children is NOT enough.
    static boolean isValidBST(TreeNode n) { return valid(n, Long.MIN_VALUE, Long.MAX_VALUE); }
    static boolean valid(TreeNode n, long low, long high) {
        if (n == null) return true;
        if (n.val <= low || n.val >= high) return false;
        return valid(n.left, low, n.val) && valid(n.right, n.val, high);
    }

    // kth smallest = kth element of the inorder (sorted) traversal.
    static int kthSmallest(TreeNode root, int k) {
        List<Integer> sorted = inorder(root);
        return sorted.get(k - 1);
    }

    // Lowest common ancestor in a BST: the first node where p and q split.
    static TreeNode lca(TreeNode n, int p, int q) {
        if (p < n.val && q < n.val) return lca(n.left, p, q);
        if (p > n.val && q > n.val) return lca(n.right, p, q);
        return n;                                         // split point (or equals one)
    }

    static void demo(TreeNode root, TreeNode bst) {
        System.out.println("\n=== worked examples ===");
        System.out.println("inorder(tree)       -> " + inorder(root));
        System.out.println("levelOrder(tree)    -> " + levelOrder(root));
        System.out.println("BST inorder(sorted) -> " + inorder(bst));
        System.out.println("kthSmallest(bst, 3) -> " + kthSmallest(bst, 3));
        System.out.println("maxDepth, diameter  -> " + maxDepth(root) + ", " + diameter(root));
    }
}
