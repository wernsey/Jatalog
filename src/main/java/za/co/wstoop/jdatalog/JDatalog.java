package za.co.wstoop.jdatalog;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class JDatalog {

    private List<Expr> edb;         // Facts
    private Collection<Rule> idb;   // Rules

    private static boolean debugEnable = false;

    public JDatalog() {
        this.edb = new ArrayList<>();
        this.idb = new ArrayList<>();
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

    static boolean isVariable(String term) {
        return Character.isUpperCase(term.charAt(0));
    }

    // Methods for the fluent interface
    public JDatalog rule(Expr head, Expr... body) throws DatalogException {
        Rule newRule = new Rule(head, body);
        return rule(newRule);
    }
    public JDatalog rule(Rule newRule) throws DatalogException {
        newRule.validate();
        idb.add(newRule);
        return this;
    }

    public JDatalog fact(String predicate, String... terms) throws DatalogException {
        return fact(new Expr(predicate, terms));
    }
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

    /* If you're executing a file that may contain multiple queries, you can pass
    #execute(Reader, QueryOutput) a QueryOutput object that will be used to display
    all the results from the separate queries, with their goals.
    Otherwise #execute(Reader, QueryOutput) will just return the answers from the
    last query. */
    public static interface QueryOutput {
        public void writeResult(List<Expr> goals, Collection<Map<String, String>> answers);
    }

    /* Default implementation of QueryOutput */
    public static class StandardQueryOutput implements QueryOutput {
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

    /* Executes all the statements in a file/string/whatever the Reader is wrapping */
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
                answers = parseStmt(scan, goals);
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

    private Collection<Map<String, String>> parseStmt(StreamTokenizer scan, List<Expr> goals) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("parseStmt");
        try {

            scan.nextToken();
            // "delete a(b,X)." deletes facts from the IDB that matches the query.
            // It is not standard Datalog AFAIK.
            if(scan.ttype == StreamTokenizer.TT_WORD && scan.sval.equalsIgnoreCase("delete")) {
                goals.clear();
                do {
                    Expr e = parseExpr(scan);
                    goals.add(e);
                } while(scan.nextToken() == ',');
                if(scan.ttype != '.') {
                    throw new DatalogException("[line " + scan.lineno() + "] Expected '.' after 'delete'");
                }
                if(debugEnable) {
                    System.out.println("Parser [line " + scan.lineno() + "]: Deleting goals: " + toString(goals));
                }
                delete(goals);
                return null;
            } else {
                scan.pushBack();
            }

            Expr head = parseExpr(scan);
            if(scan.nextToken() == ':') {
                // We're dealing with a rule
                if(scan.nextToken() != '-') {
                    throw new DatalogException("[line " + scan.lineno() + "] Expected ':-'");
                }
                List<Expr> body = new ArrayList<>();
                do {
                    Expr arg = parseExpr(scan);
                    body.add(arg);
                } while(scan.nextToken() == ',');

                if(scan.ttype != '.') {
                    throw new DatalogException("[line " + scan.lineno() + "] Expected '.' after rule");
                }
                try {
                    Rule newRule = new Rule(head, body);
                    rule(newRule);
                    debug("Parser [line " + scan.lineno() + "]: Got rule: " + newRule);
                } catch (DatalogException de) {
                    throw new DatalogException("[line " + scan.lineno() + "] Rule is invalid", de);
                }
            } else {
                // We're dealing with a fact, or a query
                if(scan.ttype == '.') {
                    // It's a fact
                    try {
                        fact(head);
                        debug("Parser [line " + scan.lineno() + "]: Got fact: " + head);
                    } catch (DatalogException de) {
                        throw new DatalogException("[line " + scan.lineno() + "] Fact is invalid", de);
                    }
                } else {
                    // It's a query
                    goals.clear();
                    goals.add(head);
                    if (scan.ttype != '.' && scan.ttype != '?' && scan.ttype != ',') {
                        /* You _can_ write facts like `a = 5 .` but I recommend against it; if you do then you *must* have the space between the
                        5 and the '.' otherwise the parser sees it as 5.0 and the error message can be a bit confusing. */
                        throw new DatalogException("[line " + scan.lineno() + "] Expected one of '.', ',' or '?' after fact/query expression");
                    }
                    while(scan.ttype == ',') {
                        goals.add(parseExpr(scan));
                        scan.nextToken();
                    }
                    debug("Parser [line " + scan.lineno() + "]: Got query: " + toString(goals));

                    if(scan.ttype == '?') {
                        try {
                            return query(goals);
                        } catch (DatalogException e) {
                            // Attach the line number to any exceptions thrown; the small drawback is that you lose
                            // the original stacktrace, but the line number is more important for users.
                            throw new DatalogException("[line " + scan.lineno() + "] " + e.getMessage());
                        }
                    } else {
                        throw new DatalogException("[line " + scan.lineno() + "] Expected '?' after query");
                    }
                }
            }
        } catch (IOException e) {
            throw new DatalogException(e);
        } finally {
            timer.stop();
        }
        return null;
    }

    private static Expr parseExpr(StreamTokenizer scan) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("parseExpr");
        try {
            scan.nextToken();

            boolean negated = false;
            if(scan.ttype == StreamTokenizer.TT_WORD && scan.sval.equalsIgnoreCase("not")) {
                negated = true;
                scan.nextToken();
            }

            String lhs = null;
            boolean builtInExpected = false;
            if(scan.ttype == StreamTokenizer.TT_WORD) {
                lhs = scan.sval;
            } else if(scan.ttype == '"' || scan.ttype == '\'') {
                lhs = scan.sval;
                builtInExpected = true;
            } else if(scan.ttype == StreamTokenizer.TT_NUMBER) {
                lhs = numberToString(scan.nval);
                builtInExpected = true;
            } else
                throw new DatalogException("[line " + scan.lineno() + "] Predicate or start of expression expected");

            scan.nextToken();
            if(scan.ttype == StreamTokenizer.TT_WORD || scan.ttype == '=' || scan.ttype == '!' || scan.ttype == '<' || scan.ttype == '>') {
                scan.pushBack();
                Expr e = parseBuiltInPredicate(lhs, scan);
                e.negated = negated;
                debug("Got built-in predicate: " + e);
                return e;
            }

            if(builtInExpected) {
                // LHS was a number or a quoted string but we didn't get an operator
                throw new DatalogException("[line " + scan.lineno() + "] Built-in predicate expected");
            } else if(scan.ttype != '(') {
                throw new DatalogException("[line " + scan.lineno() + "] Expected '(' after predicate or an operator");
            }

            List<String> terms = new ArrayList<>();
            if(scan.nextToken() != ')') {
                scan.pushBack();
                do {
                    if(scan.nextToken() == StreamTokenizer.TT_WORD) {
                        terms.add(scan.sval);
                    } else if(scan.ttype == '"' || scan.ttype == '\'') {
                        terms.add("\"" + scan.sval);
                    } else if(scan.ttype == StreamTokenizer.TT_NUMBER) {
                        terms.add(numberToString(scan.nval));
                    } else {
                        throw new DatalogException("[line " + scan.lineno() + "] Expected term in expression");
                    }
                } while(scan.nextToken() == ',');
                if(scan.ttype != ')') {
                    throw new DatalogException("[line " + scan.lineno() + "] Expected ')'");
                }
            }
            Expr e = new Expr(lhs, terms);
            e.negated = negated;
            return e;
        } catch (IOException e) {
            throw new DatalogException(e);
        } finally {
            timer.stop();
        }
    }

    private static final List<String> validOperators = Arrays.asList(new String[] {"=", "!=", "<>", "<", "<=", ">", ">="});

    private static Expr parseBuiltInPredicate(String lhs, StreamTokenizer scan) throws DatalogException {
        try {
            String operator;
            scan.nextToken();
            if(scan.ttype == StreamTokenizer.TT_WORD) {
                // At some point I was going to have "eq" and "ne" for string comparisons, but it wasn't a good idea.
                operator = scan.sval;
            } else {
                operator = Character.toString((char)scan.ttype);
                scan.nextToken();
                if(scan.ttype == '=' || scan.ttype == '>') {
                    operator = operator + Character.toString((char)scan.ttype);
                } else {
                    scan.pushBack();
                }
            }

            if(!validOperators.contains(operator)) {
                throw new DatalogException("Invalid operator '" + operator + "'");
            }

            String rhs = null;
            scan.nextToken();
            if(scan.ttype == StreamTokenizer.TT_WORD) {
                rhs = scan.sval;
            } else if(scan.ttype == '"' || scan.ttype == '\'') {
                rhs = scan.sval;
            } else if(scan.ttype == StreamTokenizer.TT_NUMBER) {
                rhs = numberToString(scan.nval);
            } else {
                throw new DatalogException("[line " + scan.lineno() + "] Right hand side of expression expected");
            }

            return new Expr(operator, lhs, rhs);

        } catch (IOException e) {
            throw new DatalogException(e);
        }
    }

    private static String numberToString(double nval) {
        // Remove trailing zeros; http://stackoverflow.com/a/14126736/115589
        if(nval == (long) nval)
            return String.format("%d",(long)nval);
        else
            return String.format("%s",nval);
    }

    // Regex for tryParseDouble()
    // There are several suggestions at http://stackoverflow.com/q/1102891/115589, but I chose to roll my own.
    private static final Pattern numberPattern = Pattern.compile("[+-]?\\d+(\\.\\d*)?([Ee][+-]?\\d+)?");

    static boolean tryParseDouble(String str) {
        return numberPattern.matcher(str).matches();
    }

    /* Normal Datalog exception. */
    static class DatalogException extends Exception {
        private static final long serialVersionUID = 1L;
        public DatalogException(String message) {
            super(message);
        }
        public DatalogException(Exception cause) {
            super(cause);
        }
        public DatalogException(String message, Exception cause) {
            super(message, cause);
        }
    }

    public Collection<Map<String, String>> query(String statement) throws DatalogException {
        // It would've been fun to wrap the results in a java.sql.ResultSet, but damn,
        // those are a lot of methods to implement:
        // https://docs.oracle.com/javase/8/docs/api/java/sql/ResultSet.html
        StringReader reader = new StringReader(statement);
        return execute(reader, null);
    }

    public Collection<Map<String, String>> query(Expr... goals) throws DatalogException {
        return query(Arrays.asList(goals));
    }

    public Collection<Map<String, String>> query(List<Expr> goals) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("query");
        try {
            if(goals.isEmpty())
                return Collections.emptyList();

            // Reorganize the goals so that negated literals are at the end.
            List<Expr> orderedGoals = reorderQuery(goals);

            // getRelevantRules() strips a large part of the rules, so if I want to
            // do some benchmarking of buildDatabase(), I use the IDB directly instead:
            // Collection<Rule> rules = idb;
            Collection<Rule> rules = getRelevantRules(goals);
            if(debugEnable) {
                System.out.println("To answer query, we need to evaluate: " + toString(rules));
            }

            // Build the database. A Set ensures that the facts are unique
            Collection<Expr> dataset = buildDatabase(new HashSet<>(edb), rules);
            if(debugEnable) {
                System.out.println("query(): Database = " + toString(dataset));
            }
            return matchGoals(orderedGoals, dataset, null);
        } finally {
            timer.stop();
        }
    }

    /* Returns a list of rules that are relevant to the query.
        If for example you're querying employment status, you don't care about family relationships, etc.
        The advantages of this of this optimization becomes bigger the complexer the rules get. */
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
    private Map<String, Collection<Rule>> buildDependantRules(Collection<Rule> rules) {
        Profiler.Timer timer = Profiler.getTimer("buildDependantRules");
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

    private Collection<Rule> getDependantRules(Collection<Expr> facts, Map<String, Collection<Rule>> dependants) {
        Profiler.Timer timer = Profiler.getTimer("getDependantRules");
        try {
            Set<Rule> dependantRules = new HashSet<>();
            for(Expr fact : facts) {
                Collection<Rule> rules = dependants.get(fact.predicate);
                if(rules != null) {
                    dependantRules.addAll(rules);
                }
            }
            return dependantRules;
        } finally {
            timer.stop();
        }
    }

    private Collection<Expr> buildDatabase(Set<Expr> facts, Collection<Rule> allRules) throws DatalogException  {
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

    private Collection<Expr> expandStrata(Set<Expr> facts, Collection<Rule> strataRules) throws DatalogException {
        Profiler.Timer timer = Profiler.getTimer("expandStrata");
        try {
            Collection<Rule> rules = strataRules;

            Map<String, Collection<Rule>> dependants = buildDependantRules(strataRules);

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

                    rules = getDependantRules(newFacts, dependants);

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

    private Set<Expr> matchRule(final Collection<Expr> facts, Rule rule) throws DatalogException {
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

    private Collection<Map<String, String>> matchGoals(List<Expr> goals, final Collection<Expr> facts, Map<String, String> bindings) throws DatalogException {

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
            for(Expr fact : facts) {
                if(!fact.predicate.equals(goal.predicate)) {
                    continue;
                }
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
            for(Expr fact : facts) {
                if(!fact.predicate.equals(goal.predicate)) {
                    continue;
                }
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

    public boolean delete(Expr... facts) throws DatalogException {
        return delete(Arrays.asList(facts));
    }

    /* Queries a set of goals and deletes all the facts that matches the query */
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

            for(int j = i + 1; j < edb.size(); j++) {
                Expr that = edb.get(j);
                if(fact.predicate.equals(that.predicate)) {
                    if(fact.arity() != that.arity()) {
                        // Technically we don't really require the arity of two facts to be the same if they have the same
                        // predicate, since they simply won't unify with the goals in the queries.
                        throw new DatalogException("Arity mismatch in EDB: " + fact.predicate + "/" + fact.arity()
                            + " vs " + that.predicate + "/" + that.arity());
                    }
                }
            }
        }
    }

    public void dump(PrintStream out) throws DatalogException {
        out.println("% Facts:");
        for(Expr fact : edb) {
            out.println(fact + ".");
        }
        out.println("\n% Rules:");
        for(Rule rule : idb) {
            out.println(rule + ".");
        }
    }

    static void debug(String message) {
        // I'm not happy with this. Remove later.
        if(debugEnable) {
            System.out.println(message);
        }
    }

    static String toString(Collection<?> collection) {
        StringBuilder sb = new StringBuilder("[");
        for(Object o : collection)
            sb.append(o.toString()).append(". ");
        sb.append("]");
        return sb.toString();
    }

    static String toString(Map<String, String> bindings) {
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

    // If you want to do benchmarking, run the file several times to get finer grained results.
    private static final boolean BENCHMARK = false;
    static final int NUM_RUNS = 1000;

    public static void main(String... args) {

        if(args.length > 0) {
            // Read input from a file...
            try {

                if(BENCHMARK) {
                    // Execute the program a couple of times without output
                    // to "warm up" the JVM for profiling.
                    for(int run = 0; run < 5; run++) {
                        System.out.print("" + (5 - run) + "...");
                        JDatalog jDatalog = new JDatalog();
                        for(String arg : args) {
                            try( Reader reader = new BufferedReader(new FileReader(arg)) ) {
                                jDatalog.execute(reader, null);
                            }
                        }
                    }
                    Profiler.reset();
                    System.gc();System.runFinalization();
                    System.out.println();

                    QueryOutput qo = new StandardQueryOutput();
                    for(int run = 0; run < NUM_RUNS; run++) {

                        JDatalog jDatalog = new JDatalog();

                        for(String arg : args) {
                            debug("Executing file " + arg);
                            try( Reader reader = new BufferedReader(new FileReader(arg)) ) {
                                jDatalog.execute(reader, qo);
                            }
                        }
                    }

                    System.out.println("Profile for running " + toString(Arrays.asList(args)) + "; NUM_RUNS=" + NUM_RUNS);
                    Profiler.keySet().stream().sorted().forEach(key -> {
                        double time = Profiler.average(key);
                        double total = Profiler.total(key);
                        int count = Profiler.count(key);
                        System.out.println(String.format("%-20s time: %10.4fms; total: %12.2fms; count: %d", key, time, total, count));
                    });
                } else {
                    JDatalog jDatalog = new JDatalog();
                    QueryOutput qo = new StandardQueryOutput();
                    for(String arg : args) {
                        debug("Executing file " + arg);
                        try( Reader reader = new BufferedReader(new FileReader(arg)) ) {
                            jDatalog.execute(reader, qo);
                        }
                    }
                }

            } catch (DatalogException | IOException e) {
                e.printStackTrace();
            }
        } else {
            // Get input from command line
            JDatalog jDatalog = new JDatalog();
            BufferedReader buffer = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("JDatalog: Java Datalog engine\nInteractive mode; type 'exit' to quit.");
            while(true) {
                try {
                    System.out.print("> ");
                    String line = buffer.readLine();
                    if(line == null) {
                        break; // EOF
                    }
                    line = line.trim();
                    if(line.equalsIgnoreCase("exit")) {
                        break;
                    } else if(line.equalsIgnoreCase("dump")) {
                        jDatalog.dump(System.out);
                        continue;
                    } else if(line.equalsIgnoreCase("validate")) {
                        jDatalog.validate();
                        System.out.println("OK."); // exception not thrown
                        continue;
                    }

                    Collection<Map<String, String>> answers = jDatalog.query(line);

                    // If `answers` is null, the line passed to `jDatalog.query(line)` was a statement that didn't
                    //      produce any results, like a fact or a rule, rather than a query.
                    // If `answers` is empty, then it was a query that doesn't have any answers, so the output is "No."
                    // If `answers` is a list of empty maps, then it was the type of query that only wanted a yes/no
                    //      answer, like `siblings(alice,bob)?` and the answer is "Yes."
                    // Otherwise `answers` is a list of all bindings that satisfy the query.
                    if(answers != null) {
                        if(!answers.isEmpty()){
                            if(answers.iterator().next().isEmpty()) {
                                System.out.println("Yes.");
                            } else {
                                for(Map<String, String> answer : answers) {
                                    System.out.println(JDatalog.toString(answer));
                                }
                            }
                        } else {
                            System.out.println("No.");
                        }
                    }

                } catch (DatalogException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /* //This is how you would use the fluent API:
        try {
            JDatalog jDatalog = new JDatalog();

            jDatalog.fact("parent", "a", "aa")
                .fact("parent", "a", "ab")
                .fact("parent", "aa", "aaa")
                .fact("parent", "aa", "aab")
                .fact("parent", "aaa", "aaaa")
                .fact("parent", "c", "ca");

            jDatalog.rule(new Expr("ancestor", "X", "Y"), new Expr("parent", "X", "Z"), new Expr("ancestor", "Z", "Y"))
                .rule(new Expr("ancestor", "X", "Y"), new Expr("parent", "X", "Y"))
                .rule(new Expr("sibling", "X", "Y"), new Expr("parent", "Z", "X"), new Expr("parent", "Z", "Y"), new Expr("!=", "X", "Y"))
                .rule(new Expr("related", "X", "Y"), new Expr("ancestor", "Z", "X"), new Expr("ancestor", "Z", "Y"));

            Collection<Map<String, String>> answers;

            // Run a query "who are siblings?"; print the answers
            answers = jDatalog.query(new Expr("sibling", "A", "B"));
            System.out.println("Siblings:");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

            // Run a query "who are aa's descendants?"; print the answers
            answers = jDatalog.query(new Expr("ancestor", "aa", "X"));
            System.out.println("Descendants:");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

            // This demonstrates how you would use a built-in predicate in the fluent API.
            System.out.println("Built-in predicates:");
            answers = jDatalog.query(new Expr("parent", "aa", "A"), new Expr("parent", "aa", "B"), new Expr("!=", "A", "B"));
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

            System.out.println("Before Deletion: " + toString(jDatalog.edb));
            jDatalog.delete(new Expr("parent", "aa", "X"), new Expr("parent", "X", "aaaa")); // deletes parent(aa,aaa) and parent(aaa,aaaa)
            System.out.println("After Deletion: " + toString(jDatalog.edb));

            // "who are aa's descendants now?"
            answers = jDatalog.query(new Expr("ancestor", "aa", "X"));
            System.out.println("Descendants:");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));

        } catch (DatalogException e) {
            e.printStackTrace();
        } */

        /* // The JDatalog.query(String) method runs queries directly.
        try{
            JDatalog jDatalog = new JDatalog();
            jDatalog.query("foo(bar). foo(baz).");
            Collection<Map<String, String>> answers = jDatalog.query("foo(What)?");
            answers.stream().forEach(answer -> System.out.println(" -> " + toString(answer)));
        } catch (DatalogException e) {
            e.printStackTrace();
        } */

    }
}
