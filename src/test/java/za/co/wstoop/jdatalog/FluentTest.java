package za.co.wstoop.jdatalog;


import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import static za.co.wstoop.jdatalog.JDatalog.expr;

// TODO: Code coverage could be better... 
public class FluentTest {

	@Test
	public void testApp() throws Exception {
		//This is how you would use the fluent API:
        try {
            JDatalog jDatalog = new JDatalog();

            jDatalog.fact("parent", "a", "aa")
                .fact("parent", "a", "ab")
                .fact("parent", "aa", "aaa")
                .fact("parent", "aa", "aab")
                .fact("parent", "aaa", "aaaa")
                .fact("parent", "c", "ca");

			jDatalog.rule(expr("ancestor", "X", "Y"), expr("parent", "X", "Z"), expr("ancestor", "Z", "Y"))
					.rule(expr("ancestor", "X", "Y"), expr("parent", "X", "Y"))
					.rule(expr("sibling", "X", "Y"), expr("parent", "Z", "X"), expr("parent", "Z", "Y"), expr("!=", "X", "Y"))
					.rule(expr("related", "X", "Y"), expr("ancestor", "Z", "X"), expr("ancestor", "Z", "Y"));

            Collection<Map<String, String>> answers;

            // Run a query "who are siblings?"; print the answers
            answers = jDatalog.query(expr("sibling", "A", "B"));
            // Siblings are aaa-aab and aa-ab as well as the reverse
            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));
            assertTrue(TestUtils.answerContains(answers, "A", "ab", "B", "aa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aa", "B", "ab"));

            // Run a query "who are aa's descendants?"; print the answers
            answers = jDatalog.query(expr("ancestor", "aa", "X"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "X", "aab"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaaa"));

            // This demonstrates how you would use a built-in predicate in the fluent API.
            answers = jDatalog.query(expr("parent", "aa", "A"), expr("parent", "aa", "B"), expr("!=", "A", "B"));            
            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));

            // Test deletion
            assertTrue(jDatalog.getEdb().contains(expr("parent", "aa", "aaa")));
            assertTrue(jDatalog.getEdb().contains(expr("parent", "aaa", "aaaa")));
            // This query deletes parent(aa,aaa) and parent(aaa,aaaa)
            jDatalog.delete(expr("parent", "aa", "X"), expr("parent", "X", "aaaa")); 
            assertFalse(jDatalog.getEdb().contains(expr("parent", "aa", "aaa")));
            assertFalse(jDatalog.getEdb().contains(expr("parent", "aaa", "aaaa")));

            // "who are aa's descendants now?"
            answers = jDatalog.query(expr("ancestor", "aa", "X"));

        } catch (DatalogException e) {
            e.printStackTrace();
        }
	}

	@Test
	public void testExecute() throws Exception {
		// The JDatalog.execute(String) method runs queries directly.
		try {

			JDatalog jDatalog = new JDatalog();
			
			// Insert some facts
			jDatalog.execute("foo(bar). foo(baz).");			
			
			// Run a query:
			Collection<Map<String, String>> answers = jDatalog.execute("foo(What)?");

			assertTrue(TestUtils.answerContains(answers, "What", "baz", "What", "bar"));
			assertFalse(TestUtils.answerContains(answers, "What", "fred"));
		} catch (DatalogException e) {
			e.printStackTrace();
		}
    }
		
	
}
