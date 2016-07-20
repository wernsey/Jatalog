package za.co.wstoop.jatalog;

import java.io.IOException;
import java.io.StreamTokenizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import za.co.wstoop.jatalog.statement.Statement;
import za.co.wstoop.jatalog.statement.StatementFactory;

/**
 * Internal class that encapsulates the parser for the Datalog language.
 */
class Parser {
	
	/* Parses a Datalog statement.
     * A statement can be:
     * - a fact, like parent(alice, bob).
     * - a rule, like ancestor(A, B) :- ancestor(A, C), parent(C, B).
     * - a query, like ancestor(X, bob)?
     * - a delete clause, like delete parent(alice, bob).
     */
    static Statement parseStmt(StreamTokenizer scan) throws DatalogException {
    	
    	List<Expr> goals = new ArrayList<>();
        try {
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
				Rule newRule = new Rule(head, body);
				return StatementFactory.insertRule(newRule);
            } else {
                // We're dealing with a fact, or a query
                if(scan.ttype == '.') {
                    // It's a fact
                    return StatementFactory.insertFact(head);
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

                    if (scan.ttype == '?') {
						return StatementFactory.query(goals);
					} else if (scan.ttype == '~') {
						return StatementFactory.deleteFacts(goals);
					} else {
						throw new DatalogException("[line " + scan.lineno() + "] Expected '?' or '~' after query");
					}
                }
            }
        } catch (IOException e) {
            throw new DatalogException(e);
        }
    }

    /* parses an expression */
    static Expr parseExpr(StreamTokenizer scan) throws DatalogException {
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
        }
    }

    private static final List<String> validOperators = Arrays.asList(new String[] {"=", "!=", "<>", "<", "<=", ">", ">="});

    /* Parses one of the built-in predicates, eg X <> Y 
     * It is represented internally as a Expr with the operator as the predicate and the 
     * operands as its terms, eg. <>(X, Y) 
     */
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

    /* Converts a number to a string - The StreamTokenizer returns numbers as doubles by default
     * so we need to convert them back to strings to store them in the expressions */
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

    /* Checks, via regex, if a String can be parsed as a Double */
    static boolean tryParseDouble(String str) {
        return numberPattern.matcher(str).matches();
    }
}
