JAVAC = javac
SRCS  = Token.java ASTNode.java Environment.java Lexer.java \
        Parser.java Standardizer.java CSEMachine.java rpal20.java

.PHONY: all clean

all:
	$(JAVAC) $(SRCS)

clean:
	rm -f *.class
