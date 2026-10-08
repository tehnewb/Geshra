package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native SVG polygon element. Attributes use their case-sensitive SVG spelling, and children
 * are created in the SVG namespace. Add attributes such as viewBox, d, cx, fill, and stroke
 * using the shared fluent attribute API.
 */
public class SvgPolygon extends Component {
    /**
     * Creates a native SVG polygon element.
     * @param children initial SVG children
     */
    public SvgPolygon(Component... children) { super("polygon"); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
