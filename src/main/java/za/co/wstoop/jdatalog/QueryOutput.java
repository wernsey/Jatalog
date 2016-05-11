package za.co.wstoop.jdatalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Interface that is used to output the result of a JDatalog statement execution.
 * <p>
 * If you're executing a file that may contain multiple queries, you can pass
 * {@link JDatalog#execute(Reader, QueryOutput)} a {@link QueryOutput} object that will be used to display
 * all the results from the separate queries, with their goals.
 * Otherwise, if you set the QueryOutput parameter to {@code null}, {@link JDatalog#execute(Reader, QueryOutput)}
 * will just return the answers from the last query.
 * </p>
 */
public interface QueryOutput {
    /**
     * Method called by the engine to output the results of a query.
     * @param goals The goals that were evaluated.
     * @param answers The result of the query, as a Collection of variable mappings.
     */
    public void writeResult(List<Expr> goals, Collection<Map<String, String>> answers);
}