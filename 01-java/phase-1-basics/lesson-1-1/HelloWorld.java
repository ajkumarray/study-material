/*
 * Lesson 1.1 — Hello World & how Java runs
 *
 * Rule: a public class must live in a file with the SAME name.
 * public class HelloWorld  ->  HelloWorld.java
 */
public class HelloWorld {

    /*
     * main is the entry point the JVM looks for. Its signature is fixed:
     *   public  - callable from outside the class (the JVM is "outside")
     *   static  - callable without creating an object first
     *   void    - returns nothing
     *   String[] args - command-line arguments
     */
    public static void main(String[] args) {
        System.out.println("Hello, World!");

        // Command-line args arrive as an array of Strings:
        //   java HelloWorld Ajay
        // makes args = ["Ajay"]
        if (args.length > 0) {
            System.out.println("Hello, " + args[0] + "!");
        } else {
            System.out.println("(Pass your name as an argument to get a personal greeting.)");
        }
    }
}
