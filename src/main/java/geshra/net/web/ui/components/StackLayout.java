package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight StackLayout container using reusable stylesheet classes. Native child order, focus,
 * and browser layout remain intact; construction does not allocate a CSS model for each child.
 */
public class StackLayout extends Div {
    /**
     * Creates an empty StackLayout container.
     */
    public StackLayout() { attribute("class", "geshra-stack"); }

    /**
     * Creates a StackLayout container with initial children.
     * @param children initial child components
     */
    public StackLayout(Component... children) { this(); add(children); }
}
