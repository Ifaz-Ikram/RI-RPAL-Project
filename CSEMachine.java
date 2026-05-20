import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CSEMachine - Evaluates a standardized RPAL tree.
 *
 * Phase A: buildControlStructures — walks the ST and produces a list of deltas.
 * Phase B: run                   — executes the deltas using three stacks.
 *
 * Lambda representation on the value stack (always 4 consecutive items,
 * bottom-to-top order):  deltaIndex | boundVar | envNode | lambdaMarker
 */
public class CSEMachine {

    // ── Phase A: build control structures ────────────────────────────────

    private List<List<ASTNode>> deltas;
    private int nextDeltaIdx;

    /**
     * Walk the standardized tree and produce control structures.
     * @param root standardized tree root
     * @return list of deltas; delta 0 is the main program
     */
    public List<List<ASTNode>> buildControlStructures(ASTNode root) {
        deltas       = new ArrayList<>();
        nextDeltaIdx = 0;

        // Create delta 0
        deltas.add(new ArrayList<>());
        nextDeltaIdx = 1;

        fillDelta(root, 0);
        return deltas;
    }

    /**
     * Recursively fill delta[deltaIdx] by walking the subtree rooted at node.
     */
    private void fillDelta(ASTNode node, int deltaIdx) {
        if (node == null) return;

        if (node.value.equals("lambda")) {
            // Create a new delta for the lambda body
            int bodyIdx = nextDeltaIdx++;
            deltas.add(new ArrayList<>());

            // Store placeholder in current delta: [deltaNumber, boundVar, lambdaMarker]
            deltas.get(deltaIdx).add(new ASTNode(String.valueOf(bodyIdx), "deltaNumber"));
            deltas.get(deltaIdx).add(node.left);           // bound variable (Vb)
            deltas.get(deltaIdx).add(new ASTNode("lambda", "KEYWORD"));

            // Body is node.left.right
            fillDelta(node.left.right, bodyIdx);

        } else if (node.value.equals("->")) {
            // Conditional: condition goes into current delta;
            // then-branch and else-branch get new deltas.
            int thenIdx = nextDeltaIdx++;
            int elseIdx = nextDeltaIdx++;
            deltas.add(new ArrayList<>());
            deltas.add(new ArrayList<>());

            // [thenIdx, elseIdx, beta] stored in current delta FIRST,
            // then the condition nodes fill into the current delta.
            deltas.get(deltaIdx).add(new ASTNode(String.valueOf(thenIdx), "deltaNumber"));
            deltas.get(deltaIdx).add(new ASTNode(String.valueOf(elseIdx), "deltaNumber"));
            deltas.get(deltaIdx).add(new ASTNode("beta", "beta"));

            // condition = node.left  → stays in current delta
            fillDelta(node.left, deltaIdx);

            // then = node.left.right
            fillDelta(node.left.right, thenIdx);

            // else = node.left.right.right
            fillDelta(node.left.right.right, elseIdx);

        } else if (node.value.equals("tau")) {
            // Count tau children
            int count = 0;
            ASTNode child = node.left;
            while (child != null) { count++; child = child.right; }

            deltas.get(deltaIdx).add(new ASTNode(String.valueOf(count), "CHILDCOUNT"));
            deltas.get(deltaIdx).add(new ASTNode("tau", "tau"));

            // Store tuple children right-to-left so stack execution evaluates
            // them left-to-right. This matters for side-effecting Print calls.
            List<ASTNode> children = new ArrayList<>();
            child = node.left;
            while (child != null) {
                children.add(child);
                child = child.right;
            }
            for (int i = children.size() - 1; i >= 0; i--) {
                fillDelta(children.get(i), deltaIdx);
            }

        } else {
            // Regular node: store a copy in current delta, then recurse into children
            deltas.get(deltaIdx).add(new ASTNode(node.value, node.type));
            fillDelta(node.left, deltaIdx);
            if (node.left != null) {
                fillDelta(node.left.right, deltaIdx);
            }
        }
    }

    // ── Phase B: CSE machine execution ───────────────────────────────────

