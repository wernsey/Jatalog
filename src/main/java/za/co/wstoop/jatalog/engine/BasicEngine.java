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
import java.util.stream.Collectors;

import za.co.wstoop.jatalog.DatalogException;
import za.co.wstoop.jatalog.Expr;
import za.co.wstoop.jatalog.Jatalog;
import za.co.wstoop.jatalog.Rule;

public class BasicEngine extends Engine {

	@Override
	public Collection<Map<String, String>> query(Jatalog j, List<Expr> goals, Map<String, String> bindings) throws DatalogException {
		if (goals.isEmpty())
			return Collections.emptyList();

		// Reorganize the goals so that negated literals are at the end.
		List<Expr> orderedGoals = Engine.reorderQuery(goals);

		// getRelevantPredicates() strips a large part of the rules, so if I want to
		// do some benchmarking of expandDatabase(), I use the IDB directly instead:
		// Collection<Rule> rules = idb;
		Collection<String> predicates = getRelevantPredicates(j, goals);			
		Collection<Rule> rules =  j.getIdb().stream().filter(rule -> predicates.contains(rule.getHead().getPredicate())).collect(Collectors.toSet());

		// Build an IndexedSet<> with only the relevant facts for this particular query.			
		IndexedSet<Expr, String> facts = new IndexedSet<>();
		for(String predicate : predicates) {
			facts.addAll(j.getEdbProvider().getFacts(predicate));
		}

		// Build the database. A Set ensures that the facts are unique
		IndexedSet<Expr, String> resultSet = expandDatabase(facts, rules);

		// Now match the expanded database to the goals
		return matchGoals(orderedGoals, resultSet, bindings);
	}
	
	/* Returns a list of rules that are relevant to the query.
    If for example you're querying employment status, you don't care about family relationships, etc.
    The advantages of this of this optimization becomes bigger the more complex the rules get. */
	private Collection<String> getRelevantPredicates(Jatalog j, List<Expr> originalGoals) {
	    Collection<String> relevant = new HashSet<>();
	    LinkedList<Expr> goals = new LinkedList<>(originalGoals);
	    while(!goals.isEmpty()) {
	        Expr expr = goals.poll();
			if (!relevant.contains(expr.getPredicate())) {
				relevant.add(expr.getPredicate());
				for (Rule rule : j.getIdb()) {
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
	private Map<String, Collection<Rule>> buildDependentRules(Collection<Rule> rules) {
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
    private Collection<Rule> getDependentRules(IndexedSet<Expr,String> facts, Map<String, Collection<Rule>> dependents) {
        	
 //     return facts.getIndexes().stream().flatMap(pred -> dependents.get(pred)!=null?dependents.get(pred).stream():Stream.empty()).filter(rules -> rules != null).collect(Collectors.toSet());
        	
        Set<Rule> dependantRules = new HashSet<>();
        for(String predicate : facts.getIndexes()) {
            Collection<Rule> rules = dependents.get(predicate);
            if(rules != null) {
                dependantRules.addAll(rules);
            }
        }
        return dependantRules;
    }
    

    /* The core of the bottom-up implementation:
     * It computes the stratification of the rules in the EDB and then expands each
     * strata in turn, returning a collection of newly derived facts. */
    private IndexedSet<Expr,String> expandDatabase(IndexedSet<Expr,String> facts, Collection<Rule> allRules) throws DatalogException {
        List< Collection<Rule> > strata = computeStratification(allRules);
        for(int i = 0; i < strata.size(); i++) {
            Collection<Rule> rules = strata.get(i);
            if(rules != null && !rules.isEmpty()) {
                expandStrata(facts, rules);
            }
        }
        return facts;
    }

    /* This implements the semi-naive part of the evaluator.
     * For all the rules derive a collection of new facts; Repeat until no new
     * facts can be derived.
     * The semi-naive part is to only use the rules that are affected by newly derived
     * facts in each iteration of the loop.
     */
    private Collection<Expr> expandStrata(IndexedSet<Expr,String> facts, Collection<Rule> strataRules) {
        Collection<Rule> rules = strataRules;

        Map<String, Collection<Rule>> dependentRules = buildDependentRules(strataRules);

        while(true) {
            // Match each rule to the facts
        	IndexedSet<Expr,String> newFacts = new IndexedSet<>();
            for(Rule rule : rules) {
                newFacts.addAll(matchRule(facts, rule));
            }

            // Repeat until there are no more facts added
            if(newFacts.isEmpty()) {
                return facts;
            }

            // Determine which rules depend on the newly derived facts
            rules = getDependentRules(newFacts, dependentRules);

            facts.addAll(newFacts);
        }
    }
    
    /* Match the facts in the EDB against a specific rule */
    private Set<Expr> matchRule(IndexedSet<Expr,String> facts, Rule rule) {
        if(rule.getBody().isEmpty()) // If this happens, you're using the API wrong.
            return Collections.emptySet();

        // Match the rule body to the facts.
        Collection<Map<String, String>> answers = matchGoals(rule.getBody(), facts, null);
        
        return answers.stream().map(answer -> rule.getHead().substitute(answer))
        		.filter(derivedFact -> !facts.contains(derivedFact))
        		.collect(Collectors.toSet());
    }

    /* Match the goals in a rule to the facts in the database (recursively). 
     * If the goal is a built-in predicate, it is also evaluated here. */
    private Collection<Map<String, String>> matchGoals(List<Expr> goals, IndexedSet<Expr,String> facts, Map<String, String> bindings) {

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
