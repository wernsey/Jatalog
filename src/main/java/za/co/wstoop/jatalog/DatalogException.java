package za.co.wstoop.jatalog;

/** 
 * Normal Datalog exception. 
 * <p>
 * It is used for a variety of reasons:
 * </p>
 * <ul>
 * <li> Trying to execute a file that doesn't exist. 
 * <li> Trying to add invalid facts or rules to the database. For example, facts are invalid if they are not ground
 * 	and rules are invalid if variables in the head don't appear in the body.
 * <li> Using built-in predicates in invalid ways, such as comparing unbound variables.
 * </ul>
 */
public class DatalogException extends Exception {
    private static final long serialVersionUID = 1L;
    /**
     * Constructor with a message
     * @param message A description of the problem
     */
    public DatalogException(String message) {
        super(message);
    }
    /**
     * Constructor with a cause
     * @param cause The exception that was thrown to cause this one
     */
    public DatalogException(Exception cause) {
        super(cause);
    }
    /**
     * Constructor with a message and a cause
     * @param message A description of the problem
     * @param cause The exception that was thrown to cause this one
     */
    public DatalogException(String message, Exception cause) {
        super(message, cause);
    }
}