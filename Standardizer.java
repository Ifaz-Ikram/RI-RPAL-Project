/**
 * Standardizer - Transforms the raw AST into a standardized tree (ST).
 * Uses post-order traversal and applies rewrite rules.
 * Must be called in a loop (10 passes) because some rules cascade.
 */
public class Standardizer {

    /**
     * Run one full post-order standardization pass over the tree rooted at t.
     * @param t root of the tree to standardize
     */
    public void standardize(ASTNode t) {
        if (t == null) return;
        standardize(t.left);
        standardize(t.right);
        applyRule(t);
    }

    // ── Rewrite rules ─────────────────────────────────────────────────────

    private void applyRule(ASTNode t) {
        if (t == null) return;

        switch (t.value) {
            case "let":   rewriteLet(t);     break;
            case "where": rewriteWhere(t);   break;
            case "within": rewriteWithin(t); break;
            case "and":   rewriteAnd(t);     break;
            case "rec":   rewriteRec(t);     break;
            case "fcn_form": rewriteFcnForm(t); break;
            case "lambda": rewriteLambda(t); break;
            case "@":     rewriteAt(t);      break;
        }
    }

    // let D in E  →  gamma(lambda(X, P), E)
    private void rewriteLet(ASTNode t) {
        if (t.left == null || !t.left.value.equals("=")) return;
        ASTNode eqNode = t.left;
        ASTNode X = eqNode.left.shallowCopy();
        ASTNode E = eqNode.left.right.shallowCopy();
        ASTNode P = eqNode.right.shallowCopy();

        t.value = "gamma"; t.type = "KEYWORD";
        t.left  = new ASTNode("lambda", "KEYWORD");
        t.left.right = E;
        t.left.left  = X;
        X.right = P;
    }

    // where E = D  →  gamma(lambda(X, P), E)
    private void rewriteWhere(ASTNode t) {
        if (t.left == null || t.left.right == null
                || !t.left.right.value.equals("=")) return;
        ASTNode P      = t.left.shallowCopy();
        ASTNode eqNode = t.left.right;
        ASTNode X = eqNode.left.shallowCopy();
        ASTNode E = eqNode.left.right.shallowCopy();

        t.value = "gamma"; t.type = "KEYWORD";
        t.left  = new ASTNode("lambda", "KEYWORD");
        t.left.right = E;
        t.left.left  = X;
        X.right = P;
    }

    // within  →  =(X2, gamma(lambda(X1,E2), E1))
    private void rewriteWithin(ASTNode t) {
        if (t.left == null || !t.left.value.equals("=")) return;
        if (t.left.right == null || !t.left.right.value.equals("=")) return;

        ASTNode eq1 = t.left, eq2 = t.left.right;
        ASTNode X1 = eq1.left.shallowCopy();
        ASTNode E1 = eq1.left.right.shallowCopy();
        ASTNode X2 = eq2.left.shallowCopy();
        ASTNode E2 = eq2.left.right.shallowCopy();

        t.value = "="; t.type = "KEYWORD";
        t.left  = X2;
        ASTNode gamma  = new ASTNode("gamma",  "KEYWORD");
        ASTNode lambda = new ASTNode("lambda", "KEYWORD");
        X2.right      = gamma;
        gamma.left    = lambda;
        lambda.right  = E1;
        lambda.left   = X1;
        X1.right      = E2;
    }

    // and (=1, =2, ...)  →  =(,(X1,X2,...), tau(E1,E2,...))
    private void rewriteAnd(ASTNode t) {
        if (t.left == null || !t.left.value.equals("=")) return;

        ASTNode commaNode = new ASTNode(",", "KEYWORD");
        ASTNode tauNode   = new ASTNode("tau", "KEYWORD");

        ASTNode eq = t.left;
        ASTNode commaHead = null, commaTail = null;
        ASTNode tauHead   = null, tauTail   = null;

        while (eq != null) {
            ASTNode xCopy = eq.left.shallowCopy();
            ASTNode eCopy = eq.left.right.shallowCopy();

            if (commaHead == null) { commaHead = commaTail = xCopy; }
            else { commaTail.right = xCopy; commaTail = xCopy; }

            if (tauHead == null) { tauHead = tauTail = eCopy; }
            else { tauTail.right = eCopy; tauTail = eCopy; }

            eq = eq.right;
        }

        commaNode.left = commaHead;
        tauNode.left   = tauHead;

        t.value = "="; t.type = "KEYWORD";
        t.left  = commaNode;
        commaNode.right = tauNode;
    }

    // rec (=(X,E))  →  =(X, gamma(YSTAR, lambda(X,E)))
    private void rewriteRec(ASTNode t) {
        if (t.left == null || !t.left.value.equals("=")) return;

        ASTNode eqNode = t.left;
        ASTNode X = eqNode.left.shallowCopy();
        ASTNode E = eqNode.left.right.shallowCopy();

        t.value = "="; t.type = "KEYWORD";
        t.left  = X;

        ASTNode gamma  = new ASTNode("gamma",  "KEYWORD");
        ASTNode ystar  = new ASTNode("YSTAR",  "KEYWORD");
        ASTNode lambda = new ASTNode("lambda", "KEYWORD");

        X.right       = gamma;
        gamma.left    = ystar;
        ystar.right   = lambda;
        lambda.left   = X.shallowCopy();
        lambda.left.right = E;
    }

    // fcn_form  →  =(P, lambda(V1, lambda(V2, ... E)))
    private void rewriteFcnForm(ASTNode t) {
        ASTNode P = t.left.shallowCopy();
        ASTNode V = t.left.right; // first Vb

        t.value = "="; t.type = "KEYWORD";
        t.left  = P;

        // walk Vb chain, building nested lambdas;
        // last V.right is the body E
        ASTNode cur = t;
        while (V.right != null && V.right.right != null) {
            ASTNode lam = new ASTNode("lambda", "KEYWORD");
            cur.left.right = lam;
            cur = cur.left.right;
            cur.left = V.shallowCopy();
            V = V.right;
        }
        // final lambda: left=V, left.right=E
        ASTNode lam = new ASTNode("lambda", "KEYWORD");
        cur.left.right = lam;
        lam.left = V.shallowCopy();
        lam.left.right = V.right;
    }

    // lambda with multiple Vb children  →  nested lambdas
    private void rewriteLambda(ASTNode t) {
        if (t.left == null) return;
        ASTNode V = t.left;
        if (V.right == null || V.right.right == null) return; // 0 or 1 param

        ASTNode cur = t;
        while (V.right != null && V.right.right != null) {
            ASTNode lam = new ASTNode("lambda", "KEYWORD");
            cur.left.right = lam;
            cur = cur.left.right;
            cur.left = V.shallowCopy();
            V = V.right;
        }
        ASTNode lam = new ASTNode("lambda", "KEYWORD");
        cur.left.right = lam;
        lam.left = V.shallowCopy();
        lam.left.right = V.right;
    }

    // @  →  gamma(gamma(N, E1), E2)
    private void rewriteAt(ASTNode t) {
        ASTNode E1 = t.left.shallowCopy();
        ASTNode N  = t.left.right.shallowCopy();
        ASTNode E2 = t.left.right.right.shallowCopy();

        t.value = "gamma"; t.type = "KEYWORD";
        ASTNode inner = new ASTNode("gamma", "KEYWORD");
        t.left        = inner;
        inner.right   = E2;
        inner.left    = N;
        N.right       = E1;
    }
}
