# clox - A Bytecode Virtual Machine Interpreter for Lox

A high-performance bytecode interpreter implementation for the Lox programming language, written in C. This implementation compiles Lox source code to bytecode and executes it on a stack-based virtual machine with automatic memory management through a mark-and-sweep garbage collector.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Execution Pipeline](#execution-pipeline)
3. [Lexical Analysis (Scanner)](#lexical-analysis-scanner)
4. [Pratt Parsing](#pratt-parsing)
5. [Bytecode Representation](#bytecode-representation)
6. [Value Representation and NaN Boxing](#value-representation-and-nan-boxing)
7. [Virtual Machine](#virtual-machine)
8. [Memory Management](#memory-management)
9. [Garbage Collection](#garbage-collection)
10. [Object System](#object-system)
11. [Hash Tables](#hash-tables)
12. [String Interning](#string-interning)
13. [Closures and Upvalues](#closures-and-upvalues)
14. [Classes and Inheritance](#classes-and-inheritance)
15. [Building and Running](#building-and-running)

---

## Architecture Overview

The clox interpreter follows a classic compilation-execution pipeline:

```
Source Code → Scanner → Parser/Compiler → Bytecode → Virtual Machine → Output
```

### Key Components

| Component | File | Responsibility |
|-----------|------|----------------|
| Entry Point | `main.c` | REPL and file execution modes |
| Scanner | `scanner.c` | Lexical analysis (tokenization) |
| Compiler | `compiler.c` | Parsing and bytecode generation |
| VM | `vm.c` | Bytecode interpretation and execution |
| Memory | `memory.c` | Dynamic allocation and garbage collection |
| Objects | `object.c` | Heap-allocated object management |
| Chunks | `chunk.c` | Bytecode storage containers |
| Values | `value.c` | Runtime value representation |
| Tables | `table.c` | Hash table implementation |
| Debug | `debug.c` | Disassembly and debugging utilities |

---

## Execution Pipeline

### Entry Point (`main.c`)

The interpreter supports two execution modes:

1. **REPL Mode**: Interactive read-eval-print loop for executing code line by line
2. **File Mode**: Execute a complete `.lox` source file

```c
int main(int argc, const char* argv[]) {
    initVM();           // Initialize the virtual machine
    
    if (argc == 1) {
        repl();         // Interactive mode
    } else if (argc == 2) {
        runFile(argv[1]); // File execution mode
    }
    
    freeVM();           // Clean up resources
    return 0;
}
```

### File Reading Strategy

The file reader uses a two-pass approach to determine file size before allocation:

```c
fseek(file, 0L, SEEK_END);    // Move to end of file
size_t fileSize = ftell(file); // Get current position (= file size)
rewind(file);                   // Reset to beginning
char* buffer = malloc(fileSize + 1); // Allocate exact size needed
```

---

## Lexical Analysis (Scanner)

The scanner (`scanner.c`) transforms source code into a stream of tokens. It operates as a single-pass scanner that produces tokens on demand.

### Scanner State

```c
typedef struct {
    const char* start;    // Beginning of current lexeme
    const char* current;  // Current character being examined
    int line;             // Current line number for error reporting
} Scanner;
```

### How the Scanner Works

The scanner maintains two pointers into the source string:

```
Source: "var x = 42;"
         ^   ^
         |   |
       start current

After scanning "var":
        "var x = 42;"
         ^  ^
         |  |
       start current
       
Token created: { type: TOKEN_VAR, start: "var", length: 3 }
```

### Token Structure

```c
typedef struct {
    TokenType type;      // What kind of token (NUMBER, STRING, IDENTIFIER, etc.)
    const char* start;   // Pointer to first character in source
    int length;          // Number of characters
    int line;            // Source line for error reporting
} Token;
```

### Token Types

The scanner recognizes:

- **Single-character tokens**: `(`, `)`, `{`, `}`, `,`, `.`, `-`, `+`, `;`, `/`, `*`
- **One or two character tokens**: `!`, `!=`, `=`, `==`, `<`, `<=`, `>`, `>=`
- **Literals**: Strings, numbers, identifiers
- **Keywords**: `and`, `class`, `else`, `false`, `for`, `fun`, `if`, `nil`, `or`, `print`, `return`, `super`, `this`, `true`, `var`, `while`

### Keyword Recognition with Trie

Keywords are identified using a trie-like structure. Instead of storing the entire trie in memory, the code uses a series of switch statements that effectively walk through the trie:

```
                    [first char]
                    /    |    \
                   a     c     f
                   |     |    /|\
                  nd   lass  a o u
                   |         | | |
                  AND      lse r n
                            |  |  |
                         FALSE FOR FUN
```

Implementation:

```c
static TokenType identifierType() {
    switch (scanner.start[0]) {
        case 'a': return checkKeyword(1, 2, "nd", TOKEN_AND);
        case 'c': return checkKeyword(1, 4, "lass", TOKEN_CLASS);
        case 'f':
            if (scanner.current - scanner.start > 1) {
                switch (scanner.start[1]) {
                    case 'a': return checkKeyword(2, 3, "lse", TOKEN_FALSE);
                    case 'o': return checkKeyword(2, 1, "r", TOKEN_FOR);
                    case 'u': return checkKeyword(2, 1, "n", TOKEN_FUN);
                }
            }
            break;
        // ... more cases
    }
    return TOKEN_IDENTIFIER;
}
```

The `checkKeyword` function verifies the rest of the keyword matches:

```c
static TokenType checkKeyword(int start, int length, const char* rest, TokenType type) {
    if (scanner.current - scanner.start == start + length &&
        memcmp(scanner.start + start, rest, length) == 0) {
        return type;
    }
    return TOKEN_IDENTIFIER;
}
```

---

## Pratt Parsing

Pratt parsing (also called "top-down operator precedence parsing") is an elegant technique for parsing expressions that handles operator precedence without complex grammar rules or deep recursion.

### The Problem Pratt Parsing Solves

Consider parsing: `1 + 2 * 3`

This should parse as `1 + (2 * 3)` because `*` has higher precedence than `+`. Traditional recursive descent parsing requires separate functions for each precedence level:

```c
// Traditional approach - one function per precedence level
expression() → equality()
equality() → comparison()
comparison() → term()
term() → factor()       // handles + and -
factor() → unary()      // handles * and /
unary() → primary()
```

Pratt parsing replaces this tower of functions with a single, table-driven algorithm.

### Core Concepts

#### 1. Precedence Levels

Each operator has a numeric precedence:

```c
typedef enum {
    PREC_NONE,
    PREC_ASSIGNMENT,  // =         (lowest)
    PREC_OR,          // or
    PREC_AND,         // and
    PREC_EQUALITY,    // == !=
    PREC_COMPARISON,  // < > <= >=
    PREC_TERM,        // + -
    PREC_FACTOR,      // * /
    PREC_UNARY,       // ! -
    PREC_CALL,        // . ()
    PREC_PRIMARY      //           (highest)
} Precedence;
```

#### 2. Prefix vs Infix

Tokens can appear in two positions:
- **Prefix**: At the start of an expression (e.g., `-` in `-5`, `(` in `(1+2)`)
- **Infix**: Between two expressions (e.g., `+` in `1+2`, `(` in `func(arg)`)

#### 3. Parse Rules Table

Each token type maps to its parsing behavior:

```c
typedef struct {
    ParseFn prefix;      // Function to call when token appears at start
    ParseFn infix;       // Function to call when token appears in middle
    Precedence precedence; // How tightly this operator binds
} ParseRule;

ParseRule rules[] = {
    // Token              Prefix     Infix    Precedence
    [TOKEN_LEFT_PAREN]  = {grouping, call,    PREC_CALL},
    [TOKEN_MINUS]       = {unary,    binary,  PREC_TERM},
    [TOKEN_PLUS]        = {NULL,     binary,  PREC_TERM},
    [TOKEN_SLASH]       = {NULL,     binary,  PREC_FACTOR},
    [TOKEN_STAR]        = {NULL,     binary,  PREC_FACTOR},
    [TOKEN_NUMBER]      = {number,   NULL,    PREC_NONE},
    [TOKEN_BANG]        = {unary,    NULL,    PREC_NONE},
    [TOKEN_EQUAL_EQUAL] = {NULL,     binary,  PREC_EQUALITY},
    // ... more rules
};
```

### The Parsing Algorithm

The heart of Pratt parsing is the `parsePrecedence` function:

```c
static void parsePrecedence(Precedence precedence) {
    advance();  // Consume the first token
    
    // Get the prefix rule for this token
    ParseFn prefixRule = getRule(parser.previous.type)->prefix;
    
    if (prefixRule == NULL) {
        error("Expect expression.");
        return;
    }
    
    // Parse the prefix expression
    bool canAssign = precedence <= PREC_ASSIGNMENT;
    prefixRule(canAssign);
    
    // Keep parsing infix expressions while precedence allows
    while (precedence <= getRule(parser.current.type)->precedence) {
        advance();
        ParseFn infixRule = getRule(parser.previous.type)->infix;
        infixRule(canAssign);
    }
}
```

### Step-by-Step Example: Parsing `1 + 2 * 3`

Let's trace through parsing `1 + 2 * 3`:

```
Step 1: parsePrecedence(PREC_ASSIGNMENT) called
        Current token: 1 (NUMBER)
        
Step 2: advance() → previous = NUMBER(1)
        Look up prefix rule for NUMBER → number()
        
Step 3: number() emits OP_CONSTANT for 1
        Stack: [1]
        
Step 4: Check infix loop condition:
        Current token: + (PLUS)
        PREC_ASSIGNMENT (1) <= PREC_TERM (6)? YES
        
Step 5: advance() → previous = PLUS
        Look up infix rule for PLUS → binary()
        
Step 6: binary() for +:
        - Get precedence of + → PREC_TERM
        - Call parsePrecedence(PREC_TERM + 1) = parsePrecedence(PREC_FACTOR)
          to parse right-hand side with higher precedence
        
Step 7: Inside nested parsePrecedence(PREC_FACTOR):
        advance() → previous = NUMBER(2)
        number() emits OP_CONSTANT for 2
        Stack: [1, 2]
        
Step 8: Check infix loop in nested call:
        Current token: * (STAR)
        PREC_FACTOR (7) <= PREC_FACTOR (7)? YES
        
Step 9: advance() → previous = STAR
        binary() for *:
        - Call parsePrecedence(PREC_FACTOR + 1) = parsePrecedence(PREC_UNARY)
        
Step 10: Inside deeply nested parsePrecedence(PREC_UNARY):
         advance() → previous = NUMBER(3)
         number() emits OP_CONSTANT for 3
         Stack: [1, 2, 3]
         
Step 11: Check infix loop:
         Current token: EOF
         PREC_UNARY (8) <= PREC_NONE (0)? NO
         Return from deeply nested call
         
Step 12: Back in Step 9's binary() for *:
         Emit OP_MULTIPLY
         Stack: [1, 6]  (2 * 3 = 6)
         Return from nested call
         
Step 13: Back in Step 6's binary() for +:
         Emit OP_ADD
         Stack: [7]  (1 + 6 = 7)
         
Step 14: Check infix loop in original call:
         Current token: EOF
         PREC_ASSIGNMENT (1) <= PREC_NONE (0)? NO
         Done!

parsePrecedence(1) for "1 + 2 * 3"
│
├─ number() emits 1
│
├─ sees '+' (prec 6 >= 1) → binary()
│  │
│  └─ parsePrecedence(7) for "2 * 3"   ← Higher precedence!
│     │
│     ├─ number() emits 2
│     │
│     ├─ sees '*' (prec 7 >= 7) → binary()
│     │  │
│     │  └─ parsePrecedence(8) for "3"
│     │     │
│     │     ├─ number() emits 3
│     │     │
│     │     └─ sees EOF (prec 0 < 8) → return
│     │
│     │  emits OP_MULTIPLY         ← * happens first!
│     │
│     └─ sees EOF (prec 0 < 7) → return
│
│  emits OP_ADD                    ← + happens second!
│
└─ done!

Bytecode: CONST 1, CONST 2, CONST 3, MULTIPLY, ADD
Result:   1 + (2 * 3) = 7  ✓

```

**Generated bytecode:**
```
OP_CONSTANT 1
OP_CONSTANT 2
OP_CONSTANT 3
OP_MULTIPLY     // 2 * 3 first (higher precedence)
OP_ADD          // then 1 + result
```

### Why Pratt Parsing is Elegant

1. **Single function handles all precedence levels**: No tower of `term()`, `factor()`, etc.
2. **Table-driven**: Adding new operators just requires a table entry
3. **Handles prefix and infix uniformly**: Same mechanism for both
4. **Easy to add new syntax**: Mixfix operators, ternary, etc.

### Prefix Expression Example: `-5 + 3`

```c
static void unary(bool canAssign) {
    TokenType operatorType = parser.previous.type;
    
    // Compile the operand (recursively, at PREC_UNARY level)
    parsePrecedence(PREC_UNARY);
    
    // Emit the operator instruction
    switch (operatorType) {
        case TOKEN_MINUS: emitByte(OP_NEGATE); break;
        case TOKEN_BANG: emitByte(OP_NOT); break;
    }
}
```

Trace:
```
1. parsePrecedence gets MINUS, calls unary()
2. unary() calls parsePrecedence(PREC_UNARY) for operand
3. That parses NUMBER(5), emits OP_CONSTANT 5
4. Returns to unary(), which emits OP_NEGATE
5. Back in main loop, sees +, calls binary()
6. binary() parses 3, emits OP_CONSTANT 3
7. Emits OP_ADD

Bytecode: OP_CONSTANT 5, OP_NEGATE, OP_CONSTANT 3, OP_ADD
```

---

## Bytecode Representation

### Chunks

A chunk is a sequence of bytecode instructions with associated metadata:

```c
typedef struct {
    int count;           // Number of bytes in use
    int capacity;        // Total allocated capacity
    uint8_t* code;       // Bytecode array
    int* lines;          // Source line numbers (parallel array)
    ValueArray constants; // Constant pool
} Chunk;
```

### Memory Layout

```
Chunk:
┌─────────────────────────────────────────────────────────────┐
│ count: 8                                                     │
│ capacity: 16                                                 │
│ code: ─────────┐                                             │
│ lines: ────────│─────┐                                       │
│ constants: ... │     │                                       │
└────────────────│─────│───────────────────────────────────────┘
                 │     │
                 ▼     ▼
code array:   [OP_CONSTANT, 0, OP_CONSTANT, 1, OP_ADD, OP_PRINT, OP_NIL, OP_RETURN]
lines array:  [    1,       1,     1,       1,    1,      1,       1,       1    ]
```

### Instruction Set

The VM implements a rich set of opcodes:

| Category | Opcodes | Description |
|----------|---------|-------------|
| Constants | `OP_CONSTANT`, `OP_NIL`, `OP_TRUE`, `OP_FALSE` | Push literal values |
| Arithmetic | `OP_ADD`, `OP_SUBTRACT`, `OP_MULTIPLY`, `OP_DIVIDE`, `OP_NEGATE` | Math operations |
| Comparison | `OP_EQUAL`, `OP_GREATER`, `OP_LESS` | Compare values |
| Logic | `OP_NOT` | Boolean negation |
| Variables | `OP_GET_LOCAL`, `OP_SET_LOCAL`, `OP_GET_GLOBAL`, `OP_SET_GLOBAL`, `OP_DEFINE_GLOBAL` | Variable access |
| Upvalues | `OP_GET_UPVALUE`, `OP_SET_UPVALUE`, `OP_CLOSE_UPVALUE` | Closure variables |
| Control Flow | `OP_JUMP`, `OP_JUMP_IF_FALSE`, `OP_LOOP` | Branching |
| Functions | `OP_CALL`, `OP_CLOSURE`, `OP_RETURN` | Function operations |
| Classes | `OP_CLASS`, `OP_INHERIT`, `OP_METHOD`, `OP_GET_PROPERTY`, `OP_SET_PROPERTY`, `OP_INVOKE`, `OP_GET_SUPER`, `OP_SUPER_INVOKE` | OOP |
| Stack | `OP_POP`, `OP_PRINT` | Stack manipulation |

### Instruction Encoding

Different instructions have different sizes:

```
Simple instruction (1 byte):
┌──────────────┐
│   OP_ADD     │
└──────────────┘

Instruction with operand (2 bytes):
┌──────────────┬──────────────┐
│ OP_CONSTANT  │   index      │
└──────────────┴──────────────┘

Jump instruction (3 bytes):
┌──────────────┬──────────────┬──────────────┐
│   OP_JUMP    │  offset_hi   │  offset_lo   │
└──────────────┴──────────────┴──────────────┘
```

### Dynamic Array Growth

Arrays grow dynamically with a doubling strategy:

```c
#define GROW_CAPACITY(capacity) \
    ((capacity) < 8 ? 8 : (capacity) * 2)

#define GROW_ARRAY(type, pointer, oldCount, newCount) \
    (type*)reallocate(pointer, sizeof(type) * (oldCount), \
        sizeof(type) * (newCount))
```

Growth pattern: 0 → 8 → 16 → 32 → 64 → 128 → ...

---

## Value Representation and NaN Boxing

### The Challenge

The VM needs to represent multiple types in a single `Value`:
- Booleans (`true`, `false`)
- Numbers (floating-point)
- Nil
- Object pointers (strings, functions, classes, etc.)

### Approach 1: Tagged Union (Default)

The straightforward approach uses a tagged union:

```c
typedef enum {
    VAL_BOOL,
    VAL_NIL,
    VAL_NUMBER,
    VAL_OBJ
} ValueType;

typedef struct {
    ValueType type;    // 4 bytes (padded to 8 on 64-bit)
    union {
        bool boolean;  // 1 byte
        double number; // 8 bytes
        Obj* obj;      // 8 bytes
    } as;
} Value;
// Total: 16 bytes per value
```

Memory layout:
```
┌────────────────────────────────────────────────────┐
│ type (4 bytes) │ padding (4 bytes) │ union (8 bytes) │
└────────────────────────────────────────────────────┘
                        16 bytes total
```

**Problem**: Each value takes 16 bytes, and we need to check the type tag on every operation.

### Approach 2: NaN Boxing (Optimization)

NaN boxing exploits the IEEE 754 floating-point representation to pack all value types into just 8 bytes.

#### Understanding IEEE 754 Doubles

A 64-bit double has this structure:
```
┌─────┬─────────────┬────────────────────────────────────────────────┐
│Sign │  Exponent   │                   Mantissa                      │
│1 bit│  11 bits    │                   52 bits                       │
└─────┴─────────────┴────────────────────────────────────────────────┘
  63    62      52    51                                            0
```

#### What is NaN?

NaN (Not a Number) is a special floating-point value. IEEE 754 defines NaN as:
- Exponent: All 1s (11111111111)
- Mantissa: Non-zero (any pattern)

This means there are **2^52 - 1 different NaN values**! Most of these are unused.

#### Quiet NaN vs Signaling NaN

- **Signaling NaN**: Mantissa's highest bit = 0, triggers exceptions
- **Quiet NaN**: Mantissa's highest bit = 1, propagates silently

Intel CPUs use a specific quiet NaN pattern, leaving many bits unused:
```
Quiet NaN:
┌─────┬─────────────┬───┬────────────────────────────────────────────┐
│  X  │ 11111111111 │ 1 │                 UNUSED                      │
└─────┴─────────────┴───┴────────────────────────────────────────────┘
  63    62      52    51   50                                       0
                      ↑
                 Quiet bit
```

**We have 51 bits to encode non-number values!**

#### NaN Boxing Encoding Scheme

```c
#define SIGN_BIT ((uint64_t)0x8000000000000000)  // Bit 63
#define QNAN     ((uint64_t)0x7ffc000000000000)  // Quiet NaN pattern

typedef uint64_t Value;
```

The encoding:
```
Regular number (any valid double):
┌────────────────────────────────────────────────────────────────────┐
│                      IEEE 754 double value                          │
└────────────────────────────────────────────────────────────────────┘

Nil (QNAN | 01):
┌─────┬─────────────┬───┬───────────────────────────────────────┬────┐
│  0  │ 11111111111 │ 1 │ 0000000000000000000000000000000000000 │ 01 │
└─────┴─────────────┴───┴───────────────────────────────────────┴────┘

False (QNAN | 10):
┌─────┬─────────────┬───┬───────────────────────────────────────┬────┐
│  0  │ 11111111111 │ 1 │ 0000000000000000000000000000000000000 │ 10 │
└─────┴─────────────┴───┴───────────────────────────────────────┴────┘

True (QNAN | 11):
┌─────┬─────────────┬───┬───────────────────────────────────────┬────┐
│  0  │ 11111111111 │ 1 │ 0000000000000000000000000000000000000 │ 11 │
└─────┴─────────────┴───┴───────────────────────────────────────┴────┘

Object pointer (SIGN_BIT | QNAN | pointer):
┌─────┬─────────────┬───┬────────────────────────────────────────────┐
│  1  │ 11111111111 │ 1 │          48-bit pointer value               │
└─────┴─────────────┴───┴────────────────────────────────────────────┘
  ↑
 Sign bit indicates it's an object
```

#### Implementation

```c
// Type checking (no tag field to read!)
#define IS_NUMBER(value) (((value) & QNAN) != QNAN)
#define IS_NIL(value)    ((value) == NIL_VAL)
#define IS_BOOL(value)   (((value) | 1) == TRUE_VAL)
#define IS_OBJ(value)    (((value) & (QNAN | SIGN_BIT)) == (QNAN | SIGN_BIT))

// Value extraction
#define AS_NUMBER(value) valueToNum(value)
#define AS_BOOL(value)   ((value) == TRUE_VAL)
#define AS_OBJ(value)    ((Obj*)(uintptr_t)((value) & ~(SIGN_BIT | QNAN)))

// Value creation
#define NIL_VAL   ((Value)(uint64_t)(QNAN | TAG_NIL))
#define TRUE_VAL  ((Value)(uint64_t)(QNAN | TAG_TRUE))
#define FALSE_VAL ((Value)(uint64_t)(QNAN | TAG_FALSE))
#define NUMBER_VAL(num) numToValue(num)
#define OBJ_VAL(obj)    (Value)(SIGN_BIT | QNAN | (uint64_t)(uintptr_t)(obj))
```

#### Why 48-bit Pointers are Enough

On x86-64, virtual addresses only use 48 bits (256 TB address space). The upper 16 bits are unused and available for our encoding.

#### Number Conversion

Numbers need special handling because we can't just cast—the bit pattern must be preserved:

```c
static inline double valueToNum(Value value) {
    double num;
    memcpy(&num, &value, sizeof(Value));  // Copy bits, don't convert
    return num;
}

static inline Value numToValue(double num) {
    Value value;
    memcpy(&value, &num, sizeof(double));
    return value;
}
```

#### Benefits of NaN Boxing

1. **Memory**: 8 bytes vs 16 bytes per value (50% reduction)
2. **Cache**: More values fit in cache lines
3. **Speed**: Equality checks can use simple `==` for many cases:
   ```c
   bool valuesEqual(Value a, Value b) {
       if (IS_NUMBER(a) && IS_NUMBER(b)) {
           return AS_NUMBER(a) == AS_NUMBER(b);
       }
       return a == b;  // Bit-exact comparison works for nil, bool, same object
   }
   ```

---

## Virtual Machine

The VM (`vm.c`) executes bytecode using a stack-based architecture.

### VM State

```c
typedef struct {
    CallFrame frames[FRAMES_MAX];  // Call stack (function calls)
    int frameCount;
    
    Value stack[STACK_MAX];        // Value stack
    Value* stackTop;               // Points to next empty slot
    
    Table globals;                 // Global variables
    Table strings;                 // String intern table
    ObjString* initString;         // Cached "init" string for constructors
    ObjUpvalue* openUpvalues;      // Linked list of open upvalues
    
    // GC state
    size_t bytesAllocated;
    size_t nextGC;
    Obj* objects;                  // Head of all objects list
    int grayCount;
    int grayCapacity;
    Obj** grayStack;               // Worklist for GC tracing
} VM;
```

### Stack-Based Execution

The VM uses a stack for all computations:

```
Expression: 1 + 2 * 3

Bytecode:
    OP_CONSTANT 1    ; push 1
    OP_CONSTANT 2    ; push 2
    OP_CONSTANT 3    ; push 3
    OP_MULTIPLY      ; pop 3, pop 2, push 6
    OP_ADD           ; pop 6, pop 1, push 7

Stack evolution:
    []           start
    [1]          after OP_CONSTANT 1
    [1, 2]       after OP_CONSTANT 2
    [1, 2, 3]    after OP_CONSTANT 3
    [1, 6]       after OP_MULTIPLY (2*3=6)
    [7]          after OP_ADD (1+6=7)
```

### Call Frames

Each function invocation creates a call frame:

```c
typedef struct {
    ObjClosure* closure;   // The function being executed
    uint8_t* ip;           // Instruction pointer within this function
    Value* slots;          // Pointer to first slot this function can use
} CallFrame;
```

When a function is called:
```
Before call to foo(a, b):
Stack: [..., foo, a, b]
                 ↑
              slots (frame->slots points here)

Frame structure:
┌─────────────────────────────────────────────┐
│ closure: pointer to foo's closure            │
│ ip: pointer to first instruction in foo      │
│ slots: pointer to foo on stack              │
└─────────────────────────────────────────────┘
```

### Main Execution Loop

```c
static InterpretResult run() {
    CallFrame* frame = &vm.frames[vm.frameCount - 1];
    
    #define READ_BYTE() (*frame->ip++)
    #define READ_CONSTANT() (frame->closure->function->chunk.constants.values[READ_BYTE()])
    
    for (;;) {
        uint8_t instruction;
        switch (instruction = READ_BYTE()) {
            case OP_CONSTANT: {
                Value constant = READ_CONSTANT();
                push(constant);
                break;
            }
            case OP_ADD: {
                if (IS_STRING(peek(0)) && IS_STRING(peek(1))) {
                    concatenate();
                } else if (IS_NUMBER(peek(0)) && IS_NUMBER(peek(1))) {
                    double b = AS_NUMBER(pop());
                    double a = AS_NUMBER(pop());
                    push(NUMBER_VAL(a + b));
                }
                break;
            }
            case OP_RETURN: {
                Value result = pop();
                closeUpvalues(frame->slots);
                vm.frameCount--;
                if (vm.frameCount == 0) {
                    pop();
                    return INTERPRET_OK;
                }
                vm.stackTop = frame->slots;
                push(result);
                frame = &vm.frames[vm.frameCount - 1];
                break;
            }
            // ... more opcodes
        }
    }
}
```

### Stack Operations

```c
void push(Value value) {
    *vm.stackTop = value;
    vm.stackTop++;
}

Value pop() {
    vm.stackTop--;
    return *vm.stackTop;
}

static Value peek(int distance) {
    return vm.stackTop[-1 - distance];  // peek(0) = top, peek(1) = second from top
}
```

---

## Memory Management

### Unified Allocation Interface

All dynamic memory operations go through a single function:

```c
void* reallocate(void* pointer, size_t oldSize, size_t newSize) {
    vm.bytesAllocated += newSize - oldSize;  // Track memory usage
    
    if (newSize > oldSize) {
        if (vm.bytesAllocated > vm.nextGC) {
            collectGarbage();  // May trigger GC
        }
    }
    
    if (newSize == 0) {
        free(pointer);
        return NULL;
    }
    
    return realloc(pointer, newSize);
}
```

**Operation Matrix:**

| oldSize | newSize | Operation |
|---------|---------|-----------|
| 0 | Non-zero | Allocate new block |
| Non-zero | 0 | Free allocation |
| Non-zero | Smaller | Shrink allocation |
| Non-zero | Larger | Grow allocation |

### Convenience Macros

```c
#define ALLOCATE(type, count) \
    (type*)reallocate(NULL, 0, sizeof(type) * (count))

#define FREE(type, pointer) \
    reallocate(pointer, sizeof(type), 0)

#define FREE_ARRAY(type, pointer, oldCount) \
    reallocate(pointer, sizeof(type) * (oldCount), 0)
```

---

## Garbage Collection

clox implements a **Mark-and-Sweep** garbage collector using the **Tri-color Abstraction**.

### Why Garbage Collection?

Without GC, we'd have memory leaks:
```lox
fun leak() {
    var x = "this string is never freed";
}
leak();  // x goes out of scope, but string remains in memory
```

### The Tri-color Abstraction

Objects are conceptually colored during collection:

```
┌──────────────────────────────────────────────────────────────────┐
│  WHITE                     GRAY                    BLACK         │
│  (candidates for         (visited but           (visited and     │
│   collection)             refs not traced)       fully traced)   │
│                                                                   │
│  ┌───┐  ┌───┐            ┌───┐                  ┌───┐  ┌───┐    │
│  │ A │  │ B │            │ C │                  │ D │  │ E │    │
│  └───┘  └───┘            └───┘                  └───┘  └───┘    │
│     ↑                       │                                    │
│     │                       ▼                                    │
│     └──── will be freed   processing             reachable ─────┤
└──────────────────────────────────────────────────────────────────┘
```

### Collection Algorithm

```c
void collectGarbage() {
    markRoots();           // Find all root objects → make them gray
    traceReferences();     // Process gray objects → make them black
    tableRemoveWhite(&vm.strings);  // Handle weak references (interned strings)
    sweep();               // Free all remaining white objects
    
    vm.nextGC = vm.bytesAllocated * GC_HEAP_GROW_FACTOR;
}
```

### Step 1: Mark Roots

Roots are objects directly accessible without following pointers:

```c
static void markRoots() {
    // The value stack
    for (Value* slot = vm.stack; slot < vm.stackTop; slot++) {
        markValue(*slot);
    }
    
    // Call frames (closures being executed)
    for (int i = 0; i < vm.frameCount; i++) {
        markObject((Obj*)vm.frames[i].closure);
    }
    
    // Open upvalues (variables captured by closures)
    for (ObjUpvalue* upvalue = vm.openUpvalues; upvalue != NULL; upvalue = upvalue->next) {
        markObject((Obj*)upvalue);
    }
    
    // Global variables table
    markTable(&vm.globals);
    
    // Compiler roots (functions being compiled)
    markCompilerRoots();
    
    // The "init" string (used for constructors)
    markObject((Obj*)vm.initString);
}
```

### Step 2: Mark Object (White → Gray)

```c
void markObject(Obj* object) {
    if (object == NULL) return;
    if (object->isMarked) return;  // Already visited
    
    object->isMarked = true;  // Mark as reachable
    
    // Add to gray stack for later processing
    if (vm.grayCapacity < vm.grayCount + 1) {
        vm.grayCapacity = GROW_CAPACITY(vm.grayCapacity);
        vm.grayStack = realloc(vm.grayStack, sizeof(Obj*) * vm.grayCapacity);
    }
    vm.grayStack[vm.grayCount++] = object;
}
```

### Step 3: Trace References (Gray → Black)

Process each gray object: mark its references, then it becomes black:

```c
static void traceReferences() {
    while (vm.grayCount > 0) {
        Obj* object = vm.grayStack[--vm.grayCount];  // Pop from gray stack
        blackenObject(object);
    }
}

static void blackenObject(Obj* object) {
    switch (object->type) {
        case OBJ_CLOSURE: {
            ObjClosure* closure = (ObjClosure*)object;
            markObject((Obj*)closure->function);  // Mark the function
            for (int i = 0; i < closure->upvalueCount; i++) {
                markObject((Obj*)closure->upvalues[i]);  // Mark captured vars
            }
            break;
        }
        case OBJ_FUNCTION: {
            ObjFunction* function = (ObjFunction*)object;
            markObject((Obj*)function->name);  // Mark function name
            markArray(&function->chunk.constants);  // Mark constants
            break;
        }
        case OBJ_CLASS: {
            ObjClass* klass = (ObjClass*)object;
            markObject((Obj*)klass->name);  // Mark class name
            markTable(&klass->methods);  // Mark all methods
            break;
        }
        case OBJ_INSTANCE: {
            ObjInstance* instance = (ObjInstance*)object;
            markObject((Obj*)instance->klass);  // Mark the class
            markTable(&instance->fields);  // Mark all fields
            break;
        }
        case OBJ_UPVALUE:
            markValue(((ObjUpvalue*)object)->closed);  // Mark closed-over value
            break;
        case OBJ_STRING:
        case OBJ_NATIVE:
            // No outgoing references
            break;
    }
}
```

### Step 4: Sweep (Free White Objects)

Walk through all objects, freeing those still white:

```c
static void sweep() {
    Obj* previous = NULL;
    Obj* object = vm.objects;
    
    while (object != NULL) {
        if (object->isMarked) {
            object->isMarked = false;  // Reset for next GC cycle
            previous = object;
            object = object->next;
        } else {
            // This object is garbage!
            Obj* unreached = object;
            object = object->next;
            
            // Unlink from list
            if (previous != NULL) {
                previous->next = object;
            } else {
                vm.objects = object;
            }
            
            freeObject(unreached);
        }
    }
}
```

### Visual Example of GC

```
Before GC:
                ┌─────────────────────────────────────────┐
  vm.objects ──►│ String "hello" │──►│ String "world" │──►│ Function foo │──►│ String "unused" │──► NULL
                └─────────────────────────────────────────┘
                       ▲                                           ▲
                       │                                           │
  Stack: ──────────────┘                           Global "foo" ───┘

  "unused" is not reachable from any root.

After markRoots():
  Gray stack: [String "hello", Function foo]
  
After traceReferences():
  String "hello" → BLACK (no references)
  Function foo → BLACK (marks its constants)
  
After sweep():
  String "world" → WHITE → FREED
  String "unused" → WHITE → FREED
  
  vm.objects ──► String "hello" ──► Function foo ──► NULL
```

### GC Triggering

Collection runs when allocation crosses a threshold:

```c
if (vm.bytesAllocated > vm.nextGC) {
    collectGarbage();
}

// After collection, threshold grows (self-adjusting)
vm.nextGC = vm.bytesAllocated * GC_HEAP_GROW_FACTOR;  // Factor = 2
```

### Write Barriers: Preventing Premature Collection

During allocation, temporary values might not be on the stack yet. If GC runs, they'd be collected:

```c
// DANGEROUS without protection:
ObjString* a = copyString("hello", 5);  // GC might run here!
ObjString* b = copyString("world", 5);  // a could be freed before we use it!

// SAFE - push to stack first:
push(OBJ_VAL(copyString("hello", 5)));  // Now on stack, safe from GC
push(OBJ_VAL(copyString("world", 5)));
```

The concatenation function demonstrates this pattern:

```c
static void concatenate() {
    // Don't pop yet! GC during allocation could free them.
    ObjString* b = AS_STRING(peek(0));
    ObjString* a = AS_STRING(peek(1));
    
    int length = a->length + b->length;
    char* chars = ALLOCATE(char, length + 1);  // May trigger GC!
    // a and b are safe because they're still on the stack
    
    memcpy(chars, a->chars, a->length);
    memcpy(chars + a->length, b->chars, b->length);
    chars[length] = '\0';
    
    ObjString* result = takeString(chars, length);
    pop();  // Now safe to remove
    pop();
    push(OBJ_VAL(result));
}
```

---

## Object System

All heap-allocated values share a common header, enabling polymorphic handling.

### Base Object Structure

```c
struct Obj {
    ObjType type;        // What kind of object
    bool isMarked;       // GC mark bit
    struct Obj* next;    // Intrusive linked list for all objects
};
```

### Object Types

```c
typedef enum {
    OBJ_BOUND_METHOD,  // Method bound to an instance
    OBJ_CLASS,         // Class definition
    OBJ_CLOSURE,       // Function with captured variables
    OBJ_FUNCTION,      // Raw function (bytecode)
    OBJ_INSTANCE,      // Class instance
    OBJ_NATIVE,        // Built-in function (C code)
    OBJ_STRING,        // String value
    OBJ_UPVALUE        // Captured variable
} ObjType;
```

### Struct Inheritance Pattern

Objects "inherit" from Obj by embedding it as the first field:

```c
typedef struct {
    Obj obj;             // "Base class" - MUST be first!
    int length;
    char* chars;
    uint32_t hash;
} ObjString;

typedef struct {
    Obj obj;
    int arity;
    int upvalueCount;
    Chunk chunk;
    ObjString* name;
} ObjFunction;
```

Memory layout of ObjString:
```
┌───────────────────────────────────────────────────────────────┐
│ Obj (base)                                                     │
│ ┌──────────┬───────────┬──────────┐                           │
│ │ type     │ isMarked  │ next     │                           │
│ └──────────┴───────────┴──────────┘                           │
├───────────────────────────────────────────────────────────────┤
│ length: 5                                                      │
│ chars: ─────────────────────────►  "hello\0"                  │
│ hash: 0x1234ABCD                                               │
└───────────────────────────────────────────────────────────────┘
```

### Safe Casting

Because `Obj` is the first field, we can safely cast between types:

```c
// Given any Obj*, cast to specific type:
Obj* obj = allocateObject(sizeof(ObjString), OBJ_STRING);
ObjString* string = (ObjString*)obj;

// Check type before casting:
if (obj->type == OBJ_STRING) {
    ObjString* s = (ObjString*)obj;
    printf("%s\n", s->chars);
}

// Macros for convenience:
#define AS_STRING(value) ((ObjString*)AS_OBJ(value))
#define IS_STRING(value) isObjType(value, OBJ_STRING)
```

### Object Allocation

```c
static Obj* allocateObject(size_t size, ObjType type) {
    Obj* object = (Obj*)reallocate(NULL, 0, size);
    object->type = type;
    object->isMarked = false;
    
    // Insert at head of global object list (for GC)
    object->next = vm.objects;
    vm.objects = object;
    
    return object;
}

#define ALLOCATE_OBJ(type, objectType) \
    (type*)allocateObject(sizeof(type), objectType)

// Usage:
ObjString* string = ALLOCATE_OBJ(ObjString, OBJ_STRING);
```

---

## Hash Tables

clox uses open addressing with linear probing for hash tables. Hash tables are a critical data structure used throughout the interpreter for fast key-value lookups.

### Where Hash Tables Are Used in clox

Hash tables (`Table` struct) are used in **five key places** throughout the implementation:

| Location | Purpose | Key Type | Value Type |
|----------|---------|----------|------------|
| `vm.globals` | Store global variables | Variable names (strings) | Variable values |
| `vm.strings` | String interning pool | String content | NIL (just need keys) |
| `ObjClass.methods` | Store class methods | Method names | Closures |
| `ObjInstance.fields` | Store instance fields | Field names | Field values |
| GC marking | Mark reachable objects | N/A | N/A |

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        HASH TABLE USAGE IN CLOX                              │
└─────────────────────────────────────────────────────────────────────────────┘

1. GLOBAL VARIABLES (vm.globals)
   ┌─────────────────────────────────────────────────────┐
   │  var x = 10;                                         │
   │  var name = "Alice";                                 │
   │                                                      │
   │  vm.globals:                                         │
   │  ┌──────────┬─────────────┐                         │
   │  │ "x"      │ NUMBER(10)  │                         │
   │  │ "name"   │ OBJ(String) │──► "Alice"              │
   │  └──────────┴─────────────┘                         │
   └─────────────────────────────────────────────────────┘

2. STRING INTERNING (vm.strings)
   ┌─────────────────────────────────────────────────────┐
   │  "hello" appears 3 times in source code             │
   │  → Only ONE ObjString allocated in memory           │
   │                                                      │
   │  vm.strings:                                         │
   │  ┌──────────────────┬─────────┐                     │
   │  │ ObjString("hello")│ NIL    │                     │
   │  │ ObjString("world")│ NIL    │                     │
   │  └──────────────────┴─────────┘                     │
   │  (Keys ARE the values - we only care about lookup)  │
   └─────────────────────────────────────────────────────┘

3. CLASS METHODS (klass->methods)
   ┌─────────────────────────────────────────────────────┐
   │  class Dog {                                         │
   │      init(name) { this.name = name; }               │
   │      bark() { print "Woof!"; }                      │
   │  }                                                   │
   │                                                      │
   │  ObjClass "Dog":                                     │
   │  ┌───────────┬─────────────────┐                    │
   │  │ "init"    │ Closure(init)   │                    │
   │  │ "bark"    │ Closure(bark)   │                    │
   │  └───────────┴─────────────────┘                    │
   └─────────────────────────────────────────────────────┘

4. INSTANCE FIELDS (instance->fields)
   ┌─────────────────────────────────────────────────────┐
   │  var dog = Dog("Rex");                              │
   │  dog.age = 5;                                       │
   │                                                      │
   │  ObjInstance:                                        │
   │  ┌───────────┬─────────────────┐                    │
   │  │ "name"    │ OBJ("Rex")      │                    │
   │  │ "age"     │ NUMBER(5)       │                    │
   │  └───────────┴─────────────────┘                    │
   └─────────────────────────────────────────────────────┘

5. INHERITANCE (tableAddAll copies methods)
   ┌─────────────────────────────────────────────────────┐
   │  class Animal { speak() { print "..."; } }          │
   │  class Dog < Animal { bark() { print "Woof!"; } }   │
   │                                                      │
   │  Dog inherits from Animal:                          │
   │  tableAddAll(&Animal->methods, &Dog->methods);      │
   │                                                      │
   │  Dog->methods now contains:                         │
   │  ┌───────────┬─────────────────┐                    │
   │  │ "speak"   │ Closure(speak)  │ ← copied           │
   │  │ "bark"    │ Closure(bark)   │                    │
   │  └───────────┴─────────────────┘                    │
   └─────────────────────────────────────────────────────┘
```

### Hash Table Operations in Runtime

```c
// Global variable operations (vm.c)
case OP_DEFINE_GLOBAL: {
    ObjString* name = READ_STRING();
    tableSet(&vm.globals, name, peek(0));  // Store in globals table
    pop();
    break;
}

case OP_GET_GLOBAL: {
    ObjString* name = READ_STRING();
    Value value;
    if (!tableGet(&vm.globals, name, &value)) {  // Lookup in globals table
        runtimeError("Undefined variable '%s'.", name->chars);
        return INTERPRET_RUNTIME_ERROR;
    }
    push(value);
    break;
}

// Instance field access (vm.c)
case OP_GET_PROPERTY: {
    ObjInstance* instance = AS_INSTANCE(peek(0));
    ObjString* name = READ_STRING();
    Value value;
    
    // First check instance fields
    if (tableGet(&instance->fields, name, &value)) {
        pop();
        push(value);
        break;
    }
    
    // Then check class methods
    if (!bindMethod(instance->klass, name)) {
        return INTERPRET_RUNTIME_ERROR;
    }
    break;
}
```

### Structure

```c
typedef struct {
    ObjString* key;   // NULL means empty slot
    Value value;
} Entry;

typedef struct {
    int count;        // Number of entries
    int capacity;     // Size of array (always 2^n - 1 for fast modulo)
    Entry* entries;   // Array of entries
} Table;
```

### Hash Function (FNV-1a)

The FNV-1a algorithm is simple, fast, and has good distribution:

```c
static uint32_t hashString(const char* key, int length) {
    uint32_t hash = 2166136261u;  // FNV offset basis
    
    for (int i = 0; i < length; i++) {
        hash ^= (uint8_t)key[i];  // XOR with byte
        hash *= 16777619;          // Multiply by FNV prime
    }
    
    return hash;
}
```

Example:
```
hashString("cat", 3):
  hash = 2166136261
  
  i=0: hash = (2166136261 ^ 'c') * 16777619 = ...
  i=1: hash = (... ^ 'a') * 16777619 = ...
  i=2: hash = (... ^ 't') * 16777619 = 108828759
```

### Open Addressing with Linear Probing

Instead of linked lists for collisions, we probe sequentially:

```
Table (capacity = 7):
Index:    0       1       2       3       4       5       6
        ┌───────┬───────┬───────┬───────┬───────┬───────┬───────┐
        │ NULL  │"apple"│ NULL  │"cat"  │"dog"  │ NULL  │ NULL  │
        │       │  5    │       │  3    │  4    │       │       │
        └───────┴───────┴───────┴───────┴───────┴───────┴───────┘

Inserting "bat" with hash that maps to index 3:
1. Index 3 is occupied ("cat")
2. Try index 4 - occupied ("dog")
3. Try index 5 - empty! Insert here.

        ┌───────┬───────┬───────┬───────┬───────┬───────┬───────┐
        │ NULL  │"apple"│ NULL  │"cat"  │"dog"  │"bat"  │ NULL  │
        │       │  5    │       │  3    │  4    │  2    │       │
        └───────┴───────┴───────┴───────┴───────┴───────┴───────┘
```

### Finding Entries

```c
static Entry* findEntry(Entry* entries, int capacity, ObjString* key) {
    uint32_t index = key->hash & capacity;  // Fast modulo (capacity is 2^n - 1)
    Entry* tombstone = NULL;
    
    for (;;) {
        Entry* entry = &entries[index];
        
        if (entry->key == NULL) {
            if (IS_NIL(entry->value)) {
                // Empty slot
                return tombstone != NULL ? tombstone : entry;
            } else {
                // Tombstone - remember first one
                if (tombstone == NULL) tombstone = entry;
            }
        } else if (entry->key == key) {  // Pointer comparison! (interning)
            return entry;
        }
        
        index = (index + 1) & capacity;  // Linear probe
    }
}
```

### Tombstones: Handling Deletion

Simply clearing a deleted entry would break probe sequences:

```
Before delete:
Index:    0       1       2       3       4       5       6
        ┌───────┬───────┬───────┬───────┬───────┬───────┬───────┐
        │ NULL  │"apple"│ NULL  │"cat"  │"dog"  │"bat"  │ NULL  │
        └───────┴───────┴───────┴───────┴───────┴───────┴───────┘

Delete "dog" (naively set to NULL):
        ┌───────┬───────┬───────┬───────┬───────┬───────┬───────┐
        │ NULL  │"apple"│ NULL  │"cat"  │ NULL  │"bat"  │ NULL  │
        └───────┴───────┴───────┴───────┴───────┴───────┴───────┘

Now searching for "bat":
1. hash("bat") → index 3
2. Index 3 is "cat", not "bat", probe next
3. Index 4 is NULL → conclude "bat" not found! WRONG!
```

Solution: Use tombstones (key=NULL, value=true):

```c
bool tableDelete(Table* table, ObjString* key) {
    Entry* entry = findEntry(table->entries, table->capacity, key);
    if (entry->key == NULL) return false;
    
    // Place tombstone (NULL key, non-NIL value)
    entry->key = NULL;
    entry->value = BOOL_VAL(true);
    
    return true;
}
```

```
After proper delete with tombstone:
        ┌───────┬───────┬───────┬───────┬───────┬───────┬───────┐
        │ NULL  │"apple"│ NULL  │"cat"  │TOMBST │"bat"  │ NULL  │
        │ nil   │       │ nil   │       │ true  │       │ nil   │
        └───────┴───────┴───────┴───────┴───────┴───────┴───────┘

Now searching for "bat":
1. Index 3 is "cat", probe
2. Index 4 is tombstone, continue probing
3. Index 5 is "bat" → Found!
```

### Load Factor and Resizing

```c
#define TABLE_MAX_LOAD 0.75  // Resize when 75% full

bool tableSet(Table* table, ObjString* key, Value value) {
    if (table->count + 1 > (table->capacity + 1) * TABLE_MAX_LOAD) {
        int capacity = GROW_CAPACITY(table->capacity + 1) - 1;
        adjustCapacity(table, capacity);
    }
    // ... insert
}
```

---

## String Interning

String interning is a technique where each unique string is stored only once in memory. All references to the same string content point to the same object.

### The Problem Without Interning

```lox
var a = "hello";
var b = "hello";
print a == b;  // Would need to compare each character: O(n)
```

Each `"hello"` would create a separate object:
```
┌─────────────────────┐     ┌─────────────────────┐
│ ObjString           │     │ ObjString           │
│ chars: "hello"      │     │ chars: "hello"      │
│ length: 5           │     │ length: 5           │
└─────────────────────┘     └─────────────────────┘
     ▲                           ▲
     │                           │
   var a                       var b

Equality check: strcmp(a->chars, b->chars) == 0 → O(n)
```

### How Interning Works

Before creating a new string, check if an identical one already exists:

```c
ObjString* copyString(const char* chars, int length) {
    uint32_t hash = hashString(chars, length);
    
    // Look up in intern table
    ObjString* interned = tableFindString(&vm.strings, chars, length, hash);
    if (interned != NULL) return interned;  // Return existing string
    
    // Not found - create new string
    char* heapChars = ALLOCATE(char, length + 1);
    memcpy(heapChars, chars, length);
    heapChars[length] = '\0';
    
    return allocateString(heapChars, length, hash);
}

static ObjString* allocateString(char* chars, int length, uint32_t hash) {
    ObjString* string = ALLOCATE_OBJ(ObjString, OBJ_STRING);
    string->length = length;
    string->chars = chars;
    string->hash = hash;
    
    // Add to intern table
    tableSet(&vm.strings, string, NIL_VAL);
    
    return string;
}
```

With interning:
```
vm.strings (intern table):
┌─────────────────────────────────────┐
│ "hello" → ObjString @ 0x1234        │
│ "world" → ObjString @ 0x5678        │
└─────────────────────────────────────┘

         ┌─────────────────────┐
         │ ObjString @ 0x1234  │
         │ chars: "hello"      │
         │ length: 5           │
         └─────────────────────┘
              ▲       ▲
              │       │
            var a   var b

Equality check: a == b (pointer comparison) → O(1)
```

### Lookup in Intern Table

The `tableFindString` function searches by content (not pointer):

```c
ObjString* tableFindString(Table* table, const char* chars, int length, uint32_t hash) {
    if (table->count == 0) return NULL;
    
    uint32_t index = hash & table->capacity;
    
    for (;;) {
        Entry* entry = &table->entries[index];
        
        if (entry->key == NULL) {
            if (IS_NIL(entry->value)) return NULL;  // Empty, not found
        } else if (entry->key->length == length &&
                   entry->key->hash == hash &&
                   memcmp(entry->key->chars, chars, length) == 0) {
            return entry->key;  // Found!
        }
        
        index = (index + 1) & table->capacity;
    }
}
```

### takeString vs copyString

Two functions for creating strings:

```c
// copyString: Source might be temporary (e.g., source code)
// Must copy characters to heap
ObjString* copyString(const char* chars, int length);

// takeString: Already have heap-allocated chars
// Take ownership of the memory
ObjString* takeString(char* chars, int length) {
    uint32_t hash = hashString(chars, length);
    
    // Check if already interned
    ObjString* interned = tableFindString(&vm.strings, chars, length, hash);
    if (interned != NULL) {
        FREE_ARRAY(char, chars, length + 1);  // Free duplicate
        return interned;
    }
    
    return allocateString(chars, length, hash);  // Take ownership
}
```

### GC and Weak References

Interned strings are stored with weak references. If nothing else references a string, it should be collected:

```c
void collectGarbage() {
    markRoots();
    traceReferences();
    tableRemoveWhite(&vm.strings);  // Remove unreachable strings from intern table
    sweep();
}

void tableRemoveWhite(Table* table) {
    for (int i = 0; i <= table->capacity; i++) {
        Entry* entry = &table->entries[i];
        if (entry->key != NULL && !entry->key->obj.isMarked) {
            tableDelete(table, entry->key);  // Remove from intern table
        }
    }
}
```

### Benefits of String Interning

1. **O(1) equality**: Compare pointers instead of characters
2. **Memory efficiency**: Duplicate strings share storage
3. **Faster hashing**: Hash computed once, stored in object
4. **Faster table lookups**: Pointer comparison in findEntry

---

## Closures and Upvalues

Closures allow functions to capture variables from enclosing scopes, even after those scopes have ended. This is a powerful feature that enables **encapsulation** and **data hiding** in functional programming.

### What is a Closure? (Encapsulation in Action)

A **closure** is a function bundled together with its **lexical environment** (the variables it can "see" from its surrounding scope). This is encapsulation because:

1. **Data Hiding**: The captured variables are hidden from the outside world
2. **State Preservation**: The function "remembers" its environment even after the outer function returns
3. **Controlled Access**: Only the inner function can access/modify the captured variables

```lox
fun makeCounter() {
    var count = 0;              // Private state - hidden from outside
    
    fun increment() {
        count = count + 1;      // Only increment() can access 'count'
        print count;
    }
    
    return increment;           // Return the closure
}

var counter = makeCounter();    // 'count' is encapsulated inside
counter();  // 1                // We can only interact through increment()
counter();  // 2
// There's NO way to access 'count' directly from here!
```

This is similar to **private members in OOP** - the `count` variable is effectively private, and the returned function is the only public interface to interact with it.

### The Challenge: Stack Variables Disappear

```
When makeCounter() is called:
┌─────────────────────────────────────────────────────────────────┐
│                         CALL STACK                               │
├─────────────────────────────────────────────────────────────────┤
│  makeCounter's frame:                                            │
│  ┌─────────┬─────────┬─────────────────┐                        │
│  │ slot 0  │ slot 1  │ slot 2          │                        │
│  │ fn ref  │ count=0 │ increment       │                        │
│  └─────────┴─────────┴─────────────────┘                        │
│                 ↑                                                │
│                 │ increment() references this!                   │
└─────────────────────────────────────────────────────────────────┘

When makeCounter() RETURNS:
┌─────────────────────────────────────────────────────────────────┐
│  makeCounter's frame is GONE! Stack slots are invalid!          │
│  ┌─────────┬─────────┬─────────────────┐                        │
│  │ ?????   │ ?????   │ ?????           │  ← Memory reused!      │
│  └─────────┴─────────┴─────────────────┘                        │
│                 ↑                                                │
│                 │ increment() still needs 'count'!              │
│                 └─── PROBLEM: Where is count now???             │
└─────────────────────────────────────────────────────────────────┘
```

**Problem**: Local variables live on the stack. When a function returns, its stack frame is popped. But closures need those variables to survive!

### Solution: Upvalues (The Bridge Between Stack and Heap)

An **upvalue** is a data structure that provides a layer of indirection for accessing captured variables. It's the mechanism that allows closures to find and access variables from enclosing scopes.

```c
typedef struct ObjUpvalue {
    Obj obj;
    Value* location;           // Pointer to where the variable currently lives
    Value closed;              // Storage for the value when moved to heap
    struct ObjUpvalue* next;   // Linked list of open upvalues (sorted by stack address)
} ObjUpvalue;

typedef struct {
    Obj obj;
    ObjFunction* function;     // The compiled function
    ObjUpvalue** upvalues;     // Array of captured variable references
    int upvalueCount;          // How many variables are captured
} ObjClosure;
```

### How Closures Find Variables in Scope (Compile-Time Resolution)

When the compiler encounters a variable reference, it searches through nested scopes in a specific order:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    VARIABLE RESOLUTION ALGORITHM                             │
└─────────────────────────────────────────────────────────────────────────────┘

fun outer() {
    var x = 1;              ← Scope depth 1
    
    fun middle() {
        var y = 2;          ← Scope depth 2
        
        fun inner() {
            print x + y;    ← Scope depth 3: Where is x? Where is y?
        }
    }
}

Resolution steps for 'x' inside inner():

Step 1: Look for 'x' as LOCAL in inner()
        └─► Not found

Step 2: Look for 'x' as LOCAL in middle() (enclosing)
        └─► Not found

Step 3: Look for 'x' as LOCAL in outer() (enclosing of enclosing)
        └─► FOUND! Mark it as captured, create upvalue chain

For 'y':
Step 1: Look for 'y' as LOCAL in inner()
        └─► Not found

Step 2: Look for 'y' as LOCAL in middle() (enclosing)
        └─► FOUND! Mark it as captured
```

The compiler builds an **upvalue chain** for variables not in the immediate enclosing scope:

```c
static int resolveUpvalue(Compiler* compiler, Token* name) {
    if (compiler->enclosing == NULL) return -1;  // Reached global scope
    
    // STEP 1: Is it a local in the IMMEDIATE enclosing function?
    int local = resolveLocal(compiler->enclosing, name);
    if (local != -1) {
        // Mark the local as captured (so it gets hoisted later)
        compiler->enclosing->locals[local].isCaptured = true;
        return addUpvalue(compiler, (uint8_t)local, true);  // isLocal = true
    }
    
    // STEP 2: Is it an upvalue in the enclosing function? (RECURSIVE!)
    int upvalue = resolveUpvalue(compiler->enclosing, name);
    if (upvalue != -1) {
        // It's an upvalue of our enclosing function - we chain to it
        return addUpvalue(compiler, (uint8_t)upvalue, false);  // isLocal = false
    }
    
    return -1;  // Not found in any enclosing scope → must be global
}
```

**Visual representation of upvalue chaining:**

```
fun outer() {
    var x = 1;      ← Local in outer
    
    fun middle() {
        fun inner() {
            print x;  ← Needs to access x
        }
    }
}

Upvalue chain created:

outer                    middle                   inner
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│ Locals:         │     │ Locals: (none)  │     │ Locals: (none)  │
│ [0] x (captured)│     │                 │     │                 │
│                 │     │ Upvalues:       │     │ Upvalues:       │
│                 │     │ [0] → outer's   │     │ [0] → middle's  │
│                 │◄────│      local x    │◄────│      upvalue[0] │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                        isLocal = true          isLocal = false
                        (points to stack)       (points to upvalue)
```

### The isCaptured Flag: Marking Variables for Heap Migration

```c
typedef struct {
    Token name;
    int depth;
    bool isCaptured;  // THE KEY FLAG: Does any closure reference this?
} Local;
```

When a variable is captured:
1. `resolveUpvalue()` sets `isCaptured = true` on the local
2. At compile time, when the scope ends, we check this flag
3. If captured → emit `OP_CLOSE_UPVALUE` (hoist to heap)
4. If not captured → emit `OP_POP` (just discard)

```c
static void endScope() {
    current->scopeDepth--;

    while (current->localCount > 0 &&
           current->locals[current->localCount - 1].depth > current->scopeDepth) {
        
        if (current->locals[current->localCount - 1].isCaptured) {
            emitByte(OP_CLOSE_UPVALUE);  // Hoist to heap!
        } else {
            emitByte(OP_POP);            // Just discard
        }
        current->localCount--;
    }
}
```

### Open vs Closed Upvalues: The Two States

An upvalue exists in one of two states:

**State 1: OPEN (Variable still on stack)**

```
┌─────────────────────────────────────────────────────────────────┐
│                        OPEN UPVALUE                              │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Stack:                          ObjUpvalue:                     │
│  ┌─────────────────┐            ┌──────────────────────┐        │
│  │ ...             │            │ location: ───────────┼──┐     │
│  ├─────────────────┤            │ closed: NIL (unused) │  │     │
│  │ count = 3       │◄───────────│ next: ...            │  │     │
│  ├─────────────────┤            └──────────────────────┘  │     │
│  │ ...             │                                      │     │
│  └─────────────────┘◄─────────────────────────────────────┘     │
│                                                                  │
│  The upvalue's 'location' pointer points to the stack slot      │
│  Reading/writing through the upvalue accesses stack directly    │
└─────────────────────────────────────────────────────────────────┘
```

**State 2: CLOSED (Variable moved to heap)**

```
┌─────────────────────────────────────────────────────────────────┐
│                       CLOSED UPVALUE                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  Stack:                          ObjUpvalue (on heap):           │
│  ┌─────────────────┐            ┌──────────────────────┐        │
│  │ ...             │            │ location: ───────┐   │        │
│  ├─────────────────┤            │ closed: 3      ◄─┘   │        │
│  │ (reused memory) │            │ next: ...            │        │
│  ├─────────────────┤            └──────────────────────┘        │
│  │ ...             │                    ▲                       │
│  └─────────────────┘                    │                       │
│                                         │                       │
│  The upvalue's 'location' now points to its OWN 'closed' field  │
│  The value has been COPIED from the stack into 'closed'         │
│  This is how the value survives after the function returns!     │
└─────────────────────────────────────────────────────────────────┘
```

### When Does a Variable Move from Stack to Heap?

A variable is "closed" (moved to heap) in these situations:

1. **Function Returns (OP_RETURN)**
   - All open upvalues belonging to the returning function are closed
   - `closeUpvalues(frame->slots)` closes everything in the frame

2. **Block Scope Ends with Captured Variable (OP_CLOSE_UPVALUE)**
   - When a local goes out of scope and `isCaptured` is true
   - Happens mid-function, not just at return

```lox
fun example() {
    var result;
    {
        var temp = 42;
        result = fun() { return temp; };  // temp is captured
    }  // ← OP_CLOSE_UPVALUE emitted here for 'temp'
       //   temp is closed even though example() hasn't returned
    
    return result;  // result still has access to temp's value
}
```

### How We Know Whether to Keep an Element on Heap

The decision is made at **compile time** through the `isCaptured` flag:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    DECISION: STACK vs HEAP                                   │
└─────────────────────────────────────────────────────────────────────────────┘

                          Is this local variable
                          referenced by an inner
                               function?
                                   │
                   ┌───────────────┴───────────────┐
                   │                               │
                   ▼                               ▼
                  YES                              NO
                   │                               │
                   ▼                               ▼
        ┌──────────────────┐            ┌──────────────────┐
        │ isCaptured = true│            │isCaptured = false│
        └────────┬─────────┘            └────────┬─────────┘
                 │                               │
                 ▼                               ▼
         When scope ends:               When scope ends:
        ┌──────────────────┐            ┌──────────────────┐
        │OP_CLOSE_UPVALUE  │            │ OP_POP           │
        │                  │            │                  │
        │ - Copy value to  │            │ - Simply discard │
        │   upvalue.closed │            │   from stack     │
        │ - Point location │            │                  │
        │   to itself      │            │                  │
        └──────────────────┘            └──────────────────┘
                 │                               │
                 ▼                               ▼
           Value survives                 Value is gone
           on the HEAP                    forever
```

### How Values Are Pushed to Heap (The closeUpvalues Function)

```c
static void closeUpvalues(Value* last) {
    // Walk through open upvalues from stack top downward
    while (vm.openUpvalues != NULL &&
           vm.openUpvalues->location >= last) {
        
        ObjUpvalue* upvalue = vm.openUpvalues;
        
        // STEP 1: Copy the value from stack to the upvalue's storage
        upvalue->closed = *upvalue->location;
        
        // STEP 2: Redirect the pointer to its own 'closed' field
        upvalue->location = &upvalue->closed;
        
        // STEP 3: Remove from open upvalues list
        vm.openUpvalues = upvalue->next;
    }
}
```

**Step-by-step visualization:**

```
BEFORE closeUpvalues():
═══════════════════════════════════════════════════════════════════════

Stack (about to be popped):              vm.openUpvalues list:
┌─────────────────┐ higher addresses     ┌───────────────────┐
│ ...             │        ▲             │ ObjUpvalue A      │
├─────────────────┤        │             │ location: ────────┼──┐
│ y = 20          │◄───────│─────────────│ closed: NIL       │  │
├─────────────────┤        │             │ next: ────────────┼──│──┐
│ x = 10          │◄───────│─────────┐   └───────────────────┘  │  │
├─────────────────┤        │         │                          │  │
│ fn ref          │        │         │   ┌───────────────────┐  │  │
└─────────────────┘ lower addresses  │   │ ObjUpvalue B      │  │  │
      ▲                              │   │ location: ────────┼──┘  │
      │                              └───│ closed: NIL       │     │
      │                                  │ next: NULL        │◄────┘
      │                                  └───────────────────┘
      │
   frame->slots (closeUpvalues will close everything >= this)


AFTER closeUpvalues():
═══════════════════════════════════════════════════════════════════════

Stack (now popped):                      Upvalues are now CLOSED:
┌─────────────────┐                      ┌───────────────────┐
│ (garbage)       │                      │ ObjUpvalue A      │
├─────────────────┤                      │ location: ────┐   │
│ (garbage)       │                      │ closed: 20  ◄─┘   │ ← Points to self!
├─────────────────┤                      │ next: ────────────┼──┐
│ (garbage)       │                      └───────────────────┘  │
└─────────────────┘                                             │
                                         ┌───────────────────┐  │
vm.openUpvalues = NULL (empty)           │ ObjUpvalue B      │  │
                                         │ location: ────┐   │◄─┘
Values 10 and 20 SURVIVE on the heap!    │ closed: 10  ◄─┘   │
                                         │ next: NULL        │
                                         └───────────────────┘
```

### Why Do We Need Heap Storage? (Closures Can Escape!)

A common question: "If the inner function is only accessible inside the outer function, why move values to heap?"

**The key insight: Inner functions can be RETURNED and called AFTER the outer function is gone!**

```lox
fun outer() {
    var x = 0;
    
    fun inner() {
        x = x + 1;
        print x;
    }
    
    return inner;  // ← inner ESCAPES outer!
}

var counter = outer();  // outer() finishes, its stack frame is GONE

counter();  // prints 1 - but where is x??? // calls inner() function
counter();  // prints 2 - x must survive somewhere!
counter();  // prints 3
```

**The closure `inner` OUTLIVES the function `outer` that created `x`!**

```
═══════════════════════════════════════════════════════════════════════════════
TIMELINE: Why Heap Storage is Necessary
═══════════════════════════════════════════════════════════════════════════════

TIME 1: var counter = outer();
─────────────────────────────────────────────────────────────────────────────

  outer() is called, creates:
  
  Stack:                              Upvalue (OPEN):
  ┌─────────────────┐                ┌────────────────────┐
  │ x = 0           │◄───────────────│ location: ─────────┤
  └─────────────────┘                │ closed: NIL        │
                                     └────────────────────┘

TIME 2: outer() returns inner
─────────────────────────────────────────────────────────────────────────────

  outer() is about to return → closeUpvalues() runs!
  
  Stack (about to be popped):         Upvalue (being CLOSED):
  ┌─────────────────┐                ┌────────────────────┐
  │ x = 0           │ ──── copy ───► │ closed: 0          │
  └─────────────────┘                │ location: ─────┐   │
                                     │ closed: 0    ◄─┘   │
                                     └────────────────────┘

TIME 3: outer() has returned, counter holds inner
─────────────────────────────────────────────────────────────────────────────

  Stack:                              Upvalue (CLOSED):
  ┌─────────────────┐                ┌────────────────────┐
  │ (garbage)       │                │ location: ─────┐   │
  │ (reused by      │                │ closed: 0    ◄─┘   │ ← x survives here!
  │  other code)    │                └────────────────────┘
  └─────────────────┘                        ▲
                                             │
                                      counter's closure
                                      still references this

TIME 4: counter() called → x becomes 1
─────────────────────────────────────────────────────────────────────────────

  inner() runs: x = x + 1
  
  Reads *location  → gets 0 from closed field
  Adds 1           → result is 1  
  Writes *location → stores 1 in closed field
  
                                     ┌────────────────────┐
                                     │ location: ─────┐   │
                                     │ closed: 1    ◄─┘   │ ← Updated!
                                     └────────────────────┘

TIME 5: counter() called again → x becomes 2
─────────────────────────────────────────────────────────────────────────────

                                     ┌────────────────────┐
                                     │ location: ─────┐   │
                                     │ closed: 2    ◄─┘   │ ← Updated again!
                                     └────────────────────┘
```

**Each call to `outer()` creates a NEW upvalue with its own `x`:**

```lox
var counter1 = outer();  // Creates upvalue #1 with x=0
var counter2 = outer();  // Creates upvalue #2 with NEW x=0

counter1();  // prints 1 (modifies upvalue #1)
counter1();  // prints 2
counter2();  // prints 1 (upvalue #2 is independent!)
counter1();  // prints 3
```

```
counter1's upvalue:              counter2's upvalue:
┌────────────────────┐          ┌────────────────────┐
│ closed: 3          │          │ closed: 1          │
└────────────────────┘          └────────────────────┘
       ▲                              ▲
       │                              │
   counter1                       counter2
   
Two independent closures, each with their own captured x!
```

| Scenario | Heap Needed? | Why? |
|----------|--------------|------|
| Inner called inside outer | Technically no, but upvalue still used | Uniform mechanism |
| Inner returned from outer | **YES!** | Closure outlives outer's stack frame |
| Multiple calls to outer | Each creates new upvalue | Each closure gets independent state |

### Why the Open Upvalues List is Sorted

The `vm.openUpvalues` list is sorted by stack address (highest first). This enables:

1. **Efficient closing**: When closing, we only need to close upvalues >= the frame's slots
2. **Deduplication**: Easy to find if an upvalue already exists for a slot

```c
static ObjUpvalue* captureUpvalue(Value* local) {
    ObjUpvalue* prevUpvalue = NULL;
    ObjUpvalue* upvalue = vm.openUpvalues;
    
    // Walk the list (sorted by address, descending)
    while (upvalue != NULL && upvalue->location > local) {
        prevUpvalue = upvalue;
        upvalue = upvalue->next;
    }
    
    // Already captured? Reuse it!
    if (upvalue != NULL && upvalue->location == local) {
        return upvalue;  // Multiple closures share the same upvalue
    }
    
    // Create new and insert in sorted position
    ObjUpvalue* createdUpvalue = newUpvalue(local);
    createdUpvalue->next = upvalue;
    
    if (prevUpvalue == NULL) {
        vm.openUpvalues = createdUpvalue;
    } else {
        prevUpvalue->next = createdUpvalue;
    }
    
    return createdUpvalue;
}
```

### Complete Example with Stack-to-Heap Migration

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
```

**Timeline of execution:**

```
═══════════════════════════════════════════════════════════════════════════════
PHASE 1: Compiling makeCounter
═══════════════════════════════════════════════════════════════════════════════

Compiler sees: var count = 0;
→ count is local at slot 1 in makeCounter

Compiler sees: count = count + 1; (inside increment)
→ resolveLocal(increment, "count") → -1 (not local to increment)
→ resolveUpvalue(increment, "count")
  → resolveLocal(makeCounter, "count") → 1 (found!)
  → Mark makeCounter.locals[1].isCaptured = TRUE
  → addUpvalue(increment, index=1, isLocal=true)
→ Emit OP_GET_UPVALUE 0 and OP_SET_UPVALUE 0

When compiling makeCounter's closing brace:
→ endScope() sees count has isCaptured = true
→ Emit OP_CLOSE_UPVALUE (not OP_POP!)

═══════════════════════════════════════════════════════════════════════════════
PHASE 2: Calling makeCounter()
═══════════════════════════════════════════════════════════════════════════════

Stack:
┌────────────────┐
│ slot 0: fn ref │  
│ slot 1: count=0│ ◄── Local variable on stack
│ slot 2: incr   │ ◄── Closure being created
└────────────────┘

OP_CLOSURE executed for increment:
→ captureUpvalue(frame->slots + 1)  // Capture slot 1 (count)
→ Creates ObjUpvalue pointing to stack slot
→ Adds to vm.openUpvalues list

vm.openUpvalues:
┌───────────────────┐
│ ObjUpvalue        │
│ location: ────────┼──► points to stack slot 1
│ closed: NIL       │
└───────────────────┘

═══════════════════════════════════════════════════════════════════════════════
PHASE 3: makeCounter() returns
═══════════════════════════════════════════════════════════════════════════════

OP_RETURN executed:
→ closeUpvalues(frame->slots) is called
→ Finds the upvalue for 'count'
→ Copies count's value (0) into upvalue.closed
→ Points upvalue.location to &upvalue.closed

vm.openUpvalues: NULL (empty now)

The closure is returned, stack frame is popped, but:
┌───────────────────┐
│ ObjUpvalue        │
│ location: ────┐   │
│ closed: 0   ◄─┘   │ ← Value 0 preserved here!
└───────────────────┘
        ▲
        │
        └── increment's upvalues[0] points here

═══════════════════════════════════════════════════════════════════════════════
PHASE 4: Calling counter() (which is increment)
═══════════════════════════════════════════════════════════════════════════════

OP_GET_UPVALUE 0:
→ frame->closure->upvalues[0]->location → &upvalue.closed
→ Gets value 0

OP_ADD:
→ 0 + 1 = 1

OP_SET_UPVALUE 0:
→ *frame->closure->upvalues[0]->location = 1
→ upvalue.closed is now 1

After first call:
┌───────────────────┐
│ ObjUpvalue        │
│ location: ────┐   │
│ closed: 1   ◄─┘   │ ← Updated to 1!
└───────────────────┘

═══════════════════════════════════════════════════════════════════════════════
PHASE 5: Second call to counter()
═══════════════════════════════════════════════════════════════════════════════

Same upvalue, same process:
→ Gets 1, adds 1 = 2, stores back

┌───────────────────┐
│ ObjUpvalue        │
│ location: ────┐   │
│ closed: 2   ◄─┘   │ ← Updated to 2!
└───────────────────┘
```

### Multiple Closures Sharing the Same Variable

```lox
fun makePair() {
    var value = 0;
    
    fun getValue() { return value; }
    fun setValue(n) { value = n; }
    
    return [getValue, setValue];  // (Lox doesn't have arrays, but imagine)
}
```

Both closures share the **same upvalue object**:

```
┌──────────────────────────┐
│  ObjUpvalue              │
│  location: ─► closed: 0  │◄────┬────────────────────────┐
└──────────────────────────┘     │                        │
                                 │                        │
┌──────────────────────────┐     │     ┌──────────────────────────┐
│ ObjClosure (getValue)    │     │     │ ObjClosure (setValue)    │
│ upvalues[0]: ────────────┼─────┘     │ upvalues[0]: ────────────┼
└──────────────────────────┘           └──────────────────────────┘

When setValue(5) is called:
→ Modifies upvalue.closed to 5
→ getValue() will now return 5
→ They share the SAME captured variable!
```

This is real encapsulation: both functions have access to the same private state, and nothing else can access it directly.

---

## Classes and Inheritance

### Class Structure

```c
typedef struct {
    Obj obj;
    ObjString* name;    // Class name
    Table methods;      // Method table
} ObjClass;

typedef struct {
    Obj obj;
    ObjClass* klass;    // The class this is an instance of
    Table fields;       // Instance fields (set at runtime)
} ObjInstance;
```

### Creating Classes

```lox
class Greeter {
    init(name) {
        this.name = name;
    }
    
    greet() {
        print "Hello, " + this.name + "!";
    }
}
```

Compiles to:
```
OP_CLASS "Greeter"    ; Create class object
OP_DEFINE_GLOBAL "Greeter"

OP_GET_GLOBAL "Greeter"   ; Load class for method binding
OP_CLOSURE <init>         ; Create init closure
OP_METHOD "init"          ; Bind method to class

OP_GET_GLOBAL "Greeter"
OP_CLOSURE <greet>
OP_METHOD "greet"

OP_POP                    ; Pop class after method definitions
```

### Method Definition

```c
static void defineMethod(ObjString* name) {
    Value method = peek(0);         // Closure on stack
    ObjClass* klass = AS_CLASS(peek(1));  // Class below it
    tableSet(&klass->methods, name, method);
    pop();  // Pop the closure
}
```

### Instance Creation

```c
case OBJ_CLASS: {
    ObjClass* klass = AS_CLASS(callee);
    
    // Create new instance
    vm.stackTop[-argCount - 1] = OBJ_VAL(newInstance(klass));
    
    // Call initializer if it exists
    Value initializer;
    if (tableGet(&klass->methods, vm.initString, &initializer)) {
        return call(AS_CLOSURE(initializer), argCount);
    } else if (argCount != 0) {
        runtimeError("Expected 0 arguments but got %d.", argCount);
        return false;
    }
    return true;
}
```

### Bound Methods

A **Bound Method** is a wrapper object that bundles together:
1. **A method (closure)** - the actual function code
2. **A receiver (instance)** - the object that `this` refers to

#### The Problem: Methods Need to Know Their `this`

```lox
class Dog {
    init(name) {
        this.name = name;
    }
    
    bark() {
        print this.name + " says Woof!";  // 'this' must refer to the dog!
    }
}

var dog = Dog("Rex");
dog.bark();  // "Rex says Woof!" - this = dog
```

When you call `dog.bark()`, the method needs to know which dog is `this`. Simple enough.

**But what about this?**

```lox
var dog = Dog("Rex");
var barkFn = dog.bark;  // Just ACCESS the method, don't call it yet

barkFn();  // Later we call it - but where is 'this'???
```

When we store `dog.bark` in a variable, we're separating the method from the instance. When we later call `barkFn()`, how does it know `this` should be `dog`?

#### The Solution: ObjBoundMethod

When you access a method on an instance (without calling it), we create a **Bound Method** that remembers both the method AND the instance:

```c
typedef struct {
    Obj obj;
    Value receiver;       // The instance ('this')
    ObjClosure* method;   // The actual method code
} ObjBoundMethod;
```

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          BOUND METHOD STRUCTURE                              │
└─────────────────────────────────────────────────────────────────────────────┘

var barkFn = dog.bark;   // This creates a bound method!

            ObjBoundMethod
           ┌─────────────────────────────────┐
           │ receiver: ──────────────────────┼───► ObjInstance (dog)
           │                                 │     ┌─────────────────┐
           │ method: ────────────────────────┼──┐  │ klass: Dog      │
           └─────────────────────────────────┘  │  │ fields:         │
                                                │  │   name: "Rex"   │
                                                │  └─────────────────┘
                                                │
                                                └──► ObjClosure (bark)
                                                     ┌─────────────────┐
                                                     │ function: bark  │
                                                     │ upvalues: ...   │
                                                     └─────────────────┘
```

#### When Is a Bound Method Created?

| Scenario | What Happens | Bound Method Created? |
|----------|--------------|----------------------|
| `dog.bark()` | Direct call via OP_INVOKE | **No** (optimized) |
| `var fn = dog.bark` | Method access | **Yes** |
| `fn()` | Calling stored method | Uses existing bound method |
| `super.bark()` | Super call | **Yes** (OP_GET_SUPER) |

#### How It Works in the VM

**Step 1: Accessing a method creates a bound method (OP_GET_PROPERTY)**

```c
case OP_GET_PROPERTY: {
    ObjInstance* instance = AS_INSTANCE(peek(0));
    ObjString* name = READ_STRING();
    Value value;

    // First check if it's a field
    if (tableGet(&instance->fields, name, &value)) {
        pop();
        push(value);
        break;
    }

    // Not a field? It must be a method → create bound method!
    if (!bindMethod(instance->klass, name)) {
        return INTERPRET_RUNTIME_ERROR;
    }
    break;
}

static bool bindMethod(ObjClass* klass, ObjString* name) {
    Value method;
    if (!tableGet(&klass->methods, name, &method)) {
        runtimeError("Undefined property '%s'.", name->chars);
        return false;
    }

    // Wrap method + instance together
    ObjBoundMethod* bound = newBoundMethod(peek(0),       // instance on stack
                                           AS_CLOSURE(method));
    pop();              // Pop instance
    push(OBJ_VAL(bound)); // Push bound method
    return true;
}
```

**Step 2: Calling a bound method sets up `this`**

```c
// In callValue():
case OBJ_BOUND_METHOD: {
    ObjBoundMethod* bound = AS_BOUND_METHOD(callee);

    // Put the receiver (instance) in slot 0 of the new call frame
    // This is where 'this' will be found!
    vm.stackTop[-argCount - 1] = bound->receiver;
    
    // Call the actual method closure
    return call(bound->method, argCount);
}
```

#### Visual Walkthrough

```
═══════════════════════════════════════════════════════════════════════════════
EXAMPLE: var barkFn = dog.bark;  barkFn();
═══════════════════════════════════════════════════════════════════════════════

STEP 1: Evaluate dog.bark
─────────────────────────────────────────────────────────────────────────────

Stack before:          Stack after:
┌─────────────┐        ┌─────────────────────────┐
│ dog (inst)  │   →    │ ObjBoundMethod          │
└─────────────┘        │   receiver: dog         │
                       │   method: bark closure  │
                       └─────────────────────────┘

STEP 2: Store in barkFn
─────────────────────────────────────────────────────────────────────────────

barkFn now holds the ObjBoundMethod

STEP 3: Call barkFn()
─────────────────────────────────────────────────────────────────────────────

callValue sees OBJ_BOUND_METHOD:

Before:                            After (setting up call frame):
Stack:                             Stack:
┌─────────────────────────┐        ┌─────────────────────────┐
│ bound method            │        │ dog  ← slot 0 ('this')  │ ← receiver inserted!
└─────────────────────────┘        └─────────────────────────┘

Then call(bound->method, 0) is invoked with bark closure

STEP 4: Inside bark(), 'this' works!
─────────────────────────────────────────────────────────────────────────────

When bark() accesses 'this', it reads slot 0 of its call frame
→ Gets 'dog' instance
→ this.name = "Rex"
→ Prints "Rex says Woof!"
```

#### Why Not Store the Instance in the Closure?

Because **one method can be bound to different instances**:

```lox
class Dog {
    bark() { print this.name; }
}

var rex = Dog();
rex.name = "Rex";

var max = Dog();
max.name = "Max";

var rexBark = rex.bark;  // Bound to rex
var maxBark = max.bark;  // Bound to max

rexBark();  // "Rex"
maxBark();  // "Max"
```

```
ObjClass Dog:
┌─────────────────────────────────┐
│ methods:                        │
│   "bark" → ObjClosure (shared!) │
└─────────────────────────────────┘
                    ▲
                    │ (same closure)
        ┌───────────┴───────────┐
        │                       │
ObjBoundMethod              ObjBoundMethod
┌──────────────────┐       ┌──────────────────┐
│ receiver: rex    │       │ receiver: max    │
│ method: bark ────┼───┐   │ method: bark ────┼───┐
└──────────────────┘   │   └──────────────────┘   │
                       └───────────┬──────────────┘
                                   ▼
                          ObjClosure (bark)
                          (shared by all dogs!)
```

The **method code is shared** (one closure), but each bound method has a **different receiver**.

### Optimized Invocation (OP_INVOKE)

When you call a method directly (the common case), we skip creating a bound method:

```lox
dog.bark();  // Common case - direct call, no ObjBoundMethod created!
```

Instead of:
1. Create bound method
2. Push bound method
3. Call bound method
4. Bound method sets up `this`

We use `OP_INVOKE` which directly:
1. Look up method in class
2. Set up `this` to be the instance
3. Call the method

```c
case OP_INVOKE: {
    ObjString* method = READ_STRING();
    int argCount = READ_BYTE();
    if (!invoke(method, argCount)) {
        return INTERPRET_RUNTIME_ERROR;
    }
    frame = &vm.frames[vm.frameCount - 1];
    break;
}

static bool invoke(ObjString* name, int argCount) {
    Value receiver = peek(argCount);
    ObjInstance* instance = AS_INSTANCE(receiver);

    // Check if it's a field that happens to be callable
    Value value;
    if (tableGet(&instance->fields, name, &value)) {
        vm.stackTop[-argCount - 1] = value;
        return callValue(value, argCount);
    }

    // Call method directly from class - no bound method created!
    return invokeFromClass(instance->klass, name, argCount);
}
```

This optimization is faster because no intermediate ObjBoundMethod object is allocated!

```c
case OP_INVOKE: {
    ObjString* method = READ_STRING();
    int argCount = READ_BYTE();
    
    if (!invoke(method, argCount)) {
        return INTERPRET_RUNTIME_ERROR;
    }
    frame = &vm.frames[vm.frameCount - 1];
    break;
}

static bool invoke(ObjString* name, int argCount) {
    Value receiver = peek(argCount);
    ObjInstance* instance = AS_INSTANCE(receiver);
    
    // Check if it's a field (might be a stored function)
    Value value;
    if (tableGet(&instance->fields, name, &value)) {
        vm.stackTop[-argCount - 1] = value;
        return callValue(value, argCount);
    }
    
    // Call method directly from class
    return invokeFromClass(instance->klass, name, argCount);
}
```

### Inheritance

```lox
class Animal {
    speak() {
        print "Some sound";
    }
}

class Dog < Animal {
    speak() {
        print "Woof!";
    }
    
    fetch() {
        super.speak();  // Call parent's speak
        print "Fetching...";
    }
}
```

Inheritance copies methods:

```c
case OP_INHERIT: {
    Value superclass = peek(1);
    if (!IS_CLASS(superclass)) {
        runtimeError("Superclass must be a class.");
        return INTERPRET_RUNTIME_ERROR;
    }
    
    ObjClass* subclass = AS_CLASS(peek(0));
    // Copy all methods from superclass to subclass
    tableAddAll(&AS_CLASS(superclass)->methods, &subclass->methods);
    pop();  // Pop subclass
    break;
}
```

### Super Calls

`super` references are resolved at compile time through a synthetic local:

```c
static void classDeclaration() {
    // ...
    if (match(TOKEN_LESS)) {
        // Has superclass
        consume(TOKEN_IDENTIFIER, "Expect superclass name.");
        variable(false);  // Load superclass
        
        // Create scope for 'super'
        beginScope();
        addLocal(syntheticToken("super"));
        defineVariable(0);
        
        namedVariable(className, false);  // Load subclass
        emitByte(OP_INHERIT);
    }
    // ...
}
```

At runtime:

```c
case OP_GET_SUPER: {
    ObjString* name = READ_STRING();
    ObjClass* superclass = AS_CLASS(pop());
    
    if (!bindMethod(superclass, name)) {
        return INTERPRET_RUNTIME_ERROR;
    }
    break;
}

case OP_SUPER_INVOKE: {
    ObjString* method = READ_STRING();
    int argCount = READ_BYTE();
    ObjClass* superclass = AS_CLASS(pop());
    
    if (!invokeFromClass(superclass, method, argCount)) {
        return INTERPRET_RUNTIME_ERROR;
    }
    frame = &vm.frames[vm.frameCount - 1];
    break;
}
```

---

## Building and Running

### Compilation

```bash
cd src/lox
gcc -O2 -o clox *.c
```

### Debug Build

Enable debugging flags in `common.h`:

```c
#define NAN_BOXING           // Use optimized value representation
#define DEBUG_PRINT_CODE     // Disassemble bytecode after compilation
#define DEBUG_TRACE_EXECUTION // Trace each instruction during execution
#define DEBUG_STRESS_GC      // Run GC on every allocation (for testing)
#define DEBUG_LOG_GC         // Log GC operations
```

### Running

```bash
# REPL mode
./clox

# File mode
./clox path/to/script.lox
```

### Example Session

```
> var x = 10;
> var y = 20;
> print x + y;
30
> fun greet(name) { print "Hello, " + name + "!"; }
> greet("World");
Hello, World!
> class Counter {
    init() { this.count = 0; }
    inc() { this.count = this.count + 1; }
  }
> var c = Counter();
> c.inc();
> c.inc();
> print c.count;
2
```

---

## Summary

clox demonstrates a complete, production-quality language implementation featuring:

| Feature | Implementation |
|---------|----------------|
| **Parsing** | Pratt parsing for elegant precedence handling |
| **Values** | NaN boxing for compact 8-byte representation |
| **Execution** | Stack-based bytecode VM |
| **Memory** | Mark-and-sweep GC with tri-color abstraction |
| **Strings** | FNV-1a hashing with interning for O(1) equality |
| **Closures** | Upvalues with open/closed states |
| **Classes** | Methods, inheritance, super calls |

### Comparison: jlox vs clox

| Aspect | jlox | clox |
|--------|------|------|
| Language | Java | C |
| Execution | Tree-walk | Bytecode VM |
| Memory | GC (JVM) | Mark-and-sweep |
| Speed | Slower | ~10-100x faster |
| Code Complexity | Simpler | More complex |
| Value Representation | Java Objects | NaN boxing |
| Variable Lookup | Environment chain | Stack slots + upvalues |

### Key Concepts Interconnection

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        HOW EVERYTHING FITS TOGETHER                          │
└─────────────────────────────────────────────────────────────────────────────┘

Source Code        Scanner          Parser/Compiler        VM
    │                 │                   │                 │
    │   tokenize      │     parse +       │    execute      │
    ▼                 ▼     compile       ▼                 ▼
 "var x=1"  ──►  [VAR][ID][=][NUM] ──►  Bytecode  ──►  Stack Operations

                    HASH TABLES ARE USED THROUGHOUT:
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  vm.strings ─────► String interning (O(1) equality)                         │
│  vm.globals ─────► Global variable storage                                  │
│  class.methods ──► Method lookup for OOP                                    │
│  instance.fields ► Instance state storage                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

                    CLOSURES: ENCAPSULATION VIA UPVALUES
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  1. Compile time: Mark captured variables (isCaptured = true)              │
│  2. Runtime: Create upvalues pointing to stack slots                        │
│  3. When scope ends: Close upvalues (stack → heap migration)               │
│  4. Closure survives: Upvalue.closed preserves the value                   │
│                                                                             │
│     Stack ──────────────► Upvalue.closed (heap)                             │
│     (temporary)            (permanent)                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘

                    MEMORY: GC KEEPS EVERYTHING ALIVE
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  All Objects ←──────────────── vm.objects (linked list)                     │
│       ↓                                                                     │
│  Mark Phase: Start from roots (stack, globals, open upvalues)              │
│       ↓                                                                     │
│  Trace Phase: Follow references (closures → upvalues → values)             │
│       ↓                                                                     │
│  Sweep Phase: Free unmarked objects (including closed upvalues!)           │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Quick Reference: Variable Access Patterns

| Scenario | Compile-Time | Runtime Opcode | Storage |
|----------|--------------|----------------|---------|
| Local variable | Slot index known | `OP_GET_LOCAL` / `OP_SET_LOCAL` | Stack |
| Global variable | Name in constant pool | `OP_GET_GLOBAL` / `OP_SET_GLOBAL` | `vm.globals` hash table |
| Captured (still on stack) | Upvalue created | `OP_GET_UPVALUE` / `OP_SET_UPVALUE` | Stack (via upvalue.location) |
| Captured (closed) | Upvalue closed | `OP_GET_UPVALUE` / `OP_SET_UPVALUE` | Heap (upvalue.closed) |
| Instance field | Name in constant pool | `OP_GET_PROPERTY` / `OP_SET_PROPERTY` | `instance.fields` hash table |
| Method | Name in constant pool | `OP_INVOKE` / `OP_GET_PROPERTY` | `class.methods` hash table |

