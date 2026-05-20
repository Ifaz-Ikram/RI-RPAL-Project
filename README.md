# Java RPAL Interpreter

This project implements an RPAL interpreter in Java using a hand-written lexer,
recursive descent parser, AST standardizer, and CSE machine evaluator.

## Build

```sh
make
```

## Run

```sh
java rpal20 file_name
```

To print the raw AST for debugging:

```sh
java rpal20 -ast file_name
```

## Tests

```sh
java rpal20 tests/hello.rpal
java rpal20 tests/factorial.rpal
java rpal20 tests/sum.rpal
```
