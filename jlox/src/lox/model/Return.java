package lox.model;

/**
 * Return is an exception used to implement the 'return' statement in Lox.
 * 
 * This uses Java's exception mechanism as a control flow tool. When a return
 * statement is executed in a Lox function, it throws a Return exception that
 * carries the return value. The function call handler catches this exception
 * and extracts the value to return to the caller.
 * 
 * This approach allows return statements to unwind the call stack through
 * multiple nested statements and expressions without explicit return value
 * threading through the interpreter methods.
 * 
 * The constructor disables stack traces and suppression for performance,
 * since this is used for control flow, not actual error handling.
 */
public class Return extends RuntimeException {
    public final Object value;

    public Return(Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}
