package za.co.wstoop.jdatalog;

/* Normal Datalog exception. */
public class DatalogException extends Exception {
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