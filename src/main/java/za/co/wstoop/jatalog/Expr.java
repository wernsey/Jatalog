package za.co.wstoop.jatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import za.co.wstoop.jatalog.engine.Indexable;

/**
 * Represents a Datalog literal expression.
 * <p>
 * An expression is a predicate followed by zero or more terms, in the form {@code pred(term1, term2, term3...)}.
 * </p><p>
 * An expression is said to be <i>ground</i> if it contains no <i>variables</i> in its terms. Variables are indicated in
 * terms starting with an upper-case letter, for example, the term A in {@code ancestor(A, bob)} is a variable while the term "bob" is
 * not.
 * </p><p>
 * The number of terms is the expression's <i>arity</i>.
 * </p>
 */
public class Expr implements Indexable<String> {

    private String predicate;
    private final List<Term> terms;

    protected boolean negated = false;

    /**
     * Standard constructor that accepts a predicate and a list of terms.
     * @param predicate The predicate of the expression.
     * @param terms The terms of the expression.
     */
    public Expr(String predicate, List<String> terms) {
        this.predicate = predicate;
        // I've seen both versions of the symbol for not equals being used, so I allow
        // both, but we convert to "<>" internally to simplify matters later.
        if(this.predicate.equals("!=")) {
            this.predicate = "<>";
        }
        this.terms = terms.stream().map(Term::new).collect(Collectors.toList());
    }

    /**
     * Constructor for the fluent API that allows a variable number of terms.
     * @param predicate The predicate of the expression.
     * @param terms The terms of the expression.
     */
    public Expr(String predicate, String... terms) {
        this(predicate, Arrays.asList(terms));
    }

    /**
     * The arity of an expression is simply the number of terms.
     * For example, an expression {@code foo(bar, baz, fred)} has an arity of 3 and is sometimes 
     * written as {@code foo/3}.
     * It is expected that the arity of facts with the same predicate is the same, although Jatalog 
     * does not enforce it (expressions with the same predicates but different arities wont unify).
     * @return the arity
     */
    public int arity() {
        return terms.size();
    }

    /**
     * An expression is said to be ground if none of its terms are variables.
     * @return true if the expression is ground
     */
    public boolean isGround() {
        for(Term term : terms) {
            if(term.isVariable())
                return false;
        }
        return true;
    }

    /**
     * Checks whether the expression is negated, eg. {@code not foo(bar, baz)}
     * @return true if the expression is negated
     */
    public boolean isNegated() {
        return negated;
    }

    /**
     * Checks whether an expression represents one of the supported built-in predicates.
     * Jatalog supports several built-in operators: =, &lt;&gt;, &lt;, &lt;=, &gt;, &gt;=. 
     * These are represented internally as expressions with the operator in the predicate and the operands
     * in the terms. Thus, a clause like {@code X > 100} is represented internally as {@code ">"(X, 100)}.
     * If the engine encounters one of these predicates it calls {@link #evalBuiltIn(Map)} rather than unifying
     * it against the goals. 
     * @return true if the expression is a built-in predicate.
     */
    public boolean isBuiltIn() {
        char op = predicate.charAt(0);
        return !Character.isLetterOrDigit(op) && op != '\"';
    }

    /**
     * Checks whether an expression represents one of the supported built-in functions.
     * @return true if the expression is a built-in function.
     */
    public boolean isFunction() {
        return predicate.toUpperCase().startsWith("FN_");
    }

