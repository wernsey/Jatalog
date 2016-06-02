package za.co.wstoop.jdatalog.statement;

import java.util.Collection;
import java.util.Map;

import za.co.wstoop.jdatalog.DatalogException;
import za.co.wstoop.jdatalog.JDatalog;
import za.co.wstoop.jdatalog.Rule;

class InsertRuleStatement implements Statement {
	
	private final Rule rule;
	
	InsertRuleStatement(Rule rule) {
		this.rule = rule;
	}

	@Override
	public Collection<Map<String, String>> execute(JDatalog datalog, Map<String, String> bindings) throws DatalogException {
		Rule newRule;
		if(bindings != null) {
			newRule = rule.substitute(bindings);
		} else {
			newRule = rule;
		}
		datalog.rule(newRule);
		return null;
	}

}
