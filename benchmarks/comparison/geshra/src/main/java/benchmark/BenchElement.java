package benchmark;

import geshra.net.web.ui.Component;

/**
 * Renders one native container tag without custom client code or themed widgets.
 * Child lifecycle and mutation batching remain owned by Geshra.
 */
public final class BenchElement extends Component {
    /**
     * Creates an unattached native element.
     * @param tag native HTML tag
     */
    public BenchElement(String tag) { super(tag); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
