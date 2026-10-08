package geshra.net.web.ui.components;



/**
 * Native span text element with the tooltip accessibility role.
 */
public class Tooltip extends TextComponent {
    /**
     * Creates an accessible text element.
     * @param text display text
     */
    public Tooltip(String text) { super(text, "span"); attribute("role", "tooltip");  }


}
