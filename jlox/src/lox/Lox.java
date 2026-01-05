package lox;

import lox.model.*;
import lox.service.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * Lox is the main entry point for the Lox interpreter.
 * 
 * This class orchestrates the entire interpretation pipeline:
 * Source Code → Scanner → Parser → Resolver → Interpreter
 * 
 * The interpreter can run in two modes:
 * 1. Script mode: Execute a .lox file
 *    Usage: java lox.Lox script.lox
 * 
 * 2. REPL mode: Interactive prompt for executing Lox code line by line
 *    Usage: java lox.Lox
 * 
 * Pipeline stages:
 * 1. Scanner: Converts source code into tokens (lexical analysis)
 * 2. Parser: Builds an Abstract Syntax Tree from tokens (syntactic analysis)
 * 3. Resolver: Performs variable resolution and semantic analysis
 * 4. Interpreter: Executes the program by traversing the AST
 * 
 * Error handling:
 * - Syntax errors: Reported during scanning/parsing, program doesn't run
 * - Resolution errors: Reported during semantic analysis, program doesn't run
 * - Runtime errors: Reported during execution, program terminates
 * - Exit codes: 64 (usage), 65 (syntax error), 70 (runtime error)
 * 
 * The interpreter maintains a single Interpreter instance across all executions,
 * which means:
 * - Global variables persist in REPL mode
 * - Variable resolution data accumulates
 * - Native functions remain available
 * 
 * Error recovery:
 * - REPL mode: Errors are reported but the session continues
 * - Script mode: Any error terminates execution with appropriate exit code
 */
public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            runFile(args[0]);
        } else {
            runPrompt();
        }
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));

        // Indicate an error in the exit code.
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);
        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            hadError = false;
        }
    }

    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        // Stop if there was a resolution error.
        if (hadError) return;

        interpreter.interpret(statements);
    }

    public static void error(int line, String message) {
        report(line, "", message);
    }

    public static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
                "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

    private static void report(int line, String where,
                               String message) {
        System.err.println(
                "[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    public static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }
}
