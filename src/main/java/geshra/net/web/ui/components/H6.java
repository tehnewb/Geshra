package geshra.net.web.ui.components;

/**
 * Native h6 text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class H6 extends TextComponent {
    /**
     * Creates an empty h6 element.
     */
    public H6() { this(""); }

    /**
     * Creates a h6 element with plain text.
     * @param text escaped text content
     */
    public H6(String text) { super(text, "h6"); }
}
