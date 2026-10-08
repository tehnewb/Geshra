package geshra.net.web.ui.components;

/**
 * Native pre text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Preformatted extends TextComponent {
    /**
     * Creates an empty pre element.
     */
    public Preformatted() { this(""); }

    /**
     * Creates a pre element with plain text.
     * @param text escaped text content
     */
    public Preformatted(String text) { super(text, "pre"); }
}
