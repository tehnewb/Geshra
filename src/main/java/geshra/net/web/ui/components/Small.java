package geshra.net.web.ui.components;

/**
 * Native small text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Small extends TextComponent {
    /**
     * Creates an empty small element.
     */
    public Small() { this(""); }

    /**
     * Creates a small element with plain text.
     * @param text escaped text content
     */
    public Small(String text) { super(text, "small"); }
}