    private List<List<ASTNode>> controlStructures;
    private Deque<ASTNode>      control;   // control stack
    private Deque<ASTNode>      valStack;  // value stack
    private Deque<Environment>  envStack;  // environment stack
    private Map<String, Environment> environments;
    private Environment         currEnv;
    private int                 envCounter;

    /**
     * Execute the control structures produced by buildControlStructures.
     * @param deltas list of deltas from buildControlStructures
     */
    public void run(List<List<ASTNode>> deltas) {
        this.controlStructures = deltas;
        control    = new ArrayDeque<>();
        valStack   = new ArrayDeque<>();
        envStack   = new ArrayDeque<>();
        environments = new HashMap<>();
        envCounter = 1;

        // Initial environment env0
        currEnv      = new Environment();
        currEnv.name = "env0";
        envStack.push(currEnv);
        environments.put(currEnv.name, currEnv);

        // Push env0 marker onto both stacks
        ASTNode env0Node = new ASTNode("env0", "ENV");
        control.push(env0Node);
        valStack.push(new ASTNode("env0", "ENV"));

        // Push delta 0 in source order. Deque#push makes the last inserted item
        // execute first, which is what the prefix control structure needs.
        List<ASTNode> delta0 = deltas.get(0);
        for (ASTNode node : delta0) {
            control.push(node);
        }

        // Main loop
        while (!control.isEmpty()) {
            ASTNode token = control.pop();
            step(token);
        }
    }

    /** Execute one CSE machine step for the given token. */
    private void step(ASTNode token) {
        String v = token.value;
        String t = token.type;

        // ── Push values directly onto valStack ──
        if (isValue(token)) {
            if (v.equals("lambda")) {
                // Consume: boundVar, deltaIdx from control; then package as 4-item closure.
                ASTNode boundVar = control.pop();
                ASTNode deltaIdx = control.pop();
                ASTNode envNode  = new ASTNode(currEnv.name, "ENV");
                valStack.push(deltaIdx);
                valStack.push(boundVar);
                valStack.push(envNode);
                valStack.push(new ASTNode(token.value, token.type)); // lambda marker on top
            } else {
                valStack.push(copyTree(token));
            }
            return;
        }

        if (t.equals("PUSH")) {
            valStack.push(copyTree(token.left));
            return;
        }

        if (t.equals("THUNK")) {
            ASTNode thunk = copyTree(token);
            thunk.right = new ASTNode(currEnv.name, "ENV");
            valStack.push(thunk);
            return;
        }

        // ── nil → treat as empty tuple ──
        if (v.equals("nil")) {
            ASTNode nilNode = new ASTNode("nil", "tau");
            valStack.push(nilNode);
            return;
        }

        // ── gamma (function application) ──
        if (v.equals("gamma")) {
            applyGamma();
            return;
        }

        // ── environment marker (CSE Rule 5) ──
        if (t.equals("ENV") || v.startsWith("env")) {
            restoreEnv(token);
            return;
        }

        // ── variable lookup ──
        if (t.equals("ID") && !isBuiltin(v)) {
            lookupVar(v);
            return;
        }

        // ── binary / unary operators ──
        if (isBinaryOp(v)) { applyBinary(v); return; }
        if (v.equals("neg") || v.equals("not")) { applyUnary(v); return; }

        // ── beta (conditional branch) ──
        if (v.equals("beta")) {
            applyBeta();
            return;
        }

        // ── tau (tuple construction) ──
        if (v.equals("tau")) {
            ASTNode countNode = control.pop();
            int n = Integer.parseInt(countNode.value);
            buildTau(n);
            return;
        }

        // ── aug (tuple augmentation) ──
        if (v.equals("aug")) {
            applyAug();
            return;
        }

        // ── ItoS (integer to string) ──
        if (v.equals("ItoS")) {
            ASTNode num = valStack.pop();
            valStack.push(new ASTNode(num.value, "STR"));
            return;
        }
    }

    // ── isValue: tokens that go straight onto valStack ───────────────────

