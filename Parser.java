import java.util.List;
import java.util.Stack;

/**
 * Parser - Recursive descent parser for RPAL.
 * Builds an AST bottom-up using a stack and the left-child/right-sibling convention.
 */
public class Parser {

    private List<Token> tokens;
    private int index;
    private Token current;
    private Token prev;
    private Stack<ASTNode> stack;
    public boolean errorExist;

    public Parser(List<Token> tokens) {
        this.tokens    = tokens;
        this.index     = 0;
        this.stack     = new Stack<>();
        this.errorExist = false;
        this.current   = tokens.isEmpty() ? new Token("", "") : tokens.get(0);
        this.prev      = null;
    }

    // ── Token consumption ────────────────────────────────────────────────

    /**
     * Consume the current token.
     * If expectedValue is "UserDefined", any token value is accepted.
     */
    private void read(String expectedValue) {
        if (!expectedValue.equals("UserDefined") && !current.value.equals(expectedValue)) {
            System.err.println("Parse error: expected '" + expectedValue
                               + "' but got '" + current.value + "'");
            errorExist = true;
            return;
        }
        prev = current;
        index++;
        if (index < tokens.size()) {
            current = tokens.get(index);
        } else {
            current = new Token("", "");
        }
    }

    // ── Tree builder ─────────────────────────────────────────────────────

    /**
     * Pop n nodes from the stack, chain them as left-child/right-sibling,
     * attach as left child of a new parent node, then push the parent.
     */
    private void buildTree(String value, String type, int n) {
        ASTNode parent = new ASTNode(value, type);
        ASTNode head = null;
        for (int i = 0; i < n; i++) {
            ASTNode child = stack.pop();
            child.right = head;
            head = child;
        }
        parent.left = head;
        stack.push(parent);
    }

    // ── Entry point ───────────────────────────────────────────────────────

    /** Parse the token stream and return the root ASTNode. */
    public ASTNode parse() {
        E();
        if (stack.isEmpty()) {
            throw new RuntimeException("Parse produced no tree");
        }
        return stack.peek();
    }

    // ── Grammar rules ─────────────────────────────────────────────────────

    private void E() {
        if (current.value.equals("let")) {
            read("let"); D(); read("in"); E();
            buildTree("let", "KEYWORD", 2);
        } else if (current.value.equals("fn")) {
            read("fn");
            Vb();
            int n = 1;
            while (current.type.equals("<IDENTIFIER>") || current.value.equals("(")) {
                Vb(); n++;
            }
            read(".");
            E();
            buildTree("lambda", "KEYWORD", n + 1);
        } else {
            Ew();
        }
    }

    private void Ew() {
        T();
        if (current.value.equals("where")) {
            read("where"); Dr();
            buildTree("where", "KEYWORD", 2);
        }
    }

    private void T() {
        Ta();
        if (current.value.equals(",")) {
            read(","); Ta();
            int n = 1;
            while (current.value.equals(",")) {
                n++; read(","); Ta();
            }
            buildTree("tau", "KEYWORD", n + 1);
        }
    }

    private void Ta() {
        Tc();
        while (current.value.equals("aug")) {
            read("aug"); Tc();
            buildTree("aug", "KEYWORD", 2);
        }
    }

    private void Tc() {
        B();
        if (current.value.equals("->")) {
            read("->"); Tc(); read("|"); Tc();
            buildTree("->", "KEYWORD", 3);
        }
    }

    private void B() {
        Bt();
        while (current.value.equals("or")) {
            read("or"); Bt();
            buildTree("or", "KEYWORD", 2);
        }
    }

    private void Bt() {
        Bs();
        while (current.value.equals("&")) {
            read("&"); Bs();
            buildTree("&", "KEYWORD", 2);
        }
    }

    private void Bs() {
        if (current.value.equals("not")) {
            read("not"); Bp();
            buildTree("not", "KEYWORD", 1);
        } else {
            Bp();
        }
    }

    private void Bp() {
        A();
        if (current.value.equals("gr") || current.value.equals(">")) {
            read(current.value); A(); buildTree("gr", "KEYWORD", 2);
        } else if (current.value.equals("ge") || current.value.equals(">=")) {
            read(current.value); A(); buildTree("ge", "KEYWORD", 2);
        } else if (current.value.equals("ls") || current.value.equals("<")) {
            read(current.value); A(); buildTree("ls", "KEYWORD", 2);
        } else if (current.value.equals("le") || current.value.equals("<=")) {
            read(current.value); A(); buildTree("le", "KEYWORD", 2);
        } else if (current.value.equals("eq")) {
            read("eq"); A(); buildTree("eq", "KEYWORD", 2);
        } else if (current.value.equals("ne")) {
            read("ne"); A(); buildTree("ne", "KEYWORD", 2);
        }
    }

