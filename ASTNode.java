/**
 * ASTNode - Binary tree node using left-child / right-sibling representation.
 * left  = first child of this node
 * right = next sibling (NOT a second child)
 */
public class ASTNode {
    public String value;
    public String type;
    public ASTNode left;   // first child
    public ASTNode right;  // next sibling

    public ASTNode(String value, String type) {
        this.value = value;
        this.type  = type;
        this.left  = null;
        this.right = null;
    }

    /**
     * Shallow copy: same value/type, keeps left subtree, clears right sibling.
     * Used by the standardizer to avoid mutating shared subtrees.
     */
    public ASTNode shallowCopy() {
        ASTNode copy = new ASTNode(this.value, this.type);
        copy.left  = this.left;
        copy.right = null;
        return copy;
    }
}
