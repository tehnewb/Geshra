package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native ol container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class OrderedList extends Component {
    /**
     * Creates an empty ol container.
     */
    public OrderedList() { super("ol"); }

    /**
     * Creates a ol container with child components.
     * @param children initial children
     */
    public OrderedList(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
