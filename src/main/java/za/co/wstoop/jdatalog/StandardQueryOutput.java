package za.co.wstoop.jdatalog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/* Default implementation of QueryOutput */
public class StandardQueryOutput implements QueryOutput {
    @Override
    public void writeResult(List<Expr> goals, Collection<Map<String, String>> answers) {
        Profiler.Timer timer = Profiler.getTimer("output");
        try {
            System.out.println(JDatalog.toString(goals) + "?");
            if(!answers.isEmpty()){
                if(answers.iterator().next().isEmpty()) {
                    System.out.println("  Yes.");
                } else {
                    for(Map<String, String> answer : answers) {
                        System.out.println("  " + JDatalog.toString(answer));
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