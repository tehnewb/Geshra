package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native dl container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class DefinitionList extends Component {
    /**
     * Creates an empty dl container.
     */
    public DefinitionList() { super("dl"); }

    /**
     * Creates a dl container with child components.
     * @param children initial children
     */
    public DefinitionList(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
