package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native main container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Main extends Component {
    /**
     * Creates an empty main container.
     */
    public Main() { super("main"); }

    /**
     * Creates a main container with child components.
     * @param children initial children
     */
    public Main(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
