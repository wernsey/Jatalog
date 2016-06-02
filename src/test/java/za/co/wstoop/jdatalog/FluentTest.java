package za.co.wstoop.jdatalog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import org.junit.Test;

import za.co.wstoop.jdatalog.statement.Statement;

// TODO: Code coverage could be better... 
public class FluentTest {

	private JDatalog createDatabase() throws DatalogException {

        JDatalog jDatalog = new JDatalog();

        jDatalog.fact("parent", "a", "aa")
            .fact("parent", "a", "ab")
            .fact("parent", "aa", "aaa")
            .fact("parent", "aa", "aab")
            .fact("parent", "aaa", "aaaa")
            .fact("parent", "c", "ca");

		jDatalog.rule(Expr.expr("ancestor", "X", "Y"), Expr.expr("parent", "X", "Z"), Expr.expr("ancestor", "Z", "Y"))
				.rule(Expr.expr("ancestor", "X", "Y"), Expr.expr("parent", "X", "Y"))
				.rule(Expr.expr("sibling", "X", "Y"), Expr.expr("parent", "Z", "X"), Expr.expr("parent", "Z", "Y"), Expr.ne("X", "Y"))
				.rule(Expr.expr("related", "X", "Y"), Expr.expr("ancestor", "Z", "X"), Expr.expr("ancestor", "Z", "Y"));
		
		return jDatalog;
	}
	
	@Test
	public void testApp() throws Exception {
		//This is how you would use the fluent API:
        try {
        	JDatalog jDatalog = createDatabase();
			jDatalog.validate();
			
            Collection<Map<String, String>> answers;

            // Run a query "who are siblings?"; print the answers
            answers = jDatalog.query(Expr.expr("sibling", "A", "B"));
            // Siblings are aaa-aab and aa-ab as well as the reverse
            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));
            assertTrue(TestUtils.answerContains(answers, "A", "ab", "B", "aa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aa", "B", "ab"));

            // Run a query "who are aa's descendants?"; print the answers
            answers = jDatalog.query(Expr.expr("ancestor", "aa", "X"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "X", "aab"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaaa"));

            // This demonstrates how you would use a built-in predicate in the fluent API.
            answers = jDatalog.query(Expr.expr("parent", "aa", "A"), Expr.expr("parent", "aa", "B"), Expr.ne("A", "B"));            
            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));

            // Test deletion
            assertTrue(jDatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aa", "aaa")));
            assertTrue(jDatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aaa", "aaaa")));
            // This query deletes parent(aa,aaa) and parent(aaa,aaaa)
            jDatalog.delete(Expr.expr("parent", "aa", "X"), Expr.expr("parent", "X", "aaaa")); 
            assertFalse(jDatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aa", "aaa")));
            assertFalse(jDatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aaa", "aaaa")));

            // "who are aa's descendants now?"
            answers = jDatalog.query(Expr.expr("ancestor", "aa", "X"));

        } catch (DatalogException e) {
            e.printStackTrace();
        }
	}

	@Test
	public void testBindings() throws Exception {
		// This is how you would use the fluent API with variable bindings:
		// They are inspired by JDBC prepared statements
		try {
			JDatalog jDatalog = createDatabase();
			jDatalog.validate();
			
			Statement statement = JDatalog.prepareStatement("sibling(A, B)?");
			
			//assertTrue(statement instanceof QueryStatement); // ugh - package private :(
			//assertTrue(goals.size() == 1);
			//assertTrue(goals.contains(new Expr("sibling", "A", "B")));
			// TODO: Unit test of goal with more than one Expr, perhaps "sibling(A,B), A <> aaa"
			
			Map<String, String> bindings = JDatalog.makeBindings("A", "aaa", "X", "xxx");
						
			// Run a query "who are siblings (of `aaa`)?"; print the answers
			Collection<Map<String, String>> answers;
            answers = statement.execute(jDatalog, bindings);
			assertTrue(answers != null);
            
            // Only aab is a sibling of aaa
            assertFalse(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));
            assertFalse(TestUtils.answerContains(answers, "A", "ab", "B", "aa"));
            assertFalse(TestUtils.answerContains(answers, "A", "aa", "B", "ab"));
			
		} catch (DatalogException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testExecute() throws Exception {
		// The JDatalog.executeAll(String) method runs queries directly.
		try {

			JDatalog jDatalog = new JDatalog();
			
			// Insert some facts
			jDatalog.executeAll("foo(bar). foo(baz).");			
			
			// Run a query:
			Collection<Map<String, String>> answers = jDatalog.executeAll("foo(What)?");
			assertTrue(answers != null);
			assertTrue(TestUtils.answerContains(answers, "What", "baz", "What", "bar"));
			assertFalse(TestUtils.answerContains(answers, "What", "fred"));
		} catch (DatalogException e) {
			e.printStackTrace();
		}
    }
		
	@Test
	public void testDemo() throws Exception {
		// This is how you would use the fluent API:
		try {
			JDatalog jDatalog = new JDatalog();

			jDatalog.fact("parent", "alice", "bob")
				.fact("parent", "bob", "carol");

			jDatalog.rule(Expr.expr("ancestor", "X", "Y"), Expr.expr("parent", "X", "Z"), Expr.expr("ancestor", "Z", "Y"))
					.rule(Expr.expr("ancestor", "X", "Y"), Expr.expr("parent", "X", "Y"));
			
			Collection<Map<String, String>> answers;
			answers = jDatalog.query(Expr.expr("ancestor", "X", "carol"));

			assertTrue(TestUtils.answerContains(answers, "X", "alice"));
			assertTrue(TestUtils.answerContains(answers, "X", "bob"));
			
		} catch (DatalogException e) {
			e.printStackTrace();
		}
	}
	
}
