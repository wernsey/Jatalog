package za.co.wstoop.jdatalog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * Shell for JDatalog.
 * This class contains a {@link #main(String...)} method that
 * <ul>
 * <li> if supplied with a list of filenames will execute each one in turn, or
 * <li> if no parameters are specified presents the user with an interactive Read-Evaluate-Print-Loop (REPL)
 *  through which the user can execute Datalog statements (using {@code System.in} and {@code System.out}).
 * </ul>
 */
public class Shell {

    // TODO: The benchmarking is obsolete. Remove it.
    // If you want to do benchmarking, run the file several times to get finer grained results.
    private static final boolean BENCHMARK = false;
    static final int NUM_RUNS = 1000;

    /**
     * Main method.
     * @param args Names of files containing datalog statements to execute.
     *  If none are specified the Shell defaults to a REPL through which the user can interact with the engine.
     */
    public static void main(String... args) {

        if(args.length > 0) {
            // Read input from a file...
            try {

                if(BENCHMARK) {
                    // Execute the program a couple of times without output
                    // to "warm up" the JVM for profiling.
                    for(int run = 0; run < 5; run++) {
                        System.out.print("" + (5 - run) + "...");
                        JDatalog jDatalog = new JDatalog();
                        for(String arg : args) {
                            try( Reader reader = new BufferedReader(new FileReader(arg)) ) {
                                jDatalog.execute(reader, null);
                            }
                        }
                    }
                    Profiler.reset();
                    System.gc();System.runFinalization();
                    System.out.println();

                    QueryOutput qo = new StandardQueryOutput();
                    for (int run = 0; run < NUM_RUNS; run++) {

                        JDatalog jDatalog = new JDatalog();

                        for (String arg : args) {
                            if (JDatalog.isDebugEnabled()) {
                                System.out.println("Executing file " + arg);
                            }
                            try (Reader reader = new BufferedReader(new FileReader(arg))) {
                                jDatalog.execute(reader, qo);
                            }
                        }
                    }

                    System.out.println("Profile for running " + JDatalog.toString(Arrays.asList(args)) + "; NUM_RUNS=" + NUM_RUNS);
                    Profiler.keySet().stream().sorted().forEach(key -> {
                        double time = Profiler.average(key);
                        double total = Profiler.total(key);
                        int count = Profiler.count(key);
                        System.out.println(String.format("%-20s time: %10.4fms; total: %12.2fms; count: %d", key, time, total, count));
                    });
                } else {
                    JDatalog jDatalog = new JDatalog();
                    QueryOutput qo = new StandardQueryOutput();
                    for (String arg : args) {
                        if (JDatalog.isDebugEnabled()) {
                            System.out.println("Executing file " + arg);
                        }
                        try (Reader reader = new BufferedReader(new FileReader(arg))) {
                            jDatalog.execute(reader, qo);
                        }
                    }
                }

            } catch (DatalogException | IOException e) {
                e.printStackTrace();
            }
        } else {
            // Get input from command line
            JDatalog jDatalog = new JDatalog();
            BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("JDatalog: Java Datalog engine\nInteractive mode; type 'exit' to quit.");
            while(true) {
                try {
                    System.out.print("> ");
                    String line = buffer.readLine();
                    if(line == null) {
                        break; // EOF
                    }
                    line = line.trim();
                    if(line.equalsIgnoreCase("exit")) {
                        break;
                    } else if(line.equalsIgnoreCase("dump")) {
                        jDatalog.dump(System.out);
                        continue;
                    } else if(line.equalsIgnoreCase("validate")) {
                        jDatalog.validate();
                        System.out.println("OK."); // exception not thrown
                        continue;
                    }

                    Collection<Map<String, String>> answers = jDatalog.query(line);

                    // If `answers` is null, the line passed to `jDatalog.query(line)` was a statement that didn't
                    //      produce any results, like a fact or a rule, rather than a query.
                    // If `answers` is empty, then it was a query that doesn't have any answers, so the output is "No."
                    // If `answers` is a list of empty maps, then it was the type of query that only wanted a yes/no
                    //      answer, like `siblings(alice,bob)?` and the answer is "Yes."
                    // Otherwise `answers` is a list of all bindings that satisfy the query.
                    if(answers != null) {
                        if(!answers.isEmpty()){
                            if(answers.iterator().next().isEmpty()) {
                                System.out.println("Yes.");
                            } else {
                                for(Map<String, String> answer : answers) {
                                    System.out.println(JDatalog.toString(answer));
                                }
                            }
                        } else {
                            System.out.println("No.");
                        }
                    }

                } catch (DatalogException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // TODO: Rather move this dead code to JUnit tests...
        /* //This is how you would use the fluent API:
        try {
            JDatalog jDatalog = new JDatalog();

            jDatalog.fact("parent", "a", "aa")
                .fact("parent", "a", "ab")
                .fact("parent", "aa", "aaa")
                .fact("parent", "aa", "aab")
                .fact("parent", "aaa", "aaaa")
                .fact("parent", "c", "ca");

            jDatalog.rule(new Expr("ancestor", "X", "Y"), new Expr("parent", "X", "Z"), new Expr("ancestor", "Z", "Y"))
                .rule(new Expr("ancestor", "X", "Y"), new Expr("parent", "X", "Y"))
                .rule(new Expr("sibling", "X", "Y"), new Expr("parent", "Z", "X"), new Expr("parent", "Z", "Y"), new Expr("!=", "X", "Y"))
                .rule(new Expr("related", "X", "Y"), new Expr("ancestor", "Z", "X"), new Expr("ancestor", "Z", "Y"));

            Collection<Map<String, String>> answers;

            // Run a query "who are siblings?"; print the answers
            answers = jDatalog.query(new Expr("sibling", "A", "B"));
            System.out.println("Siblings:");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

            // Run a query "who are aa's descendants?"; print the answers
            answers = jDatalog.query(new Expr("ancestor", "aa", "X"));
            System.out.println("Descendants:");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

            // This demonstrates how you would use a built-in predicate in the fluent API.
            System.out.println("Built-in predicates:");
            answers = jDatalog.query(new Expr("parent", "aa", "A"), new Expr("parent", "aa", "B"), new Expr("!=", "A", "B"));
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

            System.out.println("Before Deletion: " + toString(jDatalog.edb));
            jDatalog.delete(new Expr("parent", "aa", "X"), new Expr("parent", "X", "aaaa")); // deletes parent(aa,aaa) and parent(aaa,aaaa)
            System.out.println("After Deletion: " + toString(jDatalog.edb));

            // "who are aa's descendants now?"
            answers = jDatalog.query(new Expr("ancestor", "aa", "X"));
            System.out.println("Descendants:");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

        } catch (DatalogException e) {
            e.printStackTrace();
        } */

        /* // The JDatalog.query(String) method runs queries directly.
        try{
            JDatalog jDatalog = new JDatalog();
            jDatalog.query("foo(bar). foo(baz).");
            Collection<Map<String, String>> answers = jDatalog.query("foo(What)?");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));
        } catch (DatalogException e) {
            e.printStackTrace();
        } */

    }
}
