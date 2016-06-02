package za.co.wstoop.jdatalog.statement;

import java.util.List;

import za.co.wstoop.jdatalog.Expr;
import za.co.wstoop.jdatalog.JDatalog;
import za.co.wstoop.jdatalog.Rule;

/**
 * Provides factory methods for building Statement instances for
 * use with the fluent API.
 * <p>
 * {@link JDatalog#prepareStatement(String)} can be used to parse
 * Strings to statement object.
 * </p><p>
 * </p>
 * @see Statement
 * @see Statement#execute(JDatalog, java.util.Map)
 * @see JDatalog#prepareStatement(String)
 */
public class StatementFactory {
	
	/**
	 * Creates a statement to query the database.
	 * @param goals The goals of the query
	 * @return A statement that will query the database for the given goals.
	 */
	public static Statement query(List<Expr> goals) {
		return new QueryStatement(goals);
	}
	
	/**
	 * Creates a statement that will insert a fact into the EDB.
	 * @param fact The fact to insert
	 * @return A statement that will insert the given fact into the database.
	 */
	public static Statement insertFact(Expr fact) {
		return new InsertFactStatement(fact);
	}
	
	/**
	 * Creates a statement that will insert a rule into the IDB.
	 * @param rule The rule to insert
	 * @return A statement that will insert the given rule into the database.
	 */
	public static Statement insertRule(Rule rule) {
		return new InsertRuleStatement(rule);
	}
	
	/**
	 * Creates a statement that will delete facts from the database.
	 * @param goals The goals of the facts to delete
	 * @return A statement that will delete facts matching the goals from the database.
	 */
	public static Statement deleteFacts(List<Expr> goals) {
		return new DeleteStatement(goals);		
	}
}
