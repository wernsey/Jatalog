package za.co.wstoop.jdatalog;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Main entry-point for the JDatalog engine.
 * <p>
 * It consists of several aspects: 
 * </p><ul>
 * <li> A database, storing the facts and rules.
 * <li> A parser, for reading and executing statements in the Datalog language.
 * <li> An evaluation engine, which executes Datalog queries.
 * <li> A fluent API for accessing and querying the Datalog database programmatically from Java programs. 
 * </ul>
 * <h3>The Database</h3>
 * <ul>
 * <li> The facts, called the <i>Extensional Database</i> (EDB) which is stored as a Collection of <i>ground literal</i> {@link Expr} objects.
 * 		<p>The methods {@link #fact(Expr)} and {@link #fact(String, String...)} are used to add facts to the database.</p>
 * <li> The rules, called the <i>Intensional Database</i> (IDB) which is stored as a Collection of {@link Rule} objects.
 * 		<p>The methods {@link #rule(Rule)} and {@link #rule(Expr, Expr...)} are used to add rules to the database.</p>
 * </ul>
 * <h3>The Parser</h3>
 * <p>
 * {@link #execute(Reader, QueryOutput)} uses a {@link java.io.Reader} to read a series of Datalog statements from a file or a String
 * and executes them.
 * </p><p> 
 * Statements can insert facts or rules in the database or execute queries against the database.
 * </p><p>
 * {@link #execute(String)} is a shorthand wrapper that can be used with the fluent API.
 * </p>
 * <h3>The Evaluation Engine</h3>
 * JDatalog's evaluation engine is bottom-up, semi-naive with stratified negation.
 * <p>
 * <i>Bottom-up</i> means that the evaluator will start with all the known facts in the EDB and use the rules to derive new facts
 * and repeat this process until no more new facts can be derived. It will then match all of the facts to the goal of the query
 * to determine the answer
 * (The alternative is <i>top-down</i> where the evaluator starts with a series of goals and use the rules and facts in the 
 * database to prove the goal).
 * </p><p>
 * <i>Semi-naive</i> is an optimization wherein the evaluator will only consider a subset of the rules that may be affected 
 * by facts derived during the previous iteration rather than all of the rules.
 * </p><p>
 * <i>Stratified negation</i> arranges the order in which rules are evaluated in such a way that negated goals "makes sense". Consider,
 * for example, the rule {@code p(X) :- q(X), not r(X).}: All the {@code r(X)} facts must be derived first before the {@code p(X)}
 * facts can be derived. If the rules are evaluated in the wrong order then the evaluator may derive a fact {@code p(a)} in one 
 * iteration and then derive {@code r(a)} in a future iteration which will contradict each other.
 * </p><p>
 * Stratification also puts additional constraints on the usage of negated expressions in JDatalog, which the engine checks for.
 * </p><p>
 * In addition JDatalog implements some built-in predicates: equals "=", not equals "&lt;&gt;", greater than "&gt;", greater or
 * equals "&gt;=", less than "&lt;" and less or equals "&lt;=".
 * </p> 
 * <h3>The Fluent API</h3>
 * Several methods exist to make it easy to use JDatalog from a Java program without invoking the parser.
 * <hr>
 * <i>I tried to stick to [ceri]'s definitions, but what they call literals ended up being called <b>expressions</b> in JDatalog. See {@link Expr}</i>
 */
public class JDatalog {

    private List<Expr> edb;         // Facts
    private Collection<Rule> idb;   // Rules

    // TODO: I don't like this. Remove.
    private static boolean debugEnable = false;

    /**
     * Default constructor.
     * <p>
     * Creates a JDatalog instance with an empty IDB and EDB.
     * </p>
     */
    public JDatalog() {
        this.edb = new ArrayList<>();
        this.idb = new ArrayList<>();
    }

    /**
     * Checks whether a term represents a variable.
     * Variables start with upper-case characters. 
     * @param term The term to test
     * @return true if the term is a variable
     */
    static boolean isVariable(String term) {
        return Character.isUpperCase(term.charAt(0));
    }

    /**
     * Executes all the statements in a file/string or another object wrapped by a {@link java.io.Reader}.
     * <p>
     * An optional {@link QueryOutput} object can be supplied as a parameter to output the results of multiple queries.
     * </p><p>
     * This is how to interpret the returned {@code Collection<Map<String, String>>}, assuming you store it in a variable
     * called {@code answers}: 
	 * </p>
     * <ul>
	 * <li> If {@code answers} is {@code null}, the statement didn't produce any results; i.e. it was a fact or a rule, not a query.
	 * <li> If {@code answers} is empty, then it was a query that doesn't have any answers, so the output is "No."
	 * <li> If {@code answers} is a list of empty maps, then it was the type of query that only wanted a yes/no
	 *     answer, like {@code siblings(alice,bob)?} and the answer is "Yes."
	 * <li> Otherwise {@code answers} is a list of all bindings that satisfy the query.
	 * </ul>
     * @param reader The reader from which the statements are read.
     * @param output The object through which output should be written. Can be {@code null} in which case no output will be written.
     * @return The answer of the last statement in the file, as a Collection of variable mappings.
     * @throws DatalogException on syntax and I/O errors encountered while executing. 
     * @see QueryOutput
     */
    public Collection<Map<String, String>> execute(Reader reader, QueryOutput output) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("execute");
        try {
            debug("Executing reader...");

            StreamTokenizer scan = new StreamTokenizer(reader);
            scan.ordinaryChar('.'); // '.' looks like a number to StreamTokenizer by default
            scan.commentChar('%'); // Prolog-style % comments; slashSlashComments and slashStarComments can stay as well.
            scan.quoteChar('"');
            scan.quoteChar('\'');
            //scan.parseNumbers(); // WTF? You can't disable parsing of numbers unless you reset the syntax (http://stackoverflow.com/q/8856750/115589)
            scan.nextToken();

            // Tracks the last query's answers
            Collection<Map<String, String>> answers = null;

            // Tracks the last query's goals (for output purposes)
            List<Expr> goals = new ArrayList<>();
            
            while(scan.ttype != StreamTokenizer.TT_EOF) {
                scan.pushBack();
                answers = DatalogParser.parseStmt(this, scan, goals);
                if(answers != null && output != null) {
                    output.writeResult(goals, answers);
                }
                scan.nextToken();
            }
            return answers;
        } catch (IOException e) {
            throw new DatalogException(e);
        } finally {
            timer.stop();
        }
    }

    /**
     * Executes a Datalog statement.
     * @param statement the statement to execute as a string
     * @return The answer of the string, as a Collection of variable mappings.
     * 	See {@link #execute(Reader, QueryOutput)} for details on how to interpret the result.
     * @throws DatalogException on syntax errors encountered while executing. 
     */
    public Collection<Map<String, String>> execute(String statement) throws DatalogException {
        // It would've been fun to wrap the results in a java.sql.ResultSet, but damn,
        // those are a lot of methods to implement:
        // https://docs.oracle.com/javase/8/docs/api/java/sql/ResultSet.html
        StringReader reader = new StringReader(statement);
        return execute(reader, null);
    }
    
    /* Reorganize the goals in a query so that negated literals are at the end.
    A rule such as `a(X) :- not b(X), c(X)` won't work if the `not b(X)` is evaluated first, since X will not
    be bound to anything yet, meaning there are an infinite number of values for X that satisfy `not b(X)`.
    Reorganising the goals will solve the problem: every variable in the negative literals will have a binding
    by the time they are evaluated if the rule is /safe/, which we assume they are - see Rule#validate()
    Also, the built-in predicates (except `=`) should only be evaluated after their variables have been bound
    for the same reason; see [ceri] for more information. */
    static List<Expr> reorderQuery(List<Expr> query) {
        List<Expr> ordered = new ArrayList<>(query.size());
        for(Expr e : query) {
            if(!e.isNegated() && !(e.isBuiltIn() && !e.predicate.equals("="))) {
                ordered.add(e);
            }
        }
        // Note that a rule like s(A, B) :- r(A, B), X = Y, q(Y), A > X. will cause an error relating to both sides
        // of the '=' being unbound, and it can be fixed by moving the '=' operators to here, but I've decided against
        // it, because the '=' should be evaluated ASAP, and it is difficult to determine programatically when that is.
        // The onus is thus on the user to structure '=' operators properly.
        for(Expr e : query) {
            if(e.isNegated() || (e.isBuiltIn() && !e.predicate.equals("="))) {
                ordered.add(e);
            }
        }
        return ordered;
    }

    /**
     * Executes a query with the specified goals against the database.
     * @param goals The list of goals of the query.
     * @return The answer of the last statement in the file, as a Collection of variable mappings.
     * 	See {@link #execute(Reader, QueryOutput)} for details on how to interpret the result.
     * @throws DatalogException on syntax errors encountered while executing. 
     */
     public Collection<Map<String, String>> query(List<Expr> goals) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("query");
        try {
            if(goals.isEmpty())
                return Collections.emptyList();

            // Reorganize the goals so that negated literals are at the end.
            List<Expr> orderedGoals = reorderQuery(goals);

            // getRelevantRules() strips a large part of the rules, so if I want to
            // do some benchmarking of expandDatabase(), I use the IDB directly instead:
            // Collection<Rule> rules = idb;
            Collection<Rule> rules = getRelevantRules(goals);
            if(debugEnable) {
                System.out.println("To answer query, we need to evaluate: " + toString(rules));
            }
            
            IndexedSet<Expr,String> facts = new IndexedSet<>(edb);

            // Build the database. A Set ensures that the facts are unique
            IndexedSet<Expr,String> resultSet = expandDatabase(facts, rules);
            if(debugEnable) {
                System.out.println("query(): Database = " + toString(resultSet));
            }
            
            // Now match the expanded database to the goals
            return matchGoals(orderedGoals, resultSet, null);
        } finally {
            timer.stop();
        }
    }

	 /**
	  * Executes a query with the specified goals against the database.
	  * This is part of the fluent API.
	  * @param goals The goals of the query.
	  * @return The answer of the last statement in the file, as a Collection of variable mappings.
	  * 	See {@link #execute(Reader, QueryOutput)} for details on how to interpret the result.
	  * @throws DatalogException on syntax errors encountered while executing. 
	  */
     public Collection<Map<String, String>> query(Expr... goals) throws DatalogException {
         return query(Arrays.asList(goals));
     }

    /* Returns a list of rules that are relevant to the query.
        If for example you're querying employment status, you don't care about family relationships, etc.
        The advantages of this of this optimization becomes bigger the more complex the rules get. */
    private Collection<Rule> getRelevantRules(List<Expr> originalGoals) {
        Profiler.Timer timer = Profiler.getTimer("getRelevantRules");
        try {
            Collection<Rule> relevant = new HashSet<>();
            LinkedList<Expr> goals = new LinkedList<>(originalGoals);
            while(!goals.isEmpty()) {
                Expr expr = goals.poll();
                for(Rule rule : idb) {
                    if(rule.head.predicate.equals(expr.predicate) && !relevant.contains(rule)) {
                        relevant.add(rule);
                        goals.addAll(rule.body);
                    }
                }
            }
            return relevant;
        } finally {
            timer.stop();
        }
    }

    /* This basically constructs the dependency graph for semi-naive evaluation: In the returned map, the string
        is a predicate in the rules' heads that maps to a collection of all the rules that have that predicate in
        their body so that we can easily find the rules that are affected when new facts are deduced in different
        iterations of buildDatabase().
        For example if you have a rule p(X) :- q(X) then there will be a mapping from "q" to that rule
        so that when new facts q(Z) are deduced, the rule will be run in the next iteration to deduce p(Z) */
    private Map<String, Collection<Rule>> buildDependentRules(Collection<Rule> rules) {
        Profiler.Timer timer = Profiler.getTimer("buildDependentRules");
        try {
            Map<String, Collection<Rule>> map = new HashMap<>();
            for(Rule rule : rules) {
                for(Expr goal : rule.body) {
                    Collection<Rule> dependants = map.get(goal.predicate);
                    if(dependants == null) {
                        dependants = new ArrayList<>();
                        map.put(goal.predicate, dependants);
                    }
                    if(!dependants.contains(rule))
                        dependants.add(rule);
                }
            }
            return map;
        } finally {
            timer.stop();
        }
    }

    /* Retrieves all the rules that are affected by a collection of facts.
     * This is used as part of the semi-naive evaluation: When new facts are generated, we 
     * take a look at which rules have those facts in their bodies and may cause new facts 
     * to be derived during the next iteration. 
     * The `dependents` parameter was built earlier in the buildDependentRules() method */
    private Collection<Rule> getDependentRules(Collection<Expr> facts, Map<String, Collection<Rule>> dependents) {
        Profiler.Timer timer = Profiler.getTimer("getDependentRules");
        try {
            Set<Rule> dependantRules = new HashSet<>();
            for(Expr fact : facts) {
                Collection<Rule> rules = dependents.get(fact.predicate);
                if(rules != null) {
                    dependantRules.addAll(rules);
                }
            }
            return dependantRules;
        } finally {
            timer.stop();
        }
    }

    /* The core of the bottom-up implementation:
     * It computes the stratification of the rules in the EDB and then expands each
     * strata in turn, returning a collection of newly derived facts. */
    private IndexedSet<Expr,String> expandDatabase(IndexedSet<Expr,String> facts, Collection<Rule> allRules) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("buildDatabase");
        try {
            List< Collection<Rule> > strata = computeStratification(allRules);
            for(int i = 0; i < strata.size(); i++) {
                debug("Expanding strata " + i);
                Collection<Rule> rules = strata.get(i);
                if(rules != null && !rules.isEmpty()) {
                    expandStrata(facts, rules);
                }
            }
            return facts;
        } finally {
            timer.stop();
        }
    }

    /* Computes the stratification of the rules in the IDB by doing a depth-first search.
     * It throws a DatalogException if there are negative loops in the rules, in which case the
     * rules aren't stratified and cannot be computed. */
    private List< Collection<Rule> > computeStratification(Collection<Rule> allRules) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("computeStratification");
        try {
            ArrayList<Collection<Rule>> strata = new ArrayList<>(10);

            Map<String, Integer> strats = new HashMap<>();
            for(Rule rule : allRules) {
                String pred = rule.head.predicate;
                Integer stratum = strats.get(pred);
                if(stratum == null) {
                    stratum = depthFirstSearch(rule.head, allRules, new ArrayList<>(), 0);
                    strats.put(pred, stratum);
                    if(debugEnable) {
                        System.out.println("Strata{" + pred + "} == " + strats.get(pred));
                    }
                }

                while(stratum >= strata.size()) {
                    strata.add(new ArrayList<>());
                }
                strata.get(stratum).add(rule);
            }

            strata.add(allRules);
            return strata;
        } finally {
            timer.stop();
        }
    }

    /* The recursive depth-first method that computes the stratification of a set of rules */
    private int depthFirstSearch(Expr goal, Collection<Rule> graph, List<Expr> visited, int level) throws DatalogException {
        String pred = goal.predicate;

        // Step (1): Guard against negative recursion
        boolean negated = goal.isNegated();
        StringBuilder route = new StringBuilder(pred); // for error reporting
        for(int i = visited.size()-1; i >= 0; i--) {
            Expr e = visited.get(i);
            route.append(e.isNegated() ? " <- ~" : " <- ").append(e.predicate);
            if(e.predicate.equals(pred)) {
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
            if(rule.head.predicate.equals(pred)) {
                for(Expr expr : rule.body) {
                    int x = depthFirstSearch(expr, graph, visited, level + 1);
                    if(expr.negated)
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

    /* This implements the semi-naive part of the evaluator.
     * For all the rules derive a collection of new facts; Repeat until no new
     * facts can be derived.
     * The semi-naive part is to only use the rules that are affected by newly derived
     * facts in each iteration of the loop.
     */
    private Collection<Expr> expandStrata(IndexedSet<Expr,String> facts, Collection<Rule> strataRules) {
        Profiler.Timer timer = Profiler.getTimer("expandStrata");
        try {
            Collection<Rule> rules = strataRules;

            Map<String, Collection<Rule>> dependentRules = buildDependentRules(strataRules);

            while(true) {
                Profiler.Timer loopTimer = Profiler.getTimer("loopTimer");
                try {

                    // Match each rule to the facts
                    Profiler.Timer matchRuleTimer = Profiler.getTimer("matchRules");
                    Set<Expr> newFacts = new HashSet<>();
                    for(Rule rule : rules) {
                        newFacts.addAll(matchRule(facts, rule));
                    }
                    matchRuleTimer.stop();

                    // Delta facts: The new facts that have been added in this iteration for semi-naive evaluation.
                    Profiler.Timer deltaFactsTimer = Profiler.getTimer("deltaFacts");
                    newFacts.removeAll(facts);
                    deltaFactsTimer.stop();

                    // Repeat until there are no more facts added
                    if(newFacts.isEmpty()) {
                        return facts;
                    }
                    if(debugEnable) {
                        System.out.println("expandStrata(): deltaFacts = " + toString(newFacts));
                    }

                    rules = getDependentRules(newFacts, dependentRules);

                    Profiler.Timer addAllTimer = Profiler.getTimer("addAll");
                    facts.addAll(newFacts);
                    addAllTimer.stop();

                } finally {
                    loopTimer.stop();
                }
            }
        } finally {
            timer.stop();
        }
    }

    /* Match the facts in the EDB against a specific rule */
    private Set<Expr> matchRule(IndexedSet<Expr,String> facts, Rule rule) {
        Profiler.Timer timer = Profiler.getTimer("matchRule");
        try {
            if(rule.body.isEmpty()) // If this happens, you're using the API wrong.
                return Collections.emptySet();

            // Match the rule body to the facts.
            Collection<Map<String, String>> answers = matchGoals(rule.body, facts, null);

            // For each match found, substitute the bindings into the head to create a new fact.
            Set<Expr> results = new HashSet<>();
            for(Map<String, String> answer : answers) {
                results.add(rule.head.substitute(answer));
            }
            return results;
        } finally {
            timer.stop();
        }
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
                    if(debugEnable) {
                        System.out.println("** (+) Goal: " + goal + "; " + newBindings);
                    }
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
            for(Expr fact : facts.getIndexed(goal.predicate)) {
                Map<String, String> newBindings = new StackMap<String, String>(bindings);
                if(fact.unify(goal, newBindings)) {
                    if(lastGoal) {
                        if(debugEnable) {
                            System.out.println("** (+) Goal: " + goal + "; " + newBindings);
                        }
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
            for(Expr fact : facts.getIndexed(goal.predicate)) {
                Map<String, String> newBindings = new StackMap<String, String>(bindings);
                if(fact.unify(goal, newBindings)) {
                    return Collections.emptyList();
                }
            }
            // not found
            if(debugEnable) {
                System.out.println("** (-) Goal: " + goal + "; " + bindings);
            }
            if(lastGoal) {
                answers.add(bindings);
            } else {
                answers.addAll(matchGoals(goals.subList(1, goals.size()), facts, bindings));
            }
        }
        return answers;
    }

    /**
     * Validates all the rules and facts in the database.
     * <p>TODO: Certain problems, such as comparison between unbound variables can only be detected 
     * when the query is executed and won't cause a validation failure. <i>It should be possible to fix this</i></p>
     * @throws DatalogException If any rules or facts are invalid. The message contains the reason.
     */
    public void validate() throws DatalogException {
        for(Rule rule : idb) {
            rule.validate();
        }

        // Search for negated loops:
        computeStratification(idb);

        for(int i = 0; i < edb.size(); i++) {
            Expr fact = edb.get(i);
            if(!fact.isGround()) {
                throw new DatalogException("Fact " + fact + " is not ground");
            } else if(fact.isNegated()) {
                throw new DatalogException("Fact " + fact + " is negated");
            }
            // else if(fact.isBuiltIn()) // I allow facts like `a = 5` or `=(a,5)`

            // Technically we don't really need to check whether the facts with the same predicate have 
            // the same arity, since they simply won't unify with goals in the queries.            
            for(int j = i + 1; j < edb.size(); j++) {
                Expr that = edb.get(j);
                if(fact.predicate.equals(that.predicate)) {
                    if(fact.arity() != that.arity()) {
                        throw new DatalogException("Arity mismatch in EDB: " + fact.predicate + "/" + fact.arity()
                            + " vs " + that.predicate + "/" + that.arity());
                    }
                }
            }
        }
    }

    /**
     * Dumps the contents of the database to a output stream
     * @param out where to write the output to
     */
    public void dump(PrintStream out) {
        out.println("% Facts:");
        for(Expr fact : edb) {
            out.println(fact + ".");
        }
        out.println("\n% Rules:");
        for(Rule rule : idb) {
            out.println(rule + ".");
        }
    }

    // Methods for the fluent interface
    
    /**
     * Adds a new {@link Rule} to the IDB database.
     * This is part of the fluent API.
     * @param head The head of the rule
     * @param body The expressions that make up the body of the rule.
     * @return {@code this} so that methods can be chained.
     * @throws DatalogException if the rule is invalid.
     */
    public JDatalog rule(Expr head, Expr... body) throws DatalogException {
        Rule newRule = new Rule(head, body);
        return rule(newRule);
    }
    
    /**
     * Adds a new rule to the IDB database.
     * This is part of the fluent API.
     * @param newRule the rule to add.
     * @return {@code this} so that methods can be chained.
     * @throws DatalogException if the rule is invalid.
     */
    public JDatalog rule(Rule newRule) throws DatalogException {
        newRule.validate();
        idb.add(newRule);
        return this;
    }

    /**
     * Adds a new fact to the EDB database.
     * This is part of the fluent API.
     * @param predicate The predicate of the fact. 
     * @param terms the terms of the fact.
     * @return {@code this} so that methods can be chained.
     * @throws DatalogException if the fact is invalid. Facts must be {@link Expr#isGround() ground} and
     * 	cannot be {@link Expr#isNegated() negated}
     */
    public JDatalog fact(String predicate, String... terms) throws DatalogException {
        return fact(new Expr(predicate, terms));
    }
    
    /**
     * Adds a new fact to the EDB database.
     * This is part of the fluent API.
     * @param newFact The fact to add.
     * @return {@code this} so that methods can be chained.
     * @throws DatalogException if the fact is invalid. Facts must be {@link Expr#isGround() ground} and
     * 	cannot be {@link Expr#isNegated() negated}
     */
    public JDatalog fact(Expr newFact) throws DatalogException {
        if(!newFact.isGround()) {
            throw new DatalogException("Facts must be ground: " + newFact);
        }
        if(newFact.isNegated()) {
            throw new DatalogException("Facts cannot be negated: " + newFact);
        }
        // You can also match the arity of the fact against existing facts in the EDB,
        // but it's more of a principle than a technical problem; see JDatalog#validate()
        edb.add(newFact);
        return this;
    }

    /**
     * Deletes all the facts in the database that matches a specific query 
     * @param goals The query to which to match the facts.
     * @return true if any facts were deleted.
     * @throws DatalogException on errors encountered during evaluation.
     */
    public boolean delete(Expr... goals) throws DatalogException {
        return delete(Arrays.asList(goals));
    }

    /**
     * Deletes all the facts in the database that matches a specific query 
     * @param goals The query to which to match the facts.
     * @return true if any facts were deleted.
     * @throws DatalogException on errors encountered during evaluation.
     */
    public boolean delete(List<Expr> goals) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("delete");
        try {
            Collection<Map<String, String>> answers = query(goals);
            List<Expr> facts = answers.stream()
                // and substitute the answer on each goal
                .flatMap(answer -> goals.stream().map(goal -> goal.substitute(answer)))
                .collect(Collectors.toList());
            if(debugEnable) {
                System.out.println("Facts to delete: " + toString(facts));
            }
            return edb.removeAll(facts);
        } finally {
            timer.stop();
        }
    }

    /**
     * Formats a collection of JDatalog entities, like {@link Expr}s and {@link Rule}s 
     * @param collection the collection to convert to a string
     * @return A String representation of the collection.
     */
    public static String toString(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        for(Object o : collection)
            sb.append(o.toString()).append(". ");
        sb.append("]");
        return sb.toString();
    }

    /**
     * Formats a Map of variable bindings to a String for output
     * @param bindings the bindings to convert to a String
     * @return A string representing the variable bindings
     */
    public static String toString(Map<String, String> bindings) {
        StringBuilder sb = new StringBuilder("{");
        int s = bindings.size(), i = 0;
        for(String k : bindings.keySet()) {
            String v = bindings.get(k);
            sb.append(k).append(": ");
            if(v.startsWith("\"")) {
                // Needs more org.apache.commons.lang3.StringEscapeUtils#escapeJava(String)
                sb.append('"').append(v.substring(1).replaceAll("\"", "\\\\\"")).append("\"");
            } else {
                sb.append(v);
            }
            if(++i < s) sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    // TODO: I'm not happy with this. Remove later.
    static void debug(String message) {
        if(debugEnable) {
            System.out.println(message);
        }
    }
    
    // TODO: I'm not happy with this. Remove later.
	static boolean isDebugEnabled() {
		return debugEnable;
	}

	/* Only used for unit testing */
	List<Expr> getEdb() {
		return edb;
	}

	/* Only used for unit testing */
	Collection<Rule> getIdb() {
		return idb;
	}	
}
