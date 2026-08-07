import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

/*
 * Lesson 3.4 — File I/O with NIO.2
 *
 * Two generations of file APIs:
 *   java.io  (1996): File, FileReader, streams — you'll READ this in old code
 *   java.nio.file (Java 7, "NIO.2"): Path + Files — you should WRITE this
 *
 * Mental model:
 *   Path  = WHERE  (a location; may not exist — it's just a name)
 *   Files = static toolbox of operations ON paths (read, write, copy, walk...)
 *
 * Byte vs character:
 *   bytes      -> InputStream/OutputStream  (images, zips, anything binary)
 *   characters -> Reader/Writer + a CHARSET (text)
 * NIO.2 helpers default to UTF-8 (since Java 18 java.io does too).
 */
public class FileIO {

    public static void main(String[] args) throws IOException {

        // A scratch area next to this lesson; cleaned up at the end.
        Path playground = Path.of("playground");
        Files.createDirectories(playground);        // mkdir -p (no error if exists)

        // ============================================================
        // 1. Path — locations and their algebra
        // ============================================================
        System.out.println("=== Path ===");

        Path notes = playground.resolve("notes.txt");     // playground/notes.txt
        System.out.println("path      : " + notes);
        System.out.println("absolute  : " + notes.toAbsolutePath().normalize());
        System.out.println("fileName  : " + notes.getFileName());
        System.out.println("parent    : " + notes.getParent());
        System.out.println("exists?   : " + Files.exists(notes) + "   (a Path is just a NAME)");

        // ============================================================
        // 2. Writing — one-liners for the common cases
        // ============================================================
        System.out.println("\n=== writing ===");

        Files.writeString(notes, """
                Phase 3 progress:
                exceptions done
                collections done
                generics done
                """);
        System.out.println("wrote " + Files.size(notes) + " bytes");

        // Append instead of overwrite — via OpenOptions:
        Files.writeString(notes, "io in progress\n", StandardOpenOption.APPEND);

        // Write a List<String> as lines:
        Path langs = playground.resolve("langs.csv");
        Files.write(langs, List.of("java,1995", "typescript,2012", "go,2009"));

        // ============================================================
        // 3. Reading — pick by file size
        // ============================================================
        System.out.println("\n=== reading ===");

        // Small file? Slurp it whole:
        String content = Files.readString(notes);
        System.out.println("readString -> " + content.lines().count() + " lines");

        // Medium? All lines into a List:
        List<String> lines = Files.readAllLines(langs);
        System.out.println("readAllLines -> " + lines);

        // Large? STREAM the lines lazily — never holds the whole file in memory.
        // The stream holds an open file handle -> ALWAYS try-with-resources:
        try (Stream<String> stream = Files.lines(notes)) {
            long done = stream.filter(l -> l.contains("done")).count();
            System.out.println("streamed   -> " + done + " lines contain 'done'");
        }

        // Old-school buffered reader — same resource discipline (this is
        // where lesson 3.1's try-with-resources earns its living):
        try (BufferedReader reader = Files.newBufferedReader(langs)) {
            System.out.println("buffered   -> first line: " + reader.readLine());
        }

        // ============================================================
        // 4. Copy / move / delete
        // ============================================================
        System.out.println("\n=== manage ===");

        Path backup = playground.resolve("notes.bak");
        Files.copy(notes, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.move(backup, playground.resolve("notes.backup"));
        System.out.println("after copy+move: " + Files.exists(playground.resolve("notes.backup")));
        // Files.delete throws if missing; deleteIfExists doesn't:
        System.out.println("delete missing : " + Files.deleteIfExists(playground.resolve("ghost.txt")));

        // ============================================================
        // 5. Walking a directory tree
        // ============================================================
        System.out.println("\n=== walking ===");

        // Files.walk = recursive; list = one level. Both lazy streams -> close them.
        try (Stream<Path> tree = Files.walk(playground)) {
            tree.filter(Files::isRegularFile)
                .forEach(p -> System.out.println("  found: " + p));
        }

        // Checked exceptions inside lambdas are awkward (lambdas can't
        // throw them) — the standard trick wraps as UncheckedIOException:
        try (Stream<Path> tree = Files.walk(playground)) {
            long bytes = tree.filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);   // 3.1's chaining!
                        }
                    })
                    .sum();
            System.out.println("  total size: " + bytes + " bytes");
        }

        // ============================================================
        // 6. Cleanup (and why order matters)
        // ============================================================
        // Directories must be empty before deletion -> delete DEEPEST first
        // (reverse sort by depth handles it):
        try (Stream<Path> tree = Files.walk(playground)) {
            tree.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
        System.out.println("\ncleaned up: playground exists? " + Files.exists(playground));
    }
}
