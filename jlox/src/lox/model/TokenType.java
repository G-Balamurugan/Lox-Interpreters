package lox.model;

/**
 * TokenType enumeration defines all the types of tokens that can be recognized
 * by the Lox scanner/lexer. This includes keywords, operators, literals, and
 * special characters used in the Lox programming language.
 * 
 * Token categories:
 * - Single-character tokens: (, ), {, }, etc.
 * - One or two character tokens: !=, ==, <=, etc.
 * - Literals: identifiers, strings, numbers
 * - Keywords: and, class, if, while, etc.
 * - EOF: End of file marker
 */
public enum TokenType {
    // Single-character tokens
    LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE,
    COMMA, DOT, MINUS, PLUS, SEMICOLON, SLASH, STAR,

    // One or two character tokens
    BANG, BANG_EQUAL,
    EQUAL, EQUAL_EQUAL,
    GREATER, GREATER_EQUAL,
    LESS, LESS_EQUAL,

    // Literals
    IDENTIFIER, STRING, NUMBER,

    // Keywords
    AND, CLASS, ELSE, FALSE, FUN, FOR, IF, NIL, OR,
    PRINT, RETURN, SUPER, THIS, TRUE, VAR, WHILE,

    EOF
}
