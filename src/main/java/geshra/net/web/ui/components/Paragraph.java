package geshra.net.web.ui.components;

/**
 * Native p text element. Text is assigned through textContent, preserving escaping and
 * the browser's ordinary semantic, accessibility, and layout behavior.
 */
public class Paragraph extends TextComponent {
    /**
     * Creates an empty p element.
     */
    public Paragraph() { this(""); }

    /**
     * Creates a p element with plain text.
     * @param text escaped text content
     */
    public Paragraph(String text) { super(text, "p"); }
}
