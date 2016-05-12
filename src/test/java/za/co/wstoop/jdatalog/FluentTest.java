package za.co.wstoop.jdatalog;


import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Map;

import static za.co.wstoop.jdatalog.JDatalog.expr;

// TODO: turn into a proper unit test... 
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
            System.out.println("Siblings:");
            answers.stream().forEach(answer -> System.out.println(" -> " + JDatalog.toString(answer)));

            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));
            assertTrue(TestUtils.answerContains(answers, "A", "ab", "B", "aa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aa", "B", "ab"));

            // Run a query "who are aa's descendants?"; print the answers
            answers = jDatalog.query(expr("ancestor", "aa", "X"));
            System.out.println("Descendants:");
            answers.stream().forEach(answer -> System.out.println(" -> " + JDatalog.toString(answer)));
            assertTrue(TestUtils.answerContains(answers, "X", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "X", "aab"));
            assertTrue(TestUtils.answerContains(answers, "X", "aaaa"));

            // This demonstrates how you would use a built-in predicate in the fluent API.
            answers = jDatalog.query(expr("parent", "aa", "A"), expr("parent", "aa", "B"), expr("!=", "A", "B"));            
            System.out.println("Built-in predicates:");
            answers.stream().forEach(answer -> System.out.println(" -> " + JDatalog.toString(answer)));            
            assertTrue(TestUtils.answerContains(answers, "A", "aab", "B", "aaa"));
            assertTrue(TestUtils.answerContains(answers, "A", "aaa", "B", "aab"));

            assertTrue(jDatalog.getEdb().contains(expr("parent", "aa", "aaa")));
            assertTrue(jDatalog.getEdb().contains(expr("parent", "aaa", "aaaa")));
            System.out.println("Before Deletion: " + JDatalog.toString(jDatalog.getEdb()));
            jDatalog.delete(expr("parent", "aa", "X"), expr("parent", "X", "aaaa")); // deletes parent(aa,aaa) and parent(aaa,aaaa)
            System.out.println("After Deletion: " + JDatalog.toString(jDatalog.getEdb()));
            assertFalse(jDatalog.getEdb().contains(expr("parent", "aa", "aaa")));
            assertFalse(jDatalog.getEdb().contains(expr("parent", "aaa", "aaaa")));

            // "who are aa's descendants now?"
            answers = jDatalog.query(expr("ancestor", "aa", "X"));
            System.out.println("Descendants:");
            answers.stream().forEach(answer -> System.out.println(" -> " + JDatalog.toString(answer)));

        } catch (DatalogException e) {
            e.printStackTrace();
        }
	}

	@Test
	public void teststatement() throws Exception {
		// The JDatalog.execute(String) method runs queries directly.
		try {
			System.out.println("Test JDatalog.execute()");
			JDatalog jDatalog = new JDatalog();
			jDatalog.execute("foo(bar). foo(baz).");
			Collection<Map<String, String>> answers = jDatalog.execute("foo(What)?");
			answers.stream().forEach(answer -> System.out.println(" -> " + JDatalog.toString(answer)));

			assertTrue(TestUtils.answerContains(answers, "What", "baz", "What", "bar"));
			assertFalse(TestUtils.answerContains(answers, "What", "fred"));
		} catch (DatalogException e) {
			e.printStackTrace();
		}
    }
		
	
}
