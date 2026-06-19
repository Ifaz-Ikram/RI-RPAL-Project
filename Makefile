JAVAC = javac
SRCS  = Token.java ASTNode.java Environment.java Lexer.java \
        Parser.java Standardizer.java CSEMachine.java rpal20.java \
        RpalWebServer.java

.PHONY: all web clean

all:
	$(JAVAC) $(SRCS)

web: all
	java RpalWebServer

clean:
	rm -f *.class