    private boolean isValue(ASTNode n) {
        String v = n.value, t = n.type;
        if (t.equals("INT") || t.equals("STR") || t.equals("BOOL")
                || t.equals("NIL") || t.equals("DUMMY")) return true;
        if (v.equals("lambda") || v.equals("YSTAR")) return true;
        if (v.equals("Print") || v.equals("print")) return true;
        if (v.equals("Isinteger") || v.equals("Istruthvalue") || v.equals("Isstring")
                || v.equals("Istuple") || v.equals("Isfunction") || v.equals("Isdummy")
                || v.equals("Isempty") || v.equals("Null")) return true;
        if (v.equals("Stem") || v.equals("Stern") || v.equals("Conc")
                || v.equals("Order") || v.equals("order") || v.equals("ItoS")) return true;
        return false;
    }

    private boolean isBuiltin(String v) {
        return v.equals("Print") || v.equals("print")
            || v.equals("Isinteger") || v.equals("Istruthvalue") || v.equals("Isstring")
            || v.equals("Istuple")   || v.equals("Isfunction")   || v.equals("Isdummy")
            || v.equals("Isempty")   || v.equals("Null")
            || v.equals("Stem")      || v.equals("Stern")         || v.equals("Conc")
            || v.equals("Order")     || v.equals("order")         || v.equals("ItoS");
    }

    // ── gamma application ─────────────────────────────────────────────────

    private void applyGamma() {
        ASTNode top = valStack.peek();

        if (top.value.equals("lambda")) {
            applyLambda();
        } else if (top.value.equals("Conc") && top.left != null) {
            applyConcSecond(valStack.pop());
        } else if (top.value.equals("tau")) {
            applyTupleSelect();
        } else if (top.value.equals("YSTAR")) {
            applyYStar();
        } else if (top.value.equals("eta")) {
            applyEta();
        } else {
            // Built-in function
            applyBuiltin(top);
        }
    }

    // CSE Rule 4: apply lambda closure
    private void applyLambda() {
        valStack.pop(); // lambda marker
        ASTNode envNode  = valStack.pop();
        ASTNode boundVar = valStack.pop();
        ASTNode deltaIdx = valStack.pop();

        // The argument is whatever is now on top of valStack
        Environment newEnv = new Environment();
        newEnv.name = "env" + envCounter++;
        newEnv.prev = findEnv(envNode.value);
        environments.put(newEnv.name, newEnv);

        bindArgument(boundVar, newEnv);

        currEnv = newEnv;
        envStack.push(newEnv);
        control.push(new ASTNode(newEnv.name, "ENV"));
        valStack.push(new ASTNode(newEnv.name, "ENV"));

        int idx = Integer.parseInt(deltaIdx.value);
        List<ASTNode> delta = controlStructures.get(idx);
        for (ASTNode node : delta) {
            control.push(node);
        }
    }

    /**
     * Bind the argument on top of valStack to boundVar in newEnv.
     * Handles tuple destructuring, lambda args, Conc partial, eta, and simple values.
     */
    private void bindArgument(ASTNode boundVar, Environment newEnv) {
        List<ASTNode> binding = new ArrayList<>();

        if (boundVar.value.equals(",") && valStack.peek().value.equals("tau")) {
            // Tuple destructuring: bind each comma-var to corresponding tau element
            ASTNode tau = valStack.pop();
            ASTNode commaVar = boundVar.left;
            ASTNode tauChild = tau.left;
            while (commaVar != null && tauChild != null) {
                List<ASTNode> single = new ArrayList<>();
                single.add(copyTree(tauChild));
                newEnv.boundVar.put(commaVar.value, single);
                commaVar = commaVar.right;
                tauChild = tauChild.right;
            }

        } else if (valStack.peek().value.equals("lambda") || valStack.peek().value.equals("eta")) {
            // Argument is a closure (4 items on stack)
            List<ASTNode> tmp = new ArrayList<>();
            for (int i = 0; i < 4; i++) tmp.add(0, copyTree(valStack.pop())); // reverse order
            if (tmp.get(3).value.equals("eta")) {
                tmp.set(2, new ASTNode(newEnv.name, "ENV"));
            }
            newEnv.boundVar.put(boundVar.value, tmp);

        } else if (valStack.peek().value.equals("Conc") && valStack.peek().left != null) {
            // Partial Conc application
            List<ASTNode> tmp = new ArrayList<>();
            tmp.add(copyTree(valStack.pop())); // Conc node (with .left set)
            tmp.add(copyTree(valStack.pop())); // first string
            newEnv.boundVar.put(boundVar.value, tmp);

        } else {
            // Simple value
            binding.add(copyTree(valStack.pop()));
            newEnv.boundVar.put(boundVar.value, binding);
        }
    }

