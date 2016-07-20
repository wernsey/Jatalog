package za.co.wstoop.jatalog.output;

import java.util.Collection;
import java.util.Map;

import za.co.wstoop.jatalog.statement.Statement;

/**
 * Default implementation of {@link QueryOutput} that uses {@code System.out}.
 */
public class DefaultQueryOutput implements QueryOutput {

    @Override
    public void writeResult(Statement statement, Collection<Map<String, String>> answers) {
		System.out.println(statement.toString());
		if (!answers.isEmpty()) {
			if (answers.iterator().next().isEmpty()) {
				System.out.println("  Yes.");
			} else {
				for (Map<String, String> answer : answers) {
					System.out.println("  " + OutputUtils.bindingsToString(answer));
				}
			}
		} else {
			System.out.println("  No.");
		}
	}

}