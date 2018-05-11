package za.co.wstoop.jatalog;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExprTest {

  @Test
  public void testEquals() {
    Expr e1 = new Expr("foo", "a", "b");
    assertTrue(e1.getPredicate().equals("foo"));
    assertTrue(e1.arity() == 2);
    assertFalse(e1.isNegated());

    Expr e2 = new Expr("foo", "a", "b");
    assertTrue(e1.equals(e2));

    Expr e3 = new Expr("bar", "a", "b");
    assertFalse(e1.equals(e3));
    Expr e4 = new Expr("foo", "a", "b", "c");
    assertTrue(e4.arity() == 3);
    assertFalse(e1.equals(e4));

    assertFalse(e1.equals(null));
    assertFalse(e1.equals(this));
  }

  @Test
  public void testGround() {

    assertTrue(new Term("X").isVariable());
    assertFalse(new Term("x").isVariable());
    assertTrue(new Term("Hello").isVariable());
    assertFalse(new Term("hello").isVariable());

    Expr e1 = Expr.not("foo", "a", "b");
    assertTrue(e1.isGround());

    Expr e2 = new Expr("foo", "A", "B");
    assertFalse(e2.isGround());
  }

  @Test
  public void testNegation() {
    Expr e1 = Expr.not("foo", "a", "b");
    assertTrue(e1.isNegated());

    Expr e2 = new Expr("foo", "a", "b");
    assertFalse(e1.equals(e2));
  }

  @Test
  public void testGoodUnification() {
    Map<String, Term> bindings = new HashMap<>();
    Expr e1 = new Expr("foo", "a", "b");

    Expr e2 = new Expr("foo", "a", "b");
    assertTrue(e1.unify(e2, bindings));

    bindings.put("X", new Term("b"));
    Expr e3 = new Expr("foo", "a", "X");
    assertTrue(e1.unify(e3, bindings));
    assertTrue(e3.unify(e1, bindings));

    Expr e3a = new Expr("foo", "a", "X");
    assertTrue(e3.unify(e3a, bindings));

    bindings.clear();
    Expr e4 = new Expr("foo", "Y", "X");
    assertTrue(e1.unify(e4, bindings));
    assertTrue(bindings.get("Y").equals(new Term("a")));

    bindings.clear();
    assertTrue(e4.unify(e1, bindings));
    assertTrue(bindings.get("Y").equals(new Term("a")));
    assertTrue(bindings.get("X").equals(new Term("b")));
  }

  @Test
  public void testBadUnification() {
    Map<String, Term> bindings = new HashMap<>();
    Expr e1 = new Expr("foo", "a", "b");

    Expr e2 = new Expr("foo", "a", "b", "c");
    assertFalse(e1.unify(e2, bindings));
    assertFalse(e2.unify(e1, bindings));

    Expr e3 = new Expr("bar", "a", "b");
    assertFalse(e1.unify(e3, bindings));
    assertFalse(e3.unify(e1, bindings));

    Expr e4 = new Expr("foo", "A", "b");
    assertTrue(e1.unify(e4, bindings));
    bindings.clear();
    bindings.put("A", new Term("xxxx"));
    assertFalse(e1.unify(e4, bindings));
    assertFalse(e4.unify(e1, bindings));
  }

  @Test
  public void testToString() {
    Expr e1 = new Expr("foo", "a", "b");
    assertTrue(e1.toString().equals("foo(a, b)"));

    Expr e2 = Expr.not("foo", "a", "b");
    assertTrue(e2.toString().equals("not foo(a, b)"));

    Expr e3 = new Expr("<>", "X", "Y");
    assertTrue(e3.toString().equals("X <> Y"));
  }

  @Test
  public void testIsBuiltin() {
    Expr e1 = new Expr("<>", "A", "B");
    assertTrue(e1.isBuiltIn());
    Expr e2 = new Expr("\"quoted predicate", "A", "B");
    assertFalse(e2.isBuiltIn());
  }

  @Test
  public void testSubstitute() {
    Expr e1 = new Expr("foo", "X", "Y");
    Map<String, Term> bindings = new HashMap<>();
    bindings.put("X", new Term("a"));
    Expr e2 = e1.substitute(bindings);
    assertTrue(e2.getTerms().get(0).equals(new Term("a")));
    assertTrue(e2.getTerms().get(1).equals(new Term("Y")));
    assertFalse(e2.isNegated());

    e1 = Expr.not("foo", "X", "Y");
    e2 = e1.substitute(bindings);
    assertTrue(e2.getTerms().get(0).equals(new Term("a")));
    assertTrue(e2.getTerms().get(1).equals(new Term("Y")));
    assertTrue(e2.isNegated());
  }

  @Test
  public void testQuotedStrings() {
    Expr e1 = new Expr("foo", "\"This is a quoted string");
    Map<String, Term> bindings = new HashMap<>();
    assertTrue(e1.toString().equals("foo(\"This is a quoted string\")"));
    bindings.put("X", new Term("\"This is a quoted string"));
    bindings.put("Y", new Term("random gibberish"));

    Expr e2 = new Expr("foo", "X");
    assertTrue(e1.unify(e2, bindings));

    Expr e3 = new Expr("foo", "Y");
    assertFalse(e1.unify(e3, bindings));

    bindings.clear();
    assertTrue(e1.unify(e2, bindings));
    assertTrue(bindings.get("X").equals(new Term("\"This is a quoted string")));

    bindings.clear();
    assertTrue(e2.unify(e1, bindings));
    assertTrue(bindings.get("X").equals(new Term("\"This is a quoted string")));
  }

  @Test
  public void testEvalBuiltinEq() throws Exception {

    Map<String, Term> bindings = new HashMap<>();
    Expr e1 = new Expr("=", "X", "Y");

    bindings.put("X", new Term("hello"));
    bindings.put("Y", new Term("hello"));
    assertTrue(e1.evalBuiltIn(bindings));

    bindings.clear();
    bindings.put("X", new Term("hello"));
    assertTrue(e1.evalBuiltIn(bindings));
    assertTrue(bindings.get("Y").equals(new Term("hello")));

    bindings.clear();
    bindings.put("Y", new Term("hello"));
    assertTrue(e1.evalBuiltIn(bindings));
    assertTrue(bindings.get("X").equals(new Term("hello")));

    bindings.clear();
    bindings.put("X", new Term("hello"));
    assertTrue(e1.evalBuiltIn(bindings));
    assertTrue(bindings.get("Y").equals(new Term("hello")));

    try {
      bindings.clear();
      e1.evalBuiltIn(bindings);
      assertFalse(true);
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    bindings.put("X", new Term("100"));
    bindings.put("Y", new Term("100.0000"));
    assertTrue(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("100"));
    bindings.put("Y", new Term("105"));
    assertFalse(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("100"));
    bindings.put("Y", new Term("aaa"));
    assertFalse(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("aaa"));
    bindings.put("Y", new Term("100"));
    assertFalse(e1.evalBuiltIn(bindings));

    e1 = new Expr("=", "X", "aaa");
    bindings.clear();
    bindings.put("X", new Term("aaa"));
    assertTrue(e1.evalBuiltIn(bindings));

    e1 = new Expr("=", "aaa", "Y");
    bindings.clear();
    bindings.put("Y", new Term("aaa"));
    assertTrue(e1.evalBuiltIn(bindings));
  }

  @Test
  public void testEvalBuiltinNe() throws Exception {

    Map<String, Term> bindings = new HashMap<>();
    Expr e1 = new Expr("!=", "X", "Y");
    assertTrue(e1.getPredicate().equals("<>"));

    bindings.put("X", new Term("hello"));
    bindings.put("Y", new Term("hello"));
    assertFalse(e1.evalBuiltIn(bindings));

    bindings.put("Y", new Term("olleh"));
    assertTrue(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("10"));
    bindings.put("Y", new Term("10.000"));
    assertFalse(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("10"));
    bindings.put("Y", new Term("10.0001"));
    assertTrue(e1.evalBuiltIn(bindings));

    try {
      bindings.clear();
      e1.evalBuiltIn(bindings);
      assertFalse(true);
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    try {
      bindings.clear();
      bindings.put("X", new Term("10"));
      e1.evalBuiltIn(bindings);
      assertFalse(true);
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    try {
      bindings.clear();
      bindings.put("Y", new Term("10"));
      e1.evalBuiltIn(bindings);
      assertFalse(true);
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    bindings.put("X", new Term("100"));
    bindings.put("Y", new Term("aaa"));
    assertTrue(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("aaa"));
    bindings.put("Y", new Term("100"));
    assertTrue(e1.evalBuiltIn(bindings));
  }

  @Test
  public void testEvalBuiltinOther() throws Exception {

    Map<String, Term> bindings = new HashMap<>();
    bindings.put("X", new Term("100"));
    bindings.put("Y", new Term("200"));

    Expr e1 = new Expr("=!=", "X", "Y"); // Bad operator
    try {
      e1.evalBuiltIn(bindings);
      assertTrue(false);
    } catch (RuntimeException e) {
      assertTrue(true);
    }

    e1 = new Expr(">", "X", "Y");
    assertFalse(e1.evalBuiltIn(bindings));
    e1 = new Expr(">", "X", "0");
    assertTrue(e1.evalBuiltIn(bindings));
    e1 = new Expr(">=", "X", "Y");
    assertFalse(e1.evalBuiltIn(bindings));
    e1 = new Expr(">=", "X", "0");
    assertTrue(e1.evalBuiltIn(bindings));
    e1 = new Expr(">=", "X", "100");
    assertTrue(e1.evalBuiltIn(bindings));
    e1 = new Expr("<", "X", "Y");
    assertTrue(e1.evalBuiltIn(bindings));
    e1 = new Expr("<", "X", "X");
    assertFalse(e1.evalBuiltIn(bindings));
    e1 = new Expr("<=", "X", "Y");
    assertTrue(e1.evalBuiltIn(bindings));
    e1 = new Expr("<=", "X", "X");
    assertTrue(e1.evalBuiltIn(bindings));
    e1 = new Expr("<=", "Y", "X");
    assertFalse(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("100"));
    bindings.put("Y", new Term("aaa"));
    e1 = new Expr("<", "X", "Y");
    assertFalse(e1.evalBuiltIn(bindings));

    bindings.put("X", new Term("aaa"));
    bindings.put("Y", new Term("100"));
    e1 = new Expr("<", "X", "Y");
    assertTrue(e1.evalBuiltIn(bindings));
  }
}
