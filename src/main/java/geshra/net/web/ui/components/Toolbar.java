package geshra.net.web.ui.components;

import geshra.net.web.ui.Component;

/**
 * Lightweight Toolbar container using reusable stylesheet classes. Native child order, focus,
 * and browser layout remain intact; construction does not allocate a CSS model for each child.
 */
public class Toolbar extends HorizontalLayout {
    /**
     * Creates an empty Toolbar container.
     */
    public Toolbar() { attribute("class", "geshra-toolbar"); }

    /**
     * Creates a Toolbar container with initial children.
     * @param children initial child components
     */
    public Toolbar(Component... children) { this(); add(children); }
}
