package za.co.wstoop.jdatalog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RuleTest {
	@Test
	public void testValidate() throws DatalogException {

		Rule rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("q", "B"), new Expr("<>", "A", "B"));
		rule.validate();
		assertTrue(true);
		
		rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("q", "B"), new Expr("q", "C"), new Expr("=", "C", "B"));
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
			// Variable B must appear in a positive expression - exception thrown
			rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), new Expr("<>", "A", "B"));
			rule.validate();
			assertFalse(true);
		} catch (DatalogException e) {
			assertTrue(true);
		}	

		try {
			// Again, variable B must appear in a positive expression - exception thrown
			rule = new Rule(new Expr("p", "A", "B"), new Expr("q", "A"), Expr.not("q", "B"));
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
}
