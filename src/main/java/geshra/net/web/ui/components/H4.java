package geshra.net.web.ui.components;

/**
 * Native h4 text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class H4 extends TextComponent {
    /**
     * Creates an empty h4 element.
     */
    public H4() { this(""); }

    /**
     * Creates a h4 element with plain text.
     * @param text escaped text content
     */
    public H4(String text) { super(text, "h4"); }
}
