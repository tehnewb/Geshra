package geshra.net.web.ui.components;



/**
 * Accessible binary switch using native checkbox activation, keyboard, focus, and checked behavior.
 */
public class Switch extends Checkbox {
    /**
     * Creates an unchecked switch.
     */
    public Switch() { this(false); }

    /**
     * Creates a switch with an initial state.
     * @param checked initial state
     */
    public Switch(boolean checked) { super(checked); attribute("role", "switch"); }


}
