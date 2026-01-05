package lox.repository;

import lox.service.Interpreter;

import java.util.List;

/**
 * LoxCallable is the interface for all callable objects in Lox.
 * 
 * This interface abstracts the concept of "something that can be called" in Lox,
 * allowing both user-defined functions and built-in native functions to be
 * treated uniformly by the interpreter.
 * 
 * Implementations include:
 * - LoxFunction: User-defined functions and methods
 * - LoxClass: Classes (constructors are callable)
 * - Native functions: Built-in functions like clock()
 * 
 * The interface defines two key operations:
 * - arity(): Returns the number of parameters the callable expects
 * - call(): Executes the callable with the given arguments
 * 
 * This design follows the Strategy pattern, where the call behavior is
 * encapsulated in the implementing class, allowing the interpreter to
 * invoke any callable object without knowing its specific type.
 */
public interface LoxCallable {
    int arity();
    Object call(Interpreter interpreter, List<Object> arguments);
}
