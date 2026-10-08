package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native footer container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Footer extends Component {
    /**
     * Creates an empty footer container.
     */
    public Footer() { super("footer"); }

    /**
     * Creates a footer container with child components.
     * @param children initial children
     */
    public Footer(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
