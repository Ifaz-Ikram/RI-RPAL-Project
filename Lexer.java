import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lexer - Tokenizes an RPAL source file.
 * Whitespace and line comments (//) are filtered before returning.
 */
public class Lexer {

    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
        "fn", "where", "let", "aug", "within", "in", "rec",
        "eq", "gr", "ge", "ls", "le", "ne", "or", "not",
        "true", "false", "nil", "dummy", "and"
    ));

    // Characters that form operator tokens
    private static final Set<Character> OP_CHARS = new HashSet<>(Arrays.asList(
        '+','-','*','<','>','&','.','@','/',':', '=','~','|','$','!',
        '#','%','^','_','[',']','{','}','"','`','?'
    ));

    /**
     * Scans the file and returns a token list with DELETE tokens removed.
     * @param filename path to the RPAL source file
     * @return list of tokens with whitespace/comments removed
     */
    public List<Token> scan(String filename) {
        String src;
        try {
            src = new String(Files.readAllBytes(Paths.get(filename)));
        } catch (IOException e) {
            throw new RuntimeException("Cannot read file: " + filename);
        }

        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int len = src.length();

        while (i < len) {
            char c = src.charAt(i);

            // Identifier or keyword
            if (Character.isLetter(c)) {
                int start = i;
                while (i + 1 < len && (Character.isLetterOrDigit(src.charAt(i + 1))
                        || src.charAt(i + 1) == '_')) {
                    i++;
                }
                String word = src.substring(start, i + 1);
                if (KEYWORDS.contains(word)) {
                    tokens.add(new Token(word, word));
                } else {
                    tokens.add(new Token(word, "<IDENTIFIER>"));
                }

            // Integer
            } else if (Character.isDigit(c)) {
                int start = i;
                while (i + 1 < len && Character.isDigit(src.charAt(i + 1))) {
                    i++;
                }
                tokens.add(new Token(normalizeInteger(src.substring(start, i + 1)), "<INTEGER>"));

            // Whitespace — DELETE
            } else if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                // skip

            // Open / close paren, semicolon, comma
            } else if (c == '(') {
                tokens.add(new Token("(", "("));
            } else if (c == ')') {
                tokens.add(new Token(")", ")"));
            } else if (c == ';') {
                tokens.add(new Token(";", ";"));
            } else if (c == ',') {
                tokens.add(new Token(",", ","));

            // String literal enclosed in single quotes
            } else if (c == '\'') {
                int start = i + 1;
                i++;
                // consume until closing '
                while (i < len && src.charAt(i) != '\'') {
                    i++;
                }
                // i is now at closing quote
                String strVal = (i < len) ? src.substring(start, i) : src.substring(start);
                tokens.add(new Token(strVal, "<STRING>"));

            // Line comment // — DELETE
            } else if (c == '/' && i + 1 < len && src.charAt(i + 1) == '/') {
                // skip until end of line
                while (i < len && src.charAt(i) != '\n') {
                    i++;
                }
                // don't add token

            // Operator characters
            } else if (OP_CHARS.contains(c)) {
                int start = i;
                while (i + 1 < len && OP_CHARS.contains(src.charAt(i + 1))) {
                    i++;
                }
                tokens.add(new Token(src.substring(start, i + 1), "<OPERATOR>"));
            }
            // else: ignore unknown characters

            i++;
        }

        return tokens;
    }

    /**
     * Convert integer literals to their canonical decimal spelling.
     * @param value raw integer token text
     * @return integer text without redundant leading zeroes
     */
    private String normalizeInteger(String value) {
        int idx = 0;
        while (idx < value.length() - 1 && value.charAt(idx) == '0') {
            idx++;
        }
        return value.substring(idx);
    }
}
