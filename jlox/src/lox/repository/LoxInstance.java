package lox.repository;

import lox.model.RuntimeError;
import lox.model.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * LoxInstance represents an instance of a Lox class at runtime.
 * 
 * This class stores the state (fields) for an individual object created from
 * a Lox class. Each instance maintains a reference to its class definition
 * and a map of field values.
 * 
 * Key features:
 * - Field storage: Dynamic map of field names to values
 * - Property access: get() retrieves fields or methods
 * - Property assignment: set() updates field values
 * - Method binding: Methods are bound to 'this' instance when accessed
 * 
 * Property resolution order:
 * 1. Check if it's a field on the instance
 * 2. If not, look for a method in the class
 * 3. If method found, bind it to this instance
 * 4. If neither found, throw a RuntimeError
 * 
 * Example:
 * class Person {
 *     init(name) {
 *         this.name = name;  // set("name", "Alice")
 *     }
 *     greet() {
 *         print this.name;   // get("name") returns field value
 *     }
 * }
 * 
 * var person = Person("Alice");
 * person.age = 30;        // set("age", 30) - adds new field
 * print person.name;      // get("name") - returns "Alice"
 * person.greet();         // get("greet") - returns bound method
 * 
 * Fields vs Methods:
 * - Fields are checked first (fields shadow methods with the same name)
 * - Methods are looked up from the class and bound to the instance
 * - Both use the same syntax: instance.property
 */
public class LoxInstance {
    private LoxClass klass;
    private final Map<String, Object> fields = new HashMap<>();

    public LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    public Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }

        LoxFunction method = klass.findMethod(name.lexeme);
        if (method != null) return method.bind(this);

        throw new RuntimeError(name,
                "Undefined property '" + name.lexeme + "'.");
    }

    public void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}
