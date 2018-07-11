package za.co.wstoop.jatalog;

/**
 * A term object is either a value or a variable.
 */
public class Term {

  private final String term;
  private final boolean isVariable;

  public Term(String term) {
    this(term, Character.isUpperCase(term.charAt(0)));
  }

  public Term(String term, boolean isVariable) {
    int firstPos = term.indexOf("\"");
    int lastPos = term.lastIndexOf("\"");
    if (firstPos >= 0 && lastPos >= 0 && firstPos < lastPos) {
      this.term = term.substring(firstPos + 1, lastPos);
    } else {
      this.term = term;
    }
    this.isVariable = isVariable;
  }

  @Override
  public boolean equals(Object o) {
    return o != null && (o == this || o instanceof Term && term.equals(((Term) o).term)
        && isVariable == ((Term) o).isVariable);
  }

  @Override
  public int hashCode() {
    int result = 0;
    result = 31 * result + term.hashCode();
    result = 31 * result + Boolean.hashCode(isVariable);
    return result;
  }

  @Override
  public String toString() {
    return term;
  }

  /**
   * Checks whether a term represents a variable. Variables start with upper-case characters.
   *
   * @return true if the term is a variable.
   */
  public boolean isVariable() {
    return isVariable;
  }

  /**
   * Term's value.
   *
   * @return a string or a variable name.
   */
  public String value() {
    return term;
  }
}
