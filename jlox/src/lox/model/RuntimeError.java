package lox.model;

/**
 * RuntimeError represents errors that occur during program execution.
 * 
 * Unlike syntax errors which are caught during parsing, runtime errors occur
 * when the interpreter attempts to execute code that has a semantic problem:
 * - Undefined variables
 * - Type mismatches (e.g., adding a string and a number)
 * - Calling non-functions
 * - Accessing properties on non-instances
 * 
 * Each RuntimeError stores the token where the error occurred, which allows
 * the interpreter to provide helpful error messages with line numbers.
 */
public class RuntimeError extends RuntimeException {
    public final Token token;

    public RuntimeError(Token token, String message) {
        super(message);
        this.token = token;
    }
}
