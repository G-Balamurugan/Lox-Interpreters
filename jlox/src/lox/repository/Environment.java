package lox.repository;

import lox.model.RuntimeError;
import lox.model.Token;

import java.util.HashMap;
import java.util.Map;

/**
 * Environment manages variable storage and scope chains in the Lox interpreter.
 * 
 * This class implements a linked-list structure of scopes, where each environment
 * has a reference to its enclosing (parent) environment. This creates a scope chain
 * that supports lexical scoping:
 * 
 * Global Scope → Function Scope → Block Scope → Inner Block Scope
 * 
 * Key features:
 * - Variable storage: Maps variable names to their values
 * - Scope chaining: Each environment links to its parent via 'enclosing'
 * - Variable resolution: Searches current scope, then walks up the chain
 * - Distance-based access: Fast direct access using pre-computed distances
 * 
 * The Resolver pre-calculates how many environments away each variable is,
 * allowing the interpreter to use ancestor() and getAt() for O(1) access
 * instead of O(n) recursive lookups.
 * 
 * Example scope chain:
 * var global = "top";
 * {
 *     var local = "block";  // New environment with enclosing = global env
 *     fun test() {
 *         var func = "fn";  // New environment with enclosing = block env
 *     }
 * }
 */
public class Environment {
    public final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();

    public Environment() {
        enclosing = null;
    }
    public Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    public Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }
        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    public void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name,
                "Undefined variable '" + name.lexeme + "'.");
    }

    public void define(String name, Object value) {
        values.put(name, value);
    }

    public Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }
        return environment;
    }

    public Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    public void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }
}
