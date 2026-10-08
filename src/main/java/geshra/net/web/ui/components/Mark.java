package geshra.net.web.ui.components;

/**
 * Native mark text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Mark extends TextComponent {
    /**
     * Creates an empty mark element.
     */
    public Mark() { this(""); }

    /**
     * Creates a mark element with plain text.
     * @param text escaped text content
     */
    public Mark(String text) { super(text, "mark"); }
}
