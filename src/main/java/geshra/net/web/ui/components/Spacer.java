package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight Spacer presentation element styled by the shared component stylesheet.
 * It requires no Java timer or per-frame server updates.
 */
public class Spacer extends Component {
    /**
     * Creates a Spacer element.
     */
    public Spacer() { super("div"); attribute("class", "geshra-spacer"); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
