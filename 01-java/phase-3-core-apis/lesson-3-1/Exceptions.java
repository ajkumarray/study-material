import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/*
 * Lesson 3.1 — Exceptions
 *
 * THE HIERARCHY (memorize this tree):
 *
 *   Throwable
 *   ├── Error                    JVM-level disasters (OutOfMemoryError,
 *   │                            StackOverflowError). DON'T catch these.
 *   └── Exception
 *       ├── (checked)            IOException, SQLException...
 *       │                        "expected failures of the outside world"
 *       │                        compiler FORCES you to handle or declare
 *       └── RuntimeException     NullPointerException, IllegalArgumentException,
 *           (unchecked)          IndexOutOfBounds... "programming bugs" —
 *                                fix the code, don't catch them
 *
 * CHECKED   = the compiler makes callers deal with it (catch or declare).
 * UNCHECKED = RuntimeException + Error; propagates freely.
 */
public class Exceptions {

    public static void main(String[] args) {

        // ============================================================
        // 1. try / catch / finally
        // ============================================================
        System.out.println("=== try/catch/finally ===");

        try {
            int[] arr = new int[3];
            arr[5] = 1;                              // throws!
            System.out.println("never reached");     // skipped after the throw
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("caught: " + e.getMessage());
        } finally {
            System.out.println("finally ALWAYS runs (cleanup lives here)");
        }
        // finally runs whether the try succeeded, threw-and-caught, or
        // threw-and-propagated. Only System.exit() / JVM death skips it.

        // ============================================================
        // 2. Catch order: SUBTYPES FIRST
        // ============================================================
        System.out.println("\n=== catch order & multi-catch ===");

        try {
            Object o = "not a number";
            Integer n = (Integer) o;                  // ClassCastException
        } catch (ClassCastException | NullPointerException e) {
            // MULTI-CATCH: one block, several unrelated types, no duplication.
            System.out.println("multi-caught: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            // Broader type must come AFTER narrower ones —
            // reversed order would be a compile error ("already caught").
            System.out.println("some other runtime problem");
        }

        // ============================================================
        // 3. throw & throws — producing and declaring
        // ============================================================
        System.out.println("\n=== throw / throws ===");

        // throw  = the ACT of raising an exception (a statement)
        // throws = the DECLARATION on a method: "I might throw this,
        //          caller must deal with it" (checked exceptions only need this)
        try {
            registerUser("aj");                       // too short -> unchecked throw
        } catch (IllegalArgumentException e) {
            System.out.println("rejected: " + e.getMessage());
        }

        try {
            readConfig("no-such-file.cfg");           // declared checked exception
        } catch (IOException e) {
            System.out.println("io failure: " + e.getMessage());
        }

        // ============================================================
        // 4. Custom exceptions + CHAINING (the cause)
        // ============================================================
        System.out.println("\n=== custom + chained ===");

        try {
            loadProfile("ajay");
        } catch (ProfileLoadException e) {
            System.out.println("caught: " + e.getMessage());
            System.out.println("  caused by: " + e.getCause());
            // The original low-level exception rides along as the CAUSE —
            // never swallow it; the stack trace prints the whole chain.
        }

        // ============================================================
        // 5. try-with-resources — modern cleanup
        // ============================================================
        System.out.println("\n=== try-with-resources ===");

        // Anything AutoCloseable declared in the ( ) is closed
        // AUTOMATICALLY, in REVERSE order, even on exceptions.
        // Replaces the old finally { if (r != null) r.close(); } dance.
        try (AuditLog log = new AuditLog()) {
            log.write("payment processed");
        }   // <- log.close() already called here, guaranteed
        catch (Exception e) {
            System.out.println("unexpected: " + e);
        }

        // If BOTH the body and close() throw, the body's exception wins;
        // close()'s becomes a SUPPRESSED exception attached to it
        // (e.getSuppressed()) — with old-style finally, the close()
        // exception would OVERWRITE the real one. Subtle but loved
        // by senior interviewers.

        // ============================================================
        // 6. The finally-return trap (never do this)
        // ============================================================
        System.out.println("\n=== finally trap ===");
        System.out.println("trickyValue() = " + trickyValue()
                + "   (finally's return HIJACKED try's return!)");
    }

    // Unchecked: signals a CALLER BUG (bad argument). No `throws` needed.
    static void registerUser(String username) {
        if (username.length() < 3) {
            throw new IllegalArgumentException(
                    "username too short: '" + username + "'");
        }
        System.out.println("registered " + username);
    }

    // Checked: the outside world can fail regardless of code quality.
    // We don't handle it here -> we DECLARE it and let the caller decide.
    static String readConfig(String path) throws IOException {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        }
    }

    // EXCEPTION TRANSLATION: catch the low-level checked exception,
    // rethrow as one meaningful to THIS layer — with the cause chained.
    // (This is what Spring does: SQLException -> DataAccessException.)
    static void loadProfile(String user) throws ProfileLoadException {
        try {
            readConfig(user + ".profile");
        } catch (IOException e) {
            throw new ProfileLoadException("could not load profile for " + user, e);
        }
    }

    @SuppressWarnings("finally")
    static int trickyValue() {
        try {
            return 1;
        } finally {
            return 2;    // overrides try's return AND swallows any exception.
        }                // Real code: NEVER return/throw from finally.
    }
}

/*
 * Custom exception conventions:
 * - name ends in "Exception"; extend Exception (checked) or
 *   RuntimeException (unchecked) — modern APIs lean unchecked.
 * - provide (message) and (message, cause) constructors.
 * - add fields if callers need structured data (error codes, ids).
 */
class ProfileLoadException extends Exception {
    ProfileLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

/*
 * AutoCloseable = one method: close(). Implement it and your class
 * works in try-with-resources. (JDBC connections, streams, HTTP
 * clients — all AutoCloseable.)
 */
class AuditLog implements AutoCloseable {
    AuditLog() {
        System.out.println("  audit log opened");
    }

    void write(String entry) {
        System.out.println("  audit: " + entry);
    }

    @Override
    public void close() {
        System.out.println("  audit log closed (automatically!)");
    }
}
