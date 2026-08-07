import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/*
 * Phase 5 — Hashing
 * Run:  java -ea HashingPatterns.java
 *
 * A hash map/set gives O(1) average insert, lookup, and delete (Java Phase 3.2
 * — buckets + hashCode/equals). That single power turns a huge number of
 * O(n^2) brute-force scans into O(n): "have I seen X?", "how many of X?",
 * "which items share a key?". The instinct: trade O(n) SPACE for O(n) TIME.
 */
public class HashingPatterns {

    public static void main(String[] args) {
        // two-sum (unsorted) via hash map
        assert Arrays.equals(twoSum(new int[]{2, 7, 11, 15}, 9), new int[]{0, 1});
        assert Arrays.equals(twoSum(new int[]{3, 2, 4}, 6), new int[]{1, 2});

        // frequency counting: first non-repeating character
        assert firstUniqueChar("leetcode") == 0;      // 'l'
        assert firstUniqueChar("loveleetcode") == 2;  // 'v'
        assert firstUniqueChar("aabb") == -1;

        // grouping: anagrams
        List<List<String>> groups = groupAnagrams(new String[]{"eat", "tea", "tan", "ate", "nat", "bat"});
        assert groups.size() == 3;                    // [eat,tea,ate], [tan,nat], [bat]

        // set membership: longest consecutive sequence in O(n)
        assert longestConsecutive(new int[]{100, 4, 200, 1, 3, 2}) == 4;   // 1,2,3,4
        assert longestConsecutive(new int[]{}) == 0;

        // dedupe / contains-duplicate-within-k
        assert containsNearbyDuplicate(new int[]{1, 2, 3, 1}, 3);
        assert !containsNearbyDuplicate(new int[]{1, 2, 3, 1}, 2);

        // subarray sum == k (prefix sum + map, revisited from Phase 2)
        assert subarraySum(new int[]{1, 1, 1}, 2) == 2;
        assert subarraySum(new int[]{1, 2, 3}, 3) == 2;

        System.out.println("All hashing tests passed.");
        demo();
    }

    // TWO-SUM, unsorted: remember each value's index; for x, look for target-x.
    // One pass, O(n) time & space. (The sorted version used two pointers, Phase 2.)
    static int[] twoSum(int[] a, int target) {
        Map<Integer, Integer> seen = new HashMap<>();     // value -> index
        for (int i = 0; i < a.length; i++) {
            Integer j = seen.get(target - a[i]);
            if (j != null) return new int[]{j, i};
            seen.put(a[i], i);
        }
        return new int[]{-1, -1};
    }

    // FREQUENCY COUNT: tally, then scan for the first count-1 char.
    static int firstUniqueChar(String s) {
        Map<Character, Integer> count = new HashMap<>();
        for (char c : s.toCharArray()) count.merge(c, 1, Integer::sum);
        for (int i = 0; i < s.length(); i++) if (count.get(s.charAt(i)) == 1) return i;
        return -1;
    }

    // GROUPING by a canonical key: anagrams share their sorted letters.
    static List<List<String>> groupAnagrams(String[] words) {
        Map<String, List<String>> map = new HashMap<>();
        for (String w : words) {
            char[] chars = w.toCharArray();
            Arrays.sort(chars);                            // "eat" -> "aet"
            String key = new String(chars);
            map.computeIfAbsent(key, k -> new ArrayList<>()).add(w);   // lazy multimap
        }
        return new ArrayList<>(map.values());
    }

    // SET MEMBERSHIP for O(1) reach: only start counting a run at its SMALLEST
    // element (n-1 not present). Each number is visited O(1) times -> O(n) total.
    static int longestConsecutive(int[] a) {
        Set<Integer> set = new HashSet<>();
        for (int x : a) set.add(x);
        int best = 0;
        for (int x : set) {
            if (!set.contains(x - 1)) {                    // x is a run start
                int len = 1;
                while (set.contains(x + len)) len++;
                best = Math.max(best, len);
            }
        }
        return best;
    }

    // Sliding set of the last k indices -> duplicate within distance k.
    static boolean containsNearbyDuplicate(int[] a, int k) {
        Map<Integer, Integer> last = new HashMap<>();
        for (int i = 0; i < a.length; i++) {
            Integer j = last.get(a[i]);
            if (j != null && i - j <= k) return true;
            last.put(a[i], i);
        }
        return false;
    }

    // Prefix sum + map: count subarrays summing to k in O(n) (Phase 2.3 combo).
    static int subarraySum(int[] a, int k) {
        Map<Integer, Integer> count = new HashMap<>();
        count.put(0, 1);
        int running = 0, res = 0;
        for (int x : a) {
            running += x;
            res += count.getOrDefault(running - k, 0);
            count.merge(running, 1, Integer::sum);
        }
        return res;
    }

    static void demo() {
        System.out.println("\n=== worked examples ===");
        System.out.println("twoSum([2,7,11,15], 9)   -> " + Arrays.toString(twoSum(new int[]{2,7,11,15}, 9)));
        System.out.println("firstUniqueChar(\"loveleetcode\") -> " + firstUniqueChar("loveleetcode"));
        System.out.println("groupAnagrams([eat,tea,tan,ate,nat,bat]) -> "
                + groupAnagrams(new String[]{"eat","tea","tan","ate","nat","bat"}));
        System.out.println("longestConsecutive([100,4,200,1,3,2]) -> "
                + longestConsecutive(new int[]{100,4,200,1,3,2}));
    }
}
