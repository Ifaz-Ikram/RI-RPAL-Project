import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Environment - Lexical scope chain for the CSE machine.
 * Variables are stored as String → List&lt;ASTNode&gt; to support
 * multi-item bindings (closures, partial Conc, etc.).
 */
public class Environment {
    /** Human-readable name, e.g. "env0", "env1". */
    public String name;

    /** Parent environment; null for the root (env0). */
    public Environment prev;

    /** Variable bindings: name → stored value nodes. */
    public Map<String, List<ASTNode>> boundVar;

    public Environment() {
        this.name     = "env0";
        this.prev     = null;
        this.boundVar = new HashMap<>();
    }

    /**
     * Walk the environment chain and return the binding for {@code varName}.
     * Throws RuntimeException if the variable is not found.
     */
    public List<ASTNode> lookup(String varName) {
        Environment env = this;
        while (env != null) {
            if (env.boundVar.containsKey(varName)) {
                return env.boundVar.get(varName);
            }
            env = env.prev;
        }
        throw new RuntimeException("Unbound variable: " + varName);
    }
}
