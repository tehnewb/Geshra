package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native SVG svg element. Attributes use their case-sensitive SVG spelling, and children
 * are created in the SVG namespace. Add attributes such as viewBox, d, cx, fill, and stroke
 * using the shared fluent attribute API.
 */
public class Svg extends Component {
    /**
     * Creates a native SVG svg element.
     * @param children initial SVG children
     */
    public Svg(Component... children) { super("svg"); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
