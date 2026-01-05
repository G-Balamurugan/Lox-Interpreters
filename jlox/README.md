# Lox Interpreter

A tree-walk interpreter for the Lox programming language, implemented in Java. This project is a complete implementation of an interpreter featuring lexical analysis, parsing, semantic analysis, and execution with support for variables, functions, classes, and inheritance.

## 🌟 Features

- **Complete Lox Language Support**: Variables, functions, classes, inheritance
- **REPL Mode**: Interactive prompt for testing code snippets
- **Script Mode**: Execute `.lox` files
- **Error Reporting**: Clear error messages with line numbers
- **Static Analysis**: Pre-execution variable resolution and semantic checks
- **Object-Oriented**: Full OOP support with classes, methods, and inheritance
- **Closures**: First-class functions with lexical scoping
- **Native Functions**: Built-in functions like `clock()`

## 📋 Table of Contents

- [Architecture](#architecture)
- [Installation](#installation)
- [Usage](#usage)
- [Language Features](#language-features)
- [Examples](#examples)
- [Project Structure](#project-structure)
- [Development](#development)

## 🏗️ Architecture

The interpreter uses a **multi-phase pipeline** architecture:

```
Source Code → Scanner → Tokens → Parser → AST → Resolver → Interpreter → Execution
```

### Pipeline Stages

1. **Scanner** (`Scanner.java`)
   - Lexical analysis: Converts source code into tokens
   - Recognizes keywords, identifiers, literals, operators

2. **Parser** (`Parser.java`)
   - Syntactic analysis: Builds Abstract Syntax Tree (AST)
   - Implements recursive descent parsing
   - Handles operator precedence through grammar structure

3. **Resolver** (`Resolver.java`)
   - Static semantic analysis
   - Variable resolution with distance calculation
   - Error detection (undefined variables, invalid returns, etc.)

4. **Interpreter** (`Interpreter.java`)
   - AST traversal and execution
   - Variable storage with Environment chains
   - Function calls and class instantiation

### Project Layers

```
src/
├── lox/
│   ├── Lox.java                    # Main entry point
│   ├── model/                      # Data models (AST nodes, tokens)
│   │   ├── Expr.java              # Expression AST nodes
│   │   ├── Stmt.java              # Statement AST nodes
│   │   ├── Token.java             # Token representation
│   │   ├── TokenType.java         # Token types enum
│   │   ├── RuntimeError.java      # Runtime error exception
│   │   └── Return.java            # Return control flow exception
│   ├── repository/                 # Data storage & management
│   │   ├── Environment.java       # Variable storage and scoping
│   │   ├── LoxCallable.java       # Callable interface
│   │   ├── LoxClass.java          # Class representation
│   │   ├── LoxFunction.java       # Function representation
│   │   └── LoxInstance.java       # Object instance
│   └── service/                    # Business logic
│       ├── Scanner.java           # Lexical analyzer
│       ├── Parser.java            # Syntax analyzer
│       ├── Resolver.java          # Semantic analyzer
│       └── Interpreter.java       # Executor
```

## 🚀 Installation

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- IntelliJ IDEA (or any Java IDE) OR command line

### Setup

```bash
# Clone the repository
git clone https://github.com/yourusername/JavaInterpreter.git
cd JavaInterpreter
```

## 💻 Usage

### Option 1: Using IntelliJ IDEA (Recommended)

#### REPL Mode (Interactive):
1. Open the project in IntelliJ IDEA
2. Navigate to `src/lox/Lox.java`
3. Right-click on `Lox.java` → **Run 'Lox.main()'**
4. The REPL will start in the Run console

#### Script Mode (Run a .lox file):
1. Right-click on `Lox.java` → **Modify Run Configuration...**
2. In **Program arguments**, enter: `jlox/test/test.lox` (or path to any `.lox` file)
3. Click **OK**
4. Click **Run** button

**To test different files:**
- Change the Program arguments to different test files:
  - `jlox/test/test.lox` - Grade calculator example
  - `jlox/test/class_example.lox` - Banking OOP example
  - `jlox/test/resolver_test.lox` - Scope resolution examples
  - `jlox/test/class_test.lox` - Comprehensive class tests

### Option 2: Using Terminal/Command Line

#### Compile the project:
```bash
javac -d build src/lox/*.java src/lox/model/*.java src/lox/repository/*.java src/lox/service/*.java
```

#### REPL Mode:
```bash
java -cp build lox.Lox
```

Example REPL session:
```
> var name = "Alice";
> print "Hello, " + name;
Hello, Alice
> fun greet() { print "Hi!"; }
> greet();
Hi!
> exit
```

#### Script Mode:
```bash
java -cp build lox.Lox test/test.lox
```

## 📚 Language Features

### Variables

```lox
var a = 10;
var b = "Hello";
var c = true;
var d = nil;
```

### Operators

```lox
// Arithmetic
var sum = 10 + 20;
var product = 5 * 6;

// Comparison
var greater = 10 > 5;
var equal = "a" == "a";

// Logical
var and = true and false;
var or = true or false;
var not = !true;
```

### Control Flow

```lox
// If-else
if (condition) {
    print "true branch";
} else {
    print "false branch";
}

// While loop
while (x < 10) {
    print x;
    x = x + 1;
}

// For loop
for (var i = 0; i < 5; i = i + 1) {
    print i;
}
```

### Functions

```lox
fun greet(name) {
    print "Hello, " + name + "!";
}

greet("World");

// Functions are first-class
fun makeAdder(n) {
    fun add(x) {
        return x + n;
    }
    return add;
}

var addFive = makeAdder(5);
print addFive(10);  // 15
```

### Closures

```lox
fun makeCounter() {
    var count = 0;
    fun increment() {
        count = count + 1;
        return count;
    }
    return increment;
}

var counter = makeCounter();
print counter();  // 1
print counter();  // 2
print counter();  // 3
```

### Classes

```lox
class Person {
    init(name, age) {
        this.name = name;
        this.age = age;
    }
    
    greet() {
        print "Hello, I'm " + this.name;
    }
}

var person = Person("Alice", 30);
person.greet();  // Hello, I'm Alice
```

### Inheritance

```lox
class Animal {
    init(name) {
        this.name = name;
    }
    
    speak() {
        print this.name + " makes a sound";
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

### Super Keyword

```lox
class Shape {
    init(name) {
        this.name = name;
    }
    
    describe() {
        print "This is a " + this.name;
    }
}

class Circle < Shape {
    init(radius) {
        super.init("circle");
        this.radius = radius;
    }
    
    describe() {
        super.describe();
        print "Radius: " + this.radius;
    }
}

var circle = Circle(5);
circle.describe();
// This is a circle
// Radius: 5
```

## 📖 Examples

The `test/` directory contains several example programs:

### 1. Grade Calculator (`test/test.lox`)
Demonstrates:
- Variables and arithmetic operations
- Functions with parameters and return values
- Nested conditionals for grade assignment
- For and while loops
- Recursion (Fibonacci calculation)

### 2. Simple Class Example (`test/simple_class_test.lox`)
Demonstrates:
- Class definitions with init methods
- Instance fields and methods
- Basic object-oriented programming patterns
- Object state management

### 3. Scope Resolution (`test/resolver_test.lox`)
Demonstrates:
- Nested scopes and variable shadowing
- Closure creation and capture
- Deep nesting with multiple scope levels
- Variable resolution at different distances

### 4. Comprehensive Class Tests (`test/class_test.lox`)
Demonstrates:
- Multiple inheritance levels
- Method chaining
- Instance field access
- Complex OOP patterns

## 🗂️ Project Structure

```
JavaInterpreter/
├── src/
│   ├── lox/
│   │   ├── Lox.java                   # Main entry point and REPL
│   │   ├── model/                     # Data models
│   │   │   ├── Expr.java              # Expression AST nodes
│   │   │   ├── Stmt.java              # Statement AST nodes
│   │   │   ├── Token.java             # Token representation
│   │   │   ├── TokenType.java         # Token types
│   │   │   ├── RuntimeError.java      # Runtime exceptions
│   │   │   └── Return.java            # Return control flow
│   │   ├── repository/                # Runtime data management
│   │   │   ├── Environment.java       # Variable storage
│   │   │   ├── LoxCallable.java       # Callable interface
│   │   │   ├── LoxClass.java          # Class representation
│   │   │   ├── LoxFunction.java       # Function representation
│   │   │   └── LoxInstance.java       # Object instances
│   │   └── service/                   # Core interpreter services
│   │       ├── Scanner.java           # Lexical analyzer
│   │       ├── Parser.java            # Syntax analyzer
│   │       ├── Resolver.java          # Semantic analyzer
│   │       └── Interpreter.java       # Executor
│   └── tool/
│       └── GenerateAst.java           # AST generator utility
├── test/
│   ├── test.lox                       # Grade calculator example
│   ├── simple_class_test.lox          # Simple OOP example
│   ├── resolver_test.lox              # Scope resolution examples
│   └── class_test.lox                 # Comprehensive class tests
├── build/                             # Compiled .class files (generated)
└── README.md                          # This file
```

## 🛠️ Development

### Key Design Patterns

1. **Visitor Pattern**
   - Used for AST traversal (Expr.Visitor, Stmt.Visitor)
   - Separates operations from data structures
   - Enables multiple passes (parsing, resolution, interpretation)

2. **Strategy Pattern**
   - LoxCallable interface for different callable types
   - Uniform handling of functions, classes, and native functions

3. **Chain of Responsibility**
   - Environment chaining for variable resolution
   - Lexical scoping through linked environments

### Variable Resolution

The Resolver pre-calculates variable distances for O(1) lookup:

```
var global = "g";
{
    var local = "l";
    {
        print global;  // Distance: not in locals (global)
        print local;   // Distance: 1 (one scope away)
    }
}
```

Distance is stored in the Interpreter's `locals` map, enabling fast access via `environment.getAt(distance, name)`.

### Error Handling

The interpreter has three types of errors:

1. **Lexical/Syntax Errors** (Scanner/Parser)
   - Reported with line numbers
   - Program doesn't execute
   - Exit code: 65

2. **Semantic Errors** (Resolver)
   - Invalid variable usage
   - Scope violations
   - Program doesn't execute
   - Exit code: 65

3. **Runtime Errors** (Interpreter)
   - Type mismatches
   - Undefined variables
   - Invalid operations
   - Program terminates
   - Exit code: 70

## Acknowledgments

- **Robert Nystrom** - Original Lox language design and "Crafting Interpreters" book
- The book is available at: https://craftinginterpreters.com/

---

**Happy Coding! 🎉**
