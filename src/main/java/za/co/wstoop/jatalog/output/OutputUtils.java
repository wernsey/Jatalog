package za.co.wstoop.jatalog.output;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.Rule;

/**
 * Utilities for processing {@link Jatalog}'s output.
 */
public class OutputUtils {

    /**
     * Formats a collection of Jatalog entities, like {@link Expr}s and {@link Rule}s
     * @param collection the collection to convert to a string
     * @return A String representation of the collection.
     */
    public static String listToString(List<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        for(Object o : collection)
            sb.append(o.toString()).append(". ");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Formats a Map of variable bindings to a String for output
     * @param bindings the bindings to convert to a String
     * @return A string representing the variable bindings
     */
    public static String bindingsToString(Map<String, String> bindings) {
        StringBuilder sb = new StringBuilder("{");
        int s = bindings.size(), i = 0;
        for(String k : bindings.keySet()) {
            String v = bindings.get(k);
            sb.append(k).append(": ");
            if(v.startsWith("\"")) {
                // Needs more org.apache.commons.lang3.StringEscapeUtils#escapeJava(String)
                sb.append('"').append(v.substring(1).replaceAll("\"", "\\\\\"")).append("\"");
            } else {
                sb.append(v);
            }
            if(++i < s) sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Helper method to convert a collection of answers to a String.
     * <ul>
     * <li> If {@code answers} is null, the line passed to {@code jatalog.query(line)} was a statement that didn't
     *      produce any results, like a fact or a rule, rather than a query.
     * <li> If {@code answers} is empty, then it was a query that doesn't have any answers, so the output is "No."
     * <li> If {@code answers} is a list of empty maps, then it was the type of query that only wanted a yes/no
     *      answer, like {@code siblings(alice,bob)} and the answer is "Yes."
     * <li> Otherwise {@code answers} is a list of all bindings that satisfy the query.
     * </ul>
     * @param answers The collection of answers
     * @return A string representing the answers.
     */
    public static String answersToString(Collection<Map<String, String>> answers) {

        StringBuilder sb = new StringBuilder();
        // If `answers` is null, the line passed to `jatalog.query(line)` was a statement that didn't
        //      produce any results, like a fact or a rule, rather than a query.
        // If `answers` is empty, then it was a query that doesn't have any answers, so the output is "No."
        // If `answers` is a list of empty maps, then it was the type of query that only wanted a yes/no
        //      answer, like `siblings(alice,bob)?` and the answer is "Yes."
        // Otherwise `answers` is a list of all bindings that satisfy the query.
        if(answers != null) {
            if(!answers.isEmpty()){
                if(answers.iterator().next().isEmpty()) {
                    sb.append("Yes.");
                } else {
                    Iterator<Map<String, String>> iter = answers.iterator();
                    while (iter.hasNext()) {
                        sb.append(bindingsToString(iter.next()));
                        if (iter.hasNext()) {
                            sb.append("\n");
                        }
                    }
                }
            } else {
                sb.append("No.");
            }
        }
        return sb.toString();
    }
}
