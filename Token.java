/**
 * Token - Simple data class representing a lexical token.
 */
public class Token {
    public String value;
    public String type;

    public Token(String value, String type) {
        this.value = value;
        this.type  = type;
    }

    @Override
    public String toString() {
        return "Token(" + type + ", " + value + ")";
    }
}
