package geshra.net.web.ui.components;



/**
 * Native output text element with the status accessibility role.
 */
public class Output extends TextComponent {
    /**
     * Creates an accessible text element.
     * @param text display text
     */
    public Output(String text) { super(text, "output"); attribute("role", "status");  }


}
