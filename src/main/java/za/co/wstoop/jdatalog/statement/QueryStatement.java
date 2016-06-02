package za.co.wstoop.jdatalog.statement;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import za.co.wstoop.jdatalog.DatalogException;
import za.co.wstoop.jdatalog.Expr;
import za.co.wstoop.jdatalog.JDatalog;

class QueryStatement implements Statement {

	private List<Expr> goals;
	
	QueryStatement(List<Expr> goals) {
		this.goals = goals;
	}

	@Override
	public Collection<Map<String, String>> execute(JDatalog datalog, Map<String, String> bindings) throws DatalogException {
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
