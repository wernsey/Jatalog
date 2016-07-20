package za.co.wstoop.jatalog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import za.co.wstoop.jatalog.output.DefaultQueryOutput;
import za.co.wstoop.jatalog.output.QueryOutput;
import za.co.wstoop.jatalog.output.OutputUtils;

/**
 * Shell for Jatalog.
 * This class contains a {@link #main(String...)} method that
 * <ul>
 * <li> if supplied with a list of filenames will execute each one in turn, or
 * <li> if no parameters are specified presents the user with an interactive Read-Evaluate-Print-Loop (REPL)
 *  through which the user can execute Datalog statements (using {@code System.in} and {@code System.out}).
 * </ul>
 */
public class Shell {
    
    /**
     * Main method.
     * @param args Names of files containing datalog statements to execute.
     *  If none are specified the Shell defaults to a REPL through which the user can interact with the engine.
     */
    public static void main(String... args) {

        if(args.length > 0) {
            // Read input from a file...
            try {
                Jatalog jatalog = new Jatalog();
                QueryOutput qo = new DefaultQueryOutput();
                for (String arg : args) {
                    try (Reader reader = new BufferedReader(new FileReader(arg))) {
                        jatalog.executeAll(reader, qo);
                    }
                }                
            } catch (DatalogException | IOException e) {
                e.printStackTrace();
            }
        } else {
            // Get input from command line
            Jatalog jatalog = new Jatalog();
            BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Jatalog: Java Datalog engine\nInteractive mode; Type 'help' for commands, 'exit' to quit.");
            
            boolean timer = false;
            List<String> history = new LinkedList<>();
            
            while(true) {
                try {
                    System.out.print("> ");
                    String line = buffer.readLine();
                    if(line == null) {
                        break; // EOF
                    }
                    line = line.trim();
                    StringTokenizer tokenizer = new StringTokenizer(line);
                    if(!tokenizer.hasMoreTokens())
                    	continue;
                    String command = tokenizer.nextToken().toLowerCase();
                    
                    // Intercept some special commands
                    if(command.equals("exit")) {
                        break;
                    } else if(command.equals("dump")) {
                        System.out.println(jatalog);
						history.add(line);
						continue;
                    } else if(command.equals("history")) {
                        for(String item : history) {
                        	System.out.println(item);
                        }
						continue;
					} else if (command.equals("load")) {
						if(!tokenizer.hasMoreTokens()) {
							System.err.println("error: filename expected");
							continue;
						}
						String filename = tokenizer.nextToken();
						QueryOutput qo = new DefaultQueryOutput();
						try (Reader reader = new BufferedReader(new FileReader(filename))) {
							jatalog.executeAll(reader, qo);
						}
                        System.out.println("OK."); // exception not thrown
						history.add(line);
						continue;
					} else if(command.equals("validate")) {
                        jatalog.validate();
                        System.out.println("OK."); // exception not thrown
                        history.add(line);
                        continue;
                    } else if (command.equals("timer")) {
						if(!tokenizer.hasMoreTokens()) {
							timer = !timer;
						} else {
							timer = tokenizer.nextToken().matches("(?i:yes|on|true)");
						}
						System.out.println("Timer is now " + (timer?"on":"off"));
						history.add(line);
						continue;
					} else if(command.equals("help")) {
						System.out.println("load filename  - Loads and executes the specified file.");
						System.out.println("timer [on|off] - Enable/disable the query timer.");
						System.out.println("validate       - Validates the facts and rules in the database.");
						System.out.println("dump           - Displays the facts and rules on the console.");
						System.out.println("history        - Displays all commands entered in this session.");
						System.out.println("help           - Displays this message.");
						System.out.println("exit           - Quits the program.");
                        continue;
                    } 
                    
                    long start = System.currentTimeMillis();                    
                    Collection<Map<String, String>> answers = jatalog.executeAll(line);
                    double elapsed = (System.currentTimeMillis() - start)/1000.0;
                    
					if (answers != null) {
						// line contained a query with an answer.
						String result = OutputUtils.answersToString(answers);
						System.out.println(result);
	                    
	                    if(timer) {
	                    	System.out.println(String.format(" %.3fs elapsed", elapsed));
	                    }
					}        
                    history.add(line);

                } catch (DatalogException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
