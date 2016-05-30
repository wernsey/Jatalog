package za.co.wstoop.jdatalog;

import java.util.Collection;

/**
 * Implementation of {@link EdbProvider} that wraps around an {@link IndexedSet}
 * for an in-memory EDB.
 */
public class BasicEdbProvider implements EdbProvider {

	private IndexedSet<Expr, String> edb;
	
	public BasicEdbProvider() {
		edb = new IndexedSet<Expr, String>();
	}
	
	@Override
	public Collection<Expr> allFacts() {
		return edb;
	}

	@Override
	public void add(Expr fact) {
		edb.add(fact);
	}

	@Override
	public boolean removeAll(Collection<Expr> facts) {
		return edb.removeAll(facts);
	}

	@Override
	public void validate() throws DatalogException {
		for (Expr fact : allFacts()) {
			fact.validFact();
		}
	}

}