    /**
     * Unifies {@code this} expression with another expression.
     * @param that The expression to unify with
     * @param bindings The bindings of variables to values after unification
     * @return true if the expressions unify.
     */
    public boolean unify(Expr that, Map<String, Term> bindings) {
        if(!this.predicate.equals(that.predicate) || this.arity() != that.arity()) {
            return false;
        }
        for(int i = 0; i < this.arity(); i++) {
            Term term1 = this.terms.get(i);
            Term term2 = that.terms.get(i);
            if(term1.isVariable()) {
                if(!term1.equals(term2)) {
                    if(!bindings.containsKey(term1.value())) {
                        bindings.put(term1.value(), term2);
                    } else if (!bindings.get(term1.value()).equals(term2)) {
                        return false;
                    }
                }
            } else if(term2.isVariable()) {
                if(!bindings.containsKey(term2.value())) {
                    bindings.put(term2.value(), term1);
                } else if (!bindings.get(term2.value()).equals(term1)) {
                    return false;
                }
            } else if (!term1.equals(term2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Substitutes the variables in this expression with bindings from a unification.
     * @param bindings The bindings to substitute.
     * @return A new expression with the variables replaced with the values in bindings.
     */
    public Expr substitute(Map<String, Term> bindings) {
        // that.terms.add() below doesn't work without the new ArrayList()
        Expr that = new Expr(this.predicate, new ArrayList<>());
        that.negated = negated;
        for(Term term : this.terms) {
            Term value;
            if(term.isVariable()) {
                value = bindings.get(term.value());
                if(value == null) {
                    value = term;
                }
            } else {
                value = term;
            }
            that.terms.add(value);
        }
        return that;
    }

    /**
     * Evaluates a built-in predicate. 
     * @param bindings A map of variable bindings 
     * @return true if the operator matched.
     */
    public boolean evalBuiltIn(Map<String, Term> bindings) {
    	// This method may throw a RuntimeException for a variety of possible reasons, but 
    	// these conditions are supposed to have been caught earlier in the chain by 
    	// methods such as Rule#validate().
    	// The RuntimeException is a requirement of using the Streams API.
    	Term term1 = terms.get(0);
        if(term1.isVariable() && bindings.containsKey(term1.value()))
            term1 = bindings.get(term1.value());
        Term term2 = terms.get(1);
        if(term2.isVariable() && bindings.containsKey(term2.value()))
            term2 = bindings.get(term2.value());
        if(predicate.equals("=")) {
            // '=' is special
            if(term1.isVariable()) {
                if(term2.isVariable()) {
                	// Rule#validate() was supposed to catch this condition
                    throw new RuntimeException("Both operands of '=' are unbound (" + term1 + ", " + term2 + ") in evaluation of " + this);
                }
                bindings.put(term1.value(), term2);
                return true;
            } else if(term2.isVariable()) {
                bindings.put(term2.value(), term1);
                return true;
            } else {
				if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
					double d1 = Double.parseDouble(term1.value());
					double d2 = Double.parseDouble(term2.value());
					return d1 == d2;
				} else {
					return term1.equals(term2);
				}
            }
        } else {
            try {
            	
            	// These errors can be detected in the validate method:
                if(term1.isVariable() || term2.isVariable()) {
                	// Rule#validate() was supposed to catch this condition
                	throw new RuntimeException("Unbound variable in evaluation of " + this);
                }
                
                if(predicate.equals("<>")) {
                    // '<>' is also a bit special
                    if(Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                            double d1 = Double.parseDouble(term1.value());
                            double d2 = Double.parseDouble(term2.value());
                            return d1 != d2;
                    } else {
                        return !term1.equals(term2);
                    }
                } else {
                    // Ordinary comparison operator
                	// If the term doesn't parse to a double it gets treated as 0.0.
                	double d1 = 0.0, d2 = 0.0;
                    if(Parser.tryParseDouble(term1.value())) {
                    	d1 = Double.parseDouble(term1.value());
                    }
                    if(Parser.tryParseDouble(term2.value())) {
                    	d2 = Double.parseDouble(term2.value());
                    }
                    switch(predicate) {
                        case "<": return d1 < d2;
                        case "<=": return d1 <= d2;
                        case ">": return d1 > d2;
                        case ">=": return d1 >= d2;
                    }
                }
            } catch (NumberFormatException e) {
                // You found a way to write a double in a way that the regex in tryParseDouble() doesn't understand.
                throw new RuntimeException("tryParseDouble() experienced a false positive!?", e);
            }
        }
        throw new RuntimeException("Unimplemented built-in predicate " + predicate);
    }

    /**
     * Evaluates a built-in predicate. Replace the variables with the values in bindings.
     * @param bindings bindings A map of variable bindings.
     * @return true if the operator matched.
     */
    public boolean evalBuiltInFunction(Map<String, Term> bindings) {
        String function = getFunction();
        if ("SAME".equals(function)) {
            return evalSame(bindings);
        }
        if ("DISTINCT".equals(function)) {
            return evalDistinct(bindings);
        }
        if ("GT".equals(function)) {
            return evalGt(bindings);
        }
        if ("LT".equals(function)) {
            return evalLt(bindings);
        }
        if ("GEQ".equals(function)) {
            return evalGeq(bindings);
        }
        if ("LEQ".equals(function)) {
            return evalLeq(bindings);
        }
        if ("PLUS".equals(function)) {
            return evalPlus(bindings);
        }
        if ("TIMES".equals(function)) {
            return evalTimes(bindings);
        }
        if ("MINUS".equals(function)) {
            return evalMinus(bindings);
        }
        if ("DIV".equals(function)) {
            return evalDiv(bindings);
        }
        if ("MOD".equals(function)) {
            return evalMod(bindings);
        }
        if ("POW".equals(function)) {
            return evalPow(bindings);
        }
        if ("EXP".equals(function)) {
            return evalExp(bindings);
        }
        if ("SQRT".equals(function)) {
            return evalSqrt(bindings);
        }
        if ("LOG".equals(function)) {
            return evalLog(bindings);
        }
        if ("CEIL".equals(function)) {
            return evalCeil(bindings);
        }
        if ("FLOOR".equals(function)) {
            return evalFloor(bindings);
        }
        if ("ROUND".equals(function)) {
            return evalRound(bindings);
        }
        if ("ABS".equals(function)) {
            return evalAbs(bindings);
        }
        throw new RuntimeException("Unimplemented built-in function " + predicate);
    }

    /**
     * SAME(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if X and Y are bound and X ≠ Y. Otherwise True. If variable X is free then,
     * X is bound to Y's value. Similarly if Y is free.
     */
    private boolean evalSame(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    return d1 == d2;
                }
                return term1.equals(term2);
            }
            if (term1.isVariable()) {
                if (bindings.containsKey(term1.value())) {
                    bindings.put(term1.value(), term2);
                    return true;
                }
                return false;
            }
            if (term2.isVariable()) {
                if (bindings.containsKey(term2.value())) {
                    bindings.put(term2.value(), term1);
                    return true;
                }
                return false;
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * DISTINCT(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return True if X ≠ Y, otherwise False.
     */
    private boolean evalDistinct(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    return d1 != d2;
                }
                return !term1.equals(term2);
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * GT(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return True if X > Y, otherwise False.
     */
    private boolean evalGt(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    return d1 > d2;
                }
                return term1.value().compareTo(term2.value()) > 0;
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * LT(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return True if X < Y, otherwise False.
     */
    private boolean evalLt(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    return d1 < d2;
                }
                return term1.value().compareTo(term2.value()) < 0;
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * GEQ(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return True if X ≥ Y, otherwise False.
     */
    private boolean evalGeq(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    return d1 >= d2;
                }
                return term1.value().compareTo(term2.value()) >= 0;
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * LEQ(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return True if X ≤ Y, otherwise False.
     */
    private boolean evalLeq(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    return d1 <= d2;
                }
                return term1.value().compareTo(term2.value()) <= 0;
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * PLUS(X, Y, Z)
     *
     * @param bindings A map of variable bindings
     * @return False if Z is bound and Z ≠ X + Y. Otherwise True. If Z is free, Z is bound to X + Y.
     */
    private boolean evalPlus(Map<String, Term> bindings) {
        if (arity() == 3) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);
            Term term3 = terms.get(2);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }
            if (term3.isVariable() && bindings.containsKey(term3.value())) {
                term3 = bindings.get(term3.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    Term term = new Term(Double.toString(d1 + d2));
                    if (term3.isVariable()) {
                        bindings.put(term3.value(), term);
                        return true;
                    }
                    return term3.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * TIMES(X, Y, Z)
     *
     * @param bindings A map of variable bindings
     * @return False if Z is bound and Z ≠ X * Y. Otherwise True. If Z is free, Z is bound to X * Y.
     */
    private boolean evalTimes(Map<String, Term> bindings) {
        if (arity() == 3) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);
            Term term3 = terms.get(2);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }
            if (term3.isVariable() && bindings.containsKey(term3.value())) {
                term3 = bindings.get(term3.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    Term term = new Term(Double.toString(d1 * d2));
                    if (term3.isVariable()) {
                        bindings.put(term3.value(), term);
                        return true;
                    }
                    return term3.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * MINUS(X, Y, Z)
     *
     * @param bindings A map of variable bindings
     * @return False if Z is bound and Z ≠ X - Y. Otherwise True. If Z is free, Z is bound to X - Y.
     */
    private boolean evalMinus(Map<String, Term> bindings) {
        if (arity() == 3) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);
            Term term3 = terms.get(2);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }
            if (term3.isVariable() && bindings.containsKey(term3.value())) {
                term3 = bindings.get(term3.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    Term term = new Term(Double.toString(d1 - d2));
                    if (term3.isVariable()) {
                        bindings.put(term3.value(), term);
                        return true;
                    }
                    return term3.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * DIV(X, Y, Z)
     *
     * @param bindings A map of variable bindings
     * @return False if Z is bound and Z ≠ X / Y. Otherwise True. If Z is free, Z is bound to X / Y.
     */
    private boolean evalDiv(Map<String, Term> bindings) {
        if (arity() == 3) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);
            Term term3 = terms.get(2);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }
            if (term3.isVariable() && bindings.containsKey(term3.value())) {
                term3 = bindings.get(term3.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    Term term = new Term(Double.toString(d1 / d2));
                    if (term3.isVariable()) {
                        bindings.put(term3.value(), term);
                        return true;
                    }
                    return term3.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * MOD(X, Y, Z)
     *
     * @param bindings A map of variable bindings
     * @return False if Z is bound and Z ≠ X mod Y. Otherwise True. If Z is free, Z is bound to
     * X mod Y.
     */
    private boolean evalMod(Map<String, Term> bindings) {
        if (arity() == 3) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);
            Term term3 = terms.get(2);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }
            if (term3.isVariable() && bindings.containsKey(term3.value())) {
                term3 = bindings.get(term3.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    Term term = new Term(Double.toString(d1 % d2));
                    if (term3.isVariable()) {
                        bindings.put(term3.value(), term);
                        return true;
                    }
                    return term3.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * POW(X, Y, Z)
     *
     * @param bindings A map of variable bindings
     * @return False if Z is bound and Z ≠ X ^ Y. Otherwise True. If Z is free, Z is bound to X ^ Y.
     */
    private boolean evalPow(Map<String, Term> bindings) {
        if (arity() == 3) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);
            Term term3 = terms.get(2);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }
            if (term3.isVariable() && bindings.containsKey(term3.value())) {
                term3 = bindings.get(term3.value());
            }

            if (!term1.isVariable() && !term2.isVariable()) {
                if (Parser.tryParseDouble(term1.value()) && Parser.tryParseDouble(term2.value())) {
                    double d1 = Double.parseDouble(term1.value());
                    double d2 = Double.parseDouble(term2.value());
                    Term term = new Term(Double.toString(Math.pow(d1, d2)));
                    if (term3.isVariable()) {
                        bindings.put(term3.value(), term);
                        return true;
                    }
                    return term3.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * EXP(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if Y is bound and Y ≠ e ^ X. Otherwise True. If Y is free, Y is bound to e ^ X.
     */
    private boolean evalExp(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable()) {
                if (Parser.tryParseDouble(term1.value())) {
                    double d = Double.parseDouble(term1.value());
                    Term term = new Term(Double.toString(Math.exp(d)));
                    if (term2.isVariable()) {
                        bindings.put(term2.value(), term);
                        return true;
                    }
                    return term2.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * SQRT(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if Y is bound and Y ≠ √X. Otherwise True. If Y is free, Y is bound to √X.
     */
    private boolean evalSqrt(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable()) {
                if (Parser.tryParseDouble(term1.value())) {
                    double d = Double.parseDouble(term1.value());
                    Term term = new Term(Double.toString(Math.sqrt(d)));
                    if (term2.isVariable()) {
                        bindings.put(term2.value(), term);
                        return true;
                    }
                    return term2.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * LOG(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if Y is bound and Y ≠ ln X. Otherwise True. If Y is free, Y is bound to ln X.
     */
    private boolean evalLog(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable()) {
                if (Parser.tryParseDouble(term1.value())) {
                    double d = Double.parseDouble(term1.value());
                    Term term = new Term(Double.toString(Math.log(d)));
                    if (term2.isVariable()) {
                        bindings.put(term2.value(), term);
                        return true;
                    }
                    return term2.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * CEIL(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if Y is bound and Y ≠ ⌈X⌉. Otherwise True. If Y is free, Y is bound to ⌈X⌉.
     */
    private boolean evalCeil(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable()) {
                if (Parser.tryParseDouble(term1.value())) {
                    double d = Double.parseDouble(term1.value());
                    Term term = new Term(Double.toString(Math.ceil(d)));
                    if (term2.isVariable()) {
                        bindings.put(term2.value(), term);
                        return true;
                    }
                    return term2.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * FLOOR(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if Y is bound and Y ≠ ⌊X⌋. Otherwise True. If Y is free, Y is bound to ⌊X⌋.
     */
    private boolean evalFloor(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable()) {
                if (Parser.tryParseDouble(term1.value())) {
                    double d = Double.parseDouble(term1.value());
                    Term term = new Term(Double.toString(Math.floor(d)));
                    if (term2.isVariable()) {
                        bindings.put(term2.value(), term);
                        return true;
                    }
                    return term2.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * ROUND(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if Y is bound and Y ≠ |X|. Otherwise True. If Y is free, Y is bound to |X|.
     */
    private boolean evalRound(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable()) {
                if (Parser.tryParseDouble(term1.value())) {
                    double d = Double.parseDouble(term1.value());
                    Term term = new Term(Double.toString(Math.round(d)));
                    if (term2.isVariable()) {
                        bindings.put(term2.value(), term);
                        return true;
                    }
                    return term2.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    /**
     * ABS(X, Y)
     *
     * @param bindings A map of variable bindings
     * @return False if Y is bound and Y ≠ abs(X). Otherwise True. If Y is free, Y is bound to
     * abs(X).
     */
    private boolean evalAbs(Map<String, Term> bindings) {
        if (arity() == 2) {

            Term term1 = terms.get(0);
            Term term2 = terms.get(1);

            if (term1.isVariable() && bindings.containsKey(term1.value())) {
                term1 = bindings.get(term1.value());
            }
            if (term2.isVariable() && bindings.containsKey(term2.value())) {
                term2 = bindings.get(term2.value());
            }

            if (!term1.isVariable()) {
                if (Parser.tryParseDouble(term1.value())) {
                    double d = Double.parseDouble(term1.value());
                    Term term = new Term(Double.toString(Math.abs(d)));
                    if (term2.isVariable()) {
                        bindings.put(term2.value(), term);
                        return true;
                    }
                    return term2.equals(term);
                }
            }
        }
        throw new RuntimeException("Function evaluation failed.");
    }

    private String getFunction() {
        return isFunction() ? getPredicate().substring(3).toUpperCase() : getPredicate();
    }

    public String getPredicate() {
		return predicate;
	}
    
    public List<Term> getTerms() {
		return terms;
	}

    @Override
    public boolean equals(Object other) {
        if(other == null || !(other instanceof Expr)) {
            return false;
        }
        Expr that = ((Expr) other);
        if(!this.predicate.equals(that.predicate)) {
            return false;
        }
        if(arity() != that.arity() || negated != that.negated) {
            return false;
        }
        for(int i = 0; i < terms.size(); i++) {
            if(!terms.get(i).equals(that.terms.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = predicate.hashCode();
        for(Term term : terms) {
            hash = 31 * hash + term.hashCode();
        }
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if(isNegated()) {
            sb.append("not ");
        }
        if(isBuiltIn()) {
            termToString(sb, terms.get(0));
            sb.append(" ").append(predicate).append(" ");
            termToString(sb, terms.get(1));
        } else {
            sb.append(predicate).append('(');
            for(int i = 0; i < terms.size(); i++) {
                Term term = terms.get(i);
                termToString(sb, term);
                if(i < terms.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(')');
        }
        return sb.toString();
    }

    /* Converts a term to a string. If it started as a quoted string it is now enclosed in quotes,
     * and other quotes escaped.
     * caveat: You're going to have trouble if you have other special characters in your strings */
    private static StringBuilder termToString(StringBuilder sb, Term term) {
        if(term.value().startsWith("\""))
            sb.append('"').append(term.value().substring(1).replaceAll("\"", "\\\\\"")).append('"');
        else
            sb.append(term.value());
        return sb;
    }

	/**
	 * Helper method for creating a new expression.
	 * This method is part of the fluent API intended for {@code import static}
	 * @param predicate The predicate of the expression.
	 * @param terms The terms of the expression.
	 * @return the new expression
	 */
	public static Expr expr(String predicate, String... terms) {
		return new Expr(predicate, terms);
	}

    /**
     * Static method for constructing negated expressions in the fluent API.
     * Negated expressions are of the form {@code not predicate(term1, term2,...)}.
     * @param predicate The predicate of the expression
     * @param terms The terms of the expression
     * @return The negated expression
     */
    public static Expr not(String predicate, String... terms) {
        Expr e = new Expr(predicate, terms);
        e.negated = true;
        return e;
    }
    
    /**
     * Static helper method for constructing an expression {@code a = b} in the fluent API.
     * @param a the left hand side of the operator
     * @param b the right hand side of the operator
     * @return the expression
     */
    public static Expr eq(String a, String b) {
        return new Expr("=", a, b);
    }
    
    /**
     * Static helper method for constructing an expression {@code a <> b} in the fluent API.
     * @param a the left hand side of the operator
     * @param b the right hand side of the operator
     * @return the expression
     */
    public static Expr ne(String a, String b) {
        return new Expr("<>", a, b);
    }
    
    /**
     * Static helper method for constructing an expression {@code a < b} in the fluent API.
     * @param a the left hand side of the operator
     * @param b the right hand side of the operator
     * @return the expression
     */
    public static Expr lt(String a, String b) {
        return new Expr("<", a, b);
    }
    
    /**
     * Static helper method for constructing an expression {@code a <= b} in the fluent API.
     * @param a the left hand side of the operator
     * @param b the right hand side of the operator
     * @return the expression
     */
    public static Expr le(String a, String b) {
        return new Expr("<=", a, b);
    }
    
    /**
     * Static helper method for constructing an expression {@code a > b} in the fluent API.
     * @param a the left hand side of the operator
     * @param b the right hand side of the operator
     * @return the expression
     */
    public static Expr gt(String a, String b) {
        return new Expr(">", a, b);
    }
    
    /**
     * Static helper method for constructing an expression {@code a >= b} in the fluent API.
     * @param a the left hand side of the operator
     * @param b the right hand side of the operator
     * @return the expression
     */
    public static Expr ge(String a, String b) {
        return new Expr(">=", a, b);
    }

	@Override
	public String index() {		
		return predicate;
	}

	/**
	 * Validates a fact in the IDB.
	 * Valid facts must be ground and cannot be negative.
	 * @throws DatalogException if the fact is invalid.
	 */
	public void validFact() throws DatalogException {
		if(!isGround()) {
            throw new DatalogException("Fact " + this + " is not ground");
        } else if(isNegated()) {
            throw new DatalogException("Fact " + this + " is negated");
        }
	}
}