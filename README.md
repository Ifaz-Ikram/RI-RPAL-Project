# RPAL Interpreter

An interpreter for the functional programming language **RPAL (Right-reference Pedagogical Algorithmic Language)**, implemented in Java. It processes RPAL source code by tokenizing input, parsing it into an Abstract Syntax Tree (AST), standardizing the tree, and evaluating expressions using a **CSE (Control Stack Environment)** machine.

---

## Project Overview

This project implements a complete RPAL interpreter pipeline with the following components:

1. **Lexer** (`Lexer.java`): Performs lexical analysis and converts RPAL source code into tokens.
2. **Parser** (`Parser.java`): Builds an Abstract Syntax Tree (AST) using recursive descent parsing.
3. **Standardizer** (`Standardizer.java`): Converts the AST into a Standardized Tree (ST).
4. **CSE Machine** (`CSEMachine.java`): Evaluates the standardized tree using the Control Stack Environment model.

---

## File Structure

| File | Description |
|------|-------------|
| `rpal20.java` | Main runner class and command-line entry point |
| `Token.java` | Token data class used by the lexer and parser |
| `Lexer.java` | Lexical analyzer for RPAL source files |
| `ASTNode.java` | AST/ST node class using left-child / right-sibling representation |
| `Parser.java` | Recursive descent parser for the RPAL grammar |
| `Standardizer.java` | AST to standardized tree transformer |
| `Environment.java` | Environment chain and variable binding structure |
| `CSEMachine.java` | Control Stack Environment evaluator |
| `Makefile` | Build automation for compiling Java source files |
| `README.md` | Project documentation |
| `REPORT.md` | Assignment report |
| `tests/` | RPAL test programs |

---

## Features

- Lexical analysis for RPAL tokens, keywords, operators, strings, and comments
- Recursive descent parser for AST generation
- AST visualization with the `-ast` flag
- Tree standardization for `let`, `where`, `within`, `and`, `rec`, function forms, lambdas, and `@`
- Evaluation using a CSE machine
- Support for:
  - Lambda expressions and closures
  - Recursive functions
  - Higher-order functions
  - Tuples and tuple operations
  - Conditional expressions
  - Built-in functions such as `Print`, `print`, `Order`, `Stem`, `Stern`, `Conc`, and `ItoS`

---

## Usage

Compile from the project root:

```bash
make
```

Run an RPAL program:

```bash
java rpal20 file.rpal
```

Print the AST without executing:

```bash
java rpal20 -ast file.rpal
```

Clean compiled class files:

```bash
make clean
```

---

## Examples

```bash
make
java rpal20 tests/hello.rpal
java rpal20 tests/factorial.rpal
java rpal20 tests/sum.rpal
java rpal20 -ast tests/test_tuples.rpal
```

---

## Sample Program

```rpal
let Sum(A) = Psum (A,Order A )
where rec Psum (T,N) = N eq 0 -> 0
| Psum(T,N-1)+T N
in Print ( Sum (1,2,3,4,5) )
```

Expected output:

```text
15
```

---

## Compilation Process

1. **Scanning**: Source code is tokenized by `Lexer.java`.
2. **Parsing**: Tokens are parsed into an AST by `Parser.java`.
3. **Standardization**: The AST is transformed into a standardized tree by `Standardizer.java`.
4. **Control Structure Generation**: The CSE machine builds delta control structures from the standardized tree.
5. **Execution**: `CSEMachine.java` evaluates the program using control, stack, and environment structures.

---

## Implementation Details

### Lexer

The lexer identifies identifiers, integers, strings, operators, keywords, and punctuation. It filters whitespace and `//` comments before returning tokens.

### Parser

The parser implements the RPAL grammar using recursive descent methods. It builds the AST with a stack and the left-child / right-sibling representation.

### Standardizer

The standardizer transforms high-level RPAL constructs into simpler functional forms using `gamma`, `lambda`, `tau`, and `YSTAR`.

### CSE Machine

The CSE machine uses:

- **Control Stack**: Instructions and control structures to execute
- **Value Stack**: Intermediate values and final results
- **Environment Chain**: Lexical scopes and variable bindings

---

## Testing

The `tests/` directory contains test programs covering language features such as variables, functions, recursion, tuples, strings, conditionals, built-ins, and tuple operations.

### Basic Tests

```bash
java rpal20 tests/hello.rpal
java rpal20 tests/factorial.rpal
java rpal20 tests/sum.rpal
```

### Additional Tests

```bash
java rpal20 tests/test_basic_let.rpal
java rpal20 tests/test_conditional.rpal
java rpal20 tests/test_factorial.rpal
java rpal20 tests/test_tuples.rpal
java rpal20 tests/test_vector_sum.rpal
java rpal20 tests/m_factorial_10.rpal
java rpal20 tests/m_palindrome_check.rpal
```

---

## Requirements

- Java Development Kit (JDK)
- `make`
- No third-party libraries

---

## Authors

Project done by:

- AHMEDH M.R.R. — 230027U
- IFAZ M.I.M. — 230253H
