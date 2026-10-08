package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native dd container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class DescriptionDetail extends Component {
    /**
     * Creates an empty dd container.
     */
    public DescriptionDetail() { super("dd"); }

    /**
     * Creates a dd container with child components.
     * @param children initial children
     */
    public DescriptionDetail(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
