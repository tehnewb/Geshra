package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native nav container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Navigation extends Component {
    /**
     * Creates an empty nav container.
     */
    public Navigation() { super("nav"); }

    /**
     * Creates a nav container with child components.
     * @param children initial children
     */
    public Navigation(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
