package za.co.wstoop.jdatalog;

import java.util.Collection;


public interface EdbProvider {

	/**
	 * Retrieves a {@code Collection} of all the facts in the database.
	 * <p>
	 * TODO: I would very much like for the {@link JDatalog#query(java.util.List, java.util.Map)} not to depend on {@link #allFacts()}.
	 * rather retrieve facts as needed. More info in the README
	 * </p> 
	 * @return All the facts in the EDB
	 */
	public Collection<Expr> allFacts();
	
	/**
	 * Adds a fact to the EDB database.
	 * @param fact The fact to add
	 */
	public void add(Expr fact);
	
	/**
	 * Removes facts from the database
	 * @param facts the facts to remove
	 * @return true if facts were removed
	 */
	public boolean removeAll(Collection<Expr> facts);

	/**
	 * The `EdbProvider` has to validate the facts itself because 
	 * facts stored in memory may have to be validated differently from facts backed by a 
	 * database. The concern is that iterating through all of the facts in a database on 
	 * disk may not scale that well if the number of facts increases.
	 * @throws DatalogException if there are invalid facts in the EDB. See {@link Expr#validFact()}.
	 */
	public void validate() throws DatalogException;
}
