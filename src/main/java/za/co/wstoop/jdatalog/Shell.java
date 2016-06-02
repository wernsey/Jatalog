package za.co.wstoop.jdatalog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern loadPattern = Pattern.compile("^(?i:load)\\s+(\\S+)\\s*$");

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
                                jDatalog.executeAll(reader, null);
                            }
                        }
                    }
                    Profiler.reset();
                    System.gc();System.runFinalization();
                    System.out.println();

                    QueryOutput qo = new DefaultQueryOutput();
                    for (int run = 0; run < NUM_RUNS; run++) {

                        JDatalog jDatalog = new JDatalog();

                        for (String arg : args) {
                            try (Reader reader = new BufferedReader(new FileReader(arg))) {
                                jDatalog.executeAll(reader, qo);
                            }
                            System.gc();System.runFinalization();
                        }
                    }

                    System.out.println("Profile for running " + OutputUtils.listToString(Arrays.asList(args)) + "; NUM_RUNS=" + NUM_RUNS);
                    Profiler.keySet().stream().sorted().forEach(key -> {
                        double time = Profiler.average(key);
                        double total = Profiler.total(key);
                        int count = Profiler.count(key);
                        System.out.println(String.format("%-21s time: %10.4fms; total: %10.2fms; count: %d", key, time, total, count));
                    });
                } else {
                    JDatalog jDatalog = new JDatalog();
                    QueryOutput qo = new DefaultQueryOutput();
                    for (String arg : args) {
                        try (Reader reader = new BufferedReader(new FileReader(arg))) {
                            jDatalog.executeAll(reader, qo);
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
                    
                    Matcher m = loadPattern.matcher(line);
                    
                    // Intercept some special commands
                    line = line.trim();
                    if(line.equalsIgnoreCase("exit")) {
                        break;
                    } else if(line.equalsIgnoreCase("dump")) {
                        System.out.println(jDatalog);
                        continue;
					} else if (m.matches()) {
						String filename = m.group(1);
						System.out.println("Loading " + filename);
						QueryOutput qo = new DefaultQueryOutput();
						try (Reader reader = new BufferedReader(new FileReader(filename))) {
							jDatalog.executeAll(reader, qo);
						}
						continue;
                    } else if(line.equalsIgnoreCase("validate")) {
                        jDatalog.validate();
                        System.out.println("OK."); // exception not thrown
                        continue;
                    }

                    Collection<Map<String, String>> answers = jDatalog.executeAll(line);
                    System.out.println(OutputUtils.answersToString(answers));                   

                } catch (DatalogException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
