package za.co.wstoop.jatalog;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import org.junit.Test;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.statement.Statement;

// TODO: Code coverage could be better... 
public class FluentTest {
	
	@Test
	public void testApp() throws Exception {
		//This is how you would use the fluent API:
        try {
        	Jatalog jatalog = TestUtils.createDatabase();
			jatalog.validate();
			
            Collection<Map<String, String>> answers;

            // Run a query "who are siblings?"; print the answers
            answers = jatalog.query(Expr.expr("sibling", "A", "B"));
            // Siblings are aaa-aab and aa-ab as well as the reverse
            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));
            assertTrue(TestUtils.answerContains(answers, "A", "ab", "B", "aa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aa", "B", "ab"));

            // Run a query "who are aa's descendants?"; print the answers
            answers = jatalog.query(Expr.expr("ancestor", "aa", "X"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "X", "aab"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaaa"));
            
            // Alternative way to execute the statement:
            answers = jatalog.executeAll("ancestor(aa, X)?");
            assertTrue(TestUtils.answerContains(answers, "X", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "X", "aab"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaaa"));

            // This demonstrates how you would use a built-in predicate in the fluent API.
            answers = jatalog.query(Expr.expr("parent", "aa", "A"), Expr.expr("parent", "aa", "B"), Expr.ne("A", "B"));            
            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));

            // Test deletion
            assertTrue(jatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aa", "aaa")));
            assertTrue(jatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aaa", "aaaa")));
            // This query deletes parent(aa,aaa) and parent(aaa,aaaa)
            jatalog.delete(Expr.expr("parent", "aa", "X"), Expr.expr("parent", "X", "aaaa")); 
            assertFalse(jatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aa", "aaa")));
            assertFalse(jatalog.getEdbProvider().allFacts().contains(Expr.expr("parent", "aaa", "aaaa")));

            // "who are aa's descendants now?"
            answers = jatalog.query(Expr.expr("ancestor", "aa", "X"));
            assertFalse(answers.contains(Expr.expr("parent", "aa", "aaa")));
            assertFalse(answers.contains(Expr.expr("parent", "aaa", "aaaa")));
            assertTrue(TestUtils.answerContains(answers, "X", "aab"));

        } catch (DatalogException e) {
            e.printStackTrace();
        }
	}

	@Test
	public void testBindings() throws Exception {
		// This is how you would use the fluent API with variable bindings:
		// They are inspired by JDBC prepared statements
		try {
			Jatalog jatalog = TestUtils.createDatabase();
			jatalog.validate();
			
			Statement statement = Jatalog.prepareStatement("sibling(A, B)?");
			
			//assertTrue(statement instanceof QueryStatement); // ugh - package private :(
						
			Map<String, String> bindings = Jatalog.makeBindings("A", "aaa", "X", "xxx");
						
			// Run a query "who are siblings (of `aaa`)?"
			Collection<Map<String, String>> answers;
            answers = statement.execute(jatalog, bindings);
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
	public void testMultiGoals() throws Exception {
		// You can have multiple goals in queries.
		try {
			Jatalog jatalog = TestUtils.createDatabase();
			jatalog.validate();

			// Run a query "who are siblings A, and A is not `aaa`?"
			Collection<Map<String, String>> answers;
            answers = jatalog.executeAll("sibling(A, B), A <> aaa?");
			assertTrue(answers != null);
            
            // Only aab is a sibling of aaa
			assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertFalse(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));
            assertTrue(TestUtils.answerContains(answers, "A", "ab", "B", "aa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aa", "B", "ab"));
			
		} catch (DatalogException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testExecute() throws Exception {
		// The Jatalog.executeAll(String) method runs queries directly.
		try {

			Jatalog jatalog = new Jatalog();
			
			// Insert some facts
			jatalog.executeAll("foo(bar). foo(baz).");			
			
			// Run a query:
			Collection<Map<String, String>> answers = jatalog.executeAll("foo(What)?");
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
			Jatalog jatalog = new Jatalog();

			jatalog.fact("parent", "alice", "bob")
				.fact("parent", "bob", "carol");

			jatalog.rule(Expr.expr("ancestor", "X", "Y"), Expr.expr("parent", "X", "Z"), Expr.expr("ancestor", "Z", "Y"))
					.rule(Expr.expr("ancestor", "X", "Y"), Expr.expr("parent", "X", "Y"));
			
			Collection<Map<String, String>> answers;
			answers = jatalog.query(Expr.expr("ancestor", "X", "carol"));

			assertTrue(TestUtils.answerContains(answers, "X", "alice"));
			assertTrue(TestUtils.answerContains(answers, "X", "bob"));
			
		} catch (DatalogException e) {
			e.printStackTrace();
		}
	}
	
}
