package geshra.net.web.ui.components;

/**
 * Native blockquote text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Blockquote extends TextComponent {
    /**
     * Creates an empty blockquote element.
     */
    public Blockquote() { this(""); }

    /**
     * Creates a blockquote element with plain text.
     * @param text escaped text content
     */
    public Blockquote(String text) { super(text, "blockquote"); }
}
