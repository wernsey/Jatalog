package za.co.wstoop.jatalog.engine;

import java.util.Collection;
import java.util.Collections;
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
	public Collection<Map<String, String>> query(Jatalog jatalog, List<Expr> goals, Map<String, String> bindings) throws DatalogException {
		if (goals.isEmpty())
			return Collections.emptyList();

		// Reorganize the goals so that negated literals are at the end.
		List<Expr> orderedGoals = Engine.reorderQuery(goals);

		
		Collection<String> predicates = getRelevantPredicates(jatalog, goals);			
		Collection<Rule> rules = jatalog.getIdb().stream().filter(rule -> predicates.contains(rule.getHead().getPredicate())).collect(Collectors.toSet());

		// Build an IndexedSet<> with only the relevant facts for this particular query.			
		IndexedSet<Expr, String> facts = new IndexedSet<>();
		for(String predicate : predicates) {
			facts.addAll(jatalog.getEdbProvider().getFacts(predicate));
		}

		// Build the database. A Set ensures that the facts are unique
		IndexedSet<Expr, String> resultSet = expandDatabase(facts, rules);

		// Now match the expanded database to the goals
		return matchGoals(orderedGoals, resultSet, bindings);
	}
	
    /* The core of the bottom-up implementation:
     * It computes the stratification of the rules in the EDB and then expands each
     * strata in turn, returning a collection of newly derived facts. */
    private IndexedSet<Expr,String> expandDatabase(IndexedSet<Expr,String> facts, Collection<Rule> allRules) throws DatalogException {
        List< Collection<Rule> > strata = computeStratification(allRules);
        for(int i = 0; i < strata.size(); i++) {
            Collection<Rule> rules = strata.get(i);
            expandStrata(facts, rules);
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

		if (strataRules == null || strataRules.isEmpty()) {
			return Collections.emptyList();
		}
		
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

}