    private void A() {
        if (current.value.equals("+")) {
            read("+"); At();
        } else if (current.value.equals("-")) {
            read("-"); At(); buildTree("neg", "KEYWORD", 1);
        } else {
            At();
            while (current.value.equals("+") || current.value.equals("-")) {
                String op = current.value;
                read(op); At();
                buildTree(op, "OPERATOR", 2);
            }
        }
    }

    private void At() {
        Af();
        while (current.value.equals("*") || current.value.equals("/")) {
            String op = current.value;
            read(op); Af();
            buildTree(op, "OPERATOR", 2);
        }
    }

    private void Af() {
        Ap();
        if (current.value.equals("**")) {
            read("**"); Af();
            buildTree("**", "KEYWORD", 2);
        }
    }

    private void Ap() {
        R();
        while (current.value.equals("@")) {
            read("@");
            read("UserDefined");
            buildTree(prev.value, "ID", 0);
            R();
            buildTree("@", "KEYWORD", 3);
        }
    }

    private void R() {
        Rn();
        while (current.type.equals("<IDENTIFIER>") || current.type.equals("<INTEGER>")
               || current.type.equals("<STRING>")
               || current.value.equals("true") || current.value.equals("false")
               || current.value.equals("nil")  || current.value.equals("(")
               || current.value.equals("dummy")) {
            Rn();
            buildTree("gamma", "KEYWORD", 2);
        }
    }

    private void Rn() {
        if (current.type.equals("<IDENTIFIER>")) {
            read("UserDefined"); buildTree(prev.value, "ID", 0);
        } else if (current.type.equals("<INTEGER>")) {
            read("UserDefined"); buildTree(prev.value, "INT", 0);
        } else if (current.type.equals("<STRING>")) {
            read("UserDefined"); buildTree(prev.value, "STR", 0);
        } else if (current.value.equals("true")) {
            read("true"); buildTree("true", "BOOL", 0);
        } else if (current.value.equals("false")) {
            read("false"); buildTree("false", "BOOL", 0);
        } else if (current.value.equals("nil")) {
            read("nil"); buildTree("nil", "NIL", 0);
        } else if (current.value.equals("dummy")) {
            read("dummy"); buildTree("dummy", "DUMMY", 0);
        } else if (current.value.equals("(")) {
            read("("); E(); read(")");
        }
    }

    private void D() {
        Da();
        while (current.value.equals("within")) {
            read("within"); D();
            buildTree("within", "KEYWORD", 2);
        }
    }

    private void Da() {
        Dr();
        int n = 0;
        while (current.value.equals("and")) {
            read("and"); Dr(); n++;
        }
        if (n > 0) buildTree("and", "KEYWORD", n + 1);
    }

    private void Dr() {
        if (current.value.equals("rec")) {
            read("rec"); Db();
            buildTree("rec", "KEYWORD", 1);
        } else {
            Db();
        }
    }

    private void Db() {
        if (current.value.equals("(")) {
            read("("); D(); read(")");
            return;
        }
        if (current.type.equals("<IDENTIFIER>")) {
            Vl();
            if (current.value.equals("=")) {
                read("="); E();
                buildTree("=", "KEYWORD", 2);
            } else {
                // fcn_form: the Vl() above already consumed the function name as ID
                // We need additional Vb's for parameters
                Vb();
                int n = 1;
                while (current.type.equals("<IDENTIFIER>") || current.value.equals("(")) {
                    Vb(); n++;
                }
                read("="); E();
                buildTree("fcn_form", "KEYWORD", n + 2);
            }
        }
    }

    private void Vb() {
        if (current.type.equals("<IDENTIFIER>")) {
            read("UserDefined"); buildTree(prev.value, "ID", 0);
        } else if (current.value.equals("(")) {
            read("(");
            if (current.type.equals("<IDENTIFIER>")) {
                Vl(); read(")");
            } else {
                read(")"); buildTree("()", "KEYWORD", 0);
            }
        }
    }

    private void Vl() {
        read("UserDefined"); buildTree(prev.value, "ID", 0);
        int n = 0;
        while (current.value.equals(",")) {
            read(","); read("UserDefined"); buildTree(prev.value, "ID", 0); n++;
        }
        if (n > 0) buildTree(",", "KEYWORD", n + 1);
    }

    // ── AST printing (for -ast flag) ──────────────────────────────────────

    /**
     * Pre-order traversal printing the AST.
     * @param node  current node
     * @param depth indentation level
     */
    public void printAST(ASTNode node, int depth) {
        if (node == null) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < depth; i++) sb.append('.');
        String t = node.type;
        if (t.equals("ID") || t.equals("STR") || t.equals("INT")) {
            sb.append("<").append(t).append(":").append(node.value).append(">");
        } else if (t.equals("BOOL") || t.equals("NIL") || t.equals("DUMMY")) {
            sb.append("<").append(node.value).append(">");
        } else {
            sb.append(node.value);
        }
        System.out.println(sb);
        printAST(node.left,  depth + 1);
        printAST(node.right, depth);
    }
}