    // CSE Rule 10: tuple selection  (tau on stack, index below it)
    private void applyTupleSelect() {
        ASTNode tau = valStack.pop();
        ASTNode idxNode = valStack.pop();
        int n = Integer.parseInt(idxNode.value); // 1-based

        ASTNode child = tau.left;
        for (int i = 1; i < n && child != null; i++) {
            child = child.right;
        }
        if (child == null) throw new RuntimeException("Tuple index out of range: " + n);

        if (child.value.equals("lamdaTuple")) {
            // Unpack lambda tuple: push items left-to-right
            ASTNode cur = child.left;
            while (cur != null) {
                valStack.push(new ASTNode(cur.value, cur.type));
                cur = cur.right;
            }
        } else {
            valStack.push(new ASTNode(child.value, child.type));
        }
    }

    // CSE Rule 12: YSTAR applied to lambda → convert to eta
    private void applyYStar() {
        valStack.pop(); // YSTAR
        if (!valStack.peek().value.equals("lambda")) return;

        ASTNode lambdaMark = valStack.pop();
        lambdaMark.value   = "eta"; // repurpose
        ASTNode envNode  = valStack.pop();
        ASTNode boundVar = valStack.pop();
        ASTNode deltaIdx = valStack.pop();

        valStack.push(deltaIdx);
        valStack.push(boundVar);
        valStack.push(envNode);
        valStack.push(lambdaMark); // now "eta"
    }

    // CSE Rule 13: eta (fixed point) — push duplicated gamma applications
    private void applyEta() {
        ASTNode eta      = valStack.pop();
        ASTNode envNode  = valStack.pop();
        ASTNode boundVar = valStack.pop();
        ASTNode deltaIdx = valStack.pop();

        // Push eta back
        valStack.push(deltaIdx);
        valStack.push(boundVar);
        valStack.push(envNode);
        valStack.push(eta);

        // Push a lambda copy
        valStack.push(new ASTNode(deltaIdx.value, deltaIdx.type));
        valStack.push(new ASTNode(boundVar.value, boundVar.type));
        valStack.push(new ASTNode(envNode.value,  envNode.type));
        valStack.push(new ASTNode("lambda", "KEYWORD"));

        // Two gammas on control
        control.push(new ASTNode("gamma", "KEYWORD"));
        control.push(new ASTNode("gamma", "KEYWORD"));
    }

    // ── Built-in function dispatch ────────────────────────────────────────

