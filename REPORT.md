# RPAL Interpreter — CS 3513 Programming Languages

## Student Information
- Student 1: [Name] | [Index Number]
- Student 2: [Name] | [Index Number]

## Project Overview
This project implements a complete RPAL interpreter in Java. It includes a hand-written lexer, recursive descent parser, AST standardizer, and Control-Stack-Environment (CSE) machine evaluator. The interpreter reads an RPAL source file, builds the AST, standardizes it into core RPAL forms, and evaluates the program using the CSE machine.

## Program Structure
- `rpal20.java` — program entry point; handles command-line arguments and runs lexer, parser, standardizer, and CSE machine.
- `Token.java` — token data class used by the lexer and parser.
- `Lexer.java` — lexical analyzer for RPAL source files.
- `ASTNode.java` — tree node class using left-child / right-sibling representation.
- `Parser.java` — recursive descent parser for the RPAL grammar.
- `Standardizer.java` — AST to standardized tree transformer.
- `Environment.java` — lexical environment chain for variable bindings.
- `CSEMachine.java` — evaluator implementing the RPAL CSE machine.

## Class/Function Prototypes

### Lexer.java
- `List<Token> scan(String filename)` — tokenizes RPAL source, filters whitespace/comments

### Parser.java
- `ASTNode parse()` — entry point, calls E()
- `void E()`, `void Ew()`, `void T()`, `void Ta()`, `void Tc()`, `void B()`,
  `void Bt()`, `void Bs()`, `void Bp()`, `void A()`, `void At()`, `void Af()`,
  `void Ap()`, `void R()`, `void Rn()`, `void D()`, `void Da()`, `void Dr()`,
  `void Db()`, `void Vb()`, `void Vl()`
- `void buildTree(String token, String type, int n)` — builds AST node from stack
- `void read(String expected, String type)` — consumes a token

### Standardizer.java
- `void standardize(ASTNode root)` — converts AST to ST (call 10 times)
- `void applyRule(ASTNode t)` — applies one standardization rule to node t
- Rules: let, where, within, and, rec, fcn_form, lambda, @

### CSEMachine.java
- `List<List<ASTNode>> buildControlStructures(ASTNode root)` — builds delta arrays
- `void run(List<List<ASTNode>> deltas)` — executes the CSE machine
- `void arrangeTuple(ASTNode node, List<ASTNode> result)` — flattens tau for printing
- `String processString(String s)` — handles \n \t escape sequences

### Environment.java
- `Map<String, List<ASTNode>> boundVar` — variable bindings
- `Environment prev` — parent environment chain

## Lexer Summary
Handles identifiers, integers, strings, operators, keywords, and punctuation. Whitespace and line comments are filtered before tokens are returned to the parser.

## Parser Summary
Recursive descent parser implementing the full RPAL grammar. It builds an AST using a stack and the left-child / right-sibling representation.

## Standardization Summary
Post-order tree transformation converting AST constructs such as let, where, rec, within, and function forms into standardized equivalents such as gamma, lambda, and YSTAR. Standardization is applied 10 times to handle cascading transformations.

## CSE Machine Summary
Implements the Control-Stack-Environment machine. It evaluates the standardized tree using a control stack, value stack, and environment chain. It handles lambda application, Y-combinator recursion, tuples, conditionals, tuple selection, tuple augmentation, and built-in functions.

## How to Compile and Run
```sh
make
java rpal20 <filename>
```
