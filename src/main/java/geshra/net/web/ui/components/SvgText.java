package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Native SVG text element. Attributes use their case-sensitive SVG spelling, and children
 * are created in the SVG namespace. Add attributes such as viewBox, d, cx, fill, and stroke
 * using the shared fluent attribute API.
 */
public class SvgText extends Component {
    /**
     * Creates a native SVG text element.
     * @param children initial SVG children
     */
    public SvgText(Component... children) { super("text"); add(children); }

    @Override
    protected void create() { }

    @Override
    protected void destroy() { }
}
