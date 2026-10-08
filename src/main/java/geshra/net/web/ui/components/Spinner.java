package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight Spinner presentation element styled by the shared component stylesheet.
 * It requires no Java timer or per-frame server updates.
 */
public class Spinner extends Component {
    /**
     * Creates a Spinner element.
     */
    public Spinner() { super("span"); attribute("class", "geshra-spinner"); attribute("role", "status"); ariaLabel("Loading"); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
