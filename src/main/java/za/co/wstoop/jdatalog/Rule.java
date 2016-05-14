package za.co.wstoop.jdatalog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Class that represents a Datalog rule.
 * A rule has a head that is an expression and a body that is a list of expressions.
 * It takes the form {@code foo(X, Y) :- bar(X, Y), baz(X), fred(Y)}
 * @see Expr
 */
public class Rule {

	Expr head;
	List<Expr> body;

	/**
	 * Constructor that takes an expression as the head of the rule and a list of expressions as the body.
	 * The expressions in the body may be reordered to be able to evaluate rules correctly.
	 * @param head The head of the rule (left hand side)
	 * @param body The list of expressions that make up the body of the rule (right hand side)
	 */
	public Rule(Expr head, List<Expr> body) {
		this.head = head;

		this.body = JDatalog.reorderQuery(body);
	}

	/**
	 * Constructor for the fluent API that allows a variable number of expressions in the body.
	 * The expressions in the body may be reordered to be able to evaluate rules correctly.
	 * @param head The head of the rule (left hand side)
	 * @param body The list of expressions that make up the body of the rule (right hand side)
	 */
	public Rule(Expr head, Expr... body) {
		this(head, Arrays.asList(body));
	}

	/**
	 * Checks whether a rule is valid.
	 * There are a variety of reasons why a rule may not be valid:
	 * <ul>
	 * <li> Each variable in the head of the rule <i>must</i> appear in the body.
	 * <li> Each variable in the body of a rule should appear at least once in a positive (that is non-negated) expression.
	 * <li> Variables that are used in built-in predicates must appear at least once in a positive expression.
	 * </ul>
	 * @throws DatalogException if the rule is not valid, with the reason in the message.
	 */
	public void validate() throws DatalogException {

		// Check for /safety/: each variable in the body of a rule should appear at least once in a positive expression,
		// to prevent infinite results. E.g. p(X) :- not q(X, Y) is unsafe because there are an infinite number of values
		// for Y that satisfies `not q`. This is a requirement for negation - [gree] contains a nice description.
		// We also leave out variables from the built-in predicates because variables must be bound to be able to compare
		// them, i.e. a rule like `s(A, B) :- r(A,B), A > X` is invalid ('=' is an exception because it can bind variables)
		// You won't be able to tell if the variables have been bound to _numeric_ values until you actually evaluate the
		// expression, though.
		Set<String> bodyVariables = new HashSet<String>();
		for(Expr clause : body) {
			if (clause.isBuiltIn()) {
				if (clause.terms.size() != 2)
					throw new DatalogException("Operator " + clause.predicate + " must have only two operands");
				String a = clause.terms.get(0);
				String b = clause.terms.get(1);
				if (clause.predicate.equals("=")) {
					if (JDatalog.isVariable(a) && JDatalog.isVariable(b) && !bodyVariables.contains(a)
							&& !bodyVariables.contains(b)) {
						throw new DatalogException("Both variables of '=' are unbound in clause " + a + " = " + b);
					}
				} else {
					if (JDatalog.isVariable(a) && !bodyVariables.contains(a)) {
						throw new DatalogException("Unbound variable " + a + " in " + clause);
					}
					if (JDatalog.isVariable(b) && !bodyVariables.contains(b)) {
						throw new DatalogException("Unbound variable " + b + " in " + clause);
					}
				}
			} 
			if(clause.isNegated()) {
				for (String term : clause.terms) {
					if (JDatalog.isVariable(term) && !bodyVariables.contains(term)) {
						throw new DatalogException("Variable " + term + " of rule " + toString() + " must appear in at least one positive expression");
					}
				}
			} else {
				for (String term : clause.terms) {
					if (JDatalog.isVariable(term)) {
						bodyVariables.add(term);
					}
				}
			}
		}
		
		// Enforce the rule that variables in the head must appear in the body
		for (String term : head.terms) {
			if (!JDatalog.isVariable(term)) {
				throw new DatalogException("Constant " + term + " in head of rule " + toString());
			}
			if (!bodyVariables.contains(term)) {
				throw new DatalogException(
						"Variables " + term + " from the head of rule " + toString() + " must appear in the body");
			}
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