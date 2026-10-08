package geshra.net.web.ui.components;

/**
 * Native h2 text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class H2 extends TextComponent {
    /**
     * Creates an empty h2 element.
     */
    public H2() { this(""); }

    /**
     * Creates a h2 element with plain text.
     * @param text escaped text content
     */
    public H2(String text) { super(text, "h2"); }
}
