package geshra.net.web.ui.components;

/**
 * Native h3 text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class H3 extends TextComponent {
    /**
     * Creates an empty h3 element.
     */
    public H3() { this(""); }

    /**
     * Creates a h3 element with plain text.
     * @param text escaped text content
     */
    public H3(String text) { super(text, "h3"); }
}
