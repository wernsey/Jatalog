package za.co.wstoop.jatalog.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.Rule;

public abstract class Engine {

	public abstract Collection<Map<String, String>> query(Jatalog jatalog, List<Expr> goals, Map<String, String> bindings) throws DatalogException;

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
    
    /* Returns a list of rules that are relevant to the query.
    If for example you're querying employment status, you don't care about family relationships, etc.
    The advantages of this of this optimization becomes bigger the more complex the rules get. */
    protected static Collection<String> getRelevantPredicates(Jatalog jatalog, List<Expr> originalGoals) {
	    Collection<String> relevant = new HashSet<>();
	    LinkedList<Expr> goals = new LinkedList<>(originalGoals);
	    while(!goals.isEmpty()) {
	        Expr expr = goals.poll();
			if (!relevant.contains(expr.getPredicate())) {
				relevant.add(expr.getPredicate());
				for (Rule rule : jatalog.getIdb()) {
					if (rule.getHead().getPredicate().equals(expr.getPredicate())) {
						goals.addAll(rule.getBody());
					}
				}
			}
	    }
	    return relevant;
	}
	
    /* This basically constructs the dependency graph for semi-naive evaluation: In the returned map, the string
    is a predicate in the rules' heads that maps to a collection of all the rules that have that predicate in
    their body so that we can easily find the rules that are affected when new facts are deduced in different
    iterations of buildDatabase().
    For example if you have a rule p(X) :- q(X) then there will be a mapping from "q" to that rule
    so that when new facts q(Z) are deduced, the rule will be run in the next iteration to deduce p(Z) */
	protected static Map<String, Collection<Rule>> buildDependentRules(Collection<Rule> rules) {
	    Map<String, Collection<Rule>> map = new HashMap<>();
	    for(Rule rule : rules) {
	        for(Expr goal : rule.getBody()) {
	            Collection<Rule> dependants = map.get(goal.getPredicate());
	            if(dependants == null) {
	                dependants = new ArrayList<>();
	                map.put(goal.getPredicate(), dependants);
	            }
	            if(!dependants.contains(rule))
	                dependants.add(rule);
	        }
	    }
	    return map;
	}
	
    /* Retrieves all the rules that are affected by a collection of facts.
     * This is used as part of the semi-naive evaluation: When new facts are generated, we 
     * take a look at which rules have those facts in their bodies and may cause new facts 
     * to be derived during the next iteration. 
     * The `dependents` parameter was built earlier in the buildDependentRules() method */
    protected static Collection<Rule> getDependentRules(IndexedSet<Expr,String> facts, Map<String, Collection<Rule>> dependents) {
        Set<Rule> dependantRules = new HashSet<>();
        for(String predicate : facts.getIndexes()) {
            Collection<Rule> rules = dependents.get(predicate);
            if(rules != null) {
                dependantRules.addAll(rules);
            }
        }
        return dependantRules;
    }
    
    /* Match the goals in a rule to the facts in the database (recursively). 
     * If the goal is a built-in predicate, it is also evaluated here. */
    protected static Collection<Map<String, String>> matchGoals(List<Expr> goals, IndexedSet<Expr,String> facts, Map<String, String> bindings) {

        Expr goal = goals.get(0); // First goal; Assumes goals won't be empty

        boolean lastGoal = (goals.size() == 1);

        if(goal.isBuiltIn()) {
            Map<String, String> newBindings = new StackMap<String, String>(bindings);
            boolean eval = goal.evalBuiltIn(newBindings);
            if(eval && !goal.isNegated() || !eval && goal.isNegated()) {
                if(lastGoal) {
                    return Collections.singletonList(newBindings);
                } else {
                    return matchGoals(goals.subList(1, goals.size()), facts, newBindings);
                }
            }
            return Collections.emptyList();
        }

        Collection<Map<String, String>> answers = new ArrayList<>();
        if(!goal.isNegated()) {
            // Positive rule: Match each fact to the first goal.
            // If the fact matches: If it is the last/only goal then we can return the bindings
            // as an answer, otherwise we recursively check the remaining goals.
            for(Expr fact : facts.getIndexed(goal.getPredicate())) {
                Map<String, String> newBindings = new StackMap<String, String>(bindings);
                if(fact.unify(goal, newBindings)) {
                    if(lastGoal) {
                        answers.add(newBindings);
                    } else {
                        // More goals to match. Recurse with the remaining goals.
                        answers.addAll(matchGoals(goals.subList(1, goals.size()), facts, newBindings));
                    }
                }
            }
        } else {
            // Negated rule: If you find any fact that matches the goal, then the goal is false.
            // See definition 4.3.2 of [bra2] and section VI-B of [ceri].
            // Substitute the bindings in the rule first.
            // If your rule is `und(X) :- stud(X), not grad(X)` and you're at the `not grad` part, and in the
            // previous goal stud(a) was true, then bindings now contains X:a so we want to search the database
            // for the fact grad(a).
            if(bindings != null) {
                goal = goal.substitute(bindings);
            }
            for(Expr fact : facts.getIndexed(goal.getPredicate())) {
                Map<String, String> newBindings = new StackMap<String, String>(bindings);
                if(fact.unify(goal, newBindings)) {
                    return Collections.emptyList();
                }
            }
            // not found
            if(lastGoal) {
                answers.add(bindings);
            } else {
                answers.addAll(matchGoals(goals.subList(1, goals.size()), facts, bindings));
            }
        }
        return answers;
    }
}