    private void applyBuiltin(ASTNode top) {
        String fn = top.value;

        if (fn.equals("Print") || fn.equals("print")) {
            valStack.pop(); // pop Print token
            printValue(valStack.peek()); // peek, do NOT pop the value
            return;
        }

        valStack.pop(); // consume the function token

        if (fn.equals("Isinteger")) {
            ASTNode arg = valStack.pop();
            valStack.push(new ASTNode(arg.type.equals("INT") ? "true" : "false", "BOOL"));
        } else if (fn.equals("Isstring")) {
            ASTNode arg = valStack.pop();
            valStack.push(new ASTNode(arg.type.equals("STR") ? "true" : "false", "BOOL"));
        } else if (fn.equals("Istruthvalue")) {
            ASTNode arg = valStack.pop();
            boolean b = arg.value.equals("true") || arg.value.equals("false");
            valStack.push(new ASTNode(b ? "true" : "false", "BOOL"));
        } else if (fn.equals("Istuple")) {
            ASTNode arg = valStack.pop();
            valStack.push(new ASTNode(arg.type.equals("tau") ? "true" : "false", "BOOL"));
        } else if (fn.equals("Isfunction")) {
            // peek only
            ASTNode arg = valStack.peek();
            valStack.push(new ASTNode(arg.value.equals("lambda") ? "true" : "false", "BOOL"));
        } else if (fn.equals("Isdummy")) {
            ASTNode arg = valStack.peek();
            valStack.push(new ASTNode(arg.value.equals("dummy") ? "true" : "false", "BOOL"));
        } else if (fn.equals("Isempty") || fn.equals("Null")) {
            ASTNode arg = valStack.pop();
            boolean empty = arg.value.equals("nil") || (arg.type.equals("tau") && arg.left == null);
            valStack.push(new ASTNode(empty ? "true" : "false", "BOOL"));
        } else if (fn.equals("Stem")) {
            ASTNode arg = valStack.pop();
            String s = arg.value;
            valStack.push(new ASTNode(s.isEmpty() ? "" : String.valueOf(s.charAt(0)), "STR"));
        } else if (fn.equals("Stern")) {
            ASTNode arg = valStack.pop();
            String s = arg.value;
            valStack.push(new ASTNode(s.isEmpty() ? "" : s.substring(1), "STR"));
        } else if (fn.equals("Conc")) {
            // Curried: first call stores the first string on a Conc node
            ASTNode firstStr = valStack.pop();
            // Put Conc back with first string attached
            ASTNode concNode = new ASTNode("Conc", "ID");
            concNode.left = firstStr;
            valStack.push(concNode);
            // Next gamma will finish it
        } else if (fn.equals("Order") || fn.equals("order")) {
            ASTNode tau = valStack.pop();
            int count = 0;
            if (!tau.value.equals("nil")) {
                ASTNode child = tau.left;
                while (child != null) { count++; child = child.right; }
            }
            valStack.push(new ASTNode(String.valueOf(count), "INT"));
        } else if (fn.equals("ItoS")) {
            ASTNode num = valStack.pop();
            valStack.push(new ASTNode(num.value, "STR"));
        }
        // Conc second call: top is a Conc with .left set
        else if (top.value.equals("Conc") && top.left != null) {
            // This path is hit when gamma is applied to a partial Conc
            // top was already consumed above — re-handle below
        }
    }

    // Conc second application (partial Conc node sitting on top of stack)
    private void applyConcSecond(ASTNode concNode) {
        // concNode.left = firstString
        ASTNode second = valStack.pop();
        String result = concNode.left.value + second.value;
        valStack.push(new ASTNode(result, "STR"));
    }

    // ── Environment restoration (CSE Rule 5) ─────────────────────────────

    private void restoreEnv(ASTNode envToken) {
        // Save the result on top of valStack
        List<ASTNode> saved = new ArrayList<>();
        ASTNode top = valStack.peek();
        if (top.value.equals("lambda") || top.value.equals("eta")) {
            for (int i = 0; i < 4; i++) saved.add(0, valStack.pop());
        } else {
            saved.add(valStack.pop());
        }

        // Remove the matching env marker from the value stack
        ASTNode envMarker = valStack.peek();
        if (envToken.value.equals(envMarker.value)) {
            valStack.pop();
        }

        // Restore previous environment
        envStack.pop();
        currEnv = envStack.isEmpty() ? null : envStack.peek();

        // Put saved values back in bottom-to-top order. This preserves the
        // invariant that a closure has lambda/eta as the stack top.
        for (ASTNode node : saved) {
            valStack.push(node);
        }
    }

    // ── Variable lookup ───────────────────────────────────────────────────

    private void lookupVar(String name) {
        List<ASTNode> binding = currEnv.lookup(name);

        // Special case: partial Conc stored in env
        if (binding.size() == 1 && binding.get(0).value.equals("Conc")
                && binding.get(0).left != null) {
            // push string then Conc so next gamma completes it
            valStack.push(copyTree(binding.get(0).left));
            valStack.push(copyTree(binding.get(0)));
            control.push(new ASTNode("gamma", "KEYWORD"));
            return;
        }

        for (ASTNode n : binding) {
            if (n.value.equals("lamdaTuple")) {
                ASTNode child = n.left;
                while (child != null) {
                    valStack.push(copyTree(child));
                    child = child.right;
                }
            } else {
                valStack.push(copyTree(n));
            }
        }
    }

