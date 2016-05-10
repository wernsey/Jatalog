package za.co.wstoop.jdatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import za.co.wstoop.jdatalog.JDatalog.DatalogException;

public class Expr {

    String predicate;
    List<String> terms;

    protected boolean negated = false;

    public Expr(String predicate, List<String> terms) {
        this.predicate = predicate;
        // I've seen both versions of the symbol for not equals being used, so I allow
        // both, but we convert to "<>" internally to simplify matters later.
        if(this.predicate.equals("!=")) {
            this.predicate = "<>";
        }
        this.terms = terms;
    }

    public Expr(String predicate, String... terms) {
        this(predicate, Arrays.asList(terms));
    }

    public int arity() {
        return terms.size();
    }

    public boolean isGround() {
        for(String term : terms) {
            if(JDatalog.isVariable(term))
                return false;
        }
        return true;
    }

    public boolean isNegated() {
        return negated;
    }

    public boolean isBuiltIn() {
        char op = predicate.charAt(0);
        return !Character.isLetterOrDigit(op) && op != '\"';
    }

    public static Expr not(String predicate, String... terms) {
        Expr e = new Expr(predicate, terms);
        e.negated = true;
        return e;
    }

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
                if(JDatalog.isVariable(term1))
                    throw new DatalogException("Unbound variable " + term1 + " in evaluation of " + this);
                if(JDatalog.isVariable(term2))
                    throw new DatalogException("Unbound variable " + term2 + " in evaluation of " + this);

                if(predicate.equals("<>")) {
                    // '<>' is also a bit special
                    if(JDatalog.tryParseDouble(term1) && JDatalog.tryParseDouble(term2)) {
                            double d1 = Double.parseDouble(term1);
                            double d2 = Double.parseDouble(term2);
                            return d1 != d2;
                    } else {
                        return !term1.equals(term2);
                    }
                } else {
                    // ordinary comparison operator
                    if(!JDatalog.tryParseDouble(term1) || !JDatalog.tryParseDouble(term2)) {
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