package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native figure container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Figure extends Component {
    /**
     * Creates an empty figure container.
     */
    public Figure() { super("figure"); }

    /**
     * Creates a figure container with child components.
     * @param children initial children
     */
    public Figure(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
