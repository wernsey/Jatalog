package za.co.wstoop.jdatalog.statement;

import java.util.Collection;
import java.util.Map;

import za.co.wstoop.jdatalog.DatalogException;
import za.co.wstoop.jdatalog.Expr;
import za.co.wstoop.jdatalog.JDatalog;

class InsertFactStatement implements Statement {

	private final Expr fact;
	
	InsertFactStatement(Expr fact) {
		this.fact = fact;
	}

	@Override
	public Collection<Map<String, String>> execute(JDatalog datalog, Map<String, String> bindings) throws DatalogException {
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
