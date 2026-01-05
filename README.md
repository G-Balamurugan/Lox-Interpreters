# Lox Language Interpreter

A programming language interpreter project featuring two complete implementations of the **Lox scripting language** — a tree-walk interpreter in Java and a high-performance bytecode virtual machine in C.

---

## Overview

This project explores two fundamentally different approaches to language implementation:

| Implementation | Language | Approach | Key Features |
|----------------|----------|----------|--------------|
| **jlox** | Java | Tree-walk interpreter | AST-based execution, visitor pattern, environment chains |
| **clox** | C | Bytecode VM | Stack-based VM, Pratt parsing, mark-sweep GC, NaN boxing |

---

## Language Features

The Lox language supports:

- **Dynamic Typing** — Numbers, strings, booleans, nil
- **Expressions** — Arithmetic, comparison, logical operators
- **Variables** — Lexically scoped with block scope support
- **Control Flow** — `if`/`else`, `while`, `for` loops
- **Functions** — First-class functions with closures
- **Classes** — Object-oriented programming with single inheritance
- **Methods** — Instance methods with `this` binding
- **Inheritance** — `super` keyword for parent method access

---

## Project Structure

```
Interpreter/
│
├── jlox/                          # Java Tree-Walk Interpreter
│   ├── src/lox/
│   │   ├── Lox.java               # Entry point & REPL
│   │   ├── model/
│   │   │   ├── Expr.java          # Expression AST nodes
│   │   │   ├── Stmt.java          # Statement AST nodes
│   │   │   ├── Token.java         # Token representation
│   │   │   └── TokenType.java     # Token types
│   │   ├── repository/
│   │   │   ├── Environment.java   # Variable scoping
│   │   │   ├── LoxClass.java      # Class representation
│   │   │   ├── LoxFunction.java   # Function representation
│   │   │   └── LoxInstance.java   # Object instances
│   │   └── service/
│   │       ├── Scanner.java       # Lexical analysis
│   │       ├── Parser.java        # Syntax analysis
│   │       ├── Resolver.java      # Semantic analysis
│   │       └── Interpreter.java   # Execution
│   ├── build/                     # Compiled classes
│   └── test/                      # Test programs
│       ├── test.lox
│       ├── simple_class_test.lox
│       ├── resolver_test.lox
│       └── class_test.lox
│
└── clox/                          # C Bytecode Virtual Machine
    ├── src/lox/
    │   ├── main.c                 # Entry point
    │   ├── scanner.c              # Tokenizer
    │   ├── compiler.c             # Parser & code generator
    │   ├── vm.c                   # Virtual machine
    │   ├── chunk.c                # Bytecode chunks
    │   ├── value.c                # Value representation
    │   ├── object.c               # Heap objects
    │   ├── table.c                # Hash tables
    │   ├── memory.c               # Memory & garbage collection
    │   ├── debug.c                # Debugging utilities
    │   └── include/               # Header files
    └── test/                      # Test programs
        ├── test_complex.lox
        └── test_hashtable.lox
```

---

## Setup & Installation

### Prerequisites

- **jlox**: Java JDK 8 or higher
- **clox**: C compiler (gcc or clang)

---

## jlox — Java Interpreter

### Build

```bash
cd jlox
javac -d build src/lox/*.java src/lox/model/*.java src/lox/repository/*.java src/lox/service/*.java
```

### Run

**Interactive REPL:**
```bash
java -cp build lox.Lox
```

**Execute a script:**
```bash
java -cp build lox.Lox test/test.lox
```

### Test Files

```bash
java -cp build lox.Lox test/test.lox              # Functions, loops, recursion
java -cp build lox.Lox test/simple_class_test.lox # Classes and inheritance
java -cp build lox.Lox test/resolver_test.lox     # Closures and scoping
java -cp build lox.Lox test/class_test.lox        # OOP banking example
```

---

## clox — C Virtual Machine

### Build

```bash
cd clox/src/lox
gcc -O2 -o clox *.c
```

### Run

**Interactive REPL:**
```bash
./clox
```

**Execute a script:**
```bash
./clox ../../test/test_hashtable.lox
```

### Test Files

```bash
./clox ../../test/test_complex.lox      # Basic expressions
./clox ../../test/test_hashtable.lox    # Variables and scoping
```

### Debug Options

Enable in `include/common.h`:
```c
#define DEBUG_PRINT_CODE        // Show compiled bytecode
#define DEBUG_TRACE_EXECUTION   // Trace VM execution
#define DEBUG_LOG_GC            // Log garbage collection
```

---

## Example Programs

### Hello World
```lox
print "Hello, World!";
```

### Closures
```lox
fun counter() {
    var count = 0;
    fun increment() {
        count = count + 1;
        return count;
    }
    return increment;
}

var c = counter();
print c();  // 1
print c();  // 2
```

### Classes
```lox
class Animal {
    init(name) {
        this.name = name;
    }
    speak() {
        print this.name + " makes a sound.";
    }
}

class Dog < Animal {
    speak() {
        print this.name + " barks!";
    }
}

var dog = Dog("Rex");
dog.speak();  // Rex barks!
```

---

## Implementation Comparison

| Aspect | jlox | clox |
|--------|------|------|
| Execution | Traverses AST directly | Compiles to bytecode |
| Memory | JVM managed | Custom mark-sweep GC |
| Performance | Baseline | ~10-100x faster |
| Complexity | Easier to follow | More optimization details |

---

## References

- Language design inspired by [Crafting Interpreters](https://craftinginterpreters.com/) by Robert Nystrom
- See individual README files in `jlox/` and `clox/` for implementation details

---
