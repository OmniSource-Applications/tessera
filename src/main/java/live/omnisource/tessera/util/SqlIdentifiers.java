package live.omnisource.tessera.util;

import java.util.regex.Pattern;

public class SqlIdentifiers {
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_]\\w{0,127}$");

    private SqlIdentifiers() {}

    /**
     * Validate that the given string is a safe SQL identifier.
     * Throws IllegalArgumentException if the identifier contains unsafe characters.
     */
    public static String validate(String identifier, String label) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        if (!SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    label + " contains invalid characters: " + identifier +
                            ". Only letters, digits, and underscores are allowed.");
        }
        return identifier;
    }

    /** Validate and double-quote for PostgreSQL / Oracle / standard SQL. */
    public static String quoteDouble(String identifier, String label) {
        return "\"" + validate(identifier, label) + "\"";
    }

    /** Validate and backtick-quote for MySQL. */
    public static String quoteBacktick(String identifier, String label) {
        return "`" + validate(identifier, label) + "`";
    }

    /** Check if the identifier is safe without throwing. */
    public static boolean isSafe(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }
}
