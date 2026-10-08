package geshra.net.web.ui.components;

/**
 * Native em text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Emphasis extends TextComponent {
    /**
     * Creates an empty em element.
     */
    public Emphasis() { this(""); }

    /**
     * Creates a em element with plain text.
     * @param text escaped text content
     */
    public Emphasis(String text) { super(text, "em"); }
}
