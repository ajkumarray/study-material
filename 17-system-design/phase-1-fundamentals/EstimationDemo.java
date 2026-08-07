/*
 * System Design Phase 1 — Fundamentals: back-of-the-envelope estimation
 * Run:  java EstimationDemo.java
 *
 * Design interviews (and real capacity planning) start with rough numbers:
 * how many requests/second, how much storage, how much bandwidth. You don't
 * need precision — you need the right ORDER OF MAGNITUDE to pick an
 * architecture. This computes a worked example (a Twitter-like service) and
 * prints the latency numbers every engineer should know.
 */
public class EstimationDemo {

    public static void main(String[] args) {
        System.out.println("=== back-of-the-envelope: a Twitter-like feed ===");
        estimate(300_000_000L,   // monthly active users
                 0.5,            // fraction active daily
                 2,              // tweets posted per active user per day
                 100,            // reads (feed loads) per active user per day
                 300);           // avg tweet size in bytes

        System.out.println("\n=== latency numbers every programmer should know ===");
        latencyNumbers();

        System.out.println("\n=== the powers-of-two / scale cheatsheet ===");
        System.out.println("  1 thousand = 1e3 (KB)   1 million = 1e6 (MB)");
        System.out.println("  1 billion  = 1e9 (GB)   1 trillion = 1e12 (TB)");
        System.out.println("  seconds/day ~ 86,400 (~1e5). QPS = daily_count / 1e5 is a handy shortcut.");
    }

    static void estimate(long mau, double dailyActiveFrac, int writesPerUser,
                         int readsPerUser, int tweetBytes) {
        long dau = (long) (mau * dailyActiveFrac);
        long writesPerDay = dau * writesPerUser;
        long readsPerDay  = (long) dau * readsPerUser;
        double secondsPerDay = 86_400;

        double writeQps = writesPerDay / secondsPerDay;
        double readQps  = readsPerDay / secondsPerDay;
        double peakReadQps = readQps * 3;               // peak ~2-3x average

        System.out.printf("  DAU               : %,d%n", dau);
        System.out.printf("  write QPS (avg)   : %,.0f  (posts/sec)%n", writeQps);
        System.out.printf("  read  QPS (avg)   : %,.0f  (feed loads/sec)%n", readQps);
        System.out.printf("  read  QPS (peak)  : %,.0f  (~3x avg -> size for peak)%n", peakReadQps);
        System.out.printf("  read:write ratio  : %.0f:1  (read-heavy -> cache + replicas)%n",
                readQps / writeQps);

        long bytesPerDay = writesPerDay * tweetBytes;
        double gbPerDay = bytesPerDay / 1e9;
        System.out.printf("  new storage/day   : %.1f GB  (~%.0f TB/year of raw tweets)%n",
                gbPerDay, gbPerDay * 365 / 1000);
        System.out.printf("  read bandwidth    : %.1f MB/s avg  (readQPS x tweetSize)%n",
                readQps * tweetBytes / 1e6);
        System.out.println("  takeaway: read-heavy + high QPS -> CDN/cache, read replicas,");
        System.out.println("            fan-out-on-write for feeds; storage grows -> sharding.");
    }

    // Approximate orders of magnitude (Jeff Dean's classic list). The RATIOS
    // are the point: memory is ~100x faster than SSD, ~1,000,000x faster than a
    // cross-continent round trip. This is WHY we cache and avoid network hops.
    static void latencyNumbers() {
        print("L1 cache reference",              1);
        print("Branch mispredict",               3);
        print("L2 cache reference",              4);
        print("Mutex lock/unlock",               17);
        print("Main memory reference",           100);
        print("Compress 1KB (fast)",             2_000);
        print("Read 1MB sequentially from RAM",  3_000);
        print("SSD random read",                 16_000);
        print("Read 1MB from SSD",               49_000);
        print("Round trip within datacenter",    500_000);
        print("Read 1MB from disk (HDD)",        825_000);
        print("Round trip CA <-> Netherlands",   150_000_000);
    }
    static void print(String what, long ns) {
        String human = ns >= 1_000_000 ? "%.1f ms".formatted(ns / 1e6)
                     : ns >= 1_000 ? "%.1f us".formatted(ns / 1e3)
                     : ns + " ns";
        System.out.printf("  %-32s %s%n", what, human);
    }
}
