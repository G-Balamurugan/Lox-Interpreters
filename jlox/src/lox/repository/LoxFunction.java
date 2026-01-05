package lox.repository;

import lox.model.Return;
import lox.model.Stmt;
import lox.service.Interpreter;

import java.util.List;

/**
 * LoxFunction represents a function definition in the Lox language.
 * 
 * This class encapsulates a user-defined function, storing its declaration
 * (parameters and body), closure environment, and whether it's an initializer.
 * Functions in Lox are first-class objects that capture their defining environment,
 * enabling closures.
 * 
 * Key features:
 * - Closure capture: Stores the environment where the function was defined
 * - Parameter binding: Creates a new environment with parameter values
 * - Return handling: Uses the Return exception for control flow
 * - Method binding: Can be bound to an instance (for methods)
 * - Initializer support: Special handling for class constructors
 * 
 * Function execution flow:
 * 1. Create a new environment with closure as parent
 * 2. Bind parameters to argument values in the new environment
 * 3. Execute the function body in this environment
 * 4. Catch Return exception to get the return value
 * 5. Return the value (or nil if no explicit return)
 * 
 * Closures work because each function carries its defining environment:
 * 
 * fun makeCounter() {
 *     var count = 0;  // In makeCounterEnv
 *     fun increment() {
 *         count = count + 1;  // Accesses count via closure
 *     }
 *     return increment;  // Returns function with makeCounterEnv as closure
 * }
 * 
 * Method binding:
 * When a method is accessed on an instance, bind() creates a new function
 * with 'this' defined in its closure, pointing to the instance.
 */
public class LoxFunction implements LoxCallable {
    private final Stmt.Function declaration;
    private final Environment closure;
    private final boolean isInitializer;

    public LoxFunction(Stmt.Function declaration, Environment closure,
                       boolean isInitializer) {
        this.isInitializer = isInitializer;
        this.closure = closure;
        this.declaration = declaration;
    }

    public LoxFunction bind(LoxInstance instance) {
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        return new LoxFunction(declaration, environment,
                isInitializer);
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }

    @Override
    public int arity() {
        return declaration.params.size();
    }

    @Override
    public Object call(Interpreter interpreter,
                       List<Object> arguments) {
        Environment environment = new Environment(closure);
        for (int i = 0; i < declaration.params.size(); i++) {
            environment.define(declaration.params.get(i).lexeme,
                    arguments.get(i));
        }

        try {
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            if (isInitializer)
                return closure.getAt(0, "this");

            return returnValue.value;
        }

        if (isInitializer) return closure.getAt(0, "this");
        return null;
    }
}
