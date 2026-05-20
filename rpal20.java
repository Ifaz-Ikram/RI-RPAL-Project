import java.util.List;

/**
 * rpal20 - Entry point for the RPAL interpreter.
 *
 * Usage:
 *   java rpal20 file_name         - run program
 *   java rpal20 -ast file_name    - print AST and exit
 */
public class rpal20 {

    public static void main(String[] args) {
        try {
            run(args);
        } catch (ArithmeticException e) {
            if ("/ by zero".equals(e.getMessage())) {
                System.err.print("Error: division by zero");
            } else {
                System.err.print("Error: " + e.getMessage());
            }
            System.exit(1);
        } catch (RuntimeException e) {
            System.err.print("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Execute the interpreter after argument validation.
     * @param args command-line arguments
     */
    private static void run(String[] args) {
        String filename = null;
        boolean astMode = false;

        if (args.length == 1) {
            filename = args[0];
        } else if (args.length == 2 && args[0].equals("-ast")) {
            astMode  = true;
            filename = args[1];
        } else {
            System.err.println("Usage: java rpal20 file_name");
            System.err.println("       java rpal20 -ast file_name");
            System.exit(1);
        }

        // Step 1: Lex
        Lexer lexer = new Lexer();
        List<Token> tokens = lexer.scan(filename);

        // Step 2: Parse
        Parser parser = new Parser(tokens);
        ASTNode root  = parser.parse();
        if (parser.errorExist) {
            System.err.println("Parsing failed.");
            System.exit(1);
        }

        // Step 3: Print AST if requested
        if (astMode) {
            parser.printAST(root, 0);
            return;
        }

        // Step 4: Standardize (10 passes for cascading transforms)
        Standardizer std = new Standardizer();
        for (int i = 0; i < 10; i++) {
            std.standardize(root);
        }

        // Step 5: Build control structures and execute
        CSEMachine cse = new CSEMachine();
        List<List<ASTNode>> deltas = cse.buildControlStructures(root);
        cse.run(deltas);
    }
}
