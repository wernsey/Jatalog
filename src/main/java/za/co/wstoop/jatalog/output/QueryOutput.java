package za.co.wstoop.jatalog.output;

import java.io.Reader;
import java.util.Collection;
import java.util.Map;

import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.statement.Statement;

/**
 * Interface that is used to output the result of a Jatalog statement execution.
 * <p>
 * If you're executing a file that may contain multiple queries, you can pass
 * {@link Jatalog#executeAll(Reader, QueryOutput)} a {@link QueryOutput} object that will be used to display
 * all the results from the separate queries, with their goals.
 * Otherwise, if you set the QueryOutput parameter to {@code null}, {@link Jatalog#executeAll(Reader, QueryOutput)}
 * will just return the answers from the last query.
 * </p>
 * @see OutputUtils#answersToString(Collection)
 * @see OutputUtils#bindingsToString(Map)
 */
public interface QueryOutput {
    /**
     * Method called by the engine to output the results of a query.
     * @param statement The statement that was evaluated to produce the output.
     * @param answers The result of the query, as a Collection of variable mappings.
     */
    public void writeResult(Statement statement, Collection<Map<String, String>> answers);
}