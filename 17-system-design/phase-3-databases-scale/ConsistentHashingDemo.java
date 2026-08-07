import java.util.SortedMap;
import java.util.TreeMap;

/*
 * System Design Phase 3 — Databases at scale: CONSISTENT HASHING
 * Run:  java ConsistentHashingDemo.java
 *
 * When you shard data across N nodes, the naive rule `node = hash(key) % N`
 * has a fatal flaw: change N (add/remove a node) and ALMOST EVERY key remaps
 * to a different node — a catastrophic reshuffle that invalidates caches and
 * moves nearly all data. CONSISTENT HASHING fixes this: adding/removing a node
 * only moves the keys NEAR it (~1/N of them). Used by Cassandra, DynamoDB,
 * Redis Cluster, CDNs, and distributed caches.
 */
public class ConsistentHashingDemo {

    public static void main(String[] args) {
        System.out.println("=== naive  hash % N  (why it's bad) ===");
        naiveRemapCost();

        System.out.println("\n=== consistent hashing (the fix) ===");
        ConsistentHash ring = new ConsistentHash(150);   // 150 virtual nodes per physical node
        for (String node : new String[]{"nodeA", "nodeB", "nodeC"}) ring.addNode(node);

        String[] keys = {"user:1", "user:2", "cart:9", "order:42", "session:xyz", "img:cat"};
        System.out.println("  placement with 3 nodes:");
        var before = new java.util.HashMap<String, String>();
        for (String k : keys) { before.put(k, ring.getNode(k)); System.out.printf("    %-12s -> %s%n", k, before.get(k)); }

        System.out.println("  add nodeD -> only keys near D move:");
        ring.addNode("nodeD");
        int moved = 0;
        for (String k : keys) {
            String now = ring.getNode(k);
            if (!now.equals(before.get(k))) { moved++; System.out.printf("    %-12s %s -> %s%n", k, before.get(k), now); }
        }
        System.out.println("  " + moved + "/" + keys.length + " keys moved (naive % N would move almost ALL)");
    }

    // With hash % N, changing N reshuffles almost everything.
    static void naiveRemapCost() {
        int keys = 10_000, moved = 0;
        for (int k = 0; k < keys; k++) {
            int before = Math.floorMod(Integer.hashCode(k), 4);   // 4 nodes
            int after  = Math.floorMod(Integer.hashCode(k), 5);   // add one -> 5 nodes
            if (before != after) moved++;
        }
        System.out.printf("  going 4 -> 5 nodes remapped %,d / %,d keys (%.0f%%)  <-- catastrophic%n",
                moved, keys, 100.0 * moved / keys);
    }

    // ============================================================
    // The ring: nodes and keys are hashed onto a circle (0..2^32). A key
    // belongs to the FIRST node clockwise from its hash. VIRTUAL NODES (many
    // points per physical node) spread load evenly and smooth rebalancing.
    // ============================================================
    static class ConsistentHash {
        private final SortedMap<Integer, String> ring = new TreeMap<>();
        private final int vnodes;
        ConsistentHash(int vnodes) { this.vnodes = vnodes; }

        void addNode(String node) {
            for (int i = 0; i < vnodes; i++) ring.put(hash(node + "#" + i), node);
        }
        void removeNode(String node) {
            for (int i = 0; i < vnodes; i++) ring.remove(hash(node + "#" + i));
        }
        String getNode(String key) {
            if (ring.isEmpty()) return null;
            int h = hash(key);
            SortedMap<Integer, String> tail = ring.tailMap(h);        // first vnode clockwise
            int point = tail.isEmpty() ? ring.firstKey() : tail.firstKey();   // wrap around
            return ring.get(point);
        }
        private int hash(String s) {
            // A simple well-spread hash (FNV-1a) mapped to the ring.
            int h = 0x811c9dc5;
            for (byte b : s.getBytes()) { h ^= (b & 0xff); h *= 0x01000193; }
            return h & 0x7fffffff;
        }
    }
}
