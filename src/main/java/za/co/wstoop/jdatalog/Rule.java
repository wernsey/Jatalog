package za.co.wstoop.jdatalog;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import za.co.wstoop.jdatalog.JDatalog.DatalogException;

public class Rule {

	Expr head;
	List<Expr> body;

	Rule(Expr head, List<Expr> body) {
		this.head = head;

		this.body = JDatalog.reorderQuery(body);
	}

	Rule(Expr head, Expr... body) {
		this(head, Arrays.asList(body));
	}

	void validate() throws DatalogException {

		Set<String> headVars = head.terms.stream()
			.filter(term -> JDatalog.isVariable(term))
			.collect(Collectors.toSet());

		Set<String> bodyVars = body.stream()
			.flatMap(expr -> expr.terms.stream())
			.filter(term -> JDatalog.isVariable(term))
			.collect(Collectors.toSet());

		// Enforce the rule that variables in the head must appear in the body
		headVars.removeAll(bodyVars);
		if(!headVars.isEmpty()) {
			throw new DatalogException("These variables from the head of rule " + toString() + " must appear in the body: " + JDatalog.toString(headVars));
		}

		// Check for /safety/: each variable in the body of a rule should appear at least once in a positive expression,
		// to prevent infinite results. E.g. p(X) :- not q(X, Y) is unsafe because there are an infinite number of values
		// for Y that satisfies `not q`. This is a requirement for negation - [gree] contains a nice description.
		// We also leave out variables from the built-in predicates because variables must be bound to be able to compare
		// them, i.e. a rule like `s(A, B) :- r(A,B), A > X` is invalid ('=' is an exception because it can bind variables)
		// You won't be able to tell if the variables have been bound to _numeric_ values until you actually evaluate the
		// expression, though.
		Set<String> positiveVars = body.stream()
			.flatMap(expr -> (!expr.isNegated() && !(expr.isBuiltIn() && !expr.predicate.equals("="))) ? expr.terms.stream() : Stream.empty())
			.filter(term -> JDatalog.isVariable(term))
			.collect(Collectors.toSet());
		bodyVars.removeAll(positiveVars);
		if(!bodyVars.isEmpty()) {
			throw new DatalogException("Each variable of rule " + toString() + " must appear in at least one positive expression: " + JDatalog.toString(bodyVars));
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(head);
		sb.append(" :- ");
		for(int i = 0; i < body.size(); i++) {
			sb.append(body.get(i));
			if(i < body.size() - 1)
				sb.append(", ");
		}
		return sb.toString();
	}
}