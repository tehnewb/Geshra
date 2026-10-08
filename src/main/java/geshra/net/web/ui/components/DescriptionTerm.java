package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native dt container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class DescriptionTerm extends Component {
    /**
     * Creates an empty dt container.
     */
    public DescriptionTerm() { super("dt"); }

    /**
     * Creates a dt container with child components.
     * @param children initial children
     */
    public DescriptionTerm(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