    /**
     * Create a delayed argument token for normal-order function application.
     * @param expression argument expression subtree
     * @return thunk token carrying the expression
     */
    private ASTNode makeThunk(ASTNode expression) {
        ASTNode thunk = new ASTNode("thunk", "THUNK");
        thunk.left = copyTree(expression);
        if (thunk.left != null) thunk.left.right = null;
        return thunk;
    }

    /** Create a control token that pushes an already evaluated stack value. */
    private ASTNode pushValue(ASTNode value) {
        ASTNode node = new ASTNode("push", "PUSH");
        node.left = copyTree(value);
        return node;
    }

    /** Check whether a stack node is a delayed argument. */
    private boolean isThunk(ASTNode node) {
        return node != null && node.type.equals("THUNK");
    }

    /** Return the second node on the value stack, or null if absent. */
    private ASTNode secondOnStack() {
        int i = 0;
        for (ASTNode node : valStack) {
            if (i == 1) return node;
            i++;
        }
        return null;
    }

    /**
     * Schedule a thunk expression for evaluation in its captured environment.
     * The current pending control item resumes after the thunk result is pushed.
     */
    private void forceThunk(ASTNode thunk) {
        String envName = thunk.right == null ? currEnv.name : thunk.right.value;
        Environment thunkEnv = findEnv(envName);
        currEnv = thunkEnv;
        envStack.push(thunkEnv);

        ASTNode envMarker = new ASTNode(thunkEnv.name, "ENV");
        valStack.push(envMarker);
        control.push(envMarker);

        int deltaIdx = controlStructures.size();
        controlStructures.add(new ArrayList<>());
        nextDeltaIdx = controlStructures.size();
        fillDelta(thunk.left, deltaIdx);
        List<ASTNode> delta = controlStructures.get(deltaIdx);
        for (ASTNode node : delta) {
            control.push(node);
        }
    }

    // ── Operators ─────────────────────────────────────────────────────────

    private boolean isBinaryOp(String op) {
        switch (op) {
            case "+": case "-": case "*": case "/": case "**":
            case "gr": case "ge": case "ls": case "le":
            case "eq": case "ne":
            case ">": case ">=": case "<": case "<=":
            case "or": case "&":
                return true;
        }
        return false;
    }

    private void applyBinary(String op) {
        ASTNode n1 = valStack.pop();
        ASTNode n2 = valStack.pop();

        if (n1.type.equals("INT") && n2.type.equals("INT")) {
            int a = Integer.parseInt(n1.value);
            int b = Integer.parseInt(n2.value);
            switch (op) {
                case "+":  valStack.push(new ASTNode(String.valueOf(a + b), "INT")); break;
                case "-":  valStack.push(new ASTNode(String.valueOf(a - b), "INT")); break;
                case "*":  valStack.push(new ASTNode(String.valueOf(a * b), "INT")); break;
                case "/":  valStack.push(new ASTNode(String.valueOf(a / b), "INT")); break;
                case "**": valStack.push(new ASTNode(String.valueOf((int)Math.pow(a, b)), "INT")); break;
                case "gr": case ">":  valStack.push(boolNode(a > b));  break;
                case "ge": case ">=": valStack.push(boolNode(a >= b)); break;
                case "ls": case "<":  valStack.push(boolNode(a < b));  break;
                case "le": case "<=": valStack.push(boolNode(a <= b)); break;
                case "eq":            valStack.push(boolNode(a == b)); break;
                case "ne":            valStack.push(boolNode(a != b)); break;
                default: throw new RuntimeException("Unknown INT op: " + op);
            }
        } else if (n1.type.equals("STR") && n2.type.equals("STR")) {
            switch (op) {
                case "eq": valStack.push(boolNode(n1.value.equals(n2.value)));  break;
                case "ne": valStack.push(boolNode(!n1.value.equals(n2.value))); break;
                default: throw new RuntimeException("STR op not supported: " + op);
            }
        } else {
            // Boolean operations
            boolean b1 = n1.value.equals("true");
            boolean b2 = n2.value.equals("true");
            switch (op) {
                case "or":  valStack.push(boolNode(b1 || b2)); break;
                case "&":   valStack.push(boolNode(b1 && b2)); break;
                case "eq":  valStack.push(boolNode(n1.value.equals(n2.value)));  break;
                case "ne":  valStack.push(boolNode(!n1.value.equals(n2.value))); break;
                default: throw new RuntimeException("Unsupported op: " + op
                             + " on " + n1.type + ", " + n2.type);
            }
        }
    }

