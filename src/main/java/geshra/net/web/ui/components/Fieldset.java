package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native fieldset container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Fieldset extends Component {
    /**
     * Creates an empty fieldset container.
     */
    public Fieldset() { super("fieldset"); }

    /**
     * Creates a fieldset container with child components.
     * @param children initial children
     */
    public Fieldset(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
