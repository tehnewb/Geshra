package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight Divider presentation element styled by the shared component stylesheet.
 * It requires no Java timer or per-frame server updates.
 */
public class Divider extends Component {
    /**
     * Creates a Divider element.
     */
    public Divider() { super("hr"); attribute("class", "geshra-divider"); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