    private void applyUnary(String op) {
        ASTNode n = valStack.pop();
        if (op.equals("neg")) {
            valStack.push(new ASTNode(String.valueOf(-Integer.parseInt(n.value)), "INT"));
        } else { // not
            valStack.push(boolNode(n.value.equals("false")));
        }
    }

    private ASTNode boolNode(boolean b) {
        return new ASTNode(b ? "true" : "false", "BOOL");
    }

    // ── Beta (conditional) ────────────────────────────────────────────────

    private void applyBeta() {
        ASTNode boolVal  = valStack.pop();
        ASTNode elseIdx  = control.pop();
        ASTNode thenIdx  = control.pop();

        int idx = boolVal.value.equals("true")
                ? Integer.parseInt(thenIdx.value)
                : Integer.parseInt(elseIdx.value);
        List<ASTNode> delta = controlStructures.get(idx);
        for (ASTNode node : delta) {
            control.push(node);
        }
    }

    // ── Tau (tuple construction) ──────────────────────────────────────────

    private void buildTau(int n) {
        ASTNode tau = new ASTNode("tau", "tau");
        ASTNode head = null;

        for (int i = 0; i < n; i++) {
            ASTNode item;
            if (valStack.peek().value.equals("lambda") || valStack.peek().value.equals("eta")) {
                // Pack lambda/eta closure as a lamdaTuple node
                ASTNode lambdaMark = valStack.pop();
                ASTNode envNode    = valStack.pop();
                ASTNode boundVar   = valStack.pop();
                ASTNode deltaIdx   = valStack.pop();

                ASTNode lt = new ASTNode("lamdaTuple", "lamdaTuple");
                lt.left = deltaIdx;
                deltaIdx.right  = boundVar;
                boundVar.right  = envNode;
                envNode.right   = lambdaMark;
                item = lt;
            } else {
                item = valStack.pop();
            }

            item.right = head;
            head = item;
        }
        tau.left = head;
        valStack.push(tau);
    }

    /**
     * Force delayed operands before a strict operator is evaluated.
     * @param operation operation token to retry after forcing
     * @param arity number of stack operands required
     * @return true if a thunk was scheduled
     */
    private boolean forceStrictOperands(ASTNode operation, int arity) {
        if (arity == 1) {
            ASTNode node = valStack.peek();
            if (isThunk(node)) {
                valStack.pop();
                control.push(operation);
                forceThunk(node);
                return true;
            }
            return false;
        }

        ASTNode top = valStack.pop();
        if (isThunk(top)) {
            control.push(operation);
            forceThunk(top);
            return true;
        }

        ASTNode second = valStack.pop();
        if (isThunk(second)) {
            control.push(operation);
            control.push(pushValue(top));
            forceThunk(second);
            return true;
        }
        valStack.push(second);
        valStack.push(top);
        return false;
    }

    // ── Aug (tuple augmentation) ──────────────────────────────────────────

