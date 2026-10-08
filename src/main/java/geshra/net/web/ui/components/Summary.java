package geshra.net.web.ui.components;

/**
 * Native summary text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Summary extends TextComponent {
    /**
     * Creates an empty summary element.
     */
    public Summary() { this(""); }

    /**
     * Creates a summary element with plain text.
     * @param text escaped text content
     */
    public Summary(String text) { super(text, "summary"); }
}
