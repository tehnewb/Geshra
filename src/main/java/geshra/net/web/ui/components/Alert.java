package geshra.net.web.ui.components;



/**
 * Native div text element with the alert accessibility role.
 */
public class Alert extends TextComponent {
    /**
     * Creates an accessible text element.
     * @param text display text
     */
    public Alert(String text) { super(text, "div"); attribute("role", "alert"); attribute("class", "geshra-alert"); }


}
