package geshra.net.web.ui.css;

/**
 * Enum representing valid CSS {@code text-decoration} property values.
 * <p>
 * The {@code text-decoration} property specifies the decoration added to text,
 * such as underlines, overlines, or strikethroughs.
 *
 * <ul>
 *     <li>{@code NONE} – No decoration is applied (removes any inherited decoration).</li>
 *     <li>{@code UNDERLINE} – A line is drawn below the text.</li>
 *     <li>{@code OVERLINE} – A line is drawn above the text.</li>
 *     <li>{@code LINE_THROUGH} – A line is drawn through the text (strikethrough).</li>
 * </ul>
 *
 * @see <a href="https://developer.mozilla.org/en-US/docs/Web/CSS/text-decoration">MDN Reference</a>
 */
public enum TextDecoration {

    /**
     * No decoration applied to the text.
     */
    NONE("none"),

    /**
     * Underlines the text.
     */
    UNDERLINE("underline"),

    /**
     * Overlines the text.
     */
    OVERLINE("overline"),

    /**
     * Draws a line through the text (strikethrough).
     */
    LINE_THROUGH("line-through");

    private final String value; // CSS keyword emitted for this enum constant.

    /**
     * Associates this constant with the CSS keyword emitted when serializing declarations.
     *
     * @param value application value or CSS text to retain
     */
    TextDecoration(String value) {
        this.value = value;
    }

    /**
     * Gets the CSS property name this enum represents.
     *
     * @return the string {@code "text-decoration"}
     */
    public static String getPropertyName() {
        /*
         * Return the fixed CSS property string without allocating a declaration.
         */
        return "text-decoration";
    }

    /**
     * Gets the raw CSS value for this enum constant.
     *
     * @return the CSS string value (e.g., "underline", "line-through")
     */
    public String getValue() {
        return value;
    }
}
