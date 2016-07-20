package za.co.wstoop.jatalog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Rule;

public class RuleTest {
	@Test
	public void testValidate() throws DatalogException {

		Rule rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("q", "B"), new Expr("<>", "A", "B"));
		rule.validate();
		assertTrue(true);

		rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("q", "B"), new Expr("q", "C"),
				new Expr("=", "C", "B"));
		rule.validate();
		assertTrue(true);

		try {
			// The variable C must appear in the body - exception thrown
			rule = new Rule(new Expr("p", "A", "C"), new Expr("q", "A"), new Expr("q", "B"), new Expr("<>", "A", "B"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		try {
			// Variable B must appear in a positive expression - exception
			// thrown
			rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("<>", "A", "B"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		try {
			// Again, variable B must appear in a positive expression -
			// exception thrown
			rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), Expr.not("q", "B"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), Expr.eq("A", "B"));
		rule.validate();
		assertTrue(true);

		rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), Expr.eq("a", "B"));
		rule.validate();
		assertTrue(true);

		rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "B"), Expr.eq("A", "b"));
		rule.validate();
		assertTrue(true);

		try {
			// Invalid number of operands
			rule = new Rule(new Expr("p", "A", "B"), Expr.expr("=", "A", "B", "C"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		try {
			// Both operands of '=' unbound - exception thrown
			rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), Expr.eq("C", "D"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		try {
			// Left operand unbound - exception thrown
			rule = new Rule(new Expr("p", "A", "B"), Expr.ne("C", "B"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		try {
			// Right operand unbound - exception thrown
			rule = new Rule(new Expr("p", "A", "B"), Expr.ne("A", "C"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		rule = new Rule(new Expr("p", "A"), new Expr("q", "A"), Expr.ne("A", "a"));
		rule.validate();
		assertTrue(true);
		rule = new Rule(new Expr("p", "A"), new Expr("q", "A"), Expr.ne("a", "A"));
		rule.validate();
		assertTrue(true);

		rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A", "B"), Expr.not("r", "A", "B"));
		rule.validate();
		assertTrue(true);

		try {
			// Right operand unbound - exception thrown
			rule = new Rule(new Expr("p", "A", "B"), Expr.expr("q", "a", "A"), Expr.not("q", "b", "B"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

		try {
			// Right operand unbound - exception thrown
			rule = new Rule(new Expr("p", "a"), Expr.expr("q", "A"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}

	}

	@Test
	public void testToString() throws DatalogException {
		Rule rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("q", "B"), new Expr("<>", "A", "B"));
		assertTrue(rule.toString().equals("p(A, B) :- q(A), q(B), A <> B"));
	}
	
	@Test
	public void testSubstitute() throws DatalogException {
		Rule rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("q", "B"), new Expr("<>", "A", "B"));
		
		Map<String, String> bindings = new HashMap<>();
		bindings.put("A", "aa");
		Rule subsRule = rule.substitute(bindings);
		assertTrue(subsRule.equals(new Rule(new Expr("p", "aa", "B"), new Expr("q", "aa"), new Expr("q", "B"), new Expr("<>", "aa", "B"))));
		
		// Original rule unchanged?
		assertTrue(rule.equals(new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("q", "B"), new Expr("<>", "A", "B")))); 
	}
}
