package za.co.wstoop.jatalog.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.Rule;

public abstract class Engine {

	public abstract Collection<Map<String, String>> query(Jatalog j, List<Expr> goals, Map<String, String> bindings) throws DatalogException;

	/* Reorganize the goals in a query so that negated literals are at the end.
    A rule such as `a(X) :- not b(X), c(X)` won't work if the `not b(X)` is evaluated first, since X will not
    be bound to anything yet, meaning there are an infinite number of values for X that satisfy `not b(X)`.
    Reorganising the goals will solve the problem: every variable in the negative literals will have a binding
    by the time they are evaluated if the rule is /safe/, which we assume they are - see Rule#validate()
    Also, the built-in predicates (except `=`) should only be evaluated after their variables have been bound
    for the same reason; see [ceri] for more information. */
    public static List<Expr> reorderQuery(List<Expr> query) {
        List<Expr> ordered = new ArrayList<>(query.size());
        for(Expr e : query) {
            if(!e.isNegated() && !(e.isBuiltIn() && !e.getPredicate().equals("="))) {
                ordered.add(e);
            }
        }
        // Note that a rule like s(A, B) :- r(A, B), X = Y, q(Y), A > X. will cause an error relating to both sides
        // of the '=' being unbound, and it can be fixed by moving the '=' operators to here, but I've decided against
        // it, because the '=' should be evaluated ASAP, and it is difficult to determine programatically when that is.
        // The onus is thus on the user to structure '=' operators properly.
        for(Expr e : query) {
            if(e.isNegated() || (e.isBuiltIn() && !e.getPredicate().equals("="))) {
                ordered.add(e);
            }
        }
        return ordered;
    }

    /* Computes the stratification of the rules in the IDB by doing a depth-first search.
     * It throws a DatalogException if there are negative loops in the rules, in which case the
     * rules aren't stratified and cannot be computed. */
    public static List< Collection<Rule> > computeStratification(Collection<Rule> allRules) throws DatalogException {
        ArrayList<Collection<Rule>> strata = new ArrayList<>(10);

        Map<String, Integer> strats = new HashMap<>();
        for(Rule rule : allRules) {
            String pred = rule.getHead().getPredicate();
            Integer stratum = strats.get(pred);
            if(stratum == null) {
                stratum = depthFirstSearch(rule.getHead(), allRules, new ArrayList<>(), 0);
                strats.put(pred, stratum);
            }

            while(stratum >= strata.size()) {
                strata.add(new ArrayList<>());
            }
            strata.get(stratum).add(rule);
        }

        strata.add(allRules);
        return strata;
    }
    
    /* The recursive depth-first method that computes the stratification of a set of rules */
    private static int depthFirstSearch(Expr goal, Collection<Rule> graph, List<Expr> visited, int level) throws DatalogException {
        String pred = goal.getPredicate();

        // Step (1): Guard against negative recursion
        boolean negated = goal.isNegated();
        StringBuilder route = new StringBuilder(pred); // for error reporting
        for(int i = visited.size()-1; i >= 0; i--) {
            Expr e = visited.get(i);
            route.append(e.isNegated() ? " <- ~" : " <- ").append(e.getPredicate());
            if(e.getPredicate().equals(pred)) {
                if(negated) {
                    throw new DatalogException("Program is not stratified - predicate " + pred + " has a negative recursion: " + route);
                }
                return 0;
            }
            if(e.isNegated()) {
                negated = true;
            }
        }
        visited.add(goal);

        // Step (2): Do the actual depth-first search to compute the strata
        int m = 0;
        for(Rule rule : graph) {
            if(rule.getHead().getPredicate().equals(pred)) {
                for(Expr expr : rule.getBody()) {
                    int x = depthFirstSearch(expr, graph, visited, level + 1);
                    if(expr.isNegated())
                        x++;
                    if(x > m) {
                        m = x;
                    }
                }
            }
        }
        visited.remove(visited.size()-1);

        return m;
    }
}
