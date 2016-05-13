package za.co.wstoop.jdatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
public class Expr {

    String predicate;
    List<String> terms;

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
        this.terms = terms;
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
     * It is expected that the arity of facts with the same predicate is the same, although JDatalog 
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
        for(String term : terms) {
            if(JDatalog.isVariable(term))
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
     * JDatalog supports several built-in operators: =, &lt;&gt;, &lt;, &lt;=, &gt;, &gt;=. 
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
     * Static method for constructing negated expressions in the fluent API
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
     * Unifies {@code this} expression with another expression.
     * @param that The expression to unify with
     * @param bindings The bindings of variables to values after unification
     * @return true if the expressions unify.
     */
    boolean unify(Expr that, Map<String, String> bindings) {
        if(!this.predicate.equals(that.predicate) || this.arity() != that.arity()) {
            return false;
        }
        for(int i = 0; i < this.arity(); i++) {
            String term1 = this.terms.get(i);
            String term2 = that.terms.get(i);
            if(JDatalog.isVariable(term1)) {
                if(!term1.equals(term2)) {
                    if(!bindings.containsKey(term1)) {
                        bindings.put(term1, term2);
                    } else if (!bindings.get(term1).equals(term2)) {
                        return false;
                    }
                }
            } else if(JDatalog.isVariable(term2)) {
                if(!bindings.containsKey(term2)) {
                    bindings.put(term2, term1);
                } else if (!bindings.get(term2).equals(term1)) {
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
    Expr substitute(Map<String, String> bindings) {
        // that.terms.add() below doesn't work without the new ArrayList()
        Expr that = new Expr(this.predicate, new ArrayList<>());
        that.negated = negated;
        for(String term : this.terms) {
            String value;
            if(JDatalog.isVariable(term)) {
                value = bindings.get(term);
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
     * @param bindings 
     * @return true if the operator matched.
     * @throws DatalogException for a variety of possible errors.
     */
    boolean evalBuiltIn(Map<String, String> bindings) throws DatalogException {
        //System.out.println("EVAL " + this + "; " + bindings);
        String term1 = terms.get(0);
        if(JDatalog.isVariable(term1) && bindings.containsKey(term1))
            term1 = bindings.get(term1);
        String term2 = terms.get(1);
        if(JDatalog.isVariable(term2) && bindings.containsKey(term2))
            term2 = bindings.get(term2);
        if(predicate.equals("=")) {
            // '=' is special
            if(JDatalog.isVariable(term1)) {
                if(JDatalog.isVariable(term2)) {
                    throw new DatalogException("Both operands of '=' are unbound (" + term1 + ", " + term2 + ") in evaluation of " + this);
                }
                bindings.put(term1, term2);
                return true;
            } else if(JDatalog.isVariable(term2)) {
                bindings.put(term2, term1);
                return true;
            } else {
                return term1.equals(term2);
            }
        } else {
            try {
            	
            	// These errors can be detected in the validate method:
                if(JDatalog.isVariable(term1))
                    throw new DatalogException("Unbound variable " + term1 + " in evaluation of " + this);
                if(JDatalog.isVariable(term2))
                    throw new DatalogException("Unbound variable " + term2 + " in evaluation of " + this);

                if(predicate.equals("<>")) {
                    // '<>' is also a bit special
                    if(DatalogParser.tryParseDouble(term1) && DatalogParser.tryParseDouble(term2)) {
                            double d1 = Double.parseDouble(term1);
                            double d2 = Double.parseDouble(term2);
                            return d1 != d2;
                    } else {
                        return !term1.equals(term2);
                    }
                } else {
                    // ordinary comparison operator
                    if(!DatalogParser.tryParseDouble(term1) || !DatalogParser.tryParseDouble(term2)) {
                        throw new DatalogException("Both parameters of " + predicate + " must be numeric (" + term1 + ", " + term2 + ") in evaluation of " + this);
                    }
                    double d1 = Double.parseDouble(term1);
                    double d2 = Double.parseDouble(term2);
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
        for(String term : terms) {
            hash += term.hashCode();
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
                String term = terms.get(i);
                termToString(sb, term);
                if(i < terms.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static StringBuilder termToString(StringBuilder sb, String term) {
        if(term.startsWith("\""))
            sb.append('"').append(term.substring(1).replaceAll("\"", "\\\\\"")).append('"');
        else
            sb.append(term);
        return sb;
    }

}