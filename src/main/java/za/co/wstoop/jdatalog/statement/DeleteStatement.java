package za.co.wstoop.jdatalog.statement;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import za.co.wstoop.jdatalog.DatalogException;
import za.co.wstoop.jdatalog.Expr;
import za.co.wstoop.jdatalog.JDatalog;

class DeleteStatement implements Statement {

	private List<Expr> goals;
	
	DeleteStatement(List<Expr> goals) {
		this.goals = goals;
	}

	@Override
	public Collection<Map<String, String>> execute(JDatalog datalog, Map<String, String> bindings) throws DatalogException {
		datalog.delete(goals, bindings);
		return null;
	}

}
