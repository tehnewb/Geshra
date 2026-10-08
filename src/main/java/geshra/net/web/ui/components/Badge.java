package geshra.net.web.ui.components;



/**
 * Native span text element with the status accessibility role.
 */
public class Badge extends TextComponent {
    /**
     * Creates an accessible text element.
     * @param text display text
     */
    public Badge(String text) { super(text, "span"); attribute("role", "status"); attribute("class", "geshra-badge"); }


}
