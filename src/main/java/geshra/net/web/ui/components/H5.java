package geshra.net.web.ui.components;

/**
 * Native h5 text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class H5 extends TextComponent {
    /**
     * Creates an empty h5 element.
     */
    public H5() { this(""); }

    /**
     * Creates a h5 element with plain text.
     * @param text escaped text content
     */
    public H5(String text) { super(text, "h5"); }
}
