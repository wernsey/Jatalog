package za.co.wstoop.jatalog.statement;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.Term;

class QueryStatement implements Statement {

	private final List<Expr> goals;
	
	QueryStatement(List<Expr> goals) {
		this.goals = goals;
	}

	@Override
	public Collection<Map<String, Term>> execute(Jatalog datalog, Map<String, Term> bindings) throws DatalogException {
		return datalog.query(goals, bindings);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < goals.size(); i++) {
			sb.append(goals.get(i).toString());
			if (i < goals.size() - 1)
				sb.append(", ");
		}
		sb.append("?");
		return sb.toString();
	}
}
