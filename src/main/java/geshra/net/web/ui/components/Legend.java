package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native legend container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Legend extends Component {
    /**
     * Creates an empty legend container.
     */
    public Legend() { super("legend"); }

    /**
     * Creates a legend container with child components.
     * @param children initial children
     */
    public Legend(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
