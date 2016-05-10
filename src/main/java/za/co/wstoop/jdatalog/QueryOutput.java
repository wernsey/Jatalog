package za.co.wstoop.jdatalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/* If you're executing a file that may contain multiple queries, you can pass
#execute(Reader, QueryOutput) a QueryOutput object that will be used to display
all the results from the separate queries, with their goals.
Otherwise #execute(Reader, QueryOutput) will just return the answers from the
last query. */
public interface QueryOutput {
    public void writeResult(List<Expr> goals, Collection<Map<String, String>> answers);
}