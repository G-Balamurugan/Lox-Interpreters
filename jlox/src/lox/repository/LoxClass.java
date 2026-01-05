package lox.repository;

import lox.service.Interpreter;

import java.util.List;
import java.util.Map;

/**
 * LoxClass represents a class definition in the Lox language.
 * 
 * This class stores the blueprint for creating instances of a Lox class,
 * including its name, methods, and optional superclass. Classes in Lox
 * are first-class objects that can be assigned to variables, passed as
 * arguments, and called as constructors.
 * 
 * Key features:
 * - Class hierarchy: Supports single inheritance via superclass
 * - Method storage: Maintains a map of method names to LoxFunction objects
 * - Method resolution: Searches for methods in the class and superclass chain
 * - Callable: Implements LoxCallable to act as a constructor
 * 
 * When a class is called (e.g., `var obj = MyClass();`), it:
 * 1. Creates a new LoxInstance
 * 2. Looks for an 'init' method (constructor)
 * 3. Calls init() if it exists
 * 4. Returns the new instance
 * 
 * Method resolution follows the inheritance chain:
 * CurrentClass → Superclass → Superclass's Superclass → ... → null
 * 
 * Example:
 * class Animal {
 *     init(name) { this.name = name; }
 *     speak() { print "..."; }
 * }
 * class Dog < Animal {
 *     speak() { print "Woof!"; }  // Overrides Animal.speak
 * }
 */
public class LoxClass implements LoxCallable {
    final String name;
    final LoxClass superclass;
    private final Map<String, LoxFunction> methods;

    public LoxClass(String name, LoxClass superclass,
                    Map<String, LoxFunction> methods) {
        this.superclass = superclass;
        this.name = name;
        this.methods = methods;
    }

    public LoxFunction findMethod(String name) {
        if (methods.containsKey(name)) {
            return methods.get(name);
        }

        if (superclass != null) {
            return superclass.findMethod(name);
        }

        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public Object call(Interpreter interpreter,
                       List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }

        return instance;
    }

    @Override
    public int arity() {
        LoxFunction initializer = findMethod("init");
        if (initializer == null) return 0;
        return initializer.arity();
    }
}
