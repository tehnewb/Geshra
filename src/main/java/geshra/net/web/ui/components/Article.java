package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native article container. Child components retain the browser's ordinary DOM ordering and
 * semantic behavior; the component adds no per-frame scheduler or rendering loop.
 */
public class Article extends Component {
    /**
     * Creates an empty article container.
     */
    public Article() { super("article"); }

    /**
     * Creates a article container with child components.
     * @param children initial children
     */
    public Article(Component... children) { this(); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
