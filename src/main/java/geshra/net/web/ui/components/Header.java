package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native header container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Header extends Component {
    /**
     * Creates an empty header container.
     */
    public Header() { super("header"); }

    /**
     * Creates a header container with child components.
     * @param children initial children
     */
    public Header(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
