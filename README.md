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
| `RpalWebServer.java` | Lightweight HTTP server for the web interface |
| `Makefile` | Build automation for compiling Java source files |
| `README.md` | Project documentation |
| `web/index.html` | Web UI page structure |
| `web/style.css` | Web UI styling and responsive layout |
| `web/script.js` | Web UI behavior and API calls |
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

## CLI Usage

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

Print the standardized tree without executing:

```bash
java rpal20 -st file.rpal
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
java rpal20 -st tests/test_tuples.rpal
```

---

## Web UI

The project also includes a lightweight browser interface built with Java's built-in HTTP server and plain HTML, CSS, and JavaScript. It keeps the original interpreter logic unchanged by calling the existing `rpal20` CLI behind the scenes.

Compile the interpreter and web server from the project root:

```bash
make
```

Start the local web server:

```bash
java RpalWebServer
```

Or compile and start it with the Makefile shortcut:

```bash
make web
```

To use a different port:

```bash
java RpalWebServer 8081
```

Open the browser at:

```text
http://localhost:8080/#/editor
```

The web interface lets you:

- Type RPAL code directly in the editor
- Upload a `.rpal` file
- Load sample programs from the `tests/` folder
- Choose `Run Program`, `Show AST`, or `Show Standardized Tree`
- View stdout, errors, and the interpreter exit code
- Clear the editor/output or copy the output
- Inspect lexer tokens in a table with index, value, and token type
- Render AST and ST as interactive D3 visual trees
- Compare AST and Standardized Tree side by side
- Use the pipeline explanation cards to explain each interpreter stage during a demo
- Toggle light/dark mode and use `Ctrl + Enter` to run code
- Navigate dedicated app screens with browser back/forward support
- Keep editor code, selected mode, selected sample, and theme in local storage

### Web App Routes

RPAL Studio uses hash routing, so refreshing a screen or using browser back/forward keeps the current view:

| Route | Purpose |
|------|---------|
| `#/editor` | Online IDE editor, actions panel, upload, samples, quick stats |
| `#/run` | stdout, stderr, exit code, possible cause, copy/download output |
| `#/tree` | Full tree explorer with AST/ST switch, node inspector, search, fullscreen, raw tree drawer |
| `#/tokens` | Lexer token table, token type filter, search, token count cards |
| `#/compare` | Split-screen AST vs Standardized Tree comparison |
| `#/tests` | Searchable test browser with preview, Load, Run, AST, and ST actions |
| `#/pipeline` | Documentation/demo page explaining the interpreter pipeline |
| `#/settings` | Theme, shortcuts, backend summary, project info |

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl + Enter` | Run the current editor program |

The web interface sends requests to:

```text
POST /api/run
```

The request contains the source code and selected mode: `run`, `ast`, or `st`. The server writes the code to a temporary `.rpal` file, runs `java rpal20`, captures stdout/stderr/exit code, deletes the temporary file, and returns JSON to the browser.

Additional demo modes are also sent to the same endpoint:

- `tokens`: runs `Lexer.java` and returns a token list as JSON
- `compare`: returns both the AST JSON tree and the Standardized Tree JSON tree

### AST/ST Visualizer

For AST and ST modes, the backend converts the `ASTNode` left-child/right-sibling tree into JSON:

```json
{
  "label": "gamma",
  "type": "KEYWORD",
  "children": []
}
```

The frontend uses D3.js to render that JSON as an SVG tree. The visualizer supports zoom, pan, node expand/collapse, expand all, collapse all, fit to screen, reset view, node search, fullscreen mode, node inspection, and SVG download. Raw dot-indented tree output remains available in the Tree Explorer drawer.

### Tests From The UI

The Tests screen reads available `.rpal` files from the `tests/` folder through `/api/samples`. Search for a test, preview it, then use `Load`, `Run`, `AST`, or `ST`. Running a test loads it into the editor first, then navigates to the appropriate result screen.

---

## Screenshots

Screenshots are not included in this repository yet. Suggested screenshots for the project report:

- Web UI with sample RPAL code loaded
- Successful Run Program output
- AST mode output
- Standardized Tree mode output
- Tokens table
- AST vs ST comparison view
- Pipeline explanation cards
- Tree Explorer fullscreen view with node inspector
- Tests browser with preview panel
- Error panel showing invalid RPAL syntax

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

Compile before running tests:

```bash
make
```

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

## Troubleshooting

### Port already in use

If `java RpalWebServer` fails because port `8080` is already in use, start the server on another port:

```bash
java RpalWebServer 8081
```

Then open:

```text
http://localhost:8081
```

### Java not found

If `java` or `javac` is not found, install a Java Development Kit (JDK) and confirm it is on your `PATH`:

```bash
java -version
javac -version
```

### Make not found

If `make` is not available, compile directly with:

```bash
javac Token.java ASTNode.java Environment.java Lexer.java Parser.java Standardizer.java CSEMachine.java rpal20.java RpalWebServer.java
```

### Empty output

Some RPAL programs only produce visible output when they call `Print` or `print`. If the output panel is empty, confirm that the program prints a value and check the error panel for runtime or syntax errors.

### Invalid RPAL syntax

If the error panel shows a parse error, run AST mode with a smaller expression or compare the program with the examples in `tests/`. Syntax and runtime errors are shown in the web UI error panel and on stderr in CLI mode.

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
