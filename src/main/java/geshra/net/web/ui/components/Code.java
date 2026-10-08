package geshra.net.web.ui.components;

/**
 * Native code text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Code extends TextComponent {
    /**
     * Creates an empty code element.
     */
    public Code() { this(""); }

    /**
     * Creates a code element with plain text.
     * @param text escaped text content
     */
    public Code(String text) { super(text, "code"); }
}
