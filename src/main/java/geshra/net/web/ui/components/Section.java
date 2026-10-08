package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native section container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Section extends Component {
    /**
     * Creates an empty section container.
     */
    public Section() { super("section"); }

    /**
     * Creates a section container with child components.
     * @param children initial children
     */
    public Section(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
