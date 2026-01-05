package lox.model;

/**
 * Token represents a lexical token in the Lox language.
 * 
 * A token is the smallest meaningful unit of code, produced by the scanner
 * during the lexical analysis phase. Each token contains:
 * - type: The category of the token (keyword, operator, identifier, etc.)
 * - lexeme: The actual text from the source code
 * - literal: The runtime value for literals (numbers, strings)
 * - line: The line number where this token appears (for error reporting)
 * 
 * Example: For the code "var x = 42;", tokens include:
 * - Token(VAR, "var", null, 1)
 * - Token(IDENTIFIER, "x", null, 1)
 * - Token(EQUAL, "=", null, 1)
 * - Token(NUMBER, "42", 42.0, 1)
 */
public class Token {
    public final TokenType type;
    public final String lexeme;
    public final Object literal;
    public final int line;

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    public String toString() {
        return type + " " + lexeme + " " + literal;
    }
}
