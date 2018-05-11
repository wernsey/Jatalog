package za.co.wstoop.jatalog.statement;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.Term;

class DeleteStatement implements Statement {

	private final List<Expr> goals;
	
	DeleteStatement(List<Expr> goals) {
		this.goals = goals;
	}

	@Override
	public Collection<Map<String, Term>> execute(Jatalog datalog, Map<String, Term> bindings) throws DatalogException {
		datalog.delete(goals, bindings);
		return null;
	}

}
