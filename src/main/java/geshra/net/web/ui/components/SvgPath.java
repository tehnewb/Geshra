package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native SVG path element. Attributes use their case-sensitive SVG spelling, and children
 * are created in the SVG namespace. Add attributes such as viewBox, d, cx, fill, and stroke
 * using the shared fluent attribute API.
 */
public class SvgPath extends Component {
    /**
     * Creates a native SVG path element.
     * @param children initial SVG children
     */
    public SvgPath(Component... children) { super("path"); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
