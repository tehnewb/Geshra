package geshra.net.web.ui.components;



/**
 * Native select option with separate submitted value and visible label. Selection and disabled
 * states use actual Boolean properties; adding options preserves native browser defaults.
 */
public class Option extends TextComponent {
    /**
     * Creates an option.
     * @param value submitted string
     * @param label visible text
     */
    public Option(String value, String label) { super(label, "option"); setProperty("value", value); }

    /**
     * Sets whether this option is selected.
     * @param selected native selected state
     * @return this option
     */
    public Option setSelected(boolean selected) { setProperty("selected", selected); return this; }


}
