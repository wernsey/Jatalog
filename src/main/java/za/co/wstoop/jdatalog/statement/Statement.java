package za.co.wstoop.jdatalog.statement;

import java.util.Collection;
import java.util.Map;

import za.co.wstoop.jdatalog.DatalogException;
import za.co.wstoop.jdatalog.JDatalog;
import za.co.wstoop.jdatalog.OutputUtils;

/**
 * Represents a statement that can be executed against a JDatalog database.
 * <p>
 * There are several types of statements: to insert facts, to insert rules,
 * to retract facts and to query the database.
 * </p><p>
 * Instances of Statement are created by {@link StatementFactory}.
 * </p><p>
 * Strings can be parsed to Statements through {@link JDatalog#prepareStatement(String)}
 * </p>
 * @see StatementFactory
 * @see JDatalog#prepareStatement(String)
 */
public interface Statement {
	
	/**
	 * Executes a statement against a JDatalog database.
	 * @param datalog The database against which to execute the statement.
	 * @param bindings an optional (nullable) mapping of variables to values.
	 * <p>
	 * A statement like "a(B,C)?" with bindings {@code <B = "foo", C = "bar">}
	 * is equivalent to the statement "a(foo,bar)?"
	 * </p> 
	 * @return The result of the statement.
     * <ul>
	 * <li> If null, the statement was an insert or delete that didn't produce query results.
	 * <li> If empty the query's answer is "No."
	 * <li> If a list of empty maps, then answer is "Yes."
	 * <li> Otherwise it is a list of all bindings that satisfy the query.
	 * </ul>
	 * JDatalog provides a {@link OutputUtils#answersToString(Collection)} method that can convert answers to 
	 * Strings
	 * @throws DatalogException if an error occurs in processing the statement
	 * @see OutputUtils#answersToString(Collection)
	 */
	public Collection<Map<String, String>> execute(JDatalog datalog, Map<String, String> bindings) throws DatalogException;
	
	/**
	 * Shorthand for {@code statement.execute(jDatalog, null)}.
	 * @param datalog The database against which to execute the statement.
	 * @return The result of the statement
	 * @throws DatalogException if an error occurs in processing the statement
	 * @see #execute(JDatalog, Map)
	 */
	default public Collection<Map<String, String>> execute(JDatalog datalog) throws DatalogException {
		return execute(datalog, null);
	}
	
	
}
