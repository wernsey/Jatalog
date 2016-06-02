package za.co.wstoop.jdatalog;

import java.util.Collection;
import java.util.Map;

import za.co.wstoop.jdatalog.statement.Statement;

/**
 * Default implementation of {@link QueryOutput} that uses {@code System.out}.
 */
public class DefaultQueryOutput implements QueryOutput {

    @Override
    public void writeResult(Statement statement, Collection<Map<String, String>> answers) {
        Profiler.Timer timer = Profiler.getTimer("output");
        try {
            System.out.println(statement.toString());
            if(!answers.isEmpty()){
                if(answers.iterator().next().isEmpty()) {
                    System.out.println("  Yes.");
                } else {
                    for(Map<String, String> answer : answers) {
                        System.out.println("  " + OutputUtils.bindingsToString(answer));
                    }
                }
            } else {
                System.out.println("  No.");
            }
        } finally {
            timer.stop();
        }
    }

}