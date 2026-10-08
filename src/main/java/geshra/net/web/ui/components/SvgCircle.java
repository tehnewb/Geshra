package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native SVG circle element. Attributes use their case-sensitive SVG spelling, and children
 * are created in the SVG namespace. Add attributes such as viewBox, d, cx, fill, and stroke
 * using the shared fluent attribute API.
 */
public class SvgCircle extends Component {
    /**
     * Creates a native SVG circle element.
     * @param children initial SVG children
     */
    public SvgCircle(Component... children) { super("circle"); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
