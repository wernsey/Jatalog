package za.co.wstoop.jatalog;

import java.util.Collection;

import za.co.wstoop.jatalog.engine.IndexedSet;

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
	public Collection<Expr> getFacts(String predicate) {
		return edb.getIndexed(predicate);
	}

}
