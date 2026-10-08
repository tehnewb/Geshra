package geshra.net.web.ui.components;

/**
 * Native strong text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Strong extends TextComponent {
    /**
     * Creates an empty strong element.
     */
    public Strong() { this(""); }

    /**
     * Creates a strong element with plain text.
     * @param text escaped text content
     */
    public Strong(String text) { super(text, "strong"); }
}
