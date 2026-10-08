package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight HorizontalLayout container using reusable stylesheet classes. Native child order, focus,
 * and browser layout remain intact; construction does not allocate a CSS model for each child.
 */
public class HorizontalLayout extends Div {
    /**
     * Creates an empty HorizontalLayout container.
     */
    public HorizontalLayout() { attribute("class", "geshra-horizontal"); }

    /**
     * Creates a HorizontalLayout container with initial children.
     * @param children initial child components
     */
    public HorizontalLayout(Component... children) { this(); add(children); }
}
