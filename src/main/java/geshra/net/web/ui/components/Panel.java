package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight Panel container using reusable stylesheet classes. Native child order, focus,
 * and browser layout remain intact; construction does not allocate a CSS model for each child.
 */
public class Panel extends Section {
    /**
     * Creates an empty Panel container.
     */
    public Panel() { attribute("class", "geshra-panel"); }

    /**
     * Creates a Panel container with initial children.
     * @param children initial child components
     */
    public Panel(Component... children) { this(); add(children); }
}
