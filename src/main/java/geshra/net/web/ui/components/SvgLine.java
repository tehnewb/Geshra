package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native SVG line element. Attributes use their case-sensitive SVG spelling, and children
 * are created in the SVG namespace. Add attributes such as viewBox, d, cx, fill, and stroke
 * using the shared fluent attribute API.
 */
public class SvgLine extends Component {
    /**
     * Creates a native SVG line element.
     * @param children initial SVG children
     */
    public SvgLine(Component... children) { super("line"); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
