package za.co.wstoop.jatalog.statement;

import java.util.Collection;
import java.util.Map;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;

class InsertFactStatement implements Statement {

	private final Expr fact;
	
	InsertFactStatement(Expr fact) {
		this.fact = fact;
	}

	@Override
	public Collection<Map<String, String>> execute(Jatalog datalog, Map<String, String> bindings) throws DatalogException {
		Expr newFact;
		if(bindings != null) {
			newFact = fact.substitute(bindings);
		} else {
			newFact = fact;
		}
		datalog.fact(newFact);
		return null;
	}

}
