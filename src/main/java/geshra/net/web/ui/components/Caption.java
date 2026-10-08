package geshra.net.web.ui.components;

/**
 * Native caption text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Caption extends TextComponent {
    /**
     * Creates an empty caption element.
     */
    public Caption() { this(""); }

    /**
     * Creates a caption element with plain text.
     * @param text escaped text content
     */
    public Caption(String text) { super(text, "caption"); }
}