    private void applyAug() {
        ASTNode t1 = valStack.pop();
        ASTNode t2 = valStack.pop();

        boolean t1nil = t1.value.equals("nil") || (t1.type.equals("tau") && t1.left == null);
        boolean t2nil = t2.value.equals("nil") || (t2.type.equals("tau") && t2.left == null);

        if (t1nil && t2nil) {
            ASTNode tau = new ASTNode("tau", "tau");
            valStack.push(tau);
        } else if (t1nil) {
            ASTNode tau = new ASTNode("tau", "tau");
            tau.left = t2;
            valStack.push(tau);
        } else if (t2nil) {
            ASTNode tau = new ASTNode("tau", "tau");
            tau.left = t1;
            valStack.push(tau);
        } else if (!t1.type.equals("tau") && t2.type.equals("tau")) {
            // Append t1 to end of t2's child list
            ASTNode cur = t2.left;
            while (cur.right != null) cur = cur.right;
            cur.right = new ASTNode(t1.value, t1.type);
            valStack.push(t2);
        } else if (t1.type.equals("tau") && !t2.type.equals("tau")) {
            // Append t2 to end of t1's child list
            ASTNode cur = t1.left;
            while (cur.right != null) cur = cur.right;
            cur.right = new ASTNode(t2.value, t2.type);
            valStack.push(t1);
        } else {
            // Both tau — merge
            ASTNode tau = new ASTNode("tau", "tau");
            tau.left = t1;
            ASTNode cur = t1;
            while (cur.right != null) cur = cur.right;
            cur.right = t2;
            valStack.push(tau);
        }
    }

    // ── Print helpers ─────────────────────────────────────────────────────

    /**
     * Print the value node without consuming it from the stack.
     * Tuples print as "(v1, v2, v3)"; STRs have escape sequences processed.
     */
    private void printValue(ASTNode node) {
        if (node.type.equals("tau")) {
            List<ASTNode> elems = new ArrayList<>();
            arrangeTuple(node, elems);
            System.out.print("(");
            for (int i = 0; i < elems.size(); i++) {
                if (i > 0) System.out.print(", ");
                ASTNode e = elems.get(i);
                if (e.type.equals("STR")) System.out.print(processString(e.value));
                else                      System.out.print(e.value);
            }
            System.out.print(")");
        } else if (node.value.equals("lambda") || node.value.equals("eta")) {
            // ArrayDeque iterates from top to bottom. A closure is stored on
            // the stack as top-to-bottom: lambda, envNode, boundVar, deltaIdx.
            Object[] arr = valStack.toArray();
            String bv = arr.length >= 3 ? ((ASTNode) arr[2]).value : "?";
            String di = arr.length >= 4 ? ((ASTNode) arr[3]).value : "?";
            System.out.print("[lambda closure: " + bv + ": " + di + "]");
        } else if (node.type.equals("STR")) {
            System.out.print(processString(node.value));
        } else {
            System.out.print(node.value);
        }
    }

    /** Collect non-tau leaf nodes from a tau tree into a flat list. */
    private void arrangeTuple(ASTNode node, List<ASTNode> result) {
        if (node == null) return;
        if (node.value.equals("lamdaTuple")) return;
        if (!node.value.equals("tau") && !node.value.equals("nil")) {
            result.add(node);
        }
        arrangeTuple(node.left,  result);
        arrangeTuple(node.right, result);
    }

    /** Process RPAL string escape sequences and strip surrounding quotes if present. */
    private String processString(String s) {
        s = s.replace("\\n", "\n").replace("\\t", "\t");
        s = s.replace("'", "");
        return s;
    }

    // ── Environment chain lookup ──────────────────────────────────────────

    /** Find an environment by name by walking the envStack chain. */
    private Environment findEnv(String name) {
        Environment known = environments.get(name);
        if (known != null) return known;

        for (Environment e : envStack) {
            if (e.name.equals(name)) return e;
        }
        // Fallback: walk prev chain from currEnv
        Environment e = currEnv;
        while (e != null) {
            if (e.name.equals(name)) return e;
            e = e.prev;
        }
        throw new RuntimeException("Environment not found: " + name);
    }

    /**
     * Copy a stack value before storing or reusing it. The CSE machine mutates a
     * few marker nodes, notably lambda to eta during YSTAR application, so shared
     * environment bindings must not be pushed back by reference.
     */
    private ASTNode copyTree(ASTNode node) {
        if (node == null) return null;
        ASTNode copy = new ASTNode(node.value, node.type);
        copy.left = copyTree(node.left);
        copy.right = copyTree(node.right);
        return copy;
    }
}
